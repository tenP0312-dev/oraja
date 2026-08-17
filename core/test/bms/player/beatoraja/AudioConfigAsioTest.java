package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.Json;

import bms.player.beatoraja.AudioConfig.DriverType;

class AudioConfigAsioTest {
	@Test
	void asioDriverAndHostApiRoundTripThroughTheExistingConfig() {
		Config config = new Config();
		AudioConfig audio = new AudioConfig();
		audio.setDriver(DriverType.ASIO);
		audio.setDriverName("Yamaha Steinberg USB ASIO");
		audio.setDriverHostApi(3);
		config.setAudioConfig(audio);

		Json json = new Json();
		json.setIgnoreUnknownFields(true);
		Config restored = json.fromJson(Config.class, Config.getConfigJson(config));

		assertEquals(DriverType.ASIO, restored.getAudioConfig().getDriver());
		assertEquals("Yamaha Steinberg USB ASIO", restored.getAudioConfig().getDriverName());
		assertEquals(3, restored.getAudioConfig().getDriverHostApi());
	}
}
