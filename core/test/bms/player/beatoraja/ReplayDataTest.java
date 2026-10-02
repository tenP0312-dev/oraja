package bms.player.beatoraja;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReplayDataTest {
    @Test
    void addNotesReplaySerializationRestoresLegacyAndCurrentPlacement() {
        com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
        ReplayData replay = new ReplayData();
        replay.bmsirManiacSettings = new bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings();
        replay.bmsirManiacSettings.setAddNotes(100);
        replay.bmsirManiacAlgorithmVersion = 1;
        replay.keylog = new bms.player.beatoraja.input.KeyInputLog[]{
                new bms.player.beatoraja.input.KeyInputLog(1000, 0, true)};
        replay.bmsirAddNotesPlacementVersion = 2;
        ReplayData current = json.fromJson(ReplayData.class, json.toJson(replay));
        assertTrue(current.validate());
        assertEquals(2, current.bmsirManiacSettings.getAddNotesPlacementVersion());
        replay.bmsirAddNotesPlacementVersion = 1;
        // Json omits default-valued fields, matching a pre-version replay.
        ReplayData legacy = json.fromJson(ReplayData.class, json.toJson(replay));
        assertTrue(legacy.validate());
        assertEquals(1, legacy.bmsirManiacSettings.getAddNotesPlacementVersion());
        assertEquals("add-notes-100", legacy.bmsirManiacSettings.rankingKey());
        legacy.bmsirAddNotesPlacementVersion = 3;
        assertFalse(legacy.validate());
    }
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
