package bms.player.beatoraja.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;
import bms.player.beatoraja.song.archive.SongArchives;

class SolidSevenZipAudioTest {

	@TempDir
	Path temporary;

	@Test
	void materializesSolidSevenZipOnceBeforeParallelPcmDecode() throws Exception {
		// The fixture contains 31 copies of a short OGG generated from the
		// repository-owned defaultsound/guide-pg.wav and packed as one solid block.
		Path archive = writeFixture();
		List<String> entries = IntStream.range(0, 31)
				.mapToObj(index -> "sound_%03d.ogg".formatted(index))
				.toList();
		assertEquals(entries, SongArchives.listEntries(archive));
		List<SongResource> resources = entries.stream()
				.map(name -> SongResources.fromPath(SongArchives.virtualPath(archive, name)))
				.toList();

		List<Path> materializedPaths;
		try (SongResources.MaterializedBatch batch = SongResources.materializeBatch(resources)) {
			materializedPaths = resources.stream().map(batch::pathFor).toList();
			assertTrue(materializedPaths.stream().allMatch(Files::isRegularFile));
			long loaded = resources.parallelStream()
					.map(resource -> SongResources.local(batch.pathFor(resource)))
					.map(resource -> {
						try {
							return PcmTestSupport.load(resource, 44_100);
						} catch (Exception error) {
							return null;
						}
					})
					.filter(Objects::nonNull)
					.count();
			assertEquals(entries.size(), loaded);
		}
		assertTrue(materializedPaths.stream().noneMatch(Files::exists));
		assertFalse(Files.exists(temporary.resolve("sound_000.ogg")));
	}

	@Test
	void readsLaterSolidSevenZipEntriesConcurrently() throws Exception {
		Path archive = writeFixture();
		SongResource resource = SongResources.fromPath(
				SongArchives.virtualPath(archive, "sound_030.ogg"));
		byte[] expected;
		try (InputStream input = resource.openStream()) {
			expected = input.readAllBytes();
		}

		int readerCount = 32;
		ExecutorService executor = Executors.newFixedThreadPool(readerCount);
		CountDownLatch ready = new CountDownLatch(readerCount);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<byte[]>> reads = IntStream.range(0, readerCount)
					.mapToObj(ignored -> executor.submit(() -> {
						ready.countDown();
						start.await();
						try (InputStream input = resource.openStream()) {
							return input.readAllBytes();
						}
					}))
					.toList();
			assertTrue(ready.await(10, TimeUnit.SECONDS), "solid 7z readers did not start together");
			start.countDown();
			for (Future<byte[]> read : reads) {
				assertArrayEquals(expected, read.get(30, TimeUnit.SECONDS));
			}
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	private Path writeFixture() throws Exception {
		Path archive = temporary.resolve("solid-audio.7z");
		try (InputStream encoded = Objects.requireNonNull(
				getClass().getResourceAsStream("/solid-sevenzip-audio.7z.b64"))) {
			Files.write(archive, Base64.getMimeDecoder().decode(encoded.readAllBytes()));
		}
		return archive;
	}
}
