package bms.player.beatoraja.select;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bms.player.beatoraja.select.DifferenceChartDropImporter.Failure;
import bms.player.beatoraja.select.DifferenceChartDropImporter.ImportException;
import bms.player.beatoraja.song.archive.SongArchives;

class DifferenceChartDropImporterTest {

	@TempDir
	Path temporary;

	@Test
	void copiesSupportedLooseChartsBesideTheSelectedSongWithoutMovingSources() throws Exception {
		Path songFolder = Files.createDirectories(temporary.resolve("songs/Selected"));
		Path selected = write(songFolder.resolve("normal.bms"), "#TITLE Selected");
		Path sourceFolder = Files.createDirectories(temporary.resolve("downloads"));
		Path bms = write(sourceFolder.resolve("another.bms"), "#TITLE Difference");
		Path bmson = write(sourceFolder.resolve("extra.BMSON"), "{\"info\":{}}");

		DifferenceChartDropImporter.ImportResult result =
				DifferenceChartDropImporter.importCharts(selected, List.of(bms, bmson));

		assertEquals(songFolder.toAbsolutePath(), result.targetDirectory());
		assertEquals(List.of(songFolder.resolve("another.bms"), songFolder.resolve("extra.BMSON")),
				result.importedFiles());
		assertArrayEquals(Files.readAllBytes(bms), Files.readAllBytes(songFolder.resolve("another.bms")));
		assertArrayEquals(Files.readAllBytes(bmson), Files.readAllBytes(songFolder.resolve("extra.BMSON")));
		assertTrue(Files.exists(bms));
		assertTrue(Files.exists(bmson));
	}

	@Test
	void rejectsAnExistingDestinationWithoutImportingAnyPartOfTheBatch() throws Exception {
		Path songFolder = Files.createDirectories(temporary.resolve("songs/Selected"));
		Path selected = write(songFolder.resolve("normal.bms"), "selected");
		write(songFolder.resolve("existing.bms"), "keep");
		Path sourceFolder = Files.createDirectories(temporary.resolve("downloads"));
		Path first = write(sourceFolder.resolve("new.bms"), "new");
		Path collision = write(sourceFolder.resolve("EXISTING.BMS"), "replace");

		ImportException error = assertThrows(ImportException.class,
				() -> DifferenceChartDropImporter.importCharts(selected, List.of(first, collision)));

		assertEquals(Failure.DESTINATION_EXISTS, error.failure());
		assertFalse(Files.exists(songFolder.resolve("new.bms")));
		assertEquals("keep", Files.readString(songFolder.resolve("existing.bms")));
	}

	@Test
	void rejectsDuplicateDestinationNamesAcrossOneBatch() throws Exception {
		Path songFolder = Files.createDirectories(temporary.resolve("songs/Selected"));
		Path selected = write(songFolder.resolve("normal.bms"), "selected");
		Path first = write(Files.createDirectories(temporary.resolve("one")).resolve("same.pms"), "one");
		Path second = write(Files.createDirectories(temporary.resolve("two")).resolve("SAME.PMS"), "two");

		ImportException error = assertThrows(ImportException.class,
				() -> DifferenceChartDropImporter.importCharts(selected, List.of(first, second)));

		assertEquals(Failure.DUPLICATE_FILENAME, error.failure());
		assertFalse(Files.exists(songFolder.resolve("same.pms")));
	}

	@Test
	void rejectsDirectoriesAndUnsupportedFilesWithoutCopyingThem() throws Exception {
		Path songFolder = Files.createDirectories(temporary.resolve("songs/Selected"));
		Path selected = write(songFolder.resolve("normal.bms"), "selected");
		Path directory = Files.createDirectories(temporary.resolve("pack"));
		Path zip = write(temporary.resolve("song.zip"), "zip");

		ImportException directoryError = assertThrows(ImportException.class,
				() -> DifferenceChartDropImporter.importCharts(selected, List.of(directory)));
		ImportException zipError = assertThrows(ImportException.class,
				() -> DifferenceChartDropImporter.importCharts(selected, List.of(zip)));

		assertEquals(Failure.SOURCE_NOT_FILE, directoryError.failure());
		assertEquals(Failure.UNSUPPORTED_TYPE, zipError.failure());
		assertFalse(Files.exists(songFolder.resolve("song.zip")));
	}

	@Test
	void rejectsAnArchiveBackedSelectedChart() throws Exception {
		Path archive = write(temporary.resolve("song.zip"), "archive");
		Path virtualChart = SongArchives.virtualPath(archive, "chart.bms");
		Path difference = write(temporary.resolve("difference.bms"), "difference");

		ImportException error = assertThrows(ImportException.class,
				() -> DifferenceChartDropImporter.importCharts(virtualChart, List.of(difference)));

		assertEquals(Failure.ARCHIVE_SELECTED, error.failure());
		assertFalse(Files.exists(temporary.resolve("chart.bms")));
	}

	private Path write(Path path, String contents) throws Exception {
		Files.write(path, contents.getBytes(StandardCharsets.UTF_8));
		return path;
	}
}
