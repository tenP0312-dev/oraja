package bms.player.beatoraja.nonstop;

import bms.model.BMSModel;
import bms.model.Note;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * A resolved, playable set: the merged chart plus everything the runtime needs
 * to shape each song's volume. Play, preview and replay all use this same
 * object, so what is heard while editing is what is played.
 */
public final class CompiledMix {
    private final MixPlan plan;
    private final BMSModel model;
    private final MixSongResource resource;
    private final MixPlan.Layout layout;
    private final long[] chartEnds;
    private final float[] staticGain;
    private final IdentityHashMap<Note, Integer> noteTrack;
    private final MixCompiler.TimedNote[][] sounds;
    private final List<MixCompiler.Issue> issues;
    private final String identity;
    private final MixLoad load;

    CompiledMix(MixPlan plan, BMSModel model, MixSongResource resource, MixPlan.Layout layout, long[] chartEnds,
            float[] staticGain, IdentityHashMap<Note, Integer> noteTrack, MixCompiler.TimedNote[][] sounds,
            List<MixCompiler.Issue> issues, String identity, MixLoad load) {
        this.plan = plan;
        this.model = model;
        this.resource = resource;
        this.layout = layout;
        this.chartEnds = chartEnds;
        this.staticGain = staticGain;
        this.noteTrack = noteTrack;
        this.sounds = sounds;
        this.issues = issues;
        this.identity = identity;
        this.load = load;
    }

    public MixPlan plan() {
        return plan;
    }

    public BMSModel model() {
        return model;
    }

    public MixSongResource resource() {
        return resource;
    }

    public MixPlan.Layout layout() {
        return layout;
    }

    public long[] chartEnds() {
        return chartEnds;
    }

    public List<MixCompiler.Issue> issues() {
        return issues;
    }

    public boolean hasErrors() {
        for (MixCompiler.Issue issue : issues) {
            if (issue.severity() == MixCompiler.Severity.ERROR) {
                return true;
            }
        }
        return false;
    }

    /** Hash of everything that changes what is played; records are kept per identity. */
    public String identity() {
        return identity;
    }

    /** BGA load estimate of the set, counted even when the set plays without BGA. */
    public MixLoad load() {
        return load;
    }

    public int trackCount() {
        return sounds.length;
    }

    /** Track that a merged note came from, or -1. */
    public int trackOf(Note note) {
        Integer track = note == null ? null : noteTrack.get(note);
        return track == null ? -1 : track;
    }

    public MixCompiler.TimedNote[] sounds(int track) {
        return sounds[track];
    }

    /** Set time (ms) of the track that owns the lanes. */
    public int ownerAt(long setMs) {
        for (int i = 0; i < layout.handoff().length; i++) {
            if (setMs < layout.handoff()[i]) {
                return i;
            }
        }
        return sounds.length - 1;
    }

    /**
     * Volume factor of a track at a set time: its static gain times the fade
     * of the joins on both sides. Zero once a song has been faded or cut out.
     */
    public float gain(int track, long setMs) {
        if (track < 0 || track >= sounds.length) {
            return 1f;
        }
        double gain = staticGain[track];
        long start = layout.start()[track];
        long end = layout.end()[track];
        if (track > 0) {
            MixTransition join = plan.transitions.get(track - 1);
            long fade = join.incomingFadeMs();
            if (fade > 0 && setMs < start + fade) {
                gain *= join.incoming((setMs - start) / (double) fade);
            }
        }
        if (track < sounds.length - 1) {
            MixTransition join = plan.transitions.get(track);
            switch (join.type) {
                case BLEND -> {
                    long nextStart = layout.start()[track + 1];
                    long fade = Math.max(1L, end - nextStart);
                    if (setMs >= end) {
                        gain = 0;
                    } else if (setMs > nextStart) {
                        gain *= join.outgoing((setMs - nextStart) / (double) fade);
                    }
                }
                case FADE_OUT -> {
                    long fade = Math.max(1L, join.outgoingFadeMs());
                    if (setMs >= end) {
                        gain = 0;
                    } else if (setMs > end - fade) {
                        gain *= join.outgoing((setMs - (end - fade)) / (double) fade);
                    }
                }
                case CUT, FADE_IN -> {
                    long release = MixTransition.CUT_RELEASE_MS;
                    if (setMs >= end + release) {
                        gain = 0;
                    } else if (setMs > end) {
                        gain *= 1.0 - (setMs - end) / (double) release;
                    }
                }
            }
        }
        return (float) Math.max(0.0, gain);
    }

    /** Volume factor for a note when it is triggered. */
    public float gainForNote(Note note) {
        int track = trackOf(note);
        if (track < 0) {
            return 1f;
        }
        return gain(track, note.getMilliTime());
    }

    /** Index of the first sound of a track at or after {@code setMs}. */
    public int firstSoundAtOrAfter(int track, long setMs) {
        MixCompiler.TimedNote[] list = sounds[track];
        int low = 0;
        int high = list.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (list[mid].setMs() < setMs) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
