package bms.player.beatoraja;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigConfigurationLayoutTest {
	@Test
	void defaultsToClassicAndRoundTripsSidebar() {
		Config config = new Config();
		assertEquals(Config.ConfigurationLayout.CLASSIC, config.getConfigurationLayout());

		Json json = new Json();
		json.setIgnoreUnknownFields(true);
		Config legacy = json.fromJson(Config.class, "{}");
		assertEquals(Config.ConfigurationLayout.CLASSIC, legacy.getConfigurationLayout());

		config.setConfigurationLayout(Config.ConfigurationLayout.SIDEBAR);

		Config restored = json.fromJson(Config.class, Config.getConfigJson(config));
		assertEquals(Config.ConfigurationLayout.SIDEBAR, restored.getConfigurationLayout());
	}

	@Test
	void nullLayoutIsNormalizedToClassic() {
		Config config = new Config();
		config.setConfigurationLayout(null);

		assertEquals(Config.ConfigurationLayout.CLASSIC, config.getConfigurationLayout());
	}
}
