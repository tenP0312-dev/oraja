package bms.player.beatoraja.bmsir;

import bms.model.BMSModel;
import bms.model.ChartInformation;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRTestPlayFolderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void matchesAnExactDirectoryComponentAcrossSupportedPathShapes() {
        assertTrue(BMSIRTestPlayFolder.contains(
                "/songs/_BMSIR_TESTPLAY/work/chart.bms"));
        assertTrue(BMSIRTestPlayFolder.contains(
                "C:\\BMS\\_bmsir_testplay\\work\\chart.bme"));
        assertTrue(BMSIRTestPlayFolder.contains(
                "/songs/pack.zip!/_BMSIR_TESTPLAY/chart.bmson"));

        assertFalse(BMSIRTestPlayFolder.contains(
                "/songs/_BMSIR_TESTPLAYBACK/chart.bms"));
        assertFalse(BMSIRTestPlayFolder.contains(
                "/songs/work/_BMSIR_TESTPLAY.bms"));
        assertFalse(BMSIRTestPlayFolder.contains((String) null));
    }

    @Test
    void recognizesSongAndModelPathsAndAnyTestChartInACourse() {
        SongData song = new SongData();
        song.setPath("/songs/_BMSIR_TESTPLAY/chart.bms");

        BMSModel normal = model("/songs/released/chart.bms");
        BMSModel test = model("/songs/_BMSIR_TESTPLAY/chart.bms");

        assertTrue(BMSIRTestPlayFolder.contains(song));
        assertFalse(BMSIRTestPlayFolder.contains(normal));
        assertTrue(BMSIRTestPlayFolder.contains(test));
        assertTrue(BMSIRTestPlayFolder.containsAny(new BMSModel[]{normal, test}));
        assertFalse(BMSIRTestPlayFolder.containsAny(new BMSModel[]{normal}));
    }

    @Test
    void createsOneIdempotentChildOnlyBelowAnExistingRoot() throws Exception {
        Path created = BMSIRTestPlayFolder.createUnder(temporaryDirectory);

        assertEquals(
                temporaryDirectory.resolve(BMSIRTestPlayFolder.DIRECTORY_NAME),
                created
        );
        assertTrue(Files.isDirectory(created));
        assertEquals(created, BMSIRTestPlayFolder.createUnder(temporaryDirectory));

        Path missing = temporaryDirectory.resolve("missing");
        assertThrows(IOException.class, () -> BMSIRTestPlayFolder.createUnder(missing));
        assertFalse(Files.exists(missing));
    }

    private static BMSModel model(String path) {
        BMSModel model = new BMSModel();
        model.setChartInformation(new ChartInformation(Path.of(path), 0, null));
        return model;
    }
}
