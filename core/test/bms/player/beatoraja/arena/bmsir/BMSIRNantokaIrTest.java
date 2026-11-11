package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.CourseData.CourseDataConstraint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRNantokaIrTest {
    private static final String TOKEN = "t".repeat(43);

    @TempDir
    Path temporaryDirectory;

    @Test
    void configurationRequiresHttpsExceptOnThisMachine() throws Exception {
        assertEquals("example.com", BMSIRNantokaIr.parse(config("https://example.com", TOKEN)).server().getHost());
        assertEquals(8787, BMSIRNantokaIr.parse(config("http://127.0.0.1:8787", TOKEN)).server().getPort());
        assertEquals("/api/v1/", BMSIRNantokaIr.parse(config("https://example.com/api/v1", TOKEN)).server().getPath());
        assertEquals("https://example.com/api/v1/score",
                BMSIRNantokaIr.parse(config("https://example.com/api/v1/", TOKEN)).server().resolve("score").toString());
        assertEquals(TOKEN, BMSIRNantokaIr.parse("\uFEFF" + config("https://example.com", TOKEN)).token());
        assertThrows(IOException.class, () -> BMSIRNantokaIr.parse(config("http://example.com", TOKEN)));
        assertThrows(IOException.class, () -> BMSIRNantokaIr.parse(config("https://example.com", "short")));
        assertThrows(IOException.class, () -> BMSIRNantokaIr.parse(config("https://user@example.com", TOKEN)));
    }

    @Test
    void importReadsOnlyUnmodifiedNantokaManiaBestsNewerThanTheMarker() throws Exception {
        Path database = temporaryDirectory.resolve("bmsir_maniac.db");
        String nantoka = settings(true, false).canonicalOptions();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE score(sha256 TEXT, mode INTEGER, clear INTEGER, epg INTEGER, lpg INTEGER, egr INTEGER,
                    lgr INTEGER, egd INTEGER, lgd INTEGER, ebd INTEGER, lbd INTEGER, epr INTEGER, lpr INTEGER, ems INTEGER,
                    lms INTEGER, notes INTEGER, combo INTEGER, minbp INTEGER, option INTEGER, seed INTEGER, ghost TEXT, date INTEGER)""");
            statement.execute("CREATE TABLE maniac_score_meta(storage_hash TEXT, base_sha256 TEXT, options TEXT)");
            insert(statement, "s1", "a".repeat(64), nantoka, 200);
            insert(statement, "s2", "b".repeat(64), settings(true, true).canonicalOptions(), 300);
            insert(statement, "s3", "c".repeat(64), settings(false, true).canonicalOptions(), 400);
            insert(statement, "s4", "d".repeat(64), nantoka, 100);
        }
        List<ObjectNode> items = BMSIRNantokaIr.localBests(database, 150);
        assertEquals(1, items.size());
        ObjectNode item = items.get(0);
        assertEquals("a".repeat(64), item.path("sha256").asText());
        assertEquals(BMSIRManiacSettings.NANTOKA_JUDGE_VERSION, item.path("judge_version").asInt());
        assertEquals(1, item.path("lnmode").asInt());
        assertEquals(7 * 2 + 3, item.path("exscore").asInt());
        assertEquals(200, item.path("played_at").asLong());
        assertTrue(item.path("replay_hash").asText().matches("[0-9a-f]{64}"));
    }

    @Test
    void titlesComeFromTheSongDatabaseAndAreOptional() {
        bms.player.beatoraja.song.SongData song = new bms.player.beatoraja.song.SongData();
        song.setSha256("A".repeat(64));
        song.setTitle("Title");
        song.setSubtitle("[ANOTHER]");
        song.setArtist("Artist");
        bms.player.beatoraja.song.SongDatabaseAccessor songs = (bms.player.beatoraja.song.SongDatabaseAccessor) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {bms.player.beatoraja.song.SongDatabaseAccessor.class},
                (proxy, method, args) -> method.getName().equals("getSongDatas") && args.length == 1
                        ? new bms.player.beatoraja.song.SongData[] {song} : null);
        ObjectNode known = BMSIRNantokaIr.chartPayload("a".repeat(64), 2, 0);
        ObjectNode unknown = BMSIRNantokaIr.chartPayload("b".repeat(64), 2, 0);
        BMSIRNantokaIr.addTitles(songs, List.of(known, unknown));
        assertEquals(song.getFullTitle(), known.path("title").asText());
        assertEquals("Artist", known.path("artist").asText());
        assertTrue(unknown.path("title").isMissingNode());
    }

    @Test
    void courseHashFollowsStagesAndRulesButNotRuleOrder() {
        String[] stages = {"1".repeat(64), "2".repeat(64)};
        CourseDataConstraint[] rules = {CourseDataConstraint.NO_SPEED, CourseDataConstraint.GAUGE_LR2};
        CourseDataConstraint[] reversed = {CourseDataConstraint.GAUGE_LR2, CourseDataConstraint.NO_SPEED};
        assertEquals(BMSIRNantokaIr.courseHash(stages, rules), BMSIRNantokaIr.courseHash(stages, reversed));
        assertNotEquals(BMSIRNantokaIr.courseHash(stages, rules), BMSIRNantokaIr.courseHash(stages, null));
        assertNotEquals(BMSIRNantokaIr.courseHash(stages, rules),
                BMSIRNantokaIr.courseHash(new String[] {stages[1], stages[0]}, rules));
    }

    /** Opt-in check against a running server: NANTOKA_IR_E2E_SERVER, NANTOKA_IR_E2E_TOKEN and a copy of a real bmsir_maniac.db. */
    @Test
    @EnabledIfEnvironmentVariable(named = "NANTOKA_IR_E2E_SERVER", matches = ".+")
    void importsARealManiacDatabaseIntoARunningServer() throws Exception {
        BMSIRNantokaIr.Config config = BMSIRNantokaIr.parse(config(System.getenv("NANTOKA_IR_E2E_SERVER"), System.getenv("NANTOKA_IR_E2E_TOKEN")));
        List<ObjectNode> items = BMSIRNantokaIr.localBests(Path.of(System.getenv("NANTOKA_IR_E2E_DB")), 0);
        assertTrue(items.size() > 0);
        ObjectMapper json = new ObjectMapper();
        int imported = 0;
        for (int start = 0; start < items.size(); start += 40) {
            ObjectNode request = json.createObjectNode();
            items.subList(start, Math.min(items.size(), start + 40)).forEach(request.putArray("items")::add);
            imported += BMSIRNantokaIr.post(config, "import", request).path("imported").asInt();
        }
        System.out.println("E2E imported " + imported + " of " + items.size());
        ObjectNode first = items.get(0);
        ObjectNode ranking = BMSIRNantokaIr.post(config, "ranking", BMSIRNantokaIr.chartPayload(
                first.path("sha256").asText(), first.path("judge_version").asInt(), first.path("lnmode").asInt()));
        assertTrue(ranking.path("total").asInt() >= 1);
        assertEquals(items.size(), BMSIRNantokaIr.post(config, "sync", json.createObjectNode()).path("total").asInt());
    }

    private static BMSIRManiacSettings settings(boolean nantoka, boolean mines) {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setNantokaMania(nantoka);
        if (mines) settings.setAddMines(10);
        return settings;
    }

    private static void insert(Statement statement, String storage, String base, String options, long date) throws Exception {
        statement.execute("INSERT INTO score VALUES('" + storage + "',1,5,4,3,2,1,0,0,0,0,1,0,2,0,100,50,3,0,-1,'g'," + date + ")");
        statement.execute("INSERT INTO maniac_score_meta VALUES('" + storage + "','" + base + "','" + options + "')");
    }

    private static String config(String server, String token) {
        return "{\"server\":\"" + server + "\",\"token\":\"" + token + "\"}";
    }
}
