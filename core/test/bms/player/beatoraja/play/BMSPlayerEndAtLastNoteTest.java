package bms.player.beatoraja.play;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bms.player.beatoraja.BMSPlayerMode;
import org.junit.jupiter.api.Test;

class BMSPlayerEndAtLastNoteTest {
    @Test
    void disabledOptionNeverEndsEarly() {
        assertFalse(BMSPlayer.allNotesJudged(false, BMSPlayerMode.Mode.PLAY, 500, 500));
    }

    @Test
    void endsOnlyOnceEveryNoteIncludingLnEndsIsJudged() {
        assertFalse(BMSPlayer.allNotesJudged(true, BMSPlayerMode.Mode.PLAY, 0, 500));
        // The last LN is still held: its end has not been judged yet.
        assertFalse(BMSPlayer.allNotesJudged(true, BMSPlayerMode.Mode.PLAY, 499, 500));
        assertTrue(BMSPlayer.allNotesJudged(true, BMSPlayerMode.Mode.PLAY, 500, 500));
        assertTrue(BMSPlayer.allNotesJudged(true, BMSPlayerMode.Mode.REPLAY, 500, 500));
    }

    @Test
    void autoplayPracticeAndEmptyChartsKeepNormalEndTiming() {
        assertFalse(BMSPlayer.allNotesJudged(true, BMSPlayerMode.Mode.AUTOPLAY, 500, 500));
        assertFalse(BMSPlayer.allNotesJudged(true, BMSPlayerMode.Mode.PRACTICE, 500, 500));
        assertFalse(BMSPlayer.allNotesJudged(true, BMSPlayerMode.Mode.PLAY, 0, 0));
    }

    @Test
    void finishesShortlyAfterTheLastJudgmentInsteadOfAfterTheFiveSecondMargin() {
        long lastNote = 120_000;
        long judgedAt = lastNote + 600;
        assertFalse(BMSPlayer.endAtLastNoteSettled(judgedAt, Long.MIN_VALUE, lastNote));
        // Same frame as the last judgment: the gauge may not be updated yet.
        assertFalse(BMSPlayer.endAtLastNoteSettled(judgedAt, judgedAt, lastNote));
        assertTrue(BMSPlayer.endAtLastNoteSettled(judgedAt + BMSPlayer.END_AT_LAST_NOTE_SETTLE, judgedAt, lastNote));
        assertTrue(judgedAt + BMSPlayer.END_AT_LAST_NOTE_SETTLE < lastNote + BMSPlayer.TIME_MARGIN);
    }

    @Test
    void waitsUntilTheLastChartObjectIncludingMinesIsPast() {
        long lastObject = 120_000;
        long judgedAt = lastObject - 2_000;
        assertFalse(BMSPlayer.endAtLastNoteSettled(lastObject, judgedAt, lastObject));
        assertTrue(BMSPlayer.endAtLastNoteSettled(
                lastObject + BMSPlayer.END_AT_LAST_NOTE_OBJECT_MARGIN, judgedAt, lastObject));
    }
}
