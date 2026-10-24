package bms.player.beatoraja.play;

import bms.model.LongNote;
import bms.model.Mode;
import bms.model.Note;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class NantokaManiaScratchTest {
    static Stream<Arguments> scratchInputs() {
        return Stream.of(Mode.BEAT_5K, Mode.BEAT_7K, Mode.BEAT_10K, Mode.BEAT_14K)
                .flatMap(mode -> java.util.Arrays.stream(mode.scratchKey).boxed()
                        .flatMap(lane -> Stream.of(0, 1)
                                .flatMap(direction -> Stream.of(true, false)
                                        .map(releaseFirst -> Arguments.of(mode, lane, direction, releaseFirst)))));
    }

    private void reverse(NantokaManiaJudgeTest.Fixture f, int lane, int from,
                         boolean releaseFirst, long at) {
        f.engine.advanceTo(at - 1);
        if (releaseFirst) {
            f.engine.input(lane, from, false, at);
            f.engine.input(lane, 1 - from, true, at);
        } else {
            f.engine.input(lane, 1 - from, true, at);
            f.engine.input(lane, from, false, at);
        }
        f.engine.advanceTo(at);
    }

    @ParameterizedTest @MethodSource("scratchInputs")
    void earlyReverseCannotConsumeTheNextScratch(Mode mode, int lane, int direction, boolean releaseFirst) {
        for (long at : new long[]{1_800_000, 1_900_000}) {
            var f = new NantokaManiaJudgeTest.Fixture(mode);
            LongNote bss = f.hold(lane, 1_000_000, 2_000_000, LongNote.TYPE_CHARGENOTE);
            Note next = f.note(lane, 2_050_000);
            f.begin(false, 2); f.input(lane, direction, true, 1_000_000);

            reverse(f, lane, direction, releaseFirst, at);
            int endpointJudge = at == 1_800_000 ? 3 : 0;
            assertEquals(List.of(0, endpointJudge), f.grades(), "reverse at " + at);
            assertSame(bss.getPair(), f.results.get(1).note());
            assertEquals(0, next.getState());
            assertNull(f.engine.processing(lane));
            assertTrue(f.engine.holding(lane));

            reverse(f, lane, 1 - direction, releaseFirst, 2_050_000);
            assertEquals(List.of(0, endpointJudge, 0), f.grades());
            assertSame(next, f.results.get(2).note());
            assertEquals(1, next.getState());
        }
    }

    @ParameterizedTest @MethodSource("scratchInputs")
    void earlyReverseKeepsFollowingLongScratchPlayable(Mode mode, int lane, int direction, boolean releaseFirst) {
        for (int type : new int[]{LongNote.TYPE_CHARGENOTE, LongNote.TYPE_HELLCHARGENOTE}) {
            var f = new NantokaManiaJudgeTest.Fixture(mode);
            f.hold(lane, 1_000_000, 2_000_000, LongNote.TYPE_CHARGENOTE);
            LongNote next = f.hold(lane, 2_050_000, 2_500_000, type);
            f.begin(false, 2); f.input(lane, direction, true, 1_000_000);

            reverse(f, lane, direction, releaseFirst, 1_800_000);
            assertEquals(List.of(0, 3), f.grades());
            assertEquals(0, next.getState());
            assertEquals(0, next.getPair().getState());
            assertTrue(f.suppressed.isEmpty());

            reverse(f, lane, 1 - direction, releaseFirst, 2_050_000);
            assertSame(next.getPair(), f.engine.processing(lane));
            reverse(f, lane, direction, releaseFirst, 2_500_000);
            assertEquals(List.of(0, 3, 0, 0), f.grades());
            assertEquals(1, next.getPair().getState());
        }
    }

    @Test void laterTimestampAndOtherLanesStillAcceptInput() {
        var f = new NantokaManiaJudgeTest.Fixture(Mode.BEAT_14K);
        f.hold(7, 1_000_000, 2_000_000, LongNote.TYPE_CHARGENOTE);
        Note next = f.note(7, 2_000_001), otherScratch = f.note(15, 2_000_000);
        Note key = f.note(0, 2_000_000);
        f.begin(false, 2); f.input(7, 0, true, 1_000_000);
        f.input(7, 0, false, 2_000_000);
        f.input(15, 0, true, 2_000_000);
        f.input(0, 0, true, 2_000_000);
        f.input(7, 1, true, 2_000_001);
        assertEquals(List.of(0, 0, 0, 0, 0), f.grades());
        for (Note note : new Note[]{next, otherScratch, key}) assertEquals(1, note.getState());
    }

    @Test void hcnReverseRetainsHeldStateAndBodyRecovery() {
        var f = new NantokaManiaJudgeTest.Fixture(Mode.BEAT_7K);
        f.hold(7, 1_000_000, 2_000_000, LongNote.TYPE_HELLCHARGENOTE);
        Note next = f.note(7, 2_050_000);
        f.begin(false, 2); f.input(7, 0, true, 1_000_000);
        reverse(f, 7, 0, true, 1_800_000);
        int recoveryCount = f.recoveries.size();
        f.engine.advanceTo(1_900_000);
        assertEquals(List.of(0, 3), f.grades());
        assertTrue(f.engine.holding(7));
        assertTrue(f.recoveries.size() > recoveryCount);
        assertEquals(0, next.getState());
        reverse(f, 7, 1, true, 2_050_000);
        assertEquals(List.of(0, 3, 0), f.grades());
        assertEquals(1, next.getState());
    }
}
