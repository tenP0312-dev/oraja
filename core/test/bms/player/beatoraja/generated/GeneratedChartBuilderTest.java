package bms.player.beatoraja.generated;

import bms.model.BMSDecoder;
import bms.model.BMSModel;
import bms.model.Mode;
import bms.model.Note;
import bms.model.TimeLine;
import bms.player.beatoraja.bmsir.BMSIRTestPlayFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedChartBuilderTest {
    @TempDir
    Path directory;

    private static final double BPM = 150.0;
    private static final double BEAT = 60.0 / BPM;
    private static final double SLOT = BEAT / 4;
    private static final double FIRST_BEAT = BEAT;

    /**
     * Synthetic music on the 16th grid: a per-bar pattern of low/mid/high
     * strengths. {@code bars[b]} picks the pattern of bar b; null bars are silent.
     */
    private record Music(double[][][] bars) implements GeneratedChartBuilder.Onsets {
        @Override
        public double strength(double timeSec) {
            double[] bands = bands(timeSec);
            return bands[0] + bands[1] + bands[2];
        }

        @Override
        public double[] bands(double timeSec) {
            int slot = (int) Math.round((timeSec - FIRST_BEAT) / SLOT);
            if (slot < 0 || Math.abs(timeSec - FIRST_BEAT - slot * SLOT) > 1e-6) {
                return new double[3];
            }
            int bar = slot / 16;
            if (bar >= bars.length || bars[bar] == null) {
                return new double[3];
            }
            return bars[bar][slot % 16].clone();
        }

        double endSec() {
            return FIRST_BEAT + bars.length * 16 * SLOT - SLOT / 2;
        }
    }

    /** kick on 0/8, snare on 4/12, hi-hats on every 8th, plus extra hits from {@code variant} */
    private static double[][] drumBar(long variant) {
        double[][] bar = new double[16][3];
        for (int slot = 0; slot < 16; slot += 2) {
            bar[slot][2] = 0.6;
        }
        bar[0][0] = 3.0;
        bar[8][0] = 3.0;
        bar[4][1] = 2.5;
        bar[12][1] = 2.5;
        Random random = new Random(variant);
        for (int hit = 0; hit < 3; hit++) {
            bar[1 + 2 * random.nextInt(8)][1] += 1.0 + random.nextDouble();
        }
        return bar;
    }

    /** bars with independent random hits, so no bar repeats another */
    private static Music varied(int bars) {
        Random random = new Random(1000 + bars);
        double[][][] pattern = new double[bars][16][3];
        for (int bar = 0; bar < bars; bar++) {
            for (int slot = 0; slot < 16; slot++) {
                for (int band = 0; band < 3; band++) {
                    pattern[bar][slot][band] = random.nextDouble() < 0.4 ? 0.5 + 2.5 * random.nextDouble() : 0.0;
                }
            }
        }
        return new Music(pattern);
    }

    private BMSModel decode(GeneratedChartBuilder.Chart chart) throws Exception {
        Path bms = directory.resolve("chart.bms");
        Files.write(bms, chart.text().getBytes(Charset.forName("MS932")));
        BMSModel model = new BMSDecoder().decode(bms);
        assertNotNull(model);
        return model;
    }

    private static GeneratedChartBuilder.Chart build(Music music, GeneratedChartBuilder.Settings settings, long seed) {
        return GeneratedChartBuilder.build("曲名 test", "audio.mp3", BPM, FIRST_BEAT, music.endSec(), music, settings, seed);
    }

    private static long audioStartMicros(BMSModel model) {
        for (TimeLine timeLine : model.getAllTimeLines()) {
            for (Note note : timeLine.getBackGroundNotes()) {
                if (note.getWav() >= 0) {
                    return timeLine.getMicroTime();
                }
            }
        }
        return -1;
    }

    private static int keyCount(TimeLine timeLine) {
        int count = 0;
        for (int lane = 0; lane < 7; lane++) {
            count += timeLine.getNote(lane) != null ? 1 : 0;
        }
        return count;
    }

    /** grid slot (from the first beat) → sorted key lanes, plus scratch as lane 7 */
    private static List<int[]> rows(BMSModel model) {
        return rows(model, 4);
    }

    /** as {@link #rows(BMSModel)} on a grid of {@code perBeat} positions per beat */
    private static List<int[]> rows(BMSModel model, int perBeat) {
        double grid = BEAT / perBeat;
        long start = audioStartMicros(model);
        List<int[]> rows = new ArrayList<>();
        for (TimeLine timeLine : model.getAllTimeLines()) {
            List<Integer> lanes = new ArrayList<>();
            for (int lane = 0; lane < 8; lane++) {
                if (timeLine.getNote(lane) != null) {
                    lanes.add(lane);
                }
            }
            if (lanes.isEmpty()) {
                continue;
            }
            double audio = (timeLine.getMicroTime() - start) / 1e6;
            int slot = (int) Math.round((audio - FIRST_BEAT) / grid);
            assertEquals(FIRST_BEAT + slot * grid, audio, 2e-6, "note off the grid");
            int[] row = new int[lanes.size() + 1];
            row[0] = slot;
            for (int index = 0; index < lanes.size(); index++) {
                row[index + 1] = lanes.get(index);
            }
            rows.add(row);
        }
        return rows;
    }

    @Test
    void decodesAsSevenKeyChartOnTheAudioGrid() throws Exception {
        Music music = varied(24);
        GeneratedChartBuilder.Chart chart = build(music, new GeneratedChartBuilder.Settings(3, 1, 3, false), 1);
        BMSModel model = decode(chart);
        assertEquals(Mode.BEAT_7K, model.getMode());
        assertEquals(BPM, model.getBpm(), 1e-6);
        assertEquals(chart.notes(), model.getTotalNotes());
        assertEquals(4 * BEAT * 1e6, audioStartMicros(model), 2);
        assertEquals(chart.positions(), rows(model).size());
        assertTrue(chart.text().contains("#TOTAL "));
    }

    @Test
    void notesGoWhereTheMusicHitsAndNotInSilence() throws Exception {
        double[][][] bars = new double[16][][];
        for (int bar = 0; bar < bars.length; bar++) {
            bars[bar] = bar >= 6 && bar < 9 ? null : drumBar(2000 + bar);
        }
        Music music = new Music(bars);
        java.util.Set<Integer> placed = new java.util.HashSet<>();
        for (int[] row : rows(decode(build(music, new GeneratedChartBuilder.Settings(1, 1, 1, false), 2)))) {
            int bar = row[0] / 16;
            assertFalse(bar >= 6 && bar < 9, "note in a silent bar at slot " + row[0]);
            assertTrue(music.strength(FIRST_BEAT + row[0] * SLOT) > 0, "note on silence at slot " + row[0]);
            placed.add(row[0]);
        }
        // the smallest amount still keeps every kick and snare
        for (int bar = 0; bar < bars.length; bar++) {
            if (bars[bar] == null) {
                continue;
            }
            for (int column : new int[] {0, 4, 8, 12}) {
                assertTrue(placed.contains(bar * 16 + column), "missing hit at bar " + bar + " slot " + column);
            }
        }
    }

    @Test
    void noteAmountScalesTheSongsOwnDensity() {
        Music music = varied(20);
        int slots = (int) Math.floor((music.endSec() - FIRST_BEAT) / SLOT) + 1;
        double[] strength = strengths(music, slots);
        int clear = GeneratedChartBuilder.clearOnsets(strength);
        int nonZero = 0;
        for (double value : strength) {
            nonZero += value > 0 ? 1 : 0;
        }
        assertTrue(clear > 0 && clear <= nonZero, clear + " of " + nonZero);
        // the extra notes of the higher amounts go only where something sounds
        double loud = AudioGridEstimator.percentile(strength, 95);
        int audible = 0;
        for (double value : strength) {
            audible += value >= GeneratedChartBuilder.SILENT_ONSET * loud ? 1 : 0;
        }
        int previous = 0;
        for (int density = GeneratedChartBuilder.MIN_DENSITY; density <= GeneratedChartBuilder.MAX_DENSITY; density++) {
            int count = 0;
            for (boolean chosen : GeneratedChartBuilder.choose(strength, density)) {
                count += chosen ? 1 : 0;
            }
            assertEquals(Math.min(audible, Math.round(GeneratedChartBuilder.DENSITY_SCALE[density - 1] * clear)), count);
            assertTrue(count > previous || count == audible);
            previous = count;
        }
        // "as the music" places exactly the song's clear onsets
        int count = 0;
        for (boolean chosen : GeneratedChartBuilder.choose(strength, GeneratedChartBuilder.DEFAULT_DENSITY)) {
            count += chosen ? 1 : 0;
        }
        assertEquals(clear, count);
    }

    private static double[] strengths(Music music, int slots) {
        double[] strength = new double[slots];
        for (int slot = 0; slot < slots; slot++) {
            strength[slot] = music.strength(FIRST_BEAT + slot * SLOT);
        }
        return strength;
    }

    @Test
    void repeatedBarsRepeatTheirLayout() throws Exception {
        double[][] verse = drumBar(1);
        double[][] fill = drumBar(2);
        fill[2][0] = 3.0;
        fill[6][0] = 3.0;
        fill[10][1] = 3.0;
        double[][][] bars = {verse, verse, verse, fill, verse, verse, fill, verse};
        GeneratedChartBuilder.Chart chart = build(new Music(bars), new GeneratedChartBuilder.Settings(3, 1, 3, false), 3);
        assertEquals(6, chart.repeated(), "every bar after the first verse and the first fill repeats");
        assertEquals(0, build(varied(8), new GeneratedChartBuilder.Settings(3, 1, 3, false), 3).repeated());
        List<int[]> rows = rows(decode(chart));
        String first = layout(rows, 0);
        String firstFill = layout(rows, 3);
        assertNotEquals(first, firstFill);
        for (int bar : new int[] {1, 2, 4, 5, 7}) {
            assertEquals(first, layout(rows, bar), "verse bar " + bar);
        }
        assertEquals(firstFill, layout(rows, 6), "second fill");
    }

    @Test
    void aRepeatedBarKeepsItsOwnFill() throws Exception {
        // the fourth bar repeats the verse except for a 16th roll in its last beat
        double[][] verse = drumBar(1);
        double[][] rolled = drumBar(1);
        for (int slot = 12; slot < 16; slot++) {
            rolled[slot][1] = Math.max(rolled[slot][1], 1.2);
        }
        double[][][] bars = {verse, verse, verse, rolled, verse, verse};
        List<int[]> repeated = rows(decode(build(new Music(bars), new GeneratedChartBuilder.Settings(3, 1, 3, false), 4)));
        GeneratedChartBuilder.Settings noRepeat = new GeneratedChartBuilder.Settings(true, 3, 8, false, 1, 3, false);
        List<int[]> independent = rows(decode(build(new Music(bars), noRepeat, 4)));
        // repetition only reuses lanes: every bar keeps the positions of its own audio
        assertEquals(positions(independent), positions(repeated));
        for (int slot = 3 * 16 + 12; slot < 4 * 16; slot++) {
            final int roll = slot;
            assertTrue(repeated.stream().anyMatch(row -> row[0] == roll), "roll 16th without a note at " + slot);
        }
    }

    private static List<Integer> positions(List<int[]> rows) {
        List<Integer> positions = new ArrayList<>();
        for (int[] row : rows) {
            positions.add(row[0]);
        }
        return positions;
    }

    @Test
    void noNotesOutsideTheRhythmicSpanOrWhereASoundDiesAway() throws Exception {
        double[][][] bars = new double[12][][];
        for (int bar = 0; bar < bars.length; bar++) {
            bars[bar] = drumBar(7000 + bar);
        }
        Music music = new Music(bars);
        double spanStart = FIRST_BEAT + 2 * 4 * BEAT;
        double spanEnd = FIRST_BEAT + 10 * 4 * BEAT;
        double fadeFrom = FIRST_BEAT + 5 * 4 * BEAT;
        double fadeTo = FIRST_BEAT + 6 * 4 * BEAT;
        GeneratedChartBuilder.Onsets limited = new GeneratedChartBuilder.Onsets() {
            public double strength(double timeSec) {
                return music.strength(timeSec);
            }

            public double[] bands(double timeSec) {
                return music.bands(timeSec);
            }

            public double[] rhythmicSpan(double firstBarSec, double barSec, double endSec, double[] barStarts) {
                return new double[] {spanStart, spanEnd};
            }

            public boolean fading(double timeSec) {
                return timeSec >= fadeFrom && timeSec < fadeTo;
            }
        };
        List<int[]> rows = rows(decode(GeneratedChartBuilder.build("t", "audio.mp3", BPM, FIRST_BEAT, music.endSec(),
                limited, new GeneratedChartBuilder.Settings(4, 1, 1, false), 5)));
        assertFalse(rows.isEmpty());
        for (int[] row : rows) {
            int bar = row[0] / 16;
            assertTrue(row[0] >= 2 * 16 && row[0] <= 10 * 16, "a note outside the rhythmic span at slot " + row[0]);
            assertNotEquals(5, bar, "a note where the sound dies away at slot " + row[0]);
        }
    }

    @Test
    void aDenseChorusGetsChordsLikeASparseVerse() throws Exception {
        // verse: few loud drum hits; chorus: twice the hits, each measuring half as strong
        double[][][] bars = new double[16][][];
        for (int bar = 0; bar < bars.length; bar++) {
            double[][] pattern = new double[16][3];
            boolean chorus = bar >= 8;
            for (int slot = 0; slot < 16; slot += chorus ? 1 : 2) {
                pattern[slot][0] = (chorus ? 1.0 : 2.0) * (1.0 + 0.3 * ((slot * 7 + bar * 3) % 5) / 4.0);
            }
            bars[bar] = pattern;
        }
        List<int[]> rows = rows(decode(build(new Music(bars), new GeneratedChartBuilder.Settings(3, 1, 4, false), 6)));
        double verse = 0;
        double chorus = 0;
        int verseRows = 0;
        int chorusRows = 0;
        for (int[] row : rows) {
            if (row[0] / 16 < 8) {
                verse += row.length - 1;
                verseRows++;
            } else {
                chorus += row.length - 1;
                chorusRows++;
            }
        }
        assertTrue(verseRows > 0 && chorusRows > 0);
        // ranked song-wide the chorus got only single notes (1.0 vs 2.0)
        assertTrue(chorus / chorusRows >= 0.75 * verse / verseRows,
                "chorus chords " + chorus / chorusRows + " vs verse " + verse / verseRows);
    }

    private static String layout(List<int[]> rows, int bar) {
        StringBuilder text = new StringBuilder();
        for (int[] row : rows) {
            if (row[0] / 16 == bar) {
                text.append(row[0] % 16).append(':');
                for (int index = 1; index < row.length; index++) {
                    text.append(row[index]).append(',');
                }
                text.append(' ');
            }
        }
        return text.toString();
    }

    @Test
    void chordSizesStayInRangeAndConstantWhenMinEqualsMax() throws Exception {
        Music music = varied(20);
        int[] histogram = new int[8];
        for (int[] row : rows(decode(build(music, new GeneratedChartBuilder.Settings(3, 2, 4, false), 4)))) {
            int size = row.length - 1;
            assertTrue(size >= 2 && size <= 4, "chord " + size);
            histogram[size]++;
        }
        assertTrue(histogram[2] > histogram[3] && histogram[3] > histogram[4], java.util.Arrays.toString(histogram));
        for (int[] row : rows(decode(build(music, new GeneratedChartBuilder.Settings(2, 3, 3, false), 4)))) {
            assertEquals(3, row.length - 1);
        }
    }

    @Test
    void lanesAvoidRepeatsWhenThereIsRoom() throws Exception {
        // every bar differs, so no layout is copied across a bar line
        List<int[]> rows = rows(decode(build(varied(20), new GeneratedChartBuilder.Settings(4, 1, 3, false), 5)));
        for (int index = 1; index < rows.size(); index++) {
            for (int a = 1; a < rows.get(index).length; a++) {
                for (int b = 1; b < rows.get(index - 1).length; b++) {
                    assertNotEquals(rows.get(index - 1)[b], rows.get(index)[a], "jack at slot " + rows.get(index)[0]);
                }
            }
        }
    }

    @Test
    void scratchOnlyOnClearHiHatsAtLeastAnEighthApart() throws Exception {
        double[][][] bars = new double[16][][];
        for (int bar = 0; bar < bars.length; bar++) {
            double[][] pattern = drumBar(3000 + bar);
            pattern[15][2] = 2.0; // a hi-hat run across the bar line
            pattern[14][2] = 2.0;
            bars[bar] = pattern;
        }
        Music music = new Music(bars);
        int scratches = 0;
        int positions = 0;
        int previousScratchSlot = Integer.MIN_VALUE / 2;
        for (int[] row : rows(decode(build(music, new GeneratedChartBuilder.Settings(4, 1, 2, true), 6)))) {
            positions++;
            if (row[row.length - 1] == 7) {
                double[] bands = music.bands(FIRST_BEAT + row[0] * SLOT);
                assertTrue(bands[2] >= 2.0 * Math.max(bands[0], bands[1]) && bands[2] > 0,
                        "scratch away from a clear hi-hat at " + row[0]);
                assertTrue(row[0] - previousScratchSlot >= 2, "scratches closer than an 8th at " + row[0]);
                previousScratchSlot = row[0];
                scratches++;
            }
        }
        assertTrue(scratches > 0);
        // positions are audible hits only (no silent filler), most of them hi-hats here
        assertTrue(scratches < positions * 2 / 5, scratches + " scratches of " + positions);
        for (int[] row : rows(decode(build(music, new GeneratedChartBuilder.Settings(4, 1, 2, false), 6)))) {
            assertNotEquals(7, row[row.length - 1]);
        }
    }

    @Test
    void hiHatRuleNeedsDominanceAndTheStrongerHalf() {
        assertTrue(GeneratedChartBuilder.isHiHat(new double[] {0.5, 0.4, 1.0}, 0.8));
        assertFalse(GeneratedChartBuilder.isHiHat(new double[] {0.6, 0.4, 1.0}, 0.8), "high band not 2x the low band");
        assertFalse(GeneratedChartBuilder.isHiHat(new double[] {0.1, 0.1, 0.7}, 0.8), "weaker half of hi-hats");
        assertFalse(GeneratedChartBuilder.isHiHat(new double[] {0, 0, 0}, 0.0));
    }

    @Test
    void sameSeedIsDeterministicAndReshuffleChangesLanes() {
        Music music = varied(12);
        GeneratedChartBuilder.Settings settings = new GeneratedChartBuilder.Settings(2, 1, 2, false);
        assertEquals(build(music, settings, 9).text(), build(music, settings, 9).text());
        assertNotEquals(build(music, settings, 9).text(), build(music, settings, 10).text());
    }

    @Test
    void earlyFirstBeatGetsALeadOfAtLeastAQuarterBeat() throws Exception {
        GeneratedChartBuilder.Chart chart = GeneratedChartBuilder.build("t", "audio.mp3", BPM, 0.01, 20.0,
                new GeneratedChartBuilder.Onsets() {
                    public double strength(double timeSec) {
                        return 1.0;
                    }

                    public double[] bands(double timeSec) {
                        return new double[] {1, 0, 0};
                    }
                }, new GeneratedChartBuilder.Settings(4, 1, 1, false), 1);
        BMSModel model = decode(chart);
        long start = audioStartMicros(model);
        long first = -1;
        for (TimeLine timeLine : model.getAllTimeLines()) {
            if (keyCount(timeLine) > 0) {
                first = timeLine.getMicroTime();
                break;
            }
        }
        assertEquals((0.01 + BEAT) * 1e6, first - start, 2);
    }

    @Test
    void followMusicOffIsThePlainFixedGrid() throws Exception {
        Music music = varied(12);
        for (int division : new int[] {4, 8, 12, 16, 24}) {
            GeneratedChartBuilder.Settings settings =
                    new GeneratedChartBuilder.Settings(false, 3, division, false, 1, 1, false);
            int perBar = settings.perBar();
            int slots = (int) Math.floor((music.endSec() - FIRST_BEAT) / (4 * BEAT / perBar)) + 1;
            List<int[]> rows = rows(decode(build(music, settings, 7)), perBar / 4);
            int step = perBar / division;
            assertEquals((slots + step - 1) / step, rows.size(), "every " + division + "th");
            for (int[] row : rows) {
                assertEquals(0, row[0] % step);
            }
        }
    }

    @Test
    void repeatOffLaysOutEveryBarIndependently() throws Exception {
        double[][] verse = drumBar(1);
        double[][][] bars = {verse, verse, verse, verse, verse, verse};
        GeneratedChartBuilder.Settings off = new GeneratedChartBuilder.Settings(true, 3, 8, false, 1, 3, false);
        GeneratedChartBuilder.Chart chart = build(new Music(bars), off, 3);
        assertEquals(0, chart.repeated());
        List<int[]> rows = rows(decode(chart));
        java.util.Set<String> layouts = new java.util.HashSet<>();
        for (int bar = 0; bar < bars.length; bar++) {
            layouts.add(layout(rows, bar));
        }
        assertTrue(layouts.size() > 1, "identical audio still gets fresh random lanes per bar");
        GeneratedChartBuilder.Settings on = new GeneratedChartBuilder.Settings(true, 3, 8, true, 1, 3, false);
        List<int[]> repeated = rows(decode(build(new Music(bars), on, 3)));
        for (int bar = 1; bar < bars.length; bar++) {
            assertEquals(layout(repeated, 0), layout(repeated, bar));
        }
    }

    /** Hits on triplet 8ths (k/3 of a beat) with nothing on straight 8ths or 16ths. */
    private static GeneratedChartBuilder.Onsets tripletMusic() {
        return new GeneratedChartBuilder.Onsets() {
            public double strength(double timeSec) {
                double[] bands = bands(timeSec);
                return bands[0] + bands[1] + bands[2];
            }

            public double[] bands(double timeSec) {
                double position = (timeSec - FIRST_BEAT) / (BEAT / 3);
                if (timeSec < FIRST_BEAT || Math.abs(position - Math.rint(position)) > 1e-6) {
                    return new double[3];
                }
                long third = Math.round(position);
                return third % 3 == 0 ? new double[] {3.0, 0, 0.5} : new double[] {0, 1.5, 0.8};
            }
        };
    }

    private static List<int[]> unionRows(GeneratedChartBuilder.Onsets music, double endSec, Path bms) throws Exception {
        Files.write(bms, GeneratedChartBuilder.build("t", "audio.mp3", BPM, FIRST_BEAT, endSec, music,
                new GeneratedChartBuilder.Settings(3, 1, 1, false), 1).text().getBytes(Charset.forName("MS932")));
        return rows(new BMSDecoder().decode(bms), 12);
    }

    @Test
    void tripletsAreChartedWhereTheyAreWithoutASetting() throws Exception {
        List<int[]> rows = unionRows(tripletMusic(), 20.0, directory.resolve("triplet.bms"));
        assertFalse(rows.isEmpty());
        int onTriplets = 0;
        for (int[] row : rows) {
            assertTrue(row[0] % 4 == 0, "a note off the triplet hits at twelfth " + row[0]);
            onTriplets += row[0] % 12 == 4 || row[0] % 12 == 8 ? 1 : 0;
        }
        assertTrue(onTriplets > rows.size() / 3, onTriplets + " of " + rows.size() + " on the off-beat triplets");
    }

    @Test
    void straightMusicGetsNoTriplets() throws Exception {
        Music music = varied(16);
        for (int[] row : unionRows(music, music.endSec(), directory.resolve("straight.bms"))) {
            assertTrue(row[0] % 12 != 4 && row[0] % 12 != 8, "false triplet at twelfth " + row[0]);
        }
    }

    @Test
    void aSongCanSwitchBetweenStraightAndTriplets() throws Exception {
        Music straight = varied(8);
        GeneratedChartBuilder.Onsets triplets = tripletMusic();
        double switchSec = FIRST_BEAT + 8 * 4 * BEAT;
        GeneratedChartBuilder.Onsets mixed = new GeneratedChartBuilder.Onsets() {
            public double strength(double timeSec) {
                return timeSec < switchSec ? straight.strength(timeSec) : triplets.strength(timeSec);
            }

            public double[] bands(double timeSec) {
                return timeSec < switchSec ? straight.bands(timeSec) : triplets.bands(timeSec);
            }
        };
        int before = 0;
        int after = 0;
        for (int[] row : unionRows(mixed, switchSec + 8 * 4 * BEAT, directory.resolve("mixed.bms"))) {
            boolean triplet = row[0] % 12 == 4 || row[0] % 12 == 8;
            if (triplet && row[0] < 8 * 4 * 12) {
                before++;
            } else if (triplet) {
                after++;
            }
        }
        assertEquals(0, before, "triplets in the straight half");
        assertTrue(after > 0, "no triplets in the triplet half");
    }

    @Test
    void metricalBonusFollowsTheRealInterval() {
        // 150 BPM: beat / 8th / 16th = 1 / 0.5 / 0.25, as before
        double[] fast = GeneratedChartBuilder.metricWeights(4, 16, 60.0 / 150);
        assertArrayEquals(new double[] {1.0, 0.25, 0.5, 0.25}, fast, 1e-9);
        // 79 BPM: an 8th is 380 ms (beat-like) and a 16th 190 ms (8th-like)
        double[] slow = GeneratedChartBuilder.metricWeights(4, 16, 60.0 / 79);
        assertArrayEquals(new double[] {1.0, 0.5, 1.0, 0.5}, slow, 1e-9);
        // union grid (12 per beat) at 150 BPM: beat 1, 8th 0.5, 16th and triplet 8th (133 ms) 0.25
        double[] union = GeneratedChartBuilder.metricWeights(12, 48, 60.0 / 150);
        assertEquals(1.0, union[0], 1e-9);
        assertEquals(0.25, union[3], 1e-9);
        assertEquals(0.25, union[4], 1e-9);
        assertEquals(0.5, union[6], 1e-9);
        // ... and at 79 BPM a triplet 8th (253 ms) is beat-like, a 16th (190 ms) 8th-like
        double[] slowUnion = GeneratedChartBuilder.metricWeights(12, 48, 60.0 / 79);
        assertEquals(1.0, slowUnion[4], 1e-9);
        assertEquals(0.5, slowUnion[3], 1e-9);
    }

    @Test
    void thinPassagesKeepOnlyTheirHits() throws Exception {
        // eight loud drum bars, then eight bars of quiet kicks on the beat with faint noise between
        double[][][] bars = new double[16][][];
        for (int bar = 0; bar < bars.length; bar++) {
            if (bar < 8) {
                bars[bar] = drumBar(5000 + bar);
            } else {
                double[][] thin = new double[16][3];
                for (int slot = 0; slot < 16; slot++) {
                    thin[slot][2] = 0.04;
                }
                for (int slot = 0; slot < 16; slot += 4) {
                    thin[slot][0] = 0.9;
                }
                bars[bar] = thin;
            }
        }
        for (int[] row : rows(decode(build(new Music(bars), new GeneratedChartBuilder.Settings(3, 1, 1, false), 8)))) {
            if (row[0] / 16 >= 8) {
                assertEquals(0, row[0] % 4, "a note between the kicks of the thin passage at slot " + row[0]);
            }
        }
    }

    @Test
    void noNotesInASilentBreakEvenAtTheHighestAmount() throws Exception {
        // more notes are wanted than there are hits; the extra ones must not fill the break
        double[][][] bars = new double[12][][];
        for (int bar = 0; bar < bars.length; bar++) {
            bars[bar] = bar == 5 || bar == 6 ? null : drumBar(6000 + bar);
        }
        GeneratedChartBuilder.Settings dense = new GeneratedChartBuilder.Settings(GeneratedChartBuilder.MAX_DENSITY,
                1, 1, false);
        for (int[] row : rows(decode(build(new Music(bars), dense, 9)))) {
            int bar = row[0] / 16;
            assertTrue(bar != 5 && bar != 6, "a note in the silent break at slot " + row[0]);
        }
    }

    /** Onsets at exact times (seconds → strength), seen within 20 ms like the real envelope. */
    private record Events(double[][] events) implements GeneratedChartBuilder.Onsets {
        @Override
        public double strength(double timeSec) {
            double peak = 0.0;
            for (double[] event : events) {
                if (Math.abs(event[0] - timeSec) <= 0.02) {
                    peak = Math.max(peak, event[1]);
                }
            }
            return peak;
        }

        @Override
        public double[] bands(double timeSec) {
            return new double[] {strength(timeSec), 0, 0};
        }

        @Override
        public double peakTime(double fromSec, double toSec) {
            double best = fromSec;
            double strongest = 0.0;
            for (double[] event : events) {
                if (event[0] >= fromSec && event[0] <= toSec && event[1] > strongest) {
                    strongest = event[1];
                    best = event[0];
                }
            }
            return best;
        }
    }

    /** beats and 8ths, plus hits at {@code offsets} (in beats) inside every beat */
    private static Events eventsPerBeat(int beats, double... offsets) {
        List<double[]> events = new ArrayList<>();
        for (int beat = 0; beat < beats; beat++) {
            double start = FIRST_BEAT + beat * BEAT;
            events.add(new double[] {start, 3.0});
            events.add(new double[] {start + BEAT / 2, 2.0});
            for (double offset : offsets) {
                events.add(new double[] {start + offset * BEAT, 1.5});
            }
        }
        return new Events(events.toArray(new double[0][]));
    }

    @Test
    void lateSixteenthsStaySixteenthsInsteadOfOffTriplets() throws Exception {
        // a 16th sung 22 ms late (150 BPM): nearer the 1/3 triplet than the 16th
        Events late = eventsPerBeat(64, 0.25 + 0.022 / BEAT);
        int sixteenths = 0;
        for (int[] row : unionRows(late, FIRST_BEAT + 64 * BEAT, directory.resolve("late.bms"))) {
            assertTrue(row[0] % 12 != 4 && row[0] % 12 != 8, "an off triplet at twelfth " + row[0]);
            sixteenths += row[0] % 12 == 3 ? 1 : 0;
        }
        assertTrue(sixteenths > 32, "late 16ths lost: " + sixteenths);
    }

    @Test
    void halfTempoSixteenthsAreNotSnappedToTriplets() throws Exception {
        // at half tempo the real 16ths land on 3/8 and 7/8 of the beat, between grid positions
        Events halfTempo = eventsPerBeat(64, 3.0 / 8, 7.0 / 8);
        for (int[] row : unionRows(halfTempo, FIRST_BEAT + 64 * BEAT, directory.resolve("half.bms"))) {
            assertTrue(row[0] % 12 != 4 && row[0] % 12 != 8, "a 3/8 sound snapped to a triplet at twelfth " + row[0]);
        }
    }

    @Test
    void clearTripletsStayTriplets() throws Exception {
        Events triplets = eventsPerBeat(64, 1.0 / 3, 2.0 / 3);
        int onTriplets = 0;
        for (int[] row : unionRows(triplets, FIRST_BEAT + 64 * BEAT, directory.resolve("clear.bms"))) {
            onTriplets += row[0] % 12 == 4 || row[0] % 12 == 8 ? 1 : 0;
        }
        assertTrue(onTriplets > 64, "triplets lost: " + onTriplets);
    }

    @Test
    void barsFollowADriftingTempoWithExactBpmChanges() throws Exception {
        // the music's bars grow from 1.6 s to 1.62 s; notes must land on its beats
        int bars = 12;
        double[] starts = new double[bars + 1];
        starts[0] = FIRST_BEAT;
        for (int bar = 0; bar < bars; bar++) {
            starts[bar + 1] = starts[bar] + 4 * BEAT * (1 + 0.001 * bar);
        }
        List<double[]> events = new ArrayList<>();
        for (int bar = 0; bar < bars; bar++) {
            for (int beat = 0; beat < 4; beat++) {
                events.add(new double[] {starts[bar] + beat * (starts[bar + 1] - starts[bar]) / 4, 2.0});
            }
        }
        Events music = new Events(events.toArray(new double[0][]));
        GeneratedChartBuilder.Onsets drifting = new GeneratedChartBuilder.Onsets() {
            public double strength(double timeSec) {
                return music.strength(timeSec);
            }

            public double[] bands(double timeSec) {
                return music.bands(timeSec);
            }

            public double[] barStarts(double firstBarSec, double barSec, int count) {
                assertEquals(FIRST_BEAT, firstBarSec, 1e-9);
                return starts;
            }
        };
        GeneratedChartBuilder.Chart chart = GeneratedChartBuilder.build("t", "audio.mp3", BPM, FIRST_BEAT,
                starts[bars] - 0.01, drifting, new GeneratedChartBuilder.Settings(3, 1, 1, false), 2);
        assertTrue(chart.text().contains("#BPM01 "), chart.text());
        BMSModel model = decode(chart);
        long audio = audioStartMicros(model);
        int checked = 0;
        for (TimeLine timeLine : model.getAllTimeLines()) {
            if (keyCount(timeLine) == 0) {
                continue;
            }
            double time = (timeLine.getMicroTime() - audio) / 1e6;
            double nearest = Double.MAX_VALUE;
            for (double[] event : events) {
                nearest = Math.min(nearest, Math.abs(event[0] - time));
            }
            assertTrue(nearest < 0.003, "a note " + nearest * 1000 + " ms off the drifting beats at " + time);
            checked++;
        }
        assertTrue(checked >= bars * 3, "too few notes: " + checked);
    }

    @Test
    void aMovieStartsTogetherWithTheAudio() throws Exception {
        Music music = varied(4);
        GeneratedChartBuilder.Chart chart = GeneratedChartBuilder.build("t", "audio.wav", "bga.mp4", BPM, FIRST_BEAT,
                music.endSec(), music, new GeneratedChartBuilder.Settings(3, 1, 1, false), 1);
        assertTrue(chart.text().contains("#BMP01 bga.mp4"));
        BMSModel model = decode(chart);
        assertEquals("bga.mp4", model.getBgaList()[0]);
        long audioStart = audioStartMicros(model);
        long movieStart = -1;
        for (TimeLine timeLine : model.getAllTimeLines()) {
            if (timeLine.getBGA() == 0) {
                movieStart = timeLine.getMicroTime();
                break;
            }
        }
        assertEquals(audioStart, movieStart, "movie and audio must start at the same instant");
        assertFalse(build(music, new GeneratedChartBuilder.Settings(3, 1, 1, false), 1).text().contains("#BMP"),
                "audio-only charts have no BGA");
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new GeneratedChartBuilder.Settings(false, 3, 6, true, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedChartBuilder.Settings(0, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedChartBuilder.Settings(5, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedChartBuilder.Settings(2, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedChartBuilder.Settings(2, 3, 2, false));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedChartBuilder.Settings(2, 1, 8, false));
    }

    @Test
    void generatedChartsLiveInTheWorkFolder() {
        Path chart = AudioChartSession.generatedRoot().resolve("0123456789abcdef").resolve("chart.bms");
        assertTrue(BMSIRTestPlayFolder.contains(chart.toString()));
    }

    @Test
    void recognisesAudioFiles() {
        assertTrue(AudioChartSession.isAudioFile(Path.of("song.MP3")));
        assertTrue(AudioChartSession.isAudioFile(Path.of("a/b/song.ogg")));
        assertFalse(AudioChartSession.isAudioFile(Path.of("chart.bms")));
        assertFalse(AudioChartSession.isAudioFile(Path.of("noextension")));
    }
}
