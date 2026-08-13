package bms.player.beatoraja.select;

import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreviewMusicProcessorTest {
    @Test
    void missingSongPathSkipsPreview() {
        SongData song = new SongData();
        song.setPreview("preview.ogg");

        assertEquals("", PreviewMusicProcessor.resolvePreviewPath(song));
    }

    @Test
    void localSongPathResolvesPreviewBesideTheChart() {
        SongData song = new SongData();
        song.setPath(Paths.get("songs", "test", "chart.bms").toString());
        song.setPreview("preview.ogg");

        assertEquals(
                Paths.get("songs", "test", "preview.ogg").toString(),
                PreviewMusicProcessor.resolvePreviewPath(song)
        );
    }

    @Test
    void archiveSongPathResolvesPreviewInsideTheArchive() {
        SongData song = new SongData();
        song.setPath(Paths.get("songs", "pack.zip!-Pack", "chart.bms").toString());
        song.setPreview("preview.ogg");

        assertEquals(
                Paths.get("songs", "pack.zip!-Pack", "preview.ogg").toAbsolutePath().toString(),
                PreviewMusicProcessor.resolvePreviewPath(song)
        );
    }
}
