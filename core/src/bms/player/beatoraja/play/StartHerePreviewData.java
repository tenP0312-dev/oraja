package bms.player.beatoraja.play;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.MineNote;
import bms.model.Note;
import bms.model.TimeLine;
import bms.player.beatoraja.PlayConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * READY中の譜面先頭プレビュー用に、譜面ロード時に一度だけ作る不変データ。
 */
public final class StartHerePreviewData {
    public record PreviewNote(int lane, double section, boolean firstChord) {
    }

    private static final StartHerePreviewData INVALID =
            new StartHerePreviewData(false, 0, 0, List.of());

    private final boolean valid;
    private final int laneCount;
    private final int measures;
    private final List<PreviewNote> notes;

    private StartHerePreviewData(
            boolean valid,
            int laneCount,
            int measures,
            List<PreviewNote> notes
    ) {
        this.valid = valid;
        this.laneCount = laneCount;
        this.measures = measures;
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
    }

    public static StartHerePreviewData build(
            BMSModel model,
            int measures,
            int maxNotesPerSide
    ) {
        if (
                model == null
                        || model.getMode() == null
                        || model.getMode().key <= 0
                        || measures < PlayConfig.START_HERE_PREVIEW_MEASURES_MIN
                        || measures > PlayConfig.START_HERE_PREVIEW_MEASURES_MAX
                        || maxNotesPerSide < PlayConfig.START_HERE_PREVIEW_MAX_NOTES_MIN
                        || maxNotesPerSide > PlayConfig.START_HERE_PREVIEW_MAX_NOTES_MAX
        ) {
            return INVALID;
        }

        int laneCount = model.getMode().key;
        int players = Math.max(1, model.getMode().player);
        int lanesPerPlayer = Math.max(1, laneCount / players);
        int[] sideCounts = new int[players];
        List<PreviewNote> notes = new ArrayList<>();
        double firstSection = Double.NaN;

        for (TimeLine timeline : model.getAllTimeLines()) {
            double section = timeline.getSection();
            if (section < 0 || section >= measures) {
                continue;
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
                int side = Math.min(players - 1, lane / lanesPerPlayer);
                sideCounts[side]++;
                if (sideCounts[side] > maxNotesPerSide) {
                    return INVALID;
                }
                if (Double.isNaN(firstSection)) {
                    firstSection = section;
                }
                notes.add(new PreviewNote(lane, section, section == firstSection));
            }
        }
        if (notes.isEmpty()) {
            return INVALID;
        }
        return new StartHerePreviewData(true, laneCount, measures, notes);
    }

    public boolean isValid() {
        return valid;
    }

    public int laneCount() {
        return laneCount;
    }

    public int measures() {
        return measures;
    }

    public List<PreviewNote> notes() {
        return notes;
    }
}
