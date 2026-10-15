package bms.player.beatoraja;

import bms.player.beatoraja.ir.IRScoreData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BMSIRFloatingHispeedScoreTest {
    @TempDir Path directory;

    @Test
    void irScoreCarriesTheLivePlayMarkerAndRoundTrips() {
        ScoreData score = BMSIRLongNoteScoreTest.score("fhs", 0, 5, 0);
        assertFalse(new IRScoreData(score).bmsirFloatingHispeedUsed);
        score.setBmsirFloatingHispeedUsed(true);
        IRScoreData ir = new IRScoreData(score);
        assertTrue(ir.bmsirFloatingHispeedUsed);
        assertTrue(ir.convertToScoreData().isBmsirFloatingHispeedUsed());
    }

    @Test
    void localScoreDatabaseDoesNotPersistTheMarker() throws Exception {
        ScoreDatabaseAccessor db = new ScoreDatabaseAccessor(directory.resolve("score.db").toString());
        db.createTable();
        ScoreData score = BMSIRLongNoteScoreTest.score("fhs-db", 0, 5, 0);
        score.setBmsirFloatingHispeedUsed(true);
        db.setScoreData(score);
        ScoreData stored = db.getScoreData("fhs-db", 0);
        assertNotNull(stored);
        assertFalse(stored.isBmsirFloatingHispeedUsed());
        assertFalse(new IRScoreData(stored).bmsirFloatingHispeedUsed);
    }
}
