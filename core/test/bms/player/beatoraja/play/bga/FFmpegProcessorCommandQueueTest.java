package bms.player.beatoraja.play.bga;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FFmpegProcessorCommandQueueTest {
	@Test
	void waitingDecoderReceivesPlayWithoutThreadInterrupt() throws Exception {
		FFmpegProcessor.MovieCommandQueue queue = new FFmpegProcessor.MovieCommandQueue();
		AtomicReference<FFmpegProcessor.Command> received = new AtomicReference<>();
		CountDownLatch waiting = new CountDownLatch(1);

		Thread decoder = new Thread(() -> {
			waiting.countDown();
			try {
				received.set(queue.take());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		decoder.start();
		assertTrue(waiting.await(2, TimeUnit.SECONDS));

		queue.submit(FFmpegProcessor.Command.PLAY);
		decoder.join(2_000);

		assertFalse(decoder.isAlive());
		assertFalse(decoder.isInterrupted());
		assertEquals(FFmpegProcessor.Command.PLAY, received.get());
	}

	@Test
	void playLoopAndStopRemainOrderedDuringInitialization() {
		FFmpegProcessor.MovieCommandQueue queue = new FFmpegProcessor.MovieCommandQueue();

		queue.submit(FFmpegProcessor.Command.PLAY);
		queue.submit(FFmpegProcessor.Command.LOOP);
		queue.submit(FFmpegProcessor.Command.STOP);

		assertEquals(FFmpegProcessor.Command.PLAY, queue.poll());
		assertEquals(FFmpegProcessor.Command.LOOP, queue.poll());
		assertEquals(FFmpegProcessor.Command.STOP, queue.poll());
		assertNull(queue.poll());
	}

	@Test
	void haltReplacesPendingCommandsAndRejectsLaterPlayback() {
		FFmpegProcessor.MovieCommandQueue queue = new FFmpegProcessor.MovieCommandQueue();
		queue.submit(FFmpegProcessor.Command.PLAY);
		queue.submit(FFmpegProcessor.Command.LOOP);

		queue.submit(FFmpegProcessor.Command.HALT);
		queue.submit(FFmpegProcessor.Command.PLAY);

		assertEquals(1, queue.size());
		assertEquals(FFmpegProcessor.Command.HALT, queue.poll());
		assertNull(queue.poll());
	}

	@Test
	void burstLargerThanThePreviousCapacityIsNotDropped() {
		FFmpegProcessor.MovieCommandQueue queue = new FFmpegProcessor.MovieCommandQueue();

		for (int i = 0; i < 64; i++) {
			queue.submit(FFmpegProcessor.Command.PLAY);
		}

		assertEquals(64, queue.size());
		for (int i = 0; i < 64; i++) {
			assertEquals(FFmpegProcessor.Command.PLAY, queue.poll());
		}
		assertNull(queue.poll());
	}
}
