package bms.player.beatoraja.play.bga;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.song.SongResources;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_FFV1;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR0;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24;
import static org.junit.jupiter.api.Assertions.*;

class FFmpegVideoDecoderTest {
    @TempDir Path directory;
    @Test void rgbCopyHonorsPaddedStrideAndRejectsChangedDimensions() {
        Frame frame = new Frame(); frame.imageWidth = 3; frame.imageHeight = 2;
        frame.imageStride = 16; frame.imageChannels = 3;
        ByteBuffer source = ByteBuffer.allocate(32);
        for (int n = 0; n < 9; n++) { source.put(n, (byte) n); source.put(16+n, (byte) (10+n)); }
        frame.image = new Buffer[]{source};
        ByteBuffer target = ByteBuffer.allocate(18);
        FFmpegVideoDecoder.copyRgb(frame,target,3,2);
        for (int n = 0; n < 9; n++) { assertEquals(n, target.get(n)); assertEquals(10+n,target.get(9+n)); }
        assertThrows(IllegalArgumentException.class, () -> FFmpegVideoDecoder.copyRgb(frame,target,2,2));
    }
    @Test void realNativeDecodePreservesRgbPreloadsSeeksAndReleasesBudget() throws Exception {
        Path movie = directory.resolve("self-authored.mkv");
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(movie.toFile(),64,48);
                Frame frame = new Frame(64,48,Frame.DEPTH_UBYTE,3)) {
            recorder.setFormat("matroska"); recorder.setVideoCodec(AV_CODEC_ID_FFV1);
            recorder.setPixelFormat(AV_PIX_FMT_BGR0); recorder.setFrameRate(30); recorder.start();
            ByteBuffer pixels = (ByteBuffer) frame.image[0];
            for (int n = 0; n < 30; n++) {
                for (int row = 0; row < 48; row++) for (int col = 0; col < 64; col++) {
                    int p = row * frame.imageStride + col * 3;
                    pixels.put(p,(byte)n); pixels.put(p+1,(byte)100); pixels.put(p+2,(byte)200);
                }
                recorder.record(frame,AV_PIX_FMT_RGB24);
            }
        }
        Config config = new Config(); var profile = BgaQualityProfile.from(config);
        var metrics = new BgaPerformanceMetrics(true); var bytes = new AtomicLong();
        try (var decoder = new FFmpegVideoDecoder(SongResources.local(movie),profile,metrics,
                new BgaMemoryBudget(profile.memoryBytes(),bytes),64,48)) {
            decoder.seek(0,1); decoder.step(0,false);
            var queue = decoder.queue(); var first = queue.pollLatestAtOrBefore(0,1);
            assertNotNull(first);
            try {
                assertEquals(0, first.pixels.get(0) & 255); assertEquals(100,first.pixels.get(1) & 255);
                assertEquals(200,first.pixels.get(2) & 255);
            } finally { queue.release(first); }
            assertTrue(bytes.get() > 0 && bytes.get() <= profile.memoryBytes());
            decoder.seek(500_000,2);
            for (int n = 0; n < 10; n++) decoder.step(700_000,false);
            var later = queue.pollLatestAtOrBefore(700_000,2); assertNotNull(later);
            assertTrue(later.ptsUs >= 500_000); queue.release(later);
            decoder.seek(0,3); decoder.step(0,false);
            var retry = queue.pollLatestAtOrBefore(0,3); assertNotNull(retry);
            assertEquals(3,retry.generation); queue.release(retry);
        }
        assertEquals(0,bytes.get());
    }
    @Test void invalidMovieClosesPartialSessionWithoutRetainingBudget() throws Exception {
        Path broken = directory.resolve("broken.mkv"); java.nio.file.Files.writeString(broken,"not a video");
        var profile = BgaQualityProfile.from(new Config()); var bytes = new AtomicLong();
        assertThrows(Exception.class, () -> new FFmpegVideoDecoder(SongResources.local(broken),profile,
                new BgaPerformanceMetrics(false),new BgaMemoryBudget(profile.memoryBytes(),bytes),64,48));
        assertEquals(0,bytes.get());
    }
}
