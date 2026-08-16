package bms.player.beatoraja.result;

import bms.model.BMSModel;
import bms.model.ChartInformation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRTestPlayResultPolicyTest {
    @Test
    void singleResultPersistsOnlyOutsideTheReservedFolder() {
        assertTrue(MusicResult.shouldPersistScore(
                model("/songs/released/chart.bms")));
        assertFalse(MusicResult.shouldPersistScore(
                model("/songs/_BMSIR_TESTPLAY/work/chart.bms")));
    }

    @Test
    void courseResultIsDisposableWhenAnyChartIsInTheReservedFolder() {
        BMSModel normal = model("/songs/released/chart.bms");
        BMSModel test = model("/songs/_BMSIR_TESTPLAY/work/chart.bms");

        assertTrue(CourseResult.shouldPersistScore(new BMSModel[]{normal}));
        assertFalse(CourseResult.shouldPersistScore(new BMSModel[]{normal, test}));
    }

    private static BMSModel model(String path) {
        BMSModel model = new BMSModel();
        model.setChartInformation(new ChartInformation(Path.of(path), 0, null));
        return model;
    }
}
