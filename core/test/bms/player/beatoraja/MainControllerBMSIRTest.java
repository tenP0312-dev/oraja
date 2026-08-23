package bms.player.beatoraja;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.MainController.CursorMode;

class MainControllerBMSIRTest {

    @Test
    void duplicateIrAccountIsLoggedInOnlyOnceAtStartup() {
        IRConfig first = ir("BMS-IR", "190001");
        IRConfig duplicate = ir("BMS-IR", "190001");
        IRConfig other = ir("BMS-IR", "190002");

        assertArrayEquals(
                new IRConfig[]{first, other},
                MainController.uniqueIrConfigs(
                        new IRConfig[]{first, null, duplicate, other}
                )
        );
    }

	@Test
	void fullscreenReturnsToThePreviousWindowedMode() {
		assertEquals(
				Config.DisplayMode.WINDOW,
				MainController.rememberedWindowedMode(Config.DisplayMode.WINDOW)
		);
		assertEquals(
				Config.DisplayMode.BORDERLESS,
				MainController.rememberedWindowedMode(Config.DisplayMode.BORDERLESS)
		);
		assertEquals(
				Config.DisplayMode.WINDOW,
				MainController.rememberedWindowedMode(Config.DisplayMode.FULLSCREEN)
		);
	}

	@Test
	void asioStartupFailureDoesNotSilentlyRewriteTheSelectedDriver() {
		assertTrue(MainController.shouldFallbackToOpenAlOnPortAudioFailure(DriverType.PortAudio));
		assertFalse(MainController.shouldFallbackToOpenAlOnPortAudioFailure(DriverType.ASIO));
	}

	@Test
	void bodyOnlyDownloadsStillReconcileTaskProgress() {
		Config config = new Config();
		config.setEnableHttp(false);
		config.setEnableBmsirBodyDownload(false);
		assertFalse(MainController.shouldUpdateDownloadTaskState(config));

		config.setEnableBmsirBodyDownload(true);
		assertTrue(MainController.shouldUpdateDownloadTaskState(config));

		config.setEnableBmsirBodyDownload(false);
		config.setEnableHttp(true);
		assertTrue(MainController.shouldUpdateDownloadTaskState(config));
	}

	@Test
	void arenaCursorHideNeverCapturesTheOsPointer() {
		assertEquals(
				CursorMode.HIDDEN,
				MainController.cursorMode(false, true, true, false, true)
		);
		assertEquals(
				CursorMode.VISIBLE,
				MainController.cursorMode(false, true, true, false, false)
		);
		assertEquals(
				CursorMode.VISIBLE,
				MainController.cursorMode(false, true, true, true, true)
		);
	}

	@Test
	void cursorVisibilityReturnsForMenusAndOutsidePlay() {
		assertEquals(
				CursorMode.VISIBLE,
				MainController.cursorMode(true, true, true, false, true)
		);
		assertEquals(
				CursorMode.VISIBLE,
				MainController.cursorMode(false, false, true, false, true)
		);
	}

	@Test
	void ordinaryPlayKeepsItsExistingInactivityCapture() {
		assertEquals(
				CursorMode.CAPTURED,
				MainController.cursorMode(false, true, false, false, true)
		);
		assertEquals(
				CursorMode.VISIBLE,
				MainController.cursorMode(false, true, false, false, false)
		);
	}

    private static IRConfig ir(String name, String userId) {
        IRConfig config = new IRConfig();
        config.setIrname(name);
        config.setUserid(userId);
        return config;
    }
}
