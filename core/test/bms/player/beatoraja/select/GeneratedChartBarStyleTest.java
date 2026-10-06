package bms.player.beatoraja.select;

import bms.player.beatoraja.select.bar.FunctionBar;
import bms.player.beatoraja.select.bar.GeneratedAudioChartBar;
import bms.player.beatoraja.select.bar.GeneratedAudioFolderBar;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedChartBarStyleTest {
    @Test
    void textStatusDoesNotAssumeSongOrFolderBarClasses() {
        // 0.4.14.97 crashed here: the status step cast every song-style bar to SongBar
        // and every folder-style bar to FolderBar
        GeneratedAudioFolderBar folder = new GeneratedAudioFolderBar(null, Path.of("audio"), "CHARTS FROM AUDIO");
        GeneratedAudioChartBar file = new GeneratedAudioChartBar(null, Path.of("audio", "song.mp3"));
        long now = System.currentTimeMillis() / 1000;
        assertEquals(4, BarRenderer.barTextStatus(folder, BarRenderer.generatedChartBarStyle(folder), status -> true, now));
        assertEquals(2, BarRenderer.barTextStatus(file, BarRenderer.generatedChartBarStyle(file), status -> true, now));
        assertEquals(0, BarRenderer.barTextStatus(folder, 1, status -> false, now));
        assertEquals(0, BarRenderer.barTextStatus(file, 0, status -> false, now));
        assertEquals(9, BarRenderer.barTextStatus(file, 5, status -> true, now));
        assertEquals(0, BarRenderer.barTextStatus(file, 5, status -> false, now));
    }

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
