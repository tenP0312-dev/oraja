package bms.player.beatoraja.bmsir;

import bms.model.BMSModel;
import bms.model.ChartInformation;
import bms.model.LongNote;
import bms.model.Note;
import bms.model.TimeLine;

/**
 * Dedicated-client policy that interprets every long note as legacy LN.
 *
 * BMS/BMSON files can declare CN or HCN directly, so forcing only the player
 * configuration is not sufficient. This policy normalizes both the decoder
 * input and the decoded notes to keep gameplay, local metadata and IR payloads
 * on the same LN note-count scale.
 */
public final class BMSIRLongNotePolicy {

    public static final int IR_LN_TYPE = BMSModel.LNTYPE_LONGNOTE;

    private BMSIRLongNotePolicy() {
    }

    public static ChartInformation forceLongNote(ChartInformation information) {
        if (information == null) {
            return null;
        }
        return new ChartInformation(information.path, IR_LN_TYPE, information.selectedRandoms);
    }

    public static BMSModel normalizeModel(BMSModel model) {
        if (model == null) {
            return null;
        }

        ChartInformation information = model.getChartInformation();
        if (information != null && information.lntype != IR_LN_TYPE) {
            model.setChartInformation(forceLongNote(information));
        }

        // BMS #LNMODE uses LongNote.TYPE_* values (LN is 1), while the
        // player/IR lntype field uses BMSModel.LNTYPE_* values (LN is 0).
        model.setLnmode(LongNote.TYPE_LONGNOTE);

        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < timeline.getLaneCount(); lane++) {
                Note note = timeline.getNote(lane);
                if (note instanceof LongNote longNote) {
                    longNote.setType(LongNote.TYPE_LONGNOTE);
                }
            }
        }
        return model;
    }
}
