package bms.player.beatoraja.select;

import org.junit.jupiter.api.Test;

import static bms.player.beatoraja.select.MusicSelectInputProcessor.F2HoldDetector.Action.LONG_PRESS;
import static bms.player.beatoraja.select.MusicSelectInputProcessor.F2HoldDetector.Action.NONE;
import static bms.player.beatoraja.select.MusicSelectInputProcessor.F2HoldDetector.Action.SHORT_PRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicSelectF2HoldDetectorTest {
    @Test
    void shortPressUpdatesOnlyWhenReleased() {
        MusicSelectInputProcessor.F2HoldDetector detector =
                new MusicSelectInputProcessor.F2HoldDetector();
        assertEquals(NONE, detector.update(true, 100L));
        assertEquals(NONE, detector.update(true, 900L));
        assertEquals(SHORT_PRESS, detector.update(false, 901L));
    }

    @Test
    void longPressFiresOnceAndSuppressesShortPress() {
        MusicSelectInputProcessor.F2HoldDetector detector =
                new MusicSelectInputProcessor.F2HoldDetector();
        assertEquals(NONE, detector.update(true, 100L));
        assertEquals(LONG_PRESS, detector.update(true, 1100L));
        assertEquals(NONE, detector.update(true, 2100L));
        assertEquals(NONE, detector.update(false, 2101L));
    }
}
