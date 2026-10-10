package bms.player.beatoraja.play.bga;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.song.SongResources;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BgaPlaybackCoordinatorTest {
    static class FakeDecoder implements BgaPlaybackCoordinator.Decoder {
        final DecodedFrameQueue queue = new DecodedFrameQueue(3, 1, 1,
                new BgaPerformanceMetrics(false), DecodedFrameQueueTest.heap(new AtomicInteger()));
        final AtomicInteger live;
        volatile long generation;
        FakeDecoder(AtomicInteger live) { this.live = live; live.incrementAndGet(); }
        public DecodedFrameQueue queue() { return queue; }
        public void seek(long targetUs, long generation) { this.generation = generation; }
        public void step(long targetUs, boolean loop) {}
        public void close() { queue.close(); live.decrementAndGet(); }
    }
    static void await(java.util.function.BooleanSupplier done) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!done.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(done.getAsBoolean());
    }
    @Test void offNeverOpensEvenWhenExplicitlyRequested() throws Exception {
        Config config = new Config(); config.setBgaQualityMode(BgaQualityProfile.Mode.OFF);
        AtomicInteger opens = new AtomicInteger();
        var coordinator = new BgaPlaybackCoordinator(config, movie -> { opens.incrementAndGet(); throw new AssertionError(); });
        var movie = coordinator.movie(SongResources.local(Path.of("unused.mp4")));
        coordinator.beginTick(0); assertFalse(coordinator.request(movie,0,0,1,false));
        coordinator.endTick(); coordinator.dispose(); assertEquals(0, opens.get());
    }
    @Test void currentMainAndLayerWinOverPreloadAndWorkersStayBounded() throws Exception {
        var live = new AtomicInteger();
        var coordinator = new BgaPlaybackCoordinator(new Config(), movie -> new FakeDecoder(live));
        var main = coordinator.movie(SongResources.local(Path.of("main.mp4")));
        var layer = coordinator.movie(SongResources.local(Path.of("layer.mp4")));
        var next = coordinator.movie(SongResources.local(Path.of("next.mp4")));
        try {
            coordinator.beginTick(0);
            assertTrue(coordinator.request(main,0,0,1,false));
            assertTrue(coordinator.request(layer,0,0,2,false));
            assertFalse(coordinator.request(next,500,500,3,true));
            coordinator.endTick();
            await(() -> live.get() == 2);
            assertEquals(2, coordinator.metrics.activeDecoders.get());
        } finally { coordinator.dispose(); }
        await(() -> live.get() == 0);
    }
    @Test void blockedOpenDoesNotBlockRenderAndCannotOverwriteNewOwner() throws Exception {
        var firstEntered = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var first = new AtomicReference<FakeDecoder>();
        var second = new AtomicReference<FakeDecoder>();
        var opens = new AtomicInteger(); var live = new AtomicInteger();
        var coordinator = new BgaPlaybackCoordinator(new Config(), movie -> {
            var decoder = new FakeDecoder(live);
            int n = opens.incrementAndGet();
            if (n == 1) {
                first.set(decoder); firstEntered.countDown();
                assertTrue(releaseFirst.await(3, TimeUnit.SECONDS));
            } else if (n == 2) second.set(decoder);
            return decoder;
        });
        var a = coordinator.movie(SongResources.local(Path.of("a.mp4")));
        var b = coordinator.movie(SongResources.local(Path.of("b.mp4")));
        try {
            coordinator.beginTick(0); coordinator.request(a,0,0,1,false); coordinator.endTick();
            assertTrue(firstEntered.await(3, TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
                coordinator.beginTick(1);
                assertTrue(coordinator.request(b,1,0,1,false));
                assertTrue(coordinator.request(a,500,500,3,true));
                assertNull(a.getFrame(500)); coordinator.endTick();
            });
            await(() -> second.get() != null && a.queue() == second.get().queue);
            releaseFirst.countDown();
            await(() -> opens.get() >= 3 && b.queue() != null);
            assertSame(second.get().queue, a.queue());
            assertNotSame(first.get().queue, a.queue());
            assertTrue(live.get() <= 2);
        } finally { releaseFirst.countDown(); coordinator.dispose(); }
        await(() -> live.get() == 0);
    }
    @Test void repeatedRetryCoalescesToLastTokenWithoutWaitingForNativeOpen() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var live = new AtomicInteger(); var decoder = new AtomicReference<FakeDecoder>();
        Config config = new Config(); config.setBgaMaxActiveDecoders(1); config.setBgaShowPerformanceStats(true);
        var coordinator = new BgaPlaybackCoordinator(config, movie -> {
            var result = new FakeDecoder(live); decoder.set(result); entered.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS)); return result;
        });
        var movie = coordinator.movie(SongResources.local(Path.of("retry.mp4")));
        try {
            coordinator.beginTick(0); coordinator.request(movie,0,0,1,false); coordinator.endTick();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            long before = movie.playback.token();
            for (int i = 0; i < 100; i++) {
                coordinator.reset(); coordinator.beginTick(0);
                coordinator.request(movie,0,0,1,false); coordinator.endTick();
                assertNull(movie.getFrame(0));
            }
            long last = movie.playback.token(); assertTrue(last > before);
            release.countDown(); await(() -> movie.processedToken == last);
            assertEquals(last, decoder.get().generation);
            assertTrue(coordinator.metrics.count(BgaPerformanceMetrics.Counter.SEEK_COALESCED) >= 99);
        } finally { release.countDown(); coordinator.dispose(); }
        await(() -> live.get() == 0);
    }
    @Test void failureDisablesOnlyItsResourceAndDoesNotRetryEveryFrame() throws Exception {
        var opens = new AtomicInteger();
        var coordinator = new BgaPlaybackCoordinator(new Config(), movie -> {
            opens.incrementAndGet(); throw new java.io.IOException("broken test movie");
        });
        var bad = coordinator.movie(SongResources.local(Path.of("broken.mp4")));
        try {
            coordinator.beginTick(0); coordinator.request(bad,0,0,1,false); coordinator.endTick();
            await(() -> bad.failed);
            for (int i = 0; i < 100; i++) {
                coordinator.beginTick(i);
                assertFalse(coordinator.request(bad,i,0,1,false)); assertNull(bad.getFrame(i));
                coordinator.endTick();
            }
            assertEquals(1, opens.get());
        } finally { coordinator.dispose(); }
    }
    @Test void lowerPriorityPreloadCannotResetCurrentPlayback() throws Exception {
        var live = new AtomicInteger();
        var coordinator = new BgaPlaybackCoordinator(new Config(), movie -> new FakeDecoder(live));
        var movie = coordinator.movie(SongResources.local(Path.of("current.mp4")));
        try {
            coordinator.beginTick(250); assertTrue(coordinator.request(movie,250,100,1,false));
            long token = movie.playback.token();
            assertFalse(coordinator.request(movie,750,750,3,true));
            assertEquals(token,movie.playback.token()); assertEquals(150_000,movie.targetUs);
            assertFalse(movie.preload); coordinator.endTick();
        } finally { coordinator.dispose(); }
        await(() -> live.get() == 0);
    }
    @Test void sameVideoIdHasIndependentMainAndLayerStartTimes() throws Exception {
        var live = new AtomicInteger();
        var coordinator = new BgaPlaybackCoordinator(new Config(), movie -> new FakeDecoder(live));
        var main = coordinator.movie(SongResources.local(Path.of("both-tracks.mp4")));
        var layer = main.variant(1);
        try {
            assertNotSame(main,layer); assertSame(layer,main.variant(1));
            coordinator.beginTick(500);
            assertTrue(coordinator.request(main,500,100,1,false));
            assertTrue(coordinator.request(layer,500,450,2,false));
            assertEquals(400_000,main.targetUs); assertEquals(50_000,layer.targetUs);
            assertNotEquals(main.playback.token(),layer.playback.token());
            coordinator.endTick(); await(() -> live.get() == 2);
        } finally { coordinator.dispose(); }
        await(() -> live.get() == 0);
    }
}
