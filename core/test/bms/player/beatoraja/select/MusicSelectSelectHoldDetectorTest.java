package bms.player.beatoraja.select;

import org.junit.jupiter.api.Test;

import static bms.player.beatoraja.select.MusicSelectInputProcessor.SelectHoldDetector.Action.LONG_PRESS;
import static bms.player.beatoraja.select.MusicSelectInputProcessor.SelectHoldDetector.Action.NONE;
import static bms.player.beatoraja.select.MusicSelectInputProcessor.SelectHoldDetector.Action.SHORT_PRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSelectSelectHoldDetectorTest {
    @Test
    void releaseBefore350MillisecondsIsAShortPress() {
        MusicSelectInputProcessor.SelectHoldDetector detector =
                new MusicSelectInputProcessor.SelectHoldDetector();

        assertEquals(NONE, detector.update(true, 1_000L, false));
        assertEquals(NONE, detector.update(true, 1_349L, false));
        assertEquals(SHORT_PRESS, detector.update(false, 1_349L, false));
        assertFalse(detector.isLongPressActive());
    }

    @Test
    void reaching350MillisecondsOpensOptionsOnceAndSuppressesShortPress() {
        MusicSelectInputProcessor.SelectHoldDetector detector =
                new MusicSelectInputProcessor.SelectHoldDetector();

        assertEquals(NONE, detector.update(true, 1_000L, false));
        assertEquals(LONG_PRESS, detector.update(true, 1_350L, false));
        assertTrue(detector.isLongPressActive());
        assertEquals(NONE, detector.update(true, 2_000L, false));
        assertEquals(NONE, detector.update(false, 2_001L, false));
        assertFalse(detector.isLongPressActive());
    }

    @Test
    void noShortActionOpensOptionsOnTheFirstPressedFrame() {
        MusicSelectInputProcessor.SelectHoldDetector detector =
                new MusicSelectInputProcessor.SelectHoldDetector();

        assertEquals(LONG_PRESS, detector.update(true, 1_000L, true));
        assertTrue(detector.isLongPressActive());
        assertEquals(NONE, detector.update(true, 1_001L, true));
        assertEquals(NONE, detector.update(false, 1_002L, true));
        assertFalse(detector.isLongPressActive());
    }

    @Test
    void startSelectCancellationNeverEmitsAShortPress() {
        MusicSelectInputProcessor.SelectHoldDetector detector =
                new MusicSelectInputProcessor.SelectHoldDetector();

        assertEquals(NONE, detector.update(true, 1_000L, false));
        detector.cancel();
        assertEquals(NONE, detector.update(false, 1_100L, true));
    }

    @Test
    void aLateReleaseIsNeverMisclassifiedWhenTheThresholdFrameWasSkipped() {
        MusicSelectInputProcessor.SelectHoldDetector detector =
                new MusicSelectInputProcessor.SelectHoldDetector();

        assertEquals(NONE, detector.update(true, 1_000L, false));
        assertEquals(NONE, detector.update(false, 1_350L, false));
    }
}
