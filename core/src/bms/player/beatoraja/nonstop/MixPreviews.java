package bms.player.beatoraja.nonstop;

/**
 * Parts of a set used for checking while editing (Issue #535 §7.1 F). A
 * preview is compiled by the same compiler as the full set, so a join sounds
 * the same when previewed and when played; previews are never recorded.
 */
public final class MixPreviews {
    /** Time kept before and after a join when previewing it. */
    public static final long CONTEXT_MS = 8000L;

    private MixPreviews() {
    }

    /** The two songs around join {@code j}; ranges are narrowed after loading. */
    public static MixPlan join(MixPlan plan, int j) {
        MixPlan preview = plan.copy();
        preview.tracks.clear();
        preview.transitions.clear();
        preview.tracks.add(plan.tracks.get(j).copy());
        preview.tracks.add(plan.tracks.get(j + 1).copy());
        preview.transitions.add(plan.transitions.get(j).copy());
        preview.name = plan.name + " (preview)";
        return preview;
    }

    /** Narrows a two-song preview to the join and some context on both sides. */
    public static void narrowJoin(MixPlan preview, long[] chartEnds) {
        MixTrack a = preview.tracks.get(0);
        MixTrack b = preview.tracks.get(1);
        MixTransition join = preview.transitions.get(0);
        long outA = a.resolvedOut(chartEnds[0]);
        long joinLength = Math.max(0L, join.placementOverlapMs());
        a.inMs = Math.max(a.inMs, outA - joinLength - CONTEXT_MS);
        long outB = b.resolvedOut(chartEnds[1]);
        b.outMs = Math.min(outB, b.inMs + Math.max(joinLength, join.incomingFadeMs()) + CONTEXT_MS);
    }

    /** The set from track {@code from} to its end. */
    public static MixPlan from(MixPlan plan, int from) {
        MixPlan preview = plan.copy();
        preview.tracks.clear();
        preview.transitions.clear();
        for (int i = from; i < plan.tracks.size(); i++) {
            preview.tracks.add(plan.tracks.get(i).copy());
            if (i > from) {
                preview.transitions.add(plan.transitions.get(i - 1).copy());
            }
        }
        preview.name = plan.name + " (preview)";
        return preview;
    }

    /** One song's used part only. */
    public static MixPlan single(MixPlan plan, int index) {
        MixPlan preview = plan.copy();
        preview.tracks.clear();
        preview.transitions.clear();
        preview.tracks.add(plan.tracks.get(index).copy());
        preview.name = plan.name + " (preview)";
        return preview;
    }
}
