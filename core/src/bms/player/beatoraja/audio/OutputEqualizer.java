package bms.player.beatoraja.audio;

import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.AudioConfig.EqualizerMode;

/** Independent peaking-biquad EQ on interleaved output PCM. One instance per audio stream. */
public final class OutputEqualizer {
    private final double[][] coefficients;
    private final double[][] z1;
    private final double[][] z2;
    private final int channels;
    private final double preamp;
    private final boolean bypass;

    // Settings and coefficients are snapshotted before the mixer starts. The audio
    // thread only updates its own filter history; processing allocates and locks nothing.
    public OutputEqualizer(AudioConfig config, int sampleRate, int channels) {
        if (sampleRate <= 0 || channels <= 0) {
            throw new IllegalArgumentException("Invalid audio format");
        }
        this.channels = channels;
        EqualizerMode mode = config.getEqualizerMode();
        double[] frequencies = mode.getFrequencies();
        double[] gains = mode == EqualizerMode.SWITCH
                ? config.getSwitchEqualizerGains() : config.getLr2EqualizerGains();
        coefficients = new double[frequencies.length][];
        z1 = new double[frequencies.length][channels];
        z2 = new double[frequencies.length][channels];
        preamp = mode == EqualizerMode.OFF ? 1 : Math.pow(10, config.getEqualizerPreamp() / 20);
        boolean flat = true;
        for (int i = 0; i < frequencies.length; i++) {
            double frequency = mode == EqualizerMode.SWITCH && i == 3
                    ? Math.min(frequencies[i], sampleRate * 0.4) : frequencies[i];
            // A band above Nyquist cannot be represented. Do not fold it into the audible range.
            if (gains[i] == 0 || frequency >= sampleRate * 0.5) continue;
            double a = Math.pow(10, gains[i] / 40);
            double w = 2 * Math.PI * frequency / sampleRate;
            double alpha = Math.sin(w) / (2 * mode.getQ());
            double a0 = 1 + alpha / a;
            coefficients[i] = new double[] {
                    (1 + alpha * a) / a0, -2 * Math.cos(w) / a0,
                    (1 - alpha * a) / a0, -2 * Math.cos(w) / a0,
                    (1 - alpha / a) / a0
            };
            flat = false;
        }
        bypass = flat && preamp == 1;
    }

    public void process(float[] samples) {
        if (samples.length % channels != 0) throw new IllegalArgumentException("Incomplete audio frame");
        if (bypass) return;
        for (int frame = 0; frame < samples.length; frame += channels) {
            for (int ch = 0; ch < channels; ch++) {
                double x = samples[frame + ch] * preamp;
                for (int band = 0; band < coefficients.length; band++) {
                    double[] c = coefficients[band];
                    if (c == null) continue;
                    double y = c[0] * x + z1[band][ch];
                    z1[band][ch] = c[1] * x - c[3] * y + z2[band][ch];
                    z2[band][ch] = c[2] * x - c[4] * y;
                    x = y;
                }
                samples[frame + ch] = (float) x;
            }
        }
    }
}
