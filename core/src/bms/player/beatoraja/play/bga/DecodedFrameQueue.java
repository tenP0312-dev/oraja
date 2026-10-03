package bms.player.beatoraja.play.bga;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.bytedeco.javacpp.BytePointer;

/** Fixed leases: one decoder producer, one GL consumer; neither shares a pixel lock. */
final class DecodedFrameQueue implements AutoCloseable {
    private static final int FREE = 0, WRITING = 1, READY = 2, READING = 3;
    interface Allocation extends AutoCloseable {
        ByteBuffer pixels();
        void close();
    }
    interface Allocator { Allocation allocate(int bytes); }
    static final Allocator NATIVE = bytes -> {
        BytePointer pointer = new BytePointer(bytes);
        ByteBuffer buffer = pointer.asByteBuffer();
        return new Allocation() {
            public ByteBuffer pixels() { return buffer; }
            public void close() { pointer.close(); }
        };
    };
    static final class Slot {
        final AtomicInteger state = new AtomicInteger();
        final Allocation allocation;
        final ByteBuffer pixels;
        long generation, ptsUs;
        Slot(Allocation allocation) { this.allocation = allocation; pixels = allocation.pixels(); }
    }
    final int width, height;
    private final Slot[] slots;
    private final BgaPerformanceMetrics metrics;
    private volatile boolean retired;

    DecodedFrameQueue(int length, int width, int height, BgaPerformanceMetrics metrics, Allocator allocator) {
        if (length < 1 || length > 4 || width < 1 || height < 1) throw new IllegalArgumentException();
        this.width = width;
        this.height = height;
        this.metrics = metrics;
        slots = new Slot[length];
        try {
            int size = Math.toIntExact((long) width * height * 3);
            for (int i = 0; i < length; i++) slots[i] = new Slot(allocator.allocate(size));
        } catch (RuntimeException | Error error) {
            for (Slot slot : slots) if (slot != null) slot.allocation.close();
            throw error;
        }
    }
    /** Decoder only. A full queue evicts an unread oldest frame, never a consumer lease. */
    Slot acquireWrite() {
        if (retired) return null;
        for (Slot slot : slots) if (slot.state.compareAndSet(FREE, WRITING)) return slot;
        Slot oldest = null;
        for (Slot slot : slots) {
            if (slot.state.get() == READY && (oldest == null || slot.ptsUs < oldest.ptsUs)) oldest = slot;
        }
        if (oldest != null && oldest.state.compareAndSet(READY, WRITING)) {
            metrics.increment(BgaPerformanceMetrics.Counter.DROPPED_FULL);
            return oldest;
        }
        return null;
    }
    void publish(Slot slot, long generation, long ptsUs) {
        slot.generation = generation;
        slot.ptsUs = ptsUs;
        slot.state.set(retired ? FREE : READY);
    }
    void cancelWrite(Slot slot) { slot.state.set(FREE); }
    /** GL only. At most four CAS operations; returns the latest available PTS <= target. */
    Slot pollLatestAtOrBefore(long targetUs, long generation) {
        if (retired) return null;
        Slot best = null;
        for (Slot slot : slots) {
            if (!slot.state.compareAndSet(READY, READING)) continue;
            if (slot.generation != generation || slot.ptsUs < 0) {
                release(slot);
                metrics.increment(BgaPerformanceMetrics.Counter.DROPPED_LATE);
            } else if (slot.ptsUs > targetUs) {
                slot.state.set(READY);
            } else if (best == null || slot.ptsUs > best.ptsUs) {
                if (best != null) {
                    release(best);
                    metrics.increment(BgaPerformanceMetrics.Counter.DROPPED_LATE);
                }
                best = slot;
            } else {
                release(slot);
                metrics.increment(BgaPerformanceMetrics.Counter.DROPPED_LATE);
            }
        }
        return best;
    }
    void release(Slot slot) { slot.state.set(FREE); }
    int depth() {
        int depth = 0;
        for (Slot slot : slots) if (slot.state.get() == READY) depth++;
        return depth;
    }
    /** Decoder only, after production stops. GL never waits here. */
    public void close() {
        retired = true;
        for (Slot slot : slots) slot.state.compareAndSet(READY, FREE);
        for (Slot slot : slots) {
            while (slot.state.get() == READING || slot.state.get() == WRITING) LockSupport.parkNanos(100_000);
            slot.allocation.close();
        }
    }
}
