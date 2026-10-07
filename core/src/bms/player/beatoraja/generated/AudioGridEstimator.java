package bms.player.beatoraja.generated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Estimates a constant BPM and the first beat of an audio track.
 *
 * <p>Java port of {@code tools/estimate_audio_grid.py} (Issue #431): a log
 * spectral-flux onset envelope is matched against beat + 8th-note combs. On
 * 26 constant-BPM BMS renders the Python version found the exact BPM for 22,
 * the half/double tempo for 2, and usually placed the grid within 10 ms.
 * Half/double tempo and on/off-beat phase stay ambiguous, so callers should
 * let the player adjust the result.</p>
 *
 * <p>Feed it samples decoded by the player's own decoders: timing is measured
 * on exactly what will be played.</p>
 */
public final class AudioGridEstimator {

    public static final int SAMPLE_RATE = 22050;
    static final int HOP = 128;
    static final int WINDOW = 1024;
    public static final double FRAME_RATE = SAMPLE_RATE / (double) HOP;
    /** Measured on synthetic drum/click/pluck trains: an onset peaks ~35 ms after its frame start. */
    public static final double ONSET_LAG_SEC = 0.035;
    static final double COARSE_SPAN_SEC = 40.0;
    static final double DOUBLE_TIME_RATIO = 0.8;
    /** An octave alternative this close to the chosen tempo is offered to the player. */
    public static final double OCTAVE_CLOSE = 0.75;
    /** Log-spectral flux inflates room noise, so the first-sound threshold sits well above it. */
    static final double FIRST_SOUND_RATIO = 0.3;
    static final double SUSTAIN_SEC = 2.0;
    static final double STABLE_DRIFT_MS = 30.0;
    /** Tempo following: per-bar alignment step, the drift that switches it on, and its limits. */
    static final double TEMPO_STEP_SEC = 0.03;
    static final double TEMPO_DRIFT_SEC = 0.03;
    /** Beyond this the fixed tempo is wrong (a 4:3 miss drifted ~300 ms), not drifting. */
    static final double TEMPO_MAX_DRIFT_SEC = 0.15;
    static final double TEMPO_CLEAR_RATIO = 1.15;
    static final double TEMPO_MAX_CHANGE = 0.03;
    static final int TEMPO_MIN_BARS = 8;
    /** (subdivision, weight): beats, plus 8th notes at half weight. */
    private static final int[] GRID_LEVELS = {1, 2};
    private static final double[] GRID_WEIGHTS = {1.0, 0.5};

    /** Number of onset bands (low / mid / high) and their edges. */
    public static final int BANDS = 3;
    public static final int BAND_HIGH = 2;
    private static final double[] BAND_EDGES_HZ = {200.0, 2000.0};

    public static final double DEFAULT_MIN_BPM = 70.0;
    public static final double DEFAULT_MAX_BPM = 250.0;

    private AudioGridEstimator() {
    }

    public record Candidate(double bpm, double confidence) {
    }

    /**
     * @param bpm          estimated constant tempo
     * @param firstBeatSec audio time of the first beat at or just before the music starts
     * @param phaseSec     beat phase in [0, 60 / bpm)
     * @param envelope     onset strength per frame, for callers that weight notes by it
     * @param bandEnvelopes low / mid / high onset strength per frame, each scaled to mean 1
     */
    public record Result(
            double bpm,
            double firstBeatSec,
            double phaseSec,
            double durationSec,
            double lastSoundSec,
            double confidence,
            double maxDriftMs,
            boolean stableTempo,
            List<Candidate> alternatives,
            double[] envelope,
            double[][] bandEnvelopes) {

        /** Peak onset strength within {@code radiusSec} of an audio time. */
        public double onsetStrengthAt(double timeSec, double radiusSec) {
            return peakNear(envelope, timeSec, radiusSec);
        }

        /**
         * The half or double tempo when it scored at least {@link #OCTAVE_CLOSE}
         * of the chosen tempo: such songs are often heard at the other tempo
         * (operator: アオとキラメキ, 173 chosen, 86.5 heard), and no threshold
         * settles it for every song, so callers offer it as a one-step choice.
         */
        public Candidate octaveAlternative() {
            for (Candidate candidate : alternatives) {
                double ratio = candidate.bpm() / bpm;
                boolean octave = Math.abs(ratio - 2.0) < 0.01 || Math.abs(ratio - 0.5) < 0.0025;
                if (octave && candidate.confidence() >= OCTAVE_CLOSE * confidence) {
                    return candidate;
                }
            }
            return null;
        }

        /**
         * Follows a tempo that drifts away from the fixed grid (a band without a
         * click; operator: 誘惑 slows ~60 ms behind over its last 80 s, so the
         * fixed grid lost the hits and the ending thinned out). Each bar's grid
         * of beats and 8ths is aligned to the onsets within
         * {@link #TEMPO_STEP_SEC} of the previous bar's alignment, the
         * alignments are smoothed, and the start times of {@code bars + 1} bars
         * are returned, relative to the song's median alignment (so the first
         * one is near {@code firstBarSec}). Null when the
         * alignment stays within {@link #TEMPO_DRIFT_SEC} (a steady song keeps
         * its fixed grid and constant BPM; 26 BMS renders and 9 of 11 operator
         * songs stayed within 25 ms) or exceeds {@link #TEMPO_MAX_DRIFT_SEC}.
         */
        public double[] barStarts(double firstBarSec, double barSec, int bars) {
            if (bars < TEMPO_MIN_BARS) {
                return null;
            }
            int reach = (int) Math.round(TEMPO_STEP_SEC * FRAME_RATE);
            double[] raw = new double[bars + 1];
            boolean[] heard = new boolean[bars + 1];
            double previous = 0.0;
            for (int bar = 0; bar <= bars; bar++) {
                double start = firstBarSec + bar * barSec;
                double best = previous;
                double bestScore = -1.0;
                double sum = 0.0;
                for (int k = -reach; k <= reach; k++) {
                    double shift = previous + k / FRAME_RATE;
                    double score = 0.0;
                    for (int j = -16; j < 16; j++) {
                        double time = start + j * barSec / 16 + shift;
                        score += (j % 2 == 0 ? 1.0 : 0.5) * peakNear(envelope, time, 0.01);
                    }
                    sum += score;
                    if (score > bestScore) {
                        bestScore = score;
                        best = shift;
                    }
                }
                // a clear alignment only: a quiet or rhythmless stretch keeps the previous one
                heard[bar] = bestScore > TEMPO_CLEAR_RATIO * sum / (2 * reach + 1);
                raw[bar] = heard[bar] ? best : previous;
                previous = raw[bar];
            }
            double[] smooth = new double[bars + 1];
            for (int bar = 0; bar <= bars; bar++) {
                double[] window = Arrays.copyOfRange(raw, Math.max(0, bar - 2), Math.min(bars + 1, bar + 3));
                smooth[bar] = percentile(window, 50);
            }
            List<Double> heardOffsets = new ArrayList<>();
            for (int bar = 0; bar <= bars; bar++) {
                if (heard[bar]) {
                    heardOffsets.add(smooth[bar]);
                }
            }
            if (heardOffsets.size() < TEMPO_MIN_BARS) {
                return null;
            }
            double[] values = heardOffsets.stream().mapToDouble(Double::doubleValue).toArray();
            double drift = percentile(values, 95) - percentile(values, 5);
            // steady: keep the fixed grid; far beyond a band's drift: the tempo itself is wrong
            if (drift < TEMPO_DRIFT_SEC || drift > TEMPO_MAX_DRIFT_SEC) {
                return null;
            }
            // the fixed grid already fits the song as a whole: keep its median alignment
            double reference = percentile(values, 50);
            double[] starts = new double[bars + 1];
            for (int bar = 0; bar <= bars; bar++) {
                double left = smooth[Math.max(0, bar - 1)];
                double right = smooth[Math.min(bars, bar + 1)];
                starts[bar] = firstBarSec + bar * barSec + (left + smooth[bar] + right) / 3 - reference;
            }
            for (int bar = 1; bar <= bars; bar++) {
                // a bar stays within TEMPO_MAX_CHANGE of the fixed bar length
                double length = Math.max(barSec * (1 - TEMPO_MAX_CHANGE),
                        Math.min(barSec * (1 + TEMPO_MAX_CHANGE), starts[bar] - starts[bar - 1]));
                starts[bar] = starts[bar - 1] + length;
            }
            return starts;
        }

        /** Audio time of the strongest onset between two audio times. */
        public double onsetPeakTime(double fromSec, double toSec) {
            int from = Math.max(0, (int) Math.floor((fromSec - ONSET_LAG_SEC) * FRAME_RATE));
            int to = Math.min(envelope.length - 1, (int) Math.ceil((toSec - ONSET_LAG_SEC) * FRAME_RATE));
            int best = from;
            for (int index = from; index <= to; index++) {
                if (envelope[index] > envelope[best]) {
                    best = index;
                }
            }
            return best / FRAME_RATE + ONSET_LAG_SEC;
        }

        /** Peak low / mid / high onset strengths within {@code radiusSec} of an audio time. */
        public double[] bandStrengthsAt(double timeSec, double radiusSec) {
            double[] strengths = new double[bandEnvelopes.length];
            for (int band = 0; band < strengths.length; band++) {
                strengths[band] = peakNear(bandEnvelopes[band], timeSec, radiusSec);
            }
            return strengths;
        }

        private static double peakNear(double[] values, double timeSec, double radiusSec) {
            double centre = (timeSec - ONSET_LAG_SEC) * FRAME_RATE;
            int from = Math.max(0, (int) Math.floor(centre - radiusSec * FRAME_RATE));
            int to = Math.min(values.length - 1, (int) Math.ceil(centre + radiusSec * FRAME_RATE));
            double peak = 0.0;
            for (int index = from; index <= to; index++) {
                peak = Math.max(peak, values[index]);
            }
            return peak;
        }
    }

    public static Result analyze(float[] samples, int sampleRate) {
        return analyze(samples, sampleRate, DEFAULT_MIN_BPM, DEFAULT_MAX_BPM);
    }

    public static Result analyze(float[] samples, int sampleRate, double minBpm, double maxBpm) {
        float[] audio = resample(samples, sampleRate, SAMPLE_RATE);
        if (audio.length < SAMPLE_RATE) {
            throw new IllegalArgumentException("audio is shorter than one second");
        }
        float peakAmplitude = 0f;
        for (float sample : audio) {
            peakAmplitude = Math.max(peakAmplitude, Math.abs(sample));
        }
        if (peakAmplitude < 1e-4f) {
            throw new IllegalArgumentException("audio is silent");
        }

        double[][] envelopes = onsetEnvelopes(audio);
        double[] env = envelopes[0];
        List<Scored> candidates = estimate(env, minBpm, maxBpm);
        Scored best = candidates.get(0);
        double bpm = best.bpm;
        double periodSec = 60.0 / bpm;
        double phaseSec = best.phaseFrames / FRAME_RATE + ONSET_LAG_SEC;

        double threshold = percentile(env, 99) * FIRST_SOUND_RATIO;
        int lastLoud = -1;
        for (int index = 0; index < env.length; index++) {
            if (env[index] > threshold) {
                lastLoud = index;
            }
        }
        double firstSound = firstSustainedSound(env, threshold);
        double lastSound = lastLoud >= 0 ? lastLoud / FRAME_RATE + ONSET_LAG_SEC : audio.length / (double) SAMPLE_RATE;
        // earliest grid beat that is not before the music starts (with half a beat of slack)
        int k = (int) Math.ceil((firstSound - periodSec * 0.5 - phaseSec) / periodSec);
        int notNegative = (int) Math.ceil(-phaseSec / periodSec);
        double firstBeat = phaseSec + Math.max(k, notNegative) * periodSec;
        double drift = stability(env, bpm, best.phaseFrames);

        List<Candidate> alternatives = new ArrayList<>();
        for (int index = 1; index < Math.min(4, candidates.size()); index++) {
            alternatives.add(new Candidate(candidates.get(index).bpm, candidates.get(index).score));
        }
        return new Result(
                bpm,
                firstBeat,
                floorMod(phaseSec, periodSec),
                audio.length / (double) SAMPLE_RATE,
                lastSound,
                best.score,
                drift,
                drift <= STABLE_DRIFT_MS,
                List.copyOf(alternatives),
                env,
                new double[][] {envelopes[1], envelopes[2], envelopes[3]});
    }

    /**
     * Start of the music: the first onset burst followed by another within
     * {@link #SUSTAIN_SEC}. A lone burst is ignored; decoders that keep the
     * encoder delay (the bundled MP3 decoder) produce one where digital silence
     * steps into the track's noise floor.
     */
    static double firstSustainedSound(double[] env, double threshold) {
        List<Integer> bursts = new ArrayList<>();
        boolean above = false;
        for (int index = 0; index < env.length; index++) {
            boolean loud = env[index] > threshold;
            if (loud && !above) {
                bursts.add(index);
            }
            above = loud;
        }
        int window = (int) (SUSTAIN_SEC * FRAME_RATE);
        for (int index = 0; index + 1 < bursts.size(); index++) {
            if (bursts.get(index + 1) - bursts.get(index) <= window) {
                return bursts.get(index) / FRAME_RATE;
            }
        }
        return bursts.isEmpty() ? 0.0 : bursts.get(0) / FRAME_RATE;
    }

    // ---------------------------------------------------------------- envelope

    static float[] resample(float[] samples, int sourceRate, int targetRate) {
        if (sourceRate == targetRate) {
            return samples;
        }
        double ratio = sourceRate / (double) targetRate;
        int length = (int) Math.floor(samples.length / ratio);
        float[] out = new float[length];
        if (ratio >= 1.0) {
            // box filter over each output period: enough anti-aliasing for an 8 kHz analysis band
            for (int index = 0; index < length; index++) {
                int from = (int) Math.floor(index * ratio);
                int to = Math.min(samples.length, Math.max(from + 1, (int) Math.floor((index + 1) * ratio)));
                double sum = 0.0;
                for (int source = from; source < to; source++) {
                    sum += samples[source];
                }
                out[index] = (float) (sum / (to - from));
            }
        } else {
            for (int index = 0; index < length; index++) {
                double position = index * ratio;
                int base = (int) position;
                double fraction = position - base;
                float next = base + 1 < samples.length ? samples[base + 1] : samples[base];
                out[index] = (float) (samples[base] * (1.0 - fraction) + next * fraction);
            }
        }
        return out;
    }

    static double[] onsetEnvelope(float[] audio) {
        return onsetEnvelopes(audio)[0];
    }

    /**
     * Onset envelopes from one STFT pass: [0] full band (30 Hz-8 kHz, used for
     * the tempo/beat search), then low (30-200 Hz), mid (200 Hz-2 kHz) and high
     * (2-8 kHz). Each band is scaled by its own mean, so bands are comparable
     * ("which part of the kit moved here").
     */
    static double[][] onsetEnvelopes(float[] audio) {
        int frames = 1 + (audio.length - WINDOW) / HOP;
        int lowBin = (int) Math.ceil(30.0 * WINDOW / SAMPLE_RATE);
        int highBin = (int) Math.floor(8000.0 * WINDOW / SAMPLE_RATE);
        int bins = highBin - lowBin + 1;
        int[] bandOfBin = new int[bins];
        for (int bin = 0; bin < bins; bin++) {
            double frequency = (lowBin + bin) * (double) SAMPLE_RATE / WINDOW;
            bandOfBin[bin] = frequency < BAND_EDGES_HZ[0] ? 0 : frequency < BAND_EDGES_HZ[1] ? 1 : 2;
        }
        double[] window = new double[WINDOW];
        for (int index = 0; index < WINDOW; index++) {
            window[index] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * index / (WINDOW - 1));
        }

        Fft fft = new Fft(WINDOW);
        double[] re = new double[WINDOW];
        double[] im = new double[WINDOW];
        double[] previous = new double[bins];
        double[] current = new double[bins];
        double[] next = new double[bins];
        double[][] flux = new double[1 + BANDS][frames];
        // two real frames per complex FFT: frame a in the real part, frame b in the imaginary part
        for (int frame = 0; frame < frames; frame += 2) {
            boolean pair = frame + 1 < frames;
            int a = frame * HOP;
            int b = (frame + 1) * HOP;
            for (int index = 0; index < WINDOW; index++) {
                re[index] = audio[a + index] * window[index];
                im[index] = pair ? audio[b + index] * window[index] : 0.0;
            }
            fft.transform(re, im);
            for (int bin = 0; bin < bins; bin++) {
                int k = lowBin + bin;
                int mirror = (WINDOW - k) % WINDOW;
                double aRe = 0.5 * (re[k] + re[mirror]);
                double aIm = 0.5 * (im[k] - im[mirror]);
                double bRe = 0.5 * (im[k] + im[mirror]);
                double bIm = -0.5 * (re[k] - re[mirror]);
                current[bin] = Math.log1p(100.0 * Math.hypot(aRe, aIm));
                next[bin] = Math.log1p(100.0 * Math.hypot(bRe, bIm));
            }
            if (frame > 0) {
                addPositiveDifference(current, previous, bandOfBin, flux, frame);
            }
            if (pair) {
                addPositiveDifference(next, current, bandOfBin, flux, frame + 1);
                System.arraycopy(next, 0, previous, 0, bins);
            } else {
                System.arraycopy(current, 0, previous, 0, bins);
            }
        }
        double[][] envelopes = new double[1 + BANDS][];
        envelopes[0] = detrendAndSmooth(flux[0]);
        for (int band = 1; band <= BANDS; band++) {
            double[] env = detrendAndSmooth(flux[band]);
            double scale = mean(env) + 1e-9;
            for (int index = 0; index < env.length; index++) {
                env[index] /= scale;
            }
            envelopes[band] = env;
        }
        return envelopes;
    }

    private static void addPositiveDifference(double[] current, double[] previous, int[] bandOfBin,
            double[][] flux, int frame) {
        for (int bin = 0; bin < current.length; bin++) {
            double delta = current[bin] - previous[bin];
            if (delta > 0) {
                flux[0][frame] += delta;
                flux[1 + bandOfBin[bin]][frame] += delta;
            }
        }
    }

    private static double[] detrendAndSmooth(double[] flux) {
        int frames = flux.length;
        // remove slow trends so quiet and loud passages weigh comparably (numpy "same" convolution)
        int kernel = (int) (FRAME_RATE * 0.5);
        int before = kernel - 1 - (kernel - 1) / 2;
        int after = (kernel - 1) / 2;
        double[] prefix = new double[frames + 1];
        for (int index = 0; index < frames; index++) {
            prefix[index + 1] = prefix[index] + flux[index];
        }
        double[] rectified = new double[frames];
        for (int index = 0; index < frames; index++) {
            int from = Math.max(0, index - before);
            int to = Math.min(frames, index + after + 1);
            double local = (prefix[to] - prefix[from]) / kernel;
            rectified[index] = Math.max(flux[index] - local, 0.0);
        }
        // a frame-wide smoothing keeps sub-frame phase information usable
        double[] env = new double[frames];
        for (int index = 0; index < frames; index++) {
            double left = index > 0 ? rectified[index - 1] : 0.0;
            double right = index + 1 < frames ? rectified[index + 1] : 0.0;
            env[index] = 0.25 * left + 0.5 * rectified[index] + 0.25 * right;
        }
        return env;
    }

    // ---------------------------------------------------------------- comb search

    private static double interp(double[] env, double position) {
        double clipped = Math.min(Math.max(position, 0.0), env.length - 1.001);
        int base = (int) clipped;
        double fraction = clipped - base;
        return env[base] * (1.0 - fraction) + env[base + 1] * fraction;
    }

    /** Weighted mean onset strength on the beat and 8th-note grids at one phase (frames). */
    private static double gridScore(double[] env, double period, int beats, double phase) {
        double score = 0.0;
        for (int level = 0; level < GRID_LEVELS.length; level++) {
            int subdivision = GRID_LEVELS[level];
            int points = beats * subdivision;
            if (points <= 0) {
                continue;
            }
            double step = period / subdivision;
            double sum = 0.0;
            for (int point = 0; point < points; point++) {
                sum += interp(env, point * step + phase);
            }
            score += GRID_WEIGHTS[level] * sum / points;
        }
        return score;
    }

    private static int beatCount(double[] env, double period) {
        return (int) Math.floor((env.length - 2 - period) / period);
    }

    /** @return {phase, score} */
    private static double[] bestPhase(double[] env, double bpm, int phases) {
        double period = FRAME_RATE * 60.0 / bpm;
        int beats = beatCount(env, period);
        double bestPhase = 0.0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < phases; index++) {
            double phase = period * index / phases;
            double score = gridScore(env, period, beats, phase);
            if (score > bestScore) {
                bestScore = score;
                bestPhase = phase;
            }
        }
        return new double[] {bestPhase, bestScore};
    }

    /** @return {phase, score} with a parabolic sub-step peak */
    private static double[] refinePhase(double[] env, double bpm, double coarsePhase) {
        double period = FRAME_RATE * 60.0 / bpm;
        int beats = beatCount(env, period);
        int count = 25;
        double[] phases = new double[count];
        double[] scores = new double[count];
        int top = 0;
        for (int index = 0; index < count; index++) {
            double offset = -0.6 + 1.2 * index / (count - 1);
            phases[index] = Math.max(0.0, coarsePhase + offset * (period / 48 * 2));
            scores[index] = gridScore(env, period, beats, phases[index]);
            if (scores[index] > scores[top]) {
                top = index;
            }
        }
        if (top > 0 && top < count - 1) {
            double a = scores[top - 1];
            double b = scores[top];
            double c = scores[top + 1];
            double denominator = a - 2 * b + c;
            double shift = denominator != 0 ? 0.5 * (a - c) / denominator : 0.0;
            return new double[] {phases[top] + shift * (phases[1] - phases[0]), b};
        }
        return new double[] {phases[top], scores[top]};
    }

    private static double[] loudestSegment(double[] env, int span) {
        if (env.length <= span) {
            return env;
        }
        double sum = 0.0;
        for (int index = 0; index < span; index++) {
            sum += env[index];
        }
        double best = sum;
        int bestStart = 0;
        for (int start = 1; start + span <= env.length; start++) {
            sum += env[start + span - 1] - env[start - 1];
            if (sum > best) {
                best = sum;
                bestStart = start;
            }
        }
        return Arrays.copyOfRange(env, bestStart, bestStart + span);
    }

    private static final class Scored {
        final double bpm;
        final double phaseFrames;
        final double score;
        final double ranked;

        Scored(double bpm, double phaseFrames, double score, double ranked) {
            this.bpm = bpm;
            this.phaseFrames = phaseFrames;
            this.score = score;
            this.ranked = ranked;
        }
    }

    private static List<Scored> estimate(double[] env, double minBpm, double maxBpm) {
        double baseline = mean(env) + 1e-9;
        // Coarse pass on the busiest stretch only: over a whole song a 0.1 BPM step
        // would already smear the comb peak, so the step has to match the span.
        double[] segment = loudestSegment(env, (int) (COARSE_SPAN_SEC * FRAME_RATE));
        int count = (int) Math.ceil((maxBpm + 0.05 - minBpm) / 0.1);
        double[] bpms = new double[count];
        double[] ranked = new double[count];
        for (int index = 0; index < count; index++) {
            bpms[index] = minBpm + index * 0.1;
            double coarse = bestPhase(segment, bpms[index], 32)[1];
            // weak log-tempo prior toward 100-190 that also breaks half/double ties
            double octaves = Math.log(bpms[index] / 140.0) / Math.log(2) / 0.9;
            double prior = Math.exp(-0.5 * octaves * octaves);
            ranked[index] = coarse * (0.85 + 0.15 * prior);
        }
        List<Integer> peaks = new ArrayList<>();
        for (int index = 1; index < count - 1; index++) {
            if (ranked[index] >= ranked[index - 1] && ranked[index] >= ranked[index + 1]) {
                peaks.add(index);
            }
        }
        peaks.sort((left, right) -> Double.compare(ranked[right], ranked[left]));

        List<Scored> candidates = new ArrayList<>();
        for (int peak : peaks.subList(0, Math.min(6, peaks.size()))) {
            double low = bpms[peak] - 0.3;
            double bestBpm = low;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int step = 0; step < 60; step++) {
                double bpm = low + step * 0.01;
                double score = bestPhase(env, bpm, 64)[1];
                if (score > bestScore) {
                    bestScore = score;
                    bestBpm = bpm;
                }
            }
            double[] refined = refinePhase(env, bestBpm, bestPhase(env, bestBpm, 96)[0]);
            candidates.add(new Scored(bestBpm, refined[0], refined[1] / baseline, ranked[peak]));
        }
        if (candidates.isEmpty()) {
            double bpm = 120.0;
            double[] phase = bestPhase(env, bpm, 96);
            candidates.add(new Scored(bpm, phase[0], phase[1] / baseline, phase[1]));
        }
        candidates.sort((left, right) -> Double.compare(right.ranked, left.ranked));
        return preferDoubleTime(candidates, maxBpm);
    }

    /**
     * A comb on every other beat often scores a little higher because it only
     * lands on the strongest hits. If the doubled tempo is also strong, take it.
     */
    private static List<Scored> preferDoubleTime(List<Scored> candidates, double maxBpm) {
        Scored best = candidates.get(0);
        for (int index = 1; index < candidates.size(); index++) {
            Scored other = candidates.get(index);
            if (Math.abs(other.bpm / best.bpm - 2.0) < 0.005
                    && other.bpm <= maxBpm
                    && other.score >= DOUBLE_TIME_RATIO * best.score) {
                candidates.remove(index);
                candidates.add(0, other);
                break;
            }
        }
        List<Scored> unique = new ArrayList<>();
        for (Scored candidate : candidates) {
            boolean distinct = true;
            for (Scored kept : unique) {
                if (Math.abs(candidate.bpm - kept.bpm) <= 0.05) {
                    distinct = false;
                    break;
                }
            }
            if (distinct) {
                unique.add(candidate);
            }
        }
        return unique;
    }

    /** Largest deviation (ms) of the local beat phase from the global grid over 15 s windows. */
    private static double stability(double[] env, double bpm, double phase) {
        double period = FRAME_RATE * 60.0 / bpm;
        int window = (int) (15.0 * FRAME_RATE);
        double overallMean = mean(env);
        double worst = 0.0;
        for (int start = 0; start < env.length - window; start += window / 2) {
            double[] segment = Arrays.copyOfRange(env, start, start + window);
            if (mean(segment) < overallMean * 0.3) {
                continue;
            }
            double localPhase = floorMod(phase - start, period);
            int beats = (int) Math.floor(window / period) - 1;
            double bestOffset = 0.0;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < 41; index++) {
                double offset = (-0.5 + index / 40.0) * period * 0.5;
                double sum = 0.0;
                for (int beat = 0; beat < beats; beat++) {
                    sum += interp(segment, beat * period + localPhase + offset);
                }
                double score = beats > 0 ? sum / beats : 0.0;
                if (score > bestScore) {
                    bestScore = score;
                    bestOffset = offset;
                }
            }
            worst = Math.max(worst, Math.abs(bestOffset / FRAME_RATE * 1000.0));
        }
        return worst;
    }

    // ---------------------------------------------------------------- helpers

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return values.length > 0 ? sum / values.length : 0.0;
    }

    /** numpy's default (linear) percentile. */
    static double percentile(double[] values, double percent) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double rank = percent / 100.0 * (sorted.length - 1);
        int base = (int) Math.floor(rank);
        int upper = Math.min(sorted.length - 1, base + 1);
        return sorted[base] + (sorted[upper] - sorted[base]) * (rank - base);
    }

    static double floorMod(double value, double period) {
        double result = value % period;
        return result < 0 ? result + period : result;
    }

    /** Iterative radix-2 complex FFT of a fixed power-of-two size. */
    static final class Fft {
        private final int size;
        private final int[] reversed;
        private final double[] cos;
        private final double[] sin;

        Fft(int size) {
            this.size = size;
            int bits = Integer.numberOfTrailingZeros(size);
            reversed = new int[size];
            for (int index = 0; index < size; index++) {
                reversed[index] = Integer.reverse(index) >>> (32 - bits);
            }
            cos = new double[size / 2];
            sin = new double[size / 2];
            for (int index = 0; index < size / 2; index++) {
                cos[index] = Math.cos(-2.0 * Math.PI * index / size);
                sin[index] = Math.sin(-2.0 * Math.PI * index / size);
            }
        }

        void transform(double[] re, double[] im) {
            for (int index = 0; index < size; index++) {
                int target = reversed[index];
                if (target > index) {
                    double t = re[index];
                    re[index] = re[target];
                    re[target] = t;
                    t = im[index];
                    im[index] = im[target];
                    im[target] = t;
                }
            }
            for (int length = 2; length <= size; length <<= 1) {
                int half = length >> 1;
                int stride = size / length;
                for (int start = 0; start < size; start += length) {
                    for (int offset = 0; offset < half; offset++) {
                        double wr = cos[offset * stride];
                        double wi = sin[offset * stride];
                        int even = start + offset;
                        int odd = even + half;
                        double xr = re[odd] * wr - im[odd] * wi;
                        double xi = re[odd] * wi + im[odd] * wr;
                        re[odd] = re[even] - xr;
                        im[odd] = im[even] - xi;
                        re[even] += xr;
                        im[even] += xi;
                    }
                }
            }
        }
    }
}
