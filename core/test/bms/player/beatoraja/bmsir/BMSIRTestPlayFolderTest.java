package bms.player.beatoraja.bmsir;

import bms.model.BMSModel;
import bms.model.ChartInformation;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRTestPlayFolderTest {
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
    void matchesTheConfiguredWorkRootAndDescendantsWithoutPrefixCollisions() {
        String workDirectory = "/songs/authoring";

        assertTrue(BMSIRTestPlayFolder.contains(
                "/songs/authoring/chart.bms", workDirectory));
        assertTrue(BMSIRTestPlayFolder.contains(
                "/songs/authoring/nested/chart.bmson", workDirectory));
        assertTrue(BMSIRTestPlayFolder.contains(
                "/songs/authoring/pack.zip!/chart.bms", workDirectory));
        assertFalse(BMSIRTestPlayFolder.contains(
                "/songs/authoring-old/chart.bms", workDirectory));
        assertFalse(BMSIRTestPlayFolder.contains(
                "/songs/released/chart.bms", workDirectory));
        assertFalse(BMSIRTestPlayFolder.contains(
                "/songs/authoring/chart.bms", ""));
    }

    @Test
    void recognizesSongAndModelPathsAndAnyConfiguredWorkChartInACourse() {
        String workDirectory = "/songs/authoring";
        SongData song = new SongData();
        song.setPath("/songs/authoring/chart.bms");

        BMSModel normal = model("/songs/released/chart.bms");
        BMSModel test = model("/songs/authoring/chart.bms");

        assertTrue(BMSIRTestPlayFolder.contains(song, workDirectory));
        assertFalse(BMSIRTestPlayFolder.contains(normal, workDirectory));
        assertTrue(BMSIRTestPlayFolder.contains(test, workDirectory));
        assertTrue(BMSIRTestPlayFolder.containsAny(
                new BMSModel[]{normal, test}, workDirectory));
        assertFalse(BMSIRTestPlayFolder.containsAny(
                new BMSModel[]{normal}, workDirectory));
    }

    private static BMSModel model(String path) {
        BMSModel model = new BMSModel();
        model.setChartInformation(new ChartInformation(Path.of(path), 0, null));
        return model;
    }
}
