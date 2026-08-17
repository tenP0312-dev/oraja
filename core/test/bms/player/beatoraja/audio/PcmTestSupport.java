package bms.player.beatoraja.audio;

import bms.player.beatoraja.song.SongResource;

import java.io.IOException;
import java.nio.file.Path;

/** Shared test-only access to the package-private PCM driver contract. */
public final class PcmTestSupport {

    private PcmTestSupport() {
    }

    public static PCM load(SongResource resource, int sampleRate) throws IOException {
        return PCM.loadBounded(resource, new TestAudioDriver(sampleRate), 1024L * 1024L);
    }

    private static final class TestAudioDriver extends AbstractAudioDriver<PCM> {

        private TestAudioDriver(int sampleRate) {
            super(1);
            setSampleRate(sampleRate);
        }

        @Override protected PCM getKeySound(Path path) { return null; }
        @Override protected PCM getKeySound(PCM pcm) { return pcm; }
        @Override protected void disposeKeySound(PCM pcm) {}
        @Override protected void play(PCM pcm, int channel, float volume, float pitch) {}
        @Override protected void play(AudioElement<PCM> id, float volume, boolean loop) {}
        @Override protected void setVolume(AudioElement<PCM> id, float volume) {}
        @Override protected boolean isPlaying(PCM id) { return false; }
        @Override protected void stop(PCM id) {}
        @Override protected void stop(PCM id, int channel) {}
        @Override protected void setVolume(PCM id, int channel, float volume) {}
        @Override public void dispose() {}
    }
}
