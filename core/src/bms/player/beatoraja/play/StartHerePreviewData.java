package bms.player.beatoraja.play;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.MineNote;
import bms.model.Note;
import bms.model.TimeLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * READY中の譜面先頭プレビュー用に、譜面ロード時に一度だけ作る不変データ。
 */
public final class StartHerePreviewData {
    public record PreviewNote(int lane) {
    }

    private static final StartHerePreviewData INVALID =
            new StartHerePreviewData(false, 0, List.of(), 120.0, 1.0);

    private final boolean valid;
    private final int laneCount;
    private final List<PreviewNote> notes;
    private final double anchorBpm;
    private final double anchorScroll;

    private StartHerePreviewData(
            boolean valid,
            int laneCount,
            List<PreviewNote> notes,
            double anchorBpm,
            double anchorScroll
    ) {
        this.valid = valid;
        this.laneCount = laneCount;
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
        this.anchorBpm = anchorBpm;
        this.anchorScroll = anchorScroll;
    }

    public static StartHerePreviewData build(BMSModel model) {
        if (
                model == null
                        || model.getMode() == null
                        || model.getMode().key <= 0
                        || model.getAllTimeLines() == null
        ) {
            return INVALID;
        }

        int laneCount = model.getMode().key;
        boolean[] markedLanes = new boolean[laneCount];
        List<PreviewNote> notes = new ArrayList<>();
        long firstMicroTime = Long.MIN_VALUE;

        for (TimeLine timeline : model.getAllTimeLines()) {
            if (firstMicroTime != Long.MIN_VALUE && timeline.getMicroTime() != firstMicroTime) {
                break;
            }
            for (int lane = 0; lane < laneCount; lane++) {
                Note note = timeline.getNote(lane);
                if (
                        note == null
                                || note instanceof MineNote
                                || note instanceof LongNote longNote && longNote.isEnd()
                ) {
                    continue;
                }
                if (firstMicroTime == Long.MIN_VALUE) {
                    firstMicroTime = timeline.getMicroTime();
                }
                if (!markedLanes[lane]) {
                    markedLanes[lane] = true;
                    notes.add(new PreviewNote(lane));
                }
            }
        }
        if (notes.isEmpty()) {
            return INVALID;
        }
        return new StartHerePreviewData(
                true,
                laneCount,
                notes,
                resolvePositiveBpm(model, firstMicroTime),
                resolvePositiveScroll(model, firstMicroTime)
        );
    }

    private static double resolvePositiveBpm(BMSModel model, long anchorMicroTime) {
        double before = positiveOrZero(model.getBpm());
        double after = 0.0;
        for (TimeLine timeline : model.getAllTimeLines()) {
            double bpm = positiveOrZero(timeline.getBPM());
            if (timeline.getMicroTime() <= anchorMicroTime && bpm > 0.0) {
                before = bpm;
            } else if (timeline.getMicroTime() > anchorMicroTime && bpm > 0.0) {
                after = bpm;
                break;
            }
        }
        if (before > 0.0) {
            return before;
        }
        if (after > 0.0) {
            return after;
        }
        double main = positiveOrZero(model.getBpm());
        return main > 0.0 ? main : 120.0;
    }

    private static double resolvePositiveScroll(BMSModel model, long anchorMicroTime) {
        double before = 1.0;
        double after = 0.0;
        for (TimeLine timeline : model.getAllTimeLines()) {
            double scroll = positiveOrZero(timeline.getScroll());
            if (timeline.getMicroTime() <= anchorMicroTime && scroll > 0.0) {
                before = scroll;
            } else if (timeline.getMicroTime() > anchorMicroTime && scroll > 0.0) {
                after = scroll;
                break;
            }
        }
        return before > 0.0 ? before : after > 0.0 ? after : 1.0;
    }

    private static double positiveOrZero(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    public boolean isValid() {
        return valid;
    }

    public int laneCount() {
        return laneCount;
    }

    public List<PreviewNote> notes() {
        return notes;
    }

    public double anchorBpm() {
        return anchorBpm;
    }

    public double anchorScroll() {
        return anchorScroll;
    }
}
