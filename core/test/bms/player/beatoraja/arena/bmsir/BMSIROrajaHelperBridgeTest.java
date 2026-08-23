package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.ReplayData;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void bundledViewPayloadIsVersionedAndIncludesBothDpSides() {
        bms.model.BMSModel model = new bms.model.BMSModel();
        model.setMode(Mode.BEAT_14K);
        model.setTitle("DP chart");
        ReplayData replay = new ReplayData();
        replay.randomoption = 2;
        replay.randomoption2 = 2;
        replay.laneShufflePattern = new int[][]{
                {0, 1, 2, 3, 4, 5, 6, 7},
                {8, 9, 10, 11, 12, 13, 14, 15}
        };

        var message = BMSIROrajaHelperBridge.placementMessage(model, replay);

        assertEquals(1, message.path("schemaVersion").asInt());
        assertTrue(message.path("updatedAt").asLong() > 0);
        assertEquals("1234567", message.path("randomPlacement").asText());
        assertEquals("1234567", message.path("randomPlacement2P").asText());
        assertTrue(message.path("doublePlay").asBoolean());
		assertEquals("song_play", message.path("event").asText());
    }

	@Test
	void resultAndPlayEndPayloadsCarryScoreAndProgressMetrics() {
		SongData song = new SongData();
		song.setTitle("Chart");
		song.setArtist("Artist");
		song.setNotes(1000);
		ScoreData score = new ScoreData();
		score.setNotes(1000);
		score.setEpg(700);
		score.setLpg(50);
		score.setEgr(100);
		score.setClear(6);
		score.setMinbp(12);

		var result = BMSIROrajaHelperBridge.resultMessage(
				song, new ReplayData(), Mode.BEAT_7K, score
		);
		var playEnd = BMSIROrajaHelperBridge.playEndMessage(
				song, new ReplayData(), Mode.BEAT_7K, score,
				750, 1000, 42, true
		);

		assertEquals("song_result", result.path("event").asText());
		assertEquals(1600, result.path("score").asInt());
		assertEquals(80.0, result.path("scoreRate").asDouble(), 0.001);
		assertEquals(700, result.path("judges").path("epg").asInt());
		assertEquals("song_play_end", playEnd.path("event").asText());
		assertEquals(750, playEnd.path("playedNotes").asInt());
		assertEquals(42, playEnd.path("elapsedSeconds").asLong());
		assertTrue(playEnd.path("quickRetry").asBoolean());
	}
}
