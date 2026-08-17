package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.Json;

class ConfigBmsirBodyDownloadTest {

	@Test
	void oldAndNewConfigurationsKeepBodyUrlDownloadsOffByDefault() {
		assertFalse(new Config().isEnableBmsirBodyDownload());
		assertFalse(new Json().fromJson(Config.class, "{}").isEnableBmsirBodyDownload());
	}

	@Test
	void optInRoundTripsThroughSystemConfigJson() {
		Config config = new Config();
		config.setEnableBmsirBodyDownload(true);

		Config restored = new Json().fromJson(Config.class, Config.getConfigJson(config));

		assertTrue(restored.isEnableBmsirBodyDownload());
	}
}
