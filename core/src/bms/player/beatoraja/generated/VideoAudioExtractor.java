package bms.player.beatoraja.generated;

import bms.player.beatoraja.play.bga.FFmpegNativeLoader;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Extracts a video's audio track to a WAV aligned to the video (Issue #459).
 *
 * <p>The BGA decoders treat the first video frame's timestamp as video time 0,
 * so WAV time 0 is made to be that same instant: silence is prepended when the
 * audio starts later, and leading samples are dropped when it starts earlier.
 * A chart that starts the WAV and the movie together then plays them in sync.</p>
 */
public final class VideoAudioExtractor {

    /** The movie formats BGA plays (see BGAProcessor.mov_extension). */
    public static final Set<String> VIDEO_EXTENSIONS =
            Set.of("mp4", "m4v", "webm", "mpg", "mpeg", "m1v", "m2v", "avi", "wmv");
    static final int SAMPLE_RATE = 44100;
    static final int CHANNELS = 2;
    /** Longer videos are rejected: their audio alone would be hundreds of MB. */
    static final double MAX_SECONDS = 20 * 60;

    private VideoAudioExtractor() {
    }

    /**
     * @param videoStartSec timestamp of the first video frame (NaN without video)
     * @param audioStartSec timestamp of the first audio sample
     * @param shiftMs       silence prepended (positive) or audio dropped (negative)
     */
    public record Extracted(boolean hasVideo, double videoStartSec, double audioStartSec, double shiftMs,
            double durationSec) {
    }

    public static boolean isVideoFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && VIDEO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static Extracted extract(Path video, Path wav) throws IOException {
        if (!FFmpegNativeLoader.preload()) {
            throw new IOException("FFmpeg is not available");
        }
        long videoStart = firstVideoTimestamp(video);
        Path partial = wav.resolveSibling(wav.getFileName() + ".part");
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(video.toFile());
                RandomAccessFile out = new RandomAccessFile(partial.toFile(), "rw")) {
            grabber.setSampleMode(FrameGrabber.SampleMode.SHORT);
            grabber.setSampleRate(SAMPLE_RATE);
            grabber.setAudioChannels(CHANNELS);
            grabber.start();
            if (grabber.getAudioChannels() <= 0) {
                throw new IOException("the video has no audio track");
            }
            out.setLength(0);
            out.write(new byte[44]);
            long maxFrames = (long) (MAX_SECONDS * SAMPLE_RATE);
            long written = 0;
            long toDrop = 0;
            long audioStart = Long.MIN_VALUE;
            Frame frame;
            while ((frame = grabber.grabSamples()) != null) {
                if (frame.samples == null || frame.samples.length == 0) {
                    continue;
                }
                if (audioStart == Long.MIN_VALUE) {
                    audioStart = frame.timestamp;
                    long reference = videoStart == Long.MIN_VALUE ? audioStart : videoStart;
                    long shiftFrames = Math.round((audioStart - reference) * 1e-6 * SAMPLE_RATE);
                    if (shiftFrames > 0) {
                        written += writeSilence(out, shiftFrames);
                    } else {
                        toDrop = -shiftFrames;
                    }
                }
                short[] interleaved = interleave(frame);
                int frames = interleaved.length / CHANNELS;
                int skip = (int) Math.min(toDrop, frames);
                toDrop -= skip;
                if (frames - skip > 0) {
                    ByteBuffer bytes = ByteBuffer.allocate((frames - skip) * CHANNELS * 2).order(ByteOrder.LITTLE_ENDIAN);
                    for (int index = skip * CHANNELS; index < interleaved.length; index++) {
                        bytes.putShort(interleaved[index]);
                    }
                    out.write(bytes.array());
                    written += frames - skip;
                }
                if (written > maxFrames) {
                    throw new IOException("the video is longer than " + (int) (MAX_SECONDS / 60) + " minutes");
                }
            }
            grabber.stop();
            if (audioStart == Long.MIN_VALUE) {
                throw new IOException("the audio track is empty");
            }
            out.seek(0);
            out.write(wavHeader(written));
            long reference = videoStart == Long.MIN_VALUE ? audioStart : videoStart;
            Extracted result = new Extracted(
                    videoStart != Long.MIN_VALUE,
                    videoStart == Long.MIN_VALUE ? Double.NaN : videoStart / 1e6,
                    audioStart / 1e6,
                    (audioStart - reference) / 1e3,
                    written / (double) SAMPLE_RATE);
            out.close();
            Files.move(partial, wav, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return result;
        } catch (IOException exception) {
            Files.deleteIfExists(partial);
            throw exception;
        } catch (Exception exception) {
            Files.deleteIfExists(partial);
            throw new IOException(exception.getMessage(), exception);
        }
    }

    /** Timestamp (µs) of the first video frame, as the BGA decoders see it; MIN_VALUE without video. */
    static long firstVideoTimestamp(Path video) throws IOException {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(video.toFile())) {
            grabber.start();
            if (grabber.getVideoStream() < 0 && grabber.getImageWidth() <= 0) {
                return Long.MIN_VALUE;
            }
            Frame image = grabber.grabImage();
            long timestamp = image != null ? image.timestamp : Long.MIN_VALUE;
            grabber.stop();
            return timestamp;
        } catch (FrameGrabber.Exception exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    private static short[] interleave(Frame frame) {
        if (frame.samples.length == 1) {
            ShortBuffer buffer = (ShortBuffer) frame.samples[0];
            buffer.rewind();
            short[] packed = new short[buffer.remaining()];
            buffer.get(packed);
            if (frame.audioChannels == 1) {
                short[] stereo = new short[packed.length * 2];
                for (int index = 0; index < packed.length; index++) {
                    stereo[2 * index] = packed[index];
                    stereo[2 * index + 1] = packed[index];
                }
                return stereo;
            }
            return packed;
        }
        // planar: one buffer per channel
        ShortBuffer left = (ShortBuffer) frame.samples[0];
        ShortBuffer right = (ShortBuffer) frame.samples[1];
        left.rewind();
        right.rewind();
        int frames = Math.min(left.remaining(), right.remaining());
        short[] packed = new short[frames * 2];
        for (int index = 0; index < frames; index++) {
            packed[2 * index] = left.get();
            packed[2 * index + 1] = right.get();
        }
        return packed;
    }

    private static long writeSilence(RandomAccessFile out, long frames) throws IOException {
        byte[] block = new byte[4096 * CHANNELS * 2];
        long remaining = frames;
        while (remaining > 0) {
            int now = (int) Math.min(remaining, 4096);
            out.write(block, 0, now * CHANNELS * 2);
            remaining -= now;
        }
        return frames;
    }

    static byte[] wavHeader(long frames) {
        long dataBytes = frames * CHANNELS * 2;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt((int) (36 + dataBytes))
                .put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) CHANNELS).putInt(SAMPLE_RATE)
                .putInt(SAMPLE_RATE * CHANNELS * 2).putShort((short) (CHANNELS * 2)).putShort((short) 16)
                .put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt((int) dataBytes);
        return header.array();
    }
}
