package bms.player.beatoraja.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IRUtilTest {
    @Test
    void rankingPlayerWinsAndTheOfflineFallbackRemainsCompatible() {
        assertEquals("RIVAL", IRUtil.playerName(null, "RIVAL"));
        assertEquals("YOU", IRUtil.playerName(null, ""));
    }
}
