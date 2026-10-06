package bms.player.beatoraja.generated;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Same synthetic cases as tools/test_estimate_audio_grid.py: exact tempo and
 * beat positions are known, and no third-party audio is needed.
 */
class AudioGridEstimatorTest {
    private static final int RATE = 44100;

    static float[] synthTrack(double bpm, double offset, double duration, long seed, double bpmAfterHalf, double amplitude) {
        Random random = new Random(seed);
        int count = (int) (duration * RATE);
        double[] out = new double[count];
        double[] kick = new double[(int) (0.25 * RATE)];
        double phase = 0.0;
        for (int index = 0; index < kick.length; index++) {
            double t = index / (double) RATE;
            phase += 2 * Math.PI * (120 * Math.exp(-t * 25) + 45) / RATE;
            kick[index] = Math.sin(phase) * Math.exp(-t * 14) * 0.9;
        }
        double[] snare = new double[(int) (0.15 * RATE)];
        for (int index = 0; index < snare.length; index++) {
            snare[index] = random.nextGaussian() * Math.exp(-index / (double) RATE * 30) * 0.5;
        }
        double[] hat = new double[(int) (0.05 * RATE)];
        double previous = random.nextGaussian();
        for (int index = 0; index < hat.length; index++) {
            double next = random.nextGaussian();
            hat[index] = (next - previous) * Math.exp(-index / (double) RATE * 90) * 0.4;
            previous = next;
        }

        int beatIndex = 0;
        double now = offset;
        while (now < duration) {
            double beat = 60.0 / (bpmAfterHalf > 0 && now > duration / 2 ? bpmAfterHalf : bpm);
            add(out, now, beatIndex % 4 == 0 || beatIndex % 4 == 2 ? kick : snare, 1.0);
            add(out, now, hat, 1.0);
            add(out, now + beat / 2, hat, 0.8);
            beatIndex++;
            now += beat;
        }
        double peak = 0.0;
        for (int index = 0; index < count; index++) {
            out[index] += random.nextGaussian() * 0.02;
            peak = Math.max(peak, Math.abs(out[index]));
        }
        float[] samples = new float[count];
        for (int index = 0; index < count; index++) {
            samples[index] = (float) (out[index] * amplitude / (peak * 1.1));
        }
        return samples;
    }

    private static void add(double[] out, double time, double[] sound, double gain) {
        int start = (int) Math.round(time * RATE);
        for (int index = 0; index < sound.length && start + index < out.length; index++) {
            if (start + index >= 0) {
                out[start + index] += sound[index] * gain;
            }
        }
    }

    private static double phaseErrorMs(AudioGridEstimator.Result result, double offset, double bpm) {
        double beat = 60.0 / bpm;
        return Math.abs(AudioGridEstimator.floorMod(result.firstBeatSec() - offset + beat / 2, beat) - beat / 2) * 1000;
    }

    @Test
    void recoversConstantTempoAndBeatPosition() {
        double[][] cases = {{147.37, 0.337, 1}, {128.0, 1.2, 2}, {160.12, 0.8, 3}};
        for (double[] c : cases) {
            AudioGridEstimator.Result result = AudioGridEstimator.analyze(
                    synthTrack(c[0], c[1], 45, (long) c[2], 0, 1.0), RATE);
            assertEquals(c[0], result.bpm(), 0.05, "bpm for " + c[0]);
            assertTrue(phaseErrorMs(result, c[1], c[0]) < 10.0, "phase for " + c[0] + ": " + result.firstBeatSec());
            assertTrue(result.stableTempo());
        }
    }

    @Test
    void quietTrack() {
        AudioGridEstimator.Result result = AudioGridEstimator.analyze(synthTrack(140.0, 0.4, 45, 4, 0, 0.03), RATE);
        assertEquals(140.0, result.bpm(), 0.05);
        assertTrue(phaseErrorMs(result, 0.4, 140.0) < 10.0);
    }

    @Test
    void firstBeatSkipsSilentIntro() {
        AudioGridEstimator.Result result = AudioGridEstimator.analyze(synthTrack(150.0, 5.0, 45, 5, 0, 1.0), RATE);
        assertEquals(150.0, result.bpm(), 0.05);
        assertEquals(5.0, result.firstBeatSec(), 0.02);
        assertTrue(result.lastSoundSec() > 40.0);
    }

    @Test
    void firstBeatIgnoresALoneStartupTransient() {
        // like the bundled MP3 decoder: digital silence, a step into noise, music at 5 s
        float[] samples = synthTrack(150.0, 5.0, 45, 9, 0, 1.0);
        int silence = (int) (0.05 * RATE);
        for (int index = 0; index < silence; index++) {
            samples[index] = 0f;
        }
        samples[silence] = 0.3f;
        AudioGridEstimator.Result result = AudioGridEstimator.analyze(samples, RATE);
        assertEquals(5.0, result.firstBeatSec(), 0.02);
    }

    @Test
    void octaveAmbiguousTempoKeepsTrueBpmAmongCandidates() {
        AudioGridEstimator.Result result = AudioGridEstimator.analyze(synthTrack(93.5, 0.05, 45, 3, 0, 1.0), RATE);
        boolean found = Math.abs(result.bpm() - 93.5) < 0.05;
        for (AudioGridEstimator.Candidate candidate : result.alternatives()) {
            found |= Math.abs(candidate.bpm() - 93.5) < 0.05;
        }
        assertTrue(found, "93.5 missing: " + result.bpm() + " " + result.alternatives());
        // and it is close enough to be offered as a one-press choice
        AudioGridEstimator.Candidate octave = result.octaveAlternative();
        assertNotNull(octave, "half/double tempo not offered: " + result.alternatives());
        double ratio = octave.bpm() / result.bpm();
        assertTrue(Math.abs(ratio - 2) < 0.01 || Math.abs(ratio - 0.5) < 0.01, "ratio " + ratio);
    }

    @Test
    void tempoChangeIsReportedUnstable() {
        AudioGridEstimator.Result result = AudioGridEstimator.analyze(synthTrack(120.0, 0.3, 90, 6, 126.0, 1.0), RATE);
        assertFalse(result.stableTempo());
    }

    @Test
    void rejectsSilentAndShortAudio() {
        assertThrows(IllegalArgumentException.class, () -> AudioGridEstimator.analyze(new float[RATE * 5], RATE));
        assertThrows(IllegalArgumentException.class,
                () -> AudioGridEstimator.analyze(synthTrack(120.0, 0.0, 0.5, 7, 0, 1.0), RATE));
    }

    @Test
    void onsetStrengthPeaksOnHits() {
        AudioGridEstimator.Result result = AudioGridEstimator.analyze(synthTrack(120.0, 1.0, 45, 8, 0, 1.0), RATE);
        double onBeat = result.onsetStrengthAt(10.0, 0.02);
        double between = result.onsetStrengthAt(10.125, 0.02);
        assertTrue(onBeat > between * 2, onBeat + " vs " + between);
    }

    @Test
    void fftMatchesDirectTransform() {
        int size = 16;
        double[] re = new double[size];
        double[] im = new double[size];
        Random random = new Random(1);
        for (int index = 0; index < size; index++) {
            re[index] = random.nextGaussian();
            im[index] = random.nextGaussian();
        }
        double[] expectedRe = new double[size];
        double[] expectedIm = new double[size];
        for (int k = 0; k < size; k++) {
            for (int n = 0; n < size; n++) {
                double angle = -2 * Math.PI * k * n / size;
                expectedRe[k] += re[n] * Math.cos(angle) - im[n] * Math.sin(angle);
                expectedIm[k] += re[n] * Math.sin(angle) + im[n] * Math.cos(angle);
            }
        }
        new AudioGridEstimator.Fft(size).transform(re, im);
        assertArrayEquals(expectedRe, re, 1e-9);
        assertArrayEquals(expectedIm, im, 1e-9);
    }
}
