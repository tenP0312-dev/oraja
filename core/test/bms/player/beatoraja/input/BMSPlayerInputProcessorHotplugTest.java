package bms.player.beatoraja.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BMSPlayerInputProcessorHotplugTest {
    @Test
    void reconnectUsesFirstAvailableStableSuffix() {
        assertEquals(
                "IIDX Controller-3",
                BMSPlayerInputProcessor.uniqueControllerName(
                        "IIDX Controller",
                        new String[] {"IIDX Controller", "IIDX Controller-2", "Other"}
                )
        );
    }

    @Test
    void unnamedControllerStillGetsStableIdentity() {
        assertEquals(
                "Controller-2",
                BMSPlayerInputProcessor.uniqueControllerName(
                        "",
                        new String[] {"Controller"}
                )
        );
    }
}
