package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerConfigEndPlayAtLastNoteTest {
    @Test
    void defaultsToOffIncludingConfigsWrittenBeforeTheOption() {
        assertFalse(new PlayerConfig().isEndPlayAtLastNote());
        assertFalse(new Json().fromJson(PlayerConfig.class, "{}").isEndPlayAtLastNote());
    }

    @Test
    void roundTripsThroughPlayerConfigJson() {
        PlayerConfig config = new PlayerConfig();
        config.setEndPlayAtLastNote(true);

        PlayerConfig restored = new Json().fromJson(PlayerConfig.class, PlayerConfig.getConfigJson(config));

        assertTrue(restored.isEndPlayAtLastNote());
    }

    @Test
    void survivesWriteAndReadOfThePlayerProfile(@TempDir Path root) throws Exception {
        PlayerConfig config = new PlayerConfig();
        config.setId("endtest");
        config.setEndPlayAtLastNote(true);
        Files.createDirectories(root.resolve("endtest"));

        PlayerConfig.write(root.toString(), config);
        PlayerConfig restored = PlayerConfig.readPlayerConfig(root.toString(), "endtest");

        assertTrue(restored.isEndPlayAtLastNote());
    }
}
