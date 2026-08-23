package bms.player.beatoraja.external;

import bms.player.beatoraja.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenShotFileExporterTest {
    @Test
    void configuredFormatControlsTheSavedExtension() {
        assertEquals(".png", ScreenShotFileExporter.extensionFor(Config.ScreenShotFormat.PNG));
        assertEquals(".jpg", ScreenShotFileExporter.extensionFor(Config.ScreenShotFormat.JPG));
        assertEquals(".png", ScreenShotFileExporter.extensionFor(null));
    }
}
