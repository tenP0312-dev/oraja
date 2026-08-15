package bms.player.beatoraja.config;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Note;
import bms.model.TimeLine;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.play.BMSPlayer;
import bms.player.beatoraja.play.GrooveGauge;
import bms.player.beatoraja.play.JudgeManager;
import bms.player.beatoraja.play.LaneProperty;
import bms.player.beatoraja.play.LaneRenderer;
import bms.player.beatoraja.play.PlaySkin;
import bms.player.beatoraja.skin.Skin;
import bms.player.beatoraja.skin.SkinPropertyMapper;

import java.util.Arrays;

import static bms.player.beatoraja.skin.SkinProperty.*;

/** Stateful autoplay-shaped BMSPlayer used only to evaluate a play skin. */
final class SkinPreviewPlayer extends BMSPlayer implements SkinPreviewState {
	private static final long TAP_HOLD_MILLIS = 100L;
	private static final int[] JUDGE_TIMERS = {
			TIMER_JUDGE_1P, TIMER_JUDGE_2P, TIMER_JUDGE_3P
	};
	private static final int[] COMBO_TIMERS = {
			TIMER_COMBO_1P, TIMER_COMBO_2P, TIMER_COMBO_3P
	};
	private static final int[] END_OF_NOTE_TIMERS = {
			TIMER_ENDOFNOTE_1P, TIMER_ENDOFNOTE_2P
	};
	private static final int[] FULL_COMBO_TIMERS = {
			TIMER_FULLCOMBO_1P, TIMER_FULLCOMBO_2P
	};

	private final BMSModel previewModel;
	private ScoreData previewScore;
	private LaneProperty previewLaneProperty;
	private LaneRenderer previewLaneRenderer;
	private JudgeManager previewJudge;
	private GrooveGauge previewGauge;
	private int previewState = STATE_PRELOAD;
	private int previewPastNotes;
	private final int[] previewPastNotesByPlayer;
	private long previewIteration = Long.MIN_VALUE;

	SkinPreviewPlayer(MainController main, PlayerResource resource) {
		super(main, resource, true);
		previewModel = resource.getBMSModel();
		previewPastNotesByPlayer = new int[Math.max(1, previewModel.getMode().player)];
		previewScore = new ScoreData(previewModel.getMode());
		previewScore.setNotes(previewModel.getTotalNotes());
		resource.setScoreData(previewScore);
	}

	void attachSkin(Skin skin) {
		setSkinForPreview(skin);
		previewLaneProperty = new LaneProperty(previewModel.getMode());
		previewJudge = new JudgeManager(this);
		previewLaneRenderer = new LaneRenderer(this, previewModel);
		previewJudge.init(previewModel, resource);
		previewScore = previewJudge.getScoreData();
		resource.setScoreData(previewScore);
		previewGauge = GrooveGauge.create(
				previewModel, resource.getPlayerConfig().getGauge(), resource);
		if (previewGauge == null) {
			previewGauge = GrooveGauge.create(
					previewModel, GrooveGauge.NORMAL, resource);
		}
		resource.setGrooveGauge(previewGauge);
		getScoreDataProperty().setTargetScore(
				previewModel.getTotalNotes(), null,
				previewModel.getTotalNotes() * 3 / 2, null,
				previewModel.getTotalNotes());
	}

	@Override
	public long preparePreviewFrame(Skin skin) {
		timer.update();
		PlaySkin playSkin = (PlaySkin) skin;
		SkinPreviewLifecycle.PlayFrame frame = SkinPreviewLifecycle.playFrame(
				timer.getNowTime(),
				(long) playSkin.getLoadstart() + playSkin.getLoadend(),
				playSkin.getPlaystart(),
				getPlaytime(),
				playSkin.getFinishMargin(),
				playSkin.getFadeout());
		if (previewIteration != frame.iteration()) {
			previewIteration = frame.iteration();
			resetPreviewCycle(skin);
		}

		previewState = switch (frame.phase()) {
			case PRELOAD -> STATE_PRELOAD;
			case READY -> STATE_READY;
			case PLAY -> STATE_PLAY;
			case FINISHED -> STATE_FINISHED;
		};
		SkinPreviewLifecycle.setTimer(timer, TIMER_STARTINPUT,
				frame.position() >= playSkin.getInput()
						? frame.position() - playSkin.getInput() : -1L);
		SkinPreviewLifecycle.setTimer(timer, TIMER_READY, frame.readyTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_PLAY, frame.playTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_RHYTHM, frame.playTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_MUSIC_END, frame.musicEndTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_FADEOUT, frame.fadeoutTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_FAILED, -1L);
		SkinPreviewLifecycle.setTimer(timer, TIMER_PM_CHARA_1P_NEUTRAL, frame.position());
		SkinPreviewLifecycle.setTimer(timer, TIMER_PM_CHARA_2P_NEUTRAL, frame.position());

		long played = Math.max(0L, Math.min(getPlaytime(), frame.playTime()));
		updateLiveData(played);
		for (int player = 0; player < previewPastNotesByPlayer.length; player++) {
			long lastNote = latestJudgementTimeForPlayer(
					previewModel, player, Long.MAX_VALUE);
			SkinPreviewLifecycle.setTimer(timer, END_OF_NOTE_TIMERS[player],
					frame.playTime() >= lastNote && lastNote >= 0L
							? frame.playTime() - lastNote : -1L);
			SkinPreviewLifecycle.setTimer(timer, FULL_COMBO_TIMERS[player],
					frame.phase() == SkinPreviewLifecycle.PlayPhase.FINISHED
							? frame.phaseTime() : -1L);
		}

		if (frame.phase() == SkinPreviewLifecycle.PlayPhase.PLAY && previewPastNotes > 0) {
			updateJudgementEffects(frame.playTime());
		} else {
			clearPlayEffects();
		}
		return frame.position();
	}

	private void resetPreviewCycle(Skin skin) {
		timer.resetSkinPreviewCycle();
		skin.resetSkinPreviewCycle();
		previewLaneRenderer.resetTimelinePosition();
		Arrays.fill(previewPastNotesByPlayer, 0);
		clearPlayEffects();
	}

	private void updateLiveData(long playTime) {
		previewPastNotes = 0;
		for (int player = 0; player < previewPastNotesByPlayer.length; player++) {
			previewPastNotesByPlayer[player] = countPastNotes(
					previewModel, playTime, player);
			previewPastNotes += previewPastNotesByPlayer[player];
		}
		int pgreat = previewPastNotes * 3 / 4;
		int great = previewPastNotes - pgreat;
		previewScore.setEpg(pgreat / 2);
		previewScore.setLpg(pgreat - previewScore.getEpg());
		previewScore.setEgr(great / 2);
		previewScore.setLgr(great - previewScore.getEgr());
		previewScore.setCombo(previewPastNotes);
		previewScore.setPassnotes(previewPastNotes);
		previewScore.setMinbp(0);
		getScoreDataProperty().update(previewScore, previewPastNotes);

		float progress = previewModel.getTotalNotes() > 0
				? (float) previewPastNotes / previewModel.getTotalNotes() : 0f;
		if (previewGauge != null) {
			previewGauge.setValue(Math.min(100f, 22f + progress * 68f));
		}
	}

	static int countPastNotes(BMSModel model, long playTime) {
		int count = 0;
		for (int player = 0; player < Math.max(1, model.getMode().player); player++) {
			count += countPastNotes(model, playTime, player);
		}
		return Math.min(count, model.getTotalNotes());
	}

	static int countPastNotes(BMSModel model, long playTime, int player) {
		int players = Math.max(1, model.getMode().player);
		if (player < 0 || player >= players) {
			return 0;
		}
		int sideWidth = model.getMode().key / players;
		int firstLane = player * sideWidth;
		int lastLane = firstLane + sideWidth;
		int count = 0;
		for (TimeLine timeline : model.getAllTimeLines()) {
			if (timeline.getTime() > playTime) {
				break;
			}
			for (int lane = firstLane; lane < lastLane; lane++) {
				if (isJudgementNote(model, timeline.getNote(lane))) {
					count++;
				}
			}
		}
		return count;
	}

	private void updateJudgementEffects(long playTime) {
		long[] latestTimes = new long[previewPastNotesByPlayer.length];
		int[] latestLanes = new int[previewPastNotesByPlayer.length];
		Arrays.fill(latestTimes, -1L);
		Arrays.fill(latestLanes, -1);
		previewJudge.clearSkinPreviewJudgement();
		for (int lane = 0; lane < previewModel.getMode().key; lane++) {
			long laneTime = latestJudgementTime(previewModel, lane, playTime);
			LaneEffect effect = laneEffect(previewModel, lane, playTime);
			setLaneEffectTimers(lane, laneTime, playTime, effect);
			previewJudge.setSkinPreviewLongNote(lane, effect.activeLongNote());
			int player = previewLaneProperty.getLanePlayer()[lane];
			if (laneTime > latestTimes[player]) {
				latestTimes[player] = laneTime;
				latestLanes[player] = lane;
			}
		}
		for (int player = 0; player < previewPastNotesByPlayer.length; player++) {
			long elapsed = latestTimes[player] >= 0L
					? playTime - latestTimes[player] : -1L;
			SkinPreviewLifecycle.setTimer(timer, JUDGE_TIMERS[player], elapsed);
			SkinPreviewLifecycle.setTimer(timer, COMBO_TIMERS[player], elapsed);
			if (latestLanes[player] >= 0) {
				previewJudge.setSkinPreviewJudgement(
						latestLanes[player], 0, previewPastNotesByPlayer[player], 0L);
			}
		}
	}

	private void clearPlayEffects() {
		for (int player = 0; player < previewPastNotesByPlayer.length; player++) {
			SkinPreviewLifecycle.setTimer(timer, JUDGE_TIMERS[player], -1L);
			SkinPreviewLifecycle.setTimer(timer, COMBO_TIMERS[player], -1L);
		}
		previewJudge.clearSkinPreviewJudgement();
		previewJudge.clearSkinPreviewLongNotes();
		clearLaneEffects();
	}

	private void setLaneEffectTimers(
			int lane, long noteTime, long playTime, LaneEffect effect) {
		int player = previewLaneProperty.getLanePlayer()[lane];
		int offset = previewLaneProperty.getLaneSkinOffset()[lane];
		long elapsed = noteTime >= 0L ? playTime - noteTime : -1L;
		SkinPreviewLifecycle.setTimer(
				timer, SkinPropertyMapper.bombTimerId(player, offset), elapsed);
		SkinPreviewLifecycle.setTimer(
				timer, SkinPropertyMapper.keyOnTimerId(player, offset), effect.keyOnElapsed());
		SkinPreviewLifecycle.setTimer(
				timer, SkinPropertyMapper.keyOffTimerId(player, offset), effect.keyOffElapsed());
		SkinPreviewLifecycle.setTimer(
				timer, SkinPropertyMapper.holdTimerId(player, offset), effect.longNoteElapsed());
	}

	private void clearLaneEffects() {
		for (int lane = 0; lane < previewModel.getMode().key; lane++) {
			setLaneEffectTimers(lane, -1L, 0L, LaneEffect.NONE);
		}
	}

	static LaneEffect laneEffect(BMSModel model, int lane, long playTime) {
		long pressedAt = -1L;
		long releasedAt = -1L;
		LongNote activeLongNote = null;
		for (TimeLine timeline : model.getAllTimeLines()) {
			if (timeline.getTime() > playTime) {
				break;
			}
			Note note = timeline.getNote(lane);
			if (note == null || note instanceof LongNote longNote && longNote.isEnd()) {
				continue;
			}
			pressedAt = timeline.getTime();
			if (note instanceof LongNote longNote && longNote.getPair() != null) {
				releasedAt = longNote.getPair().getTime();
				activeLongNote = playTime < releasedAt ? longNote : null;
			} else {
				releasedAt = pressedAt + TAP_HOLD_MILLIS;
				activeLongNote = null;
			}
		}

		if (pressedAt < 0L) {
			return LaneEffect.NONE;
		}
		if (playTime < releasedAt) {
			long held = playTime - pressedAt;
			return new LaneEffect(held, -1L, activeLongNote,
					activeLongNote != null ? held : -1L);
		}
		return new LaneEffect(-1L, playTime - releasedAt, null, -1L);
	}

	record LaneEffect(
			long keyOnElapsed,
			long keyOffElapsed,
			LongNote activeLongNote,
			long longNoteElapsed) {
		private static final LaneEffect NONE = new LaneEffect(-1L, -1L, null, -1L);
	}

	static long latestJudgementTime(BMSModel model, int lane, long playTime) {
		long latest = -1L;
		for (TimeLine timeline : model.getAllTimeLines()) {
			if (timeline.getTime() > playTime) {
				break;
			}
			Note note = timeline.getNote(lane);
			if (isJudgementNote(model, note)) {
				latest = timeline.getTime();
			}
		}
		return latest;
	}

	static long latestJudgementTimeForPlayer(
			BMSModel model, int player, long playTime) {
		int players = Math.max(1, model.getMode().player);
		if (player < 0 || player >= players) {
			return -1L;
		}
		int sideWidth = model.getMode().key / players;
		int firstLane = player * sideWidth;
		long latest = -1L;
		for (int lane = firstLane; lane < firstLane + sideWidth; lane++) {
			latest = Math.max(latest, latestJudgementTime(model, lane, playTime));
		}
		return latest;
	}

	private static boolean isJudgementNote(BMSModel model, Note note) {
		if (!(note instanceof LongNote longNote)) {
			return note != null;
		}
		return longNote.getType() == LongNote.TYPE_CHARGENOTE
				|| longNote.getType() == LongNote.TYPE_HELLCHARGENOTE
				|| (longNote.getType() == LongNote.TYPE_UNDEFINED
						&& model.getLntype() != BMSModel.LNTYPE_LONGNOTE)
				|| !longNote.isEnd();
	}

	@Override public void create() {}
	@Override public void render() {}
	@Override public int getState() { return previewState; }
	@Override public LaneRenderer getLanerender() { return previewLaneRenderer; }
	@Override public LaneProperty getLaneProperty() { return previewLaneProperty; }
	@Override public JudgeManager getJudgeManager() { return previewJudge; }
	@Override public GrooveGauge getGauge() { return previewGauge; }
	@Override public int getJudgeCount(int judge, boolean fast) {
		return previewScore.getJudgeCount(judge, fast);
	}
	@Override public boolean isNoteEnd() { return previewPastNotes >= previewModel.getTotalNotes(); }
	@Override public int getPastNotes() {
		return previewPastNotes;
	}
	@Override public int getPlaytime() { return Math.max(1, previewModel.getLastTime() + 1000); }
	@Override public long getNowQuarterNoteTime() { return timer.getNowTime(TIMER_PLAY) % 400L; }

	@Override
	public void dispose() {
		setSkinForPreview(null);
		if (previewLaneRenderer != null) {
			previewLaneRenderer.dispose();
			previewLaneRenderer = null;
		}
		getPracticeConfiguration().dispose();
	}
}
