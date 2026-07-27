package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.ReplayData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSIROrajaHelperBridgeTest {
    @Test
    void dpPlacementIsExportedPerSideWithoutScratchLanes() {
        ReplayData replay = new ReplayData();
        replay.laneShufflePattern = new int[][]{
                {2, 0, 6, 4, 1, 5, 3, 7},
                {10, 8, 14, 12, 9, 13, 11, 15}
        };

        assertEquals(
                "3175264",
                BMSIROrajaHelperBridge.sidePlacement(
                        Mode.BEAT_14K,
                        replay,
                        0,
                        2
                )
        );
        assertEquals(
                "3175264",
                BMSIROrajaHelperBridge.sidePlacement(
                        Mode.BEAT_14K,
                        replay,
                        1,
                        2
                )
        );
        assertEquals(
                14,
                BMSIROrajaHelperBridge.playableKeyCount(Mode.BEAT_14K)
        );
    }

    @Test
    void normalAndMirrorHaveDeterministicFallbackPlacements() {
        ReplayData replay = new ReplayData();
        assertEquals(
                "1234567",
                BMSIROrajaHelperBridge.sidePlacement(
                        Mode.BEAT_7K,
                        replay,
                        0,
                        0
                )
        );
        assertEquals(
                "7654321",
                BMSIROrajaHelperBridge.sidePlacement(
                        Mode.BEAT_7K,
                        replay,
                        0,
                        1
                )
        );
        assertEquals(
                "123456789",
                BMSIROrajaHelperBridge.sidePlacement(
                        Mode.POPN_9K,
                        replay,
                        0,
                        0
                )
        );
    }
}
