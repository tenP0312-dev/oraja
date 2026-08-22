package bms.player.beatoraja.play;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSIRHispeedTest {
    @Test
    void appliesBaseScrollAndTheSelectedReferenceBpm() {
        assertEquals(2.5f, BMSIRHispeed.appliedHispeed(
                2.0f, 125, 150, false, 300.0
        ));
        assertEquals(1.25f, BMSIRHispeed.appliedHispeed(
                2.0f, 125, 150, true, 300.0
        ));
        assertEquals(1.5f, BMSIRHispeed.appliedHispeed(
                2.0f, 125, 180, true, 300.0
        ));
    }

    @Test
    void keepsEffectiveSpeedAboveTheStoredHispeedLimit() {
        assertEquals(200.0f, BMSIRHispeed.appliedHispeed(
                10.0f, 2000, 150, false, 150.0
        ));
    }

    @Test
    void startHereAndPlayShareTheLiftAwareGreenNumber() {
        assertEquals(480, BMSIRHispeed.currentDuration(
                2.0f, 150.0, 1.0, true, 0.5f, true, 0.2f
        ));
        assertEquals(0.4, BMSIRHispeed.effectiveLaneCover(
                true, 0.5f, true, 0.2f
        ), 0.0001);
    }

    @Test
    void clampsReferenceBpmToTheSupportedRange() {
        assertEquals(50, BMSIRHispeed.clampReferenceBpm(0));
        assertEquals(150, BMSIRHispeed.clampReferenceBpm(150));
        assertEquals(400, BMSIRHispeed.clampReferenceBpm(999));
    }
}
