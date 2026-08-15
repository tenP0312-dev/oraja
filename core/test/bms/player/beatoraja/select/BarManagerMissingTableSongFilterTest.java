package bms.player.beatoraja.select;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarManagerMissingTableSongFilterTest {
    @Test
    void sharedTableSettingOverridesHttpForcedVisibilityOnlyInsideTables() {
        assertTrue(BarManager.shouldHideUnavailableBars(true, true, true));
        assertFalse(BarManager.shouldHideUnavailableBars(true, false, true));
    }

    @Test
    void disabledTableSettingKeepsLegacyVisibility() {
        assertFalse(BarManager.shouldHideUnavailableBars(true, true, false));
    }

    @Test
    void legacyGlobalHideStillAppliesOutsideTables() {
        assertTrue(BarManager.shouldHideUnavailableBars(false, false, false));
    }
}
