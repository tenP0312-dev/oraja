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
                "1b8e32a781f35e9d0ac24fe9f7a2ab127f7b415135b6588858e4eab53f67e283",
                modifier.getPlacementHash()
        );

        // Repeated WAV 10 stays on one side within measure 0 and remains
        // stable when it appears in the immediately following measure.
        assertNotNull(model.getAllTimeLines()[0].getNote(0));
        assertNotNull(model.getAllTimeLines()[1].getNote(1));
        assertNotNull(model.getAllTimeLines()[5].getNote(4));

        // The scratch is on 2P; nearby keys at both sides of its timing avoid
        // that side even while the rest of the measure remains balanced.
        assertNotNull(model.getAllTimeLines()[2].getNote(15));
        assertNotNull(model.getAllTimeLines()[1].getNote(1));
        assertNotNull(model.getAllTimeLines()[3].getNote(2));
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
        assertNotNull(model.getAllTimeLines()[2].getNote(11));
        assertNull(model.getAllTimeLines()[2].getNote(5));
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
    void difficultyProfileChangesDeterministicDensePlacement() {
        BMSModel easy = denseFixture();
        BMSModel normal = denseFixture();
        BMSModel hard = denseFixture();

        BMSIRSpToDpModifier.apply(easy, 1);
        BMSIRSpToDpModifier.apply(normal, 2);
        BMSIRSpToDpModifier.apply(hard, 3);

        String easyHash = BMSIRManiacModifier.placementHash(easy);
        String normalHash = BMSIRManiacModifier.placementHash(normal);
        String hardHash = BMSIRManiacModifier.placementHash(hard);
        assertNotEquals(easyHash, normalHash);
        assertNotEquals(normalHash, hardHash);
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

        BMSModel model = new BMSModel();
        model.setMode(mode);
        model.setPlayer(mode.player == 2 ? 3 : 1);
        model.setBpm(120);
        model.setSHA256("sp-to-dp-fixed-fixture");
        model.setAllTimeLine(lines);
        return model;
    }

    private static TimeLine line(double section, long time, int lanes) {
        TimeLine line = new TimeLine(section, time, lanes);
        line.setBPM(120);
        return line;
    }

    private static BMSModel denseFixture() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(150);
        model.setSHA256("sp-to-dp-difficulty-fixture");
        TimeLine[] lines = new TimeLine[24];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = line(index / 8.0, index * 300_000L, Mode.BEAT_7K.key);
            lines[index].setNote(index % 4, new NormalNote(40 + index % 3));
            if (index % 7 == 3) {
                lines[index].setNote(Mode.BEAT_7K.scratchKey[0], new NormalNote(90 + index));
            }
        }
        model.setAllTimeLine(lines);
        return model;
    }

    private static int laneOf(TimeLine timeline, Object note) {
        for (int lane = 0; lane < timeline.getLaneCount(); lane++) {
            if (timeline.getNote(lane) == note) return lane;
        }
        return -1;
    }
}
