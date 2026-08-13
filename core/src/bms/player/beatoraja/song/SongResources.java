package bms.player.beatoraja.song;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import bms.player.beatoraja.song.archive.SongArchives;

/** Factory and single-file materialization cache for {@link SongResource}. */
public final class SongResources {

	private static final int MAX_MATERIALIZED_ENTRIES = 128;
	private static final long MAX_MATERIALIZED_BYTES = 2L * 1024 * 1024 * 1024;
	private static final String TEMP_FILE_PREFIX = "beatoraja-song-resource-";
	private static final String PROCESS_TEMP_FILE_PREFIX = TEMP_FILE_PREFIX + ProcessHandle.current().pid() + "-";
	private static final Duration STALE_TEMP_FILE_AGE = Duration.ofHours(24);
	private static final Map<String, MaterializedEntry> MATERIALIZED = new LinkedHashMap<>(16, 0.75f, true);

	static {
		cleanupStaleMaterializedFiles(Path.of(System.getProperty("java.io.tmpdir")), Instant.now());
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			synchronized (MATERIALIZED) {
				MATERIALIZED.values().stream().map(MaterializedEntry::path).forEach(SongResources::deleteQuietly);
			}
		}, "song resource cleanup"));
	}

	private SongResources() {
	}

	public static SongResource fromPath(Path path) {
		return SongArchives.resource(path);
	}

	public static SongResource local(Path path) {
		return new LocalSongResource(path.normalize());
	}

	public static synchronized Path materialize(SongResource resource) throws IOException {
		Optional<Path> localPath = resource.localPath();
		if (localPath.isPresent()) {
			return localPath.get();
		}
		String cacheKey = resource.cacheKey();
		MaterializedEntry cached = MATERIALIZED.get(cacheKey);
		if (cached != null && Files.isRegularFile(cached.path())) {
			return cached.path();
		}
		String suffix = extensionSuffix(resource.name());
		Path target = Files.createTempFile(PROCESS_TEMP_FILE_PREFIX, suffix);
		try (InputStream input = resource.openStream()) {
			Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(target);
			throw e;
		}
		long targetSize;
		try {
			targetSize = Files.size(target);
		} catch (IOException e) {
			Files.deleteIfExists(target);
			throw e;
		}
		MaterializedEntry replaced = MATERIALIZED.put(cacheKey, new MaterializedEntry(target, targetSize));
		if (replaced != null) {
			deleteQuietly(replaced.path());
		}
		trimMaterializedEntries();
		return target;
	}

	private static void trimMaterializedEntries() {
		long totalSize = MATERIALIZED.values().stream().mapToLong(MaterializedEntry::size).sum();
		Iterator<MaterializedEntry> entries = MATERIALIZED.values().iterator();
		while ((MATERIALIZED.size() > MAX_MATERIALIZED_ENTRIES || totalSize > MAX_MATERIALIZED_BYTES)
				&& entries.hasNext()) {
			MaterializedEntry oldest = entries.next();
			entries.remove();
			totalSize -= oldest.size();
			deleteQuietly(oldest.path());
		}
	}

	static int cleanupStaleMaterializedFiles(Path temporaryDirectory, Instant now) {
		if (!Files.isDirectory(temporaryDirectory)) {
			return 0;
		}
		Instant cutoff = now.minus(STALE_TEMP_FILE_AGE);
		int removed = 0;
		try (var files = Files.newDirectoryStream(temporaryDirectory, TEMP_FILE_PREFIX + "*")) {
			for (Path file : files) {
				try {
					FileTime modified = Files.getLastModifiedTime(file);
					if (Files.isRegularFile(file) && !belongsToLiveProcess(file)
							&& modified.toInstant().isBefore(cutoff)
							&& Files.deleteIfExists(file)) {
						removed++;
					}
				} catch (IOException ignored) {
				}
			}
		} catch (IOException ignored) {
		}
		return removed;
	}

	private static boolean belongsToLiveProcess(Path file) {
		String name = file.getFileName().toString();
		int pidStart = TEMP_FILE_PREFIX.length();
		int pidEnd = name.indexOf('-', pidStart);
		if (pidEnd <= pidStart) {
			return false;
		}
		try {
			long pid = Long.parseLong(name.substring(pidStart, pidEnd));
			return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
		} catch (NumberFormatException | SecurityException e) {
			return false;
		}
	}

	private static void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
		}
	}

	private static String extensionSuffix(String name) {
		int index = name.lastIndexOf('.');
		return index >= 0 && index < name.length() - 1 ? name.substring(index) : ".tmp";
	}

	private record MaterializedEntry(Path path, long size) {
	}

	private record LocalSongResource(Path path) implements SongResource {
		@Override
		public SongResource parent() {
			Path parent = path.getParent();
			return parent != null ? local(parent) : this;
		}

		@Override
		public SongResource resolve(String relativePath) {
			return local(path.resolve(relativePath));
		}

		@Override
		public String name() {
			Path filename = path.getFileName();
			return filename != null ? filename.toString() : path.toString();
		}

		@Override
		public String displayPath() {
			return path.toString();
		}

		@Override
		public String cacheKey() {
			try {
				return path.toAbsolutePath() + ":" + Files.size(path) + ":" + Files.getLastModifiedTime(path);
			} catch (IOException e) {
				return path.toAbsolutePath().toString();
			}
		}

		@Override
		public boolean exists() {
			return Files.exists(path);
		}

		@Override
		public boolean isDirectory() {
			return Files.isDirectory(path);
		}

		@Override
		public long size() throws IOException {
			return Files.size(path);
		}

		@Override
		public InputStream openStream() throws IOException {
			return Files.newInputStream(path);
		}

		@Override
		public List<SongResource> list() throws IOException {
			try (var paths = Files.list(path)) {
				return paths.map(SongResources::local).toList();
			}
		}

		@Override
		public Optional<Path> localPath() {
			return Optional.of(path);
		}
	}
}
