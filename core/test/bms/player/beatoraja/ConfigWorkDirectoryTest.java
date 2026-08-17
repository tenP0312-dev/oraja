package bms.player.beatoraja;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigWorkDirectoryTest {
    @Test
    void defaultsEmptyAndRoundTripsTheSelectedBmsRoot() {
        Config config = new Config();
        assertEquals("", config.getWorkDirectory());

        config.setWorkDirectory("songs/authoring");

        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        Config restored = json.fromJson(Config.class, Config.getConfigJson(config));
        assertEquals("songs/authoring", restored.getWorkDirectory());
    }

    @Test
    void validatesAnExplicitNullFromAnOlderOrEditedConfig() {
        Config config = new Config();
        config.setWorkDirectory(null);

        config.validate();

        assertEquals("", config.getWorkDirectory());
    }
}
