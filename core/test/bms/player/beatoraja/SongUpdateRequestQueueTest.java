package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class SongUpdateRequestQueueTest {

	@Test
	void equivalentPendingScansCoalesceAndKeepEveryCompletion() {
		SongUpdateRequestQueue queue = new SongUpdateRequestQueue();
		AtomicInteger completions = new AtomicInteger();

		SongUpdateRequestQueue.Request first = queue.enqueue("/songs/downloads", false,
				completions::incrementAndGet);
		SongUpdateRequestQueue.Request second = queue.enqueue("/songs/downloads", false,
				completions::incrementAndGet);

		assertSame(first, second);
		assertEquals(1, queue.size());
		SongUpdateRequestQueue.Request pending = queue.poll();
		pending.completions().forEach(Runnable::run);
		assertEquals(2, completions.get());
	}

	@Test
	void distinctScanScopesRemainOrdered() {
		SongUpdateRequestQueue queue = new SongUpdateRequestQueue();

		SongUpdateRequestQueue.Request first = queue.enqueue("/songs/downloads", false, null);
		SongUpdateRequestQueue.Request second = queue.enqueue("/songs/downloads", true, null);

		assertNotSame(first, second);
		assertEquals(2, queue.size());
		assertSame(first, queue.poll());
		assertSame(second, queue.poll());
	}
}
