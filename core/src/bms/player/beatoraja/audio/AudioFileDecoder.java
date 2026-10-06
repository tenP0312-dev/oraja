package bms.player.beatoraja.audio;

import java.nio.file.Path;

/**
 * Decodes a whole audio file to mono float samples through the same decoders
 * the player uses, so timing measured on the result matches what is heard.
 * (The bundled MP3 decoder, for example, keeps the encoder delay that ffmpeg
 * would strip.)
 */
public final class AudioFileDecoder {

    private AudioFileDecoder() {
    }

    public record MonoAudio(float[] samples, int sampleRate) {
        public double durationSeconds() {
            return samples.length / (double) sampleRate;
        }
    }

    /** @return the decoded audio, or {@code null} when the file cannot be decoded */
    public static MonoAudio decodeMono(Path path, int sampleRate) {
        PCM pcm = PCM.load(path, new DecodingDriver(sampleRate));
        if (pcm == null || pcm.len <= 0 || pcm.channels <= 0) {
            return null;
        }
        int channels = pcm.channels;
        int frames = pcm.len / channels;
        float[] mono = new float[frames];
        for (int frame = 0; frame < frames; frame++) {
            float sum = 0f;
            int base = pcm.start + frame * channels;
            for (int channel = 0; channel < channels; channel++) {
                sum += sample(pcm, base + channel);
            }
            mono[frame] = sum / channels;
        }
        return new MonoAudio(mono, pcm.sampleRate);
    }

    private static float sample(PCM pcm, int index) {
        if (pcm instanceof ShortPCM shortPcm) {
            return shortPcm.sample[index] / 32768.0f;
        } else if (pcm instanceof ShortDirectPCM directPcm) {
            return directPcm.sample.getShort(index * 2) / 32768.0f;
        } else if (pcm instanceof FloatPCM floatPcm) {
            return floatPcm.sample[index];
        } else if (pcm instanceof BytePCM bytePcm) {
            return bytePcm.sample[index] / 128.0f;
        }
        return 0f;
    }

    private static final class DecodingDriver extends AbstractAudioDriver<PCM> {

        private DecodingDriver(int sampleRate) {
            super(1);
            setSampleRate(sampleRate);
            this.channels = 2;
        }

        @Override
        protected PCM getKeySound(Path path) {
            return PCM.load(path, this);
        }

        @Override
        protected PCM getKeySound(PCM pcm) {
            return pcm;
        }

        @Override
        protected void disposeKeySound(PCM pcm) {
        }

        @Override
        protected void play(PCM pcm, int channel, float volume, float pitch) {
        }

        @Override
        protected void play(AudioElement<PCM> id, float volume, boolean loop) {
        }

        @Override
        protected void setVolume(AudioElement<PCM> id, float volume) {
        }

        @Override
        protected boolean isPlaying(PCM id) {
            return false;
        }

        @Override
        protected void stop(PCM id) {
        }

        @Override
        protected void stop(PCM id, int channel) {
        }

        @Override
        protected void setVolume(PCM id, int channel, float volume) {
        }

        @Override
        public void dispose() {
        }
    }
}
