package bms.player.beatoraja.play.bga;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BgaPerformanceMetricsTest {
    @Test void rollingDistributionKeepsLifetimeCountAndMaximum() {
        var metrics = new BgaPerformanceMetrics(true);
        for (int n = 0; n < 2050; n++) metrics.record(BgaPerformanceMetrics.Metric.POLL_FRAME,n*1000L);
        var snapshot = metrics.snapshot(BgaPerformanceMetrics.Metric.POLL_FRAME);
        assertEquals(2050,snapshot.count()); assertEquals(2049,snapshot.maxUs());
        assertTrue(snapshot.p50Us() > 1500); assertTrue(snapshot.p95Us() >= snapshot.p50Us());
        assertTrue(snapshot.p99Us() >= snapshot.p95Us());
    }
    @Test void disabledRecordingStaysEmptyAndCountersAreExplicit() {
        var metrics = new BgaPerformanceMetrics(false);
        metrics.record(BgaPerformanceMetrics.Metric.OPEN,1000);
        metrics.increment(BgaPerformanceMetrics.Counter.DECODED);
        assertEquals(0,metrics.start()); assertEquals(0,metrics.snapshot(BgaPerformanceMetrics.Metric.OPEN).count());
        assertEquals(0,metrics.count(BgaPerformanceMetrics.Counter.DECODED));
    }
}
