package bms.player.beatoraja.config;

import bms.player.beatoraja.MainState;
import bms.player.beatoraja.TimerManager;

/** Pure timeline calculations shared by the stateful Skin Select previews. */
final class SkinPreviewLifecycle {
	/** BMSPlayer waits at least one second in PRELOAD before READY. */
	private static final long MIN_LOAD_MS = 1000L;
	private static final long DEFAULT_SCENE_MS = 6000L;
	private static final long MAX_SCENE_MS = 12000L;
	/** Keeps the faded last frame briefly before a scene preview repeats. */
	private static final long SCENE_HOLD_MS = 500L;
	/** Simulated confirm press that reveals a result skin's score update. */
	private static final long SCORE_UPDATE_PRESS_MS = 1500L;

	private SkinPreviewLifecycle() {}

	enum PlayPhase {
		PRELOAD, READY, PLAY, FINISHED
	}

	record PlayFrame(
			long position,
			long cycle,
			long iteration,
			PlayPhase phase,
			long phaseTime,
			long readyTime,
			long playTime,
			long musicEndTime,
			long fadeoutTime) {}

	record SceneFrame(
			long position,
			long cycle,
			long iteration,
			long inputTime,
			long updateTime,
			long fadeoutTime) {}

	static PlayFrame playFrame(
			long elapsed,
			long configuredLoad,
			long configuredReady,
			long playDuration,
			long finishMargin,
			long fadeoutDuration) {
		// Phase lengths follow BMSPlayer.render so skin animations keep their
		// real timing; only the one-second PRELOAD floor is the body's own.
		long load = Math.max(MIN_LOAD_MS, configuredLoad);
		long ready = Math.max(0L, configuredReady);
		long play = Math.max(1L, playDuration);
		long finishBeforeFade = Math.max(0L, finishMargin);
		long fadeout = Math.max(0L, fadeoutDuration);
		long finish = Math.max(1L, finishBeforeFade + fadeout);
		long cycle = load + ready + play + finish;
		long position = Math.floorMod(elapsed, cycle);
		long iteration = Math.floorDiv(elapsed, cycle);

		if (position < load) {
			return new PlayFrame(position, cycle, iteration, PlayPhase.PRELOAD,
					position, -1L, -1L, -1L, -1L);
		}
		if (position < load + ready) {
			long phaseTime = position - load;
			return new PlayFrame(position, cycle, iteration, PlayPhase.READY,
					phaseTime, phaseTime, -1L, -1L, -1L);
		}
		if (position < load + ready + play) {
			long phaseTime = position - load - ready;
			return new PlayFrame(position, cycle, iteration, PlayPhase.PLAY,
					phaseTime, ready + phaseTime, phaseTime, -1L, -1L);
		}

		long phaseTime = position - load - ready - play;
		long fadeoutTime = phaseTime >= finishBeforeFade
				? phaseTime - finishBeforeFade : -1L;
		return new PlayFrame(position, cycle, iteration, PlayPhase.FINISHED,
				phaseTime, ready + play + phaseTime, play + phaseTime,
				phaseTime, fadeoutTime);
	}

	static SceneFrame sceneFrame(
			long elapsed, long configuredInput, long configuredScene, long configuredFadeout) {
		return sceneFrame(elapsed, configuredInput, configuredScene, configuredFadeout, false);
	}

	/**
	 * Decide and result scenes fade out at the skin's scene time and finish
	 * after its fadeout, as in MusicDecide/MusicResult. Scenes that wait for a
	 * key (no or very long scene time) use a bounded stand-in length.
	 *
	 * @param immediateScoreUpdate true when the real state turns the score
	 *            update timer on from the first frame (no rank time, course result)
	 */
	static SceneFrame sceneFrame(long elapsed, long configuredInput, long configuredScene,
			long configuredFadeout, boolean immediateScoreUpdate) {
		long scene = configuredScene > 0L && configuredScene <= MAX_SCENE_MS
				? configuredScene : DEFAULT_SCENE_MS;
		long fadeout = Math.max(0L, configuredFadeout);
		long cycle = scene + fadeout + SCENE_HOLD_MS;
		long position = Math.floorMod(elapsed, cycle);
		long iteration = Math.floorDiv(elapsed, cycle);
		long inputAt = Math.max(0L, configuredInput);
		long updateAt = immediateScoreUpdate ? 0L : Math.max(SCORE_UPDATE_PRESS_MS, inputAt);
		return new SceneFrame(
				position,
				cycle,
				iteration,
				position >= inputAt ? position - inputAt : -1L,
				position >= updateAt ? position - updateAt : -1L,
				position >= scene ? position - scene : -1L);
	}

	/**
	 * Measures a preview timeline from its first rendered frame. Preview states
	 * are constructed before their skin is loaded, so the state clock already
	 * contains the load time. The TimerManager itself stays monotonic.
	 */
	static final class Clock {
		private long origin = Long.MIN_VALUE;

		long elapsed(long now) {
			if (origin == Long.MIN_VALUE) {
				origin = now;
			}
			return now - origin;
		}
	}

	static void setTimer(TimerManager timer, int id, long elapsedSinceStart) {
		if (elapsedSinceStart >= 0L) {
			timer.setMicroTimer(id,
					timer.getNowMicroTime() - elapsedSinceStart * 1000L);
		} else {
			timer.setTimerOff(id);
		}
	}

	/**
	 * Starts a freshly prepared preview at its real first frame. Preview states
	 * are constructed before their skin is loaded, so keeping that construction
	 * clock would make a heavy skin appear several seconds into its timeline.
	 */
	static void restartPreparedPreviewClock(TimerManager timer, MainState state) {
		timer.setMainState(state);
	}
}
