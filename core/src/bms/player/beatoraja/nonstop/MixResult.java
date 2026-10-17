package bms.player.beatoraja.nonstop;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.MineNote;
import bms.model.Note;
import bms.model.TimeLine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of one play of a set: the whole set and each song's part
 * (Issue #535 §8). Kept apart from ordinary chart, course and Dan records.
 */
public final class MixResult {
    public String identity = "";
    public String name = "";
    public String irId = "";
    public int irRevision;
    public long playedAt = System.currentTimeMillis();
    public int exScore;
    public int maxExScore;
    /** {@link bms.player.beatoraja.ClearType} id. */
    public int clear;
    public int gauge;
    public int badPoor;
    public int maxCombo;
    public int notes;
    /** The set was played to its end (not quit). */
    public boolean finished;
    public final List<TrackResult> tracks = new ArrayList<>();

    /** One song's part of the set. */
    public static final class TrackResult {
        public String title = "";
        public String sha256 = "";
        public int notes;
        /** PG, GR, GD, BD, PR, MS. */
        public final int[] judges = new int[6];
        /** Notes not reached (the play ended before them). */
        public int unreached;

        public int exScore() {
            return judges[0] * 2 + judges[1];
        }

        public double rate() {
            return notes == 0 ? 0 : exScore() * 50.0 / notes;
        }
    }

    public double rate() {
        return maxExScore == 0 ? 0 : exScore * 100.0 / maxExScore;
    }

    /** Collects each song's judgements from the notes of the played chart. */
    public static List<TrackResult> perTrack(CompiledMix mix, BMSModel played) {
        List<TrackResult> rows = new ArrayList<>();
        for (int i = 0; i < mix.trackCount(); i++) {
            TrackResult row = new TrackResult();
            MixTrack track = mix.plan().tracks.get(i);
            row.title = track.displayTitle();
            row.sha256 = track.sha256;
            rows.add(row);
        }
        int lanes = played.getMode().key;
        for (TimeLine tl : played.getAllTimeLines()) {
            for (int lane = 0; lane < lanes; lane++) {
                Note note = tl.getNote(lane);
                if (note == null || note instanceof MineNote) {
                    continue;
                }
                if (note instanceof LongNote ln && ln.isEnd()
                        && ln.getType() != LongNote.TYPE_CHARGENOTE && ln.getType() != LongNote.TYPE_HELLCHARGENOTE
                        && !(ln.getType() == LongNote.TYPE_UNDEFINED && played.getLntype() != BMSModel.LNTYPE_LONGNOTE)) {
                    // a plain long note is judged once, at its start
                    continue;
                }
                int track = mix.trackOf(note);
                if (track < 0) {
                    continue;
                }
                TrackResult row = rows.get(track);
                row.notes++;
                int state = note.getState();
                if (state <= 0) {
                    row.unreached++;
                } else if (state - 1 < row.judges.length) {
                    row.judges[state - 1]++;
                }
            }
        }
        return rows;
    }

    public ObjectNode toJson() {
        ObjectNode node = MixJson.mapper().createObjectNode();
        node.put("identity", identity);
        node.put("name", name);
        node.put("irId", irId);
        node.put("irRevision", irRevision);
        node.put("playedAt", playedAt);
        node.put("exScore", exScore);
        node.put("maxExScore", maxExScore);
        node.put("clear", clear);
        node.put("gauge", gauge);
        node.put("badPoor", badPoor);
        node.put("maxCombo", maxCombo);
        node.put("notes", notes);
        node.put("finished", finished);
        ArrayNode rows = node.putArray("tracks");
        for (TrackResult track : tracks) {
            ObjectNode row = rows.addObject();
            row.put("title", track.title);
            row.put("sha256", track.sha256);
            row.put("notes", track.notes);
            ArrayNode judges = row.putArray("judges");
            for (int value : track.judges) {
                judges.add(value);
            }
            row.put("unreached", track.unreached);
        }
        return node;
    }

    public static MixResult fromJson(JsonNode node) {
        MixResult result = new MixResult();
        result.identity = node.path("identity").asText("");
        result.name = node.path("name").asText("");
        result.irId = node.path("irId").asText("");
        result.irRevision = node.path("irRevision").asInt(0);
        result.playedAt = node.path("playedAt").asLong(0);
        result.exScore = node.path("exScore").asInt(0);
        result.maxExScore = node.path("maxExScore").asInt(0);
        result.clear = node.path("clear").asInt(0);
        result.gauge = node.path("gauge").asInt(0);
        result.badPoor = node.path("badPoor").asInt(0);
        result.maxCombo = node.path("maxCombo").asInt(0);
        result.notes = node.path("notes").asInt(0);
        result.finished = node.path("finished").asBoolean(false);
        for (JsonNode item : node.path("tracks")) {
            TrackResult track = new TrackResult();
            track.title = item.path("title").asText("");
            track.sha256 = item.path("sha256").asText("");
            track.notes = item.path("notes").asInt(0);
            JsonNode judges = item.path("judges");
            for (int i = 0; i < track.judges.length && i < judges.size(); i++) {
                track.judges[i] = judges.get(i).asInt(0);
            }
            track.unreached = item.path("unreached").asInt(0);
            result.tracks.add(track);
        }
        return result;
    }
}
