package bms.player.beatoraja.song;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SQLiteSongDatabaseAccessorTest {
    @TempDir
    Path temporary;

    @Test
    void readsCommittedSongsWhileFolderRefreshHoldsWriteTransaction() throws Exception {
        Path database = temporary.resolve("songdata.db");
        SQLiteSongDatabaseAccessor accessor = new SQLiteSongDatabaseAccessor(
                database.toString(),
                new String[]{temporary.toString()}
        );
        SongData song = new SongData();
        song.setTitle("Committed title");
        song.setSha256("a".repeat(64));
        song.setPath(temporary.resolve("chart.bms").toString());
        accessor.setSongDatas(new SongData[]{song});

        try (Connection writer = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = writer.createStatement()) {
            try (ResultSet mode = statement.executeQuery("PRAGMA journal_mode")) {
                assertEquals("wal", mode.getString(1).toLowerCase());
            }
            statement.execute("BEGIN EXCLUSIVE");
            statement.executeUpdate(
                    "UPDATE song SET title = 'Uncommitted title' WHERE sha256 = '" + song.getSha256() + "'"
            );

            SongData[] visible = accessor.getSongDatas("sha256", song.getSha256());
            assertEquals(1, visible.length);
            assertEquals("Committed title", visible[0].getTitle());

            statement.execute("ROLLBACK");
        }
    }

	@Test
	void refreshesPreviewPathWhenTheChartTimestampDidNotChange() throws Exception {
		Path songs = temporary.resolve("songs");
		Files.createDirectories(songs);
		Path chart = songs.resolve("chart.bms");
		Files.writeString(chart, "#PLAYER 1\n#TITLE Preview Test\n#BPM 120\n#00111:01\n");
		Files.write(songs.resolve("preview.wav"), new byte[0]);

		Path database = temporary.resolve("preview-songdata.db");
		String[] roots = {songs.toString()};
		SQLiteSongDatabaseAccessor accessor = new SQLiteSongDatabaseAccessor(database.toString(), roots);
		accessor.updateSongDatas(null, roots, false, false, null);
		assertEquals("preview.wav", previewPath(database));

		Files.delete(songs.resolve("preview.wav"));
		Files.write(songs.resolve("preview.ogg"), new byte[0]);
		accessor.updateSongDatas(null, roots, false, false, null);

		assertEquals("preview.ogg", previewPath(database));
	}

	private static String previewPath(Path database) throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery("SELECT preview FROM song")) {
			return result.next() ? result.getString(1) : null;
		}
	}
}
