package bms.player.beatoraja.song;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import bms.player.beatoraja.song.archive.SongArchives;

/** Factory and single-file materialization cache for {@link SongResource}. */
public final class SongResources {

	private static final int MAX_MATERIALIZED_ENTRIES = 128;
	private static final Map<String, Path> MATERIALIZED = new LinkedHashMap<>(16, 0.75f, true);

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			synchronized (MATERIALIZED) {
				MATERIALIZED.values().forEach(SongResources::deleteQuietly);
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
		Path cached = MATERIALIZED.get(resource.cacheKey());
		if (cached != null && Files.isRegularFile(cached)) {
			return cached;
		}
		String suffix = extensionSuffix(resource.name());
		Path target = Files.createTempFile("beatoraja-song-resource-", suffix);
		try (InputStream input = resource.openStream()) {
			Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(target);
			throw e;
		}
		Path replaced = MATERIALIZED.put(resource.cacheKey(), target);
		deleteQuietly(replaced);
		trimMaterializedEntries();
		return target;
	}

	private static void trimMaterializedEntries() {
		Iterator<Path> paths = MATERIALIZED.values().iterator();
		while (MATERIALIZED.size() > MAX_MATERIALIZED_ENTRIES && paths.hasNext()) {
			Path oldest = paths.next();
			paths.remove();
			deleteQuietly(oldest);
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
