package bms.player.beatoraja.play;

import bms.model.LongNote;
import bms.model.Mode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LaneRendererHcnReleaseTest {
    private int image(NantokaManiaJudgeTest.Fixture f, int lane, LongNote note) {
        return LaneRenderer.hellChargeBodyImage(note, f.engine.processing(lane),
                f.engine.passing(lane), f.engine.holding(lane), true);
    }

    @Test void repeatedReleaseAndReentryPreservePendingEndsAcrossBothPlayers() {
        var f = new NantokaManiaJudgeTest.Fixture(Mode.BEAT_14K);
        int[] lanes = {0, 1, 6, 8, 9, 14};
        LongNote[] notes = new LongNote[lanes.length];
        for (int i = 0; i < lanes.length; i++)
            notes[i] = f.hold(lanes[i], 1_000_000, 5_000_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false);
        for (int lane : lanes) f.input(lane, lane, true, 1_000_000);
        for (int i = 0; i < lanes.length; i++) assertEquals(6, image(f, lanes[i], notes[i]));
        for (long release : new long[]{2_000_000, 3_000_000}) {
            for (int lane : lanes) f.input(lane, lane, false, release);
            f.engine.advanceTo(release + 500_000);
            for (int i = 0; i < lanes.length; i++) {
                assertSame(notes[i].getPair(), f.engine.processing(lanes[i]));
                assertEquals(9, image(f, lanes[i], notes[i]));
            }
            assertTrue(f.grades().contains(5));
            f.engine.advanceTo(release + 600_000 - 1);
            int judgments = f.results.size();
            for (int lane : lanes) f.engine.input(lane, lane, true, release + 600_000);
            assertEquals(judgments, f.results.size());
            f.engine.advanceTo(release + 600_000);
            for (int i = 0; i < lanes.length; i++) assertEquals(6, image(f, lanes[i], notes[i]));
        }
        for (int lane : lanes) f.input(lane, lane, false, 4_990_000);
        for (int i = 0; i < lanes.length; i++) {
            assertEquals(1, notes[i].getPair().getState());
            assertNull(f.engine.processing(lanes[i]));
            assertEquals(7, image(f, lanes[i], notes[i]));
        }
    }

    @Test void missedStartReentryAndAutoplayUseTheirActualEngineStates() {
        var f = new NantokaManiaJudgeTest.Fixture(Mode.BEAT_7K);
        LongNote note = f.hold(0, 1_000_000, 5_000_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false); f.engine.advanceTo(1_500_000);
        assertEquals(9, image(f, 0, note));
        f.input(0, 0, true, 2_000_000);
        assertEquals(6, image(f, 0, note));
        f.input(0, 0, false, 3_000_000);
        assertEquals(9, image(f, 0, note));
        f.engine.advanceTo(5_500_000);
        assertNull(f.engine.processing(0));

        var auto = new NantokaManiaJudgeTest.Fixture(Mode.BEAT_7K);
        LongNote autoNote = auto.hold(0, 1_000_000, 5_000_000, LongNote.TYPE_HELLCHARGENOTE);
        auto.begin(true); auto.engine.advanceTo(2_000_000);
        assertEquals(6, image(auto, 0, autoNote));
        auto.engine.advanceTo(5_500_000);
        assertEquals(1, autoNote.getPair().getState());
    }

    @Test void ordinaryModeRetainsItsProcessingPriorityAndBodyStates() {
        LongNote note = new LongNote(1), end = new LongNote(1);
        note.setPair(end);
        for (int state : new int[]{0, 1, 5}) {
            note.setState(state);
            for (boolean holding : new boolean[]{false, true}) {
                assertEquals(6, LaneRenderer.hellChargeBodyImage(note, end, note, holding, false));
                assertEquals(state == 0 ? 7 : holding ? 8 : 9,
                        LaneRenderer.hellChargeBodyImage(note, null, note, holding, false));
                assertEquals(7, LaneRenderer.hellChargeBodyImage(note, null, null, holding, false));
            }
        }
    }
}
