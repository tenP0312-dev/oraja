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

import static bms.player.beatoraja.skin.SkinProperty.*;

/** Stateful autoplay-shaped BMSPlayer used only to evaluate a play skin. */
final class SkinPreviewPlayer extends BMSPlayer implements SkinPreviewState {
	private static final long TAP_HOLD_MILLIS = 100L;

	private final BMSModel previewModel;
	private ScoreData previewScore;
	private LaneProperty previewLaneProperty;
	private LaneRenderer previewLaneRenderer;
	private JudgeManager previewJudge;
	private GrooveGauge previewGauge;
	private int previewState = STATE_PRELOAD;
	private int previewPastNotes;
	private long previewIteration = Long.MIN_VALUE;

	SkinPreviewPlayer(MainController main, PlayerResource resource) {
		super(main, resource, true);
		previewModel = resource.getBMSModel();
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
			resetPreviewCycle();
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
		long lastNote = Math.max(0L, previewModel.getLastNoteTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_ENDOFNOTE_1P,
				frame.playTime() >= lastNote ? frame.playTime() - lastNote : -1L);
		SkinPreviewLifecycle.setTimer(timer, TIMER_ENDOFNOTE_2P,
				previewModel.getMode().player == 2 && frame.playTime() >= lastNote
						? frame.playTime() - lastNote : -1L);
		SkinPreviewLifecycle.setTimer(timer, TIMER_FULLCOMBO_1P,
				frame.phase() == SkinPreviewLifecycle.PlayPhase.FINISHED
						? frame.phaseTime() : -1L);

		if (frame.phase() == SkinPreviewLifecycle.PlayPhase.PLAY && previewPastNotes > 0) {
			updateJudgementEffects(frame.playTime());
		} else {
			SkinPreviewLifecycle.setTimer(timer, TIMER_JUDGE_1P, -1L);
			SkinPreviewLifecycle.setTimer(timer, TIMER_COMBO_1P, -1L);
			previewJudge.clearSkinPreviewJudgement();
			previewJudge.clearSkinPreviewLongNotes();
			clearLaneEffects();
		}
		return timer.getNowTime();
	}

	private void resetPreviewCycle() {
		previewLaneRenderer.resetSkinPreviewTimeline();
		previewJudge.clearSkinPreviewJudgement();
		previewJudge.clearSkinPreviewLongNotes();
		clearLaneEffects();
	}

	private void updateLiveData(long playTime) {
		previewPastNotes = countPastNotes(previewModel, playTime);
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
		for (TimeLine timeline : model.getAllTimeLines()) {
			if (timeline.getTime() > playTime) {
				break;
			}
			count += timeline.getTotalNotes(model.getLntype());
		}
		return Math.min(count, model.getTotalNotes());
	}

	private void updateJudgementEffects(long playTime) {
		long latestTime = -1L;
		int latestLane = -1;
		for (int lane = 0; lane < previewModel.getMode().key; lane++) {
			long laneTime = latestJudgementTime(previewModel, lane, playTime);
			LaneEffect effect = laneEffect(previewModel, lane, playTime);
			setLaneEffectTimers(lane, laneTime, playTime, effect);
			previewJudge.setSkinPreviewLongNote(lane, effect.activeLongNote());
			if (laneTime > latestTime) {
				latestTime = laneTime;
				latestLane = lane;
			}
		}
		long elapsed = latestTime >= 0L ? playTime - latestTime : -1L;
		SkinPreviewLifecycle.setTimer(timer, TIMER_JUDGE_1P, elapsed);
		SkinPreviewLifecycle.setTimer(timer, TIMER_COMBO_1P, elapsed);
		if (latestLane >= 0) {
			previewJudge.setSkinPreviewJudgement(latestLane, 0, previewPastNotes, 0L);
		} else {
			previewJudge.clearSkinPreviewJudgement();
		}
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
