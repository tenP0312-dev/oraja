package bms.player.beatoraja.skin.scene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneDocumentLoaderTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void rejectsUnknownDocumentFields() throws Exception {
		Path scene = temporaryDirectory.resolve("unknown.scene.json");
		Files.writeString(scene, """
				{
				  "formatVersion": 1,
				  "canvas": {"width": 1, "height": 1},
				  "timebase": {"ticksPerSecond": 1},
				  "duration": 1,
				  "textures": [],
				  "meshes": [],
				  "clips": [],
				  "rootClip": "root",
				  "unexpected": true
				}
				""");

		assertThrows(SceneValidationException.class,
				() -> new SceneDocumentLoader().load(scene));
	}

	@Test
	void compilerRejectsUnknownFormatVersion() {
		SceneDocument document = SceneCompilerTest.fixture();
		document.formatVersion = 2;

		assertThrows(SceneValidationException.class,
				() -> new SceneCompiler().compile(document,
						temporaryDirectory.resolve("version.scene.json")));
	}
}
