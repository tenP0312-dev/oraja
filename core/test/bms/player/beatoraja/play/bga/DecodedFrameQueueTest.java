package bms.player.beatoraja.play.bga;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DecodedFrameQueueTest {
    static DecodedFrameQueue.Allocator heap(AtomicInteger closed) {
        return bytes -> new DecodedFrameQueue.Allocation() {
            private final ByteBuffer pixels = ByteBuffer.allocate(bytes);
            public ByteBuffer pixels() { return pixels; }
            public void close() { closed.incrementAndGet(); }
        };
    }
    static void put(DecodedFrameQueue queue, long generation, long pts) {
        var slot = queue.acquireWrite();
        assertNotNull(slot);
        slot.pixels.put(0, (byte) pts);
        queue.publish(slot, generation, pts);
    }
    @Test void selectsLatestNotFutureAndReturnsLeases() {
        var metrics = new BgaPerformanceMetrics(true);
        var closed = new AtomicInteger();
        var queue = new DecodedFrameQueue(3, 1, 1, metrics, heap(closed));
        put(queue, 7, 10); put(queue, 7, 20); put(queue, 7, 30);
        var frame = queue.pollLatestAtOrBefore(25, 7);
        assertEquals(20, frame.ptsUs);
        assertEquals(1, queue.depth());
        queue.release(frame);
        assertNull(queue.pollLatestAtOrBefore(29, 7));
        frame = queue.pollLatestAtOrBefore(30, 7);
        assertEquals(30, frame.ptsUs);
        queue.release(frame);
        assertEquals(1, metrics.count(BgaPerformanceMetrics.Counter.DROPPED_LATE));
        queue.close(); assertEquals(3, closed.get());
    }
    @Test void retryRejectsAllOldFramesIncludingFuturePts() {
        var queue = new DecodedFrameQueue(3, 1, 1, new BgaPerformanceMetrics(false), heap(new AtomicInteger()));
        put(queue, 1, 0); put(queue, 1, 900); put(queue, 2, 0);
        var frame = queue.pollLatestAtOrBefore(0, 2);
        assertEquals(2, frame.generation);
        assertEquals(0, frame.ptsUs);
        queue.release(frame);
        assertEquals(0, queue.depth()); queue.close();
    }
    @Test void fullQueueNeverOverwritesConsumerPixels() {
        var metrics = new BgaPerformanceMetrics(true);
        var queue = new DecodedFrameQueue(2, 1, 1, metrics, heap(new AtomicInteger()));
        put(queue, 1, 1); put(queue, 1, 2);
        var held = queue.pollLatestAtOrBefore(1, 1);
        for (int n = 3; n < 100; n++) put(queue, 1, n);
        assertEquals(1, held.pixels.get(0));
        assertEquals(97, metrics.count(BgaPerformanceMetrics.Counter.DROPPED_FULL));
        queue.release(held); queue.close();
    }
    @Test void decoderCloseWaitsForLeaseButConsumerNeverWaits() throws Exception {
        var closed = new AtomicInteger();
        var queue = new DecodedFrameQueue(2, 1, 1, new BgaPerformanceMetrics(false), heap(closed));
        put(queue, 1, 0);
        var held = queue.pollLatestAtOrBefore(0, 1);
        var entered = new CountDownLatch(1);
        Thread worker = new Thread(() -> { entered.countDown(); queue.close(); });
        worker.start(); assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
            for (int i = 0; i < 1000; i++) queue.pollLatestAtOrBefore(0, 1);
        });
        assertEquals(0, closed.get());
        queue.release(held);
        worker.join(2000); assertFalse(worker.isAlive()); assertEquals(2, closed.get());
    }
    @Test void concurrentPublicationKeepsPixelsAndMetadataTogether() throws Exception {
        var queue = new DecodedFrameQueue(3, 1, 1, new BgaPerformanceMetrics(false), heap(new AtomicInteger()));
        var failure = new AtomicReference<Throwable>();
        var done = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            try {
                for (long n = 1; n <= 10_000; n++) {
                    var slot = queue.acquireWrite();
                    if (slot == null) continue;
                    slot.pixels.put(0, (byte) n);
                    queue.publish(slot, 1, n);
                }
            } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        producer.start();
        while (done.getCount() != 0 || queue.depth() != 0) {
            var frame = queue.pollLatestAtOrBefore(Long.MAX_VALUE, 1);
            if (frame != null) {
                try { assertEquals((byte) frame.ptsUs, frame.pixels.get(0)); }
                finally { queue.release(frame); }
            }
            Thread.yield();
        }
        producer.join(2000); assertNull(failure.get()); queue.close();
    }
}
