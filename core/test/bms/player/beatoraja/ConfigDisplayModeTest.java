package bms.player.beatoraja;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigDisplayModeTest {

	@Test
	void fullscreenRoundTripPersistsBorderlessReturnMode() {
		Config config = new Config();
		config.setDisplaymode(Config.DisplayMode.BORDERLESS);
		config.setDisplaymode(Config.DisplayMode.FULLSCREEN);

		Config restored = restore(config);

		assertEquals(Config.DisplayMode.FULLSCREEN, restored.getDisplaymode());
		assertEquals(Config.DisplayMode.BORDERLESS, restored.getLastWindowedDisplayMode());
	}

	@Test
	void fullscreenRoundTripPersistsWindowReturnMode() {
		Config config = new Config();
		config.setDisplaymode(Config.DisplayMode.WINDOW);
		config.setDisplaymode(Config.DisplayMode.FULLSCREEN);

		Config restored = restore(config);

		assertEquals(Config.DisplayMode.FULLSCREEN, restored.getDisplaymode());
		assertEquals(Config.DisplayMode.WINDOW, restored.getLastWindowedDisplayMode());
	}

	@Test
	void legacyBorderlessConfigUsesBorderlessAsItsReturnMode() {
		Config restored = fromJson("{\"displaymode\":\"BORDERLESS\"}");

		assertEquals(Config.DisplayMode.BORDERLESS, restored.getLastWindowedDisplayMode());
	}

	@Test
	void legacyFullscreenConfigFallsBackToWindowReturnMode() {
		Config restored = fromJson("{\"displaymode\":\"FULLSCREEN\"}");

		assertEquals(Config.DisplayMode.WINDOW, restored.getLastWindowedDisplayMode());
	}

	@Test
	void invalidFullscreenReturnModeIsNormalizedToWindow() {
		Config restored = fromJson(
				"{\"displaymode\":\"FULLSCREEN\",\"lastWindowedDisplayMode\":\"FULLSCREEN\"}"
		);

		assertEquals(Config.DisplayMode.WINDOW, restored.getLastWindowedDisplayMode());
	}

	private static Config restore(Config config) {
		return fromJson(Config.getConfigJson(config));
	}

	private static Config fromJson(String jsonText) {
		Json json = new Json();
		json.setIgnoreUnknownFields(true);
		Config restored = json.fromJson(Config.class, jsonText);
		restored.validate();
		return restored;
	}
}
