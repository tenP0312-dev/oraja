package bms.player.beatoraja.skin.scene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;

/** CPU-only LRU; no GPU resources survive the last resource handle. */
final class SceneCompilationCache {
	private final int maximumEntries;
	// Source byte budget, not an estimate of the compiled object's heap size.
	private final long maximumSourceBytes;
	private long sourceBytes;
	private final LinkedHashMap<Path, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

	SceneCompilationCache(int maximumEntries, long maximumSourceBytes) {
		this.maximumEntries = maximumEntries;
		this.maximumSourceBytes = maximumSourceBytes;
	}

	synchronized CompiledScene get(Path source) throws SceneValidationException {
		try {
			Path path = source.toRealPath();
			BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
			Entry previous = entries.get(path);
			if (previous != null && previous.size == attributes.size()
					&& previous.modified.equals(attributes.lastModifiedTime())) {
				return previous.scene;
			}
			if (previous != null) {
				entries.remove(path);
				sourceBytes -= previous.size;
			}
			CompiledScene scene = new SceneCompiler().compile(new SceneDocumentLoader().load(path), path);
			BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class);
			// Never cache a document that changed while it was being read.
			if (maximumEntries > 0 && after.size() <= maximumSourceBytes
					&& attributes.size() == after.size()
					&& attributes.lastModifiedTime().equals(after.lastModifiedTime())) {
				entries.put(path, new Entry(after.size(), after.lastModifiedTime(), scene));
				sourceBytes += after.size();
				while (entries.size() > maximumEntries || sourceBytes > maximumSourceBytes) {
					var oldest = entries.entrySet().iterator();
					sourceBytes -= oldest.next().getValue().size;
					oldest.remove();
				}
			}
			return scene;
		} catch (IOException error) {
			throw new SceneValidationException("Cannot read scene: " + source, error);
		}
	}

	private record Entry(long size, FileTime modified, CompiledScene scene) { }
}
