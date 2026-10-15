package bms.player.beatoraja.camera;

import java.io.EOFException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One worker, one device process and three reusable RGB buffers. No graphics calls here.
 * Like a controller, a camera that is missing, unplugged or stalled is reopened in the background while a
 * screen still shows it, with a backoff so a missing camera costs at most one attempt every few seconds.
 */
public final class CameraCapture implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(CameraCapture.class);
    interface Source extends AutoCloseable {
        InputStream pixels();
        void close(); // Must not wait for a driver or reader.
        default void frameRead() { }
        default void awaitStopped() throws Exception { } // Worker-only cleanup, never called by the render thread.
    }
    interface Factory { Source open(HandCameraSettings settings) throws Exception; }
    static final long FIRST_RETRY_NANOS = 2_000_000_000L, MAX_RETRY_NANOS = 10_000_000_000L;
    private final Factory factory;
    private final java.util.function.LongSupplier clock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "hand-camera-reader");
        thread.setDaemon(true);
        return thread;
    });
    private Session active;
    private boolean closed;
    /** Consecutive sessions that ended without a frame, and when the ended session may be reopened. */
    private int failures;
    private long retryAt;
    private boolean retryScheduled;

    public CameraCapture() { this(FFmpegCameraProcess::open); }
    CameraCapture(Factory factory) { this(factory, System::nanoTime); }
    CameraCapture(Factory factory, java.util.function.LongSupplier clock) { this.factory = factory; this.clock = clock; }

    public void request(HandCameraSettings settings) {
        if (closed || !settings.usable()) { stop(); return; }
        if (active != null && active.settings.equals(settings)) {
            if (!active.stopped) return;
            // The session ended by itself: camera missing, unplugged, stalled or rejected the settings.
            long now = clock.getAsLong();
            if (!retryScheduled) {
                failures = active.delivered ? 1 : failures + 1;
                retryAt = now + Math.min(MAX_RETRY_NANOS, FIRST_RETRY_NANOS << Math.min(failures - 1, 3));
                retryScheduled = true;
            }
            if (now - retryAt < 0) return;
            start(settings);
            return;
        }
        stop();
        start(settings);
    }

    /** Tests: the active session ended by itself and a reopen is scheduled. */
    boolean retryPending() { return active != null && retryScheduled; }

    private void start(HandCameraSettings settings) {
        retryScheduled = false;
        active = new Session(settings, failures > 0);
        Session session = active;
        worker.execute(() -> read(session));
    }

    public Frame poll() {
        if (active == null || active.stopped) return null;
        byte[] bytes = active.latest.getAndSet(null);
        return bytes == null ? null : new Frame(active, bytes);
    }

    public void stop() {
        if (active != null) {
            active.stopped = true;
            active.latest.set(null);
            Source source = active.source;
            if (source != null) source.close();
            active = null;
        }
        failures = 0;
        retryScheduled = false;
    }

    @Override public void close() {
        closed = true;
        stop();
        worker.shutdown();
    }

    private void read(Session session) {
        if (session.stopped) return;
        try (Source source = factory.open(session.settings)) {
            session.source = source;
            if (session.stopped) return;
            for (int i = 0; i < 3; i++) session.free.add(new byte[session.settings.frameBytes()]);
            InputStream input = source.pixels();
            while (!session.stopped) {
                byte[] bytes = session.free.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (bytes == null) continue;
                int offset = 0;
                while (offset < bytes.length && !session.stopped) {
                    int count = input.read(bytes, offset, bytes.length - offset);
                    if (count < 0) throw new EOFException();
                    offset += count;
                }
                if (session.stopped) break;
                byte[] previous = session.latest.getAndSet(bytes);
                if (previous != null) session.free.offer(previous);
                source.frameRead();
                if (!session.delivered) {
                    session.delivered = true;
                    if (session.retry) logger.info("Hand camera connected");
                }
            }
        } catch (Exception | LinkageError error) {
            if (!session.stopped) {
                // Never include command lines, device names or raw FFmpeg logs. Retries stay quiet.
                if (session.retry && !session.delivered) logger.debug("Hand camera still unavailable ({})", error.getClass().getSimpleName());
                else logger.warn("Hand camera unavailable ({}); waiting for the camera, check camera settings"
                        + " and OS permission", error.getClass().getSimpleName());
            }
        } finally {
            session.stopped = true;
            session.latest.set(null);
            Source source = session.source;
            if (source != null) {
                try { source.awaitStopped(); }
                catch (Exception ignored) { /* The process was already told to terminate. */ }
            }
            session.source = null;
        }
    }

    private static final class Session {
        final HandCameraSettings settings;
        final AtomicReference<byte[]> latest = new AtomicReference<>();
        final ArrayBlockingQueue<byte[]> free = new ArrayBlockingQueue<>(3);
        final boolean retry;
        volatile boolean stopped, delivered;
        volatile Source source;
        Session(HandCameraSettings settings, boolean retry) {
            this.settings = settings;
            this.retry = retry;
        }
    }

    public static final class Frame implements AutoCloseable {
        private final Session session;
        private byte[] pixels;
        Frame(Session session, byte[] pixels) { this.session = session; this.pixels = pixels; }
        public byte[] pixels() { return pixels; }
        public int width() { return session.settings.width(); }
        public int height() { return session.settings.height(); }
        @Override public void close() {
            if (pixels != null) { session.free.offer(pixels); pixels = null; }
        }
    }
}
