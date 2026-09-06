package bms.player.beatoraja;

import bms.player.beatoraja.song.SongData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BMSIRLongNoteScoreTest {
    @TempDir Path directory;

    @Test
    void legacyExplicitScoresAreNotReadAsForcedScoresAndAreArchivedBeforeReplacement() throws Exception {
        Path path = directory.resolve("score.db");
        ScoreDatabaseAccessor db = new ScoreDatabaseAccessor(path.toString());
        db.createTable();
        SongData explicit = song("explicit", SongData.FEATURE_HELLCHARGENOTE);
        SongData ordinary = song("ordinary", SongData.FEATURE_UNDEFINEDLN);
        db.setScoreData(score("explicit", 0, 9, 0));
        db.setScoreData(score("ordinary", 0, 7, 0));
        Map<SongData, ScoreData> results = new IdentityHashMap<>();
        db.getScoreDatas(results::put, new SongData[]{explicit, ordinary}, 0, true);
        assertNull(results.get(explicit));
        assertEquals(7, results.get(ordinary).getClear());

        db.setScoreData(score("explicit", 0, 3, 1));
        assertEquals(3, db.getScoreData("explicit", 0).getClear());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT snapshot FROM bmsir_legacy_ln_score")) {
            assertTrue(rows.next());
            var archived = new ObjectMapper().readTree(rows.getString(1));
            assertEquals(9, archived.path("clear").asInt());
            assertEquals("explicit", archived.path("sha256").asText());
            assertFalse(rows.next());
        }

        // Old sync/import data must not replace a newly verified result.
        db.setScoreData(score("explicit", 0, 10, 0));
        assertEquals(3, db.getScoreData("explicit", 0).getClear());
        db.setScoreData(score("explicit", 1, 5, 1));
        db.setScoreData(score("explicit", 2, 6, 1));
        for (int mode = 0; mode <= 2; mode++) {
            db.getScoreDatas(results::put, new SongData[]{explicit}, mode, true);
            assertEquals(mode, results.get(explicit).getMode());
            assertEquals(1, results.get(explicit).getBmsirLongNotePolicy());
        }
    }

    @Test
    void forcedScoreReadAndArchiveUpgradePreserveAnExistingDatabase() throws Exception {
        Path path = directory.resolve("old.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE score (sha256 TEXT, mode INTEGER, clear INTEGER, notes INTEGER, PRIMARY KEY(sha256, mode))");
            statement.execute("INSERT INTO score VALUES ('old', 0, 5, 4)");
        }
        ScoreDatabaseAccessor db = new ScoreDatabaseAccessor(path.toString());
        db.createTable();
        assertEquals(0, db.getScoreData("old", 0).getBmsirLongNotePolicy());
        assertEquals(5, db.getScoreData("old", 0).getClear());
    }

    static ScoreData score(String hash, int mode, int clear, int policy) {
        ScoreData score = new ScoreData();
        score.setSha256(hash);
        score.setMode(mode);
        score.setClear(clear);
        score.setNotes(4);
        score.setMinbp(0);
        score.setBmsirLongNotePolicy(policy);
        return score;
    }

    @Test
    void failedReplacementRollsBackTheArchiveAndTheScoreTogether() throws Exception {
        Path path = directory.resolve("rollback.db");
        ScoreDatabaseAccessor db = new ScoreDatabaseAccessor(path.toString());
        db.createTable();
        db.setScoreData(score("rollback", 0, 9, 0));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_forced BEFORE INSERT ON score WHEN NEW.bmsirLongNotePolicy = 1 "
                    + "BEGIN SELECT RAISE(ABORT, 'controlled test failure'); END");
        }
        db.setScoreData(score("rollback", 0, 3, 1));
        assertEquals(9, db.getScoreData("rollback", 0).getClear());
        assertEquals(0, db.getScoreData("rollback", 0).getBmsirLongNotePolicy());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name = 'bmsir_legacy_ln_score'")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void irScoreCarriesTheLocalPolicyWithoutChangingIdentityOrMode() {
        ScoreData score = score("source-sha", 2, 3, 1);
        var ir = new bms.player.beatoraja.ir.IRScoreData(score);
        assertEquals("source-sha", ir.sha256);
        assertEquals(2, ir.lntype);
        assertEquals(1, ir.bmsirLongNotePolicy);
        assertEquals(1, ir.convertToScoreData().getBmsirLongNotePolicy());
    }

    @Test
    void rivalCatalogReadsUseReturnedModeEvenWithAuthoredHcnMetadata() throws Exception {
        ScoreDatabaseAccessor db = new ScoreDatabaseAccessor(directory.resolve("rival.db").toString());
        db.createTable();
        SongData song = song("rival", SongData.FEATURE_HELLCHARGENOTE);
        for (int mode = 0; mode <= 2; mode++) db.setScoreData(score("rival", mode, 3 + mode, 0));
        Map<SongData, ScoreData> result = new IdentityHashMap<>();
        for (int mode = 0; mode <= 2; mode++) {
            db.getScoreDatas(result::put, new SongData[]{song}, mode);
            assertEquals(mode, result.get(song).getMode());
        }
    }

    private static SongData song(String hash, int features) {
        SongData song = new SongData();
        song.setSha256(hash);
        song.setFeature(features);
        return song;
    }
}
