package bms.player.beatoraja.generated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Builds a 7-key BMS chart that follows one audio file (Issues #433, #445).
 *
 * <ul>
 *   <li>Placement: on a 16th grid, positions are ranked by onset strength
 *       (normalized against the surrounding two bars) plus a small metrical
 *       bonus, and the strongest positions get
 *       notes. How many follows the song (see {@link #DENSITY_SCALE}); against
 *       real BMS charts this matched note timings with F1 0.76, versus 0.69 for
 *       every 8th and 0.72 for metrical position alone.</li>
 *   <li>Repetition: a bar whose low/mid/high onset pattern matches an earlier
 *       bar reuses that bar's lanes and chord sizes wherever both bars have a
 *       note in the same column (each bar keeps its own positions), as charters do
 *       (detected repeats had real-chart rhythm similarity 0.78-0.82 vs 0.57
 *       for random bar pairs).</li>
 *   <li>Chord size follows onset strength within [min, max]; lanes are random,
 *       avoiding the previous position's lanes; scratch (optional) goes on clear
 *       hi-hats ({@link #isHiHat}), at least an 8th apart.</li>
 * </ul>
 *
 * <p>Layout: measure 0 is an empty lead-in, measure 1 starts the audio and is
 * shortened so that measure 2 begins exactly on a beat of the music.</p>
 */
public final class GeneratedChartBuilder {

    public static final int KEYS = 7;
    /**
     * Note amount levels 1-4 scale the song's own density: the number of 16th
     * positions with a clear onset (at least {@link #CLEAR_ONSET} of the song's
     * loud level). Level 3 follows the music; on real BMS this count matched
     * the charted timings with F1 0.76, versus 0.77 when the real chart's note
     * count is known and 0.67-0.72 for any fixed share of 16ths.
     */
    static final double[] DENSITY_SCALE = {0.5, 0.75, 1.0, 1.3};
    static final double CLEAR_ONSET = 0.15;
    public static final int MIN_DENSITY = 1;
    public static final int MAX_DENSITY = DENSITY_SCALE.length;
    public static final int DEFAULT_DENSITY = 3;
    /** BMS 1P key channels for keys 1-7, then the scratch channel. */
    private static final String[] KEY_CHANNELS = {"11", "12", "13", "14", "15", "18", "19"};
    private static final String SCRATCH_CHANNEL = "16";
    private static final String AUDIO_WAV = "01";
    private static final String BGA_BMP = "01";
    /** Notes reference an undefined WAV id so pressing keys stays silent. */
    private static final String SILENT_WAV = "02";
    private static final int BEATS_PER_MEASURE = 4;
    static final int SLOTS_PER_BAR = 16;
    /** Fixed 12th / 24th grid (6 per beat). */
    static final int TRIPLET_SLOTS_PER_BAR = 24;
    /**
     * Following the music: 12 per beat, of which only 0, 1/4, 1/3, 1/2, 2/3 and
     * 3/4 are candidates. 1/4 vs 1/3 and 2/3 vs 3/4 are too close to tell apart
     * and compete (the stronger onset wins), so straight 16ths and triplet 8ths
     * are chosen per position and a song may switch between them. On 26 real
     * BMS this kept F1 (0.755 vs 0.758 on 16ths only) while capturing triplets
     * (one song: 44 of 48), with 1.9% of notes on false triplets. 16th triplets
     * (1/6, 5/6) lowered F1 to 0.73 and are left out.
     */
    static final int UNION_SLOTS_PER_BAR = 48;
    private static final int UNION_PER_BEAT = 12;
    /**
     * Ranking mixes the local (two-bar) onset level with the song-wide level:
     * score = local^(1-w) x song-wide^w. On 26 real BMS
     * a 0.7 song-wide share kept F1 (0.759 vs 0.757) and cut notes on positions
     * without a clear onset by ~20%, so thin passages (shaker / kick only) get
     * fewer notes instead of being lifted to the surrounding density.
     */
    static final double SONG_WIDE_SHARE = 0.7;
    /** The first measure of notes must start at least this far into the audio. */
    private static final double MIN_LEAD_BEATS = 0.25;
    private static final double METRIC_WEIGHT = 0.3;
    /**
     * A triplet position wins only when its sound peaks at least this far from
     * the neighbouring 16th toward the triplet. Sung or loosely played 16ths
     * land ~30 ms late, nearer the triplet than the 16th at ~105 BPM (operator:
     * CANDY POP had slightly-off notes in 8th streams); quantized BMS triplets
     * all pass (40 of 40 on 26 BMS).
     */
    static final double TRIPLET_TIMING = 0.75;
    /**
     * A 1/3 triplet also needs a 2/3 triplet within this many beats: a late
     * 16th moves 1/4 toward 1/3 but 3/4 away from 2/3, so a lone 1/3 is
     * usually a late 16th. Keeps 37 of 40 BMS triplets.
     */
    static final int TRIPLET_CONTEXT_BEATS = 4;
    /**
     * Positions below this share of the song's loud level are silent: the
     * metrical bonus alone never puts a note there (operator: CANDY POP had
     * 8ths during a 1.3-second break, at 0-1% of the loud level).
     */
    static final double SILENT_ONSET = 0.05;
    /** Cosine similarity of bar onset patterns above which a bar repeats an earlier one. */
    static final double REPEAT_SIMILARITY = 0.85;
    static final double HI_HAT_DOMINANCE = 2.0;
    /** Bars below this share of the median bar energy count as silent. */
    private static final double SILENT_BAR_SHARE = 0.25;

    private GeneratedChartBuilder() {
    }

    /** Onset evidence around an audio time (seconds). */
    public interface Onsets {
        double strength(double timeSec);

        /** low / mid / high strengths, each on its own comparable scale */
        double[] bands(double timeSec);

        /** Time of the strongest onset between two audio times; NaN when unknown. */
        default double peakTime(double fromSec, double toSec) {
            return Double.NaN;
        }
    }

    /**
     * @param followMusic place notes where the music hits ({@code density}); off = every
     *                    {@code division}th note (4, 8, 12, 16 or 24), the original fixed grid
     * @param density     note amount 1 (light) to 4 (dense) when following the music; 3 = as the music
     * @param repeatBars  reuse the layout of an earlier bar with the same onset pattern
     * @param scratch     scratch where the hi-hat range dominates
     */
    public record Settings(boolean followMusic, int density, int division, boolean repeatBars,
            int minChord, int maxChord, boolean scratch) {
        public Settings {
            if (density < MIN_DENSITY || density > MAX_DENSITY) {
                throw new IllegalArgumentException("density must be " + MIN_DENSITY + "-" + MAX_DENSITY);
            }
            if (division != 4 && division != 8 && division != 12 && division != 16 && division != 24) {
                throw new IllegalArgumentException("division must be 4, 8, 12, 16 or 24");
            }
            if (minChord < 1 || maxChord > KEYS || minChord > maxChord) {
                throw new IllegalArgumentException("chord range must satisfy 1 <= min <= max <= " + KEYS);
            }
        }

        /** Everything on: follow the music at {@code density}, repeat bars. */
        public Settings(int density, int minChord, int maxChord, boolean scratch) {
            this(true, density, 8, true, minChord, maxChord, scratch);
        }

        /**
         * Grid positions per 4/4 bar: following the music uses the union grid
         * (48, straight and triplet positions); the fixed grid uses 16, or 24
         * for 12ths and 24ths.
         */
        public int perBar() {
            return followMusic ? UNION_SLOTS_PER_BAR
                    : division % 3 == 0 ? TRIPLET_SLOTS_PER_BAR : SLOTS_PER_BAR;
        }

        public String label() {
            String chords = minChord == maxChord ? Integer.toString(minChord) : minChord + "-" + maxChord;
            return (followMusic ? "amount " + density : division + "th") + " x" + chords
                    + (repeatBars ? " +RP" : "") + (scratch ? " +SC" : "");
        }
    }

    /**
     * @param notes     key and scratch notes
     * @param positions grid positions with notes
     * @param repeated  bars copied from an earlier bar
     */
    public record Chart(String text, int notes, int positions, int repeated) {
    }

    /** One grid position with its notes. */
    record Placement(int slot, int[] lanes, boolean scratch) {
    }

    /**
     * @param audioFileName file name of the audio, relative to the chart
     * @param bpm           tempo of the grid
     * @param firstBeatSec  audio time of a beat of the music
     * @param endSec        no notes after this audio time
     */
    public static Chart build(
            String title,
            String audioFileName,
            double bpm,
            double firstBeatSec,
            double endSec,
            Onsets onsets,
            Settings settings,
            long seed) {
        return build(title, audioFileName, null, bpm, firstBeatSec, endSec, onsets, settings, seed);
    }

    /**
     * @param bgaFileName a movie started together with the audio (#459), or null
     */
    public static Chart build(
            String title,
            String audioFileName,
            String bgaFileName,
            double bpm,
            double firstBeatSec,
            double endSec,
            Onsets onsets,
            Settings settings,
            long seed) {
        if (!(bpm > 0) || !Double.isFinite(bpm)) {
            throw new IllegalArgumentException("bpm must be positive");
        }
        double beat = 60.0 / bpm;
        double measureSec = beat * BEATS_PER_MEASURE;
        // first grid position on a beat of the music, at least MIN_LEAD_BEATS into the audio
        double firstSlotSec = firstBeatSec;
        while (firstSlotSec < MIN_LEAD_BEATS * beat) {
            firstSlotSec += beat;
        }
        int perBar = settings.perBar();
        double slotSec = measureSec / perBar;
        int slots = Math.max(0, (int) Math.floor((endSec - firstSlotSec) / slotSec) + 1);

        double[] strength = new double[slots];
        double[][] bands = new double[slots][];
        for (int slot = 0; slot < slots; slot++) {
            double time = firstSlotSec + slot * slotSec;
            strength[slot] = onsets.strength(time);
            bands[slot] = onsets.bands(time);
        }
        byte[] tripletTiming = perBar == UNION_SLOTS_PER_BAR
                ? tripletTiming(onsets, firstSlotSec, slotSec, slots)
                : null;
        List<Placement> placements = place(strength, bands, tripletTiming, settings, new Random(seed), perBar, beat);
        int repeated = settings.repeatBars() ? countRepeatedBars(bands, slots, perBar) : 0;

        int totalNotes = 0;
        for (Placement placement : placements) {
            totalNotes += placement.lanes.length + (placement.scratch ? 1 : 0);
        }
        int measures = (slots + perBar - 1) / perBar;
        StringBuilder text = new StringBuilder();
        text.append("*---------------------- HEADER FIELD\n");
        text.append("#PLAYER 1\n");
        text.append("#GENRE AUTO GENERATED\n");
        text.append("#TITLE ").append(title.replace('\n', ' ')).append('\n');
        text.append("#SUBTITLE [").append(settings.label()).append("]\n");
        text.append("#ARTIST Arena oraja generator\n");
        text.append(String.format(Locale.ROOT, "#BPM %.6f\n", bpm));
        text.append("#PLAYLEVEL 0\n");
        text.append("#RANK 2\n");
        // the usual IIDX-style gauge total for the note count
        text.append(String.format(Locale.ROOT, "#TOTAL %.1f\n",
                Math.max(100.0, 7.605 * totalNotes / (0.01 * totalNotes + 6.5))));
        text.append("#WAV").append(AUDIO_WAV).append(' ').append(audioFileName).append('\n');
        if (bgaFileName != null) {
            text.append("#BMP").append(BGA_BMP).append(' ').append(bgaFileName).append('\n');
        }
        text.append("\n*---------------------- MAIN DATA FIELD\n");
        text.append(String.format(Locale.ROOT, "#00102:%.9f\n", firstSlotSec / measureSec));
        text.append("#00101:").append(AUDIO_WAV).append('\n');
        if (bgaFileName != null) {
            // the movie's first frame and the extracted audio start at the same instant
            text.append("#00104:").append(BGA_BMP).append('\n');
        }

        String[][][] channelSlots = new String[measures][KEY_CHANNELS.length + 1][perBar];
        for (String[][] measure : channelSlots) {
            for (String[] channel : measure) {
                Arrays.fill(channel, "00");
            }
        }
        for (Placement placement : placements) {
            String[][] measure = channelSlots[placement.slot / perBar];
            int column = placement.slot % perBar;
            for (int lane : placement.lanes) {
                measure[lane][column] = SILENT_WAV;
            }
            if (placement.scratch) {
                measure[KEY_CHANNELS.length][column] = SILENT_WAV;
            }
        }
        for (int measure = 0; measure < measures; measure++) {
            String measureNumber = String.format(Locale.ROOT, "%03d", measure + 2);
            for (int channel = 0; channel <= KEY_CHANNELS.length; channel++) {
                String data = String.join("", channelSlots[measure][channel]);
                if (data.chars().allMatch(c -> c == '0')) {
                    continue;
                }
                String id = channel < KEY_CHANNELS.length ? KEY_CHANNELS[channel] : SCRATCH_CHANNEL;
                text.append('#').append(measureNumber).append(id).append(':').append(data).append('\n');
            }
        }
        return new Chart(text.toString(), totalNotes, placements.size(), repeated);
    }

    /** Chooses positions, chord sizes, scratch and lanes; repeated bars copy their source bar. */
    static List<Placement> place(double[] strength, double[][] bands, Settings settings, Random random,
            int perBar, double beatSec) {
        return place(strength, bands, null, settings, random, perBar, beatSec);
    }

    static List<Placement> place(double[] strength, double[][] bands, byte[] tripletTiming, Settings settings,
            Random random, int perBar, double beatSec) {
        int slots = strength.length;
        boolean[] chosen;
        if (settings.followMusic()) {
            boolean[] candidate = new boolean[slots];
            // the losers of the straight/triplet competitions count as silent from here on
            strength = unionCandidates(strength, perBar, candidate, tripletTiming);
            chosen = choose(strength, settings.density(), metricWeights(slots, perBar, beatSec), candidate,
                    candidatesPerBar(perBar));
        } else {
            chosen = fixedGrid(slots, settings.division(), perBar);
        }
        int[] source;
        if (settings.repeatBars()) {
            source = repeatSources(bands, slots, perBar);
            if (settings.followMusic()) {
                followSourceBars(chosen, strength, source, perBar);
            }
        } else {
            source = new int[(slots + perBar - 1) / perBar];
            Arrays.fill(source, -1);
        }
        // scratches stay at least an 8th apart (2 straight / 3 triplet positions)
        int scratchGap = perBar / 8;
        List<Integer> positions = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            if (chosen[slot]) {
                positions.add(slot);
            }
        }
        int[] sizes = chordSizes(positions, strength, settings);

        Placement[] bySlot = new Placement[slots];
        List<Placement> placements = new ArrayList<>();
        boolean[] previousLanes = new boolean[KEYS];
        double hiHatFloor = settings.scratch() ? hiHatFloor(positions, bands) : 0.0;
        int lastScratchSlot = Integer.MIN_VALUE / 2;
        for (int index = 0; index < positions.size(); index++) {
            int slot = positions.get(index);
            int bar = slot / perBar;
            Placement placement;
            // a repeated bar reuses the lanes its source bar used at the same column
            Placement copied = bar < source.length && source[bar] >= 0
                    ? bySlot[source[bar] * perBar + slot % perBar]
                    : null;
            if (copied != null) {
                placement = new Placement(slot, copied.lanes.clone(), copied.scratch);
            } else {
                boolean scratch = settings.scratch()
                        && isHiHat(bands[slot], hiHatFloor)
                        && slot - lastScratchSlot >= scratchGap;
                int keys = Math.max(scratch ? 0 : 1, sizes[index] - (scratch ? 1 : 0));
                placement = new Placement(slot, chooseLanes(keys, previousLanes, random), scratch);
            }
            bySlot[slot] = placement;
            placements.add(placement);
            Arrays.fill(previousLanes, false);
            for (int lane : placement.lanes) {
                previousLanes[lane] = true;
            }
            if (placement.scratch) {
                lastScratchSlot = slot;
            }
        }
        return placements;
    }

    /**
     * A repeated bar takes its source bar's positions where its own audio still
     * hits (at least half the source's strength), and adds a column the source
     * did not use only for a clearly stronger hit of its own (a new fill).
     * Identical audio is charted identically even though local normalization
     * sees different neighbours, and no note lands where this bar is silent.
     */
    static void followSourceBars(boolean[] chosen, double[] strength, int[] source, int perBar) {
        for (int bar = 0; bar < source.length; bar++) {
            if (source[bar] < 0) {
                continue;
            }
            for (int column = 0; column < perBar && bar * perBar + column < chosen.length; column++) {
                int own = bar * perBar + column;
                int original = source[bar] * perBar + column;
                chosen[own] = chosen[original]
                        ? strength[own] >= 0.5 * strength[original]
                        : chosen[own] && strength[own] > 1.5 * strength[original];
            }
        }
    }

    /** {@link #choose(double[], int, double[])} on a straight 16th grid with note-value weights. */
    static boolean[] choose(double[] strength, int density) {
        double[] metric = new double[strength.length];
        for (int slot = 0; slot < metric.length; slot++) {
            metric[slot] = metricWeight(slot);
        }
        return choose(strength, density, metric);
    }

    /**
     * The strongest positions by onset strength (local two-bar level mixed with
     * the song-wide level, see {@link #SONG_WIDE_SHARE}) plus a metrical bonus.
     */
    static boolean[] choose(double[] strength, int density, double[] metric) {
        boolean[] all = new boolean[strength.length];
        Arrays.fill(all, true);
        return choose(strength, density, metric, all, SLOTS_PER_BAR);
    }

    /**
     * As {@link #choose(double[], int, double[])} over the candidate positions
     * only; the local level spans two bars of candidates.
     */
    static boolean[] choose(double[] fullStrength, int density, double[] fullMetric, boolean[] candidate,
            int candidatesPerBar) {
        int[] index = new int[fullStrength.length];
        int count = 0;
        for (int slot = 0; slot < fullStrength.length; slot++) {
            if (candidate[slot]) {
                index[count++] = slot;
            }
        }
        double[] strength = new double[count];
        double[] metric = new double[count];
        for (int i = 0; i < count; i++) {
            strength[i] = fullStrength[index[i]];
            metric[i] = fullMetric[index[i]];
        }
        boolean[] picked = chooseAmong(strength, density, metric, 2 * candidatesPerBar);
        boolean[] chosen = new boolean[fullStrength.length];
        for (int i = 0; i < count; i++) {
            chosen[index[i]] = picked[i];
        }
        return chosen;
    }

    private static boolean[] chooseAmong(double[] strength, int density, double[] metric, int localWindow) {
        int slots = strength.length;
        boolean[] chosen = new boolean[slots];
        if (slots == 0) {
            return chosen;
        }
        double[] local = new double[slots];
        for (int slot = 0; slot < slots; slot++) {
            int from = Math.max(0, slot - localWindow);
            int to = Math.min(slots, slot + localWindow + 1);
            double reference = AudioGridEstimator.percentile(Arrays.copyOfRange(strength, from, to), 90);
            local[slot] = strength[slot] / (reference + 1e-9);
        }
        double scale = AudioGridEstimator.percentile(local, 95) + 1e-9;
        double loud = AudioGridEstimator.percentile(strength, 95) + 1e-9;
        double[] score = new double[slots];
        int eligible = slots;
        Integer[] order = new Integer[slots];
        for (int slot = 0; slot < slots; slot++) {
            double localLevel = local[slot] / scale;
            double songLevel = Math.max(strength[slot] / loud, 1e-6);
            score[slot] = Math.pow(localLevel, 1.0 - SONG_WIDE_SHARE) * Math.pow(songLevel, SONG_WIDE_SHARE)
                    + METRIC_WEIGHT * metric[slot];
            if (strength[slot] < SILENT_ONSET * loud) {
                score[slot] = Double.NEGATIVE_INFINITY;
                eligible--;
            }
            order[slot] = slot;
        }
        Arrays.sort(order, (left, right) -> {
            int compare = Double.compare(score[right], score[left]);
            return compare != 0 ? compare : Integer.compare(left, right);
        });
        int count = Math.min(eligible, (int) Math.round(DENSITY_SCALE[density - 1] * clearOnsets(strength)));
        for (int index = 0; index < count; index++) {
            chosen[order[index]] = true;
        }
        return chosen;
    }

    /** Candidate positions per bar: all of them, or six per beat on the union grid. */
    static int candidatesPerBar(int perBar) {
        return perBar == UNION_SLOTS_PER_BAR ? 6 * BEATS_PER_MEASURE : perBar;
    }

    /**
     * Marks the union-grid candidates and settles the straight/triplet
     * competitions (1/4 vs 1/3, 2/3 vs 3/4): the weaker position is dropped and
     * its strength cleared in the returned copy.
     */
    static double[] unionCandidates(double[] strength, int perBar, boolean[] candidate) {
        return unionCandidates(strength, perBar, candidate, null);
    }

    /**
     * As {@link #unionCandidates(double[], int, boolean[])}; with peak timings
     * ({@link #tripletTiming}) a triplet needs {@link #ON_TRIPLET} and a 1/3
     * also {@link #TRIPLET_CONTEXT_BEATS}; a late 16th keeps the onset on the
     * 16th, and a sound beyond the triplet drops it.
     */
    static double[] unionCandidates(double[] strength, int perBar, boolean[] candidate, byte[] tripletTiming) {
        double[] effective = strength.clone();
        if (perBar != UNION_SLOTS_PER_BAR) {
            Arrays.fill(candidate, true);
            return effective;
        }
        for (int slot = 0; slot < strength.length; slot++) {
            int position = slot % UNION_PER_BEAT;
            candidate[slot] = position == 0 || position == 3 || position == 4 || position == 6
                    || position == 8 || position == 9;
            if (!candidate[slot]) {
                effective[slot] = 0.0;
            }
        }
        if (tripletTiming == null) {
            for (int beatStart = 0; beatStart < strength.length; beatStart += UNION_PER_BEAT) {
                compete(effective, candidate, beatStart + 3, beatStart + 4);
                compete(effective, candidate, beatStart + 9, beatStart + 8);
            }
            return effective;
        }
        int beats = (strength.length + UNION_PER_BEAT - 1) / UNION_PER_BEAT;
        boolean[] lateTriplet = new boolean[beats];
        for (int beat = 0; beat < beats; beat++) {
            int triplet = beat * UNION_PER_BEAT + 8;
            int straight = triplet + 1;
            lateTriplet[beat] = triplet < strength.length && tripletTiming[triplet] == ON_TRIPLET
                    && effective[triplet] > 0
                    && (straight >= strength.length || effective[triplet] > effective[straight]);
        }
        for (int beat = 0; beat < beats; beat++) {
            int beatStart = beat * UNION_PER_BEAT;
            boolean context = false;
            for (int near = Math.max(0, beat - TRIPLET_CONTEXT_BEATS);
                    near <= Math.min(beats - 1, beat + TRIPLET_CONTEXT_BEATS) && !context; near++) {
                context = lateTriplet[near];
            }
            settle(effective, candidate, tripletTiming, beatStart + 3, beatStart + 4, context);
            settle(effective, candidate, tripletTiming, beatStart + 9, beatStart + 8, true);
        }
        return effective;
    }

    /** The straight position keeps a tie; the loser is no longer a candidate. */
    private static void compete(double[] strength, boolean[] candidate, int straight, int triplet) {
        if (triplet >= strength.length) {
            return;
        }
        if (straight >= strength.length || strength[triplet] > strength[straight]) {
            if (straight < strength.length) {
                strength[straight] = 0.0;
                candidate[straight] = false;
            }
        } else {
            strength[triplet] = 0.0;
            candidate[triplet] = false;
        }
    }

    /** Triplet timing: the sound belongs to a position beyond the triplet; drop the triplet. */
    static final byte BEYOND_TRIPLET = 0;
    /** Triplet timing: a late (or early) 16th; the 16th takes the onset. */
    static final byte LATE_SIXTEENTH = 1;
    /** Triplet timing: the sound peaks at the triplet; it competes with the 16th. */
    static final byte ON_TRIPLET = 2;
    /** Search margin around a 16th/triplet pair, and the distance that counts as its edge. */
    private static final double TIMING_MARGIN_SEC = 0.02;
    private static final double TIMING_EDGE_SEC = 0.006;

    /**
     * Where each triplet position's sound peaks, or null when peak times are
     * unknown. A peak at the far edge of the search window is the rising slope
     * of a sound past the triplet (at half tempo, the real 16th at 3/8 of the
     * beat; operator: 燦々デイズ at 90 BPM), not a triplet.
     */
    static byte[] tripletTiming(Onsets onsets, double firstSlotSec, double slotSec, int slots) {
        byte[] timing = new byte[slots];
        for (int beatStart = 0; beatStart < slots; beatStart += UNION_PER_BEAT) {
            for (int[] pair : new int[][] {{3, 4}, {9, 8}}) {
                int triplet = beatStart + pair[1];
                if (triplet >= slots) {
                    continue;
                }
                double straightSec = firstSlotSec + (beatStart + pair[0]) * slotSec;
                double tripletSec = firstSlotSec + triplet * slotSec;
                double direction = Math.signum(tripletSec - straightSec);
                double farEdge = tripletSec + direction * TIMING_MARGIN_SEC;
                double peak = onsets.peakTime(Math.min(straightSec, tripletSec) - TIMING_MARGIN_SEC,
                        Math.max(straightSec, tripletSec) + TIMING_MARGIN_SEC);
                if (Double.isNaN(peak)) {
                    return null;
                }
                if (Math.abs(peak - farEdge) <= TIMING_EDGE_SEC) {
                    timing[triplet] = BEYOND_TRIPLET;
                } else if ((peak - straightSec) / (tripletSec - straightSec) >= TRIPLET_TIMING) {
                    timing[triplet] = ON_TRIPLET;
                } else {
                    timing[triplet] = LATE_SIXTEENTH;
                }
            }
        }
        return timing;
    }

    private static void settle(double[] strength, boolean[] candidate, byte[] timing, int straight, int triplet,
            boolean context) {
        if (triplet >= strength.length) {
            compete(strength, candidate, straight, triplet);
        } else if (timing[triplet] == ON_TRIPLET && context) {
            compete(strength, candidate, straight, triplet);
        } else if (timing[triplet] == BEYOND_TRIPLET) {
            strength[triplet] = 0.0;
            candidate[triplet] = false;
        } else {
            keepStraight(strength, candidate, straight, triplet);
        }
    }

    /** The straight position takes the onset of both (a late 16th peaks between them). */
    private static void keepStraight(double[] strength, boolean[] candidate, int straight, int triplet) {
        if (straight >= strength.length) {
            return;
        }
        if (triplet < strength.length) {
            strength[straight] = Math.max(strength[straight], strength[triplet]);
            strength[triplet] = 0.0;
            candidate[triplet] = false;
        }
    }

    /** Every {@code division}th note: the original fixed grid. */
    static boolean[] fixedGrid(int slots, int division, int perBar) {
        int step = perBar / division;
        boolean[] chosen = new boolean[slots];
        for (int slot = 0; slot < slots; slot += step) {
            chosen[slot] = true;
        }
        return chosen;
    }

    /** Positions whose onset is at least {@link #CLEAR_ONSET} of the song's 95th-percentile strength. */
    static int clearOnsets(double[] strength) {
        double loud = AudioGridEstimator.percentile(strength, 95);
        int count = 0;
        for (double value : strength) {
            if (value >= CLEAR_ONSET * loud && value > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Metrical bonus by the real spacing of the level a position belongs to:
     * a level at least 250 ms apart counts like a beat, 150 ms like an 8th,
     * anything finer like a 16th. At 150 BPM this equals beat/8th/16th = 1/0.5/0.25;
     * at 79 BPM a 16th (190 ms) is weighted like an 8th, so slow songs keep their 16ths.
     */
    static double[] metricWeights(int slots, int perBar, double beatSec) {
        int perBeat = perBar / BEATS_PER_MEASURE;
        double[] weights = new double[slots];
        for (int slot = 0; slot < slots; slot++) {
            int position = slot % perBeat;
            double spacing;
            if (position == 0) {
                spacing = beatSec;
            } else if (perBeat == 4) {
                spacing = position == 2 ? beatSec / 2 : beatSec / 4;
            } else if (perBeat == UNION_PER_BEAT) {
                spacing = position == 6 ? beatSec / 2
                        : position == 4 || position == 8 ? beatSec / 3
                        : position == 3 || position == 9 ? beatSec / 4 : beatSec / 12;
            } else {
                spacing = position % 2 == 0 ? beatSec / 3 : beatSec / 6;
            }
            weights[slot] = spacing >= 0.25 ? 1.0 : spacing >= 0.15 ? 0.5 : 0.25;
        }
        return weights;
    }

    /** beat 1.0, 8th 0.5, 16th 0.25 */
    static double metricWeight(int slot) {
        int position = slot % 4;
        return position == 0 ? 1.0 : position == 2 ? 0.5 : 0.25;
    }

    /**
     * For each full bar, the earlier bar it repeats (cosine similarity of the
     * low/mid/high onset pattern at least {@link #REPEAT_SIMILARITY}), or -1.
     */
    static int[] repeatSources(double[][] bands, int slots, int perBar) {
        // a final partial bar takes part too; its missing positions count as silent
        int bars = (slots + perBar - 1) / perBar;
        double[][] features = new double[bars][];
        double[] energy = new double[bars];
        for (int bar = 0; bar < bars; bar++) {
            double[] feature = new double[AudioGridEstimator.BANDS * perBar];
            for (int column = 0; column < perBar && bar * perBar + column < slots; column++) {
                double[] band = bands[bar * perBar + column];
                for (int b = 0; b < AudioGridEstimator.BANDS; b++) {
                    feature[b * perBar + column] = band[b];
                    energy[bar] += band[b];
                }
            }
            features[bar] = centredUnit(feature);
        }
        // nearly silent bars carry no pattern worth matching
        double floor = bars > 0 ? SILENT_BAR_SHARE * AudioGridEstimator.percentile(energy, 50) : 0.0;
        int[] source = new int[bars];
        Arrays.fill(source, -1);
        for (int bar = 1; bar < bars; bar++) {
            if (energy[bar] <= floor || features[bar] == null) {
                continue;
            }
            double best = REPEAT_SIMILARITY;
            for (int earlier = 0; earlier < bar; earlier++) {
                if (features[earlier] == null || energy[earlier] <= floor) {
                    continue;
                }
                double similarity = dot(features[bar], features[earlier]);
                if (similarity >= best) {
                    best = similarity;
                    // copy from the earliest original, not from another copy
                    source[bar] = source[earlier] >= 0 ? source[earlier] : earlier;
                }
            }
        }
        return source;
    }

    private static int countRepeatedBars(double[][] bands, int slots, int perBar) {
        int count = 0;
        for (int source : repeatSources(bands, slots, perBar)) {
            if (source >= 0) {
                count++;
            }
        }
        return count;
    }

    private static double[] centredUnit(double[] values) {
        double mean = 0.0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.length;
        double norm = 0.0;
        double[] out = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            out[index] = values[index] - mean;
            norm += out[index] * out[index];
        }
        if (norm < 1e-12) {
            return null;
        }
        norm = Math.sqrt(norm);
        for (int index = 0; index < out.length; index++) {
            out[index] /= norm;
        }
        return out;
    }

    private static double dot(double[] left, double[] right) {
        double sum = 0.0;
        for (int index = 0; index < left.length; index++) {
            sum += left[index] * right[index];
        }
        return sum;
    }

    /** The median hi-hat strength over the placed positions: scratch uses the stronger half. */
    static double hiHatFloor(List<Integer> positions, double[][] bands) {
        if (positions.isEmpty()) {
            return 0.0;
        }
        double[] highs = new double[positions.size()];
        for (int index = 0; index < highs.length; index++) {
            highs[index] = bands[positions.get(index)][AudioGridEstimator.BAND_HIGH];
        }
        return AudioGridEstimator.percentile(highs, 50);
    }

    /**
     * A clear hi-hat: the high band at least {@link #HI_HAT_DOMINANCE} times the
     * low and mid bands, and among the stronger half of hi-hats. A bare
     * "high band is the largest" put scratch on 38-56% of positions in real
     * songs; this keeps it near 11%.
     */
    static boolean isHiHat(double[] bands, double floor) {
        double high = bands[AudioGridEstimator.BAND_HIGH];
        return high >= HI_HAT_DOMINANCE * Math.max(bands[0], bands[1]) && high >= floor && high > 0;
    }

    static int dominantBand(double[] bands) {
        int best = 0;
        for (int band = 1; band < bands.length; band++) {
            if (bands[band] > bands[best]) {
                best = band;
            }
        }
        return best;
    }

    /** Ranks the chosen positions by onset strength; stronger positions get larger chords. */
    static int[] chordSizes(List<Integer> positions, double[] strength, Settings settings) {
        int count = positions.size();
        int[] sizes = new int[count];
        if (count == 0) {
            return sizes;
        }
        int range = settings.maxChord() - settings.minChord() + 1;
        double[] cumulative = new double[range];
        double total = 0.0;
        for (int index = 0; index < range; index++) {
            total += Math.pow(0.5, index);
            cumulative[index] = total;
        }
        Integer[] order = new Integer[count];
        for (int index = 0; index < count; index++) {
            order[index] = index;
        }
        Arrays.sort(order, (left, right) -> {
            int compare = Double.compare(strength[positions.get(left)], strength[positions.get(right)]);
            return compare != 0 ? compare : Integer.compare(left, right);
        });
        for (int rank = 0; rank < count; rank++) {
            double target = rank / (double) count * total;
            int size = range - 1;
            // rank 0 is the weakest: walk the cumulative weights from the most frequent (smallest) size
            for (int index = 0; index < range; index++) {
                if (target < cumulative[index]) {
                    size = index;
                    break;
                }
            }
            sizes[order[rank]] = settings.minChord() + size;
        }
        return sizes;
    }

    /** Random lanes, preferring lanes the previous position did not use. */
    static int[] chooseLanes(int count, boolean[] previous, Random random) {
        List<Integer> fresh = new ArrayList<>();
        List<Integer> repeated = new ArrayList<>();
        for (int lane = 0; lane < KEYS; lane++) {
            (previous[lane] ? repeated : fresh).add(lane);
        }
        Collections.shuffle(fresh, random);
        Collections.shuffle(repeated, random);
        fresh.addAll(repeated);
        int[] lanes = new int[Math.min(count, KEYS)];
        for (int index = 0; index < lanes.length; index++) {
            lanes[index] = fresh.get(index);
        }
        Arrays.sort(lanes);
        return lanes;
    }
}
