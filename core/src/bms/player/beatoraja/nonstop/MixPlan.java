package bms.player.beatoraja.nonstop;

import bms.model.Mode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An editable NONSTOP MIX set (Issue #535): songs, the part of each song that is
 * used, and how neighbouring songs are joined. Manual editing, automatic
 * generation and IR downloads all produce this same structure; nothing is
 * hidden from the editor.
 *
 * <p>All times of a {@link MixTrack} are in the song's own chart time
 * (milliseconds, as loaded by the player). Set time is derived by
 * {@link #layout(long[])}.</p>
 */
public final class MixPlan {
    public static final int FORMAT_VERSION = 1;
    /** Lead-in before the first song when it does not start at its own beginning. */
    public static final long LEAD_IN_MS = 2000L;

    /** Local identity; kept across edits so saved sets can be overwritten. */
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String description = "";
    public String author = "";
    public Mode mode = Mode.BEAT_7K;
    public JudgeRule judge = JudgeRule.NORMAL;
    /** Multiplies the default TOTAL computed from the whole set's note count. */
    public double totalScale = 1.0;
    /** Matches the loudness of each song (needs the loudness analyzer). */
    public boolean normalizeLoudness = true;
    /** "manual" or the generator id that made the first draft. */
    public String origin = "manual";
    /** IR set id after publishing or downloading; empty for local-only sets. */
    public String irId = "";
    /** IR revision the local copy was built from; editing a downloaded set forks it. */
    public int irRevision;
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = createdAt;

    public final List<MixTrack> tracks = new ArrayList<>();
    /** transitions.get(i) joins tracks i and i + 1. */
    public final List<MixTransition> transitions = new ArrayList<>();

    public enum JudgeRule {
        VERY_EASY(4), EASY(3), NORMAL(2), HARD(1), VERY_HARD(0),
        /** Average of the songs' own judge windows. */
        AVERAGE(-1),
        /** The strictest judge among the songs. */
        STRICTEST(-2);

        /** Index into the rule's judgerank table, or a negative marker. */
        public final int rankIndex;

        JudgeRule(int rankIndex) {
            this.rankIndex = rankIndex;
        }
    }

    public int size() {
        return tracks.size();
    }

    /** Keeps exactly one transition between neighbouring tracks. */
    public void normalizeTransitions() {
        int wanted = Math.max(0, tracks.size() - 1);
        while (transitions.size() > wanted) {
            transitions.remove(transitions.size() - 1);
        }
        while (transitions.size() < wanted) {
            transitions.add(new MixTransition());
        }
    }

    public void addTrack(int index, MixTrack track) {
        tracks.add(index, track);
        if (tracks.size() > 1) {
            transitions.add(Math.min(index, transitions.size()), new MixTransition());
        }
        normalizeTransitions();
    }

    public void removeTrack(int index) {
        tracks.remove(index);
        if (!transitions.isEmpty()) {
            transitions.remove(Math.min(index, transitions.size() - 1));
        }
        normalizeTransitions();
    }

    /** Moves a track; the joins are re-derived around the moved song. */
    public void moveTrack(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= tracks.size() || to >= tracks.size()) {
            return;
        }
        MixTrack track = tracks.remove(from);
        tracks.add(to, track);
        // the joins around a moved song no longer describe the same pair
        int low = Math.max(0, Math.min(from, to) - 1);
        int high = Math.min(transitions.size() - 1, Math.max(from, to));
        for (int i = low; i <= high; i++) {
            if (!transitions.get(i).locked) {
                transitions.set(i, transitions.get(i).copyUnlockedDefaults());
            }
        }
    }

    public MixPlan copy() {
        MixPlan copy = MixJson.fromJson(MixJson.toJson(this, true));
        copy.id = id;
        return copy;
    }

    /**
     * Places every track on the set's time line.
     *
     * @param chartEnds each track's resolved end (chart time) when {@link MixTrack#outMs} is open
     * @return per-track placement
     */
    public Layout layout(long[] chartEnds) {
        int n = tracks.size();
        long[] start = new long[n];
        long[] end = new long[n];
        long[] offset = new long[n];
        long[] handoff = new long[Math.max(0, n - 1)];
        long cursor = 0;
        for (int i = 0; i < n; i++) {
            MixTrack track = tracks.get(i);
            long in = Math.max(0L, track.inMs);
            long out = track.resolvedOut(chartEnds != null && i < chartEnds.length ? chartEnds[i] : in);
            if (i == 0) {
                cursor = in > 0 ? LEAD_IN_MS : in;
            } else {
                // a join can never reach back before the start of the previous song
                cursor = end[i - 1] - overlapMs(i - 1, start, end);
            }
            start[i] = cursor;
            offset[i] = cursor - in;
            end[i] = cursor + Math.max(0L, out - in);
            if (i > 0) {
                long overlap = Math.max(0L, overlapMs(i - 1, start, end));
                handoff[i - 1] = start[i] + Math.min(overlap, transitions.get(i - 1).effectiveHandoffMs());
            }
        }
        return new Layout(start, end, offset, handoff);
    }

    private long overlapMs(int join, long[] start, long[] end) {
        long overlap = transitions.get(join).placementOverlapMs();
        return Math.min(overlap, Math.max(0L, end[join] - start[join]));
    }

    /**
     * Track positions on the set time line (ms).
     *
     * @param start   set time where the track's used part begins
     * @param end     set time where it ends
     * @param offset  set time = chart time + offset
     * @param handoff set time where the playable chart switches from track i to i + 1
     */
    public record Layout(long[] start, long[] end, long[] offset, long[] handoff) {
        public long totalMs() {
            long last = 0;
            for (long value : end) {
                last = Math.max(last, value);
            }
            return last;
        }

        /** First set time at which track i owns the playable lanes. */
        public long playableFrom(int track) {
            return track == 0 ? Long.MIN_VALUE : handoff[track - 1];
        }

        /** Set time at which track i hands the lanes over (exclusive). */
        public long playableUntil(int track) {
            return track >= handoff.length ? Long.MAX_VALUE : handoff[track];
        }
    }
}
