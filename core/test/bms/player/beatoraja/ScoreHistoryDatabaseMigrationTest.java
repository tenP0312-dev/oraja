package bms.player.beatoraja;

import bms.player.beatoraja.ScoreLogDatabaseAccessor.ScoreLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreHistoryDatabaseMigrationTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void removesLegacyScoreDataLogPrimaryKeyAndKeepsEveryAttempt() throws Exception {
		Path database = temporaryDirectory.resolve("scoredatalog.db");
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement()) {
			statement.executeUpdate("CREATE TABLE scoredatalog ("
					+ "sha256 TEXT NOT NULL, mode INTEGER, avgjudge INTEGER NOT NULL DEFAULT 2147483647, "
					+ "PRIMARY KEY(sha256, mode))");
			statement.executeUpdate("INSERT INTO scoredatalog (sha256, mode) VALUES ('same-chart', 0)");
		}

		ScoreDataLogDatabaseAccessor accessor = new ScoreDataLogDatabaseAccessor(database.toString());
		ScoreData attempt = new ScoreData();
		attempt.setSha256("same-chart");
		attempt.setMode(0);
		attempt.setNotes(1);
		attempt.setMinbp(0);
		accessor.setScoreDataLog(attempt);
		accessor.setScoreDataLog(attempt);

		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement()) {
			assertEquals(0, primaryKeyColumnCount(statement));
			assertEquals(3, scalarInt(statement, "SELECT COUNT(*) FROM scoredatalog"));
		}
	}

	@Test
	void addsAverageJudgeHistoryColumnsToExistingScoreLogs() throws Exception {
		Path database = temporaryDirectory.resolve("scorelog.db");
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement()) {
			statement.executeUpdate("CREATE TABLE scorelog (sha256 TEXT, mode INTEGER, date INTEGER)");
			statement.executeUpdate("INSERT INTO scorelog (sha256, mode, date) VALUES ('chart', 0, 1)");
		}

		new ScoreLogDatabaseAccessor(database.toString());

		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery("SELECT avgjudge, oldavgjudge FROM scorelog")) {
			assertTrue(result.next());
			assertEquals(Long.MAX_VALUE, result.getLong("avgjudge"));
			assertEquals(Long.MAX_VALUE, result.getLong("oldavgjudge"));
		}

		ScoreLog log = new ScoreLog();
		log.setAvgjudge(100);
		log.setOldavgjudge(200);
		assertTrue(log.validate());
	}

	private static int primaryKeyColumnCount(Statement statement) throws Exception {
		int count = 0;
		try (ResultSet result = statement.executeQuery("PRAGMA table_info('scoredatalog')")) {
			while (result.next()) {
				if (result.getInt("pk") > 0) {
					count++;
				}
			}
		}
		return count;
	}

	private static int scalarInt(Statement statement, String query) throws Exception {
		try (ResultSet result = statement.executeQuery(query)) {
			return result.next() ? result.getInt(1) : 0;
		}
	}
}
