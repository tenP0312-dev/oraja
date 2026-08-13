package bms.player.beatoraja.config;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Mode;
import bms.model.Note;
import bms.model.TimeLine;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinPreviewModelTest {
	@Test
	void createsAPlayableDeterministicChartForTheRequestedMode() {
		BMSModel model = SkinPreviewModel.create(Mode.BEAT_7K);

		assertSame(Mode.BEAT_7K, model.getMode());
		assertEquals("SKIN PREVIEW", model.getTitle());
		assertEquals(SkinPreviewModel.STEP_COUNT, model.getAllTimeLines().length);
		assertTrue(model.getTotalNotes() > 0);
		assertTrue(model.getLastTime() > 10_000);
		TimeLine first = model.getAllTimeLines()[0];
		assertEquals(SkinPreviewModel.LEAD_IN_MICROS, first.getMicroTime());
		assertEquals(0.5, first.getSection(), 0.000001);
		assertLongNotesArePairedOnOneNonScratchLane(model);
		assertLongNoteLanesRemainEmptyBetweenTheirPairs(model);
	}

	@Test
	void supportsDoublePlayWithoutPlacingPreviewNotesOnScratches() {
		BMSModel model = SkinPreviewModel.create(Mode.BEAT_14K);

		assertSame(Mode.BEAT_14K, model.getMode());
		assertLongNotesArePairedOnOneNonScratchLane(model);
		assertLongNoteLanesRemainEmptyBetweenTheirPairs(model);
		for (TimeLine timeline : model.getAllTimeLines()) {
			boolean[] playerHasNote = new boolean[model.getMode().player];
			for (int lane = 0; lane < model.getMode().key; lane++) {
				if (timeline.getNote(lane) != null) {
					assertFalse(model.getMode().isScratchKey(lane));
					playerHasNote[lane / (model.getMode().key / model.getMode().player)] = true;
				}
			}
			assertTrue(playerHasNote[0], "1P preview lane must advance every step");
			assertTrue(playerHasNote[1], "2P preview lane must advance every step");
		}
	}

	private static void assertLongNotesArePairedOnOneNonScratchLane(BMSModel model) {
		Map<LongNote, Integer> lanes = new IdentityHashMap<>();
		int longNoteCount = 0;
		for (TimeLine timeline : model.getAllTimeLines()) {
			for (int lane = 0; lane < model.getMode().key; lane++) {
				Note note = timeline.getNote(lane);
				if (note instanceof LongNote longNote) {
					lanes.put(longNote, lane);
					longNoteCount++;
				}
			}
		}

		assertTrue(longNoteCount >= 2);
		for (Map.Entry<LongNote, Integer> entry : lanes.entrySet()) {
			LongNote pair = entry.getKey().getPair();
			assertNotNull(pair);
			assertEquals(entry.getValue(), lanes.get(pair));
			assertFalse(model.getMode().isScratchKey(entry.getValue()));
		}
	}

	private static void assertLongNoteLanesRemainEmptyBetweenTheirPairs(BMSModel model) {
		TimeLine[] timelines = model.getAllTimeLines();
		for (int startIndex = 0; startIndex < timelines.length; startIndex++) {
			for (int lane = 0; lane < model.getMode().key; lane++) {
				Note note = timelines[startIndex].getNote(lane);
				if (!(note instanceof LongNote start) || start.isEnd()) continue;

				for (int index = startIndex + 1;
						index < timelines.length && timelines[index].getMicroTime() < start.getPair().getMicroTime();
						index++) {
					assertNull(timelines[index].getNote(lane),
							"preview notes must not overlap an active long-note lane");
				}
			}
		}
	}
}
