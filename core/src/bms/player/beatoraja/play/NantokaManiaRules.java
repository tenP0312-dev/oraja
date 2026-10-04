package bms.player.beatoraja.play;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacPlayContext;

/** Exact microsecond intervals: difference = input time - note time. */
public final class NantokaManiaRules {
    private NantokaManiaRules() { }

    public static boolean isActive(BMSModel model) {
        return model != null && model.getValues().getOrDefault(
                BMSIRManiacPlayContext.MODEL_OPTIONS, "").contains(",nantoka_mania=true,");
    }

    public static boolean supports(Mode mode) {
        return mode == Mode.BEAT_5K || mode == Mode.BEAT_7K
                || mode == Mode.BEAT_10K || mode == Mode.BEAT_14K;
    }

    public static int totalNotes(BMSModel model) {
        if (!isActive(model)) return model.getTotalNotes();
        String cached = model.getValues().get("bmsir.nantoka.total_notes");
        if (cached != null) return Integer.parseInt(cached);
        return countNotes(model);
    }

    public static int countNotes(BMSModel model) {
        int total = 0;
        for (var timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < model.getMode().key; lane++) {
                var note = timeline.getNote(lane);
                if (note instanceof NormalNote || note instanceof LongNote) total++;
            }
        }
        return total;
    }

    public static boolean legacy(BMSModel model) {
        var settings = bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings.fromCanonicalOptions(
                model.getValues().get(BMSIRManiacPlayContext.MODEL_OPTIONS));
        return settings != null && settings.getNantokaJudgeVersion() == 1;
    }

    // Calculate float32 thresholds before projecting onto integer microseconds.
    static long boundary(int seed) { return (long) Math.floor((seed * 1000f / 60f + .25f) * 1000d); }

    public static long[][] windows(boolean scratch) {
        return windows(scratch, false);
    }

    public static long[][] windows(boolean scratch, boolean doublePlay) {
        int correction = scratch ? (doublePlay ? 4 : 2) : 0;
        int great = 2 + correction;
        int good = 7 + correction;
        int bad = 15 + correction;
        return new long[][] {
                interval(-1, 1), interval(-great, great), interval(-good, good),
                interval(-bad, bad), {-boundary(-bad), 349_999}
        };
    }

    private static long[] interval(int fast, int slow) {
        // Inclusive note-input intervals encode (lower, upper] in input-note.
        return new long[] {-boundary(slow), -boundary(fast) - 1};
    }

    public static int judge(long difference, boolean scratch) {
        return judge(difference, scratch, false);
    }

    public static int judge(long difference, boolean scratch, boolean doublePlay) {
        long d = -difference;
        long[][] windows = scratch ? (doublePlay ? DP_SCRATCH : SCRATCH) : KEY;
        for (int i = 0; i < windows.length; i++) {
            if (d >= windows[i][0] && d <= windows[i][1]) return i == 4 ? 5 : i;
        }
        return 6;
    }

    public static long lateLimit(boolean scratch) { return -(scratch ? SCRATCH : KEY)[3][0]; }
    public static long earlyGreat(boolean scratch) { return boundary(scratch ? -4 : -2); }
    private static final long[][] KEY = windows(false);
    private static final long[][] SCRATCH = windows(true);
    private static final long[][] DP_SCRATCH = windows(true, true);

    public static boolean inputCandidate(long difference) {
        return difference > -350_000 && difference <= 250_000;
    }

    static long frameTime(long frame) { return Math.floorDiv(frame * 1_000_000L, 60); }

    static long expiry(long noteTime) {
        // Independent >15-frame miss condition on the deterministic 60Hz grid.
        return frameTime(Math.floorDiv(noteTime * 60, 1_000_000L) + 16);
    }
}
