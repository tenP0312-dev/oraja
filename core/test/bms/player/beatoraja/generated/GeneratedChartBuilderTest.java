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
import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedChartBuilderTest {
    @TempDir
    Path directory;

    private static final double BPM = 150.0;
    private static final double BEAT = 60.0 / BPM;
    /** Strong on beats, weak on off-beats, with a loud stretch from 30 s. */
    private static final DoubleUnaryOperator STRENGTH = time -> {
        double position = time / BEAT;
        double onBeat = Math.abs(position - Math.rint(position)) < 0.1 ? 3.0 : 1.0;
        return onBeat * (time > 30.0 ? 2.0 : 1.0);
    };

    private BMSModel decode(GeneratedChartBuilder.Chart chart) throws Exception {
        Path bms = directory.resolve("chart.bms");
        Files.write(bms, chart.text().getBytes(Charset.forName("MS932")));
        BMSModel model = new BMSDecoder().decode(bms);
        assertNotNull(model);
        return model;
    }

    private static GeneratedChartBuilder.Chart build(GeneratedChartBuilder.Settings settings, long seed, double firstBeat) {
        return GeneratedChartBuilder.build("曲名 test", "audio.mp3", BPM, firstBeat, 60.0, STRENGTH, settings, seed);
    }

    /** Key lanes 0-6 per time line that has key notes, in time order. */
    private static List<List<Integer>> keyRows(BMSModel model) {
        List<List<Integer>> rows = new ArrayList<>();
        for (TimeLine timeLine : model.getAllTimeLines()) {
            List<Integer> lanes = new ArrayList<>();
            for (int lane = 0; lane < 7; lane++) {
                if (timeLine.getNote(lane) != null) {
                    lanes.add(lane);
                }
            }
            if (!lanes.isEmpty()) {
                rows.add(lanes);
            }
        }
        return rows;
    }

    @Test
    void decodesAsSevenKeyChartAlignedToTheAudio() throws Exception {
        double firstBeat = 1.234;
        GeneratedChartBuilder.Chart chart = build(new GeneratedChartBuilder.Settings(8, 1, 3, false), 1, firstBeat);
        BMSModel model = decode(chart);
        assertEquals(Mode.BEAT_7K, model.getMode());
        assertEquals(BPM, model.getBpm(), 1e-6);
        assertEquals(chart.notes(), model.getTotalNotes());

        long audioStart = audioStartMicros(model);
        long firstNote = firstKeyMicros(model);
        long lastNote = -1;
        for (TimeLine timeLine : model.getAllTimeLines()) {
            if (keyCount(timeLine) > 0) {
                lastNote = timeLine.getMicroTime();
            }
        }
        // one empty lead-in measure, then notes start on the given beat of the audio
        assertEquals(4 * BEAT * 1e6, audioStart, 2);
        assertEquals(firstBeat * 1e6, firstNote - audioStart, 2);
        assertTrue((lastNote - audioStart) / 1e6 <= 60.0);
        // 8th notes from 1.234 s through 60 s
        assertEquals((int) Math.floor((60.0 - firstBeat) / (BEAT / 2)) + 1, chart.positions());
    }

    @Test
    void earlyFirstBeatGetsALeadOfAtLeastAQuarterBeat() throws Exception {
        BMSModel model = decode(build(new GeneratedChartBuilder.Settings(4, 1, 1, false), 1, 0.01));
        assertEquals((0.01 + BEAT) * 1e6, firstKeyMicros(model) - audioStartMicros(model), 2);
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

    private static long firstKeyMicros(BMSModel model) {
        for (TimeLine timeLine : model.getAllTimeLines()) {
            if (keyCount(timeLine) > 0) {
                return timeLine.getMicroTime();
            }
        }
        return -1;
    }

    private static int keyCount(TimeLine timeLine) {
        int count = 0;
        for (int lane = 0; lane < 7; lane++) {
            if (timeLine.getNote(lane) != null) {
                count++;
            }
        }
        return count;
    }

    @Test
    void chordSizesStayInRangeAndFollowStrength() throws Exception {
        BMSModel model = decode(build(new GeneratedChartBuilder.Settings(8, 2, 4, false), 3, 0.5));
        List<List<Integer>> rows = keyRows(model);
        int[] histogram = new int[8];
        for (List<Integer> row : rows) {
            assertTrue(row.size() >= 2 && row.size() <= 4, "chord " + row);
            histogram[row.size()]++;
        }
        // each size is roughly half as frequent as the one below
        assertTrue(histogram[2] > histogram[3] && histogram[3] > histogram[4], java.util.Arrays.toString(histogram));
        assertTrue(histogram[4] > 0);

        // stronger (on-beat, loud) positions carry the large chords
        int[] sizes = GeneratedChartBuilder.chordSizes(BEAT, BEAT / 2, 100, STRENGTH,
                new GeneratedChartBuilder.Settings(8, 1, 3, false));
        double onBeatMean = 0;
        double offBeatMean = 0;
        for (int position = 0; position < sizes.length; position++) {
            if (position % 2 == 0) {
                onBeatMean += sizes[position];
            } else {
                offBeatMean += sizes[position];
            }
        }
        assertTrue(onBeatMean > offBeatMean, onBeatMean + " vs " + offBeatMean);
    }

    @Test
    void equalMinAndMaxGivesAConstantChord() throws Exception {
        for (List<Integer> row : keyRows(decode(build(new GeneratedChartBuilder.Settings(16, 3, 3, false), 4, 0.5)))) {
            assertEquals(3, row.size());
        }
    }

    @Test
    void lanesAvoidRepeatsWhenThereIsRoom() throws Exception {
        List<List<Integer>> rows = keyRows(decode(build(new GeneratedChartBuilder.Settings(16, 1, 3, false), 5, 0.5)));
        for (int index = 1; index < rows.size(); index++) {
            for (int lane : rows.get(index)) {
                assertFalse(rows.get(index - 1).contains(lane), "jack at row " + index);
            }
        }
        // with 4 of 7 lanes taken every time, exactly one lane (4 + 4 - 7) must repeat
        int[][] lanes = GeneratedChartBuilder.assignLanes(new int[] {4, 4, 4, 4, 4, 4}, new java.util.Random(1));
        for (int index = 1; index < lanes.length; index++) {
            int repeats = 0;
            for (int lane : lanes[index]) {
                for (int previous : lanes[index - 1]) {
                    repeats += lane == previous ? 1 : 0;
                }
            }
            assertEquals(1, repeats);
        }
    }

    @Test
    void sameSeedIsDeterministicAndReshuffleChangesLanes() {
        GeneratedChartBuilder.Settings settings = new GeneratedChartBuilder.Settings(8, 1, 2, false);
        assertEquals(build(settings, 9, 0.5).text(), build(settings, 9, 0.5).text());
        assertNotEquals(build(settings, 9, 0.5).text(), build(settings, 10, 0.5).text());
    }

    @Test
    void scratchOnlyOnStrongHitsAndNeverTwiceInARow() throws Exception {
        BMSModel model = decode(build(new GeneratedChartBuilder.Settings(8, 1, 1, true), 6, 0.5));
        int scratches = 0;
        boolean previous = false;
        int rows = 0;
        for (TimeLine timeLine : model.getAllTimeLines()) {
            if (keyCount(timeLine) == 0) {
                continue;
            }
            rows++;
            boolean scratch = timeLine.getNote(7) != null;
            assertFalse(scratch && previous, "consecutive scratch");
            scratches += scratch ? 1 : 0;
            previous = scratch;
        }
        assertTrue(scratches > 0 && scratches <= rows / 10 + 1, scratches + " of " + rows);
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratedChartBuilder.Settings(12, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedChartBuilder.Settings(8, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedChartBuilder.Settings(8, 3, 2, false));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedChartBuilder.Settings(8, 1, 8, false));
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
