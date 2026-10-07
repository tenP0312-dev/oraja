package bms.player.beatoraja.generated;

import bms.player.beatoraja.play.bga.FFmpegNativeLoader;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VideoAudioExtractorTest {
    @TempDir
    Path directory;

    @Test
    void recognisesMovieFiles() {
        assertTrue(VideoAudioExtractor.isVideoFile(Path.of("a/clip.MP4")));
        assertTrue(VideoAudioExtractor.isVideoFile(Path.of("clip.webm")));
        assertFalse(VideoAudioExtractor.isVideoFile(Path.of("song.mp3")));
        assertTrue(AudioChartSession.isSupportedFile(Path.of("clip.mp4")));
        assertTrue(AudioChartSession.isSupportedFile(Path.of("song.ogg")));
        assertFalse(AudioChartSession.isSupportedFile(Path.of("chart.bms")));
    }

    @Test
    void wavHeaderDescribes16BitStereo() {
        ByteBuffer header = ByteBuffer.wrap(VideoAudioExtractor.wavHeader(44100)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(36 + 44100 * 4, header.getInt(4));
        assertEquals(2, header.getShort(22));
        assertEquals(44100, header.getInt(24));
        assertEquals(16, header.getShort(34));
        assertEquals(44100 * 4, header.getInt(40));
    }

    /** A 2 s MP4 with a white flash and a click both at 1.0 s. */
    private Path flashAndClick() throws Exception {
        Path video = directory.resolve("flash.mp4");
        int fps = 30;
        int sampleRate = 44100;
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(video.toFile(), 64, 48, 1)) {
            recorder.setFormat("mp4");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setFrameRate(fps);
            recorder.setSampleRate(sampleRate);
            recorder.setAudioBitrate(128_000);
            recorder.start();
            short[] audio = new short[2 * sampleRate];
            for (int index = 0; index < sampleRate / 30; index++) {
                audio[sampleRate + index] = (short) (Math.sin(2 * Math.PI * 1000 * index / sampleRate)
                        * Math.exp(-index / (double) sampleRate * 80) * 26000);
            }
            int perFrame = sampleRate / fps;
            for (int frameIndex = 0; frameIndex < 2 * fps; frameIndex++) {
                Frame image = new Frame(64, 48, Frame.DEPTH_UBYTE, 3);
                ByteBuffer pixels = (ByteBuffer) image.image[0];
                byte value = (byte) (frameIndex == fps ? 255 : 0);
                for (int index = 0; index < pixels.capacity(); index++) {
                    pixels.put(index, value);
                }
                recorder.record(image, avutil.AV_PIX_FMT_BGR24);
                recorder.recordSamples(sampleRate, 1,
                        ShortBuffer.wrap(audio, frameIndex * perFrame, perFrame));
            }
            recorder.stop();
        }
        return video;
    }

    @Test
    void extractedAudioStartsWithTheFirstVideoFrame() throws Exception {
        Assumptions.assumeTrue(FFmpegNativeLoader.preload(), "FFmpeg natives for this platform build are unavailable");
        Path video = flashAndClick();
        Path wav = directory.resolve("audio.wav");
        VideoAudioExtractor.Extracted extracted = VideoAudioExtractor.extract(video, wav);
        assertTrue(extracted.hasVideo());
        byte[] data = Files.readAllBytes(wav);
        ShortBuffer pcm = ByteBuffer.wrap(data, 44, data.length - 44).slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int first = -1;
        for (int index = 0; index < pcm.limit(); index += 2) {
            if (Math.abs(pcm.get(index)) > 3000) {
                first = index / 2;
                break;
            }
        }
        assertTrue(first > 0, "no click in the extracted audio");
        // the flash is frame 30 = 1.0 s of video time; within one frame of video at 30 fps
        assertEquals(1.0, first / 44100.0, 1.0 / 30, "click vs flash");
    }
}
