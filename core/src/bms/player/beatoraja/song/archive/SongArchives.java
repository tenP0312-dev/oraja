package bms.player.beatoraja.song.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;

/**
 * Registry and path-based facade for song archives. Add a {@link SongArchive}
 * implementation here to support another archive format.
 */
public final class SongArchives {

	private static final List<SongArchive> ARCHIVES = List.of(new ZipSongArchive(), new RarSongArchive());
	private static final int MAX_CACHED_ARCHIVES = 128;
	private static final ConcurrentHashMap<Path, CachedArchive> ENTRY_CACHE = new ConcurrentHashMap<>();
	private SongArchives() {
	}

	public static boolean isSupportedArchive(Path path) {
		return archiveFor(path) != null;
	}

	/** Converts a local or virtual archive path into a resource abstraction. */
	public static SongResource resource(Path path) {
		ArchivePath archivePath = parse(path);
		return archivePath == null ? SongResources.local(path)
				: new ArchiveSongResource(archivePath.archive(), archivePath.entryName(), archivePath.visibleRoot(),
						archivePath.directory());
	}

	public static Path virtualPath(Path archive, String entryName) {
		return Path.of(archive + "!/" + normalizeEntryName(entryName));
	}

	/**
	 * Returns an archive path whose visible hierarchy omits one shared top-level
	 * directory while preserving the actual archive entry name internally.
	 */
	public static Path virtualPath(Path archive, String entryName, String rootDirectory) {
		String normalizedEntryName = normalizeEntryName(entryName);
		if (rootDirectory == null) {
			return virtualPath(archive, normalizedEntryName);
		}
		String normalizedRootDirectory = normalizeRootDirectory(rootDirectory);
		if (!normalizedEntryName.startsWith(normalizedRootDirectory + "/")) {
			throw new IllegalArgumentException("Archive entry is not inside the root directory: " + entryName);
		}
		return Path.of(archive + "!-" + normalizedRootDirectory + "/"
				+ normalizedEntryName.substring(normalizedRootDirectory.length() + 1));
	}

	public static Path virtualRoot(Path archive) {
		return Path.of(archive + "!");
	}

	public static Path virtualRoot(Path archive, String rootDirectory) {
		return rootDirectory == null ? virtualRoot(archive)
				: Path.of(archive + "!-" + normalizeRootDirectory(rootDirectory));
	}

	public static boolean isVirtualPath(Path path) {
		return parse(path) != null;
	}

	public static String entryName(Path path) {
		ArchivePath archivePath = parse(path);
		return archivePath != null ? archivePath.entryName() : null;
	}

	public static InputStream openStream(Path path) throws IOException {
		ArchivePath archivePath = parse(path);
		return archivePath == null ? Files.newInputStream(path) : openEntry(archivePath.archive(), archivePath.entryName());
	}

	/**
	 * Returns an ordinary path for a regular path or materializes one archive
	 * entry. This deliberately never extracts an entire archive.
	 */
	public static Path resolve(Path path) throws IOException {
		return resource(path).materialize();
	}

	public static List<String> listEntries(Path archive) throws IOException {
		return cachedArchive(archive).entries();
	}

	private static CachedArchive cachedArchive(Path archive) throws IOException {
		Path normalized = archive.toAbsolutePath().normalize();
		long size = Files.size(normalized);
		long modified = Files.getLastModifiedTime(normalized).toMillis();
		CachedArchive cached = ENTRY_CACHE.get(normalized);
		if (cached != null && cached.size() == size && cached.modified() == modified) {
			return cached;
		}
		List<String> entries = List.copyOf(archiveForRequired(normalized).listEntries(normalized));
		Map<String, String> namesByLowerCase = new HashMap<>();
		for (String entry : entries) {
			String previous = namesByLowerCase.put(entry.toLowerCase(Locale.ROOT), entry);
			if (previous != null && !previous.equals(entry)) {
				throw new IOException("Archive has entries that differ only by case: " + previous + " / " + entry);
			}
		}
		CachedArchive result = new CachedArchive(entries, Map.copyOf(namesByLowerCase), size, modified);
		ENTRY_CACHE.put(normalized, result);
		trimEntryCache(normalized);
		return result;
	}

	private static void trimEntryCache(Path retainedArchive) {
		if (ENTRY_CACHE.size() <= MAX_CACHED_ARCHIVES) {
			return;
		}
		for (Path cachedArchive : ENTRY_CACHE.keySet()) {
			if (ENTRY_CACHE.size() <= MAX_CACHED_ARCHIVES || cachedArchive.equals(retainedArchive)) {
				continue;
			}
			ENTRY_CACHE.remove(cachedArchive);
		}
	}

	static InputStream openEntry(Path archive, String entryName) throws IOException {
		return archiveForRequired(archive).openEntry(archive, canonicalEntryName(archive, entryName));
	}

	static boolean hasEntry(Path archive, String entryName) throws IOException {
		return cachedArchive(archive).namesByLowerCase().containsKey(entryName.toLowerCase(Locale.ROOT));
	}

	static boolean hasDirectory(Path archive, String entryName) throws IOException {
		String prefix = (entryName.isEmpty() ? "" : entryName + "/").toLowerCase(Locale.ROOT);
		return listEntries(archive).stream()
				.anyMatch(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(prefix));
	}

	static long entrySize(Path archive, String entryName) throws IOException {
		return archiveForRequired(archive).entrySize(archive, canonicalEntryName(archive, entryName));
	}

	static String archiveCacheKey(Path archive) {
		try {
			return archive.toAbsolutePath() + ":" + Files.size(archive) + ":" + Files.getLastModifiedTime(archive);
		} catch (IOException e) {
			return archive.toAbsolutePath().toString();
		}
	}

	static String resolveEntryName(String base, String relativePath) {
		String raw = (base.isEmpty() ? "" : base + "/") + relativePath.replace('\\', '/');
		if (relativePath.startsWith("/") || relativePath.startsWith("\\") || raw.contains("//")) {
			throw new IllegalArgumentException("Unsafe archive entry: " + relativePath);
		}
		java.util.ArrayDeque<String> components = new java.util.ArrayDeque<>();
		for (String component : raw.split("/")) {
			if (component.isEmpty() || component.equals(".")) {
				continue;
			}
			if (component.equals("..")) {
				if (components.isEmpty()) {
					throw new IllegalArgumentException("Archive resource escapes archive root: " + relativePath);
				}
				components.removeLast();
			} else {
				components.addLast(component);
			}
		}
		if (components.isEmpty()) {
			throw new IllegalArgumentException("Unsafe archive entry: " + relativePath);
		}
		return String.join("/", components);
	}

	/** Inspects entries and identifies a single top-level content directory. */
	public static ArchiveContents readContents(Path archive) throws IOException {
		List<String> entries = listEntries(archive);
		String rootDirectory = null;
		for (String entry : entries) {
			if (isArchiveMetadata(entry)) {
				continue;
			}
			int separator = entry.indexOf('/');
			if (separator <= 0) {
				return new ArchiveContents(entries, null);
			}
			String directory = entry.substring(0, separator);
			if (rootDirectory == null) {
				rootDirectory = directory;
			} else if (!rootDirectory.equals(directory)) {
				return new ArchiveContents(entries, null);
			}
		}
		return new ArchiveContents(entries, rootDirectory);
	}

	/** Reads a chart entry without materializing the entire archive. */
	public static byte[] readEntry(Path path) throws IOException {
		ArchivePath archivePath = parse(path);
		if (archivePath == null) {
			return Files.readAllBytes(path);
		}
		return archiveForRequired(archivePath.archive()).readEntry(
				archivePath.archive(), canonicalEntryName(archivePath.archive(), archivePath.entryName()));
	}

	private static String canonicalEntryName(Path archive, String entryName) throws IOException {
		String canonical = cachedArchive(archive).namesByLowerCase()
				.get(entryName.toLowerCase(Locale.ROOT));
		if (canonical == null) {
			throw new IOException("Archive entry does not exist: " + entryName);
		}
		return canonical;
	}

	private static SongArchive archiveFor(Path path) {
		return ARCHIVES.stream().filter(archive -> archive.supports(path)).findFirst().orElse(null);
	}

	private static SongArchive archiveForRequired(Path path) throws IOException {
		SongArchive archive = archiveFor(path);
		if (archive == null) {
			throw new IOException("Unsupported song archive: " + path);
		}
		return archive;
	}

	private static ArchivePath parse(Path path) {
		String value = path.toString();
		String lowerCase = value.toLowerCase(Locale.ROOT);
		for (SongArchive archive : ARCHIVES) {
			for (String extension : archive.extensions()) {
				int marker = lowerCase.lastIndexOf(extension + "!");
				if (marker < 0) {
					continue;
				}
				int archiveEnd = marker + extension.length();
				if (value.length() <= archiveEnd || value.charAt(archiveEnd) != '!') {
					continue;
				}
				if (value.length() == archiveEnd + 1) {
					return new ArchivePath(Path.of(value.substring(0, archiveEnd)), "", null, true);
				}
				char separator = value.charAt(archiveEnd + 1);
				if (separator != '/' && separator != '\\' && separator != '-') {
					continue;
				}
				if (separator == '-') {
					String rootAndEntry = value.substring(archiveEnd + 2).replace('\\', '/');
					int rootEnd = rootAndEntry.indexOf('/');
					String root = normalizeRootDirectory(rootEnd >= 0 ? rootAndEntry.substring(0, rootEnd) : rootAndEntry);
					if (rootEnd < 0) {
						return new ArchivePath(Path.of(value.substring(0, archiveEnd)), root, root, true);
					}
					String relativeEntry = normalizeEntryNameOrNull(rootAndEntry.substring(rootEnd + 1));
					if (relativeEntry != null) {
						return new ArchivePath(Path.of(value.substring(0, archiveEnd)), root + "/" + relativeEntry, root, false);
					}
				} else {
					String entryName = normalizeEntryNameOrNull(value.substring(archiveEnd + 2));
					if (entryName != null) {
						return new ArchivePath(Path.of(value.substring(0, archiveEnd)), entryName, null, false);
					}
				}
		}
		}
		return null;
	}

	private static String normalizeEntryName(String entryName) {
		String normalized = normalizeEntryNameOrNull(entryName);
		if (normalized == null) {
			throw new IllegalArgumentException("Unsafe archive entry: " + entryName);
		}
		return normalized;
	}

	private static String normalizeEntryNameOrNull(String entryName) {
		String normalized = entryName.replace('\\', '/');
		while (normalized.startsWith("./")) {
			normalized = normalized.substring(2);
		}
		if (normalized.isEmpty() || normalized.startsWith("/") || normalized.contains("//")) {
			return null;
		}
		for (String component : normalized.split("/")) {
			if (component.equals(".") || component.equals("..") || component.isEmpty()) {
				return null;
			}
		}
		return normalized;
	}

	private static String normalizeRootDirectory(String rootDirectory) {
		String normalized = normalizeEntryName(rootDirectory);
		if (normalized.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Archive root directory must be a single path component: " + rootDirectory);
		}
		return normalized;
	}

	private static boolean isArchiveMetadata(String entry) {
		return entry.startsWith("__MACOSX/") || entry.startsWith("._");
	}

	private record ArchivePath(Path archive, String entryName, String visibleRoot, boolean directory) {
	}

	private record CachedArchive(List<String> entries, Map<String, String> namesByLowerCase,
			long size, long modified) {
	}

	public record ArchiveContents(List<String> entries, String rootDirectory) {
	}

}
