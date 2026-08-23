package bms.player.beatoraja.result;

import bms.model.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class MusicResultInputModeTest {
    @Test
    void fiveAndTenKeyResultsUseTheWiderInputLayout() {
        assertSame(Mode.BEAT_7K, MusicResult.compatibleResultInputMode(Mode.BEAT_5K));
        assertSame(Mode.BEAT_14K, MusicResult.compatibleResultInputMode(Mode.BEAT_10K));
    }

    @Test
    void otherResultModesKeepTheirOwnInputLayout() {
        assertSame(Mode.BEAT_7K, MusicResult.compatibleResultInputMode(Mode.BEAT_7K));
        assertSame(Mode.POPN_9K, MusicResult.compatibleResultInputMode(Mode.POPN_9K));
    }
}
