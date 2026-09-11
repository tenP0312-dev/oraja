package bms.player.beatoraja.play;

import bms.player.beatoraja.PlayConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LaneRendererHispeedStepTest {
    @Test
    void lr2FixedStepDoesNotGrowAcrossRetries() {
        for (float margin : new float[]{1f, 0.25f}) {
            float speed = 1f;
            for (int retry = 0; retry < 5; retry++) {
                float step = LaneRenderer.hispeedStep(true, 1, speed, margin);
                assertEquals(margin, step);
                speed += step;
            }
            assertEquals(1f + 5 * margin, speed);
            assertEquals(margin, LaneRenderer.hispeedStep(true, PlayConfig.FIX_HISPEED_OFF, speed, margin));
        }
    }

    @Test
    void ordinaryFixedModeRetainsRelativeStep() {
        assertEquals(0.5f, LaneRenderer.hispeedStep(false, 1, 2f, 0.25f));
        assertEquals(0.25f, LaneRenderer.hispeedStep(false, PlayConfig.FIX_HISPEED_OFF, 2f, 0.25f));
    }
}
