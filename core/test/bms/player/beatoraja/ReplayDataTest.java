package bms.player.beatoraja;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplayDataTest {
    private static final long RANDOM_SEED_BASE = 65536L * 256L;

    @Test
    void missingPackedSeedStaysMissingOnBothSides() {
        ReplayData replay = new ReplayData();

        replay.setRandomOptionSeeds(-1L);

        assertEquals(-1L, replay.randomoptionseed);
        assertEquals(-1L, replay.randomoption2seed);
    }

    @Test
    void packedDoubleSeedSplitsEachSideWithoutChangingZero() {
        ReplayData replay = new ReplayData();
        long first = 0L;
        long second = 7654321L;

        replay.setRandomOptionSeeds(second * RANDOM_SEED_BASE + first);

        assertEquals(first, replay.randomoptionseed);
        assertEquals(second, replay.randomoption2seed);
    }
}
