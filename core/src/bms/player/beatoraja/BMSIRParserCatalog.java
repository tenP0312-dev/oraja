package bms.player.beatoraja;

import bms.model.BMSDecoder;
import bms.model.BMSModel;
import bms.model.ChartInformation;
import bms.player.beatoraja.song.SongData;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Canonical parser metadata captured before gameplay transforms; no chart I/O. */
public final class BMSIRParserCatalog {
    private static final Map<BMSModel, Snapshot> snapshots = Collections.synchronizedMap(new WeakHashMap<>());

    private BMSIRParserCatalog() {}

    record Snapshot(String sha, String md5, int notes, int feature, int compatibility, String chartHash) {
        SongData song() {
            SongData song = new SongData();
            song.setSha256(sha); song.setMd5(md5);
            apply(song);
            return song;
        }

        void apply(SongData song) {
            song.setNotes(notes); song.setFeature(feature);
            song.setParsercompatibility(compatibility); song.setCharthash(chartHash);
        }

        boolean matches(SongData song) {
            return sha.equals(song.getSha256()) && md5.equals(song.getMd5());
        }
    }

    /** Only fresh, deterministic current-parser BMS decodes can refresh a catalog. */
    static void capture(BMSModel model) {
        snapshots.remove(model);
        if (model == null || !Integer.toString(BMSIRParserCompatibility.CURRENT_REVISION)
                .equals(model.getValues().get(BMSDecoder.COMPATIBILITY_REVISION))
                || BMSIRLongNoteMode.isApplied(model)
                || (model.getRandom() != null && model.getRandom().length > 0)) return;
        ChartInformation original = model.getChartInformation();
        if (original == null) return;
        try {
            // Catalogs describe the source chart, independent of the selected CN/HCN mode.
            model.setChartInformation(new ChartInformation(original.path, 0, original.selectedRandoms));
            SongData canonical = new SongData(model, false);
            snapshots.put(model, new Snapshot(canonical.getSha256(), canonical.getMd5(), canonical.getNotes(),
                    canonical.getFeature(), canonical.getParsercompatibility(), canonical.getCharthash()));
        } finally {
            model.setChartInformation(original);
        }
    }

    private static Snapshot snapshot(BMSModel model) {
        if (model != null && model.getValues().containsKey(
                bms.player.beatoraja.arena.bmsir.BMSIRManiacPlayContext.MODEL_STORAGE_HASH)) return null;
        return snapshots.get(model);
    }

    static SongData metadata(BMSModel model) {
        Snapshot captured = snapshot(model);
        return captured == null ? null : captured.song();
    }

    /** Refresh unloaded selector objects without copying a forced-LN play model into them. */
    public static boolean refreshVisibleSongs(SongData played, SongData[] visible) {
        return played != null && refreshVisibleModel(played.getBMSModel(), visible);
    }

    public static boolean refreshVisibleModel(BMSModel model, SongData[] visible) {
        Snapshot snapshot = snapshot(model);
        if (snapshot == null || visible == null) return false;
        boolean changed = false;
        for (SongData song : visible) {
            if (song != null && song.getBMSModel() == null && snapshot.matches(song)
                    && song.getParsercompatibility() >= 0 && song.getParsercompatibility() <= 3) {
                snapshot.apply(song);
                changed = true;
            }
        }
        return changed;
    }
}
