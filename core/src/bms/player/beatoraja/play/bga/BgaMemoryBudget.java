package bms.player.beatoraja.play.bga;

import java.util.concurrent.atomic.AtomicLong;

/** Hard bound for owned staging and reserved conversion/GL storage, not codec/driver internals. */
final class BgaMemoryBudget {
    private final long limit;
    private final AtomicLong used;
    BgaMemoryBudget(long limit, AtomicLong used) { this.limit = limit; this.used = used; }
    boolean reserve(long bytes) {
        if (bytes <= 0 || bytes > limit) return false;
        long before = used.get();
        while (before <= limit - bytes) {
            if (used.compareAndSet(before, before + bytes)) return true;
            before = used.get();
        }
        return false;
    }
    void release(long bytes) { used.addAndGet(-bytes); }
}
