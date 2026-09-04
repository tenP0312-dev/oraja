package bms.player.beatoraja.skin.scene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SceneResourceCacheTest {
	@TempDir
	Path temporaryDirectory;

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
		CompiledScene scene = new SceneCompiler().compile(
				new SceneDocumentLoader().load(scenePath), scenePath);

		SceneResourceCache.ResourceVersion first =
				SceneResourceCache.resourceVersion(scenePath.toRealPath(), scene);
		FileTime sceneModified = Files.getLastModifiedTime(scenePath);
		Files.write(texture, new byte[] {4, 3, 2, 1});
		Files.setLastModifiedTime(texture,
				FileTime.fromMillis(Files.getLastModifiedTime(texture).toMillis() + 2000));
		SceneResourceCache.ResourceVersion second =
				SceneResourceCache.resourceVersion(scenePath.toRealPath(), scene);

		assertEquals(sceneModified, Files.getLastModifiedTime(scenePath));
		assertNotEquals(first, second);
	}
}
