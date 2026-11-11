package bms.player.beatoraja.nonstop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background loading and compiling of a set. The render thread only polls
 * {@link #state()}; nothing here touches the GPU or the audio device.
 */
public final class MixPreparation {
    private static final Logger logger = LoggerFactory.getLogger(MixPreparation.class);

    public enum State { LOADING, LOUDNESS, COMPILING, READY, FAILED, CANCELLED }

    private final MixPlan plan;
    private final MixLibrary library;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile State state = State.LOADING;
    private volatile int progress;
    private volatile int total;
    private volatile String error = "";
    private volatile CompiledMix result;
    private volatile List<MixCompiler.Source> sources;
    private volatile List<MixLibrary.Loaded> loaded;
    private java.util.function.BiConsumer<MixPlan, long[]> transform;

    private MixPreparation(MixPlan plan, MixLibrary library) {
        this.plan = plan.copy();
        this.library = library;
    }

    /** Starts preparing a snapshot of {@code plan}; later edits do not affect it. */
    public static MixPreparation start(MixPlan plan, MixLibrary library, boolean loudness) {
        return start(plan, library, loudness, null);
    }

    /**
     * @param transform adjusts the loaded snapshot before compiling (e.g. cutting a
     *                  preview out of the set); receives each track's chart end
     */
    public static MixPreparation start(MixPlan plan, MixLibrary library, boolean loudness,
            java.util.function.BiConsumer<MixPlan, long[]> transform) {
        return start(plan, library, loudness, transform, null);
    }

    /**
     * @param finished runs on the worker thread once the job is READY, FAILED or
     *                 CANCELLED, for callers that do not poll every frame
     */
    public static MixPreparation start(MixPlan plan, MixLibrary library, boolean loudness,
            java.util.function.BiConsumer<MixPlan, long[]> transform, Runnable finished) {
        MixPreparation job = new MixPreparation(plan, library);
        job.transform = transform;
        Thread worker = new Thread(() -> {
            job.run(loudness);
            if (finished != null) {
                try {
                    finished.run();
                } catch (RuntimeException e) {
                    logger.warn("NONSTOP MIX preparation callback failed", e);
                }
            }
        }, "nonstop-mix-prepare");
        worker.setDaemon(true);
        worker.start();
        return job;
    }

    private void run(boolean loudness) {
        try {
            total = plan.tracks.size();
            List<MixLibrary.Loaded> songs = new ArrayList<>();
            for (int i = 0; i < plan.tracks.size(); i++) {
                if (cancelled.get()) {
                    state = State.CANCELLED;
                    return;
                }
                songs.add(library.load(plan.tracks.get(i)));
                progress = i + 1;
            }
            loaded = songs;
            if (transform != null) {
                long[] ends = new long[songs.size()];
                for (int i = 0; i < ends.length; i++) {
                    ends[i] = songs.get(i).analysis().chartEnd;
                }
                transform.accept(plan, ends);
            }
            double[] gains = new double[songs.size()];
            java.util.Arrays.fill(gains, 1.0);
            if (loudness && plan.normalizeLoudness) {
                state = State.LOUDNESS;
                progress = 0;
                gains = library.loudnessGains(songs, value -> progress = value);
            }
            if (cancelled.get()) {
                state = State.CANCELLED;
                return;
            }
            state = State.COMPILING;
            List<MixCompiler.Source> list = new ArrayList<>();
            for (int i = 0; i < songs.size(); i++) {
                list.add(new MixCompiler.Source(songs.get(i).model(), songs.get(i).chart(), gains[i]));
            }
            sources = List.copyOf(list);
            result = MixCompiler.compile(plan, sources);
            state = State.READY;
        } catch (Throwable e) {
            logger.warn("NONSTOP MIX preparation failed", e);
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            state = State.FAILED;
        }
    }

    public void cancel() {
        cancelled.set(true);
    }

    public State state() {
        return state;
    }

    public int progress() {
        return progress;
    }

    public int total() {
        return total;
    }

    public String error() {
        return error;
    }

    public CompiledMix result() {
        return result;
    }

    public List<MixCompiler.Source> sources() {
        return sources;
    }

    public List<MixLibrary.Loaded> loaded() {
        return loaded;
    }

    /** The plan snapshot that was prepared (with #RANDOM branches filled in). */
    public MixPlan plan() {
        return plan;
    }
}
