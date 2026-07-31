package bms.player.beatoraja.play;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.MineNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartHerePreviewDataTest {
    @Test
    void cachesPlayableStartsFromTheRequestedMeasures() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);

        TimeLine first = new TimeLine(0.0, 0, Mode.BEAT_7K.key);
        first.setNote(0, new NormalNote(1));
        first.setNote(3, new NormalNote(2));

        TimeLine later = new TimeLine(1.5, 1_500_000, Mode.BEAT_7K.key);
        LongNote start = new LongNote(3);
        later.setNote(2, start);
        later.setNote(4, new MineNote(-1, 10f));

        TimeLine end = new TimeLine(1.75, 1_750_000, Mode.BEAT_7K.key);
        LongNote pair = new LongNote(-2);
        end.setNote(2, pair);
        start.setPair(pair);

        TimeLine outside = new TimeLine(2.0, 2_000_000, Mode.BEAT_7K.key);
        outside.setNote(1, new NormalNote(4));
        model.setAllTimeLine(new TimeLine[]{first, later, end, outside});

        StartHerePreviewData preview =
                StartHerePreviewData.build(model, 2, 32);

        assertTrue(preview.isValid());
        assertEquals(3, preview.notes().size());
        assertTrue(preview.notes().get(0).firstChord());
        assertTrue(preview.notes().get(1).firstChord());
        assertFalse(preview.notes().get(2).firstChord());
    }

    @Test
    void emptyAndPerSideOverflowFallBackToNormalDrawing() {
        BMSModel empty = new BMSModel();
        empty.setMode(Mode.BEAT_14K);
        empty.setAllTimeLine(new TimeLine[0]);
        assertFalse(StartHerePreviewData.build(empty, 2, 32).isValid());

        BMSModel dense = new BMSModel();
        dense.setMode(Mode.BEAT_14K);
        TimeLine[] lines = new TimeLine[33];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = new TimeLine(
                    index / 64.0,
                    index * 1_000L,
                    Mode.BEAT_14K.key
            );
            lines[index].setNote(0, new NormalNote(index));
        }
        dense.setAllTimeLine(lines);
        assertFalse(StartHerePreviewData.build(dense, 2, 32).isValid());
    }
}
