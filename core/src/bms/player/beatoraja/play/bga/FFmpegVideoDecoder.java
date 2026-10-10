package bms.player.beatoraja.play.bga;

import java.nio.ByteBuffer;
import java.util.List;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.ImageMode;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24;

/** Worker-owned FFmpeg session. No Gdx, Pixmap, Texture or render callbacks. */
final class FFmpegVideoDecoder implements BgaPlaybackCoordinator.Decoder {
    private final BgaQualityProfile profile;
    private final BgaPerformanceMetrics metrics;
    private final BgaMemoryBudget budget;
    private SongResources.MaterializedBatch files;
    private FFmpegFrameGrabber grabber;
    private DecodedFrameQueue queue;
    private long reservedBytes;
    private long originUs = Long.MIN_VALUE, lastPtsUs = -1, lastPublishedUs = -1;
    private long generation;
    private boolean eof;

    FFmpegVideoDecoder(SongResource resource, BgaQualityProfile profile, BgaPerformanceMetrics metrics,
            BgaMemoryBudget budget, int displayWidth, int displayHeight) throws Exception {
        this.profile = profile;
        this.metrics = metrics;
        this.budget = budget;
        long started = metrics.start();
        try {
            // A per-session disk lease avoids whole-movie heap retention and cache eviction while open.
            files = SongResources.materializeBatch(List.of(resource));
            grabber = new FFmpegFrameGrabber(files.pathFor(resource).toFile());
            grabber.setVideoOption("threads", "1");
            grabber.setPixelFormat(AV_PIX_FMT_RGB24);
            grabber.setAudioChannels(0);
            // Discover source metadata without allocating a full-source RGB conversion buffer.
            grabber.setImageMode(ImageMode.RAW);
            grabber.start();
            int[] size = profile.dimensions(grabber.getImageWidth(), grabber.getImageHeight(), displayWidth, displayHeight);
            long perSessionPixels = profile.memoryBytes() / profile.decoders() / (3L * profile.queueLength() + 10);
            double memoryScale = Math.min(1, Math.sqrt(perSessionPixels / ((double) size[0] * size[1])));
            size[0] = Math.max(1, (int) (size[0] * memoryScale));
            size[1] = Math.max(1, (int) (size[1] * memoryScale));
            // JavaCV aligns RGB conversion rows by rounding pixel width up to 64.
            // Include that padding, upload Pixmap and estimated RGBA texture storage.
            long sessionLimit = profile.memoryBytes() / profile.decoders();
            long bytes = ownedBytes(size[0], size[1], profile.queueLength());
            while (bytes > sessionLimit) {
                double scale = Math.sqrt(sessionLimit / (double) bytes);
                size[0] = Math.max(1, (int) (size[0] * scale));
                size[1] = Math.max(1, (int) (size[1] * scale));
                bytes = ownedBytes(size[0], size[1], profile.queueLength());
            }
            if (!budget.reserve(bytes)) throw new IllegalStateException("BGA frame memory budget exhausted");
            reservedBytes = bytes;
            grabber.setImageWidth(size[0]);
            grabber.setImageHeight(size[1]);
            grabber.setImageMode(ImageMode.COLOR);
            queue = new DecodedFrameQueue(profile.queueLength(), size[0], size[1], metrics, DecodedFrameQueue.NATIVE);
        } catch (Exception | LinkageError error) {
            close();
            throw error;
        } finally { metrics.finish(BgaPerformanceMetrics.Metric.OPEN, started); }
    }
    public DecodedFrameQueue queue() { return queue; }
    private static long ownedBytes(int width, int height, int queueLength) {
        return (long) width * height * (3L * queueLength + 7)
                + ((width + 63L) / 64 * 64) * height * 3;
    }
    public void seek(long targetUs, long generation) throws Exception {
        long started = metrics.start();
        try {
            this.generation = generation;
            if (originUs != Long.MIN_VALUE) grabber.setVideoTimestamp(Math.max(0, originUs + targetUs));
            lastPtsUs = -1;
            lastPublishedUs = -1;
            eof = false;
        } finally { metrics.finish(BgaPerformanceMetrics.Metric.SEEK, started); }
    }
    /** One native step per worker iteration; leave a future frame in the fixed queue. */
    public void step(long targetUs, boolean loop) throws Exception {
        if (eof) {
            if (loop) { originUs = Long.MIN_VALUE; grabber.setVideoTimestamp(0); eof = false; lastPtsUs = -1; }
            else return;
        }
        long period = 1_000_000L / profile.fps();
        if (lastPtsUs > targetUs + period) return;
        if (lastPtsUs >= 0 && targetUs - lastPtsUs > 500_000) seek(targetUs, generation);
        long started = metrics.start();
        Frame frame;
        try { frame = grabber.grabImage(); }
        finally {
            metrics.finish(BgaPerformanceMetrics.Metric.DECODE, started);
        }
        if (frame == null) {
            eof = true;
            if (originUs == Long.MIN_VALUE) throw new java.io.IOException("Movie contains no image frames");
            return;
        }
        metrics.increment(BgaPerformanceMetrics.Counter.DECODED);
        if (originUs == Long.MIN_VALUE) originUs = frame.timestamp;
        long ptsUs = Math.max(0, frame.timestamp - originUs);
        lastPtsUs = ptsUs;
        if (profile.dropLateFrames() && ptsUs < targetUs - 250_000) {
            metrics.increment(BgaPerformanceMetrics.Counter.DROPPED_LATE);
            return;
        }
        if (lastPublishedUs >= 0 && ptsUs - lastPublishedUs < period) return;
        DecodedFrameQueue.Slot slot = queue.acquireWrite();
        if (slot == null) return;
        started = metrics.start();
        try {
            copyRgb(frame, slot.pixels, queue.width, queue.height);
            queue.publish(slot, generation, ptsUs);
            lastPublishedUs = ptsUs;
        } catch (RuntimeException error) {
            queue.cancelWrite(slot);
            throw error;
        } finally { metrics.finish(BgaPerformanceMetrics.Metric.CONVERT, started); }
    }
    static void copyRgb(Frame frame, ByteBuffer destination, int width, int height) {
        // This decoder explicitly requests RGB24. JavaCV derives imageChannels from
        // padded stride / width, so it is not a reliable pixel-format discriminator.
        if (frame.image == null || frame.image.length == 0 || !(frame.image[0] instanceof ByteBuffer source)
                || frame.imageWidth != width || frame.imageHeight != height || frame.imageDepth != Frame.DEPTH_UBYTE
                || frame.imageStride < width * 3) throw new IllegalArgumentException("Unsupported RGB frame layout");
        int savedLimit = source.limit();
        destination.clear();
        try {
            for (int row = 0; row < height; row++) {
                source.limit(savedLimit);
                source.position(row * frame.imageStride);
                source.limit(row * frame.imageStride + width * 3);
                destination.put(source);
            }
        } finally { source.limit(savedLimit); source.position(0); }
        destination.flip();
    }
    public void close() throws Exception {
        try {
            if (queue != null) { queue.close(); queue = null; }
        } finally {
            try { if (grabber != null) { grabber.close(); grabber = null; } }
            finally {
                if (files != null) { files.close(); files = null; }
                if (reservedBytes != 0) { budget.release(reservedBytes); reservedBytes = 0; }
            }
        }
    }
}
