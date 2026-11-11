package bms.player.beatoraja.nonstop;

import bms.model.BMSModel;
import bms.model.Note;
import bms.player.beatoraja.audio.AudioDriver;

import java.util.List;

/**
 * State of the set that is currently loaded for play. The audio driver asks
 * {@link #noteGain(Note)} for every note it triggers, and the player calls
 * {@link #update} every frame so sounds that are still ringing follow the
 * fades of their song.
 */
public final class MixRuntime {

    public enum Kind {
        /** The whole set: records are kept. */
        FULL,
        /** A part of the set for checking a join; never recorded. */
        PREVIEW
    }

    /** What is loaded: the compiled set and how to rebuild it for a retry. */
    public record Session(CompiledMix mix, List<MixCompiler.Source> sources, MixPlan plan, Kind kind,
            String title) {
    }

    private static final long UPDATE_INTERVAL_MS = 40L;
    /** How far back ringing sounds are followed when a song fades. */
    private static final long RING_MS = 20_000L;

    private static volatile Session session;
    private static volatile CompiledMix active;
    private static float[] applied = new float[0];
    private static long lastUpdate = Long.MIN_VALUE;
    private static volatile MixResult lastResult;

    private MixRuntime() {
    }

    public static void activate(Session value) {
        session = value;
        active = value.mix();
        applied = new float[value.mix().trackCount()];
        java.util.Arrays.fill(applied, -1f);
        lastUpdate = Long.MIN_VALUE;
    }

    public static void deactivate() {
        session = null;
        active = null;
    }

    public static Session session() {
        return session;
    }

    public static boolean isActive() {
        return active != null;
    }

    public static boolean isMix(BMSModel model) {
        return model != null && model.getValues().containsKey(MixCompiler.MODEL_MARKER);
    }

    /** Builds a fresh chart for a retry; note states of the last play must not carry over. */
    public static BMSModel recompile() {
        Session current = session;
        if (current == null) {
            return null;
        }
        CompiledMix mix = MixCompiler.compile(current.plan(), current.sources());
        session = new Session(mix, current.sources(), current.plan(), current.kind(), current.title());
        active = mix;
        applied = new float[mix.trackCount()];
        java.util.Arrays.fill(applied, -1f);
        lastUpdate = Long.MIN_VALUE;
        return mix.model();
    }

    /** Volume factor for a triggered note; 1 outside a mix. */
    public static float noteGain(Note note) {
        CompiledMix mix = active;
        return mix == null ? 1f : mix.gainForNote(note);
    }

    /**
     * Follows the fades of every song for sounds that are still ringing.
     *
     * @param nowMs     play time in set milliseconds
     * @param keyVolume configured key-sound volume
     * @param bgVolume  configured background volume
     */
    public static void update(long nowMs, AudioDriver audio, float keyVolume, float bgVolume) {
        CompiledMix mix = active;
        if (mix == null || audio == null) {
            return;
        }
        if (lastUpdate != Long.MIN_VALUE && nowMs - lastUpdate < UPDATE_INTERVAL_MS && nowMs >= lastUpdate) {
            return;
        }
        lastUpdate = nowMs;
        for (int track = 0; track < mix.trackCount(); track++) {
            float gain = mix.gain(track, nowMs);
            float previous = track < applied.length ? applied[track] : -1f;
            if (previous >= 0f && Math.abs(previous - gain) < 0.004f) {
                continue;
            }
            if (previous < 0f && gain >= 0.999f) {
                // nothing to correct before the first fade
                applied[track] = gain;
                continue;
            }
            MixCompiler.TimedNote[] sounds = mix.sounds(track);
            int from = mix.firstSoundAtOrAfter(track, nowMs - RING_MS);
            for (int i = from; i < sounds.length && sounds[i].setMs() <= nowMs; i++) {
                MixCompiler.TimedNote sound = sounds[i];
                audio.setVolume(sound.note(), (sound.background() ? bgVolume : keyVolume) * gain);
            }
            applied[track] = gain;
        }
    }

    public static void setLastResult(MixResult result) {
        lastResult = result;
    }

    public static MixResult lastResult() {
        return lastResult;
    }
}
