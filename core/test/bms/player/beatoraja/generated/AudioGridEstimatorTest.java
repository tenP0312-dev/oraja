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

    /** Beat times at {@code bpm} until {@code slowFromSec}, then slowing linearly to {@code endBpm} at 90 s. */
    static double[] driftingBeats(double bpm, double slowFromSec, double endBpm, double duration) {
        java.util.List<Double> beats = new java.util.ArrayList<>();
        double now = 0.5;
        while (now < duration) {
            beats.add(now);
            double share = Math.max(0, Math.min(1, (now - slowFromSec) / (90.0 - slowFromSec)));
            now += 60.0 / (bpm + share * (endBpm - bpm));
        }
        return beats.stream().mapToDouble(Double::doubleValue).toArray();
    }

    static float[] trackOnBeats(double[] beats, double duration) {
        Random random = new Random(7);
        double[] out = new double[(int) (duration * RATE)];
        double[] click = new double[(int) (0.08 * RATE)];
        for (int index = 0; index < click.length; index++) {
            click[index] = random.nextGaussian() * Math.exp(-index / (double) RATE * 60) * 0.5;
        }
        for (int k = 0; k < beats.length; k++) {
            add(out, beats[k], click, k % 4 == 0 ? 1.0 : 0.6);
            if (k + 1 < beats.length) {
                add(out, (beats[k] + beats[k + 1]) / 2, click, 0.35);
            }
        }
        float[] samples = new float[out.length];
        for (int index = 0; index < out.length; index++) {
            samples[index] = (float) (0.5 * out[index] + random.nextGaussian() * 0.005);
        }
        return samples;
    }

    @Test
    void aDriftingTempoIsFollowedBarByBar() {
        // 150 BPM, easing to 149.6 BPM from 60 s: the fixed grid ends ~65 ms early
        double duration = 100.0;
        double[] beats = driftingBeats(150.0, 60.0, 149.6, duration);
        AudioGridEstimator.Result result = AudioGridEstimator.analyze(trackOnBeats(beats, duration), RATE);
        double barSec = 4 * 60.0 / result.bpm();
        double first = result.firstBeatSec();
        int firstBeat = 0;
        while (beats[firstBeat] < first - 0.03) {
            firstBeat++;
        }
        int bars = (beats.length - firstBeat) / 4 - 1;
        double[] starts = result.barStarts(first, barSec, bars);
        assertNotNull(starts, "drift not followed");
        double fixedError = 0;
        double trackedError = 0;
        for (int bar = bars - 5; bar < bars; bar++) {
            double truth = beats[firstBeat + 4 * bar];
            fixedError = Math.max(fixedError, Math.abs(first + bar * barSec - truth));
            trackedError = Math.max(trackedError, Math.abs(starts[bar] - truth));
        }
        assertTrue(fixedError > 0.04, "the fixed grid should be off at the end: " + fixedError);
        assertTrue(trackedError < 0.015, "tracked bars off at the end by " + trackedError);
    }

    @Test
    void aSteadyTempoKeepsTheFixedGrid() {
        AudioGridEstimator.Result result = AudioGridEstimator.analyze(synthTrack(150, 0.4, 60, 3, 0, 0.5), RATE);
        double barSec = 4 * 60.0 / result.bpm();
        assertNull(result.barStarts(result.firstBeatSec(), barSec, (int) (55 / barSec)));
    }

    @Test
    void talkBeforeTheMusicIsLeftOutOfTheRhythmicSpan() {
        // 20 s of irregular bursts and noise (a video's talk scene), then a steady 150 BPM track
        Random random = new Random(11);
        float[] music = synthTrack(150, 0.0, 60, 4, 0, 0.5);
        int intro = 20 * RATE;
        float[] samples = new float[intro + music.length];
        double[] click = new double[(int) (0.06 * RATE)];
        for (int index = 0; index < click.length; index++) {
            click[index] = random.nextGaussian() * Math.exp(-index / (double) RATE * 40) * 0.3;
        }
        double[] talk = new double[intro];
        // syllable-like bursts at irregular 70-320 ms gaps, as speech has
        for (double time = 0.3; time < 19.5; time += 0.07 + random.nextDouble() * 0.25) {
            add(talk, time, click, 0.3 + random.nextDouble());
        }
        for (int index = 0; index < intro; index++) {
            samples[index] = (float) (talk[index] + random.nextGaussian() * 0.01);
        }
        System.arraycopy(music, 0, samples, intro, music.length);
        AudioGridEstimator.Result result = AudioGridEstimator.analyze(samples, RATE);
        double barSec = 4 * 60.0 / result.bpm();
        double first = result.firstBeatSec();
        double[] span = result.rhythmicSpan(first, barSec, result.lastSoundSec(), null);
        assertNotNull(span);
        assertTrue(span[0] > 19.0 && span[0] < 22.5, "the music starts at 20 s, span starts at " + span[0]);
        assertTrue(span[1] > 75.0, "the music runs to the end, span ends at " + span[1]);
    }

    @Test
    void aDyingTailIsFadingButAHitIsNot() {
        float[] samples = new float[3 * RATE];
        for (int index = 0; index < samples.length; index++) {
            double t = index / (double) RATE;
            // a loud chord from 0.5 s decaying, then a new hit at 2.0 s
            double chord = t >= 0.5 ? Math.exp(-(t - 0.5) * 4) : 0.0;
            double hit = t >= 2.0 ? Math.exp(-(t - 2.0) * 4) : 0.0;
            samples[index] = (float) (0.5 * Math.sin(2 * Math.PI * 220 * t) * (chord + hit));
        }
        AudioGridEstimator.Result result = AudioGridEstimator.analyze(samples, RATE);
        assertTrue(result.fadingAt(1.6), "the quiet tail before the new hit");
        assertFalse(result.fadingAt(2.0), "the new hit");
        assertFalse(result.fadingAt(0.6), "the loud chord just after it starts");
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
