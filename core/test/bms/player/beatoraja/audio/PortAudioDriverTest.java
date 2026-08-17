package bms.player.beatoraja.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.AudioConfig.WasapiMode;
import bms.player.beatoraja.audio.PortAudioDriver.DeviceOption;

class PortAudioDriverTest {
	private static final DeviceOption MME = new DeviceOption(
			0, "Speakers", 2, "Windows MME");
	private static final DeviceOption WASAPI = new DeviceOption(
			1, "Speakers", 13, "Windows WASAPI");

	@Test
	void persistedHostApiDistinguishesDuplicateDeviceNames() {
		DeviceOption[] devices = { MME, WASAPI };

		assertSame(WASAPI, PortAudioDriver.findDeviceOption(devices, "Speakers", 13));
		assertSame(MME, PortAudioDriver.findDeviceOption(devices, "Speakers", -1));
		assertEquals("Windows WASAPI: Speakers", WASAPI.toString());
	}

	@Test
	void wasapiModeIsSelectableOnlyForWindowsPortAudioWasapi() {
		assertTrue(PortAudioDriver.isWasapiModeSelectable(
				DriverType.PortAudio, WASAPI, true));
		assertFalse(PortAudioDriver.isWasapiModeSelectable(
				DriverType.OpenAL, WASAPI, true));
		assertFalse(PortAudioDriver.isWasapiModeSelectable(
				DriverType.PortAudio, MME, true));
		assertFalse(PortAudioDriver.isWasapiModeSelectable(
				DriverType.PortAudio, WASAPI, false));
	}

	@Test
	void exclusivePathRequiresExplicitExclusiveMode() {
		assertTrue(PortAudioDriver.shouldUseWasapiExclusive(
				WasapiMode.EXCLUSIVE, WASAPI, true));
		assertFalse(PortAudioDriver.shouldUseWasapiExclusive(
				WasapiMode.SHARED, WASAPI, true));
		assertFalse(PortAudioDriver.shouldUseWasapiExclusive(
				WasapiMode.EXCLUSIVE, MME, true));
	}
}
