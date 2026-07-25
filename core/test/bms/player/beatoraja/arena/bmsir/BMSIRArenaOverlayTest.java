package bms.player.beatoraja.arena.bmsir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSIRArenaOverlayTest {
    @Test
    void scoreRateUsesTheChartMaximumAndClampsInvalidScores() {
        assertEquals(0.0, BMSIRArenaOverlay.scoreRate(0, 100));
        assertEquals(0.5, BMSIRArenaOverlay.scoreRate(100, 100));
        assertEquals(1.0, BMSIRArenaOverlay.scoreRate(200, 100));
        assertEquals(1.0, BMSIRArenaOverlay.scoreRate(250, 100));
        assertEquals(0.0, BMSIRArenaOverlay.scoreRate(-1, 100));
        assertEquals(0.0, BMSIRArenaOverlay.scoreRate(100, 0));
    }
}
