package bms.player.beatoraja;

import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScoreDatabaseAccessorDerivedHashTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsDerivedKeysInOneBatchAndFallsBackToModeZero() throws Exception {
        ScoreDatabaseAccessor database = new ScoreDatabaseAccessor(
                temporaryDirectory.resolve("score.db").toString()
        );
        database.createTable();
        database.setScoreData(score("derived-a", 2, 3));
        database.setScoreData(score("derived-b", 0, 5));

        SongData first = song("base-a", true);
        SongData second = song("base-b", true);
        SongData missing = song("base-c", false);
        Map<SongData, ScoreData> loaded = new IdentityHashMap<>();

        database.getScoreDatasByHash(
                loaded::put,
                new SongData[]{first, second, missing},
                2,
                song -> "derived-" + song.getSha256().substring("base-".length()),
                true
        );

        assertEquals(3, loaded.get(first).getClear());
        assertEquals(5, loaded.get(second).getClear());
        assertNull(loaded.get(missing));
    }

    private static ScoreData score(String hash, int mode, int clear) {
        ScoreData score = new ScoreData();
        score.setSha256(hash);
        score.setMode(mode);
        score.setClear(clear);
        score.setNotes(1);
        score.setPassnotes(1);
        score.setMinbp(0);
        return score;
    }

    private static SongData song(String hash, boolean undefinedLongNote) {
        SongData song = new SongData();
        song.setSha256(hash);
        if (undefinedLongNote) {
            song.setFeature(SongData.FEATURE_UNDEFINEDLN);
        }
        return song;
    }
}
