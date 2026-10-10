package bms.player.beatoraja.audio;

import bms.player.beatoraja.AudioConfig;

/** Publishes prepared filters from the UI; only the mixer owns filter history. */
public final class LiveOutputEqualizer {
    private final int sampleRate;
    private final int channels;
    private volatile OutputEqualizer current;

    public LiveOutputEqualizer(AudioConfig config, int sampleRate, int channels) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        update(config);
    }

    public void update(AudioConfig config) {
        current = new OutputEqualizer(config, sampleRate, channels);
    }

    public void process(float[] samples) {
        // One complete buffer uses one snapshot, even if the UI updates meanwhile.
        current.process(samples);
    }
}
