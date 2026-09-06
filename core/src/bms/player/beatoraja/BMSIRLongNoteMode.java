package bms.player.beatoraja;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.TimeLine;
import bms.player.beatoraja.song.SongData;

/** Dedicated-client play policy; the parser still preserves the authored chart. */
public final class BMSIRLongNoteMode {
    private static final String AUTHORED_FEATURES = "bmsir.authoredLongNoteFeatures";
    private static final int DEFINED = SongData.FEATURE_LONGNOTE
            | SongData.FEATURE_CHARGENOTE | SongData.FEATURE_HELLCHARGENOTE;
    public static final int SCORE_POLICY = 1;

    private BMSIRLongNoteMode() {}

    public static void apply(BMSModel model) {
        if (model == null) return;
        model.getValues().putIfAbsent(AUTHORED_FEATURES,
                Integer.toString(authoredFeatures(model)));
        // TYPE_UNDEFINED delegates judgment, rendering and note counting to
        // ChartInformation.lntype, already chosen by the player/locked Arena rule.
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < model.getMode().key; lane++) {
                if (timeline.getNote(lane) instanceof LongNote note) {
                    note.setType(LongNote.TYPE_UNDEFINED);
                    if (note.getPair() != null) note.getPair().setType(LongNote.TYPE_UNDEFINED);
                }
            }
        }
        model.setLnmode(LongNote.TYPE_UNDEFINED);
    }

    public static int authoredFeatures(BMSModel model) {
        if (model == null) return 0;
        String saved = model.getValues().get(AUTHORED_FEATURES);
        if (saved != null) return Integer.parseInt(saved);
        int features = 0;
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < model.getMode().key; lane++) {
                if (timeline.getNote(lane) instanceof LongNote note) {
                    features |= switch (note.getType()) {
                        case LongNote.TYPE_LONGNOTE -> SongData.FEATURE_LONGNOTE;
                        case LongNote.TYPE_CHARGENOTE -> SongData.FEATURE_CHARGENOTE;
                        case LongNote.TYPE_HELLCHARGENOTE -> SongData.FEATURE_HELLCHARGENOTE;
                        default -> SongData.FEATURE_UNDEFINEDLN;
                    };
                }
            }
        }
        return features;
    }

    public static boolean changesAuthoredMode(BMSModel model) {
        return (authoredFeatures(model) & DEFINED) != 0;
    }

    public static boolean changesAuthoredMode(SongData song) {
        return song.getBMSModel() != null ? changesAuthoredMode(song.getBMSModel())
                : (song.getFeature() & DEFINED) != 0;
    }

    public static ScoreData compatibleScore(ScoreData score, boolean changed) {
        return score != null && changed && score.getBmsirLongNotePolicy() != SCORE_POLICY
                ? null : score;
    }

    public static boolean authoredUndefined(BMSModel model) {
        return (authoredFeatures(model) & SongData.FEATURE_UNDEFINEDLN) != 0;
    }

    public static String replayHash(BMSModel model, String baseHash) {
        return model.getValues().containsKey(AUTHORED_FEATURES) && changesAuthoredMode(model)
                ? forcedReplayHash(baseHash) : baseHash;
    }

    public static String replayHash(SongData song, String baseHash) {
        return changesAuthoredMode(song) ? forcedReplayHash(baseHash) : baseHash;
    }

    private static String forcedReplayHash(String baseHash) {
        try {
            byte[] identity = ("bmsir-forced-ln-replay-v1:" + baseHash)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(identity));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
