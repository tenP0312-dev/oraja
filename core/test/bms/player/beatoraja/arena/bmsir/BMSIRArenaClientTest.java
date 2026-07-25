package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.song.SongData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSIRArenaClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void spOptionDoesNotIncludeStaleSecondSideOrFlipSettings() {
        PlayerConfig config = new PlayerConfig();
        config.setRandom(2);
        config.setRandom2(1);
        config.setDoubleoption(1);

        assertEquals(
                2,
                BMSIRArenaClient.encodePlayOption(config, Mode.BEAT_7K.id)
        );
        assertEquals(
                112,
                BMSIRArenaClient.encodePlayOption(config, Mode.BEAT_14K.id)
        );
    }

    @Test
    void completedFinalUsesTheServerSelectedChartTotal() {
        ScoreData score = new ScoreData();
        score.setNotes(100);
        score.setPassnotes(95);

        assertEquals(
                100,
                BMSIRArenaClient.finalProcessedNotes(score, false, 100)
        );
        assertEquals(
                95,
                BMSIRArenaClient.finalProcessedNotes(score, true, 100)
        );
    }

    @Test
    void chartCheckReportsOnlyAConsistentCachedNoteCount() {
        SongData song = new SongData();
        song.setNotes(100);

        assertEquals(100, BMSIRArenaClient.chartCheckTotalNotes(song, 100));
        assertEquals(0, BMSIRArenaClient.chartCheckTotalNotes(song, 101));
        assertEquals(0, BMSIRArenaClient.chartCheckTotalNotes(null, 100));
    }

    @Test
    void arenaStatusUpdatesOverlayQueueRatingAndRanking() throws Exception {
        BMSIRArenaClient.receiveArenaStatus(JSON.readTree("""
                {
                  "player": {
                    "rating_exact": 1234.5,
                    "matches_played": 9,
                    "queue": {"status": "queued"}
                  },
                  "ranking": {
                    "current": {"rank": 3},
                    "rows": [{"rank": 1, "player_id": 7}]
                  }
                }
                """));

        assertEquals(1234.5, BMSIRArenaClient.arenaRating());
        assertEquals(9, BMSIRArenaClient.arenaMatchesPlayed());
        assertEquals("queued", BMSIRArenaClient.queueStatus());
        assertEquals(3, BMSIRArenaClient.rankingView().path("current").path("rank").asInt());
    }
}
