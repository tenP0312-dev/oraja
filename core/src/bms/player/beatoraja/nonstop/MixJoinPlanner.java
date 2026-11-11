package bms.player.beatoraja.nonstop;

import java.util.List;

/**
 * Chooses the used part of each song and how neighbours are joined, from the
 * charts alone (Issue #535 §4–5). No join data has to be registered by hand:
 * bar lines give the phrase grid, tempo decides between a blend and a cut,
 * and long notes decide where the lanes may change hands.
 */
public final class MixJoinPlanner {

    public enum Style {
        /** Each song plays from its first bar to its end. */
        FULL(0),
        /** About two and a half minutes of each song. */
        LONG(150_000),
        /** About a minute and a quarter of each song. */
        SHORT(75_000);

        public final long segmentMs;

        Style(long segmentMs) {
            this.segmentMs = segmentMs;
        }
    }

    /** Settings that shape the joins. */
    public static final class Settings {
        public Style style = Style.FULL;
        /** Preferred blend length in bars. */
        public int blendBars = 8;
        /** Largest tempo difference that is still blended (ratio, e.g. 0.03 = 3 %). */
        public double blendTolerance = 0.03;
        /** Largest tempo difference that is joined by a fade instead of a cut. */
        public double fadeTolerance = 0.15;
        public boolean allowBlend = true;
        public boolean allowFade = true;
        public boolean allowCut = true;
        public MixTransition.Curve curve = MixTransition.Curve.EQUAL_POWER;
        /** Where the lanes change hands inside a blend (0..1 of the overlap). */
        public double handoffPosition = 0.5;

        public Settings copy() {
            Settings copy = new Settings();
            copy.style = style;
            copy.blendBars = blendBars;
            copy.blendTolerance = blendTolerance;
            copy.fadeTolerance = fadeTolerance;
            copy.allowBlend = allowBlend;
            copy.allowFade = allowFade;
            copy.allowCut = allowCut;
            copy.curve = curve;
            copy.handoffPosition = handoffPosition;
            return copy;
        }
    }

    private MixJoinPlanner() {
    }

    /**
     * Fills in ranges and joins of every unlocked track/join of {@code plan}.
     *
     * @param analyses one analysis per track, in order
     */
    public static void plan(MixPlan plan, List<MixTrackAnalysis> analyses, Settings settings) {
        plan.normalizeTransitions();
        int n = plan.tracks.size();
        for (int i = 0; i < n; i++) {
            MixTrack track = plan.tracks.get(i);
            if (!track.lockRange) {
                chooseRange(track, analyses.get(i), settings, i == 0, i == n - 1);
            }
        }
        for (int j = 0; j < n - 1; j++) {
            MixTransition join = plan.transitions.get(j);
            if (join.locked) {
                continue;
            }
            planJoin(plan, j, analyses.get(j), analyses.get(j + 1), settings);
        }
    }

    /** Re-plans a single join (the editor's "auto" button for one join). */
    public static void planJoin(MixPlan plan, int join, MixTrackAnalysis a, MixTrackAnalysis b, Settings settings) {
        MixTrack outgoing = plan.tracks.get(join);
        MixTrack incoming = plan.tracks.get(join + 1);
        MixTransition result = plan.transitions.get(join);
        double bpmA = bpmNear(a, outgoing.resolvedOut(a.chartEnd));
        double bpmB = bpmNear(b, Math.max(incoming.inMs, b.firstNoteMs));
        double difference = Math.abs(bpmA - bpmB) / Math.max(bpmA, bpmB);

        if (settings.allowBlend && difference <= settings.blendTolerance && a.steadyTempo && b.steadyTempo) {
            if (blend(outgoing, incoming, result, a, b, settings, bpmA)) {
                return;
            }
        }
        if (settings.allowFade && difference <= settings.fadeTolerance) {
            fade(outgoing, incoming, result, a, b, settings, bpmA);
            return;
        }
        if (settings.allowCut || !settings.allowFade) {
            cut(outgoing, incoming, result, a, b);
        } else {
            fade(outgoing, incoming, result, a, b, settings, bpmA);
        }
    }

    private static boolean blend(MixTrack outgoing, MixTrack incoming, MixTransition result, MixTrackAnalysis a,
            MixTrackAnalysis b, Settings settings, double bpm) {
        long barMs = Math.round(240_000.0 / bpm);
        long outEnd = outgoing.resolvedOut(a.chartEnd);
        long aSegment = outEnd - outgoing.inMs;
        long bSegment = incoming.resolvedOut(b.chartEnd) - incoming.inMs;
        for (int bars = Math.max(2, settings.blendBars); bars >= 2; bars /= 2) {
            long overlap = bars * barMs;
            if (overlap > aSegment * 0.45 || overlap > bSegment * 0.45 || overlap > MixTransition.MAX_OVERLAP_MS) {
                continue;
            }
            long handoff = findHandoff(outgoing, incoming, a, b, overlap, settings.handoffPosition, barMs);
            if (handoff < 0) {
                continue;
            }
            result.type = MixTransition.Type.BLEND;
            result.curve = settings.curve;
            result.overlapMs = overlap;
            result.handoffMs = handoff;
            result.gapMs = 0;
            return true;
        }
        return false;
    }

    private static void fade(MixTrack outgoing, MixTrack incoming, MixTransition result, MixTrackAnalysis a,
            MixTrackAnalysis b, Settings settings, double bpm) {
        tightenRanges(outgoing, incoming, a, b);
        safeCutPoints(outgoing, incoming, a, b);
        long barMs = Math.round(240_000.0 / bpm);
        long segment = outgoing.resolvedOut(a.chartEnd) - outgoing.inMs;
        result.type = MixTransition.Type.FADE_OUT;
        result.curve = settings.curve;
        result.overlapMs = Math.max(1000L, Math.min(Math.min(2 * barMs, 6000L), segment / 4));
        result.handoffMs = 0;
        result.gapMs = 0;
    }

    private static void cut(MixTrack outgoing, MixTrack incoming, MixTransition result, MixTrackAnalysis a,
            MixTrackAnalysis b) {
        tightenRanges(outgoing, incoming, a, b);
        safeCutPoints(outgoing, incoming, a, b);
        result.type = MixTransition.Type.CUT;
        result.overlapMs = 0;
        result.handoffMs = 0;
        result.gapMs = 0;
    }

    /**
     * Keeps the stretch without notes across a fade or cut short: the outgoing
     * song stops at the bar line after its last note (and long-note tail), and
     * the incoming song starts at the bar line of its first note, so a quiet
     * outro or intro never leaves more than about two bars without notes.
     */
    static void tightenRanges(MixTrack outgoing, MixTrack incoming, MixTrackAnalysis a, MixTrackAnalysis b) {
        if (!outgoing.lockRange && a.totalNotes > 0 && (outgoing.outMs < 0 || outgoing.outMs >= a.lastNoteMs)) {
            long out = barAfterLastNote(a);
            if (out >= 0) {
                outgoing.outMs = Math.max(outgoing.inMs, out);
            }
        }
        if (!incoming.lockRange && b.totalNotes > 0) {
            int index = b.barIndexAtOrBefore(b.firstNoteMs);
            if (index >= 0 && incoming.inMs < b.bars[index]) {
                incoming.inMs = b.bars[index];
            }
        }
    }

    /**
     * First bar line strictly after the last note and every long-note end (a
     * note on the cut point itself would be handed to the next song), or -1
     * when the chart has no bar lines.
     */
    static long barAfterLastNote(MixTrackAnalysis analysis) {
        long tail = analysis.lastNoteMs;
        for (long[] span : analysis.longNotes) {
            tail = Math.max(tail, span[1]);
        }
        for (long bar : analysis.bars) {
            if (bar > tail) {
                return bar;
            }
        }
        if (analysis.bars.length == 0) {
            return -1;
        }
        // the chart has no bar line after its last note: continue the grid at the end tempo
        long lastBar = analysis.bars[analysis.bars.length - 1];
        long barMs = analysis.barMsAtEnd();
        return lastBar + ((tail - lastBar) / barMs + 1) * barMs;
    }

    /** Moves the cut points off long notes, keeping them on bar lines. */
    private static void safeCutPoints(MixTrack outgoing, MixTrack incoming, MixTrackAnalysis a, MixTrackAnalysis b) {
        if (!outgoing.lockRange && outgoing.outMs >= 0) {
            long out = outgoing.outMs;
            for (int guard = 0; guard < 64 && a.longNoteAcross(out); guard++) {
                int index = a.barIndexAtOrBefore(out - 1);
                out = index >= 0 ? a.bars[index] : out - 250;
            }
            outgoing.outMs = Math.max(outgoing.inMs, out);
        }
        if (!incoming.lockRange) {
            long in = incoming.inMs;
            for (int guard = 0; guard < 64 && b.longNoteAcross(in); guard++) {
                int index = b.barIndexAtOrBefore(in - 1);
                in = index >= 0 ? b.bars[index] : 0;
            }
            incoming.inMs = Math.max(0, in);
        }
    }

    /**
     * Finds where the lanes can change hands inside a blend.
     *
     * @return offset from the start of the incoming song (ms), or -1 when no point is safe
     */
    static long findHandoff(MixTrack outgoing, MixTrack incoming, MixTrackAnalysis a, MixTrackAnalysis b,
            long overlap, double position, long barMs) {
        long outEnd = outgoing.resolvedOut(a.chartEnd);
        long aStart = outEnd - overlap;           // outgoing chart time where the incoming song starts
        long bStart = incoming.inMs;              // incoming chart time at the same moment
        long best = -1;
        double bestScore = Double.MAX_VALUE;
        long step = Math.max(250L, barMs / 4);
        for (long offset = step; offset < overlap; offset += step) {
            long aTime = aStart + offset;
            long bTime = bStart + offset;
            if (a.longNoteAcross(aTime) || b.longNoteAcross(bTime)) {
                continue;
            }
            boolean onBar = offset % barMs < 2 || barMs - offset % barMs < 2;
            double score = Math.abs(offset / (double) overlap - position) * 4.0
                    + (a.densityAround(aTime, 1000) + b.densityAround(bTime, 1000)) * 0.05
                    + (onBar ? 0 : 0.6);
            if (score < bestScore) {
                bestScore = score;
                best = offset;
            }
        }
        return best;
    }

    private static void chooseRange(MixTrack track, MixTrackAnalysis analysis, Settings settings, boolean first,
            boolean last) {
        long firstBar = analysis.bars.length > 0 ? analysis.bars[0] : 0L;
        track.inMs = first ? 0L : firstBar;
        if (settings.style == Style.FULL || last) {
            // play to the end; a blend fades the outro, so stop at the bar after the last note
            long out = -1;
            if (!last) {
                for (long bar : analysis.bars) {
                    if (bar >= analysis.lastNoteMs) {
                        out = bar;
                        break;
                    }
                }
                if (out < 0 && analysis.bars.length > 0) {
                    // the chart has no bar line after its last note: continue the grid at the end tempo
                    long lastBar = analysis.bars[analysis.bars.length - 1];
                    long barMs = analysis.barMsAtEnd();
                    long bars = (analysis.lastNoteMs - lastBar + barMs - 1) / barMs;
                    out = lastBar + Math.max(1, bars) * barMs;
                }
            }
            track.outMs = out;
            return;
        }
        long target = track.inMs + settings.style.segmentMs;
        long phrase = analysis.firstNoteMs;
        int firstNoteBar = Math.max(0, analysis.barIndexAtOrBefore(phrase));
        long best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = firstNoteBar; i < analysis.bars.length; i++) {
            long bar = analysis.bars[i];
            if (bar <= track.inMs + 20_000L || bar >= analysis.lastNoteMs) {
                continue;
            }
            if (analysis.longNoteAcross(bar)) {
                continue;
            }
            long distance = Math.abs(bar - target);
            // phrases usually start every 8 bars from the first note
            if ((i - firstNoteBar) % 8 != 0) {
                distance += 6_000L;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = bar;
            }
        }
        track.outMs = best;
    }

    private static double bpmNear(MixTrackAnalysis analysis, long ms) {
        // the analysis keeps start/end tempo; inside the chart the main tempo is the best guess
        if (ms >= analysis.lastNoteMs) {
            return analysis.endBpm;
        }
        if (ms <= analysis.firstNoteMs) {
            return analysis.startBpm;
        }
        return analysis.mainBpm;
    }
}
