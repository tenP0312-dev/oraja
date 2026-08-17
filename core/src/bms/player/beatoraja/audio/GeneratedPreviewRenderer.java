package bms.player.beatoraja.audio;

import bms.model.BMSModel;
import bms.model.MineNote;
import bms.model.Note;
import bms.model.TimeLine;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Renders a short autoplay-style song preview without touching the live audio
 * engine. Only assets that can affect the bounded output window are decoded.
 *
 * <p>The window, BGM lookback, envelope, and peak policy are adapted from BMZ
 * Player's GPLv3 generated preview implementation at commit
 * 4c98cad3fb18210dca82413d8261ab62fb797248.</p>
 */
public final class GeneratedPreviewRenderer {

    public static final int VERSION = 1;

    private static final long MAX_RENDER_DURATION_MS = 18_000;
    private static final long NOTE_PREROLL_MS = 2_000;
    private static final long FADE_IN_MS = 500;
    private static final long FADE_OUT_MS = 1_000;
    private static final long BGM_EARLY_GRACE_MS = 2_000;
    private static final int BGM_LOOKBACK_EVENTS = 8;
    private static final int BGM_DURATION_PROBE_CANDIDATES = 8;
    private static final float SILENCE_PEAK = 0.0001f;
    private static final float OUTPUT_PEAK = 0.98f;

    static final Limits DEFAULT_LIMITS = new Limits(
            256,
            16L * 1024L * 1024L,
            64L * 1024L * 1024L,
            32L * 1024L * 1024L,
            96L * 1024L * 1024L);

    private final int sampleRate;
    private final int channels;
    private final Limits limits;

    public GeneratedPreviewRenderer(int sampleRate, int channels) {
        this(sampleRate, channels, DEFAULT_LIMITS);
    }

    GeneratedPreviewRenderer(int sampleRate, int channels, Limits limits) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sample rate must be positive");
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("channel count must be positive");
        }
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.limits = java.util.Objects.requireNonNull(limits, "limits");
    }

    public RenderResult render(
            BMSModel model,
            long startMs,
            long durationMs,
            BooleanSupplier cancelled) {
        if (model == null
                || durationMs <= 0
                || durationMs > MAX_RENDER_DURATION_MS
                || isCancelled(cancelled)) {
            return null;
        }
        if (model.getMode() == null || model.getWavList() == null) {
            return null;
        }
        SongResource baseResource = chartBaseResource(model);
        if (baseResource == null) {
            return null;
        }

        long boundedStartMs = Math.max(0L, startMs);
        long endMs = saturatingAdd(boundedStartMs, durationMs);
        long prerollStartMs = Math.max(0L, boundedStartMs - NOTE_PREROLL_MS);
        PreviewPlan plan = buildPlan(model, baseResource, boundedStartMs, prerollStartMs, endMs);
        if (plan.events().isEmpty()
                || plan.soundIds().isEmpty()
                || plan.soundIds().size() > limits.maxSoundCount()
                || isCancelled(cancelled)) {
            return null;
        }

        Map<Integer, PCM> samples = loadSamples(
                model.getWavList(), baseResource, plan.soundIds(), cancelled);
        if (samples.isEmpty() || isCancelled(cancelled)) {
            return null;
        }

        int frameCount = framesFromMs(durationMs);
        if (frameCount <= 0 || frameCount > Integer.MAX_VALUE / channels) {
            return null;
        }
        float[] output = new float[frameCount * channels];
        List<PreviewEvent> events = plan.events().stream()
                .filter(event -> samples.containsKey(event.note().getWav()))
                .sorted(Comparator.comparingLong(PreviewEvent::timeMs))
                .toList();
        Map<PreviewEvent, Long> nextStartByEvent = nextStartByEvent(events);
        float chartVolume = chartVolume(model);

        for (PreviewEvent event : events) {
            if (isCancelled(cancelled)) {
                return null;
            }
            renderEvent(
                    event,
                    samples.get(event.note().getWav()),
                    boundedStartMs,
                    nextStartByEvent.get(event),
                    chartVolume,
                    output);
        }

        applyEnvelopeAndPeakLimit(output, frameCount);
        if (peakAbs(output) < SILENCE_PEAK || isCancelled(cancelled)) {
            return null;
        }
        return new RenderResult(toPcm16(output), sampleRate, channels, durationMs);
    }

    private PreviewPlan buildPlan(
            BMSModel model,
            SongResource baseResource,
            long previewStartMs,
            long prerollStartMs,
            long endMs) {
        List<PreviewEvent> bgmEvents = new ArrayList<>();
        List<PreviewEvent> playableEvents = new ArrayList<>();
        for (TimeLine timeline : model.getAllTimeLines()) {
            long timeMs = timeline.getMilliTime();
            for (Note note : timeline.getBackGroundNotes()) {
                bgmEvents.add(new PreviewEvent(note, timeMs));
            }
            if (timeMs < prerollStartMs || timeMs > endMs) {
                continue;
            }
            for (int lane = 0; lane < model.getMode().key; lane++) {
                Note note = timeline.getNote(lane);
                if (note == null || note instanceof MineNote) {
                    continue;
                }
                playableEvents.add(new PreviewEvent(note, timeMs));
                for (Note layered : note.getLayeredNotes()) {
                    if (!(layered instanceof MineNote)) {
                        playableEvents.add(new PreviewEvent(layered, timeMs));
                    }
                }
            }
        }

        LinkedHashSet<PreviewEvent> selectedBgm = new LinkedHashSet<>();
        for (PreviewEvent event : bgmEvents) {
            if (event.timeMs() >= prerollStartMs && event.timeMs() <= endMs) {
                selectedBgm.add(event);
            }
        }
        if (!bgmEvents.isEmpty()) {
            long earlyLimit = saturatingAdd(bgmEvents.get(0).timeMs(), BGM_EARLY_GRACE_MS);
            for (PreviewEvent event : bgmEvents) {
                if (event.timeMs() >= prerollStartMs) {
                    break;
                }
                if (event.timeMs() <= earlyLimit) {
                    selectedBgm.add(event);
                }
            }
        }
        int lookback = 0;
        for (int index = bgmEvents.size() - 1; index >= 0 && lookback < BGM_LOOKBACK_EVENTS; index--) {
            PreviewEvent event = bgmEvents.get(index);
            if (event.timeMs() < prerollStartMs) {
                selectedBgm.add(event);
                lookback++;
            }
        }

        Set<Integer> alreadySelectedSounds = new HashSet<>();
        selectedBgm.stream().map(event -> event.note().getWav()).forEach(alreadySelectedSounds::add);
        Map<Integer, PreviewEvent> durationCandidatesBySound = new HashMap<>();
        for (int index = bgmEvents.size() - 1; index >= 0; index--) {
            PreviewEvent event = bgmEvents.get(index);
            int soundId = event.note().getWav();
            if (event.timeMs() >= prerollStartMs
                    || soundId < 0
                    || alreadySelectedSounds.contains(soundId)) {
                continue;
            }
            durationCandidatesBySound.putIfAbsent(soundId, event);
        }
        durationCandidatesBySound.values().stream()
                .map(event -> new SizedPreviewEvent(
                        event,
                        resourceSize(resolveSoundResource(baseResource, model.getWavList(), event.note().getWav()))))
                .sorted(Comparator.comparingLong(SizedPreviewEvent::bytes).reversed())
                .limit(BGM_DURATION_PROBE_CANDIDATES)
                .map(SizedPreviewEvent::event)
                .forEach(selectedBgm::add);

        List<PreviewEvent> events = new ArrayList<>(selectedBgm.size() + playableEvents.size());
        events.addAll(selectedBgm);
        events.addAll(playableEvents);
        Set<Integer> soundIds = new HashSet<>();
        for (PreviewEvent event : events) {
            int soundId = event.note().getWav();
            if (soundId >= 0) {
                soundIds.add(soundId);
            }
        }
        return new PreviewPlan(events, soundIds);
    }

    private Map<Integer, PCM> loadSamples(
            String[] wavList,
            SongResource baseResource,
            Set<Integer> soundIds,
            BooleanSupplier cancelled) {
        List<Integer> orderedIds = soundIds.stream().sorted().toList();
        Map<Integer, SongResource> resources = new HashMap<>();
        long totalSourceBytes = 0L;
        for (int soundId : orderedIds) {
            if (isCancelled(cancelled)) {
                return Map.of();
            }
            SongResource resource = resolveSoundResource(baseResource, wavList, soundId);
            if (resource == null) {
                continue;
            }
            long sourceBytes = boundedResourceSize(resource);
            if (sourceBytes < 0L
                    || sourceBytes > limits.maxSourceBytesPerSound()
                    || exceedsLimit(totalSourceBytes, sourceBytes, limits.maxTotalSourceBytes())) {
                return Map.of();
            }
            totalSourceBytes += sourceBytes;
            resources.put(soundId, resource);
        }
        if (resources.isEmpty()) {
            return Map.of();
        }

        Map<Integer, PCM> samples = new HashMap<>();
        DummyAudioDriver driver = new DummyAudioDriver(sampleRate, channels);
        long retainedDecodedBytes = 0L;
        for (int soundId : orderedIds) {
            if (isCancelled(cancelled)) {
                return Map.of();
            }
            SongResource resource = resources.get(soundId);
            if (resource == null) {
                continue;
            }
            PCM pcm;
            try {
                pcm = PCM.loadBounded(resource, driver, limits.maxAllocatedBytesPerSound());
            } catch (PCM.PcmLimitExceededException error) {
                return Map.of();
            } catch (IOException error) {
                continue;
            }
            if (pcm != null) {
                long decodedBytes = pcm.memoryBytes();
                if (decodedBytes <= 0L
                        || exceedsLimit(
                        retainedDecodedBytes,
                        decodedBytes,
                        limits.maxRetainedDecodedBytes())) {
                    return Map.of();
                }
                retainedDecodedBytes += decodedBytes;
                samples.put(soundId, pcm);
            }
        }
        return samples;
    }

    private long boundedResourceSize(SongResource resource) {
        try {
            long size = resource.size();
            return size >= 0L ? size : -1L;
        } catch (IOException error) {
            return -1L;
        }
    }

    private static boolean exceedsLimit(long current, long additional, long maximum) {
        return current < 0L
                || additional < 0L
                || current > maximum
                || additional > maximum - current;
    }

    private void renderEvent(
            PreviewEvent event,
            PCM source,
            long previewStartMs,
            Long nextStartMs,
            float volume,
            float[] output) {
        if (source == null) {
            return;
        }
        Note note = event.note();
        PCM pcm = source;
        if (note.getMicroStarttime() != 0 || note.getMicroDuration() != 0) {
            pcm = pcm.slice(note.getMicroStarttime(), note.getMicroDuration());
            if (pcm == null) {
                return;
            }
        }
        if (pcm.sampleRate != sampleRate) {
            pcm = pcm.changeSampleRate(sampleRate);
        }
        if (pcm.channels != channels) {
            pcm = pcm.changeChannels(channels);
        }

        long relativeStartFrames = framesBetween(event.timeMs(), previewStartMs);
        int sourceFrameOffset = relativeStartFrames < 0
                ? (int) Math.min(Integer.MAX_VALUE, -relativeStartFrames)
                : 0;
        int destinationFrame = relativeStartFrames > 0
                ? (int) Math.min(Integer.MAX_VALUE, relativeStartFrames)
                : 0;
        int maxFrames = output.length / channels - destinationFrame;
        if (nextStartMs != null && nextStartMs > event.timeMs()) {
            maxFrames = Math.min(maxFrames, framesFromMs(nextStartMs - event.timeMs()));
        }
        mixPcm(pcm, sourceFrameOffset, destinationFrame, maxFrames, volume, output);
    }

    private void mixPcm(
            PCM pcm,
            int sourceFrameOffset,
            int destinationFrame,
            int maxFrames,
            float volume,
            float[] output) {
        int availableFrames = pcm.len / channels - sourceFrameOffset;
        int outputFrames = output.length / channels - destinationFrame;
        int frames = Math.min(Math.min(availableFrames, outputFrames), maxFrames);
        if (frames <= 0) {
            return;
        }
        int sourceIndex = pcm.start + sourceFrameOffset * channels;
        int destinationIndex = destinationFrame * channels;
        int scalarSamples = frames * channels;
        if (pcm instanceof ShortPCM shortPcm) {
            for (int index = 0; index < scalarSamples; index++) {
                output[destinationIndex + index] += shortPcm.sample[sourceIndex + index]
                        / 32768.0f * volume;
            }
        } else if (pcm instanceof ShortDirectPCM directPcm) {
            for (int index = 0; index < scalarSamples; index++) {
                output[destinationIndex + index] += directPcm.sample.getShort((sourceIndex + index) * 2)
                        / 32768.0f * volume;
            }
        } else if (pcm instanceof FloatPCM floatPcm) {
            for (int index = 0; index < scalarSamples; index++) {
                output[destinationIndex + index] += floatPcm.sample[sourceIndex + index] * volume;
            }
        } else if (pcm instanceof BytePCM bytePcm) {
            for (int index = 0; index < scalarSamples; index++) {
                output[destinationIndex + index] += bytePcm.sample[sourceIndex + index]
                        / 128.0f * volume;
            }
        }
    }

    private Map<PreviewEvent, Long> nextStartByEvent(List<PreviewEvent> events) {
        Map<PreviewEvent, Long> nextByEvent = new HashMap<>();
        Map<Integer, Long> nextBySound = new HashMap<>();
        for (int index = events.size() - 1; index >= 0; index--) {
            PreviewEvent event = events.get(index);
            int soundId = event.note().getWav();
            Long next = nextBySound.get(soundId);
            if (next != null) {
                nextByEvent.put(event, next);
            }
            nextBySound.put(soundId, event.timeMs());
        }
        return nextByEvent;
    }

    private void applyEnvelopeAndPeakLimit(float[] output, int frameCount) {
        int fadeInFrames = Math.min(frameCount, framesFromMs(FADE_IN_MS));
        int fadeOutFrames = Math.min(frameCount, framesFromMs(FADE_OUT_MS));
        for (int frame = 0; frame < fadeInFrames; frame++) {
            float gain = fadeGain(frame, fadeInFrames, false);
            multiplyFrame(output, frame, gain);
        }
        for (int frame = 0; frame < fadeOutFrames; frame++) {
            float gain = fadeGain(frame, fadeOutFrames, true);
            multiplyFrame(output, frameCount - fadeOutFrames + frame, gain);
        }
        float peak = peakAbs(output);
        if (peak > OUTPUT_PEAK) {
            float scale = OUTPUT_PEAK / peak;
            for (int index = 0; index < output.length; index++) {
                output[index] *= scale;
            }
        }
    }

    private void multiplyFrame(float[] output, int frame, float gain) {
        int offset = frame * channels;
        for (int channel = 0; channel < channels; channel++) {
            output[offset + channel] *= gain;
        }
    }

    private ByteBuffer toPcm16(float[] output) {
        ByteBuffer pcm = ByteBuffer.allocate(output.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float sample : output) {
            float bounded = Math.max(-1.0f, Math.min(1.0f, sample));
            pcm.putShort((short) Math.round(bounded * 32767.0f));
        }
        pcm.flip();
        return pcm;
    }

    private SongResource chartBaseResource(BMSModel model) {
        String path = model.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return SongResources.fromPath(Path.of(path)).parent();
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private SongResource resolveSoundResource(
            SongResource baseResource,
            String[] wavList,
            int soundId) {
        if (soundId < 0 || soundId >= wavList.length) {
            return null;
        }
        String path = wavList[soundId];
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            for (SongResource candidate : AudioDriver.getResources(baseResource.resolve(path))) {
                if (candidate.exists() && !candidate.isDirectory()) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private long resourceSize(SongResource resource) {
        if (resource == null) {
            return 0L;
        }
        try {
            return Math.max(0L, resource.size());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private int framesFromMs(long milliseconds) {
        if (milliseconds <= 0) {
            return 0;
        }
        if (milliseconds > (Long.MAX_VALUE - 999L) / sampleRate) {
            return Integer.MAX_VALUE;
        }
        long frames = (milliseconds * sampleRate + 999L) / 1_000L;
        return (int) Math.min(Integer.MAX_VALUE, frames);
    }

    private long framesBetween(long eventMs, long previewStartMs) {
        long difference = eventMs - previewStartMs;
        if (difference > Long.MAX_VALUE / sampleRate) {
            return Long.MAX_VALUE;
        }
        if (difference < Long.MIN_VALUE / sampleRate) {
            return Long.MIN_VALUE;
        }
        return difference * sampleRate / 1_000L;
    }

    private static float fadeGain(int frame, int frames, boolean invert) {
        if (frames <= 1) {
            return 1.0f;
        }
        float progress = (float) frame / (frames - 1);
        return invert ? 1.0f - progress : progress;
    }

    private static float peakAbs(float[] samples) {
        float peak = 0.0f;
        for (float sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    private static float chartVolume(BMSModel model) {
        int volwav = model.getVolwav();
        return volwav > 0 && volwav < 100 ? volwav / 100.0f : 1.0f;
    }

    private static boolean isCancelled(BooleanSupplier cancelled) {
        return cancelled != null && cancelled.getAsBoolean();
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record PreviewEvent(Note note, long timeMs) {
    }

    private record SizedPreviewEvent(PreviewEvent event, long bytes) {
    }

    private record PreviewPlan(List<PreviewEvent> events, Set<Integer> soundIds) {
    }

    record Limits(
            int maxSoundCount,
            long maxSourceBytesPerSound,
            long maxTotalSourceBytes,
            long maxAllocatedBytesPerSound,
            long maxRetainedDecodedBytes) {

        Limits {
            if (maxSoundCount <= 0
                    || maxSourceBytesPerSound <= 0L
                    || maxTotalSourceBytes <= 0L
                    || maxAllocatedBytesPerSound <= 0L
                    || maxRetainedDecodedBytes <= 0L) {
                throw new IllegalArgumentException("preview limits must be positive");
            }
        }
    }

    public record RenderResult(ByteBuffer pcmData, int sampleRate, int channels, long durationMs) {
    }

    private static final class DummyAudioDriver extends AbstractAudioDriver<PCM> {

        private DummyAudioDriver(int sampleRate, int channels) {
            super(1);
            setSampleRate(sampleRate);
            this.channels = channels;
        }

        @Override
        protected PCM getKeySound(Path path) {
            return PCM.load(path, this);
        }

        @Override
        protected PCM getKeySound(PCM pcm) {
            return pcm;
        }

        @Override
        protected void disposeKeySound(PCM pcm) {
        }

        @Override
        protected void play(PCM pcm, int channel, float volume, float pitch) {
        }

        @Override
        protected void play(AudioElement<PCM> id, float volume, boolean loop) {
        }

        @Override
        protected void setVolume(AudioElement<PCM> id, float volume) {
        }

        @Override
        protected boolean isPlaying(PCM id) {
            return false;
        }

        @Override
        protected void stop(PCM id) {
        }

        @Override
        protected void stop(PCM id, int channel) {
        }

        @Override
        protected void setVolume(PCM id, int channel, float volume) {
        }

        @Override
        public void dispose() {
        }
    }
}
