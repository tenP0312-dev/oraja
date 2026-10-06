package bms.player.beatoraja.generated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/**
 * Builds a 7-key BMS chart on a fixed grid over one audio file (Issue #433).
 *
 * <p>Every grid position gets notes. The chord size follows the music: the
 * onset strength at each position is ranked, and stronger positions get
 * larger chords within [{@code minChord}, {@code maxChord}] (each size is half
 * as frequent as the one below it). Lanes are random, avoiding the lanes of the
 * previous position where possible so repeated-lane jacks stay rare.</p>
 *
 * <p>Layout: measure 0 is an empty lead-in, measure 1 starts the audio and is
 * shortened so that measure 2 begins exactly on a beat of the music.</p>
 */
public final class GeneratedChartBuilder {

    public static final int KEYS = 7;
    /** BMS 1P key channels for keys 1-7, then the scratch channel. */
    private static final String[] KEY_CHANNELS = {"11", "12", "13", "14", "15", "18", "19"};
    private static final String SCRATCH_CHANNEL = "16";
    private static final String AUDIO_WAV = "01";
    /** Notes reference an undefined WAV id so pressing keys stays silent. */
    private static final String SILENT_WAV = "02";
    private static final int BEATS_PER_MEASURE = 4;
    /** The first measure of notes must start at least this far into the audio. */
    private static final double MIN_LEAD_BEATS = 0.25;
    private static final double SCRATCH_RANK = 0.9;

    private GeneratedChartBuilder() {
    }

    /**
     * @param division notes per measure: 4, 8 or 16
     */
    public record Settings(int division, int minChord, int maxChord, boolean scratch) {
        public Settings {
            if (division != 4 && division != 8 && division != 16) {
                throw new IllegalArgumentException("division must be 4, 8 or 16");
            }
            if (minChord < 1 || maxChord > KEYS || minChord > maxChord) {
                throw new IllegalArgumentException("chord range must satisfy 1 <= min <= max <= " + KEYS);
            }
        }

        public String label() {
            String chords = minChord == maxChord ? Integer.toString(minChord) : minChord + "-" + maxChord;
            return division + "th x" + chords + (scratch ? " +SC" : "");
        }
    }

    public record Chart(String text, int notes, int positions) {
    }

    /**
     * @param audioFileName file name of the audio, relative to the chart
     * @param bpm           tempo of the grid
     * @param firstBeatSec  audio time of a beat of the music
     * @param endSec        no notes after this audio time
     * @param strengthAt    onset strength around an audio time (seconds)
     */
    public static Chart build(
            String title,
            String audioFileName,
            double bpm,
            double firstBeatSec,
            double endSec,
            DoubleUnaryOperator strengthAt,
            Settings settings,
            long seed) {
        if (!(bpm > 0) || !Double.isFinite(bpm)) {
            throw new IllegalArgumentException("bpm must be positive");
        }
        double beat = 60.0 / bpm;
        double measureSec = beat * BEATS_PER_MEASURE;
        // first note on a beat of the music, at least MIN_LEAD_BEATS into the audio
        double firstNoteSec = firstBeatSec;
        while (firstNoteSec < MIN_LEAD_BEATS * beat) {
            firstNoteSec += beat;
        }
        double slotSec = measureSec / settings.division();
        int positions = Math.max(0, (int) Math.floor((endSec - firstNoteSec) / slotSec) + 1);

        int[] chordSizes = chordSizes(firstNoteSec, slotSec, positions, strengthAt, settings);
        boolean[] scratch = scratchPositions(firstNoteSec, slotSec, positions, strengthAt, settings);
        int[][] lanes = assignLanes(chordSizes, new Random(seed));

        int measures = (positions + settings.division() - 1) / settings.division();
        int totalNotes = 0;
        for (int position = 0; position < positions; position++) {
            totalNotes += lanes[position].length + (scratch[position] ? 1 : 0);
        }
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
        text.append(String.format(Locale.ROOT, "#00102:%.9f\n", firstNoteSec / measureSec));
        text.append("#00101:").append(AUDIO_WAV).append('\n');

        int notes = 0;
        for (int measure = 0; measure < measures; measure++) {
            String[][] channelSlots = new String[KEY_CHANNELS.length + 1][settings.division()];
            for (String[] slots : channelSlots) {
                Arrays.fill(slots, "00");
            }
            for (int slot = 0; slot < settings.division(); slot++) {
                int position = measure * settings.division() + slot;
                if (position >= positions) {
                    break;
                }
                for (int lane : lanes[position]) {
                    channelSlots[lane][slot] = SILENT_WAV;
                    notes++;
                }
                if (scratch[position]) {
                    channelSlots[KEY_CHANNELS.length][slot] = SILENT_WAV;
                    notes++;
                }
            }
            String measureNumber = String.format(Locale.ROOT, "%03d", measure + 2);
            for (int channel = 0; channel < channelSlots.length; channel++) {
                String data = String.join("", channelSlots[channel]);
                if (data.chars().allMatch(c -> c == '0')) {
                    continue;
                }
                String id = channel < KEY_CHANNELS.length ? KEY_CHANNELS[channel] : SCRATCH_CHANNEL;
                text.append('#').append(measureNumber).append(id).append(':').append(data).append('\n');
            }
        }
        return new Chart(text.toString(), notes, positions);
    }

    /** Rank positions by onset strength; stronger positions get larger chords. */
    static int[] chordSizes(double firstNoteSec, double slotSec, int positions,
            DoubleUnaryOperator strengthAt, Settings settings) {
        int[] sizes = new int[positions];
        if (positions == 0) {
            return sizes;
        }
        int range = settings.maxChord() - settings.minChord() + 1;
        double[] cumulative = new double[range];
        double total = 0.0;
        for (int index = 0; index < range; index++) {
            total += Math.pow(0.5, index);
            cumulative[index] = total;
        }
        double[] rank = ranks(firstNoteSec, slotSec, positions, strengthAt);
        for (int position = 0; position < positions; position++) {
            double target = rank[position] * total;
            int size = range - 1;
            // rank 0 is the weakest: walk the cumulative weights from the most frequent (smallest) size
            for (int index = 0; index < range; index++) {
                if (target < cumulative[index]) {
                    size = index;
                    break;
                }
            }
            sizes[position] = settings.minChord() + size;
        }
        return sizes;
    }

    private static boolean[] scratchPositions(double firstNoteSec, double slotSec, int positions,
            DoubleUnaryOperator strengthAt, Settings settings) {
        boolean[] scratch = new boolean[positions];
        if (!settings.scratch() || positions == 0) {
            return scratch;
        }
        double[] rank = ranks(firstNoteSec, slotSec, positions, strengthAt);
        for (int position = 0; position < positions; position++) {
            // only the strongest accents, and never two in a row
            scratch[position] = rank[position] >= SCRATCH_RANK && (position == 0 || !scratch[position - 1]);
        }
        return scratch;
    }

    /** Fractional rank in [0, 1) of each position's onset strength; ties keep time order. */
    private static double[] ranks(double firstNoteSec, double slotSec, int positions, DoubleUnaryOperator strengthAt) {
        double[] strength = new double[positions];
        Integer[] order = new Integer[positions];
        for (int position = 0; position < positions; position++) {
            strength[position] = strengthAt.applyAsDouble(firstNoteSec + position * slotSec);
            order[position] = position;
        }
        Arrays.sort(order, (left, right) -> {
            int compare = Double.compare(strength[left], strength[right]);
            return compare != 0 ? compare : Integer.compare(left, right);
        });
        double[] rank = new double[positions];
        for (int index = 0; index < positions; index++) {
            rank[order[index]] = index / (double) positions;
        }
        return rank;
    }

    /** Random lanes, preferring lanes the previous position did not use. */
    static int[][] assignLanes(int[] chordSizes, Random random) {
        int[][] lanes = new int[chordSizes.length][];
        boolean[] previous = new boolean[KEYS];
        for (int position = 0; position < chordSizes.length; position++) {
            List<Integer> fresh = new ArrayList<>();
            List<Integer> repeated = new ArrayList<>();
            for (int lane = 0; lane < KEYS; lane++) {
                (previous[lane] ? repeated : fresh).add(lane);
            }
            Collections.shuffle(fresh, random);
            Collections.shuffle(repeated, random);
            fresh.addAll(repeated);
            int size = Math.min(chordSizes[position], KEYS);
            lanes[position] = new int[size];
            Arrays.fill(previous, false);
            for (int index = 0; index < size; index++) {
                int lane = fresh.get(index);
                lanes[position][index] = lane;
                previous[lane] = true;
            }
            Arrays.sort(lanes[position]);
        }
        return lanes;
    }
}
