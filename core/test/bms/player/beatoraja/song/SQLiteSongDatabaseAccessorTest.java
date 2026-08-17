package bms.player.beatoraja.song;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void readsSongsForMultipleSelectionParentsInOneOperation() throws Exception {
        Path database = temporary.resolve("song-parent-batch.db");
        SQLiteSongDatabaseAccessor accessor = new SQLiteSongDatabaseAccessor(
                database.toString(),
                new String[]{temporary.toString()}
        );
        SongData first = songWithParent("First", "a".repeat(64), "parent-a");
        SongData second = songWithParent("Second", "b".repeat(64), "parent-b");
        SongData excluded = songWithParent("Excluded", "c".repeat(64), "parent-c");
        accessor.setSongDatas(new SongData[]{first, second, excluded});

        SongData[] visible = accessor.getSongDatasByParents(
                new String[]{"parent-a", "parent-b", "parent-a"});

        assertEquals(2, visible.length);
        assertTrue(java.util.Arrays.stream(visible).anyMatch(song -> song.getTitle().equals("First")));
        assertTrue(java.util.Arrays.stream(visible).anyMatch(song -> song.getTitle().equals("Second")));
        assertEquals(0, accessor.getSongDatasByParents(new String[0]).length);
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
	void updatesOnlyTheSelectedSongRootWhenAnUpdatePathIsProvided() throws Exception {
		Path firstRoot = temporary.resolve("first-root");
		Path secondRoot = temporary.resolve("second-root");
		Files.createDirectories(firstRoot);
		Files.createDirectories(secondRoot);
		Path firstChart = firstRoot.resolve("chart.bms");
		Path secondChart = secondRoot.resolve("chart.bms");
		Files.writeString(firstChart, chartWithTitle("First original"));
		Files.writeString(secondChart, chartWithTitle("Second original"));

		Path database = temporary.resolve("selected-root-songdata.db");
		String[] roots = {firstRoot.toString(), secondRoot.toString()};
		SQLiteSongDatabaseAccessor accessor = new SQLiteSongDatabaseAccessor(database.toString(), roots);
		accessor.updateSongDatas(null, roots, false, false, null);
		assertEquals(1, accessor.getSongDatas("title", "First original").length);
		assertEquals(1, accessor.getSongDatas("title", "Second original").length);

		Files.writeString(firstChart, chartWithTitle("First updated"));
		Files.writeString(secondChart, chartWithTitle("Second updated"));
		FileTime changed = FileTime.fromMillis(System.currentTimeMillis() + 5_000);
		Files.setLastModifiedTime(firstChart, changed);
		Files.setLastModifiedTime(secondChart, changed);

		accessor.updateSongDatas(firstRoot.toString(), roots, false, false, null);

		assertEquals(0, accessor.getSongDatas("title", "First original").length);
		assertEquals(1, accessor.getSongDatas("title", "First updated").length);
		assertEquals(1, accessor.getSongDatas("title", "Second original").length);
		assertEquals(0, accessor.getSongDatas("title", "Second updated").length);
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
		disabled.setScanSongArchives(true);
		disabled.updateSongDatas(null, roots, false, false, null);
		assertEquals(1, disabled.getSongDatas("title", "Archive Database Test").length);

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

	@Test
	void targetedArchiveFolderRefreshReenumeratesAndFailsClosed() throws Exception {
		Path songs = temporary.resolve("targeted-archive-songs");
		Files.createDirectories(songs);
		Path archive = songs.resolve("targeted.zip");
		writeChartArchive(archive, "Original");
		String[] roots = { songs.toString() };
		Path database = temporary.resolve("targeted-archive.db");
		SQLiteSongDatabaseAccessor accessor = new SQLiteSongDatabaseAccessor(database.toString(), roots, true);
		accessor.updateSongDatas(null, roots, false, false, null);
		assertEquals(1, accessor.getSongDatas("title", "Original").length);
		String virtualFolder = archiveFolderPath(database, "targeted.zip");

		writeChartArchive(archive, "Modified");
		accessor.updateSongDatas(virtualFolder, roots, false, false, null);
		assertEquals(0, accessor.getSongDatas("title", "Original").length);
		assertEquals(1, accessor.getSongDatas("title", "Modified").length);

		Files.writeString(archive, "corrupt archive");
		SongDatabaseUpdateListener listener = new SongDatabaseUpdateListener();
		accessor.updateSongDatas(virtualFolder, roots, false, false, null, listener);
		assertEquals(1, accessor.getSongDatas("title", "Modified").length);
		assertEquals(1, listener.getArchivesRejected());
		assertTrue(listener.getLastArchiveFailure().contains("ZIP"));
	}

	@Test
	void nestedArchiveChartsRemainReachableAndUsePerDirectoryPreviews() throws Exception {
		Path songs = temporary.resolve("nested-archive-songs");
		Files.createDirectories(songs);
		Path archive = songs.resolve("nested.zip");
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
			writeEntry(output, "Pack/A/chart.bms", chartWithTitle("Nested A"));
			writeEntry(output, "Pack/A/sound.wav", "sound-a");
			writeEntry(output, "Pack/A/preview.ogg", "preview-a");
			writeEntry(output, "Pack/B/chart.bms", chartWithTitle("Nested B"));
			writeEntry(output, "Pack/B/sound.wav", "sound-b");
			writeEntry(output, "Pack/B/preview.mp3", "preview-b");
		}
		String[] roots = { songs.toString() };
		Path database = temporary.resolve("nested-archive.db");
		SQLiteSongDatabaseAccessor accessor = new SQLiteSongDatabaseAccessor(database.toString(), roots, true);
		accessor.updateSongDatas(null, roots, false, false, null);

		SongData first = accessor.getSongDatas("title", "Nested A")[0];
		SongData second = accessor.getSongDatas("title", "Nested B")[0];
		assertEquals("preview.ogg", first.getPreview());
		assertEquals("preview.mp3", second.getPreview());
		assertEquals(first.getParent(), second.getParent());
		assertNotEquals(first.getFolder(), second.getFolder());

		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT path FROM folder WHERE title = 'nested.zip'")) {
			assertTrue(result.next());
			String folderPath = result.getString(1);
			folderPath = folderPath.substring(0, folderPath.length() - 1);
			assertEquals(SongUtils.crc32(folderPath, new String[0], Paths.get(".").toAbsolutePath().toString()),
					first.getParent());
		}
	}

	private static void writeEntry(ZipOutputStream output, String name, String value) throws Exception {
		output.putNextEntry(new ZipEntry(name));
		output.write(value.getBytes(StandardCharsets.UTF_8));
		output.closeEntry();
	}

	private static void writeChartArchive(Path archive, String title) throws Exception {
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
			writeEntry(output, "Pack/chart.bms", chartWithTitle(title));
			writeEntry(output, "Pack/sound.wav", "sound");
		}
	}

	private static String chartWithTitle(String title) {
		return "#PLAYER 1\n#TITLE " + title + "\n#BPM 120\n#WAV01 sound.wav\n#00111:01\n";
	}

    private SongData songWithParent(String title, String sha256, String parent) {
        SongData song = new SongData();
        song.setTitle(title);
        song.setSha256(sha256);
        song.setPath(temporary.resolve(title + ".bms").toString());
        song.setParent(parent);
        return song;
    }

	private static String previewPath(Path database) throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
			 Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery("SELECT preview FROM song")) {
			return result.next() ? result.getString(1) : null;
		}
	}

	private static String archiveFolderPath(Path database, String title) throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
				java.sql.PreparedStatement statement = connection.prepareStatement(
						"SELECT path FROM folder WHERE title = ?")) {
			statement.setString(1, title);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? result.getString(1) : null;
			}
		}
	}
}
