package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import bms.player.beatoraja.AudioConfig.WasapiMode;

class AudioConfigWasapiTest {
	@Test
	void oldConfigurationsDefaultToSharedModeAndUnspecifiedHostApi() {
		AudioConfig config = new AudioConfig();

		assertEquals(WasapiMode.SHARED, config.getWasapiMode());
		assertEquals(-1, config.getDriverHostApi());
	}

	@Test
	void validationRepairsMissingModeAndInvalidHostApi() {
		AudioConfig config = new AudioConfig();
		config.setWasapiMode(null);
		config.setDriverHostApi(99);

		config.validate();

		assertEquals(WasapiMode.SHARED, config.getWasapiMode());
		assertEquals(-1, config.getDriverHostApi());
	}
}
