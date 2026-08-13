package bms.player.beatoraja.config;

import bms.model.BMSModel;
import bms.model.Note;
import bms.player.beatoraja.audio.AudioDriver;

/** Audio sink used by the isolated skin-preview resource. */
final class SkinPreviewAudioDriver implements AudioDriver {
	private float pitch = 1f;

	@Override public void play(String path, float volume, boolean loop) {}
	@Override public void setVolume(String path, float volume) {}
	@Override public boolean isPlaying(String path) { return false; }
	@Override public void stop(String path) {}
	@Override public void dispose(String path) {}
	@Override public void setModel(BMSModel model) {}
	@Override public void setAdditionalKeySound(int judge, boolean fast, String path) {}
	@Override public void abort() {}
	@Override public float getProgress() { return 1f; }
	@Override public void play(Note note, float volume, int pitch) {}
	@Override public void play(int judge, boolean fast) {}
	@Override public void stop(Note note) {}
	@Override public void setVolume(Note note, float volume) {}
	@Override public void setGlobalPitch(float pitch) { this.pitch = pitch; }
	@Override public float getGlobalPitch() { return pitch; }
	@Override public void disposeOld() {}
	@Override public void dispose() {}
}
