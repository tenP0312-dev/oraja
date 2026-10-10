package bms.player.beatoraja.play.bga;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import bms.player.beatoraja.system.TimingDiagnostics;

/** Bounded, lock-free recording; snapshot allocation happens only in the monitor. */
public final class BgaPerformanceMetrics {
    public enum Metric {
        PREPARE, POLL_FRAME, UPLOAD, DRAW, RENDER_WAIT, OPEN, SEEK, DECODE, CONVERT
    }
    public enum Counter {
        DECODED, UPLOADED, DROPPED_LATE, DROPPED_FULL, REUSED_TEXTURE,
        SEEK_REQUESTED, SEEK_COALESCED, ERRORS, UPLOAD_BUDGET_EXCEEDED
    }
    public record Distribution(long count, double p50Us, double p95Us, double p99Us, double maxUs) {}
    private static final int SAMPLES = 1024;
    private final boolean enabled;
    private final AtomicLongArray[] samples = new AtomicLongArray[Metric.values().length];
    private final AtomicLong[] counts = new AtomicLong[Metric.values().length];
    private final AtomicLong[] maxima = new AtomicLong[Metric.values().length];
    private final LongAdder[] counters = new LongAdder[Counter.values().length];
    public final AtomicLong activeDecoders = new AtomicLong();
    public final AtomicLong preloadedDecoders = new AtomicLong();
    public final AtomicLong nativeBytes = new AtomicLong();
    public final AtomicLong queueDepth = new AtomicLong();

    public BgaPerformanceMetrics(boolean enabled) {
        this.enabled = enabled;
        for (int i = 0; i < samples.length; i++) {
            samples[i] = new AtomicLongArray(SAMPLES);
            counts[i] = new AtomicLong();
            maxima[i] = new AtomicLong();
        }
        for (int i = 0; i < counters.length; i++) counters[i] = new LongAdder();
    }
    public boolean isEnabled() { return enabled; }
    public long start() { return enabled ? System.nanoTime() : 0; }
    public void finish(Metric metric, long start) {
        if (start != 0) record(metric, Math.max(0, System.nanoTime() - start));
    }
    public void record(Metric metric, long nanos) {
        if (!enabled) return;
        TimingDiagnostics.recordMicros(switch (metric) {
            case PREPARE -> TimingDiagnostics.Metric.BGA_ASYNC_PREPARE;
            case POLL_FRAME -> TimingDiagnostics.Metric.BGA_POLL_FRAME;
            case UPLOAD -> TimingDiagnostics.Metric.BGA_TEXTURE_UPLOAD;
            case DRAW -> TimingDiagnostics.Metric.BGA_DRAW;
            case RENDER_WAIT -> TimingDiagnostics.Metric.BGA_RENDER_WAIT;
            case OPEN -> TimingDiagnostics.Metric.BGA_OPEN;
            case SEEK -> TimingDiagnostics.Metric.BGA_SEEK;
            case DECODE -> TimingDiagnostics.Metric.BGA_DECODE;
            case CONVERT -> TimingDiagnostics.Metric.BGA_CONVERT;
        }, nanos / 1000);
        int i = metric.ordinal();
        long n = counts[i].getAndIncrement();
        samples[i].set((int) (n % SAMPLES), nanos);
        maxima[i].accumulateAndGet(nanos, Math::max);
    }
    public void increment(Counter counter) {
        if (!enabled) return;
        counters[counter.ordinal()].increment();
        TimingDiagnostics.increment(switch (counter) {
            case DECODED -> TimingDiagnostics.Counter.BGA_FRAMES_DECODED;
            case UPLOADED -> TimingDiagnostics.Counter.BGA_FRAMES_UPLOADED;
            case DROPPED_LATE -> TimingDiagnostics.Counter.BGA_DROPPED_LATE;
            case DROPPED_FULL -> TimingDiagnostics.Counter.BGA_DROPPED_FULL;
            case REUSED_TEXTURE -> TimingDiagnostics.Counter.BGA_TEXTURE_REUSED;
            case SEEK_REQUESTED -> TimingDiagnostics.Counter.BGA_SEEK_REQUESTED;
            case SEEK_COALESCED -> TimingDiagnostics.Counter.BGA_SEEK_COALESCED;
            case ERRORS -> TimingDiagnostics.Counter.BGA_DECODE_ERROR;
            case UPLOAD_BUDGET_EXCEEDED -> TimingDiagnostics.Counter.BGA_UPLOAD_OVER_BUDGET;
        });
    }
    public long count(Counter counter) { return counters[counter.ordinal()].sum(); }
    public Distribution snapshot(Metric metric) {
        int i = metric.ordinal();
        long count = counts[i].get();
        int size = (int) Math.min(count, SAMPLES);
        long[] values = new long[size];
        for (int j = 0; j < size; j++) values[j] = samples[i].get(j);
        Arrays.sort(values);
        return new Distribution(count, percentile(values, .50), percentile(values, .95),
                percentile(values, .99), maxima[i].get() / 1000.0);
    }
    private static double percentile(long[] values, double p) {
        return values.length == 0 ? 0 : values[(int) Math.ceil(values.length * p) - 1] / 1000.0;
    }
}
