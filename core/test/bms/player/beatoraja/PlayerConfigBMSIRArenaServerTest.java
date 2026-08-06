package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class PlayerConfigBMSIRArenaServerTest {
    @Test
    void defaultsToSecureArenaServerWhenUnset() {
        PlayerConfig config = new PlayerConfig();

        assertEquals("wss://www.bms-ir.org/new/arena/ws/client", config.getBmsirArenaServer());
    }

    @Test
    void getterUpgradesLegacyPlainWebSocketSchemeRestoredFromDisk() throws Exception {
        // libGDX's Json deserializer sets the private field directly via reflection when
        // loading an old save file, bypassing setBmsirArenaServer(). Reproduce that path here
        // so the getter's own upgrade is what's actually under test.
        PlayerConfig config = new PlayerConfig();
        setArenaServerFieldDirectly(config, "ws://www.bms-ir.org/new/arena/ws/client");

        assertEquals("wss://www.bms-ir.org/new/arena/ws/client", config.getBmsirArenaServer());
    }

    private static void setArenaServerFieldDirectly(PlayerConfig config, String value) throws Exception {
        Field field = PlayerConfig.class.getDeclaredField("bmsirArenaServer");
        field.setAccessible(true);
        field.set(config, value);
    }

    @Test
    void setterUpgradesLegacyPlainWebSocketScheme() {
        PlayerConfig config = new PlayerConfig();

        config.setBmsirArenaServer("ws://www.bms-ir.org/new/arena/ws/client");

        assertEquals("wss://www.bms-ir.org/new/arena/ws/client", config.getBmsirArenaServer());
    }

    @Test
    void setterUpgradesLegacyPlainHttpScheme() {
        PlayerConfig config = new PlayerConfig();

        config.setBmsirArenaServer("http://www.bms-ir.org/new/arena/ws/client");

        assertEquals("https://www.bms-ir.org/new/arena/ws/client", config.getBmsirArenaServer());
    }

    @Test
    void leavesAlreadySecureSchemeUnchanged() {
        PlayerConfig config = new PlayerConfig();

        config.setBmsirArenaServer("wss://example.invalid/new/arena/ws/client");

        assertEquals("wss://example.invalid/new/arena/ws/client", config.getBmsirArenaServer());
    }

    @Test
    void blankValuePassedToSetterFallsBackToDefault() {
        PlayerConfig config = new PlayerConfig();

        config.setBmsirArenaServer("   ");

        assertEquals("wss://www.bms-ir.org/new/arena/ws/client", config.getBmsirArenaServer());
    }

    private static PlayerConfig makeConfigWithArenaServer(String value) {
        PlayerConfig config = new PlayerConfig();
        config.setBmsirArenaServer(value);
        return config;
    }
}
