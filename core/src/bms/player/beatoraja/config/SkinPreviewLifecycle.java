package bms.player.beatoraja.config;

import bms.player.beatoraja.TimerManager;

/** Pure timeline calculations shared by the stateful Skin Select previews. */
final class SkinPreviewLifecycle {
	private static final long MIN_LOAD_MS = 1200L;
	private static final long MIN_READY_MS = 1000L;
	private static final long MIN_FINISH_MS = 1200L;
	private static final long DEFAULT_SCENE_MS = 6000L;
	private static final long MAX_SCENE_MS = 12000L;

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
		long load = Math.max(MIN_LOAD_MS, configuredLoad);
		long ready = Math.max(MIN_READY_MS, configuredReady);
		long play = Math.max(1L, playDuration);
		long finishBeforeFade = Math.max(0L, finishMargin);
		long fadeout = Math.max(500L, fadeoutDuration);
		long finish = Math.max(MIN_FINISH_MS, finishBeforeFade + fadeout);
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
		long scene = configuredScene > 0L && configuredScene <= MAX_SCENE_MS
				? Math.max(3000L, configuredScene) : DEFAULT_SCENE_MS;
		long fadeout = Math.max(500L, Math.min(4000L, configuredFadeout));
		long cycle = scene + fadeout;
		long position = Math.floorMod(elapsed, cycle);
		long inputAt = Math.max(0L, Math.min(scene - 1000L, configuredInput));
		long updateAt = Math.max(1500L, inputAt);
		return new SceneFrame(
				position,
				cycle,
				position >= inputAt ? position - inputAt : -1L,
				position >= updateAt ? position - updateAt : -1L,
				position >= scene ? position - scene : -1L);
	}

	static void setTimer(TimerManager timer, int id, long elapsedSinceStart) {
		if (elapsedSinceStart >= 0L) {
			timer.setMicroTimer(id,
					timer.getNowMicroTime() - elapsedSinceStart * 1000L);
		} else {
			timer.setTimerOff(id);
		}
	}
}
