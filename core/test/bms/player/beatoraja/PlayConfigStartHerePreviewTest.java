package bms.player.beatoraja;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayConfigStartHerePreviewTest {
    @Test
    void previewDefaultsAndValidationAreBackwardCompatible() {
        PlayConfig config = new PlayConfig();
        assertTrue(config.isStartHerePreviewEnabled());
        assertEquals(2, config.getStartHerePreviewMeasures());
        assertEquals(256, config.getStartHerePreviewMaxNotes());

        config.setStartHerePreviewMeasures(-100);
        config.setStartHerePreviewMaxNotes(10_000);
        config.validate();

        assertEquals(PlayConfig.START_HERE_PREVIEW_MEASURES_MIN,
                config.getStartHerePreviewMeasures());
        assertEquals(PlayConfig.START_HERE_PREVIEW_MAX_NOTES_MAX,
                config.getStartHerePreviewMaxNotes());
    }

    @Test
    void bmsirStartupTogglesApplyToEverySupportedPlayMode() {
        PlayerConfig player = new PlayerConfig();
        assertTrue(player.isBmsirOneBassEnabled());
        assertTrue(player.isBmsirStartHerePreviewEnabled());

        player.setBmsirOneBassEnabled(false);
        player.setBmsirStartHerePreviewEnabled(false);

        assertFalse(player.isBmsirOneBassEnabled());
        assertFalse(player.isBmsirStartHerePreviewEnabled());
        for (bms.model.Mode mode : new bms.model.Mode[]{
                bms.model.Mode.BEAT_5K,
                bms.model.Mode.BEAT_7K,
                bms.model.Mode.BEAT_10K,
                bms.model.Mode.BEAT_14K,
                bms.model.Mode.POPN_9K,
                bms.model.Mode.KEYBOARD_24K,
                bms.model.Mode.KEYBOARD_24K_DOUBLE
        }) {
            assertFalse(player.getPlayConfig(mode).getPlayconfig()
                    .isStartHerePreviewEnabled());
        }
    }
}
