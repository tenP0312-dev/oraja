package bms.player.beatoraja.skin.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.IntBuffer;
import java.lang.reflect.Proxy;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class SceneResourceCacheTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void scenesShareTextureUntilTheLastOwnerCloses() throws Exception {
		GdxNativesLoader.load();
		GL20 oldGl = Gdx.gl, oldGl20 = Gdx.gl20;
		com.badlogic.gdx.Files oldFiles = Gdx.files;
		Graphics oldGraphics = Gdx.graphics;
		var nextId = new AtomicInteger();
		var deletes = new AtomicInteger();
		GL20 gl = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class}, (proxy, method, args) -> {
			switch (method.getName()) {
				case "glGenTexture": return nextId.incrementAndGet();
				case "glDeleteTexture": deletes.incrementAndGet(); return null;
				case "glGetIntegerv": ((IntBuffer) args[1]).put(0, 4096); return null;
				case "hashCode": return System.identityHashCode(proxy);
				case "equals": return proxy == args[0];
				default: return method.getReturnType() == boolean.class ? false
						: method.getReturnType() == int.class ? 0 : null;
			}
		});
		Gdx.gl = Gdx.gl20 = gl;
		Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
				(proxy, method, args) -> method.getReturnType() == boolean.class ? false
						: method.getReturnType() == int.class ? 0 : null);
		Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(com.badlogic.gdx.Files.class.getClassLoader(), new Class<?>[]{com.badlogic.gdx.Files.class}, (proxy, method, args) -> {
			if (method.getName().equals("absolute")) return new FileHandle((String) args[0]);
			throw new UnsupportedOperationException(method.getName());
		});
		try {
			Path png = temporaryDirectory.resolve("atlas.png");
			ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", png.toFile());
			String scene = """
				{"formatVersion":1,"canvas":{"width":1,"height":1},"timebase":{"ticksPerSecond":1},"duration":1,
				"textures":[{"id":"texture","path":"atlas.png"}],"meshes":[],"clips":[{"id":"root","duration":1,
				"instances":[{"id":"leaf","kind":"texture","ref":"texture","start":0,"end":1}]}],"rootClip":"root"}
				""";
			Path firstScene = temporaryDirectory.resolve("first.json"), secondScene = temporaryDirectory.resolve("second.json");
			java.nio.file.Files.writeString(firstScene, scene);
			java.nio.file.Files.writeString(secondScene, scene);
			try (var first = SceneResourceCache.acquire(firstScene); var second = SceneResourceCache.acquire(secondScene)) {
				assertSame(first.texture(0), second.texture(0));
				assertEquals(1, nextId.get());
				java.nio.file.Files.setLastModifiedTime(png,
						FileTime.fromMillis(java.nio.file.Files.getLastModifiedTime(png).toMillis() + 2000));
				try (var updated = SceneResourceCache.acquire(secondScene)) {
					assertNotSame(first.texture(0), updated.texture(0));
					assertEquals(2, nextId.get());
				}
				assertEquals(1, deletes.get());
				first.close();
				assertEquals(1, deletes.get());
			}
			assertEquals(2, deletes.get());
		} finally {
			Gdx.gl = oldGl; Gdx.gl20 = oldGl20; Gdx.files = oldFiles; Gdx.graphics = oldGraphics;
		}
	}

	@Test
	void textureLeasesShareOnlyIdenticalVersionsAndSamplerSettings() {
		var disposed = new ArrayList<Object>();
		var pool = new SceneTexturePool<SceneResourceCache.TextureKey, Object>(disposed::add);
		var stamp = new SceneResourceCache.ResourceStamp(Path.of("atlas.png"), 10, FileTime.fromMillis(1));
		var key = new SceneResourceCache.TextureKey(stamp, true, false, false, null);
		var allocations = new AtomicInteger();
		java.util.function.Supplier<Object> factory = () -> { allocations.incrementAndGet(); return new Object(); };
		var first = pool.acquire(key, factory);
		var second = pool.acquire(key, factory);
		assertSame(first.value(), second.value());
		assertEquals(1, allocations.get());
		var changedFilter = pool.acquire(new SceneResourceCache.TextureKey(stamp, false, false, false, null), factory);
		var changedWrap = pool.acquire(new SceneResourceCache.TextureKey(stamp, true, true, false, null), factory);
		var changedVersion = pool.acquire(new SceneResourceCache.TextureKey(
				new SceneResourceCache.ResourceStamp(stamp.path(), 11, stamp.modified()), true, false, false, null), factory);
		assertNotSame(first.value(), changedFilter.value());
		assertNotSame(first.value(), changedWrap.value());
		assertNotSame(first.value(), changedVersion.value());
		first.close(); first.close();
		assertEquals(List.of(), disposed);
		Object shared = second.value();
		second.close();
		assertEquals(List.of(shared), disposed);
		changedFilter.close(); changedWrap.close(); changedVersion.close();
		assertEquals(4, disposed.size());
		var reloaded = pool.acquire(key, factory);
		assertNotSame(shared, reloaded.value());
		reloaded.close();
		assertEquals(5, allocations.get());
	}

	@Test
	void failedTextureAllocationDoesNotRemainInPool() {
		var disposed = new ArrayList<Object>();
		var pool = new SceneTexturePool<String, Object>(disposed::add);
		assertThrows(IllegalStateException.class, () -> pool.acquire("atlas", () -> { throw new IllegalStateException(); }));
		var lease = pool.acquire("atlas", Object::new);
		lease.close();
		assertEquals(1, disposed.size());
	}

	@Test
	void textureOnlyUpdateChangesResourceVersion() throws Exception {
		Path texture = temporaryDirectory.resolve("texture.png");
		Files.write(texture, new byte[] {1, 2, 3, 4});
		Path scenePath = temporaryDirectory.resolve("scene.json");
		Files.writeString(scenePath, """
				{
				  "formatVersion": 1,
				  "canvas": {"width": 1, "height": 1},
				  "timebase": {"ticksPerSecond": 1},
				  "duration": 1,
				  "textures": [{"id": "texture", "path": "texture.png"}],
				  "meshes": [],
				  "clips": [{"id": "root", "duration": 1, "instances": [
				    {"id": "leaf", "kind": "texture", "ref": "texture", "start": 0, "end": 1}
				  ]}],
				  "rootClip": "root"
				}
				""");
		SceneCompilationCache cache = new SceneCompilationCache(4, 10000);
		CompiledScene scene = cache.get(scenePath);

		SceneResourceCache.ResourceVersion first =
				SceneResourceCache.resourceVersion(scenePath.toRealPath(), scene);
		SceneResourceCache.verifyTextureStamp(texture.toRealPath(), first.textures().get(0));
		FileTime sceneModified = Files.getLastModifiedTime(scenePath);
		Files.write(texture, new byte[] {4, 3, 2, 1});
		Files.setLastModifiedTime(texture,
				FileTime.fromMillis(Files.getLastModifiedTime(texture).toMillis() + 2000));
		assertThrows(SceneValidationException.class,
				() -> SceneResourceCache.verifyTextureStamp(texture.toRealPath(), first.textures().get(0)));
		SceneResourceCache.ResourceVersion second =
				SceneResourceCache.resourceVersion(scenePath.toRealPath(), cache.get(scenePath));

		assertEquals(sceneModified, Files.getLastModifiedTime(scenePath));
		assertNotEquals(first, second);
		assertSame(scene, cache.get(scenePath));
		Files.delete(texture);
		assertThrows(SceneValidationException.class,
				() -> SceneResourceCache.resourceVersion(scenePath.toRealPath(), cache.get(scenePath)));
	}
}
