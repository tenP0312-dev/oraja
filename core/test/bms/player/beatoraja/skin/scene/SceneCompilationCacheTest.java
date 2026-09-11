package bms.player.beatoraja.skin.scene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import static org.junit.jupiter.api.Assertions.*;

class SceneCompilationCacheTest {
	@TempDir Path directory;
	private Path scene(String name, int duration) throws Exception {
		Path path = directory.resolve(name);
		Files.writeString(path, """
			{"formatVersion":1,"canvas":{"width":1,"height":1},
			"timebase":{"ticksPerSecond":1},"duration":%d,
			"textures":[{"id":"t","path":"t.png"}],
			"clips":[{"id":"root","duration":1,"instances":[
			{"id":"leaf","kind":"texture","ref":"t","start":0,"end":1}]}],"rootClip":"root"}
			""".formatted(duration));
		return path;
	}

	@Test void reusesUnchangedCompilationAndInvalidatesEdits() throws Exception {
		var cache = new SceneCompilationCache(4, 10000);
		Path path = scene("scene.json", 1);
		var first = cache.get(path);
		assertSame(first, cache.get(path));
		long modified = Files.getLastModifiedTime(path).toMillis();
		scene("scene.json", 2);
		Files.setLastModifiedTime(path, FileTime.fromMillis(modified + 2000));
		var second = cache.get(path);
		assertNotSame(first, second);
		assertEquals(2, second.duration);
		assertSame(second, cache.get(path));
	}

	@Test void invalidReplacementAndDeletionNeverReturnStaleScene() throws Exception {
		var cache = new SceneCompilationCache(4, 10000);
		Path path = scene("scene.json", 1);
		cache.get(path);
		Files.writeString(path, "invalid");
		assertThrows(SceneValidationException.class, () -> cache.get(path));
		Files.delete(path);
		assertThrows(SceneValidationException.class, () -> cache.get(path));
		scene("scene.json", 3);
		assertEquals(3, cache.get(path).duration);
	}

	@Test void leastRecentlyUsedEntryIsEvicted() throws Exception {
		var cache = new SceneCompilationCache(2, 10000);
		Path a = scene("a.json", 1), b = scene("b.json", 1), c = scene("c.json", 1);
		var firstA = cache.get(a);
		var firstB = cache.get(b);
		assertSame(firstA, cache.get(a));
		cache.get(c);
		assertSame(firstA, cache.get(a));
		assertNotSame(firstB, cache.get(b));
	}

	@Test void sourceByteBudgetEvictsAndOversizedScenesAreNotRetained() throws Exception {
		Path a = scene("a.json", 1), b = scene("b.json", 1);
		var cache = new SceneCompilationCache(10, Files.size(a));
		var first = cache.get(a);
		cache.get(b);
		assertNotSame(first, cache.get(a));
		var tooSmall = new SceneCompilationCache(10, Files.size(a) - 1);
		assertNotSame(tooSmall.get(a), tooSmall.get(a));
	}
}
