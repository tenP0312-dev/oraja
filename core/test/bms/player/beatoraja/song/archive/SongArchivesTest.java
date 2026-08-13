package bms.player.beatoraja.song.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;

import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;

class SongArchivesTest {

	// Junrar's tiny public test fixture: foo/bar.txt contains "baz\n".
	private static final String SIMPLE_RAR =
			"UmFyIRoHAM+QcwAADQAAAAAAAAB8zXQgkC0ADQAAAAQAAAAD4Tl7zCeTJEEdMwsAtIEAAGZvb1xiYXIudHh0AMAACL8IrvLDGH6f/ZLdiiN04IAjAAAAAAAAAAAAAwAAAAAnkyRBFDADAP1BAABmb2/EPXsAQAcA";
	// Junrar v8.1.0 public RAR5 test fixture under the project's UnRAR-derived
	// distribution terms. It contains only FILE1.TXT and FILE2.TXT and must not
	// be used to develop a RAR-compatible compressor.
	private static final String SIMPLE_RAR5 =
			"UmFyIRoHAQDz4YLrCwEFBwAGAQGAgIAATS800SUCAwuHAASHACC6fRl6gAAACUZJTEUxLlRYVAoDAgDwWYPlessBZmlsZTENCqOo3u8lAgMLhwAEhwAg48NfeIAAAAlGSUxFMi5UWFQKAwIAd+2G5XrLAWZpbGUyDQodd1ZRAwUEAA==";

	@TempDir
	Path temporary;

	@Test
	void readsZipEntriesAsVirtualResourcesWithoutChangingTheArchive() throws Exception {
		Path archive = temporary.resolve("song.zip");
		writeZip(archive,
				"Pack/chart.bms", "#PLAYER 1\n#TITLE Archive\n#WAV01 ../shared.wav\n#00111:01\n",
				"Pack/preview.ogg", "preview",
				"shared.wav", "audio");
		byte[] original = Files.readAllBytes(archive);

		SongArchives.ArchiveContents contents = SongArchives.readContents(archive);
		assertEquals(null, contents.rootDirectory());
		Path chartPath = SongArchives.virtualPath(archive, "Pack/chart.bms");
		SongResource chart = SongResources.fromPath(chartPath);

		assertEquals("#PLAYER 1\n#TITLE Archive\n#WAV01 ../shared.wav\n#00111:01\n",
				new String(chart.openStream().readAllBytes(), StandardCharsets.UTF_8));
		assertEquals("audio", new String(chart.parent().resolve("../shared.wav").openStream().readAllBytes(),
				StandardCharsets.UTF_8));
		assertEquals(archive, SongArchives.archivePath(Path.of(
				SongArchives.virtualRoot(archive) + String.valueOf(File.separatorChar))));
		assertArrayEquals(original, Files.readAllBytes(archive));
	}

	@Test
	void hidesOneSharedTopLevelDirectoryInVirtualPaths() throws Exception {
		Path archive = temporary.resolve("pack.zip");
		writeZip(archive,
				"Pack/chart.bms", "#TITLE Root",
				"Pack/sound.wav", "audio");

		SongArchives.ArchiveContents contents = SongArchives.readContents(archive);
		assertEquals("Pack", contents.rootDirectory());
		Path chartPath = SongArchives.virtualPath(archive, "Pack/chart.bms", contents.rootDirectory());
		assertTrue(chartPath.toString().contains("pack.zip!-Pack"));
		assertEquals("audio", new String(
				SongResources.fromPath(chartPath).parent().resolve("sound.wav").openStream().readAllBytes(),
				StandardCharsets.UTF_8));
		assertEquals(archive, SongArchives.archivePath(Path.of(
				SongArchives.virtualRoot(archive, contents.rootDirectory()) + String.valueOf(File.separatorChar))));
	}

	@Test
	void readsLegacyCp932ZipEntryNames() throws Exception {
		Path archive = temporary.resolve("legacy.zip");
		try (org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream output =
				new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(archive)) {
			output.setEncoding("MS932");
			output.setUseLanguageEncodingFlag(false);
			var entry = new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("楽曲/譜面.bms");
			output.putArchiveEntry(entry);
			output.write("#TITLE Legacy".getBytes(StandardCharsets.UTF_8));
			output.closeArchiveEntry();
		}

		assertEquals(java.util.List.of("楽曲/譜面.bms"), SongArchives.listEntries(archive));
		SongResource chart = SongResources.fromPath(SongArchives.virtualPath(archive, "楽曲/譜面.bms"));
		assertEquals("#TITLE Legacy",
				new String(chart.openStream().readAllBytes(), StandardCharsets.UTF_8));
	}

	@Test
	void resolvesEntryNamesCaseInsensitivelyLikeAWindowsSongFolder() throws Exception {
		Path archive = temporary.resolve("case.zip");
		writeZip(archive,
				"Pack/chart.bms", "#WAV01 SOUND.WAV",
				"Pack/Sound.wav", "audio");

		SongResource chart = SongResources.fromPath(
				SongArchives.virtualPath(archive, "Pack/chart.bms"));
		assertEquals("audio", new String(
				chart.parent().resolve("SOUND.WAV").openStream().readAllBytes(),
				StandardCharsets.UTF_8));
	}

	@Test
	void rejectsEntryNamesThatAreAmbiguousOnWindows() throws Exception {
		Path archive = temporary.resolve("ambiguous.zip");
		writeZip(archive,
				"Pack/Sound.wav", "one",
				"Pack/sound.wav", "two");

		IOException error = assertThrows(IOException.class, () -> SongArchives.listEntries(archive));
		assertTrue(error.getMessage().contains("canonically ambiguous"));
	}

	@Test
	void resolvesUnicodeEquivalentEntryNamesAndRejectsCanonicalCollisions() throws Exception {
		String nfc = "Pack/が.wav";
		String nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD);
		Path archive = temporary.resolve("unicode.zip");
		writeZip(archive, nfd, "audio");

		SongResource entry = SongResources.fromPath(SongArchives.virtualPath(archive, nfc));
		assertTrue(entry.exists());
		assertEquals("audio", new String(entry.openStream().readAllBytes(), StandardCharsets.UTF_8));

		Path ambiguous = temporary.resolve("unicode-ambiguous.zip");
		writeZip(ambiguous, nfc, "one", nfd, "two");
		IOException error = assertThrows(IOException.class, () -> SongArchives.listEntries(ambiguous));
		assertTrue(error.getMessage().contains("canonically ambiguous"));
	}

	@Test
	void readsRarEntriesAndRejectsEscapes() throws Exception {
		Path archive = temporary.resolve("song.rar");
		Files.write(archive, Base64.getDecoder().decode(SIMPLE_RAR));

		assertEquals(java.util.List.of("foo/bar.txt"), SongArchives.listEntries(archive));
		SongResource entry = SongResources.fromPath(SongArchives.virtualPath(archive, "foo/bar.txt"));
		assertEquals("baz\n", new String(entry.openStream().readAllBytes(), StandardCharsets.UTF_8));
		assertThrows(IllegalArgumentException.class, () -> entry.parent().resolve("../../outside.wav"));
	}

	@Test
	void readsRar5EntriesWithoutExtraction() throws Exception {
		Path archive = temporary.resolve("song-rar5.rar");
		Files.write(archive, Base64.getDecoder().decode(SIMPLE_RAR5));

		assertEquals(java.util.List.of("FILE1.TXT", "FILE2.TXT"), SongArchives.listEntries(archive));
		SongResource entry = SongResources.fromPath(SongArchives.virtualPath(archive, "file1.txt"));
		assertEquals("file1\r\n", new String(entry.openStream().readAllBytes(), StandardCharsets.UTF_8));
	}

	@Test
	void readsSevenZipEntriesWithoutExtraction() throws Exception {
		Path archive = temporary.resolve("song.7z");
		writeSevenZip(archive, "Pack/chart.bms", "#TITLE Seven Zip", "Pack/sound.wav", "audio");

		assertEquals(java.util.List.of("Pack/chart.bms", "Pack/sound.wav"), SongArchives.listEntries(archive));
		SongResource chart = SongResources.fromPath(SongArchives.virtualPath(archive, "Pack/chart.bms"));
		assertEquals("#TITLE Seven Zip",
				new String(chart.openStream().readAllBytes(), StandardCharsets.UTF_8));
	}

	@Test
	void choosesArchiveBackendBySignatureWhenSupportedSuffixIsWrong() throws Exception {
		Path archive = temporary.resolve("zip-named-rar.rar");
		writeZip(archive, "Pack/chart.bms", "#TITLE Signature");

		assertEquals(java.util.List.of("Pack/chart.bms"), SongArchives.listEntries(archive));
	}

	@Test
	void invalidatesEntryCacheWhenSizeAndTimestampArePreserved() throws Exception {
		Path archive = temporary.resolve("replaced.zip");
		FileTime timestamp = FileTime.fromMillis(1_700_000_000_000L);
		writeZip(archive, "Pack/a.bms", "#TITLE A");
		Files.setLastModifiedTime(archive, timestamp);
		long size = Files.size(archive);
		assertEquals(java.util.List.of("Pack/a.bms"), SongArchives.listEntries(archive));

		writeZip(archive, "Pack/b.bms", "#TITLE B");
		assertEquals(size, Files.size(archive));
		Files.setLastModifiedTime(archive, timestamp);

		assertEquals(java.util.List.of("Pack/b.bms"), SongArchives.listEntries(archive));
	}

	@Test
	void rejectsUnsafeZipEntryNames() throws Exception {
		Path archive = temporary.resolve("unsafe.zip");
		writeZip(archive, "../chart.bms", "#TITLE Unsafe");

		IOException error = assertThrows(IOException.class, () -> SongArchives.listEntries(archive));
		assertTrue(error.getMessage().contains("Unsafe ZIP entry"));
		assertFalse(SongArchives.isVirtualPath(archive));
	}

	private static void writeZip(Path archive, String... entries) throws IOException {
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
			for (int index = 0; index < entries.length; index += 2) {
				output.putNextEntry(new ZipEntry(entries[index]));
				output.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
				output.closeEntry();
			}
		}
	}

	private static void writeSevenZip(Path archive, String... entries) throws IOException {
		try (SevenZOutputFile output = new SevenZOutputFile(archive.toFile())) {
			for (int index = 0; index < entries.length; index += 2) {
				byte[] contents = entries[index + 1].getBytes(StandardCharsets.UTF_8);
				SevenZArchiveEntry entry = new SevenZArchiveEntry();
				entry.setName(entries[index]);
				entry.setSize(contents.length);
				output.putArchiveEntry(entry);
				output.write(contents);
				output.closeArchiveEntry();
			}
		}
	}
}
