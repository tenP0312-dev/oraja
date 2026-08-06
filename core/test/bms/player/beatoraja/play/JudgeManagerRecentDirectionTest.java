package bms.player.beatoraja.play;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeManagerRecentDirectionTest {
    @Test
    void recentDirectionIsSideSpecificAndExpires() {
        int[] directions = {-1, 1, 0};
        long base = TimeUnit.SECONDS.toNanos(10);
        long[] timestamps = {base, base, 0};

        assertTrue(JudgeManager.isRecentDirection(
                1, 500, -1, directions, timestamps,
                base + TimeUnit.MILLISECONDS.toNanos(499)
        ));
        assertFalse(JudgeManager.isRecentDirection(
                1, 500, -1, directions, timestamps,
                base + TimeUnit.MILLISECONDS.toNanos(501)
        ));
        assertTrue(JudgeManager.isRecentDirection(
                2, 500, 1, directions, timestamps,
                base + TimeUnit.MILLISECONDS.toNanos(100)
        ));
        assertFalse(JudgeManager.isRecentDirection(
                3, 500, -1, directions, timestamps, base
        ));
    }

    @Test
    void durationIsClampedAndInvalidSidesFailClosed() {
        long base = TimeUnit.SECONDS.toNanos(1);
        int[] directions = {-1, 0, 0};
        long[] timestamps = {base, 0, 0};

        assertTrue(JudgeManager.isRecentDirection(
                1, 0, -1, directions, timestamps,
                base + TimeUnit.MILLISECONDS.toNanos(50)
        ));
        assertFalse(JudgeManager.isRecentDirection(
                1, 0, -1, directions, timestamps,
                base + TimeUnit.MILLISECONDS.toNanos(51)
        ));
        assertFalse(JudgeManager.isRecentDirection(
                0, 500, -1, directions, timestamps, base
        ));
        assertFalse(JudgeManager.isRecentDirection(
                4, 500, -1, directions, timestamps, base
        ));
    }
}
