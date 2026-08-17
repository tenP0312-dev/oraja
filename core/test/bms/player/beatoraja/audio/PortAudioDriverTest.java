package bms.player.beatoraja.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.AudioConfig.WasapiMode;
import bms.player.beatoraja.audio.PortAudioDriver.AsioUnavailableException;
import bms.player.beatoraja.audio.PortAudioDriver.AsioUnavailableReason;
import bms.player.beatoraja.audio.PortAudioDriver.DeviceOption;

class PortAudioDriverTest {
	private static final DeviceOption MME = new DeviceOption(
			0, "Speakers", 2, "Windows MME");
	private static final DeviceOption WASAPI = new DeviceOption(
			1, "Speakers", 13, "Windows WASAPI");
	private static final DeviceOption ASIO = new DeviceOption(
			2, "Yamaha Steinberg USB ASIO", 3, "ASIO", 8);
	private static final DeviceOption ASIO_INPUT_ONLY = new DeviceOption(
			3, "ASIO Input", 3, "ASIO", 0);

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
		assertFalse(PortAudioDriver.isWasapiModeSelectable(
				DriverType.ASIO, WASAPI, true));
	}

	@Test
	void asioDriverListsOnlyAsioOutputDevices() {
		DeviceOption[] devices = { MME, WASAPI, ASIO_INPUT_ONLY, ASIO };

		assertArrayEquals(
				new DeviceOption[]{ASIO},
				PortAudioDriver.selectDeviceOptions(DriverType.ASIO, devices, true, true));
		assertSame(
				devices,
				PortAudioDriver.selectDeviceOptions(DriverType.PortAudio, devices, true, true));
		assertEquals("ASIO: Yamaha Steinberg USB ASIO", ASIO.toString());
	}

	@Test
	void asioDiscoveryDistinguishesPlatformHostApiAndDeviceFailures() {
		assertAsioFailure(
				AsioUnavailableReason.UNSUPPORTED_PLATFORM,
				() -> PortAudioDriver.selectDeviceOptions(
						DriverType.ASIO, new DeviceOption[]{ASIO}, false, true));
		assertAsioFailure(
				AsioUnavailableReason.HOST_API_UNAVAILABLE,
				() -> PortAudioDriver.selectDeviceOptions(
						DriverType.ASIO, new DeviceOption[]{ASIO}, true, false));
		assertAsioFailure(
				AsioUnavailableReason.NO_OUTPUT_DEVICE,
				() -> PortAudioDriver.selectDeviceOptions(
						DriverType.ASIO, new DeviceOption[]{ASIO_INPUT_ONLY}, true, true));
	}

	@Test
	void asioDriverRejectsADeviceFromAnotherHostApi() {
		PortAudioDriver.validateSelectedDevice(DriverType.ASIO, ASIO);
		PortAudioDriver.validateSelectedDevice(DriverType.PortAudio, WASAPI);

		assertAsioFailure(
				AsioUnavailableReason.INVALID_DEVICE,
				() -> PortAudioDriver.validateSelectedDevice(DriverType.ASIO, WASAPI));
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

	private static void assertAsioFailure(
			AsioUnavailableReason expected,
			org.junit.jupiter.api.function.Executable executable) {
		AsioUnavailableException error = assertThrows(AsioUnavailableException.class, executable);
		assertEquals(expected, error.reason());
	}
}
