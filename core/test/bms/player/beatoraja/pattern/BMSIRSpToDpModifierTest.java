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
                "e85f3b8c7fca4c1c4ca033cf5a2a8bd6930dfd1982bbb270199068a876b9044e",
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
        assertEquals(240_000L, BMSIRSpToDpModifier.scratchGuardUsForDifficulty(1));
        assertEquals(200_000L, BMSIRSpToDpModifier.scratchGuardUsForDifficulty(2));
        assertEquals(160_000L, BMSIRSpToDpModifier.scratchGuardUsForDifficulty(3));

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
    void connectedScratchOnlyRollStaysOnOneSideAndWideChordUsesTheOther() {
        TimeLine[] lines = lines(new long[]{0L, 300_000L}, Mode.BEAT_7K);
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
                new long[]{0L, 440_000L, 2_000_000L, 2_360_000L},
                Mode.BEAT_7K
        );
        for (int index = 0; index < lines.length; index++) {
            lines[index].setNote(Mode.BEAT_7K.scratchKey[0], new NormalNote(90 + index));
        }
        return model("sp-to-dp-guard-fixture", Mode.BEAT_7K, lines);
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
