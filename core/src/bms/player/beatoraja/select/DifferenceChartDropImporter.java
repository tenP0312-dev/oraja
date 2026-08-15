package bms.player.beatoraja.select;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import bms.player.beatoraja.song.archive.SongArchives;

/** Copies loose difference-chart files beside one selected physical chart. */
public final class DifferenceChartDropImporter {

	private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
			".bms", ".bme", ".bml", ".pms", ".bmson");

	private DifferenceChartDropImporter() {
	}

	public static ImportResult importCharts(Path selectedChart, List<Path> droppedFiles)
			throws ImportException {
		if (selectedChart == null) {
			throw new ImportException(Failure.SELECTED_CHART_UNAVAILABLE, null);
		}
		if (SongArchives.isVirtualPath(selectedChart)) {
			throw new ImportException(Failure.ARCHIVE_SELECTED, selectedChart);
		}

		Path localChart = selectedChart.toAbsolutePath().normalize();
		if (!Files.isRegularFile(localChart)) {
			throw new ImportException(Failure.SELECTED_CHART_UNAVAILABLE, localChart);
		}
		Path targetDirectory = localChart.getParent();
		if (targetDirectory == null || !Files.isDirectory(targetDirectory)
				|| !Files.isWritable(targetDirectory)) {
			throw new ImportException(Failure.TARGET_NOT_WRITABLE, targetDirectory);
		}
		if (droppedFiles == null || droppedFiles.isEmpty()) {
			throw new ImportException(Failure.NO_FILES, null);
		}

		Set<String> occupiedNames = readOccupiedNames(targetDirectory);
		Set<String> batchNames = new HashSet<>();
		List<PendingCopy> pending = new ArrayList<>();
		for (Path droppedFile : droppedFiles) {
			if (droppedFile == null) {
				throw new ImportException(Failure.SOURCE_NOT_FILE, null);
			}
			Path source = droppedFile.toAbsolutePath().normalize();
			if (!Files.isRegularFile(source)) {
				throw new ImportException(Failure.SOURCE_NOT_FILE, source);
			}
			Path fileName = source.getFileName();
			if (fileName == null || !isSupportedChart(fileName.toString())) {
				throw new ImportException(Failure.UNSUPPORTED_TYPE, source);
			}

			String canonicalName = canonicalName(fileName.toString());
			if (occupiedNames.contains(canonicalName)) {
				throw new ImportException(Failure.DESTINATION_EXISTS, targetDirectory.resolve(fileName));
			}
			if (!batchNames.add(canonicalName)) {
				throw new ImportException(Failure.DUPLICATE_FILENAME, fileName);
			}
			pending.add(new PendingCopy(source, targetDirectory.resolve(fileName)));
		}

		List<Path> stagedFiles = new ArrayList<>();
		List<Path> importedFiles = new ArrayList<>();
		try {
			for (PendingCopy copy : pending) {
				Path staged = Files.createTempFile(targetDirectory, ".oraja-drop-", ".part");
				stagedFiles.add(staged);
				Files.copy(copy.source(), staged, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				copy.staged(staged);
			}
			for (PendingCopy copy : pending) {
				Files.move(copy.staged(), copy.destination());
				stagedFiles.remove(copy.staged());
				importedFiles.add(copy.destination());
			}
		} catch (IOException exception) {
			rollback(importedFiles, stagedFiles, exception);
			throw new ImportException(Failure.IO_FAILURE, targetDirectory, exception);
		}

		return new ImportResult(targetDirectory, List.copyOf(importedFiles));
	}

	private static Set<String> readOccupiedNames(Path targetDirectory) throws ImportException {
		Set<String> names = new HashSet<>();
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(targetDirectory)) {
			for (Path entry : entries) {
				Path fileName = entry.getFileName();
				if (fileName != null) {
					names.add(canonicalName(fileName.toString()));
				}
			}
		} catch (IOException exception) {
			throw new ImportException(Failure.TARGET_NOT_WRITABLE, targetDirectory, exception);
		}
		return names;
	}

	private static boolean isSupportedChart(String fileName) {
		String lowerName = fileName.toLowerCase(Locale.ROOT);
		return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
	}

	private static String canonicalName(String fileName) {
		return Normalizer.normalize(fileName, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
	}

	private static void rollback(List<Path> importedFiles, List<Path> stagedFiles, IOException cause) {
		for (Path path : importedFiles) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException rollbackFailure) {
				cause.addSuppressed(rollbackFailure);
			}
		}
		for (Path path : stagedFiles) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException rollbackFailure) {
				cause.addSuppressed(rollbackFailure);
			}
		}
	}

	public enum Failure {
		NO_FILES,
		SELECTED_CHART_UNAVAILABLE,
		ARCHIVE_SELECTED,
		TARGET_NOT_WRITABLE,
		SOURCE_NOT_FILE,
		UNSUPPORTED_TYPE,
		DESTINATION_EXISTS,
		DUPLICATE_FILENAME,
		IO_FAILURE
	}

	public static final class ImportException extends Exception {
		private final Failure failure;
		private final Path subject;

		private ImportException(Failure failure, Path subject) {
			this(failure, subject, null);
		}

		private ImportException(Failure failure, Path subject, Throwable cause) {
			super(failure + (subject == null ? "" : ": " + subject), cause);
			this.failure = failure;
			this.subject = subject;
		}

		public Failure failure() {
			return failure;
		}

		public Path subject() {
			return subject;
		}
	}

	public record ImportResult(Path targetDirectory, List<Path> importedFiles) {
	}

	private static final class PendingCopy {
		private final Path source;
		private final Path destination;
		private Path staged;

		private PendingCopy(Path source, Path destination) {
			this.source = source;
			this.destination = destination;
		}

		private Path source() {
			return source;
		}

		private Path destination() {
			return destination;
		}

		private Path staged() {
			return staged;
		}

		private void staged(Path staged) {
			this.staged = staged;
		}
	}
}
