package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerConfigBMSIRExtraModeTest {
    @Test
    void legacyExtraNoteDepthIsDisabledWithoutOverwritingManiacExtraMode() {
        PlayerConfig config = new PlayerConfig();
        config.setBmsirExtraMode(2);
        config.setExtranoteDepth(3);

        config.validate();

        assertEquals(0, config.getExtranoteDepth());
        assertEquals(2, config.getBmsirExtraMode());
    }

    @Test
    void compositeDoubleOptionUsesManiacSettingsForBattleValues() {
        PlayerConfig config = new PlayerConfig();

        config.setBmsirDoubleOption(1);
        assertEquals(1, config.getBmsirDoubleOption());
        assertEquals(1, config.getDoubleoption());
        assertFalse(config.getBmsirManiacSettings().isDoubleBattle());

        config.setBmsirDoubleOption(2);
        assertEquals(2, config.getBmsirDoubleOption());
        assertEquals(0, config.getDoubleoption());
        assertTrue(config.getBmsirManiacSettings().isDoubleBattle());
        assertFalse(config.getBmsirManiacSettings().isAutoScratch());

        config.setBmsirDoubleOption(3);
        assertEquals(3, config.getBmsirDoubleOption());
        assertTrue(config.getBmsirManiacSettings().isAutoScratch());

        config.setBmsirDoubleOption(0);
        assertEquals(0, config.getBmsirDoubleOption());
        assertFalse(config.getBmsirManiacSettings().isDoubleBattle());
    }
}
