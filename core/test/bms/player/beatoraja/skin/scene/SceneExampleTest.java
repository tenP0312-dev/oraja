package bms.player.beatoraja.skin.scene;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneExampleTest {
	@Test
	void selfAuthoredProjectiveExampleLoadsAndCompiles() throws Exception {
		Path scenePath = Path.of("..", "examples", "skin-scene",
				"projective-grid.scene.json").toAbsolutePath().normalize();
		assertTrue(Files.isRegularFile(scenePath.resolveSibling("checker.png")));
		SceneDocument document = new SceneDocumentLoader().load(scenePath);
		CompiledScene scene = new SceneCompiler().compile(document, scenePath);
		assertEquals(1, scene.textures.length);
		assertEquals(1, scene.meshes.length);
		assertEquals(3, scene.clips.length);
		assertEquals(2, scene.maxMaskDepth);
	}
}
