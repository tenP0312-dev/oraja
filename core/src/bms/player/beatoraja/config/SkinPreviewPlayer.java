package bms.player.beatoraja.config;

import bms.model.BMSModel;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.play.BMSPlayer;
import bms.player.beatoraja.play.GrooveGauge;
import bms.player.beatoraja.play.JudgeManager;
import bms.player.beatoraja.play.LaneProperty;
import bms.player.beatoraja.play.LaneRenderer;
import bms.player.beatoraja.skin.Skin;

import static bms.player.beatoraja.skin.SkinProperty.TIMER_PLAY;

/** Minimal autoplay-shaped BMSPlayer used only to evaluate a play skin. */
final class SkinPreviewPlayer extends BMSPlayer {
	private final BMSModel previewModel;
	private LaneProperty previewLaneProperty;
	private LaneRenderer previewLaneRenderer;
	private JudgeManager previewJudge;
	private GrooveGauge previewGauge;

	SkinPreviewPlayer(MainController main, PlayerResource resource) {
		super(main, resource, true);
		previewModel = resource.getBMSModel();
	}

	void attachSkin(Skin skin) {
		setSkinForPreview(skin);
		previewLaneProperty = new LaneProperty(previewModel.getMode());
		previewJudge = new JudgeManager(this);
		previewLaneRenderer = new LaneRenderer(this, previewModel);
		previewJudge.init(previewModel, resource);
		previewGauge = GrooveGauge.create(
				previewModel, resource.getPlayerConfig().getGauge(), resource);
		resource.setGrooveGauge(previewGauge);
		getScoreDataProperty().setTargetScore(0, null, 0, null, previewModel.getTotalNotes());
	}

	@Override public void create() {}
	@Override public void render() {}
	@Override public int getState() { return STATE_PLAY; }
	@Override public LaneRenderer getLanerender() { return previewLaneRenderer; }
	@Override public LaneProperty getLaneProperty() { return previewLaneProperty; }
	@Override public JudgeManager getJudgeManager() { return previewJudge; }
	@Override public GrooveGauge getGauge() { return previewGauge; }
	@Override public int getJudgeCount(int judge, boolean fast) {
		return previewJudge != null ? previewJudge.getJudgeCount(judge, fast) : 0;
	}
	@Override public boolean isNoteEnd() { return false; }
	@Override public int getPastNotes() {
		return previewJudge != null ? previewJudge.getPastNotes() : 0;
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
