package bms.player.beatoraja;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBackgroundControllerInputTest {
	@Test
	void defaultsOffAndRoundTripsWhenEnabled() {
		Config config = new Config();
		assertFalse(config.isBackgroundControllerInputEnabled());

		config.setBackgroundControllerInputEnabled(true);

		Json json = new Json();
		json.setIgnoreUnknownFields(true);
		Config restored = json.fromJson(Config.class, Config.getConfigJson(config));
		assertTrue(restored.isBackgroundControllerInputEnabled());
	}
}
