package bms.player.beatoraja.config;

import bms.player.beatoraja.skin.SkinType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

		assertEquals(0, SkinPreviewPlayer.countPastNotes(model, 799));
		assertTrue(SkinPreviewPlayer.countPastNotes(model, 800) > 0);
		assertEquals(model.getTotalNotes(),
				SkinPreviewPlayer.countPastNotes(model, model.getLastTime()));
		assertEquals(-1, SkinPreviewPlayer.latestJudgementTime(model, 0, 799));
		assertTrue(SkinPreviewPlayer.latestJudgementTime(model, 0, model.getLastTime()) >= 800);
	}

	@Test
	void previewBufferUsesDestinationSizeAndStaysBounded() {
		assertEquals(640, SkinPreview.bufferDimension(1920, 640));
		assertEquals(360, SkinPreview.bufferDimension(1080, 360));
		assertEquals(1280, SkinPreview.bufferDimension(1280, 4096));
		assertEquals(2048, SkinPreview.bufferDimension(8192, 4096));
		assertEquals(1, SkinPreview.bufferDimension(0, 0));
	}
}
