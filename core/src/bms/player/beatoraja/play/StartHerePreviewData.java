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
            new StartHerePreviewData(false, 0, List.of());

    private final boolean valid;
    private final int laneCount;
    private final List<PreviewNote> notes;

    private StartHerePreviewData(
            boolean valid,
            int laneCount,
            List<PreviewNote> notes
    ) {
        this.valid = valid;
        this.laneCount = laneCount;
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
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
        return new StartHerePreviewData(true, laneCount, notes);
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
}
