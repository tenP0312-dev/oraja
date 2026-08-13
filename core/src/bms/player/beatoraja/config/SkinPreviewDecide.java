package bms.player.beatoraja.config;

import bms.player.beatoraja.MainController;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.decide.MusicDecide;
import bms.player.beatoraja.skin.Skin;

import static bms.player.beatoraja.skin.SkinProperty.TIMER_FADEOUT;
import static bms.player.beatoraja.skin.SkinProperty.TIMER_STARTINPUT;

/** MusicDecide-compatible virtual state used only by Skin Select. */
final class SkinPreviewDecide extends MusicDecide implements SkinPreviewState {
	SkinPreviewDecide(MainController main, PlayerResource resource) {
		super(main, resource);
	}

	void attachSkin(Skin skin) {
		setSkinForPreview(skin);
	}

	@Override public void create() {}
	@Override public void render() {}

	@Override
	public long preparePreviewFrame(Skin skin) {
		timer.update();
		SkinPreviewLifecycle.SceneFrame frame = SkinPreviewLifecycle.sceneFrame(
				timer.getNowTime(), skin.getInput(), skin.getScene(), skin.getFadeout());
		SkinPreviewLifecycle.setTimer(timer, TIMER_STARTINPUT, frame.inputTime());
		SkinPreviewLifecycle.setTimer(timer, TIMER_FADEOUT, frame.fadeoutTime());
		return timer.getNowTime();
	}
}
