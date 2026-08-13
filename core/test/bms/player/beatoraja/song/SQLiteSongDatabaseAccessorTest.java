package bms.player.beatoraja.song;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	void scansZipChartsOnlyWhenArchiveScanningIsEnabled() throws Exception {
		Path songs = temporary.resolve("archive-songs");
		Files.createDirectories(songs);
		Path archive = songs.resolve("pack.zip");
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
			writeEntry(output, "Pack/chart.bms",
					"#PLAYER 1\n#TITLE Archive Database Test\n#BPM 120\n#WAV01 sound.wav\n#PREVIEW preview.wav\n#00111:01\n");
			writeEntry(output, "Pack/sound.wav", "sound");
			writeEntry(output, "Pack/preview.wav", "preview");
		}
		byte[] original = Files.readAllBytes(archive);
		String[] roots = { songs.toString() };

		Path disabledDatabase = temporary.resolve("archive-disabled.db");
		SQLiteSongDatabaseAccessor disabled = new SQLiteSongDatabaseAccessor(
				disabledDatabase.toString(), roots, false);
		disabled.updateSongDatas(null, roots, false, false, null);
		assertEquals(0, disabled.getSongDatas("title", "Archive Database Test").length);

		Path enabledDatabase = temporary.resolve("archive-enabled.db");
		SQLiteSongDatabaseAccessor enabled = new SQLiteSongDatabaseAccessor(
				enabledDatabase.toString(), roots, true);
		enabled.updateSongDatas(null, roots, false, false, null);
		SongData[] songsInArchive = enabled.getSongDatas("title", "Archive Database Test");
		assertEquals(1, songsInArchive.length);
		assertTrue(songsInArchive[0].getPath().contains("pack.zip!-Pack"));
		assertEquals("preview.wav", songsInArchive[0].getPreview());
		assertEquals(64, songsInArchive[0].getSha256().length());
		assertEquals(32, songsInArchive[0].getMd5().length());
		assertArrayEquals(original, Files.readAllBytes(archive));
	}

	private static void writeEntry(ZipOutputStream output, String name, String value) throws Exception {
		output.putNextEntry(new ZipEntry(name));
		output.write(value.getBytes(StandardCharsets.UTF_8));
		output.closeEntry();
	}

	private static String previewPath(Path database) throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery("SELECT preview FROM song")) {
			return result.next() ? result.getString(1) : null;
		}
	}
}
