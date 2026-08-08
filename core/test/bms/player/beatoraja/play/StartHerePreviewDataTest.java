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
    void cachesOnlyTheFirstPlayableTimingAcrossTheWholeChart() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);

        TimeLine first = new TimeLine(0.0, 0, Mode.BEAT_7K.key);
		first.setBPM(150);
		first.setScroll(1.5);
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

        StartHerePreviewData preview = StartHerePreviewData.build(model);

        assertTrue(preview.isValid());
        assertEquals(2, preview.notes().size());
        assertEquals(0, preview.notes().get(0).lane());
        assertEquals(3, preview.notes().get(1).lane());
		assertEquals(150.0, preview.anchorBpm());
		assertEquals(1.5, preview.anchorScroll());
    }

    @Test
    void skipsMinesAndLongNoteEndsBeforeTheFirstPlayableTiming() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);

        TimeLine ignored = new TimeLine(0.0, 0, Mode.BEAT_7K.key);
        ignored.setNote(0, new MineNote(-1, 10f));

        TimeLine hiddenStart = new TimeLine(-1.0, -1_000_000, Mode.BEAT_7K.key);
        LongNote hiddenStartNote = new LongNote(2);
        LongNote end = new LongNote(-2);
        hiddenStart.setNote(1, hiddenStartNote);
        ignored.setNote(1, end);
        hiddenStartNote.setPair(end);

        TimeLine first = new TimeLine(12.0, 12_000_000, Mode.BEAT_7K.key);
        LongNote start = new LongNote(3);
        LongNote laterEnd = new LongNote(-3);
        first.setNote(2, start);
        first.setNote(5, new NormalNote(4));
        TimeLine later = new TimeLine(13.0, 13_000_000, Mode.BEAT_7K.key);
        later.setNote(2, laterEnd);
        start.setPair(laterEnd);
        model.setAllTimeLine(new TimeLine[]{ignored, first, later});

        StartHerePreviewData preview = StartHerePreviewData.build(model);

        assertTrue(preview.isValid());
        assertEquals(2, preview.notes().size());
        assertEquals(2, preview.notes().get(0).lane());
        assertEquals(5, preview.notes().get(1).lane());
    }

    @Test
    void keepsBothDpSidesAtOneSharedFirstTiming() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_14K);

        TimeLine left = new TimeLine(4.0, 4_000_000, Mode.BEAT_14K.key);
        left.setNote(0, new NormalNote(1));
        TimeLine right = new TimeLine(4.0, 4_000_000, Mode.BEAT_14K.key);
        right.setNote(13, new NormalNote(2));
        TimeLine later = new TimeLine(4.25, 4_250_000, Mode.BEAT_14K.key);
        later.setNote(7, new NormalNote(3));
        model.setAllTimeLine(new TimeLine[]{left, right, later});

        StartHerePreviewData preview = StartHerePreviewData.build(model);

        assertTrue(preview.isValid());
        assertEquals(2, preview.notes().size());
        assertEquals(0, preview.notes().get(0).lane());
        assertEquals(13, preview.notes().get(1).lane());
    }

    @Test
    void emptyChartFallsBackToNormalDrawing() {
        BMSModel empty = new BMSModel();
        empty.setMode(Mode.BEAT_14K);
        empty.setAllTimeLine(new TimeLine[0]);
        assertFalse(StartHerePreviewData.build(empty).isValid());
    }

	@Test
	void invalidAnchorBpmUsesTheNearestPositiveChartBpm() {
		BMSModel model = new BMSModel();
		model.setMode(Mode.BEAT_7K);
		model.setBpm(0.0);
		TimeLine anchor = new TimeLine(0.0, 0, Mode.BEAT_7K.key);
		anchor.setBPM(0.0);
		anchor.setNote(0, new NormalNote(1));
		TimeLine later = new TimeLine(1.0, 1_000_000, Mode.BEAT_7K.key);
		later.setBPM(180.0);
		model.setAllTimeLine(new TimeLine[]{anchor, later});

		assertEquals(180.0, StartHerePreviewData.build(model).anchorBpm());
	}
}
