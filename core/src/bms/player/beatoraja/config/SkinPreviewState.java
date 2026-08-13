package bms.player.beatoraja.config;

import bms.player.beatoraja.skin.Skin;

/** A state that advances the timers and data required by its preview skin. */
interface SkinPreviewState {
	long preparePreviewFrame(Skin skin);
}
