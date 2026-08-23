package bms.player.beatoraja.modmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkinWidgetManagerTest {
    @Test
    void exportsWidthOnlyChange() {
        assertEquals(
                "{dst=lane, w=320.0}",
                SkinWidgetManager.formatExportChange(
                        "lane", 10.0f, 20.0f, 320.0f, 480.0f,
                        false, false, true, false
                )
        );
    }

    @Test
    void exportsHeightOnlyChange() {
        assertEquals(
                "{dst=lane, h=480.0}",
                SkinWidgetManager.formatExportChange(
                        "lane", 10.0f, 20.0f, 320.0f, 480.0f,
                        false, false, false, true
                )
        );
    }

    @Test
    void exportsEachChangedFieldOnce() {
        assertEquals(
                "{dst=lane, x=10.0, y=20.0, w=320.0, h=480.0}",
                SkinWidgetManager.formatExportChange(
                        "lane", 10.0f, 20.0f, 320.0f, 480.0f,
                        true, true, true, true
                )
        );
    }
}
