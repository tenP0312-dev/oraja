package bms.player.beatoraja.launcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bms.player.beatoraja.PlayModeConfig.ControllerConfig;
import org.junit.jupiter.api.Test;

class ControllerConfigViewModelTest {
    @Test
    void jkocStateIsIndependentForEachControllerRow() {
        ControllerConfig first = new ControllerConfig();
        first.setJKOC(true);
        ControllerConfig second = new ControllerConfig();
        second.setJKOC(false);

        ControllerConfigViewModel firstRow = new ControllerConfigViewModel(first);
        ControllerConfigViewModel secondRow = new ControllerConfigViewModel(second);

        assertTrue(firstRow.isJkoc());
        assertFalse(secondRow.isJkoc());
        secondRow.jkocProperty().set(true);
        assertTrue(secondRow.isJkoc());
        assertTrue(firstRow.isJkoc());
    }
}
