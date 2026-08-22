package bms.player.beatoraja.play;

import bms.player.beatoraja.PlayConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSIRHispeedTest {
    @Test
    void appliesBaseScrollAndTheFixed150BpmReference() {
        assertEquals(2.5f, BMSIRHispeed.appliedHispeed(
                2.0f, 125, false, 300.0
        ));
        assertEquals(1.25f, BMSIRHispeed.appliedHispeed(
                2.0f, 125, true, 300.0
        ));
    }

    @Test
    void keepsEffectiveSpeedAboveTheStoredHispeedLimit() {
        assertEquals(200.0f, BMSIRHispeed.appliedHispeed(
                10.0f, 2000, false, 150.0
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
    void greenNumberRoundTripsThroughBaseScroll() {
        PlayConfig config = new PlayConfig();
        config.setHispeed(2.0f);
        config.setBmsirBaseScrollSpeed(100);
        config.setEnablelanecover(true);
        config.setLanecover(0.5f);
        config.setEnablelift(true);
        config.setLift(0.2f);

        int green = BMSIRHispeed.equivalentGreen(config);

        assertEquals(480, green);
        assertEquals(100, BMSIRHispeed.baseScrollSpeedForGreen(config, green));
    }

    @Test
    void pseudoFhsInverseKeepsTheLockedGreenWithLiftAndCover() {
        float hispeed = BMSIRHispeed.hispeedForDuration(
                480, 150.0, 1.0, true, 0.5f, true, 0.2f
        );

        assertEquals(2.0f, hispeed, 0.0001f);
        assertEquals(480, BMSIRHispeed.currentDuration(
                hispeed, 150.0, 1.0, true, 0.5f, true, 0.2f
        ));
    }
}
