package bms.player.beatoraja.play;

import bms.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NantokaManiaIidx33Test {
    private NantokaManiaJudgeTest.Fixture fixture(Mode mode) {
        return new NantokaManiaJudgeTest.Fixture(mode);
    }

    @Test void extractedFloatBoundariesKeepTheirOpenAndClosedSidesForAllProfiles() {
        double[][] thresholds = {
                {-249.75, -116.41666412353516, -33.08333206176758, -16.41666603088379,
                        .25, 16.91666603088379, 33.58333206176758, 116.91666412353516, 250.25},
                {-283.0833435058594, -149.75, -66.41666412353516, -16.41666603088379,
                        .25, 16.91666603088379, 66.91666412353516, 150.25, 283.5833435058594},
                {-316.4166564941406, -183.0833282470703, -99.75, -16.41666603088379,
                        .25, 16.91666603088379, 100.25, 183.5833282470703, 316.9166564941406}
        };
        int[] at = {5, 3, 2, 1, 0, 0, 1, 2, 3};
        int[] after = {3, 2, 1, 0, 0, 1, 2, 3, 6};
        for (int profile = 0; profile < 3; profile++) {
            for (int i = 0; i < 9; i++) {
                long lower = (long) Math.floor(thresholds[profile][i] * 1000);
                assertEquals(at[i], NantokaManiaRules.judge(lower, profile > 0, profile == 2));
                assertEquals(after[i], NantokaManiaRules.judge(lower + 1, profile > 0, profile == 2));
            }
        }
    }

    @Test void dpScratchGreatIsWiderThanSpOnBothLanes() {
        for (Mode mode : new Mode[]{Mode.BEAT_7K, Mode.BEAT_14K}) {
            var f = fixture(mode);
            for (int lane : mode.scratchKey) f.note(lane, 1_000_000);
            f.begin(false, 2);
            for (int lane : mode.scratchKey) f.input(lane, lane, true, 1_080_000);
            assertEquals(mode == Mode.BEAT_14K ? List.of(1, 1) : List.of(2), f.grades());
        }
    }

    @Test void inputCandidateEndsAt250msIndependentlyOfTableAndMissDeadline() {
        for (Mode mode : new Mode[]{Mode.BEAT_7K, Mode.BEAT_14K}) {
            for (long difference : new long[]{-350_000, -349_999, 250_000, 250_001, 270_000}) {
                var f = fixture(mode); int lane = mode.scratchKey[0];
                Note note = f.note(lane, 1_000_000); f.begin(false, 2);
                f.input(lane, lane, true, 1_000_000 + difference);
                if (difference == -349_999) assertEquals(List.of(5), f.grades());
                else if (difference == 250_000) assertEquals(List.of(3), f.grades());
                else if (difference == 270_000) assertEquals(List.of(4), f.grades());
                else { assertTrue(f.grades().isEmpty()); assertEquals(0, note.getState()); }
            }
        }
    }

    @Test void missUsesSixteenthVirtualFrameForKeysAndBothDpScratches() {
        var f = fixture(Mode.BEAT_14K);
        for (int lane : new int[]{0, 7, 15}) f.note(lane, 1_000_000);
        f.begin(false, 2); f.engine.advanceTo(1_266_665);
        assertTrue(f.grades().isEmpty()); f.engine.advanceTo(1_266_666);
        assertEquals(List.of(4, 4, 4), f.grades());
    }

    @Test void processedCandidateProducesEmptyPoorWithoutConsumingAgain() {
        var f = fixture(Mode.BEAT_7K); Note note = f.note(0, 1_000_000);
        f.begin(false, 2); f.input(0, 0, true, 1_000_000);
        f.input(0, 0, false, 1_001_000); f.input(0, 0, true, 1_002_000);
        assertEquals(List.of(0, 5), f.grades()); assertEquals(1, note.getState());
    }

    @Test void closestUnjudgedCandidateWinsInsteadOfFirstGoodCandidate() {
        var f = fixture(Mode.BEAT_7K);
        f.note(0, 1_000_000); Note nearest = f.note(0, 1_100_000);
        f.begin(false, 2); f.input(0, 0, true, 1_095_000);
        assertSame(nearest, f.results.get(0).note()); assertEquals(List.of(0), f.grades());
    }

    @Test void everyEarlyHcnReleaseConsumesEndpointAndReentryOnlyRestoresRecovery() {
        for (long early : new long[]{400_000, 200_000, 100_000, 50_000}) {
            var f = fixture(Mode.BEAT_7K);
            LongNote ln = f.hold(0, 1_000_000, 2_020_000, LongNote.TYPE_HELLCHARGENOTE);
            f.begin(false, 2); f.input(0, 0, true, 1_000_000);
            f.input(0, 0, false, 2_020_000 - early);
            int grade = early == 400_000 ? 4 : early == 200_000 ? 3 : 0;
            assertEquals(List.of(0, grade), f.grades());
            assertEquals(grade + 1, ln.getPair().getState()); assertNull(f.engine.processing(0));
            int count = f.results.size(); f.input(0, 0, true, 2_020_001 - early);
            assertEquals(count, f.results.size()); assertNull(f.engine.processing(0));
            int recoveryCount = f.recoveries.size(); f.engine.advanceTo(2_019_999);
            assertTrue(f.recoveries.size() > recoveryCount);
        }
    }

    @Test void successfulEarlyEndpointKeepsNonHeldBodyTicksUntilChartEnd() {
        var f = fixture(Mode.BEAT_7K);
        f.hold(0, 1_000_000, 2_050_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false, 2); f.input(0, 0, true, 1_000_000);
        f.input(0, 0, false, 1_950_000); f.engine.advanceTo(2_050_000);
        assertEquals(List.of(0, 0, 5), f.grades());
        assertEquals(2_000_000, f.results.get(2).time());
        int count = f.results.size(); f.engine.advanceTo(2_400_000);
        assertEquals(count, f.results.size()); assertNull(f.engine.passing(0));
    }

    @Test void reentryOnTickRetainsThatEmptyPoorAndRecoversOnNextTick() {
        var f = fixture(Mode.BEAT_7K);
        f.hold(0, 100_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false, 2); f.input(0, 0, true, 100_000);
        f.input(0, 0, false, 200_000); f.input(0, 0, true, 266_666);
        assertEquals(List.of(0, 4, 5), f.grades());
        int before = f.recoveries.size(); f.engine.advanceTo(400_000);
        assertEquals(before + 1, f.recoveries.size());
    }

    @Test void unjudgedPreHeldHcnDoesNotRecoverUntilItsStateIsActivated() {
        var f = fixture(Mode.BEAT_7K);
        f.hold(0, 100_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false, 2); f.engine.initiallyHeld(0, 0); f.engine.advanceTo(133_333);
        assertTrue(f.recoveries.isEmpty()); assertTrue(f.grades().isEmpty());
    }

    @Test void coincidentEightAndTenFrameReentryRetainsBothPoorAndDisplay() {
        var f = fixture(Mode.BEAT_7K);
        f.hold(0, 100_000, 1_000_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false, 2); f.input(0, 0, true, 100_000);
        f.input(0, 0, false, 200_000); f.engine.advanceTo(666_665);
        int count = f.results.size(); int refresh = f.refreshes.size();
        f.input(0, 0, true, 666_666);
        assertEquals(count + 1, f.results.size()); assertEquals(5, f.results.get(count).judge());
        assertEquals(refresh + 1, f.refreshes.size()); assertTrue(f.refreshes.get(refresh));
        int recovery = f.recoveries.size(); f.engine.advanceTo(800_000);
        assertEquals(recovery + 1, f.recoveries.size());
    }

    @Test void tenFrameDisplayRefreshDoesNotAddAnEightFramePoorOrRecovery() {
        var f = fixture(Mode.BEAT_7K);
        f.hold(0, 100_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false, 2); f.input(0, 0, true, 100_000);
        f.engine.advanceTo(166_666); assertEquals(List.of(false), f.refreshes);
        f.input(0, 0, false, 200_000);
        f.engine.advanceTo(266_666); int count = f.results.size(); int recovery = f.recoveries.size();
        f.engine.advanceTo(333_333);
        assertEquals(List.of(false, true), f.refreshes);
        assertEquals(count, f.results.size()); assertEquals(recovery, f.recoveries.size());
    }

    @Test void missedHcnStartRecoversWithoutCreatingEndpointHold() {
        var f = fixture(Mode.BEAT_7K);
        LongNote ln = f.hold(0, 100_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false, 2); f.engine.advanceTo(366_666);
        f.input(0, 0, true, 370_000); f.engine.advanceTo(400_000);
        assertEquals(List.of(0), f.recoveries); assertNull(f.engine.processing(0));
        f.input(0, 0, false, 890_000); assertEquals(0, ln.getPair().getState());
        f.engine.advanceTo(1_166_666); assertEquals(5, ln.getPair().getState());
    }

    @Test void logicalScratchStopJudgesBssEndpoint() {
        var f = fixture(Mode.BEAT_7K);
        LongNote ln = f.hold(7, 1_000_000, 2_000_000, LongNote.TYPE_CHARGENOTE);
        f.begin(false, 2); f.input(7, 7, true, 1_000_000); f.input(7, 7, false, 1_900_000);
        assertEquals(List.of(0, 0), f.grades()); assertEquals(1, ln.getPair().getState());
    }

    @Test void heldHcnCanRecoverPastBodyEndUntilEndpointIsConsumed() {
        var f = fixture(Mode.BEAT_7K);
        f.hold(0, 100_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false, 2); f.input(0, 0, true, 100_000);
        f.engine.advanceTo(933_333); assertEquals(7, f.recoveries.size());
        f.input(0, 0, false, 950_000); int count = f.recoveries.size();
        f.engine.advanceTo(1_200_000); assertEquals(count, f.recoveries.size());
    }

    @Test void largeAndSmallUpdatesProduceIdenticalJudgmentsAndRecovery() {
        var small = fixture(Mode.BEAT_7K); var large = fixture(Mode.BEAT_7K);
        for (var f : List.of(small, large)) {
            f.hold(0, 100_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
            f.hold(1, 120_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
            f.begin(false, 2); f.input(0, 0, true, 100_000);
        }
        for (long at = 110_000; at <= 1_400_000; at += 10_000) small.engine.advanceTo(at);
        large.engine.advanceTo(1_400_000);
        assertEquals(small.grades(), large.grades()); assertEquals(small.recoveries, large.recoveries);
        assertEquals(small.results.stream().map(NantokaManiaJudgeTest.Result::time).toList(),
                large.results.stream().map(NantokaManiaJudgeTest.Result::time).toList());
    }
}
