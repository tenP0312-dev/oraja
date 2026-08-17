package bms.player.beatoraja.system;

import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.Config;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Supplier;

/**
 * Opt-in gameplay timing diagnostics with allocation-free hot-path recording.
 *
 * <p>Gameplay threads only read the active session and update atomic counters.
 * Summaries, JSON formatting, GC inspection, rotation and file I/O all run on
 * one daemon writer thread.</p>
 */
public final class TimingDiagnostics {
    public static final String ENABLE_PROPERTY = "bmsir.timingDiagnostics";
    public static final String LOG_FILE_NAME = "bmsir-timing.log";

    private static final long SUMMARY_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final long MAX_LOG_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_LOG_BACKUPS = 5;
    private static final int EVENT_QUEUE_CAPACITY = 32;

    private static volatile Session active;

    private TimingDiagnostics() {
    }

    public enum Metric {
        RENDER_INTERVAL("render_interval_us"),
        RENDER_DURATION("render_duration_us"),
        INPUT_POLL_INTERVAL("input_poll_interval_us"),
        INPUT_POLL_DURATION("input_poll_duration_us"),
        INPUT_TO_JUDGE_DISPATCH("input_to_judge_dispatch_us"),
        OPENAL_PLAY_CALL("openal_play_call_us"),
        PORTAUDIO_ENQUEUE("portaudio_enqueue_us"),
        PORTAUDIO_ENQUEUE_TO_MIX("portaudio_enqueue_to_mix_us"),
        PORTAUDIO_MIX("portaudio_mix_us"),
        PORTAUDIO_WRITE("portaudio_write_us"),
        BGA_DECODE("bga_decode_us"),
        BGA_PIXMAP_LOCK("bga_pixmap_lock_us"),
        BGA_PIXMAP_COPY("bga_pixmap_copy_us"),
        BGA_RENDER_QUEUE("bga_render_queue_us"),
        BGA_TEXTURE_LOCK("bga_texture_lock_us"),
        BGA_TEXTURE_UPLOAD("bga_texture_upload_us");

        final String jsonName;

        Metric(String jsonName) {
            this.jsonName = jsonName;
        }
    }

    public enum Counter {
        PORTAUDIO_UNDERFLOW("portaudio_underflows"),
        PORTAUDIO_WRITE_ERROR("portaudio_write_errors"),
        PORTAUDIO_ENQUEUE_REJECTED("portaudio_enqueue_rejected"),
        BGA_DECODER_STARTED("bga_decoders_started"),
        BGA_DECODER_STOPPED("bga_decoders_stopped"),
        BGA_DECODE_ERROR("bga_decode_errors"),
        BGA_TEXTURE_ERROR("bga_texture_errors"),
        BGA_UPLOAD_SKIPPED("bga_uploads_skipped");

        final String jsonName;

        Counter(String jsonName) {
            this.jsonName = jsonName;
        }
    }

    /** Configure once after the system config has been validated. */
    public static synchronized void configure(Config config) {
        Session previous = active;
        active = null;
        if (previous != null) {
            previous.shutdown();
        }

        boolean enabled = config != null
                && (config.isTimingDiagnostics() || Boolean.getBoolean(ENABLE_PROPERTY));
        if (!enabled) {
            return;
        }

        Session session = new Session(
                ClientLogDirectory.resolve(LOG_FILE_NAME),
                SUMMARY_INTERVAL_NANOS,
                EVENT_QUEUE_CAPACITY,
                MAX_LOG_BYTES,
                MAX_LOG_BACKUPS,
                true
        );
        active = session;
        session.event(
                "enabled",
                "source", config.isTimingDiagnostics() ? "config" : "system_property",
                "os", System.getProperty("os.name", ""),
                "arch", System.getProperty("os.arch", ""),
                "java", System.getProperty("java.version", "")
        );
    }

    public static boolean isEnabled() {
        return active != null;
    }

    /** Returns zero while disabled, allowing callers to use a cheap paired API. */
    public static long start() {
        return active == null ? 0 : System.nanoTime();
    }

    public static long renderStarted() {
        Session session = active;
        if (session == null) {
            return 0;
        }
        long now = System.nanoTime();
        long previous = session.lastRenderStart.getAndSet(now);
        if (previous != 0 && now >= previous) {
            session.metrics[Metric.RENDER_INTERVAL.ordinal()].recordNanos(now - previous);
        }
        return now;
    }

    public static long inputPollStarted() {
        Session session = active;
        if (session == null) {
            return 0;
        }
        long now = System.nanoTime();
        long previous = session.lastInputPollStart.getAndSet(now);
        if (previous != 0 && now >= previous) {
            session.metrics[Metric.INPUT_POLL_INTERVAL.ordinal()].recordNanos(now - previous);
        }
        return now;
    }

    public static void finish(Metric metric, long startedNanos) {
        if (startedNanos == 0) {
            return;
        }
        Session session = active;
        if (session != null) {
            long elapsed = System.nanoTime() - startedNanos;
            if (elapsed >= 0) {
                session.metrics[metric.ordinal()].recordNanos(elapsed);
            }
        }
    }

    public static void recordMicros(Metric metric, long micros) {
        Session session = active;
        if (session != null && micros >= 0) {
            session.metrics[metric.ordinal()].recordMicros(micros);
        }
    }

    public static void increment(Counter counter) {
        Session session = active;
        if (session != null) {
            session.counters[counter.ordinal()].incrementAndGet();
        }
    }

    public static void stateChanged(String state) {
        Session session = active;
        if (session == null) {
            return;
        }
        session.state = state == null ? "unknown" : state;
        session.event("state", "value", session.state);
    }

    public static void audioConfigured(
            AudioConfig.DriverType driver,
            String hostApi,
            int sampleRate,
            int framesPerBuffer) {
        Session session = active;
        if (session == null) {
            return;
        }
        session.audioDriver = driver == null ? "unknown" : driver.name();
        session.audioHostApi = hostApi == null ? "" : hostApi;
        session.audioSampleRate = Math.max(sampleRate, 0);
        session.audioFramesPerBuffer = Math.max(framesPerBuffer, 0);
        double bufferMillis = sampleRate > 0 && framesPerBuffer > 0
                ? framesPerBuffer * 1000.0 / sampleRate
                : -1;
        session.event(
                "audio_config",
                "driver", session.audioDriver,
                "host_api", session.audioHostApi,
                "sample_rate", session.audioSampleRate,
                "frames_per_buffer", session.audioFramesPerBuffer,
                "theoretical_buffer_ms", bufferMillis
        );
    }

    public static void movieDecoderStarted() {
        Session session = active;
        if (session != null) {
            session.activeMovieDecoders.incrementAndGet();
            session.counters[Counter.BGA_DECODER_STARTED.ordinal()].incrementAndGet();
        }
    }

    public static void movieDecoderStopped() {
        Session session = active;
        if (session != null) {
            session.activeMovieDecoders.updateAndGet(value -> Math.max(0, value - 1));
            session.counters[Counter.BGA_DECODER_STOPPED.ordinal()].incrementAndGet();
        }
    }

    public static void movieBytesRetained(long bytes) {
        Session session = active;
        if (session != null && bytes > 0) {
            session.retainedMovieBytes.addAndGet(bytes);
        }
    }

    public static void movieBytesReleased(long bytes) {
        Session session = active;
        if (session != null && bytes > 0) {
            session.retainedMovieBytes.updateAndGet(value -> Math.max(0, value - bytes));
        }
    }

    public static void bgaUploadQueued() {
        Session session = active;
        if (session != null) {
            long pending = session.pendingBgaUploads.incrementAndGet();
            updateMax(session.maxPendingBgaUploads, pending);
        }
    }

    public static void bgaUploadFinished() {
        Session session = active;
        if (session != null) {
            session.pendingBgaUploads.updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    public static void shutdown() {
        Session session;
        synchronized (TimingDiagnostics.class) {
            session = active;
            active = null;
        }
        if (session != null) {
            session.shutdown();
        }
    }

    private static void updateMax(AtomicLong target, long value) {
        long previous = target.get();
        while (value > previous && !target.compareAndSet(previous, value)) {
            previous = target.get();
        }
    }

    static final class Histogram {
        private static final long[] UPPER_MICROS = {
                50, 100, 250, 500, 750,
                1_000, 1_500, 2_000, 3_000, 5_000,
                8_000, 12_000, 16_000, 25_000, 33_000,
                50_000, 75_000, 100_000, 250_000, 500_000,
                1_000_000, Long.MAX_VALUE
        };

        private final AtomicLongArray buckets = new AtomicLongArray(UPPER_MICROS.length);
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong maximumNanos = new AtomicLong();

        void recordNanos(long nanos) {
            if (nanos < 0) {
                return;
            }
            totalNanos.addAndGet(nanos);
            updateMax(maximumNanos, nanos);
            long micros = TimeUnit.NANOSECONDS.toMicros(nanos);
            int low = 0;
            int high = UPPER_MICROS.length - 1;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (micros <= UPPER_MICROS[middle]) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }
            buckets.incrementAndGet(low);
        }

        void recordMicros(long micros) {
            long nanos = micros > Long.MAX_VALUE / 1_000
                    ? Long.MAX_VALUE
                    : micros * 1_000;
            recordNanos(nanos);
        }

        HistogramSnapshot snapshotAndReset() {
            long[] counts = new long[UPPER_MICROS.length];
            long count = 0;
            for (int index = 0; index < counts.length; index++) {
                counts[index] = buckets.getAndSet(index, 0);
                count += counts[index];
            }
            long total = totalNanos.getAndSet(0);
            long maximum = maximumNanos.getAndSet(0);
            if (count == 0) {
                return HistogramSnapshot.EMPTY;
            }
            return new HistogramSnapshot(
                    count,
                    total,
                    maximum,
                    percentile(counts, count, 0.50, maximum),
                    percentile(counts, count, 0.95, maximum),
                    percentile(counts, count, 0.99, maximum)
            );
        }

        private static long percentile(
                long[] counts,
                long totalCount,
                double quantile,
                long maximumNanos) {
            long target = Math.max(1, (long) Math.ceil(totalCount * quantile));
            long seen = 0;
            for (int index = 0; index < counts.length; index++) {
                seen += counts[index];
                if (seen >= target) {
                    long upper = UPPER_MICROS[index];
                    return upper == Long.MAX_VALUE
                            ? TimeUnit.NANOSECONDS.toMicros(maximumNanos)
                            : upper;
                }
            }
            return TimeUnit.NANOSECONDS.toMicros(maximumNanos);
        }
    }

    record HistogramSnapshot(
            long count,
            long totalNanos,
            long maximumNanos,
            long p50Micros,
            long p95Micros,
            long p99Micros) {
        static final HistogramSnapshot EMPTY = new HistogramSnapshot(0, 0, 0, 0, 0, 0);

        double averageMicros() {
            return count == 0 ? 0 : (double) totalNanos / count / 1_000.0;
        }

        long maximumMicros() {
            return TimeUnit.NANOSECONDS.toMicros(maximumNanos);
        }
    }

    static final class AsyncLogWriter {
        private final Path path;
        private final ArrayBlockingQueue<String> events;
        private final long summaryIntervalNanos;
        private final long maxBytes;
        private final int maxBackups;
        private final Supplier<String> summarySupplier;
        private final AtomicLong droppedEvents = new AtomicLong();
        private final Thread thread;
        private volatile boolean stopping;

        AsyncLogWriter(
                Path path,
                int queueCapacity,
                long summaryIntervalNanos,
                long maxBytes,
                int maxBackups,
                Supplier<String> summarySupplier,
                boolean startThread) {
            this.path = path;
            this.events = new ArrayBlockingQueue<>(queueCapacity);
            this.summaryIntervalNanos = summaryIntervalNanos;
            this.maxBytes = maxBytes;
            this.maxBackups = maxBackups;
            this.summarySupplier = summarySupplier;
            this.thread = new Thread(this::run, "BMS timing diagnostics writer");
            this.thread.setDaemon(true);
            if (startThread) {
                this.thread.start();
            }
        }

        boolean offer(String line) {
            if (stopping || !events.offer(line)) {
                droppedEvents.incrementAndGet();
                return false;
            }
            return true;
        }

        long droppedEvents() {
            return droppedEvents.get();
        }

        int queuedEvents() {
            return events.size();
        }

        void shutdown() {
            stopping = true;
            thread.interrupt();
            if (thread.isAlive() && Thread.currentThread() != thread) {
                try {
                    thread.join(250);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void run() {
            long nextSummary = System.nanoTime() + summaryIntervalNanos;
            try {
                while (!stopping) {
                    long wait = Math.max(1, nextSummary - System.nanoTime());
                    String event = events.poll(wait, TimeUnit.NANOSECONDS);
                    if (event != null) {
                        writeLine(event);
                    }
                    if (System.nanoTime() >= nextSummary) {
                        writeLine(summarySupplier.get());
                        nextSummary = System.nanoTime() + summaryIntervalNanos;
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignored) {
                // Diagnostics must never affect gameplay or process shutdown.
            } finally {
                String event;
                while ((event = events.poll()) != null) {
                    writeLine(event);
                }
                writeLine(summarySupplier.get());
            }
        }

        private void writeLine(String line) {
            if (line == null || line.isBlank()) {
                return;
            }
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                rotateIfNeeded(path, maxBytes, maxBackups);
                Files.writeString(
                        path,
                        line + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (Throwable ignored) {
                // Diagnostics must never affect gameplay or process shutdown.
            }
        }
    }

    static void rotateIfNeeded(Path path, long maxBytes, int maxBackups) throws Exception {
        if (!Files.exists(path) || Files.size(path) < maxBytes) {
            return;
        }
        for (int index = maxBackups - 1; index >= 1; index--) {
            Path source = backupPath(path, index);
            if (Files.exists(source)) {
                Files.move(
                        source,
                        backupPath(path, index + 1),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
        Files.move(path, backupPath(path, 1), StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path backupPath(Path path, int index) {
        return path.resolveSibling(path.getFileName() + "." + index);
    }

    static final class Session {
        final Histogram[] metrics = new Histogram[Metric.values().length];
        final AtomicLong[] counters = new AtomicLong[Counter.values().length];
        final AtomicLong lastRenderStart = new AtomicLong();
        final AtomicLong lastInputPollStart = new AtomicLong();
        final AtomicLong activeMovieDecoders = new AtomicLong();
        final AtomicLong retainedMovieBytes = new AtomicLong();
        final AtomicLong pendingBgaUploads = new AtomicLong();
        final AtomicLong maxPendingBgaUploads = new AtomicLong();
        final AsyncLogWriter writer;

        volatile String state = "startup";
        volatile String audioDriver = "unknown";
        volatile String audioHostApi = "";
        volatile int audioSampleRate;
        volatile int audioFramesPerBuffer;

        private long lastSummaryNanos = System.nanoTime();
        private long previousGcCount = gcCount();
        private long previousGcTimeMillis = gcTimeMillis();

        Session(
                Path path,
                long summaryIntervalNanos,
                int queueCapacity,
                long maxBytes,
                int maxBackups,
                boolean startWriter) {
            for (int index = 0; index < metrics.length; index++) {
                metrics[index] = new Histogram();
            }
            for (int index = 0; index < counters.length; index++) {
                counters[index] = new AtomicLong();
            }
            writer = new AsyncLogWriter(
                    path,
                    queueCapacity,
                    summaryIntervalNanos,
                    maxBytes,
                    maxBackups,
                    this::summaryJson,
                    startWriter
            );
        }

        void event(String event, Object... details) {
            StringBuilder json = new StringBuilder(256);
            json.append('{');
            appendString(json, "at", Instant.now().toString());
            json.append(',');
            appendString(json, "event", event);
            for (int index = 0; index + 1 < details.length; index += 2) {
                json.append(',');
                appendValue(json, String.valueOf(details[index]), details[index + 1]);
            }
            json.append('}');
            writer.offer(json.toString());
        }

        String summaryJson() {
            long now = System.nanoTime();
            long periodMillis = TimeUnit.NANOSECONDS.toMillis(Math.max(0, now - lastSummaryNanos));
            lastSummaryNanos = now;

            long currentGcCount = gcCount();
            long currentGcTimeMillis = gcTimeMillis();
            long gcCountDelta = nonNegativeDelta(currentGcCount, previousGcCount);
            long gcTimeDelta = nonNegativeDelta(currentGcTimeMillis, previousGcTimeMillis);
            previousGcCount = currentGcCount;
            previousGcTimeMillis = currentGcTimeMillis;

            StringBuilder json = new StringBuilder(2_048);
            json.append('{');
            appendString(json, "at", Instant.now().toString());
            json.append(',');
            appendString(json, "event", "timing_summary");
            json.append(',');
            appendString(json, "state", state);
            json.append(',');
            appendNumber(json, "period_ms", periodMillis);
            json.append(',');
            appendString(json, "audio_driver", audioDriver);
            json.append(',');
            appendString(json, "audio_host_api", audioHostApi);
            json.append(',');
            appendNumber(json, "audio_sample_rate", audioSampleRate);
            json.append(',');
            appendNumber(json, "audio_frames_per_buffer", audioFramesPerBuffer);

            json.append(",\"metrics\":{");
            Metric[] metricNames = Metric.values();
            for (int index = 0; index < metricNames.length; index++) {
                if (index > 0) {
                    json.append(',');
                }
                appendHistogram(json, metricNames[index].jsonName, metrics[index].snapshotAndReset());
            }
            json.append('}');

            json.append(",\"counters\":{");
            Counter[] counterNames = Counter.values();
            for (int index = 0; index < counterNames.length; index++) {
                if (index > 0) {
                    json.append(',');
                }
                appendNumber(json, counterNames[index].jsonName, counters[index].getAndSet(0));
            }
            json.append('}');

            Runtime runtime = Runtime.getRuntime();
            long pending = pendingBgaUploads.get();
            long maxPending = maxPendingBgaUploads.getAndSet(pending);
            json.append(",\"runtime\":{");
            appendNumber(json, "heap_used_bytes", runtime.totalMemory() - runtime.freeMemory());
            json.append(',');
            appendNumber(json, "heap_committed_bytes", runtime.totalMemory());
            json.append(',');
            appendNumber(json, "heap_max_bytes", runtime.maxMemory());
            json.append(',');
            appendNumber(json, "gc_count", gcCountDelta);
            json.append(',');
            appendNumber(json, "gc_time_ms", gcTimeDelta);
            json.append(',');
            appendNumber(json, "active_movie_decoders", activeMovieDecoders.get());
            json.append(',');
            appendNumber(json, "retained_movie_bytes", retainedMovieBytes.get());
            json.append(',');
            appendNumber(json, "pending_bga_uploads", pending);
            json.append(',');
            appendNumber(json, "max_pending_bga_uploads", maxPending);
            json.append(',');
            appendNumber(json, "dropped_log_events", writer.droppedEvents());
            json.append("}}");
            return json.toString();
        }

        void shutdown() {
            event("shutdown");
            writer.shutdown();
        }
    }

    private static long gcCount() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionCount();
            if (value >= 0) {
                total += value;
            }
        }
        return total;
    }

    private static long gcTimeMillis() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionTime();
            if (value >= 0) {
                total += value;
            }
        }
        return total;
    }

    private static long nonNegativeDelta(long current, long previous) {
        return current >= previous ? current - previous : 0;
    }

    private static void appendHistogram(
            StringBuilder json,
            String name,
            HistogramSnapshot snapshot) {
        appendEscaped(json, name);
        json.append(":{");
        appendNumber(json, "count", snapshot.count());
        json.append(',');
        appendDecimal(json, "average_us", snapshot.averageMicros());
        json.append(',');
        appendNumber(json, "p50_us", snapshot.p50Micros());
        json.append(',');
        appendNumber(json, "p95_us", snapshot.p95Micros());
        json.append(',');
        appendNumber(json, "p99_us", snapshot.p99Micros());
        json.append(',');
        appendNumber(json, "max_us", snapshot.maximumMicros());
        json.append('}');
    }

    private static void appendValue(StringBuilder json, String name, Object value) {
        if (value instanceof Number number) {
            appendEscaped(json, name);
            json.append(':').append(number);
        } else if (value instanceof Boolean bool) {
            appendEscaped(json, name);
            json.append(':').append(bool);
        } else {
            appendString(json, name, String.valueOf(value == null ? "" : value));
        }
    }

    private static void appendString(StringBuilder json, String name, String value) {
        appendEscaped(json, name);
        json.append(':');
        appendEscaped(json, value == null ? "" : value);
    }

    private static void appendNumber(StringBuilder json, String name, long value) {
        appendEscaped(json, name);
        json.append(':').append(value);
    }

    private static void appendDecimal(StringBuilder json, String name, double value) {
        appendEscaped(json, name);
        json.append(':').append(String.format(Locale.ROOT, "%.3f", value));
    }

    private static void appendEscaped(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
            case '"' -> json.append("\\\"");
            case '\\' -> json.append("\\\\");
            case '\n' -> json.append("\\n");
            case '\r' -> json.append("\\r");
            case '\t' -> json.append("\\t");
            default -> {
                if (character < 0x20) {
                    json.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                } else {
                    json.append(character);
                }
            }
            }
        }
        json.append('"');
    }
}
