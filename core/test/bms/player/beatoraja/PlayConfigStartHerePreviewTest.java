package bms.player.beatoraja;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
