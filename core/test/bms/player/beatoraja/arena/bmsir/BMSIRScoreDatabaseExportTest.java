package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.ScoreDatabaseAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRScoreDatabaseExportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsOnlyNormalDatabaseWithoutOverwritingTheSource() throws Exception {
        Path normal = temporaryDirectory.resolve("score.db");
        Path maniac = temporaryDirectory.resolve("bmsir_maniac.db");
        writeScore(normal, "normal");
        writeScore(maniac, "maniac");

        BMSIRScoreDatabaseExport.ExportResult result =
                BMSIRScoreDatabaseExport.export(temporaryDirectory);

        assertNotEquals(normal, result.path());
        assertTrue(java.nio.file.Files.isRegularFile(result.path()));
        assertEquals(1, result.normalScores());
        assertEquals(1, result.excludedManiacScores());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + result.path())) {
            assertEquals("normal", connection.createStatement()
                    .executeQuery("SELECT sha256 FROM score").getString(1));
            assertEquals("ok", connection.createStatement()
                    .executeQuery("PRAGMA integrity_check").getString(1));
        }
    }

    private static void writeScore(Path path, String hash) throws Exception {
        ScoreDatabaseAccessor database = new ScoreDatabaseAccessor(path.toString());
        database.createTable();
        ScoreData score = new ScoreData();
        score.setSha256(hash);
        score.setNotes(1);
        score.setPassnotes(1);
        score.setMinbp(0);
        database.setScoreData(score);
    }
}
