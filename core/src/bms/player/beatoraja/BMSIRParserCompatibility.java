package bms.player.beatoraja;

import bms.model.BMSDecoder;
import bms.model.BMSModel;
import bms.player.beatoraja.song.SongData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Local storage only: the chart's public MD5/SHA and IR identity stay authored. */
public final class BMSIRParserCompatibility {
    public static final int CURRENT_REVISION = 3;
    public static final int AFFECTED_METADATA = 3;
    private BMSIRParserCompatibility() {}

    public static boolean affected(BMSModel model) {
        return model != null && ("2".equals(model.getValues().get(BMSDecoder.COMPATIBILITY_REVISION))
                || "3".equals(model.getValues().get(BMSDecoder.COMPATIBILITY_REVISION)))
                && "1".equals(model.getValues().get(BMSDecoder.COMPATIBILITY_AFFECTED));
    }
    public static boolean affected(SongData song) {
        int metadata = song.getParsercompatibility();
        return metadata == AFFECTED_METADATA || metadata >= 5 && metadata <= 7;
    }
    public static int catalogMetadata(BMSModel model) {
        String revision = model.getValues().get(BMSDecoder.COMPATIBILITY_REVISION);
        if (revision == null) return 4; // bmson has no historical BMS interpretation.
        if ("1".equals(revision)) return 1;
        if (!"3".equals(revision)) return affected(model) ? 3 : 2;
        if ("1".equals(model.getValues().get(BMSDecoder.ORPHAN_LNOBJ_AFFECTED)))
            return "1".equals(model.getValues().get(BMSDecoder.V2_AFFECTED)) ? 7 : 6;
        return affected(model) ? 5 : 4;
    }
    public static boolean isCurrentMetadata(int metadata) { return metadata >= 4 && metadata <= 7; }
    public static String storageHash(BMSModel model, String base) {
        return storageHash(base, affected(model) ? (catalogMetadata(model) >= 6 ? 3 : 2) : 0);
    }
    public static String storageHash(SongData song, String base) {
        return storageHash(base, affected(song) ? (song.getParsercompatibility() >= 6 ? 3 : 2) : 0);
    }
    /** Read-only replay fallback; never use a prior interpretation as a current PB. */
    public static String previousStorageHash(BMSModel model, String base) {
        return storageHash(base, "1".equals(model.getValues().get(BMSDecoder.V2_AFFECTED)) ? 2 : 0);
    }
    public static String previousStorageHash(SongData song, String base) {
        int metadata = song.getParsercompatibility();
        return storageHash(base, metadata == 3 || metadata == 5 || metadata == 7 ? 2 : 0);
    }
    private static String storageHash(String base, int revision) {
        if (revision == 0) return base;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("bmsir-parser-v" + revision + ":" + base).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
