package bms.player.beatoraja.song;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongResourcesTest {

	@TempDir
	Path temporary;

	@Test
	void removesOnlyOwnedTemporaryFilesOlderThanOneDay() throws Exception {
		Instant now = Instant.parse("2026-08-14T00:00:00Z");
		Path stale = Files.writeString(temporary.resolve("beatoraja-song-resource-stale.wav"), "stale");
		Path recent = Files.writeString(temporary.resolve("beatoraja-song-resource-recent.wav"), "recent");
		Path unrelated = Files.writeString(temporary.resolve("other-stale.wav"), "unrelated");
		Path liveProcess = Files.writeString(temporary.resolve(
				"beatoraja-song-resource-" + ProcessHandle.current().pid() + "-active.wav"), "active");
		Files.setLastModifiedTime(stale, FileTime.from(now.minusSeconds(48 * 60 * 60)));
		Files.setLastModifiedTime(recent, FileTime.from(now.minusSeconds(60 * 60)));
		Files.setLastModifiedTime(unrelated, FileTime.from(now.minusSeconds(48 * 60 * 60)));
		Files.setLastModifiedTime(liveProcess, FileTime.from(now.minusSeconds(48 * 60 * 60)));

		assertEquals(1, SongResources.cleanupStaleMaterializedFiles(temporary, now));
		assertFalse(Files.exists(stale));
		assertTrue(Files.exists(recent));
		assertTrue(Files.exists(unrelated));
		assertTrue(Files.exists(liveProcess));
	}
}
