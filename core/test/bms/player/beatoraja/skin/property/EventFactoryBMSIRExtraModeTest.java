package bms.player.beatoraja.skin.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bms.player.beatoraja.PlayerConfig;
import org.junit.jupiter.api.Test;

class EventFactoryBMSIRExtraModeTest {
    @Test
    void legacySkinControlCyclesTheManiacExtraModeInBothDirections() {
        PlayerConfig config = new PlayerConfig();

        assertEquals(1, EventFactory.cycleBmsirExtraMode(config, 1));
        assertEquals(1, config.getBmsirExtraMode());
        assertEquals(0, config.getExtranoteDepth());

        assertEquals(0, EventFactory.cycleBmsirExtraMode(config, -1));
        assertEquals(3, EventFactory.cycleBmsirExtraMode(config, -1));
    }

    @Test
    void dpSkinControlCyclesNormalAndManiacDoubleOptions() {
        PlayerConfig config = new PlayerConfig();

        assertEquals(1, EventFactory.cycleBmsirDoubleOption(config, 1));
        assertEquals(1, config.getDoubleoption());
        assertFalse(config.getBmsirManiacSettings().isDoubleBattle());

        assertEquals(2, EventFactory.cycleBmsirDoubleOption(config, 1));
        assertEquals(0, config.getDoubleoption());
        assertTrue(config.getBmsirManiacSettings().isDoubleBattle());
        assertFalse(config.getBmsirManiacSettings().isAutoScratch());

        assertEquals(3, EventFactory.cycleBmsirDoubleOption(config, 1));
        assertTrue(config.getBmsirManiacSettings().isDoubleBattle());
        assertTrue(config.getBmsirManiacSettings().isAutoScratch());

        assertEquals(2, EventFactory.cycleBmsirDoubleOption(config, -1));
        assertTrue(config.getBmsirManiacSettings().isDoubleBattle());
        assertFalse(config.getBmsirManiacSettings().isAutoScratch());
    }
}
