package bms.player.beatoraja.select;

import bms.model.BMSModel;
import bms.model.ChartInformation;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.Note;
import bms.model.TimeLine;
import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.audio.AudioDriver;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.song.SongInformation;
import bms.player.beatoraja.song.SongResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewMusicProcessorTest {

    @TempDir
    Path temporary;

    @Test
    void missingSongPathSkipsPreview() {
        SongData song = new SongData();
        song.setPreview("preview.ogg");

        assertEquals("", PreviewMusicProcessor.resolvePreviewPath(song));
    }

    @Test
    void localSongPathResolvesPreviewBesideTheChart() {
        SongData song = new SongData();
        song.setPath(Paths.get("songs", "test", "chart.bms").toString());
        song.setPreview("preview.ogg");

        assertEquals(
                Paths.get("songs", "test", "preview.ogg").toString(),
                PreviewMusicProcessor.resolvePreviewPath(song)
        );
    }

    @Test
    void archiveSongPathResolvesPreviewInsideTheArchive() {
        SongData song = new SongData();
        song.setPath(Paths.get("songs", "pack.zip!-Pack", "chart.bms").toString());
        song.setPreview("preview.ogg");

        assertEquals(
                Paths.get("songs", "pack.zip!-Pack", "preview.ogg").toAbsolutePath().toString(),
                PreviewMusicProcessor.resolvePreviewPath(song)
        );
    }

    @Test
    void existingExplicitPreviewWinsWithoutLoadingTheChart() throws Exception {
        Path chart = temporary.resolve("chart.bms");
        Path explicit = temporary.resolve("preview.wav");
        Files.writeString(chart, "#TITLE explicit");
        Files.write(explicit, new byte[]{1});
        SongData song = new SongData();
        song.setPath(chart.toString());
        song.setPreview(explicit.getFileName().toString());
        RecordingAudioDriver audio = new RecordingAudioDriver();
        AtomicInteger modelLoads = new AtomicInteger();
        PreviewMusicProcessor processor = new PreviewMusicProcessor(
                audio,
                config(),
                ignored -> {
                    modelLoads.incrementAndGet();
                    return null;
                });

        try {
            processor.start(song);
            assertTrue(audio.resourcePlayed.await(2, TimeUnit.SECONDS));
            assertEquals(0, modelLoads.get());
            assertEquals(explicit.toString(), audio.lastResource.displayPath());
        } finally {
            processor.stop();
        }
    }

    @Test
    void explicitOneShotReturnsToDefaultWhenPlaybackPollingStaysTrue() throws Exception {
        Path chart = temporary.resolve("one-shot.bms");
        Path explicit = temporary.resolve("one-shot-preview.wav");
        Files.writeString(chart, "#TITLE one shot");
        Files.write(explicit, new byte[]{1});
        SongData song = new SongData();
        song.setPath(chart.toString());
        song.setPreview(explicit.getFileName().toString());
        RecordingAudioDriver audio = new RecordingAudioDriver();
        audio.resourceDurationMillis = 25L;
        PreviewMusicProcessor processor = new PreviewMusicProcessor(
                audio,
                config(Config.SongPreview.ONCE));

        try {
            processor.setDefault("select.wav");
            processor.start(song);
            assertTrue(audio.resourcePlayed.await(2, TimeUnit.SECONDS));
            assertTrue(audio.defaultMusicRestored.await(2, TimeUnit.SECONDS));
            assertTrue(audio.stopped.contains(audio.lastResource.cacheKey()));
        } finally {
            processor.stop();
        }
    }

    @Test
    void explicitLoopDoesNotReturnToDefaultAtTheResourceDuration() throws Exception {
        Path chart = temporary.resolve("loop.bms");
        Path explicit = temporary.resolve("loop-preview.wav");
        Files.writeString(chart, "#TITLE loop");
        Files.write(explicit, new byte[]{1});
        SongData song = new SongData();
        song.setPath(chart.toString());
        song.setPreview(explicit.getFileName().toString());
        RecordingAudioDriver audio = new RecordingAudioDriver();
        audio.resourceDurationMillis = 25L;
        PreviewMusicProcessor processor = new PreviewMusicProcessor(
                audio,
                config(Config.SongPreview.LOOP));

        try {
            processor.setDefault("select.wav");
            processor.start(song);
            assertTrue(audio.resourcePlayed.await(2, TimeUnit.SECONDS));
            assertFalse(audio.defaultMusicRestored.await(250, TimeUnit.MILLISECONDS));
        } finally {
            processor.stop();
        }
    }

    @Test
    void absentPreviewGeneratesAnInMemoryResource() throws Exception {
        Path chart = temporary.resolve("generated.bms");
        Files.writeString(chart, "#TITLE generated");
        Files.write(temporary.resolve("tone.wav"), eightBitWave());
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setWavList(new String[]{"tone.wav"});
        model.setChartInformation(new ChartInformation(
                chart,
                BMSModel.LNTYPE_LONGNOTE,
                null));
        TimeLine line = new TimeLine(0.1, 100_000, Mode.BEAT_7K.key);
        line.setNote(0, new NormalNote(0));
        model.setAllTimeLine(new TimeLine[]{line});
        SongData song = new SongData();
        song.setPath(chart.toString());
        song.setLength(1_000);
        SongInformation information = new SongInformation();
        information.setDistributionValues(new int[][]{{0, 0, 0, 0, 0, 1, 0}});
        song.setInformation(information);
        RecordingAudioDriver audio = new RecordingAudioDriver();
        PreviewMusicProcessor processor = new PreviewMusicProcessor(
                audio,
                config(),
                ignored -> model);

        try {
            processor.start(song);
            assertTrue(audio.resourcePlayed.await(4, TimeUnit.SECONDS));
            assertTrue(audio.lastResource instanceof GeneratedPreviewResource);
            assertTrue(audio.lastResource.size() > 44);
        } finally {
            processor.stop();
        }
    }

    @Test
    void shutdownDuringPreviewFadeStopsDefaultMusicBeforeReturning() throws Exception {
        Path chart = temporary.resolve("fade-race.bms");
        Path explicit = temporary.resolve("fade-race-preview.wav");
        Files.writeString(chart, "#TITLE fade race");
        Files.write(explicit, new byte[]{1});
        SongData song = new SongData();
        song.setPath(chart.toString());
        song.setPreview(explicit.getFileName().toString());
        RecordingAudioDriver audio = new RecordingAudioDriver();
        audio.blockDefaultFade = true;
        PreviewMusicProcessor processor = new PreviewMusicProcessor(audio, config());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> stopTask = null;

        try {
            processor.setDefault("select.wav");
            processor.start(song);
            assertTrue(audio.defaultFadeStarted.await(2, TimeUnit.SECONDS));

            stopTask = executor.submit(() -> {
                audio.stopCallStarted.countDown();
                processor.stop();
            });
            assertTrue(audio.stopCallStarted.await(2, TimeUnit.SECONDS));
            audio.releaseDefaultFade.countDown();
            stopTask.get(2, TimeUnit.SECONDS);

            assertTrue(audio.stopped.contains("select.wav"));
            assertEquals(0, audio.resourcePlayCount.get());
        } finally {
            audio.releaseDefaultFade.countDown();
            if (stopTask != null) {
                stopTask.get(2, TimeUnit.SECONDS);
            }
            processor.stop();
            executor.shutdownNow();
        }
    }

    @Test
    void shutdownStopsActivePreviewAndMutedDefaultMusic() throws Exception {
        Path chart = temporary.resolve("active-preview.bms");
        Path explicit = temporary.resolve("active-preview.wav");
        Files.writeString(chart, "#TITLE active preview");
        Files.write(explicit, new byte[]{1});
        SongData song = new SongData();
        song.setPath(chart.toString());
        song.setPreview(explicit.getFileName().toString());
        RecordingAudioDriver audio = new RecordingAudioDriver();
        PreviewMusicProcessor processor = new PreviewMusicProcessor(audio, config());

        try {
            processor.setDefault("select.wav");
            processor.start(song);
            assertTrue(audio.resourcePlayed.await(2, TimeUnit.SECONDS));
            String previewKey = audio.lastResource.cacheKey();

            processor.stop();

            assertTrue(audio.stopped.contains(previewKey));
            assertTrue(audio.stopped.contains("select.wav"));
        } finally {
            processor.stop();
        }
    }

    private static byte[] eightBitWave() {
        int sampleRate = 8_000;
        int samples = sampleRate;
        ByteBuffer wave = ByteBuffer.allocate(44 + samples).order(ByteOrder.LITTLE_ENDIAN);
        wave.putInt(0x46464952).putInt(36 + samples).putInt(0x45564157);
        wave.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1);
        wave.putInt(sampleRate).putInt(sampleRate).putShort((short) 1).putShort((short) 8);
        wave.putInt(0x61746164).putInt(samples);
        for (int index = 0; index < samples; index++) {
            wave.put((byte) 180);
        }
        return wave.array();
    }

    private static Config config() {
        return config(Config.SongPreview.LOOP);
    }

    private static Config config(Config.SongPreview songPreview) {
        Config config = new Config();
        config.setAudioConfig(new AudioConfig());
        config.setSongPreview(songPreview);
        return config;
    }

    private static final class RecordingAudioDriver implements AudioDriver {
        private final CountDownLatch resourcePlayed = new CountDownLatch(1);
        private final CountDownLatch defaultFadeStarted = new CountDownLatch(1);
        private final CountDownLatch releaseDefaultFade = new CountDownLatch(1);
        private final CountDownLatch stopCallStarted = new CountDownLatch(1);
        private final CountDownLatch defaultMusicRestored = new CountDownLatch(1);
        private final Set<String> stopped = ConcurrentHashMap.newKeySet();
        private final AtomicInteger resourcePlayCount = new AtomicInteger();
        private volatile SongResource lastResource;
        private volatile boolean blockDefaultFade;
        private volatile long resourceDurationMillis = -1L;
        private float pitch = 1.0f;

        @Override
        public void play(String path, float volume, boolean loop) {
        }

        @Override
        public void play(SongResource resource, float volume, boolean loop) {
            lastResource = resource;
            resourcePlayCount.incrementAndGet();
            resourcePlayed.countDown();
        }

        @Override
        public long getDurationMillis(SongResource resource) {
            return resourceDurationMillis;
        }

        @Override
        public void setVolume(String path, float volume) {
            if ("select.wav".equals(path)
                    && resourcePlayCount.get() > 0
                    && volume > 0.0f) {
                defaultMusicRestored.countDown();
            }
            if (blockDefaultFade && "select.wav".equals(path)) {
                defaultFadeStarted.countDown();
                boolean interrupted = false;
                while (releaseDefaultFade.getCount() > 0) {
                    try {
                        releaseDefaultFade.await();
                    } catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        @Override public boolean isPlaying(String path) { return true; }
        @Override public void stop(String path) { stopped.add(path); }
        @Override public void dispose(String path) {}
        @Override public void setModel(BMSModel model) {}
        @Override public void setAdditionalKeySound(int judge, boolean fast, String path) {}
        @Override public void abort() {}
        @Override public float getProgress() { return 1.0f; }
        @Override public void play(Note note, float volume, int pitch) {}
        @Override public void play(int judge, boolean fast) {}
        @Override public void stop(Note note) {}
        @Override public void setVolume(Note note, float volume) {}
        @Override public void setGlobalPitch(float pitch) { this.pitch = pitch; }
        @Override public float getGlobalPitch() { return pitch; }
        @Override public void disposeOld() {}
        @Override public void dispose() {}
    }
}
