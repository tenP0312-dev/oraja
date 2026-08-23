package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bms.player.beatoraja.ir.IRScoreData;
import bms.player.beatoraja.select.ScoreDataCache;
import bms.player.beatoraja.song.SongData;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RivalDataAccessorTest {
	@TempDir
	Path temporaryDirectory;

    @Test
    void rankingResponseMatchesOnlyTheNamedRival() {
        ScoreData alice = new ScoreData();
        alice.setPlayer("Alice");
        alice.setEpg(100);
        ScoreData bob = new ScoreData();
        bob.setPlayer("Bob");
        bob.setEpg(200);
        IRScoreData[] scores = {new IRScoreData(alice), new IRScoreData(bob)};

        assertEquals(
                400,
                RivalDataAccessor.findRivalScore(scores, "Bob").getExscore()
        );
        assertNull(RivalDataAccessor.findRivalScore(scores, "Carol"));
    }

	@Test
	void updatesTheBackingDatabaseBeforeRefreshingTheCache() throws Exception {
		String hash = "a".repeat(64);
		ScoreDatabaseAccessor database = new ScoreDatabaseAccessor(
				temporaryDirectory.resolve("rival.db").toString()
		);
		database.createTable();
		SongData song = new SongData();
		song.setSha256(hash);
		ScoreDataCache cache = new ScoreDataCache() {
			@Override
			protected ScoreData readScoreDatasFromSource(SongData target, int lnmode) {
				return database.getScoreData(target.getSha256(), lnmode);
			}

			@Override
			protected void readScoreDatasFromSource(
					ScoreDatabaseAccessor.ScoreDataCollector collector,
					SongData[] songs,
					int lnmode
			) {
				database.getScoreDatas(collector, songs, lnmode);
			}
		};
		ScoreData score = new ScoreData();
		score.setSha256(hash);
		score.setMode(0);
		score.setPlayer("Alice");
		score.setClear(ClearType.Normal.id);
		score.setEpg(123);
		score.setNotes(123);
		score.setPassnotes(123);
		score.setMinbp(0);

		RivalDataAccessor.updateRivalScore(
				new IRScoreData(score), database, cache, song, 0
		);

		assertEquals(246, cache.readScoreData(song, 0).getExscore());
	}
}
