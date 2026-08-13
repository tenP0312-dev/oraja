package bms.player.beatoraja.config;

import bms.player.beatoraja.MainController;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.result.MusicResult;
import bms.player.beatoraja.skin.Skin;

import static bms.player.beatoraja.skin.SkinProperty.*;

/** MusicResult-compatible virtual state used only by Skin Select. */
final class SkinPreviewMusicResult extends MusicResult implements SkinPreviewState {
	SkinPreviewMusicResult(
			MainController main, PlayerResource resource, ScoreData oldScore) {
		super(main, resource);
		initializeSkinPreview(oldScore);
	}

	void attachSkin(Skin skin) {
		setSkinForPreview(skin);
	}

	@Override public void create() {}
	@Override public void render() {}
	@Override public void saveReplayData(int index) {}

	@Override
	public long preparePreviewFrame(Skin skin) {
		timer.update();
		SkinPreviewLifecycle.SceneFrame frame = SkinPreviewLifecycle.sceneFrame(
				timer.getNowTime(), skin.getInput(), skin.getScene(), skin.getFadeout());
		SkinPreviewLifecycle.setTimer(timer, TIMER_RESULTGRAPH_BEGIN, frame.position());
		SkinPreviewLifecycle.setTimer(timer, TIMER_RESULTGRAPH_END, frame.position());
		SkinPreviewLifecycle.setTimer(timer, TIMER_RESULT_UPDATESCORE, frame.updateTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_STARTINPUT, frame.inputTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_FADEOUT, frame.fadeoutTime());
		return timer.getNowTime();
	}
}
