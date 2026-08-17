package bms.player.beatoraja.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TimingDiagnosticsTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void histogramProducesBoundedPercentilesAndResets() {
        TimingDiagnostics.Histogram histogram = new TimingDiagnostics.Histogram();
        histogram.recordNanos(TimeUnit.MICROSECONDS.toNanos(50));
        histogram.recordNanos(TimeUnit.MICROSECONDS.toNanos(1_000));
        histogram.recordNanos(TimeUnit.MICROSECONDS.toNanos(2_000));
        histogram.recordNanos(TimeUnit.MICROSECONDS.toNanos(10_000));

        TimingDiagnostics.HistogramSnapshot snapshot = histogram.snapshotAndReset();

        assertEquals(4, snapshot.count());
        assertEquals(3_262.5, snapshot.averageMicros());
        assertEquals(1_000, snapshot.p50Micros());
        assertEquals(12_000, snapshot.p95Micros());
        assertEquals(12_000, snapshot.p99Micros());
        assertEquals(10_000, snapshot.maximumMicros());
        assertEquals(0, histogram.snapshotAndReset().count());
    }

    @Test
    void eventQueueDropsInsteadOfBlockingWhenFull(@TempDir Path directory) {
        TimingDiagnostics.AsyncLogWriter writer = new TimingDiagnostics.AsyncLogWriter(
                directory.resolve("timing.log"),
                2,
                TimeUnit.DAYS.toNanos(1),
                1_024,
                2,
                () -> "{}",
                false
        );

        assertTrue(writer.offer("one"));
        assertTrue(writer.offer("two"));
        assertFalse(writer.offer("three"));
        assertEquals(2, writer.queuedEvents());
        assertEquals(1, writer.droppedEvents());
    }

    @Test
    void writerFlushesQueuedEventsAndFinalSummaryOffThread(@TempDir Path directory) throws Exception {
        Path log = directory.resolve("timing.log");
        TimingDiagnostics.AsyncLogWriter writer = new TimingDiagnostics.AsyncLogWriter(
                log,
                2,
                TimeUnit.DAYS.toNanos(1),
                1_024,
                2,
                () -> "{\"event\":\"summary\"}",
                true
        );

        assertTrue(writer.offer("{\"event\":\"enabled\"}"));
        writer.shutdown();

        String contents = Files.readString(log);
        assertTrue(contents.contains("\"event\":\"enabled\""));
        assertTrue(contents.contains("\"event\":\"summary\""));
    }

    @Test
    void summaryIsValidJsonWithRuntimeAndMetricSections(@TempDir Path directory) throws Exception {
        TimingDiagnostics.Session session = new TimingDiagnostics.Session(
                directory.resolve("timing.log"),
                TimeUnit.DAYS.toNanos(1),
                2,
                1_024,
                2,
                false
        );
        session.metrics[TimingDiagnostics.Metric.RENDER_DURATION.ordinal()]
                .recordNanos(TimeUnit.MILLISECONDS.toNanos(2));

        JsonNode summary = JSON.readTree(session.summaryJson());

        assertEquals("timing_summary", summary.path("event").asText());
        assertEquals(1, summary.path("metrics")
                .path("render_duration_us")
                .path("count")
                .asInt());
        assertTrue(summary.path("runtime").path("heap_used_bytes").isNumber());
        assertTrue(summary.path("counters").isObject());
    }

    @Test
    void logRotationKeepsOnlyTheConfiguredBackups(@TempDir Path directory) throws Exception {
        Path log = directory.resolve("timing.log");
        Files.writeString(log, "first");

        TimingDiagnostics.rotateIfNeeded(log, 3, 2);
        assertFalse(Files.exists(log));
        assertEquals("first", Files.readString(directory.resolve("timing.log.1")));

        Files.writeString(log, "second");
        TimingDiagnostics.rotateIfNeeded(log, 3, 2);
        assertEquals("second", Files.readString(directory.resolve("timing.log.1")));
        assertEquals("first", Files.readString(directory.resolve("timing.log.2")));
    }
}
