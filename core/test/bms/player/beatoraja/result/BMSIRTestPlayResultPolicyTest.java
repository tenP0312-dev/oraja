package bms.player.beatoraja.result;

import bms.model.BMSModel;
import bms.model.ChartInformation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRTestPlayResultPolicyTest {
    @Test
    void singleResultPersistsOnlyOutsideTheConfiguredWorkFolder() {
        assertTrue(MusicResult.shouldPersistScore(
                model("/songs/released/chart.bms"), "/songs/authoring"));
        assertFalse(MusicResult.shouldPersistScore(
                model("/songs/authoring/work/chart.bms"), "/songs/authoring"));
    }

    @Test
    void courseResultIsDisposableWhenAnyChartIsInTheReservedFolder() {
        BMSModel normal = model("/songs/released/chart.bms");
        BMSModel test = model("/songs/authoring/work/chart.bms");

        assertTrue(CourseResult.shouldPersistScore(
                new BMSModel[]{normal}, "/songs/authoring"));
        assertFalse(CourseResult.shouldPersistScore(
                new BMSModel[]{normal, test}, "/songs/authoring"));
    }

    private static BMSModel model(String path) {
        BMSModel model = new BMSModel();
        model.setChartInformation(new ChartInformation(Path.of(path), 0, null));
        return model;
    }
}
