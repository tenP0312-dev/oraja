package bms.player.beatoraja;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static IRConfig ir(String name, String userId) {
        IRConfig config = new IRConfig();
        config.setIrname(name);
        config.setUserid(userId);
        return config;
    }
}
