package bms.player.beatoraja.select;

import bms.model.BMSModel;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.Config.SongPreview;
import bms.player.beatoraja.audio.AudioDriver;
import bms.player.beatoraja.audio.GeneratedPreviewRenderer;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.song.SongInformation;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Deque;
import java.util.OptionalLong;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** プレビュー再生管理用クラス。 */
public class PreviewMusicProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PreviewMusicProcessor.class);
    private static final int GENERATED_CACHE_LIMIT = 8;
    private static final int GENERATED_CHANNELS = 2;
    private static final int GENERATED_SAMPLE_RATE = 44_100;

    private final Deque<PreviewCommand> commands = new ConcurrentLinkedDeque<>();
    private final AudioDriver audio;
    private final Config config;
    private final PreviewModelLoader modelLoader;
    private final GeneratedPreviewRequestTracker requestTracker =
            new GeneratedPreviewRequestTracker();
    private final GeneratedPreviewCache<String, GeneratedPreviewResource> generatedCache =
            new GeneratedPreviewCache<>(GENERATED_CACHE_LIMIT);
    private final ThreadPoolExecutor generatedExecutor;

    private volatile PreviewThread preview;
    private volatile String defaultMusic = "";

    public PreviewMusicProcessor(AudioDriver audio, Config config) {
        this(audio, config, null);
    }

    public PreviewMusicProcessor(
            AudioDriver audio,
            Config config,
            PreviewModelLoader modelLoader) {
        this.audio = audio;
        this.config = config;
        this.modelLoader = modelLoader;
        this.generatedExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                runnable -> {
                    Thread thread = new Thread(runnable, "generated-song-preview");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    public void setDefault(String path) {
        defaultMusic = path != null ? path : "";
    }

    public synchronized void start(SongData song) {
        if (preview == null || !preview.isAlive()) {
            preview = new PreviewThread();
            preview.start();
        }
        long version = requestTracker.select(song);
        commands.add(PreviewCommand.selection(song, version));
    }

    static String resolvePreviewPath(SongData song) {
        SongResource resource = resolvePreview(song);
        return resource != null ? resource.displayPath() : "";
    }

    private static SongResource resolvePreview(SongData song) {
        if (song == null
                || song.getPath() == null
                || song.getPath().isBlank()
                || song.getPreview() == null
                || song.getPreview().isBlank()) {
            return null;
        }
        try {
            SongResource chart = SongResources.fromPath(Paths.get(song.getPath()));
            return chart.parent().resolve(song.getPreview());
        } catch (IllegalArgumentException error) {
            logger.warn(error.getMessage());
            return null;
        }
    }

    private static SongResource resolveAvailablePreview(SongData song) {
        SongResource resource = resolvePreview(song);
        if (resource == null) {
            return null;
        }
        try {
            if (!resource.exists() || resource.isDirectory()) {
                return null;
            }
            try (var input = resource.openStream()) {
                return input.read() >= 0 ? resource : null;
            }
        } catch (Exception error) {
            logger.debug("Preview resource is not available: {}", resource.displayPath(), error);
            return null;
        }
    }

    public SongData getSongData() {
        return requestTracker.current();
    }

    public synchronized void stop() {
        requestTracker.clear();
        commands.clear();
        generatedExecutor.shutdownNow();
        PreviewThread active = preview;
        if (active != null) {
            active.requestStop();
        }
        preview = null;
    }

    private void requestGeneratedPreview(SongData song, long version) {
        if (song == null || modelLoader == null || !isCurrent(song, version)) {
            return;
        }
        try {
            generatedExecutor.execute(() -> generatePreview(song, version));
        } catch (RejectedExecutionException ignored) {
            // The processor was stopped while this request was being queued.
        }
    }

    private void generatePreview(SongData song, long version) {
        try {
            if (!isCurrent(song, version)) {
                return;
            }
            BMSModel model = modelLoader.load(song);
            if (model == null || !isCurrent(song, version)) {
                return;
            }
            SongResource explicit = resolveAvailablePreview(song);
            if (explicit != null) {
                enqueueResolved(song, version, explicit);
                return;
            }
            SongInformation information = song.getInformation();
            if (information == null) {
                information = new SongInformation(model);
            }
            OptionalLong selectedStart = GeneratedPreviewSelector.selectStartMs(
                    information.getDistributionValues(),
                    Math.max(song.getLength(), model.getLastTime()));
            if (selectedStart.isEmpty() || !isCurrent(song, version)) {
                return;
            }

            int sampleRate = GENERATED_SAMPLE_RATE;
            long startMs = selectedStart.getAsLong();
            String cacheKey = generatedCacheKey(song, model, startMs, sampleRate);
            GeneratedPreviewResource cached = generatedCache.get(cacheKey);
            if (cached != null) {
                enqueueResolved(song, version, cached);
                return;
            }

            GeneratedPreviewRenderer renderer =
                    new GeneratedPreviewRenderer(sampleRate, GENERATED_CHANNELS);
            GeneratedPreviewRenderer.RenderResult rendered = renderer.render(
                    model,
                    startMs,
                    GeneratedPreviewSelector.PREVIEW_DURATION_MS,
                    () -> !isCurrent(song, version) || Thread.currentThread().isInterrupted());
            if (rendered == null || !isCurrent(song, version)) {
                return;
            }
            GeneratedPreviewResource generated =
                    GeneratedPreviewResource.from(cacheKey, rendered);
            generatedCache.put(cacheKey, generated);
            enqueueResolved(song, version, generated);
        } catch (RuntimeException error) {
            logger.warn("Generated preview failed for {}", song.getPath(), error);
        }
    }

    private void enqueueResolved(
            SongData song,
            long version,
            SongResource resource) {
        if (isCurrent(song, version)) {
            commands.add(PreviewCommand.resolved(song, version, resource));
        }
    }

    private boolean isCurrent(SongData song, long version) {
        return requestTracker.isCurrent(song, version);
    }

    private String generatedCacheKey(
            SongData song,
            BMSModel model,
            long startMs,
            int sampleRate) {
        String identity = song.getSha256();
        if (identity == null || identity.isBlank()) {
            identity = song.getPath();
        }
        return "generated-preview:v"
                + GeneratedPreviewRenderer.VERSION
                + ':' + String.valueOf(identity)
                + ':' + model.getLnmode()
                + ':' + startMs
                + ':' + sampleRate
                + ':' + GENERATED_CHANNELS;
    }

    @FunctionalInterface
    public interface PreviewModelLoader {
        BMSModel load(SongData song);
    }

    private final class PreviewThread extends Thread {

        private volatile boolean stop;
        private SongResource playingResource;
        private String playing;
        private float currentVolume;
        private long oneShotEndsAtNanos;

        private PreviewThread() {
            super("music-select-preview");
            setDaemon(true);
        }

        @Override
        public void run() {
            audio.play(defaultMusic, config.getAudioConfig().getSystemvolume(), true);
            playing = defaultMusic;
            currentVolume = config.getAudioConfig().getSystemvolume();
            while (!stop) {
                PreviewCommand command = takeLatestCommand();
                if (command != null) {
                    process(command);
                } else if (playingResource != null && previewHasEnded()) {
                    switchToDefault();
                } else if (currentVolume != config.getAudioConfig().getSystemvolume()) {
                    float volume = config.getAudioConfig().getSystemvolume();
                    if (playingResource != null) {
                        audio.setVolume(playingResource, volume);
                    } else {
                        audio.setVolume(playing, volume);
                    }
                    currentVolume = volume;
                } else {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                        if (stop) {
                            break;
                        }
                    }
                }
            }
            stopPreview(false);
        }

        private PreviewCommand takeLatestCommand() {
            PreviewCommand latest = null;
            PreviewCommand next;
            while ((next = commands.pollFirst()) != null) {
                latest = next;
            }
            return latest;
        }

        private void process(PreviewCommand command) {
            if (!isCurrent(command.song(), command.version())) {
                return;
            }
            if (command.resourceReady()) {
                playResource(command.resource());
                return;
            }
            SongResource explicit = resolveAvailablePreview(command.song());
            if (explicit != null) {
                playResource(explicit);
            } else {
                switchToDefault();
                requestGeneratedPreview(command.song(), command.version());
            }
        }

        private void playResource(SongResource resource) {
            if (resource == null || resource.cacheKey().equals(playing)) {
                return;
            }
            stopPreview(true);
            audio.play(
                    resource,
                    config.getAudioConfig().getSystemvolume(),
                    config.getSongPreview() == SongPreview.LOOP);
            playingResource = resource;
            playing = resource.cacheKey();
            oneShotEndsAtNanos = resource instanceof GeneratedPreviewResource generated
                    && config.getSongPreview() != SongPreview.LOOP
                    ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(generated.durationMs())
                    : 0L;
        }

        private boolean previewHasEnded() {
            return oneShotEndsAtNanos > 0L
                    ? System.nanoTime() >= oneShotEndsAtNanos
                    : !audio.isPlaying(playingResource);
        }

        private void switchToDefault() {
            if (playingResource == null) {
                return;
            }
            stopPreview(true);
            audio.setVolume(defaultMusic, config.getAudioConfig().getSystemvolume());
            playing = defaultMusic;
        }

        private void stopPreview(boolean pause) {
            if (playing == null || playing.isEmpty()) {
                return;
            }
            if (playingResource != null) {
                audio.stop(playingResource);
                audio.dispose(playingResource);
                playingResource = null;
                oneShotEndsAtNanos = 0L;
            } else if (pause) {
                for (int index = 10; index >= 0 && !stop; index--) {
                    float volume = index * 0.1f * config.getAudioConfig().getSystemvolume();
                    audio.setVolume(playing, volume);
                    try {
                        Thread.sleep(15);
                    } catch (InterruptedException ignored) {
                        if (stop) {
                            break;
                        }
                    }
                }
            } else {
                audio.stop(playing);
            }
        }

        private void requestStop() {
            stop = true;
            interrupt();
        }
    }

    private record PreviewCommand(
            SongData song,
            long version,
            SongResource resource,
            boolean resourceReady) {

        private static PreviewCommand selection(SongData song, long version) {
            return new PreviewCommand(song, version, null, false);
        }

        private static PreviewCommand resolved(
                SongData song,
                long version,
                SongResource resource) {
            return new PreviewCommand(song, version, resource, true);
        }
    }
}
