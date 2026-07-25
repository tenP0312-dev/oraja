package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.song.SongData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void nominationCountdownRoundsUpAndStopsAtZero() {
        assertEquals(
                60,
                BMSIRArenaClient.nominationCountdownSeconds(61_000, 1_000)
        );
        assertEquals(
                1,
                BMSIRArenaClient.nominationCountdownSeconds(61_000, 60_999)
        );
        assertEquals(
                0,
                BMSIRArenaClient.nominationCountdownSeconds(61_000, 61_000)
        );
    }

    @Test
    void officialArenaLevelsExcludeZeroUnknownAndOutOfRangeFolders() {
        assertEquals(1, BMSIRArenaClient.officialArenaLevel("★1"));
        assertEquals(25, BMSIRArenaClient.officialArenaLevel("★25"));
        assertEquals(-1, BMSIRArenaClient.officialArenaLevel("★0"));
        assertEquals(-1, BMSIRArenaClient.officialArenaLevel("★26"));
        assertEquals(-1, BMSIRArenaClient.officialArenaLevel("★???"));
        assertEquals(-1, BMSIRArenaClient.officialArenaLevel("▼1"));
    }

    @Test
    void officialArenaTableMatchesTheKnownNameOrUrl() {
        assertTrue(
                BMSIRArenaClient.isOfficialArenaTable(
                        "発狂BMS難易度表",
                        ""
                )
        );
        assertTrue(
                BMSIRArenaClient.isOfficialArenaTable(
                        "",
                        "https://darksabun.club/table/archive/insane1/"
                )
        );
        assertFalse(
                BMSIRArenaClient.isOfficialArenaTable(
                        "NEW GENERATION 発狂難易度表",
                        "http://rattoto10.jounin.jp/table_insane.html"
                )
        );
    }

    @Test
    void nominationCandidatesUseOwnedOfficialLevelsThroughTheCeiling() {
        SongData levelOne = song("a");
        SongData levelTwo = song("b");
        SongData levelThree = song("c");
        TableData table = new TableData();
        table.setName("発狂BMS難易度表");
        table.setFolder(new TableData.TableFolder[]{
                folder("★0", song("z")),
                folder("★1", levelOne),
                folder("★2", levelOne, levelTwo),
                folder("★3", levelThree),
                folder("★???", song("x"))
        });

        SongData[] candidates =
                BMSIRArenaClient.nominationCandidateElements(
                        new TableData[]{table},
                        2
                );

        assertEquals(2, candidates.length);
        assertEquals("a", candidates[0].getMd5());
        assertEquals("b", candidates[1].getMd5());
    }

    private static TableData.TableFolder folder(
            String name,
            SongData... songs
    ) {
        TableData.TableFolder folder = new TableData.TableFolder();
        folder.setName(name);
        folder.setSong(songs);
        return folder;
    }

    private static SongData song(String md5) {
        SongData song = new SongData();
        song.setMd5(md5);
        return song;
    }
}
