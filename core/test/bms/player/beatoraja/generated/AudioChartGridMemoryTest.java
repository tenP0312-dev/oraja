package bms.player.beatoraja.generated;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudioChartGridMemoryTest {
    @TempDir
    Path directory;

    private static AudioGridEstimator.Result result(double bpm, double firstBeat) {
        return new AudioGridEstimator.Result(bpm, firstBeat, 0.0, 200.0, 199.0, 10.0, 0.0, true,
                List.of(new AudioGridEstimator.Candidate(bpm / 2, 11.0)), new double[10], new double[3][10]);
    }

    @Test
    void playerCorrectionIsReusedForTheSameAnalysis() {
        AudioGridEstimator.Result analysed = result(173.0, 0.5145);
        assertNull(AudioChartSession.loadGrid(directory, analysed), "nothing saved yet");
        AudioChartSession.saveGrid(directory, 86.5, 0.5145, analysed);
        assertArrayEquals(new double[] {86.5, 0.5145}, AudioChartSession.loadGrid(directory, analysed), 1e-9);
        // a different analysis (e.g. after an algorithm change) ignores the old correction
        assertNull(AudioChartSession.loadGrid(directory, result(172.0, 0.5145)));
    }

    @Test
    void damagedCorrectionIsIgnored() throws Exception {
        Files.writeString(directory.resolve("grid.properties"), "bpm=abc\nanalysedBpm=173.0\n");
        assertNull(AudioChartSession.loadGrid(directory, result(173.0, 0.5)));
    }

    @Test
    void octaveAlternativeIsOfferedOnlyWhenClose() {
        AudioGridEstimator.Result close = result(173.0, 0.5);
        assertEquals(86.5, close.octaveAlternative().bpm(), 1e-9);
        AudioGridEstimator.Result far = new AudioGridEstimator.Result(173.0, 0.5, 0.0, 200.0, 199.0, 10.0, 0.0, true,
                List.of(new AudioGridEstimator.Candidate(86.5, 5.0), new AudioGridEstimator.Candidate(115.3, 9.9)),
                new double[10], new double[3][10]);
        assertNull(far.octaveAlternative(), "a weak octave or a non-octave candidate is not offered");
    }
}
