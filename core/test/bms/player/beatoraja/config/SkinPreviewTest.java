package bms.player.beatoraja.config;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Note;
import bms.model.TimeLine;
import bms.player.beatoraja.skin.SkinType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinPreviewTest {
	@Test
	void onlyRecursiveSkinSelectIsExcludedFromPreview() {
		EnumSet<SkinType> excluded = EnumSet.of(
				SkinType.SKIN_SELECT);

		for (SkinType type : SkinType.values()) {
			assertEquals(!excluded.contains(type), SkinConfiguration.supportsPreview(type), type.name());
		}
		assertFalse(SkinConfiguration.supportsPreview(null));
		assertTrue(SkinConfiguration.supportsPreview(SkinType.PLAY_7KEYS));
		assertTrue(SkinConfiguration.supportsPreview(SkinType.DECIDE));
		assertTrue(SkinConfiguration.supportsPreview(SkinType.RESULT));
		assertTrue(SkinConfiguration.supportsPreview(SkinType.COURSE_RESULT));
	}

	@Test
	void playTimelineCoversLoadingReadyPlayAndFinishBeforeLooping() {
		var preload = SkinPreviewLifecycle.playFrame(0, 0, 0, 10_000, 500, 500);
		var ready = SkinPreviewLifecycle.playFrame(1_200, 0, 0, 10_000, 500, 500);
		var play = SkinPreviewLifecycle.playFrame(2_200, 0, 0, 10_000, 500, 500);
		var finish = SkinPreviewLifecycle.playFrame(12_200, 0, 0, 10_000, 500, 500);
		var looped = SkinPreviewLifecycle.playFrame(13_400, 0, 0, 10_000, 500, 500);

		assertEquals(SkinPreviewLifecycle.PlayPhase.PRELOAD, preload.phase());
		assertEquals(SkinPreviewLifecycle.PlayPhase.READY, ready.phase());
		assertEquals(SkinPreviewLifecycle.PlayPhase.PLAY, play.phase());
		assertEquals(SkinPreviewLifecycle.PlayPhase.FINISHED, finish.phase());
		assertEquals(0, looped.position());
		assertEquals(1, looped.iteration());
		assertEquals(SkinPreviewLifecycle.PlayPhase.PRELOAD, looped.phase());
	}

	@Test
	void sceneTimelineExposesInputScoreUpdateAndFadeout() {
		var intro = SkinPreviewLifecycle.sceneFrame(500, 1000, 5000, 700);
		var update = SkinPreviewLifecycle.sceneFrame(1500, 1000, 5000, 700);
		var fadeout = SkinPreviewLifecycle.sceneFrame(5200, 1000, 5000, 700);

		assertEquals(-1, intro.inputTime());
		assertEquals(0, update.updateTime());
		assertEquals(200, fadeout.fadeoutTime());
	}

	@Test
	void playDataStartsAtTheFirstRealNoteTimeAndReachesTheFullChart() {
		var model = SkinPreviewModel.create(bms.model.Mode.BEAT_7K);
		long firstNoteMillis = SkinPreviewModel.LEAD_IN_MICROS / 1000L;

		assertEquals(0, SkinPreviewPlayer.countPastNotes(model, firstNoteMillis - 1));
		assertTrue(SkinPreviewPlayer.countPastNotes(model, firstNoteMillis) > 0);
		assertEquals(model.getTotalNotes(),
				SkinPreviewPlayer.countPastNotes(model, model.getLastTime()));
		assertEquals(-1, SkinPreviewPlayer.latestJudgementTime(model, 0, firstNoteMillis - 1));
		assertTrue(SkinPreviewPlayer.latestJudgementTime(model, 0, model.getLastTime()) >= 800);
	}

	@Test
	void doublePlayAdvancesJudgementsAndCombosIndependentlyForBothPlayers() {
		BMSModel model = SkinPreviewModel.create(bms.model.Mode.BEAT_14K);
		long firstNoteMillis = SkinPreviewModel.LEAD_IN_MICROS / 1000L;
		long lastNoteMillis = model.getLastTime();

		assertEquals(0, SkinPreviewPlayer.countPastNotes(model, firstNoteMillis - 1, 0));
		assertEquals(0, SkinPreviewPlayer.countPastNotes(model, firstNoteMillis - 1, 1));
		assertTrue(SkinPreviewPlayer.countPastNotes(model, firstNoteMillis, 0) > 0);
		assertTrue(SkinPreviewPlayer.countPastNotes(model, firstNoteMillis, 1) > 0);

		int playerOneNotes = SkinPreviewPlayer.countPastNotes(model, lastNoteMillis, 0);
		int playerTwoNotes = SkinPreviewPlayer.countPastNotes(model, lastNoteMillis, 1);
		assertTrue(playerOneNotes > 0);
		assertTrue(playerTwoNotes > 0);
		assertEquals(model.getTotalNotes(), playerOneNotes + playerTwoNotes);
		assertTrue(SkinPreviewPlayer.latestJudgementTimeForPlayer(
				model, 0, lastNoteMillis) >= firstNoteMillis);
		assertTrue(SkinPreviewPlayer.latestJudgementTimeForPlayer(
				model, 1, lastNoteMillis) >= firstNoteMillis);
	}

	@Test
	void tapKeyBeamTurnsOffAfterItsBoundedHold() {
		BMSModel model = SkinPreviewModel.create(bms.model.Mode.BEAT_7K);
		TimeLine first = model.getAllTimeLines()[0];
		int lane = firstOccupiedLane(model, first);
		long noteTime = first.getTime();

		var pressed = SkinPreviewPlayer.laneEffect(model, lane, noteTime + 99L);
		var released = SkinPreviewPlayer.laneEffect(model, lane, noteTime + 100L);

		assertEquals(99L, pressed.keyOnElapsed());
		assertEquals(-1L, pressed.keyOffElapsed());
		assertEquals(-1L, released.keyOnElapsed());
		assertEquals(0L, released.keyOffElapsed());
	}

	@Test
	void longNoteKeepsKeyAndAnimationActiveOnlyUntilItsEnd() {
		BMSModel model = SkinPreviewModel.create(bms.model.Mode.BEAT_7K);
		LongNote start = null;
		int lane = -1;
		for (TimeLine timeline : model.getAllTimeLines()) {
			for (int candidate = 0; candidate < model.getMode().key; candidate++) {
				Note note = timeline.getNote(candidate);
				if (note instanceof LongNote longNote && !longNote.isEnd()) {
					start = longNote;
					lane = candidate;
					break;
				}
			}
			if (start != null) break;
		}

		assertNotNull(start);
		long startTime = start.getTime();
		long endTime = start.getPair().getTime();
		var held = SkinPreviewPlayer.laneEffect(model, lane, startTime + 1L);
		var released = SkinPreviewPlayer.laneEffect(model, lane, endTime);

		assertSame(start, held.activeLongNote());
		assertEquals(1L, held.keyOnElapsed());
		assertEquals(1L, held.longNoteElapsed());
		assertNull(released.activeLongNote());
		assertEquals(-1L, released.keyOnElapsed());
		assertEquals(0L, released.keyOffElapsed());
		assertEquals(-1L, released.longNoteElapsed());
	}

	@Test
	void previewBufferUsesDestinationSizeAndStaysBounded() {
		assertEquals(640, SkinPreview.bufferDimension(1920, 640));
		assertEquals(360, SkinPreview.bufferDimension(1080, 360));
		assertEquals(1280, SkinPreview.bufferDimension(1280, 4096));
		assertEquals(2048, SkinPreview.bufferDimension(8192, 4096));
		assertEquals(1, SkinPreview.bufferDimension(0, 0));
	}

	private static int firstOccupiedLane(BMSModel model, TimeLine timeline) {
		for (int lane = 0; lane < model.getMode().key; lane++) {
			if (timeline.getNote(lane) != null) return lane;
		}
		throw new AssertionError("preview timeline has no note");
	}
}
