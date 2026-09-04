package bms.player.beatoraja.skin.scene;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.SerializationException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads a neutral scene JSON document without creating runtime resources. */
public final class SceneDocumentLoader {
	public SceneDocument load(Path path) throws SceneValidationException {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Json json = new Json();
			json.setIgnoreUnknownFields(false);
			return json.fromJson(SceneDocument.class, reader);
		} catch (IOException | SerializationException error) {
			throw new SceneValidationException("Failed to read scene " + path + ": "
					+ error.getMessage(), error);
		}
	}
}
