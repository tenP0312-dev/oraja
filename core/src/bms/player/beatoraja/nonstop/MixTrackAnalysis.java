package bms.player.beatoraja.nonstop;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.MineNote;
import bms.model.Note;
import bms.model.TimeLine;

import java.util.ArrayList;
import java.util.List;

/**
 * Chart-side facts about one song that the editor and the generator use:
 * bar lines, note density, sound activity, long-note spans and tempo. Taken
 * from the chart itself, so no audio model is needed (Issue #535 §3).
 */
public final class MixTrackAnalysis {
    /** Bin size of the density and activity curves. */
    public static final int BIN_MS = 250;

    public final long chartEnd;
    public final long firstNoteMs;
    public final long lastNoteMs;
    /** Bar line times (chart ms). */
    public final long[] bars;
    /** Playable notes per bin. */
    public final float[] density;
    /** All sounds (notes and background) per bin: a coarse loudness/activity proxy. */
    public final float[] activity;
    /** Long-note spans [start, end] in chart ms, over all lanes. */
    public final long[][] longNotes;
    public final double startBpm;
    public final double endBpm;
    public final double mainBpm;
    public final boolean steadyTempo;
    public final boolean hasStops;
    public final int totalNotes;

    private MixTrackAnalysis(long chartEnd, long firstNoteMs, long lastNoteMs, long[] bars, float[] density,
            float[] activity, long[][] longNotes, double startBpm, double endBpm, double mainBpm,
            boolean steadyTempo, boolean hasStops, int totalNotes) {
        this.chartEnd = chartEnd;
        this.firstNoteMs = firstNoteMs;
        this.lastNoteMs = lastNoteMs;
        this.bars = bars;
        this.density = density;
        this.activity = activity;
        this.longNotes = longNotes;
        this.startBpm = startBpm;
        this.endBpm = endBpm;
        this.mainBpm = mainBpm;
        this.steadyTempo = steadyTempo;
        this.hasStops = hasStops;
        this.totalNotes = totalNotes;
    }

    public static MixTrackAnalysis of(BMSModel model) {
        long end = MixCompiler.chartEnd(model);
        int bins = (int) (end / BIN_MS) + 2;
        float[] density = new float[bins];
        float[] activity = new float[bins];
        List<Long> bars = new ArrayList<>();
        List<long[]> longNotes = new ArrayList<>();
        long first = -1;
        long last = 0;
        int notes = 0;
        boolean stops = false;
        java.util.Map<Long, Long> bpmTime = new java.util.HashMap<>();
        double prevBpm = model.getBpm();
        long prevTime = 0;
        int lanes = model.getMode().key;
        for (TimeLine tl : model.getAllTimeLines()) {
            long time = tl.getMilliTime();
            bpmTime.merge(Math.round(prevBpm * 100), Math.max(0L, time - prevTime), Long::sum);
            prevBpm = tl.getBPM();
            prevTime = time;
            if (tl.getSectionLine()) {
                bars.add(time);
            }
            if (tl.getStop() > 0) {
                stops = true;
            }
            int bin = (int) Math.min(bins - 1, Math.max(0, time / BIN_MS));
            for (int lane = 0; lane < Math.min(lanes, tl.getLaneCount()); lane++) {
                Note note = tl.getNote(lane);
                if (note == null || note instanceof MineNote) {
                    continue;
                }
                if (note instanceof LongNote ln) {
                    if (ln.isEnd()) {
                        continue;
                    }
                    if (ln.getPair() != null) {
                        longNotes.add(new long[] {time, ln.getPair().getMilliTime()});
                    }
                }
                notes++;
                density[bin]++;
                activity[bin]++;
                if (first < 0) {
                    first = time;
                }
                last = time;
            }
            activity[bin] += tl.getBackGroundNotes().length;
        }
        bpmTime.merge(Math.round(prevBpm * 100), Math.max(0L, end - prevTime), Long::sum);
        double main = model.getBpm();
        long best = -1;
        for (var entry : bpmTime.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                main = entry.getKey() / 100.0;
            }
        }
        double startBpm = MixCompiler.bpmAt(model, Math.max(0, first) * 1000L);
        double endBpm = MixCompiler.bpmAt(model, end * 1000L);
        boolean steady = !stops && model.getMinBPM() == model.getMaxBPM();
        long[] barArray = bars.stream().mapToLong(Long::longValue).toArray();
        return new MixTrackAnalysis(end, Math.max(0, first), last, barArray, density, activity,
                longNotes.toArray(long[][]::new), startBpm, endBpm, main, steady, stops, notes);
    }

    /** Whether a long note is held across {@code ms} (started before, ends at or after). */
    public boolean longNoteAcross(long ms) {
        for (long[] span : longNotes) {
            if (span[0] < ms && span[1] >= ms) {
                return true;
            }
        }
        return false;
    }

    /** Whether any long note overlaps the half-open window [from, to). */
    public boolean longNoteWithin(long from, long to) {
        for (long[] span : longNotes) {
            if (span[1] >= from && span[0] < to) {
                return true;
            }
        }
        return false;
    }

    /** Notes per second around a chart time. */
    public double densityAround(long ms, long radiusMs) {
        int from = (int) Math.max(0, (ms - radiusMs) / BIN_MS);
        int to = (int) Math.min(density.length - 1, (ms + radiusMs) / BIN_MS);
        double sum = 0;
        for (int i = from; i <= to; i++) {
            sum += density[i];
        }
        return sum * 1000.0 / Math.max(1, (to - from + 1) * BIN_MS);
    }

    /** Average activity (sounds per second) in [from, to). */
    public double activity(long from, long to) {
        int a = (int) Math.max(0, from / BIN_MS);
        int b = (int) Math.min(activity.length - 1, Math.max(a, (to - 1) / BIN_MS));
        double sum = 0;
        for (int i = a; i <= b; i++) {
            sum += activity[i];
        }
        return sum * 1000.0 / Math.max(1, (b - a + 1) * BIN_MS);
    }

    /** Average playable notes per second of the whole chart. */
    public double averageDensity() {
        long span = Math.max(1000L, lastNoteMs - firstNoteMs);
        return totalNotes * 1000.0 / span;
    }

    /** Nearest bar line to {@code ms}, or {@code ms} itself when the chart has none. */
    public long nearestBar(long ms) {
        long best = ms;
        long distance = Long.MAX_VALUE;
        for (long bar : bars) {
            long d = Math.abs(bar - ms);
            if (d < distance) {
                distance = d;
                best = bar;
            }
        }
        return best;
    }

    /** Index of the bar line at or before {@code ms}; -1 before the first. */
    public int barIndexAtOrBefore(long ms) {
        int index = -1;
        for (int i = 0; i < bars.length && bars[i] <= ms; i++) {
            index = i;
        }
        return index;
    }

    /** Length of one bar at the end tempo (4/4). */
    public long barMsAtEnd() {
        return Math.round(240_000.0 / Math.max(1.0, endBpm));
    }

    public long barMsAtStart() {
        return Math.round(240_000.0 / Math.max(1.0, startBpm));
    }
}
