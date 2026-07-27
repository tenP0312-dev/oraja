package bms.player.beatoraja.play.bga;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FFmpegNativeLoaderTest {
	@Test
	void nativeLoadRunsOnceAcrossConcurrentCallers() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		FFmpegNativeLoader.NativePreloader preloader =
				new FFmpegNativeLoader.NativePreloader(() -> {
					calls.incrementAndGet();
					entered.countDown();
					assertTrue(release.await(2, TimeUnit.SECONDS));
				});
		AtomicBoolean firstResult = new AtomicBoolean();
		AtomicBoolean secondResult = new AtomicBoolean();

		Thread first = new Thread(() -> firstResult.set(preloader.preload()));
		Thread second = new Thread(() -> secondResult.set(preloader.preload()));
		first.start();
		assertTrue(entered.await(2, TimeUnit.SECONDS));
		second.start();
		release.countDown();
		first.join(2_000);
		second.join(2_000);

		assertFalse(first.isAlive());
		assertFalse(second.isAlive());
		assertTrue(firstResult.get());
		assertTrue(secondResult.get());
		assertEquals(1, calls.get());
		assertEquals(FFmpegNativeLoader.State.READY, preloader.state());
	}

	@Test
	void failedNativeLoadIsNotRetriedInTheSameJvm() {
		AtomicInteger calls = new AtomicInteger();
		IllegalStateException failure = new IllegalStateException("native load failed");
		FFmpegNativeLoader.NativePreloader preloader =
				new FFmpegNativeLoader.NativePreloader(() -> {
					calls.incrementAndGet();
					throw failure;
				});

		assertFalse(preloader.preload());
		assertFalse(preloader.preload());
		assertEquals(1, calls.get());
		assertEquals(FFmpegNativeLoader.State.FAILED, preloader.state());
		assertSame(failure, preloader.failure());
	}

	@Test
	void preexistingInterruptIsClearedForNativeLoadAndThenRestored() throws Exception {
		AtomicBoolean interruptedDuringLoad = new AtomicBoolean(true);
		AtomicBoolean result = new AtomicBoolean();
		AtomicBoolean interruptRestored = new AtomicBoolean();
		FFmpegNativeLoader.NativePreloader preloader =
				new FFmpegNativeLoader.NativePreloader(
						() -> interruptedDuringLoad.set(Thread.currentThread().isInterrupted())
				);

		Thread caller = new Thread(() -> {
			Thread.currentThread().interrupt();
			result.set(preloader.preload());
			interruptRestored.set(Thread.currentThread().isInterrupted());
		});
		caller.start();
		caller.join(2_000);

		assertFalse(caller.isAlive());
		assertTrue(result.get());
		assertFalse(interruptedDuringLoad.get());
		assertTrue(interruptRestored.get());
	}
}
