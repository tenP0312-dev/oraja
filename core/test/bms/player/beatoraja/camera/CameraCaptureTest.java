package bms.player.beatoraja.camera;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraCaptureTest {
    private final HandCameraSettings settings = new HandCameraSettings(true, "camera", 160, 120, 30, false);
    static final class Pipe implements CameraCapture.Source {
        final PipedInputStream input = new PipedInputStream(160 * 120 * 3 * 2);
        final PipedOutputStream output;
        final AtomicBoolean closed = new AtomicBoolean();
        Pipe() throws IOException { output = new PipedOutputStream(input); }
        public InputStream pixels() { return input; }
        public void close() {
            closed.set(true);
            try { output.close(); } catch (IOException ignored) { }
        }
    }
    static void until(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean());
    }
    /** poll() consumes the frame, so latch it; until() evaluates its condition once more at the end. */
    void awaitFrame(CameraCapture capture) throws Exception {
        AtomicBoolean received = new AtomicBoolean();
        until(() -> {
            if (!received.get()) {
                capture.request(settings);
                try (var frame = capture.poll()) { if (frame != null) received.set(true); }
            }
            return received.get();
        });
    }
    @Test void disabledAndMissingDeviceNeverOpenTheBackend() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        try (CameraCapture capture = new CameraCapture(s -> { opens.incrementAndGet(); return new Pipe(); })) {
            capture.request(new HandCameraSettings(false, "camera", 160, 120, 30, false));
            capture.request(new HandCameraSettings(true, "", 160, 120, 30, false));
            assertNull(capture.poll());
            assertEquals(0, opens.get());
        }
    }
    @Test void publishesOnlyCompleteFramesDropsOldFramesAndStopsBlockedReads() throws Exception {
        Pipe pipe = new Pipe();
        AtomicInteger readFrames = new AtomicInteger();
        CameraCapture.Source source = new CameraCapture.Source() {
            public InputStream pixels() { return pipe.input; }
            public void frameRead() { readFrames.incrementAndGet(); }
            public void close() { pipe.close(); }
        };
        try (CameraCapture capture = new CameraCapture(s -> source)) {
            capture.request(settings);
            pipe.output.write(new byte[100]);
            assertNull(capture.poll());
            pipe.output.write(new byte[settings.frameBytes() - 100]);
            until(() -> readFrames.get() >= 1);
            byte[] next = new byte[settings.frameBytes()];
            Arrays.fill(next, (byte) 7);
            pipe.output.write(next);
            until(() -> readFrames.get() >= 2);
            try (var frame = capture.poll()) {
                assertNotNull(frame);
                assertArrayEquals(next, frame.pixels());
                assertEquals(160, frame.width());
                assertEquals(120, frame.height());
            }
            capture.stop();
            assertTrue(pipe.closed.get());
            assertNull(capture.poll());
        }
    }
    @Test void stopDuringStartupClosesLateBackendAndDoesNotPublishOrReopen() throws Exception {
        CountDownLatch opening = new CountDownLatch(1), finish = new CountDownLatch(1);
        Pipe pipe = new Pipe();
        try (CameraCapture capture = new CameraCapture(s -> {
            opening.countDown();
            assertTrue(finish.await(3, TimeUnit.SECONDS));
            return pipe;
        })) {
            capture.request(settings);
            assertTrue(opening.await(3, TimeUnit.SECONDS));
            capture.stop();
            finish.countDown();
            until(pipe.closed::get);
            assertNull(capture.poll());
        } finally { finish.countDown(); }
    }
    @Test void partialEofIsNotRetriedEveryFrameAndReentryRetriesImmediately() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        try (CameraCapture capture = new CameraCapture(s -> {
            opens.incrementAndGet();
            return new CameraCapture.Source() {
                public InputStream pixels() { return new ByteArrayInputStream(new byte[5]); }
                public void close() { }
            };
        })) {
            capture.request(settings);
            until(() -> opens.get() == 1);
            // The same settings remain latched even if a device rejected them.
            for (int i = 0; i < 100; i++) capture.request(settings);
            assertEquals(1, opens.get());
            assertNull(capture.poll());
            capture.stop();
            capture.request(settings);
            until(() -> opens.get() == 2);
        }
    }
    @Test void aMissingCameraIsRetriedInTheBackgroundWithBackoffLikeAController() throws Exception {
        AtomicLong now = new AtomicLong();
        AtomicInteger opens = new AtomicInteger();
        AtomicBoolean plugged = new AtomicBoolean();
        Pipe pipe = new Pipe();
        try (CameraCapture capture = new CameraCapture(s -> {
            opens.incrementAndGet();
            if (!plugged.get()) throw new FFmpegCameraProcess.DeviceNotFoundException();
            return pipe;
        }, now::get)) {
            capture.request(settings);
            until(() -> opens.get() == 1);
            // Requested every frame, but not reopened before the backoff elapses: 2s, 4s, 8s, then 10s.
            long[] delays = {2, 4, 8, 10, 10};
            for (int attempt = 0; attempt < delays.length; attempt++) {
                int expected = attempt + 1;
                until(() -> { capture.request(settings); return capture.retryPending(); });
                now.addAndGet(TimeUnit.SECONDS.toNanos(delays[attempt]) - 1);
                for (int i = 0; i < 50; i++) capture.request(settings);
                assertEquals(expected, opens.get());
                now.incrementAndGet();
                capture.request(settings);
                until(() -> opens.get() == expected + 1);
            }
            // Plugged in: the next attempt connects and frames flow without leaving the screen.
            plugged.set(true);
            int before = opens.get();
            until(() -> { capture.request(settings); return capture.retryPending(); });
            now.addAndGet(CameraCapture.MAX_RETRY_NANOS);
            until(() -> { capture.request(settings); return opens.get() == before + 1; });
            pipe.output.write(new byte[settings.frameBytes()]);
            awaitFrame(capture);
        }
    }
    @Test void anUnpluggedCameraIsReopenedQuicklyAndSettingsChangesResetTheBackoff() throws Exception {
        AtomicLong now = new AtomicLong();
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<Pipe> latest = new AtomicReference<>();
        try (CameraCapture capture = new CameraCapture(s -> {
            // Publish the pipe before the count the test waits on.
            Pipe pipe = new Pipe(); latest.set(pipe); opens.incrementAndGet(); return pipe;
        }, now::get)) {
            capture.request(settings);
            until(() -> opens.get() == 1);
            latest.get().output.write(new byte[settings.frameBytes()]);
            awaitFrame(capture);
            // Unplugging ends the stream. After delivering frames the first retry waits only 2s again.
            latest.get().output.close();
            until(() -> { capture.request(settings); return capture.retryPending(); });
            now.addAndGet(CameraCapture.FIRST_RETRY_NANOS - 1);
            for (int i = 0; i < 50; i++) capture.request(settings);
            assertEquals(1, opens.get());
            now.addAndGet(CameraCapture.FIRST_RETRY_NANOS);
            until(() -> { capture.request(settings); return opens.get() == 2; });
            // A different setting opens immediately.
            capture.request(new HandCameraSettings(true, "camera", 320, 240, 30, false));
            until(() -> opens.get() == 3);
        }
    }
    @Test void aScreenWithNoCameraDemandClosesTheDeviceAndTheNextScreenCanReopen() throws Exception {
        var config = new bms.player.beatoraja.Config();
        config.setHandCameraEnabled(true);
        config.setHandCameraDevice("camera");
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<Pipe> latest = new AtomicReference<>();
        CameraCapture capture = new CameraCapture(s -> {
            Pipe pipe = new Pipe(); latest.set(pipe); opens.incrementAndGet(); return pipe;
        });
        try (HandCameraManager manager = new HandCameraManager(config, capture)) {
            manager.beginFrame(); manager.endFrame();
            assertEquals(0, opens.get());
            manager.beginFrame(); assertNull(manager.requestTexture()); manager.endFrame();
            until(() -> opens.get() == 1);
            Pipe first = latest.get();
            manager.beginFrame(); manager.endFrame();
            assertTrue(first.closed.get());
            manager.beginFrame(); assertNull(manager.requestTexture()); manager.endFrame();
            until(() -> opens.get() == 2);
        }
        assertTrue(latest.get().closed.get());
    }
}
