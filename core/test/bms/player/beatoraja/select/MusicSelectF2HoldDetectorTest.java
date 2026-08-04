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

    @Test
    void maniacChordFiresOnceAfterOneSecondAndRearmsAfterRelease() {
        MusicSelectInputProcessor.ChordHoldDetector detector =
                new MusicSelectInputProcessor.ChordHoldDetector();
        assertEquals(false, detector.update(true, 100L));
        assertEquals(false, detector.update(true, 1099L));
        assertEquals(true, detector.update(true, 1100L));
        assertEquals(false, detector.update(true, 2100L));
        assertEquals(false, detector.update(false, 2101L));
        assertEquals(false, detector.update(true, 2200L));
        assertEquals(true, detector.update(true, 3200L));
    }

    @Test
    void menuMovementRepeatsOnlyAfterTheInitialDelay() {
        MusicSelectInputProcessor.RepeatPressDetector detector =
                new MusicSelectInputProcessor.RepeatPressDetector();
        assertEquals(true, detector.update(true, 100L));
        assertEquals(false, detector.update(true, 449L));
        assertEquals(true, detector.update(true, 450L));
        assertEquals(false, detector.update(true, 539L));
        assertEquals(true, detector.update(true, 540L));
        assertEquals(false, detector.update(false, 541L));
        assertEquals(true, detector.update(true, 600L));
    }

    @Test
    void selectAndBackActionsFireOncePerPress() {
        MusicSelectInputProcessor.PressEdgeDetector detector =
                new MusicSelectInputProcessor.PressEdgeDetector();
        assertEquals(true, detector.update(true));
        assertEquals(false, detector.update(true));
        assertEquals(false, detector.update(false));
        assertEquals(true, detector.update(true));
    }
}
