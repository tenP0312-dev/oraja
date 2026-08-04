package bms.player.beatoraja.play;

import bms.player.beatoraja.PlayerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSPlayerDoubleOptionTest {
    @Test
    void legacyBattleOptionsAreDisabled() {
        assertEquals(0, BMSPlayer.normalDoubleOption(2));
        assertEquals(0, BMSPlayer.normalDoubleOption(3));
        assertEquals(0, BMSPlayer.normalDoubleOption(-1));
    }

    @Test
    void offAndFlipRemainAvailable() {
        assertEquals(0, BMSPlayer.normalDoubleOption(0));
        assertEquals(1, BMSPlayer.normalDoubleOption(1));
    }

    @Test
    void playerConfigNormalizesLegacyBattleOptionsToOff() {
        PlayerConfig config = new PlayerConfig();

        config.setDoubleoption(2);
        assertEquals(0, config.getDoubleoption());
        config.setDoubleoption(3);
        assertEquals(0, config.getDoubleoption());
    }
}
