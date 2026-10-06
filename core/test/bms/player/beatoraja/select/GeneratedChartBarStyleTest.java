package bms.player.beatoraja.select;

import bms.player.beatoraja.select.bar.FunctionBar;
import bms.player.beatoraja.select.bar.GeneratedAudioChartBar;
import bms.player.beatoraja.select.bar.GeneratedAudioFolderBar;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedChartBarStyleTest {
    @Test
    void generatedChartFoldersAndFilesAreDrawn() {
        // -1 means "not drawn": the operator saw blank rows before folders/files had a style
        assertEquals(1, BarRenderer.generatedChartBarStyle(
                new GeneratedAudioFolderBar(null, Path.of("audio"), "CHARTS FROM AUDIO")));
        assertEquals(0, BarRenderer.generatedChartBarStyle(
                new GeneratedAudioChartBar(null, Path.of("audio", "song.mp3"))));
        assertEquals(-1, BarRenderer.generatedChartBarStyle(
                new FunctionBar((selector, bar) -> { }, "other", FunctionBar.STYLE_SONG)));
    }
}
