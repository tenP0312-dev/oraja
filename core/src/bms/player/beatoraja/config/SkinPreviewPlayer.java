package bms.player.beatoraja.config;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.MineNote;
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
	/** Score timers BMSPlayer.update switches on: A, AA, AAA, BEST, TARGET. */
	private static final int[] SCORE_TIMERS = {
			TIMER_SCORE_A, TIMER_SCORE_AA, TIMER_SCORE_AAA,
			TIMER_SCORE_BEST, TIMER_SCORE_TARGET
	};
	private static final int[] SCORE_RANKS = {18, 21, 24};

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
	private int previewJudgedTimelines;
	/** Sorted chart times of every judged note, replayed like real judgements. */
	private final long[] previewJudgementTimes;
	private int previewJudgedEvents;
	private long previewGaugeMaxSince = Long.MIN_VALUE;
	private long previewFullComboSince = Long.MIN_VALUE;
	private final long[] previewScoreTimerSince = new long[SCORE_TIMERS.length];
	private final SkinPreviewLifecycle.Clock previewClock = new SkinPreviewLifecycle.Clock();

	SkinPreviewPlayer(MainController main, PlayerResource resource) {
		super(main, resource, true);
		previewModel = resource.getBMSModel();
		previewPastNotesByPlayer = new int[Math.max(1, previewModel.getMode().player)];
		previewJudgementTimes = judgementTimes(previewModel);
		Arrays.fill(previewScoreTimerSince, Long.MIN_VALUE);
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
		createPreviewGauge();
		getScoreDataProperty().setTargetScore(
				previewModel.getTotalNotes(), null,
				previewModel.getTotalNotes() * 3 / 2, null,
				previewModel.getTotalNotes());
	}

	private void createPreviewGauge() {
		previewGauge = GrooveGauge.create(
				previewModel, resource.getPlayerConfig().getGauge(), resource);
		if (previewGauge == null) {
			previewGauge = GrooveGauge.create(
					previewModel, GrooveGauge.NORMAL, resource);
		}
		resource.setGrooveGauge(previewGauge);
	}

	@Override
	public long preparePreviewFrame(Skin skin) {
		timer.update();
		PlaySkin playSkin = (PlaySkin) skin;
		SkinPreviewLifecycle.PlayFrame frame = SkinPreviewLifecycle.playFrame(
				previewClock.elapsed(timer.getNowTime()),
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
		SkinPreviewLifecycle.setTimer(timer, TIMER_RHYTHM,
				rhythmTimerElapsed(previewModel, frame.playTime()));
		SkinPreviewLifecycle.setTimer(timer, TIMER_MUSIC_END, frame.musicEndTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_FADEOUT, frame.fadeoutTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_FAILED, -1L);
		// Judgements happen when a calibrated player hits the note on the judge
		// line, which LaneRenderer draws at play time + notes display timing.
		long chartTime = frame.playTime() >= 0L
				? frame.playTime() + resource.getPlayerConfig().getJudgetiming() : -1L;
		boolean playing = frame.phase() == SkinPreviewLifecycle.PlayPhase.PLAY;
		if (playing || frame.phase() == SkinPreviewLifecycle.PlayPhase.FINISHED) {
			if (previewGaugeMaxSince == Long.MIN_VALUE && previewGauge.getGauge().isMax()) {
				previewGaugeMaxSince = chartTime;
			}
			advanceJudgements(playSkin, chartTime);
		}
		updateLiveData(chartTime);
		// LaneRenderer keeps drawing unjudged notes below the judge line, so
		// mark them the way judgement does once they reach it.
		previewJudgedTimelines = markJudgedNotes(previewModel, previewJudgedTimelines, chartTime);

		SkinPreviewLifecycle.setTimer(timer, TIMER_GAUGE_MAX_1P,
				elapsedSince(chartTime, previewGaugeMaxSince));
		SkinPreviewLifecycle.setTimer(timer, TIMER_FULLCOMBO_1P,
				elapsedSince(chartTime, previewFullComboSince));
		for (int i = 0; i < SCORE_TIMERS.length; i++) {
			SkinPreviewLifecycle.setTimer(timer, SCORE_TIMERS[i],
					elapsedSince(chartTime, previewScoreTimerSince[i]));
		}
		// BMSPlayer turns this on once play passes playtime - TIME_MARGIN,
		// which is the last note time in ordinary play.
		long lastNote = previewModel.getLastNoteTime();
		SkinPreviewLifecycle.setTimer(timer, TIMER_ENDOFNOTE_1P,
				frame.playTime() >= lastNote ? frame.playTime() - lastNote : -1L);

		updatePomyuChara(playSkin, frame.phase());
		if (playing && previewPastNotes > 0) {
			updateJudgementEffects(chartTime);
		} else {
			clearPlayEffects();
		}
		return frame.position();
	}

	private void resetPreviewCycle(Skin skin) {
		timer.resetSkinPreviewCycle();
		skin.resetSkinPreviewCycle();
		previewLaneRenderer.resetTimelinePosition();
		clearNoteStates(previewModel);
		previewJudgedTimelines = 0;
		previewJudgedEvents = 0;
		previewGaugeMaxSince = Long.MIN_VALUE;
		previewFullComboSince = Long.MIN_VALUE;
		Arrays.fill(previewScoreTimerSince, Long.MIN_VALUE);
		createPreviewGauge();
		((PlaySkin) skin).pomyu.init();
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
	}

	/**
	 * Applies the judgements reached since the previous frame in order, the way
	 * JudgeManager feeds BMSPlayer.update: gauge, score timers, full combo and
	 * the PMS character all react per note.
	 */
	private void advanceJudgements(PlaySkin playSkin, long chartTime) {
		int total = previewJudgementTimes.length;
		while (previewJudgedEvents < total
				&& previewJudgementTimes[previewJudgedEvents] <= chartTime) {
			long time = previewJudgementTimes[previewJudgedEvents];
			int notes = ++previewJudgedEvents;
			int judge = judgeOf(notes);
			previewGauge.update(judge);
			if (previewGaugeMaxSince == Long.MIN_VALUE && previewGauge.getGauge().isMax()) {
				previewGaugeMaxSince = time;
			}
			boolean[] reached = scoreTimersReached(exScore(notes), total,
					getScoreDataProperty().getBestScore(),
					getScoreDataProperty().getRivalScore());
			for (int i = 0; i < reached.length; i++) {
				if (reached[i] && previewScoreTimerSince[i] == Long.MIN_VALUE) {
					previewScoreTimerSince[i] = time;
				}
			}
			if (notes == total) {
				previewFullComboSince = time;
			}
			playSkin.pomyu.setJudge(judge);
		}
	}

	private void updatePomyuChara(PlaySkin playSkin, SkinPreviewLifecycle.PlayPhase phase) {
		switch (phase) {
			case PRELOAD, READY -> {
				if (!timer.isTimerOn(TIMER_PM_CHARA_1P_NEUTRAL)
						|| !timer.isTimerOn(TIMER_PM_CHARA_2P_NEUTRAL)) {
					timer.setTimerOn(TIMER_PM_CHARA_1P_NEUTRAL);
					timer.setTimerOn(TIMER_PM_CHARA_2P_NEUTRAL);
				}
			}
			case PLAY -> playSkin.pomyu.updateTimer(this);
			case FINISHED -> {
				for (int id = TIMER_PM_CHARA_1P_NEUTRAL; id <= TIMER_PM_CHARA_2P_BAD; id++) {
					timer.setTimerOff(id);
				}
				timer.setTimerOff(TIMER_PM_CHARA_DANCE);
			}
		}
	}

	static long elapsedSince(long now, long since) {
		return since != Long.MIN_VALUE && now >= since ? now - since : -1L;
	}

	/** Preview judgements are three PGREATs per GREAT, in a fixed order. */
	static int judgeOf(int notes) {
		return notes * 3 / 4 > (notes - 1) * 3 / 4 ? 0 : 1;
	}

	static int exScore(int notes) {
		int pgreat = notes * 3 / 4;
		return pgreat * 2 + (notes - pgreat);
	}

	/** Mirrors ScoreDataProperty ranks and BMSPlayer's BEST/TARGET checks. */
	static boolean[] scoreTimersReached(int exScore, int totalNotes, int best, int rival) {
		float rate = totalNotes == 0 ? 1f : (float) exScore / (totalNotes * 2);
		boolean[] reached = new boolean[SCORE_TIMERS.length];
		for (int i = 0; i < SCORE_RANKS.length; i++) {
			reached[i] = totalNotes != 0 && rate >= 1f * SCORE_RANKS[i] / 27;
		}
		reached[3] = exScore >= best;
		reached[4] = exScore >= rival;
		return reached;
	}

	static long[] judgementTimes(BMSModel model) {
		int count = 0;
		for (TimeLine timeline : model.getAllTimeLines()) {
			for (int lane = 0; lane < model.getMode().key; lane++) {
				if (isJudgementNote(model, timeline.getNote(lane))) {
					count++;
				}
			}
		}
		long[] times = new long[count];
		int index = 0;
		for (TimeLine timeline : model.getAllTimeLines()) {
			for (int lane = 0; lane < model.getMode().key; lane++) {
				if (isJudgementNote(model, timeline.getNote(lane))) {
					times[index++] = timeline.getTime();
				}
			}
		}
		return times;
	}

	/**
	 * Marks every note up to the lane clock as PGREAT, continuing from the
	 * returned timeline index of the previous frame.
	 */
	static int markJudgedNotes(BMSModel model, int fromTimeline, long laneTime) {
		TimeLine[] timelines = model.getAllTimeLines();
		int index = Math.max(0, fromTimeline);
		if (laneTime < 0L) {
			return index;
		}
		for (; index < timelines.length && timelines[index].getTime() <= laneTime; index++) {
			for (int lane = 0; lane < model.getMode().key; lane++) {
				Note note = timelines[index].getNote(lane);
				if (note != null && !(note instanceof MineNote)) {
					note.setState(1);
				}
			}
		}
		return index;
	}

	static void clearNoteStates(BMSModel model) {
		for (TimeLine timeline : model.getAllTimeLines()) {
			for (int lane = 0; lane < model.getMode().key; lane++) {
				Note note = timeline.getNote(lane);
				if (note != null) {
					note.setState(0);
				}
			}
		}
	}

	/**
	 * Mirrors RhythmTimerProcessor: the timer restarts on each measure line and
	 * then advances at BPM / 6000 of real time.
	 */
	static long rhythmTimerElapsed(BMSModel model, long playTime) {
		if (playTime < 0L) {
			return -1L;
		}
		long lastSectionLine = 0L;
		for (TimeLine timeline : model.getAllTimeLines()) {
			if (timeline.getTime() > playTime) {
				break;
			}
			if (timeline.getSectionLine()) {
				lastSectionLine = timeline.getTime();
			}
		}
		return Math.round((playTime - lastSectionLine) * model.getBpm() / 6000d);
	}

	/**
	 * Time since the latest quarter note, counted from the first measure line
	 * like RhythmTimerProcessor, or -1 before that line.
	 */
	static long quarterNoteElapsed(BMSModel model, long playTime) {
		long firstSectionLine = -1L;
		for (TimeLine timeline : model.getAllTimeLines()) {
			if (timeline.getSectionLine()) {
				firstSectionLine = timeline.getTime();
				break;
			}
		}
		if (firstSectionLine < 0L || playTime < firstSectionLine || model.getBpm() <= 0d) {
			return -1L;
		}
		long quarter = Math.max(1L, Math.round(60000d / model.getBpm()));
		return Math.floorMod(playTime - firstSectionLine, quarter);
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
				// JudgeManager keeps one combo shared by both sides.
				previewJudge.setSkinPreviewJudgement(
						latestLanes[player], judgeOf(previewPastNotes), previewPastNotes, 0L);
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
	@Override public long getNowQuarterNoteTime() {
		// BMSPlayer returns the absolute time of the latest quarter note.
		long elapsed = timer.isTimerOn(TIMER_PLAY)
				? quarterNoteElapsed(previewModel, timer.getNowTime(TIMER_PLAY)) : -1L;
		return elapsed >= 0L ? timer.getNowTime() - elapsed : 0L;
	}

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
