package bms.player.beatoraja.play.bga;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BgaMemoryBudgetTest {
    @Test void rejectsOverBudgetAndConcurrentReservationsStayWithinCap() throws Exception {
        var used = new AtomicLong(); var budget = new BgaMemoryBudget(16,used);
        assertFalse(budget.reserve(17)); assertFalse(budget.reserve(0));
        assertTrue(budget.reserve(16)); assertFalse(budget.reserve(1)); budget.release(16);
        var peak = new AtomicLong();
        Thread[] threads = new Thread[4];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int n = 0; n < 10_000; n++) if (budget.reserve(8)) {
                    peak.accumulateAndGet(used.get(),Math::max); budget.release(8);
                }
            }); threads[t].start();
        }
        for (Thread thread : threads) thread.join(3000);
        for (Thread thread : threads) assertFalse(thread.isAlive());
        assertTrue(peak.get() <= 16); assertEquals(0,used.get());
    }
}
