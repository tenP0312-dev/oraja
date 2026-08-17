package bms.player.beatoraja.audio;

import bms.model.BMSModel;
import bms.model.ChartInformation;
import bms.model.MineNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import bms.player.beatoraja.song.SongResources;
import bms.player.beatoraja.song.archive.SongArchives;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedPreviewRendererTest {

    private static final int SAMPLE_RATE = 8_000;

    @TempDir
    Path temporary;

    @Test
    void rendersLayeredAndSlicedPlayableAudioInsideTheWindow() throws Exception {
        Path chart = temporary.resolve("chart.bmson");
        Files.writeString(chart, "{}");
        Files.write(temporary.resolve("tone.wav"), waveBytes(2_000, (short) 10_000));
        BMSModel model = model(chart, "tone.wav");
        TimeLine line = new TimeLine(0.1, 100_000, Mode.BEAT_7K.key);
        NormalNote primary = new NormalNote(-1);
        primary.addLayeredNote(new NormalNote(0, 500_000, 250_000));
        line.setNote(0, primary);
        model.setAllTimeLine(new TimeLine[]{line});

        GeneratedPreviewRenderer.RenderResult result =
                new GeneratedPreviewRenderer(SAMPLE_RATE, 1).render(model, 0, 1_000, () -> false);

        assertNotNull(result);
        assertTrue(hasAudio(result.pcmData()));
    }

    @Test
    void retainsLongBackgroundAudioThatStartedBeforeTheWindow() throws Exception {
        Path chart = temporary.resolve("chart.bms");
        Files.writeString(chart, "#TITLE test");
        Files.write(temporary.resolve("long.wav"), waveBytes(4_000, (short) 8_000));
        BMSModel model = model(chart, "long.wav");
        TimeLine line = new TimeLine(0.0, 0, Mode.BEAT_7K.key);
        line.addBackGroundNote(new NormalNote(0));
        model.setAllTimeLine(new TimeLine[]{line});

        GeneratedPreviewRenderer.RenderResult result =
                new GeneratedPreviewRenderer(SAMPLE_RATE, 1).render(model, 2_500, 1_000, () -> false);

        assertNotNull(result);
        assertTrue(hasAudio(result.pcmData()));
    }

    @Test
    void skipsInvisibleNotesAndMines() throws Exception {
        Path chart = temporary.resolve("hidden.bms");
        Files.writeString(chart, "#TITLE hidden");
        Files.write(temporary.resolve("tone.wav"), waveBytes(1_000, (short) 8_000));
        BMSModel model = model(chart, "tone.wav");
        TimeLine line = new TimeLine(0.1, 100_000, Mode.BEAT_7K.key);
        line.setHiddenNote(0, new NormalNote(0));
        line.setNote(1, new MineNote(0, 10));
        model.setAllTimeLine(new TimeLine[]{line});

        assertNull(new GeneratedPreviewRenderer(SAMPLE_RATE, 1)
                .render(model, 0, 1_000, () -> false));
    }

    @Test
    void readsRequiredAudioDirectlyFromAnArchiveResource() throws Exception {
        Path archive = temporary.resolve("song.zip");
        byte[] wave = waveBytes(1_000, (short) 7_000);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeEntry(output, "Pack/chart.bms", new byte[0]);
            writeEntry(output, "Pack/tone.wav", wave);
        }
        Path chart = SongArchives.virtualPath(archive, "Pack/chart.bms");
        BMSModel model = model(chart, "tone.wav");
        TimeLine line = new TimeLine(0.1, 100_000, Mode.BEAT_7K.key);
        line.setNote(0, new NormalNote(0));
        model.setAllTimeLine(new TimeLine[]{line});

        GeneratedPreviewRenderer.RenderResult result =
                new GeneratedPreviewRenderer(SAMPLE_RATE, 1).render(model, 0, 1_000, () -> false);

        assertNotNull(result);
        assertTrue(hasAudio(result.pcmData()));
    }

    @Test
    void cancellationDropsTheResultBeforeLoadingAudio() throws Exception {
        Path chart = temporary.resolve("cancel.bms");
        Files.writeString(chart, "#TITLE cancel");
        BMSModel model = model(chart, "missing.wav");
        TimeLine line = new TimeLine(0.1, 100_000, Mode.BEAT_7K.key);
        line.setNote(0, new NormalNote(0));
        model.setAllTimeLine(new TimeLine[]{line});

        assertNull(new GeneratedPreviewRenderer(SAMPLE_RATE, 1)
                .render(model, 0, 1_000, () -> true));
    }

    @Test
    void boundedPreviewDecodeUsesHeapPcm() throws Exception {
        Path sound = temporary.resolve("heap.wav");
        Files.write(sound, waveBytes(100, (short) 8_000));

        PCM pcm = PCM.loadBounded(
                SongResources.fromPath(sound),
                new TestAudioDriver(SAMPLE_RATE, 1),
                16_384L);

        assertTrue(pcm instanceof ShortPCM);
    }

    @Test
    void rejectsPlansAboveTheSoundCountLimit() throws Exception {
        BMSModel model = twoSoundModel("count");
        GeneratedPreviewRenderer.Limits limits = limits(
                1,
                1_000_000L,
                1_000_000L,
                1_000_000L,
                1_000_000L);

        assertNull(new GeneratedPreviewRenderer(SAMPLE_RATE, 1, limits)
                .render(model, 0, 1_000, () -> false));
    }

    @Test
    void rejectsAResourceAboveThePerSoundSourceLimit() throws Exception {
        BMSModel model = oneSoundModel("per-source", 100);
        long sourceBytes = Files.size(temporary.resolve("per-source.wav"));
        GeneratedPreviewRenderer.Limits limits = limits(
                8,
                sourceBytes - 1L,
                sourceBytes * 2L,
                1_000_000L,
                1_000_000L);

        assertNull(new GeneratedPreviewRenderer(SAMPLE_RATE, 1, limits)
                .render(model, 0, 1_000, () -> false));
    }

    @Test
    void rejectsResourcesAboveTheCumulativeSourceLimit() throws Exception {
        BMSModel model = twoSoundModel("source-total");
        long sourceBytes = Files.size(temporary.resolve("source-total-0.wav"));
        GeneratedPreviewRenderer.Limits limits = limits(
                8,
                sourceBytes,
                sourceBytes * 2L - 1L,
                1_000_000L,
                1_000_000L);

        assertNull(new GeneratedPreviewRenderer(SAMPLE_RATE, 1, limits)
                .render(model, 0, 1_000, () -> false));
    }

    @Test
    void rejectsAResourceAboveThePerSoundAllocationLimit() throws Exception {
        BMSModel model = oneSoundModel("allocation", 100);
        GeneratedPreviewRenderer.Limits limits = limits(
                8,
                1_000_000L,
                1_000_000L,
                2_399L,
                1_000_000L);

        assertNull(new GeneratedPreviewRenderer(SAMPLE_RATE, 1, limits)
                .render(model, 0, 1_000, () -> false));
    }

    @Test
    void rejectsSamplesAboveTheRetainedDecodedLimit() throws Exception {
        BMSModel model = twoSoundModel("decoded-total");
        GeneratedPreviewRenderer.Limits limits = limits(
                8,
                1_000_000L,
                1_000_000L,
                1_000_000L,
                3_199L);

        assertNull(new GeneratedPreviewRenderer(SAMPLE_RATE, 1, limits)
                .render(model, 0, 1_000, () -> false));
    }

    private BMSModel oneSoundModel(String name, int durationMs) throws Exception {
        Path chart = temporary.resolve(name + ".bms");
        Files.writeString(chart, "#TITLE " + name);
        Files.write(temporary.resolve(name + ".wav"), waveBytes(durationMs, (short) 8_000));
        BMSModel model = model(chart, name + ".wav");
        TimeLine line = new TimeLine(0.1, 100_000, Mode.BEAT_7K.key);
        line.setNote(0, new NormalNote(0));
        model.setAllTimeLine(new TimeLine[]{line});
        return model;
    }

    private BMSModel twoSoundModel(String name) throws Exception {
        Path chart = temporary.resolve(name + ".bms");
        Files.writeString(chart, "#TITLE " + name);
        Files.write(temporary.resolve(name + "-0.wav"), waveBytes(100, (short) 8_000));
        Files.write(temporary.resolve(name + "-1.wav"), waveBytes(100, (short) 7_000));
        BMSModel model = model(chart, name + "-0.wav", name + "-1.wav");
        TimeLine line = new TimeLine(0.1, 100_000, Mode.BEAT_7K.key);
        line.setNote(0, new NormalNote(0));
        line.setNote(1, new NormalNote(1));
        model.setAllTimeLine(new TimeLine[]{line});
        return model;
    }

    private static GeneratedPreviewRenderer.Limits limits(
            int maxSoundCount,
            long maxSourceBytesPerSound,
            long maxTotalSourceBytes,
            long maxAllocatedBytesPerSound,
            long maxRetainedDecodedBytes) {
        return new GeneratedPreviewRenderer.Limits(
                maxSoundCount,
                maxSourceBytesPerSound,
                maxTotalSourceBytes,
                maxAllocatedBytesPerSound,
                maxRetainedDecodedBytes);
    }

    private static BMSModel model(Path chart, String... sounds) {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setWavList(sounds);
        model.setChartInformation(new ChartInformation(
                chart,
                BMSModel.LNTYPE_LONGNOTE,
                null));
        return model;
    }

    private static byte[] waveBytes(int durationMs, short amplitude) {
        int samples = SAMPLE_RATE * durationMs / 1_000;
        int pcmBytes = samples;
        ByteBuffer wave = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
        wave.putInt(0x46464952).putInt(36 + pcmBytes).putInt(0x45564157);
        wave.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1);
        wave.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE).putShort((short) 1).putShort((short) 8);
        wave.putInt(0x61746164).putInt(pcmBytes);
        int unsignedAmplitude = Math.max(1, Math.min(255, 128 + amplitude / 256));
        for (int index = 0; index < samples; index++) {
            wave.put((byte) unsignedAmplitude);
        }
        return wave.array();
    }

    private static boolean hasAudio(ByteBuffer pcm) {
        ByteBuffer samples = pcm.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        while (samples.remaining() >= 2) {
            if (samples.getShort() != 0) {
                return true;
            }
        }
        return false;
    }

    private static void writeEntry(ZipOutputStream output, String name, byte[] contents)
            throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents);
        output.closeEntry();
    }

    private static final class TestAudioDriver extends AbstractAudioDriver<PCM> {

        private TestAudioDriver(int sampleRate, int channels) {
            super(1);
            setSampleRate(sampleRate);
            this.channels = channels;
        }

        @Override protected PCM getKeySound(Path path) { return null; }
        @Override protected PCM getKeySound(PCM pcm) { return pcm; }
        @Override protected void disposeKeySound(PCM pcm) {}
        @Override protected void play(PCM pcm, int channel, float volume, float pitch) {}
        @Override protected void play(AudioElement<PCM> id, float volume, boolean loop) {}
        @Override protected void setVolume(AudioElement<PCM> id, float volume) {}
        @Override protected boolean isPlaying(PCM id) { return false; }
        @Override protected void stop(PCM id) {}
        @Override protected void stop(PCM id, int channel) {}
        @Override protected void setVolume(PCM id, int channel, float volume) {}
        @Override public void dispose() {}
    }
}
