package bms.player.beatoraja.ir;

import bms.model.*;
import bms.player.beatoraja.BMSIRLongNoteMode;
import bms.player.beatoraja.play.NantokaManiaRules;
import bms.player.beatoraja.song.*;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;

/** Effective interpretation, independent of local score storage keys. */
public record IRLongNoteContract(int lntype, int notes) {
    public static final IRLongNoteContract UNKNOWN = new IRLongNoteContract(-1, -1);

    public static IRLongNoteContract fromModel(BMSModel model, int selected, boolean forceLn) {
        int type = 0, notes = 0;
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < model.getMode().key; lane++) {
                Note note = timeline.getNote(lane);
                if (note instanceof NormalNote) notes++;
                else if (note instanceof LongNote ln) {
                    int effective = forceLn ? 0 : ln.getType() == LongNote.TYPE_UNDEFINED
                            ? selected : ln.getType() - 1;
                    // A mixed chart belongs to its highest effective LN type.
                    type = Math.max(type, effective);
                    if (!ln.isEnd() || effective != 0) notes++;
                }
            }
        }
        if (NantokaManiaRules.isActive(model)) notes = NantokaManiaRules.totalNotes(model);
        return new IRLongNoteContract(type, notes);
    }

    /** Called on the existing ranking worker, never mutates catalog/play models. */
    public static IRLongNoteContract forRanking(SongData song, int selected, boolean forceLn) {
        BMSModel model = song.getBMSModel();
        if (model != null && (forceLn || !BMSIRLongNoteMode.isApplied(model))) {
            return fromModel(model, selected, forceLn);
        }
        if (song.getPath() == null || song.getPath().isBlank()) return UNKNOWN;
        try {
            SongResource resource = SongResources.fromPath(Path.of(song.getPath()));
            String name = resource.name().toLowerCase(Locale.ROOT);
            if (name.endsWith(".bms") || name.endsWith(".bme") || name.endsWith(".bml") || name.endsWith(".pms")) {
                try (InputStream input = resource.openStream()) {
                    byte[] bytes = input.readNBytes(16 * 1024 * 1024 + 1);
                    if (bytes.length > 16 * 1024 * 1024) return UNKNOWN;
                    model = new BMSDecoder(selected).decode(bytes, name.endsWith(".pms"),
                            song.getBMSModel() == null ? null : song.getBMSModel().getRandom());
                }
            } else {
                Path path = resource.materialize();
                ChartDecoder decoder = ChartDecoder.getDecoder(path);
                model = decoder == null ? null : decoder.decode(new ChartInformation(path, selected, null));
            }
            // Do not associate a changed file with the catalog's former chart identity.
            if (model == null || (song.getSha256() != null && !song.getSha256().isBlank()
                    && !song.getSha256().equalsIgnoreCase(model.getSHA256()))) return UNKNOWN;
            return fromModel(model, selected, forceLn);
        } catch (Exception error) {
            return UNKNOWN;
        }
    }

    public boolean matches(IRScoreData score) {
        return score != null && lntype >= 0 && notes > 0
                && score.lntype == lntype && score.notes == notes;
    }
}
