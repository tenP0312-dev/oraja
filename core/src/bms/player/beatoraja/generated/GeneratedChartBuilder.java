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
    /** Notes reference an undefined WAV id so pressing keys stays silent. */
    private static final String SILENT_WAV = "02";
    private static final int BEATS_PER_MEASURE = 4;
    static final int SLOTS_PER_BAR = 16;
    /** The first measure of notes must start at least this far into the audio. */
    private static final double MIN_LEAD_BEATS = 0.25;
    /** Neighbourhood for onset normalization: two bars of 16ths each side. */
    private static final int LOCAL_WINDOW = 32;
    private static final double METRIC_WEIGHT = 0.3;
    /** Cosine similarity of bar onset patterns above which a bar repeats an earlier one. */
    static final double REPEAT_SIMILARITY = 0.85;
    static final double HI_HAT_DOMINANCE = 2.0;
    /** Scratches are at least an 8th apart. */
    static final int SCRATCH_MIN_GAP = 2;
    /** Bars below this share of the median bar energy count as silent. */
    private static final double SILENT_BAR_SHARE = 0.25;

    private GeneratedChartBuilder() {
    }

    /** Onset evidence around an audio time (seconds). */
    public interface Onsets {
        double strength(double timeSec);

        /** low / mid / high strengths, each on its own comparable scale */
        double[] bands(double timeSec);
    }

    /**
     * @param followMusic place notes where the music hits ({@code density}); off = every
     *                    {@code division}th note (4, 8 or 16), the original fixed grid
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
            if (division != 4 && division != 8 && division != 16) {
                throw new IllegalArgumentException("division must be 4, 8 or 16");
            }
            if (minChord < 1 || maxChord > KEYS || minChord > maxChord) {
                throw new IllegalArgumentException("chord range must satisfy 1 <= min <= max <= " + KEYS);
            }
        }

        /** Everything on: follow the music at {@code density}, repeat bars. */
        public Settings(int density, int minChord, int maxChord, boolean scratch) {
            this(true, density, 8, true, minChord, maxChord, scratch);
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
        double slotSec = measureSec / SLOTS_PER_BAR;
        int slots = Math.max(0, (int) Math.floor((endSec - firstSlotSec) / slotSec) + 1);

        double[] strength = new double[slots];
        double[][] bands = new double[slots][];
        for (int slot = 0; slot < slots; slot++) {
            double time = firstSlotSec + slot * slotSec;
            strength[slot] = onsets.strength(time);
            bands[slot] = onsets.bands(time);
        }
        List<Placement> placements = place(strength, bands, settings, new Random(seed));
        int repeated = settings.repeatBars() ? countRepeatedBars(bands, slots) : 0;

        int totalNotes = 0;
        for (Placement placement : placements) {
            totalNotes += placement.lanes.length + (placement.scratch ? 1 : 0);
        }
        int measures = (slots + SLOTS_PER_BAR - 1) / SLOTS_PER_BAR;
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
        text.append("\n*---------------------- MAIN DATA FIELD\n");
        text.append(String.format(Locale.ROOT, "#00102:%.9f\n", firstSlotSec / measureSec));
        text.append("#00101:").append(AUDIO_WAV).append('\n');

        String[][][] channelSlots = new String[measures][KEY_CHANNELS.length + 1][SLOTS_PER_BAR];
        for (String[][] measure : channelSlots) {
            for (String[] channel : measure) {
                Arrays.fill(channel, "00");
            }
        }
        for (Placement placement : placements) {
            String[][] measure = channelSlots[placement.slot / SLOTS_PER_BAR];
            int column = placement.slot % SLOTS_PER_BAR;
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
    static List<Placement> place(double[] strength, double[][] bands, Settings settings, Random random) {
        int slots = strength.length;
        boolean[] chosen = settings.followMusic()
                ? choose(strength, settings.density())
                : fixedGrid(slots, settings.division());
        int[] source;
        if (settings.repeatBars()) {
            source = repeatSources(bands, slots);
            if (settings.followMusic()) {
                followSourceBars(chosen, strength, source);
            }
        } else {
            source = new int[slots / SLOTS_PER_BAR];
            Arrays.fill(source, -1);
        }
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
            int bar = slot / SLOTS_PER_BAR;
            Placement placement;
            // a repeated bar reuses the lanes its source bar used at the same column
            Placement copied = bar < source.length && source[bar] >= 0
                    ? bySlot[source[bar] * SLOTS_PER_BAR + slot % SLOTS_PER_BAR]
                    : null;
            if (copied != null) {
                placement = new Placement(slot, copied.lanes.clone(), copied.scratch);
            } else {
                boolean scratch = settings.scratch()
                        && isHiHat(bands[slot], hiHatFloor)
                        && slot - lastScratchSlot >= SCRATCH_MIN_GAP;
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
    static void followSourceBars(boolean[] chosen, double[] strength, int[] source) {
        for (int bar = 0; bar < source.length; bar++) {
            if (source[bar] < 0) {
                continue;
            }
            for (int column = 0; column < SLOTS_PER_BAR; column++) {
                int own = bar * SLOTS_PER_BAR + column;
                int original = source[bar] * SLOTS_PER_BAR + column;
                chosen[own] = chosen[original]
                        ? strength[own] >= 0.5 * strength[original]
                        : chosen[own] && strength[own] > 1.5 * strength[original];
            }
        }
    }

    /** The strongest share of positions by local onset strength plus a metrical bonus. */
    static boolean[] choose(double[] strength, int density) {
        int slots = strength.length;
        boolean[] chosen = new boolean[slots];
        if (slots == 0) {
            return chosen;
        }
        double[] local = new double[slots];
        for (int slot = 0; slot < slots; slot++) {
            int from = Math.max(0, slot - LOCAL_WINDOW);
            int to = Math.min(slots, slot + LOCAL_WINDOW + 1);
            double reference = AudioGridEstimator.percentile(Arrays.copyOfRange(strength, from, to), 90);
            local[slot] = strength[slot] / (reference + 1e-9);
        }
        double scale = AudioGridEstimator.percentile(local, 95) + 1e-9;
        double[] score = new double[slots];
        Integer[] order = new Integer[slots];
        for (int slot = 0; slot < slots; slot++) {
            score[slot] = local[slot] / scale + METRIC_WEIGHT * metricWeight(slot);
            order[slot] = slot;
        }
        Arrays.sort(order, (left, right) -> {
            int compare = Double.compare(score[right], score[left]);
            return compare != 0 ? compare : Integer.compare(left, right);
        });
        int count = Math.min(slots, (int) Math.round(DENSITY_SCALE[density - 1] * clearOnsets(strength)));
        for (int index = 0; index < count; index++) {
            chosen[order[index]] = true;
        }
        return chosen;
    }

    /** Every {@code division}th note: the original fixed grid. */
    static boolean[] fixedGrid(int slots, int division) {
        int step = SLOTS_PER_BAR / division;
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

    /** beat 1.0, 8th 0.5, 16th 0.25 */
    static double metricWeight(int slot) {
        int position = slot % 4;
        return position == 0 ? 1.0 : position == 2 ? 0.5 : 0.25;
    }

    /**
     * For each full bar, the earlier bar it repeats (cosine similarity of the
     * low/mid/high onset pattern at least {@link #REPEAT_SIMILARITY}), or -1.
     */
    static int[] repeatSources(double[][] bands, int slots) {
        int bars = slots / SLOTS_PER_BAR;
        double[][] features = new double[bars][];
        double[] energy = new double[bars];
        for (int bar = 0; bar < bars; bar++) {
            double[] feature = new double[AudioGridEstimator.BANDS * SLOTS_PER_BAR];
            for (int column = 0; column < SLOTS_PER_BAR; column++) {
                double[] band = bands[bar * SLOTS_PER_BAR + column];
                for (int b = 0; b < AudioGridEstimator.BANDS; b++) {
                    feature[b * SLOTS_PER_BAR + column] = band[b];
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

    private static int countRepeatedBars(double[][] bands, int slots) {
        int count = 0;
        for (int source : repeatSources(bands, slots)) {
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
