package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

class ConfigTimingDiagnosticsTest {
    @Test
    void timingDiagnosticsDefaultOffAndRoundTripThroughSystemConfig() {
        Config defaults = new Config();
        assertFalse(defaults.isTimingDiagnostics());

        defaults.setTimingDiagnostics(true);
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        Config restored = json.fromJson(Config.class, Config.getConfigJson(defaults));

        assertTrue(restored.isTimingDiagnostics());
    }
}
