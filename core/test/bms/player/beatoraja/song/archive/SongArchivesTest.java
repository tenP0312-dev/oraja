package bms.player.beatoraja.song.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;

class SongArchivesTest {

	// Junrar's tiny public test fixture: foo/bar.txt contains "baz\n".
	private static final String SIMPLE_RAR =
			"UmFyIRoHAM+QcwAADQAAAAAAAAB8zXQgkC0ADQAAAAQAAAAD4Tl7zCeTJEEdMwsAtIEAAGZvb1xiYXIudHh0AMAACL8IrvLDGH6f/ZLdiiN04IAjAAAAAAAAAAAAAwAAAAAnkyRBFDADAP1BAABmb2/EPXsAQAcA";

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
		assertTrue(error.getMessage().contains("differ only by case"));
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
}
