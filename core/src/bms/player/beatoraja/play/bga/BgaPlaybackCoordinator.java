package bms.player.beatoraja.play.bga;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.PerformanceMetrics;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.system.TimingDiagnostics;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Render-owned scheduling and upload; a fixed set of workers owns all native sessions. */
final class BgaPlaybackCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(BgaPlaybackCoordinator.class);
    interface Decoder extends AutoCloseable {
        DecodedFrameQueue queue();
        void seek(long targetUs, long generation) throws Exception;
        void step(long targetUs, boolean loop) throws Exception;
        void close() throws Exception;
    }
    interface Factory { Decoder open(Movie movie) throws Exception; }
    record Playback(long generation, long token, long startMs, long notBeforeNanos, boolean loop) {}
    private record Binding(Worker worker, DecodedFrameQueue queue) {}
    final BgaQualityProfile profile;
    final BgaPerformanceMetrics metrics;
    private final BgaMemoryBudget memory;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong tokens = new AtomicLong();
    private final Worker[] workers;
    private final Factory factory;
    private volatile boolean disposed;
    private long tick, uploadSpentNanos;
    private long budgetFrame = Long.MIN_VALUE;
    private long lastTimeMs = -1;
    private long lastTickNanos;
    private boolean debounceSeek;
    private int displayWidth, displayHeight;

    BgaPlaybackCoordinator(Config config) {
        this(config, null);
    }
    BgaPlaybackCoordinator(Config config, Factory factory) {
        profile = BgaQualityProfile.from(config);
        metrics = new BgaPerformanceMetrics(config.isBgaShowPerformanceStats()
                || config.isTimingDiagnostics() || TimingDiagnostics.isEnabled());
        PerformanceMetrics.get().bga = metrics;
        memory = new BgaMemoryBudget(profile.memoryBytes(), metrics.nativeBytes);
        displayWidth = config.getResolution().width;
        displayHeight = config.getResolution().height;
        this.factory = factory != null ? factory : movie -> new FFmpegVideoDecoder(movie.resource,
                profile, metrics, memory, movie.displayWidth, movie.displayHeight);
        workers = new Worker[profile.decoders()];
        for (int i = 0; i < workers.length; i++) workers[i] = new Worker(i);
    }
    Movie movie(SongResource resource) { return new Movie(resource); }
    /** Can run on the model loader: no GL, join, or decoder lock. */
    void reset() {
        generation.incrementAndGet();
        for (Worker worker : workers) { worker.desired = null; worker.wake(); }
    }
    /** GL only. Lower numeric priority wins, current main/miss before layers/preloads. */
    void beginTick(long timeMs) {
        beginTick(timeMs, tick + 1);
    }
    void beginTick(long timeMs, long renderFrameId) {
        long now = System.nanoTime();
        long wallMs = lastTickNanos == 0 ? 0 : (now - lastTickNanos) / 1_000_000;
        debounceSeek = timeMs >= 0 && lastTimeMs >= 0
                && (timeMs < lastTimeMs || timeMs - lastTimeMs > wallMs + 100);
        if (debounceSeek) reset();
        lastTimeMs = timeMs;
        lastTickNanos = now;
        tick++;
        if (renderFrameId != budgetFrame) {
            budgetFrame = renderFrameId;
            uploadSpentNanos = 0;
        }
        for (Worker worker : workers) worker.priority = Integer.MAX_VALUE;
    }
    void viewport(int width, int height) {
        if (width > 0 && height > 0) { displayWidth = width; displayHeight = height; }
    }
    boolean request(Movie movie, long timeMs, long startMs, int priority, boolean preload) {
        if (disposed || movie.disposed || movie.failed || movie.failureReported.get() || profile.fps() == 0) return false;
        Worker selected = null;
        for (Worker worker : workers) if (worker.desired == movie) { selected = worker; break; }
        // A speculative request must never reset an already admitted current playback.
        if (selected != null && selected.seen == tick && selected.priority < priority) return false;
        movie.targetUs = preload ? 0 : Math.max(0, timeMs - startMs) * 1000;
        movie.preload = preload;
        Playback before = movie.playback;
        if (before == null || before.generation != generation.get() || before.startMs != startMs) {
            metrics.increment(BgaPerformanceMetrics.Counter.SEEK_REQUESTED);
            if (before != null && movie.processedToken != before.token)
                metrics.increment(BgaPerformanceMetrics.Counter.SEEK_COALESCED);
            movie.playback = new Playback(generation.get(), tokens.incrementAndGet(), startMs,
                    debounceSeek ? System.nanoTime() + 75_000_000L : 0, false);
        }
        if (selected == null) {
            for (Worker worker : workers) {
                if (worker.priority > priority && (selected == null || worker.priority > selected.priority)) selected = worker;
            }
            if (selected == null) return false;
            Movie previous = selected.renderMovie;
            if (previous != null && previous != movie) previous.disposeTexture();
            selected.renderMovie = movie; // Kept separately so a loader reset cannot lose GL ownership.
            movie.displayWidth = displayWidth;
            movie.displayHeight = displayHeight;
            Binding binding = movie.frames.get();
            if (binding == null || binding.worker != selected) movie.frames.set(new Binding(selected, null));
            selected.desired = movie;
            selected.startWorker();
        }
        selected.priority = Math.min(priority, selected.priority);
        selected.seen = tick;
        selected.wake();
        return true;
    }
    void endTick() {
        long preloads = 0, depth = 0;
        for (Worker worker : workers) {
            Movie movie = worker.desired;
            if (movie == null) {
                if (worker.renderMovie != null) worker.renderMovie.disposeTexture();
                worker.renderMovie = null;
                continue;
            }
            if (worker.seen != tick) {
                worker.desired = null;
                movie.disposeTexture();
                worker.renderMovie = null;
                worker.wake();
            } else {
                if (movie.preload) preloads++;
                DecodedFrameQueue queue = movie.queue();
                if (queue != null) depth += queue.depth();
            }
        }
        metrics.preloadedDecoders.set(preloads);
        metrics.queueDepth.set(depth);
    }
    void dispose() {
        disposed = true;
        reset();
        for (Worker worker : workers) {
            if (worker.renderMovie != null) worker.renderMovie.disposeTexture();
            worker.renderMovie = null;
            worker.wake();
        }
    }

    final class Movie implements MovieProcessor {
        final SongResource resource;
        volatile Playback playback;
        private final AtomicReference<Binding> frames = new AtomicReference<>();
        volatile long targetUs, processedToken;
        volatile boolean failed, disposed, preload;
        private final AtomicBoolean failureReported;
        private Movie layerVariant, missVariant;
        int displayWidth, displayHeight;
        private Pixmap uploadPixels;
        private Texture texture;
        private long uploadedToken = -1, uploadedPts = -1;
        private long lastUploadNanos;
        Movie(SongResource resource) { this(resource, new AtomicBoolean()); }
        private Movie(SongResource resource, AtomicBoolean failureReported) {
            this.resource = resource; this.failureReported = failureReported;
        }
        /** GL-owned independent timelines when the same BGA ID appears on multiple tracks. */
        Movie variant(int role) {
            if (role == 1) {
                if (layerVariant == null) layerVariant = new Movie(resource, failureReported);
                return layerVariant;
            }
            if (role == 2) {
                if (missVariant == null) missVariant = new Movie(resource, failureReported);
                return missVariant;
            }
            return this;
        }
        DecodedFrameQueue queue() {
            Binding binding = frames.get();
            return binding == null ? null : binding.queue;
        }
        public void play(long time, boolean loop) { request(this, time, time, 1, false); }
        public void stop() {
            playback = null; uploadedToken = -1;
            if (layerVariant != null) layerVariant.stop();
            if (missVariant != null) missVariant.stop();
        }
        public void dispose() {
            disposed = true; stop(); disposeTexture();
            if (layerVariant != null) layerVariant.dispose();
            if (missVariant != null) missVariant.dispose();
        }
        void disposeTexture() {
            Texture oldTexture = texture;
            Pixmap oldPixels = uploadPixels;
            texture = null;
            uploadPixels = null;
            uploadedToken = -1;
            uploadedPts = -1;
            lastUploadNanos = 0;
            try { if (oldTexture != null) oldTexture.dispose(); }
            catch (RuntimeException error) { fail(this, error); }
            finally {
                try { if (oldPixels != null) oldPixels.dispose(); }
                catch (RuntimeException error) { fail(this, error); }
            }
        }
        /** GL only. The pixel lease is acquired with CAS and released in finally. */
        public Texture getFrame(long time) {
            Playback playback = this.playback;
            if (disposed || failed || failureReported.get() || playback == null || playback.generation != generation.get()) return null;
            DecodedFrameQueue queue = queue();
            long now = System.nanoTime();
            long period = 1_000_000_000L / profile.fps();
            if (queue != null && (uploadedToken != playback.token || now - lastUploadNanos >= period)
                    && uploadSpentNanos < 2_000_000L) {
                long pollStarted = metrics.start();
                DecodedFrameQueue.Slot frame = queue.pollLatestAtOrBefore(targetUs, playback.token);
                metrics.finish(BgaPerformanceMetrics.Metric.POLL_FRAME, pollStarted);
                metrics.record(BgaPerformanceMetrics.Metric.RENDER_WAIT, 0);
                if (frame != null) {
                    long started = System.nanoTime();
                    try {
                        if (frame.ptsUs > uploadedPts || uploadedToken != playback.token) {
                            if (uploadPixels != null && (uploadPixels.getWidth() != queue.width || uploadPixels.getHeight() != queue.height)) {
                                // A session's size is frozen. A replacement session gets its own GL allocation.
                                disposeTexture();
                            }
                            if (uploadPixels == null) {
                                uploadPixels = new Pixmap(queue.width, queue.height, Pixmap.Format.RGB888);
                            } else metrics.increment(BgaPerformanceMetrics.Counter.REUSED_TEXTURE);
                            uploadPixels.getPixels().clear();
                            frame.pixels.position(0);
                            uploadPixels.getPixels().put(frame.pixels);
                            uploadPixels.getPixels().position(0);
                            if (texture == null) texture = new Texture(uploadPixels);
                            else texture.draw(uploadPixels, 0, 0);
                            uploadedToken = playback.token;
                            uploadedPts = frame.ptsUs;
                            lastUploadNanos = now;
                            metrics.increment(BgaPerformanceMetrics.Counter.UPLOADED);
                        }
                    } catch (RuntimeException error) { fail(this, error); }
                    finally {
                        queue.release(frame);
                        long elapsed = System.nanoTime() - started;
                        uploadSpentNanos += elapsed;
                        metrics.record(BgaPerformanceMetrics.Metric.UPLOAD, elapsed);
                        if (uploadSpentNanos > 2_000_000L && uploadSpentNanos - elapsed <= 2_000_000L)
                            metrics.increment(BgaPerformanceMetrics.Counter.UPLOAD_BUDGET_EXCEEDED);
                    }
                }
            }
            return failed || failureReported.get() || this.playback != playback || playback.generation != generation.get()
                    || uploadedToken != playback.token ? null : texture;
        }
    }
    private void fail(Movie movie, Throwable error) {
        movie.failed = true;
        if (movie.failureReported.compareAndSet(false, true)) {
            metrics.increment(BgaPerformanceMetrics.Counter.ERRORS);
            logger.warn("BGA resource disabled: {}", movie.resource.displayPath(), error);
        }
    }
    private final class Worker extends Thread {
        volatile Movie desired;
        Movie renderMovie;
        int priority = Integer.MAX_VALUE;
        long seen;
        boolean started;
        Worker(int index) { super("bga-decode-" + index); setDaemon(true); }
        void startWorker() { if (!started) { started = true; start(); } }
        void wake() { LockSupport.unpark(this); }
        public void run() {
            Decoder decoder = null;
            Movie current = null;
            long token = -1;
            try {
                while (!disposed) {
                    Movie next = desired;
                    if (next != null && (next.failed || next.failureReported.get() || next.disposed || next.playback == null
                            || next.playback.generation != generation.get())) next = null;
                    if (next != current) {
                        if (current != null && decoder != null) clearQueue(current, decoder);
                        if (decoder != null) { closeDecoder(decoder, current); decoder = null; }
                        current = next;
                        token = -1;
                        if (current != null) {
                            try {
                                decoder = factory.open(current);
                                publishQueue(current, decoder);
                                metrics.activeDecoders.incrementAndGet();
                                TimingDiagnostics.movieDecoderStarted();
                            } catch (Exception | LinkageError error) { fail(current, error); }
                        }
                    }
                    if (decoder != null && current != null) {
                        publishQueue(current, decoder);
                        Playback request = current.playback;
                        if (request != null && request.generation == generation.get()
                                && System.nanoTime() >= request.notBeforeNanos) {
                            try {
                                if (token != request.token) {
                                    decoder.seek(current.targetUs, request.token);
                                    token = request.token;
                                    current.processedToken = token;
                                }
                                decoder.step(current.targetUs, request.loop);
                            } catch (Exception | LinkageError error) { fail(current, error); }
                        }
                    }
                    LockSupport.parkNanos(1_000_000L);
                }
            } finally {
                if (current != null && decoder != null) clearQueue(current, decoder);
                if (decoder != null) closeDecoder(decoder, current);
            }
        }
        private void publishQueue(Movie movie, Decoder decoder) {
            Binding binding = movie.frames.get();
            if (binding != null && binding.worker == this && desired == movie && binding.queue != decoder.queue())
                movie.frames.compareAndSet(binding, new Binding(this, decoder.queue()));
        }
        private void clearQueue(Movie movie, Decoder decoder) {
            Binding binding = movie.frames.get();
            if (binding != null && binding.worker == this && binding.queue == decoder.queue())
                movie.frames.compareAndSet(binding, new Binding(this, null));
        }
        private void closeDecoder(Decoder decoder, Movie movie) {
            try { decoder.close(); }
            catch (Exception | LinkageError error) { if (movie != null) fail(movie, error); }
            finally { metrics.activeDecoders.decrementAndGet(); TimingDiagnostics.movieDecoderStopped(); }
        }
    }
}
