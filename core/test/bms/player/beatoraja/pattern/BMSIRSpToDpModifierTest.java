package bms.player.beatoraja.pattern;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BMSIRSpToDpModifierTest {
    @Test
    void fixedSevenKeyFixtureHasStableDpPlacement() {
        BMSModel model = fixture(Mode.BEAT_7K);
        int notes = model.getTotalNotes();

        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setSpToDpDifficulty(2);
        BMSIRManiacModifier modifier = new BMSIRManiacModifier(settings);
        modifier.modify(model);

        assertEquals(Mode.BEAT_14K, model.getMode());
        assertEquals(3, model.getPlayer());
        assertEquals(16, model.getAllTimeLines()[0].getLaneCount());
        assertEquals(notes, model.getTotalNotes());
        assertEquals(
                "3d4cf719b457ec6805fd6601d217719a117d2c0f2a03bcb2df5a31fa367317dc",
                modifier.getPlacementHash()
        );

        // The first scratch phrase takes 1P. Every key in its full guard
        // interval is forced to 2P instead of merely receiving a soft cost.
        assertNotNull(model.getAllTimeLines()[2].getNote(7));
        assertNotNull(model.getAllTimeLines()[0].getNote(8));
        assertNotNull(model.getAllTimeLines()[1].getNote(9));
        assertNotNull(model.getAllTimeLines()[3].getNote(10));
    }

    @Test
    void longNotePairAndAudioTimingArePreservedOnOneDpLane() {
        BMSModel model = fixture(Mode.BEAT_7K);
        LongNote start = (LongNote) model.getAllTimeLines()[4].getNote(3);
        LongNote end = start.getPair();
        long startOffset = start.getMicroStarttime();
        long duration = start.getMicroDuration();

        assertEquals(true, BMSIRSpToDpModifier.apply(model, 2));

        int lane = laneOf(model.getAllTimeLines()[4], start);
        assertEquals(lane, laneOf(model.getAllTimeLines()[6], end));
        assertSame(end, start.getPair());
        assertEquals(startOffset, start.getMicroStarttime());
        assertEquals(duration, start.getMicroDuration());
        assertEquals(30, start.getWav());
    }

    @Test
    void fiveKeyRebuildsTenKeyScratchAndLaneShape() {
        BMSModel model = fixture(Mode.BEAT_5K);
        int notes = model.getTotalNotes();

        assertEquals(true, BMSIRSpToDpModifier.apply(model, 1));

        assertEquals(Mode.BEAT_10K, model.getMode());
        assertEquals(12, model.getAllTimeLines()[0].getLaneCount());
        assertEquals(notes, model.getTotalNotes());
        assertNotNull(model.getAllTimeLines()[2].getNote(5));
        assertNull(model.getAllTimeLines()[2].getNote(11));
    }

    @Test
    void nativeDpAndNonBeatChartsAreNotChanged() {
        BMSModel model = fixture(Mode.BEAT_14K);
        String before = BMSIRManiacModifier.placementHash(model);

        assertEquals(false, BMSIRSpToDpModifier.apply(model, 3));
        assertEquals(Mode.BEAT_14K, model.getMode());
        assertEquals(before, BMSIRManiacModifier.placementHash(model));
    }

    @Test
    void difficultyChangesOnlyDeterministicScratchGuardWidth() {
        assertEquals(160_000L, BMSIRSpToDpModifier.scratchGuardUsForDifficulty(1));
        assertEquals(120_000L, BMSIRSpToDpModifier.scratchGuardUsForDifficulty(2));
        assertEquals(80_000L, BMSIRSpToDpModifier.scratchGuardUsForDifficulty(3));
        assertEquals(320_000L, BMSIRSpToDpModifier.scratchMergeGapUsForDifficulty(1));
        assertEquals(240_000L, BMSIRSpToDpModifier.scratchMergeGapUsForDifficulty(2));
        assertEquals(160_000L, BMSIRSpToDpModifier.scratchMergeGapUsForDifficulty(3));

        BMSModel easy = guardFixture();
        BMSModel normal = guardFixture();
        BMSModel hard = guardFixture();

        BMSIRSpToDpModifier.apply(easy, 1);
        BMSIRSpToDpModifier.apply(normal, 2);
        BMSIRSpToDpModifier.apply(hard, 3);

        String easyHash = BMSIRManiacModifier.placementHash(easy);
        String normalHash = BMSIRManiacModifier.placementHash(normal);
        String hardHash = BMSIRManiacModifier.placementHash(hard);
        assertNotEquals(easyHash, normalHash);
        assertNotEquals(normalHash, hardHash);
    }

    @Test
    void stairThresholdsMatchTheAgreedLevelDensities() {
        assertEquals(333_334L, BMSIRSpToDpModifier.stairGapUsForDifficulty(1));
        assertEquals(111_112L, BMSIRSpToDpModifier.stairGapUsForDifficulty(2));
        assertEquals(83_334L, BMSIRSpToDpModifier.stairGapUsForDifficulty(3));
    }

    @Test
    void monotonicSevenKeyStairsAlternateDpSidesAtEveryLevelBoundary() {
        long[] thresholds = {333_334L, 111_112L, 83_334L};
        for (int level = 1; level <= 3; level++) {
            TimeLine[] lines = new TimeLine[7];
            NormalNote[] notes = new NormalNote[7];
            for (int index = 0; index < lines.length; index++) {
                lines[index] = line(
                        index * thresholds[level - 1] / 1_000_000.0,
                        index * thresholds[level - 1],
                        Mode.BEAT_7K.key
                );
                notes[index] = new NormalNote(40);
                lines[index].setNote(index, notes[index]);
            }
            BMSModel model = model("sp-to-dp-stair-level-" + level, Mode.BEAT_7K, lines);

            BMSIRSpToDpModifier.apply(model, level);

            for (int index = 0; index < notes.length; index++) {
                int expectedLane = (index & 1) == 0 ? index : Mode.BEAT_7K.key + index;
                assertEquals(expectedLane, laneOf(lines[index], notes[index]));
            }
        }
    }

    @Test
    void threeNoteSequenceAboveTheLevelOneThresholdKeepsLegacyAssignment() {
        long gap = BMSIRSpToDpModifier.stairGapUsForDifficulty(1) + 1L;
        TimeLine[] lines = lines(new long[]{0L, gap, gap * 2L}, Mode.BEAT_7K);
        NormalNote first = new NormalNote(40);
        NormalNote second = new NormalNote(40);
        NormalNote third = new NormalNote(40);
        lines[0].setNote(0, first);
        lines[1].setNote(1, second);
        lines[2].setNote(2, third);
        BMSModel model = model("sp-to-dp-slow-step", Mode.BEAT_7K, lines);

        BMSIRSpToDpModifier.apply(model, 1);

        assertEquals(0, laneOf(lines[0], first));
        assertEquals(1, laneOf(lines[1], second));
        assertEquals(Mode.BEAT_7K.key + 2, laneOf(lines[2], third));
    }

    @Test
    void adjacentSimultaneousKeysSplitAcrossSidesForEveryPairAndLevel() {
        for (int level = 1; level <= 3; level++) {
            TimeLine[] lines = new TimeLine[6];
            NormalNote[][] notes = new NormalNote[6][2];
            for (int index = 0; index < lines.length; index++) {
                lines[index] = line(index, index * 1_000_000L, Mode.BEAT_7K.key);
                notes[index][0] = new NormalNote(50);
                notes[index][1] = new NormalNote(50);
                lines[index].setNote(index, notes[index][0]);
                lines[index].setNote(index + 1, notes[index][1]);
            }
            BMSModel model = model(
                    "sp-to-dp-adjacent-pairs-level-" + level,
                    Mode.BEAT_7K,
                    lines
            );

            BMSIRSpToDpModifier.apply(model, level);

            for (int index = 0; index < lines.length; index++) {
                int firstLane = laneOf(lines[index], notes[index][0]);
                int secondLane = laneOf(lines[index], notes[index][1]);
                assertNotEquals(
                        firstLane < Mode.BEAT_7K.key,
                        secondLane < Mode.BEAT_7K.key
                );
                assertEquals(
                        (index & 1) == 0 ? index : Mode.BEAT_7K.key + index,
                        firstLane
                );
            }
        }
    }

    @Test
    void fullSevenKeyChordUsesOddEvenSideColoring() {
        TimeLine timeline = line(0.0, 0L, Mode.BEAT_7K.key);
        NormalNote[] notes = new NormalNote[7];
        for (int lane = 0; lane < notes.length; lane++) {
            notes[lane] = new NormalNote(60);
            timeline.setNote(lane, notes[lane]);
        }
        BMSModel model = model(
                "sp-to-dp-seven-key-chord",
                Mode.BEAT_7K,
                new TimeLine[]{timeline}
        );

        BMSIRSpToDpModifier.apply(model, 1);

        for (int lane = 0; lane < notes.length; lane++) {
            int expectedLane = (lane & 1) == 0 ? lane : Mode.BEAT_7K.key + lane;
            assertEquals(expectedLane, laneOf(timeline, notes[lane]));
        }
    }

    @Test
    void adjacentScratchMergeThresholdsAreInclusive() {
        assertScratchPairPlacement(1, 320_000L, true);
        assertScratchPairPlacement(1, 320_001L, false);
        assertScratchPairPlacement(2, 240_000L, true);
        assertScratchPairPlacement(2, 240_001L, false);
        assertScratchPairPlacement(3, 160_000L, true);
        assertScratchPairPlacement(3, 160_001L, false);
    }

    @Test
    void scratchPhraseKeepsChainingWithoutDurationOrCountLimit() {
        long gap = 320_000L;
        TimeLine[] lines = lines(
                new long[]{0L, gap, gap * 2, gap * 3, gap * 4, gap * 5},
                Mode.BEAT_7K
        );
        NormalNote[] scratches = new NormalNote[lines.length];
        for (int index = 0; index < lines.length; index++) {
            scratches[index] = new NormalNote(100 + index);
            lines[index].setNote(Mode.BEAT_7K.scratchKey[0], scratches[index]);
        }
        BMSModel model = model("sp-to-dp-unbounded-scratch-chain", Mode.BEAT_7K, lines);

        BMSIRSpToDpModifier.apply(model, 1);

        int sideLane = laneOf(lines[0], scratches[0]);
        for (int index = 1; index < lines.length; index++) {
            assertEquals(sideLane, laneOf(lines[index], scratches[index]));
        }
    }

    @Test
    void separatedThreeThreeFourScratchPhrasesAlternateSides() {
        long[] times = {
                0L, 100_000L, 200_000L,
                900_000L, 1_000_000L, 1_100_000L,
                1_800_000L, 1_900_000L, 2_000_000L, 2_100_000L
        };
        TimeLine[] lines = lines(times, Mode.BEAT_7K);
        for (int index = 0; index < lines.length; index++) {
            lines[index].setNote(Mode.BEAT_7K.scratchKey[0], new NormalNote(100 + index));
        }
        BMSModel model = model("sp-to-dp-scratch-334", Mode.BEAT_7K, lines);

        BMSIRSpToDpModifier.apply(model, 2);

        for (int index = 0; index < 3; index++) {
            assertNotNull(lines[index].getNote(Mode.BEAT_14K.scratchKey[0]));
        }
        for (int index = 3; index < 6; index++) {
            assertNotNull(lines[index].getNote(Mode.BEAT_14K.scratchKey[1]));
        }
        for (int index = 6; index < lines.length; index++) {
            assertNotNull(lines[index].getNote(Mode.BEAT_14K.scratchKey[0]));
        }
    }

    @Test
    void repeatedWavDoesNotPinSeparatedScratchPhrasesToOneSide() {
        long[] times = {0L, 900_000L, 1_800_000L, 2_700_000L};
        TimeLine[] lines = lines(times, Mode.BEAT_7K);
        for (TimeLine line : lines) {
            line.setNote(Mode.BEAT_7K.scratchKey[0], new NormalNote(90));
        }
        BMSModel model = model("sp-to-dp-repeated-scratch-wav", Mode.BEAT_7K, lines);

        BMSIRSpToDpModifier.apply(model, 1);

        assertNotNull(lines[0].getNote(Mode.BEAT_14K.scratchKey[0]));
        assertNotNull(lines[1].getNote(Mode.BEAT_14K.scratchKey[1]));
        assertNotNull(lines[2].getNote(Mode.BEAT_14K.scratchKey[0]));
        assertNotNull(lines[3].getNote(Mode.BEAT_14K.scratchKey[1]));
    }

    @Test
    void connectedScratchOnlyRollStaysOnOneSideAndWideChordUsesTheOther() {
        TimeLine[] lines = lines(new long[]{0L, 240_000L}, Mode.BEAT_7K);
        lines[0].setNote(Mode.BEAT_7K.scratchKey[0], new NormalNote(90));
        lines[1].setNote(Mode.BEAT_7K.scratchKey[0], new NormalNote(91));
        lines[1].setNote(0, new NormalNote(10));
        lines[1].setNote(6, new NormalNote(20));
        BMSModel model = model("sp-to-dp-connected-scratch", Mode.BEAT_7K, lines);

        BMSIRSpToDpModifier.apply(model, 2);

        assertNotNull(lines[0].getNote(7));
        assertNotNull(lines[1].getNote(7));
        assertNotNull(lines[1].getNote(8));
        assertNotNull(lines[1].getNote(14));
        assertNull(lines[1].getNote(0));
        assertNull(lines[1].getNote(6));
    }

    @Test
    void longScratchReservesItsSideThroughThePairedEndAndGuard() {
        TimeLine[] lines = lines(
                new long[]{1_000_000L, 2_000_000L, 3_000_000L, 3_190_000L},
                Mode.BEAT_7K
        );
        LongNote scratchStart = longNote(90);
        LongNote scratchEnd = longNote(-1);
        lines[0].setNote(Mode.BEAT_7K.scratchKey[0], scratchStart);
        lines[2].setNote(Mode.BEAT_7K.scratchKey[0], scratchEnd);
        scratchStart.setPair(scratchEnd);
        lines[1].setNote(0, new NormalNote(10));
        lines[3].setNote(6, new NormalNote(20));
        BMSModel model = model("sp-to-dp-long-scratch", Mode.BEAT_7K, lines);

        BMSIRSpToDpModifier.apply(model, 2);

        assertEquals(7, laneOf(lines[0], scratchStart));
        assertEquals(7, laneOf(lines[2], scratchEnd));
        assertNotNull(lines[1].getNote(8));
        assertNotNull(lines[3].getNote(14));
    }

    @Test
    void keyLongNoteConnectingSeparateScratchPhrasesKeepsOneSafeSide() {
        TimeLine[] lines = lines(
                new long[]{500_000L, 1_000_000L, 3_000_000L, 3_500_000L},
                Mode.BEAT_7K
        );
        LongNote keyStart = longNote(30);
        LongNote keyEnd = longNote(-1);
        lines[0].setNote(0, keyStart);
        lines[1].setNote(Mode.BEAT_7K.scratchKey[0], new NormalNote(90));
        lines[2].setNote(Mode.BEAT_7K.scratchKey[0], new NormalNote(91));
        lines[3].setNote(0, keyEnd);
        keyStart.setPair(keyEnd);
        BMSModel model = model("sp-to-dp-key-ln-scratch-bridge", Mode.BEAT_7K, lines);

        BMSIRSpToDpModifier.apply(model, 2);

        assertNotNull(lines[1].getNote(7));
        assertNotNull(lines[2].getNote(7));
        assertEquals(8, laneOf(lines[0], keyStart));
        assertEquals(8, laneOf(lines[3], keyEnd));
    }

    private static BMSModel fixture(Mode mode) {
        Mode source = mode == Mode.BEAT_5K ? Mode.BEAT_5K : Mode.BEAT_7K;
        int scratch = source.scratchKey[0];
        TimeLine[] lines = {
                line(0.00, 0L, mode.key),
                line(0.10, 100_000L, mode.key),
                line(0.20, 200_000L, mode.key),
                line(0.30, 300_000L, mode.key),
                line(0.40, 400_000L, mode.key),
                line(1.05, 1_050_000L, mode.key),
                line(1.20, 1_200_000L, mode.key)
        };
        lines[0].setNote(0, new NormalNote(10));
        lines[1].setNote(1, new NormalNote(10));
        lines[2].setNote(scratch, new NormalNote(90));
        lines[3].setNote(2, new NormalNote(20));
        LongNote start = new LongNote(30, 12_345L, 456_789L);
        start.setType(LongNote.TYPE_LONGNOTE);
        LongNote end = new LongNote(-1);
        end.setType(LongNote.TYPE_LONGNOTE);
        lines[4].setNote(3, start);
        lines[6].setNote(3, end);
        start.setPair(end);
        lines[5].setNote(4, new NormalNote(10));

        return model("sp-to-dp-fixed-fixture", mode, lines);
    }

    private static TimeLine line(double section, long time, int lanes) {
        TimeLine line = new TimeLine(section, time, lanes);
        line.setBPM(120);
        return line;
    }

    private static BMSModel guardFixture() {
        TimeLine[] lines = lines(
                new long[]{0L, 300_000L, 2_000_000L, 2_200_000L},
                Mode.BEAT_7K
        );
        for (int index = 0; index < lines.length; index++) {
            lines[index].setNote(Mode.BEAT_7K.scratchKey[0], new NormalNote(90 + index));
        }
        return model("sp-to-dp-guard-fixture", Mode.BEAT_7K, lines);
    }

    private static void assertScratchPairPlacement(int level, long gap, boolean sameSide) {
        TimeLine[] lines = lines(new long[]{0L, gap}, Mode.BEAT_7K);
        NormalNote first = new NormalNote(90);
        NormalNote second = new NormalNote(91);
        lines[0].setNote(Mode.BEAT_7K.scratchKey[0], first);
        lines[1].setNote(Mode.BEAT_7K.scratchKey[0], second);
        BMSModel model = model("sp-to-dp-threshold-" + level + "-" + gap, Mode.BEAT_7K, lines);

        BMSIRSpToDpModifier.apply(model, level);

        if (sameSide) {
            assertEquals(laneOf(lines[0], first), laneOf(lines[1], second));
        } else {
            assertNotEquals(laneOf(lines[0], first), laneOf(lines[1], second));
        }
    }

    private static TimeLine[] lines(long[] times, Mode mode) {
        TimeLine[] lines = new TimeLine[times.length];
        for (int index = 0; index < times.length; index++) {
            lines[index] = line(times[index] / 1_000_000.0, times[index], mode.key);
        }
        return lines;
    }

    private static BMSModel model(String hash, Mode mode, TimeLine[] lines) {
        BMSModel model = new BMSModel();
        model.setMode(mode);
        model.setPlayer(mode.player == 2 ? 3 : 1);
        model.setBpm(120);
        model.setSHA256(hash);
        model.setAllTimeLine(lines);
        return model;
    }

    private static LongNote longNote(int wav) {
        LongNote note = new LongNote(wav, 0L, 0L);
        note.setType(LongNote.TYPE_LONGNOTE);
        return note;
    }

    private static int laneOf(TimeLine timeline, Object note) {
        for (int lane = 0; lane < timeline.getLaneCount(); lane++) {
            if (timeline.getNote(lane) == note) return lane;
        }
        return -1;
    }
}
