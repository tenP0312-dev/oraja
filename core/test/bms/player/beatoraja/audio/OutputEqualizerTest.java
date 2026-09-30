package bms.player.beatoraja.audio;

import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.AudioConfig.EqualizerMode;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class OutputEqualizerTest {
    private AudioConfig config(EqualizerMode mode, int band, double gain) {
        AudioConfig config = new AudioConfig();
        config.setEqualizerMode(mode);
        double[] gains = new double[mode.getFrequencies().length];
        if (band >= 0) gains[band] = gain;
        if (mode == EqualizerMode.SWITCH) config.setSwitchEqualizerGains(gains);
        else config.setLr2EqualizerGains(gains);
        return config;
    }

    @Test void offAndFlatAreBitExactIncludingOutOfRangeSamples() {
        for (EqualizerMode mode : EqualizerMode.values()) {
            AudioConfig config = config(mode, -1, 0);
            if (mode == EqualizerMode.OFF) {
                config.setSwitchEqualizerGains(new double[] {12, 12, 12, 12});
                config.setEqualizerPreamp(-24);
            }
            float[] input = {0, -0.0f, 0.125f, -0.75f, 2, -3};
            float[] output = input.clone();
            new OutputEqualizer(config, 48000, 2).process(output);
            assertArrayEquals(input, output);
        }
    }

    @Test void eachBandHasRequestedGainAtItsCenterOnBothCommonRates() {
        for (int sampleRate : new int[] {44100, 48000}) {
            for (EqualizerMode mode : new EqualizerMode[] {EqualizerMode.SWITCH, EqualizerMode.LR2}) {
                double[] frequencies = mode.getFrequencies();
                for (int band = 0; band < frequencies.length; band++) {
                    for (double gain : new double[] {-12, 6, 12}) {
                        assertEquals(gain, measuredGain(config(mode, band, gain), sampleRate, frequencies[band]), 0.03,
                                mode + " band " + band + " rate " + sampleRate);
                    }
                }
            }
        }
    }

    // Measure actual steady-state PCM amplitude, independently of filter coefficients.
    private double measuredGain(AudioConfig config, int sampleRate, double frequency) {
        float[] samples = new float[sampleRate * 2];
        for (int i = 0; i < samples.length; i++) samples[i] = (float) (0.05 * Math.sin(2 * Math.PI * frequency * i / sampleRate));
        float[] original = samples.clone();
        new OutputEqualizer(config, sampleRate, 1).process(samples);
        double input = 0, output = 0;
        for (int i = sampleRate; i < samples.length; i++) {
            input += original[i] * original[i];
            output += samples[i] * samples[i];
        }
        return 10 * Math.log10(output / input);
    }

    @Test void lr2BandIsNarrowerThanSwitchBandAtSameCenter() {
        double broad = measuredGain(config(EqualizerMode.SWITCH, 1, 12), 48000, 800);
        double narrow = measuredGain(config(EqualizerMode.LR2, 2, 12), 48000, 800);
        assertTrue(broad > narrow + 1);
    }

    @Test void stereoChannelsAreIndependentAndHistorySurvivesBufferBoundaries() {
        AudioConfig config = config(EqualizerMode.LR2, 3, 12);
        float[] samples = new float[2000];
        samples[0] = 0.1f;
        float[] whole = samples.clone();
        new OutputEqualizer(config, 48000, 2).process(whole);
        OutputEqualizer split = new OutputEqualizer(config, 48000, 2);
        float[] first = Arrays.copyOfRange(samples, 0, 384);
        float[] rest = Arrays.copyOfRange(samples, 384, samples.length);
        split.process(first);
        split.process(rest);
        assertArrayEquals(Arrays.copyOfRange(whole, 0, 384), first);
        assertArrayEquals(Arrays.copyOfRange(whole, 384, whole.length), rest);
        for (int i = 1; i < whole.length; i += 2) assertEquals(0, whole[i]);
    }

    @Test void preampAttenuatesAndConfigurationIsSnapshotted() {
        AudioConfig config = config(EqualizerMode.SWITCH, -1, 0);
        config.setEqualizerPreamp(-12);
        OutputEqualizer eq = new OutputEqualizer(config, 48000, 1);
        config.setEqualizerPreamp(0);
        float[] samples = {1, -1};
        eq.process(samples);
        assertEquals(Math.pow(10, -12.0 / 20), samples[0], 1e-7);
        assertEquals(-samples[0], samples[1]);
    }

    @Test void lowRatesStayFiniteAndUnrepresentableLr2BandIsBypassed() {
        AudioConfig config = config(EqualizerMode.LR2, 6, 12);
        float[] samples = {0.1f, -0.1f};
        new OutputEqualizer(config, 22050, 1).process(samples);
        assertArrayEquals(new float[] {0.1f, -0.1f}, samples);
        config.setEqualizerMode(EqualizerMode.SWITCH);
        config.setSwitchEqualizerGains(new double[] {12, -12, 12, -12});
        float[] impulse = new float[16000];
        impulse[0] = 0.1f;
        new OutputEqualizer(config, 8000, 1).process(impulse);
        for (float sample : impulse) assertTrue(Float.isFinite(sample));
        assertEquals(0, impulse[impulse.length - 1], 1e-6);
    }
}
