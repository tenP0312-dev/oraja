package bms.player.beatoraja.config;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Note;
import bms.model.TimeLine;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.Resolution;
import bms.player.beatoraja.TimerManager;
import bms.player.beatoraja.skin.CustomTimer;
import bms.player.beatoraja.skin.Skin;
import bms.player.beatoraja.skin.SkinHeader;
import bms.player.beatoraja.skin.SkinObject;
import bms.player.beatoraja.skin.SkinType;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static bms.player.beatoraja.skin.SkinProperty.TIMER_JUDGE_1P;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinPreviewTest {
	@Test
	void skinSelectPreviewSettingDefaultsOnAndRoundTripsThroughPlayerConfig() {
		PlayerConfig player = new PlayerConfig();
		assertTrue(player.isSkinSelectPreviewEnabled());

		player.setSkinSelectPreviewEnabled(false);
		PlayerConfig restored = new Json().fromJson(
				PlayerConfig.class,
				PlayerConfig.getConfigJson(player)
		);

		assertFalse(restored.isSkinSelectPreviewEnabled());
	}

	@Test
	void disabledSkinSelectPreviewBlocksLoadingUntilReenabled() {
		assertFalse(SkinConfiguration.shouldLoadPreview(
				false,
				SkinType.PLAY_7KEYS,
				true
		));
		assertTrue(SkinConfiguration.shouldLoadPreview(
				true,
				SkinType.PLAY_7KEYS,
				true
		));
		assertFalse(SkinConfiguration.shouldLoadPreview(
				true,
				SkinType.SKIN_SELECT,
				true
		));
		assertFalse(SkinConfiguration.shouldLoadPreview(
				true,
				SkinType.PLAY_7KEYS,
				false
		));
	}

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
		var preload = SkinPreviewLifecycle.playFrame(0, 0, 1_000, 10_000, 500, 500);
		var ready = SkinPreviewLifecycle.playFrame(1_000, 0, 1_000, 10_000, 500, 500);
		var play = SkinPreviewLifecycle.playFrame(2_000, 0, 1_000, 10_000, 500, 500);
		var finish = SkinPreviewLifecycle.playFrame(12_000, 0, 1_000, 10_000, 500, 500);
		var looped = SkinPreviewLifecycle.playFrame(13_000, 0, 1_000, 10_000, 500, 500);

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
	void phaseLengthsFollowTheSkinInsteadOfPreviewMinimums() {
		var ready = SkinPreviewLifecycle.playFrame(1_000, 0, 0, 10_000, 0, 200);
		var fading = SkinPreviewLifecycle.playFrame(11_100, 0, 0, 10_000, 0, 200);
		var shortScene = SkinPreviewLifecycle.sceneFrame(1_100, 0, 1_000, 300);

		assertEquals(SkinPreviewLifecycle.PlayPhase.PLAY, ready.phase());
		assertEquals(100L, fading.fadeoutTime());
		assertEquals(11_200L, fading.cycle());
		assertEquals(100L, shortScene.fadeoutTime());
		assertEquals(1_800L, shortScene.cycle());
	}

	@Test
	void resultScoreUpdateIsImmediateWhenTheRealStateTurnsItOnAtOnce() {
		var delayed = SkinPreviewLifecycle.sceneFrame(100, 0, 5000, 500, false);
		var immediate = SkinPreviewLifecycle.sceneFrame(100, 0, 5000, 500, true);

		assertEquals(-1L, delayed.updateTime());
		assertEquals(100L, immediate.updateTime());
	}

	@Test
	void everySceneLoopRestartsItsOneShotDestinationTimeline() {
		var firstIntro = SkinPreviewLifecycle.sceneFrame(250, 1000, 5000, 700);
		var firstEnd = SkinPreviewLifecycle.sceneFrame(
				firstIntro.cycle() - 1, 1000, 5000, 700);
		var secondIntro = SkinPreviewLifecycle.sceneFrame(
				firstIntro.cycle() + 250, 1000, 5000, 700);
		DummySkinObject oneShot = new DummySkinObject();
		oneShot.setDestination(0, 0, 0, 1, 1, 0, 255, 255, 255, 255,
				0, 0, 0, 0, -1, 0, 0, 0, 0, 0);
		oneShot.setDestination(500, 0, 0, 1, 1, 0, 0, 255, 255, 255,
				0, 0, 0, 0, -1, 0, 0, 0, 0, 0);

		oneShot.prepare(firstIntro.position(), null);
		assertTrue(oneShot.draw);
		oneShot.prepare(firstEnd.position(), null);
		assertFalse(oneShot.draw);
		oneShot.prepare(secondIntro.position(), null);
		assertTrue(oneShot.draw);
		assertEquals(firstIntro.position(), secondIntro.position());
		assertEquals(1, secondIntro.iteration());
	}

	@Test
	void loopBoundaryClearsStandardAndPassiveCustomTimerCaches() {
		TimerManager timer = new TimerManager();
		timer.setMicroTimer(TIMER_JUDGE_1P, 123L);
		CustomTimer customTimer = new CustomTimer(10_001, null);
		customTimer.setMicroTimer(456L);

		timer.resetSkinPreviewCycle();
		customTimer.resetSkinPreviewCycle();

		assertFalse(timer.isTimerOn(TIMER_JUDGE_1P));
		assertEquals(Long.MIN_VALUE, customTimer.getMicroTimer());
	}

	@Test
	void preparedPreviewRestartsAtPreloadInsteadOfCountingSkinLoadTime() {
		TimerManager timer = new TimerManager();
		timer.setMainState(null);
		timer.setMicroTimer(TIMER_JUDGE_1P, 123L);

		SkinPreviewLifecycle.restartPreparedPreviewClock(timer, null);
		timer.update();
		var firstFrame = SkinPreviewLifecycle.playFrame(
				timer.getNowTime(), 0, 0, 10_000, 500, 500);

		assertEquals(SkinPreviewLifecycle.PlayPhase.PRELOAD, firstFrame.phase());
		assertEquals(0L, firstFrame.iteration());
		assertFalse(timer.isTimerOn(TIMER_JUDGE_1P));
	}

	@Test
	void previewClockStartsAtTheFirstRenderedFrame() {
		var clock = new SkinPreviewLifecycle.Clock();

		assertEquals(0L, clock.elapsed(4_321L));
		assertEquals(250L, clock.elapsed(4_571L));
	}

	@Test
	void previewStateDoesNotCountTimeSpentBeforeItsFirstFrame() throws InterruptedException {
		SkinPreviewDecide decide = new SkinPreviewDecide(null, null);
		SkinHeader header = new SkinHeader();
		header.setSourceResolution(Resolution.HD);
		header.setDestinationResolution(Resolution.HD);
		Skin skin = new Skin(header);
		skin.setInput(500);
		skin.setScene(3000);
		skin.setFadeout(500);
		// Stands in for loading and preparing a heavy skin after the state exists.
		Thread.sleep(50L);

		long first = decide.preparePreviewFrame(skin);
		long second = decide.preparePreviewFrame(skin);

		assertEquals(0L, first);
		assertTrue(second >= first);
		assertTrue(decide.timer.getNowTime() >= 50L);
	}

	@Test
	void notesThatReachedTheJudgeLineAreJudgedAndRewindEachLoop() {
		BMSModel model = SkinPreviewModel.create(bms.model.Mode.BEAT_7K);
		long firstNoteMillis = SkinPreviewModel.LEAD_IN_MICROS / 1000L;

		int judged = SkinPreviewPlayer.markJudgedNotes(model, 0, -1L);
		assertEquals(0, judged);
		judged = SkinPreviewPlayer.markJudgedNotes(model, judged, firstNoteMillis);
		assertTrue(judged > 0);
		judged = SkinPreviewPlayer.markJudgedNotes(model, judged, model.getLastTime());

		for (TimeLine timeline : model.getAllTimeLines()) {
			for (int lane = 0; lane < model.getMode().key; lane++) {
				Note note = timeline.getNote(lane);
				if (note != null) {
					assertEquals(1, note.getState());
				}
			}
		}

		SkinPreviewPlayer.clearNoteStates(model);
		for (TimeLine timeline : model.getAllTimeLines()) {
			for (int lane = 0; lane < model.getMode().key; lane++) {
				Note note = timeline.getNote(lane);
				if (note != null) {
					assertEquals(0, note.getState());
				}
			}
		}
	}

	@Test
	void notesAheadOfTheLaneClockStayUnjudged() {
		BMSModel model = SkinPreviewModel.create(bms.model.Mode.BEAT_7K);
		long firstNoteMillis = SkinPreviewModel.LEAD_IN_MICROS / 1000L;

		SkinPreviewPlayer.markJudgedNotes(model, 0, firstNoteMillis - 1);

		for (TimeLine timeline : model.getAllTimeLines()) {
			for (int lane = 0; lane < model.getMode().key; lane++) {
				Note note = timeline.getNote(lane);
				if (note != null) {
					assertEquals(0, note.getState());
				}
			}
		}
	}

	@Test
	void rhythmAndQuarterNoteTimingFollowTheMeasureLinesLikeRealPlay() {
		BMSModel model = SkinPreviewModel.create(bms.model.Mode.BEAT_7K);
		long firstSectionLine = -1L;
		for (TimeLine timeline : model.getAllTimeLines()) {
			if (timeline.getSectionLine()) {
				firstSectionLine = timeline.getTime();
				break;
			}
		}
		assertTrue(firstSectionLine > 0L);
		long quarter = Math.round(60000d / SkinPreviewModel.BPM);

		assertEquals(-1L, SkinPreviewPlayer.quarterNoteElapsed(model, firstSectionLine - 1));
		assertEquals(0L, SkinPreviewPlayer.quarterNoteElapsed(model, firstSectionLine));
		assertEquals(100L, SkinPreviewPlayer.quarterNoteElapsed(model, firstSectionLine + quarter + 100));

		assertEquals(-1L, SkinPreviewPlayer.rhythmTimerElapsed(model, -1L));
		assertEquals(0L, SkinPreviewPlayer.rhythmTimerElapsed(model, firstSectionLine));
		assertEquals(Math.round(800 * SkinPreviewModel.BPM / 6000d),
				SkinPreviewPlayer.rhythmTimerElapsed(model, firstSectionLine + 800));
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
	}

	@Test
	void judgementEventsCoverEveryJudgedNoteInChartOrder() {
		BMSModel model = SkinPreviewModel.create(bms.model.Mode.BEAT_14K);
		long[] times = SkinPreviewPlayer.judgementTimes(model);

		assertEquals(model.getTotalNotes(), times.length);
		assertEquals(SkinPreviewModel.LEAD_IN_MICROS / 1000L, times[0]);
		for (int i = 1; i < times.length; i++) {
			assertTrue(times[i - 1] <= times[i]);
		}
	}

	@Test
	void previewJudgementsMatchTheScoreSplitAndRankTimers() {
		assertEquals(1, SkinPreviewPlayer.judgeOf(1));
		assertEquals(0, SkinPreviewPlayer.judgeOf(2));
		assertEquals(0, SkinPreviewPlayer.judgeOf(4));
		int greats = 0;
		for (int notes = 1; notes <= 400; notes++) {
			greats += SkinPreviewPlayer.judgeOf(notes);
		}
		assertEquals(400 - 400 * 3 / 4, greats);
		assertEquals(400 * 3 / 4 * 2 + 100, SkinPreviewPlayer.exScore(400));

		boolean[] early = SkinPreviewPlayer.scoreTimersReached(100, 400, 400, 600);
		boolean[] late = SkinPreviewPlayer.scoreTimersReached(700, 400, 400, 600);
		assertFalse(early[0]);
		assertFalse(early[3]);
		assertTrue(late[0]);
		assertTrue(late[1]);
		assertFalse(late[2]);
		assertTrue(late[3]);
		assertTrue(late[4]);
	}

	@Test
	void timersStayOffUntilTheirEventAndThenCountFromIt() {
		assertEquals(-1L, SkinPreviewPlayer.elapsedSince(500L, Long.MIN_VALUE));
		assertEquals(-1L, SkinPreviewPlayer.elapsedSince(500L, 600L));
		assertEquals(200L, SkinPreviewPlayer.elapsedSince(800L, 600L));
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

	private static final class DummySkinObject extends SkinObject {
		@Override public void draw(Skin.SkinObjectRenderer sprite) {}
		@Override public void dispose() {}
	}
}
