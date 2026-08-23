package bms.player.beatoraja.audio;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;
import bms.player.beatoraja.song.archive.SongArchives;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALSound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GdxSoundDriverDurationTest {

    @TempDir
    Path temporary;

    @Test
    void exposesDecodedOpenAlDurationThroughTheAudioDriver() throws Exception {
        Path preview = temporary.resolve("preview.ogg");
        Files.write(preview, new byte[]{1});
        SongResource resource = SongResources.fromPath(preview);
        DurationGdxSoundDriver driver = new DurationGdxSoundDriver(1.25f);

        assertEquals(1_250L, driver.getDurationMillis(resource));
    }

    @Test
    void exposesDecodedOpenAlDurationForAnArchiveResource() throws Exception {
        Path archive = temporary.resolve("pack.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("Pack/preview.ogg"));
            output.write(new byte[]{1});
            output.closeEntry();
        }
        SongResource resource = SongResources.fromPath(
                SongArchives.virtualPath(archive, "Pack/preview.ogg"));
        DurationGdxSoundDriver driver = new DurationGdxSoundDriver(0.75f);

        assertEquals(750L, driver.getDurationMillis(resource));
    }

    @Test
    void rejectsMissingOrInvalidOpenAlDurations() {
        assertEquals(-1L, GdxSoundDriver.openAlDurationMillis(null));
        assertEquals(-1L, GdxSoundDriver.openAlDurationMillis(new DurationSound(0.0f)));
        assertEquals(-1L, GdxSoundDriver.openAlDurationMillis(new DurationSound(Float.NaN)));
    }

    private static final class DurationGdxSoundDriver extends GdxSoundDriver {
        private final Sound sound;

        private DurationGdxSoundDriver(float durationSeconds) {
            super(new Config());
            sound = new DurationSound(durationSeconds);
        }

        @Override
        protected Sound getKeySound(SongResource resource) {
            return sound;
        }
    }

    private static final class DurationSound extends OpenALSound {
        private final float durationSeconds;

        private DurationSound(float durationSeconds) {
            super(null);
            this.durationSeconds = durationSeconds;
        }

        @Override
        public float duration() {
            return durationSeconds;
        }
    }
}
