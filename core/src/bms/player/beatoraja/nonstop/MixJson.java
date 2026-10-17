package bms.player.beatoraja.nonstop;

import bms.model.Mode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Recipe format of a {@link MixPlan}. The shared form never contains local
 * paths or audio: receivers match songs by chart hash.
 */
public final class MixJson {
    public static final String FORMAT = "bmsir-nonstop-mix";
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Upper bound for a recipe accepted from a file or the IR. */
    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_TRACKS = 64;

    private MixJson() {
    }

    public static ObjectMapper mapper() {
        return JSON;
    }

    /**
     * @param local include local-only fields (local id and paths); false for sharing
     */
    public static ObjectNode toJson(MixPlan plan, boolean local) {
        ObjectNode root = JSON.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", MixPlan.FORMAT_VERSION);
        if (local) {
            root.put("id", plan.id);
            root.put("createdAt", plan.createdAt);
            root.put("updatedAt", plan.updatedAt);
        }
        root.put("name", plan.name);
        root.put("description", plan.description);
        root.put("author", plan.author);
        root.put("mode", plan.mode.hint);
        root.put("judge", plan.judge.name());
        root.put("totalScale", plan.totalScale);
        root.put("normalizeLoudness", plan.normalizeLoudness);
        root.put("origin", plan.origin);
        root.put("irId", plan.irId);
        root.put("irRevision", plan.irRevision);
        ArrayNode tracks = root.putArray("tracks");
        for (MixTrack track : plan.tracks) {
            ObjectNode node = tracks.addObject();
            node.put("sha256", track.sha256);
            node.put("md5", track.md5);
            node.put("title", track.title);
            node.put("subtitle", track.subtitle);
            node.put("artist", track.artist);
            node.put("genre", track.genre);
            node.put("bpm", track.bpm);
            node.put("level", track.level);
            node.put("inMs", track.inMs);
            node.put("outMs", track.outMs);
            node.put("gainDb", track.gainDb);
            if (track.random != null) {
                ArrayNode random = node.putArray("random");
                for (int value : track.random) {
                    random.add(value);
                }
            }
            node.put("lockSong", track.lockSong);
            node.put("lockRange", track.lockRange);
            if (local && track.localPath != null && !track.localPath.isEmpty()) {
                node.put("localPath", track.localPath);
            }
        }
        ArrayNode joins = root.putArray("transitions");
        for (MixTransition join : plan.transitions) {
            ObjectNode node = joins.addObject();
            node.put("type", join.type.name());
            node.put("curve", join.curve.name());
            node.put("overlapMs", join.overlapMs);
            node.put("handoffMs", join.handoffMs);
            node.put("gapMs", join.gapMs);
            node.put("locked", join.locked);
        }
        return root;
    }

    public static String toText(MixPlan plan, boolean local) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(toJson(plan, local));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static MixPlan fromText(String text) throws IOException {
        if (text == null) {
            throw new IOException("empty recipe");
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IOException("recipe is too large");
        }
        return fromJson(JSON.readTree(text));
    }

    /** Parses a recipe; unknown or invalid values fall back to safe defaults. */
    public static MixPlan fromJson(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("recipe must be a JSON object");
        }
        String format = root.path("format").asText(FORMAT);
        if (!FORMAT.equals(format)) {
            throw new IllegalArgumentException("not a NONSTOP MIX recipe");
        }
        int version = root.path("version").asInt(MixPlan.FORMAT_VERSION);
        if (version > MixPlan.FORMAT_VERSION) {
            throw new IllegalArgumentException("recipe version " + version + " is newer than this client");
        }
        MixPlan plan = new MixPlan();
        if (root.hasNonNull("id")) {
            plan.id = limited(root.path("id").asText(plan.id), 64);
        }
        plan.createdAt = root.path("createdAt").asLong(plan.createdAt);
        plan.updatedAt = root.path("updatedAt").asLong(plan.updatedAt);
        plan.name = limited(root.path("name").asText(""), 80);
        plan.description = limited(root.path("description").asText(""), 1000);
        plan.author = limited(root.path("author").asText(""), 40);
        Mode mode = Mode.getMode(root.path("mode").asText(Mode.BEAT_7K.hint));
        plan.mode = mode != null ? mode : Mode.BEAT_7K;
        plan.judge = enumValue(MixPlan.JudgeRule.class, root.path("judge").asText(), MixPlan.JudgeRule.NORMAL);
        plan.totalScale = clamp(root.path("totalScale").asDouble(1.0), 0.25, 4.0, 1.0);
        plan.normalizeLoudness = root.path("normalizeLoudness").asBoolean(true);
        plan.origin = limited(root.path("origin").asText("manual"), 32);
        plan.irId = limited(root.path("irId").asText(""), 64);
        plan.irRevision = root.path("irRevision").asInt(0);
        JsonNode tracks = root.path("tracks");
        if (tracks.isArray()) {
            for (JsonNode node : tracks) {
                if (plan.tracks.size() >= MAX_TRACKS) {
                    break;
                }
                MixTrack track = new MixTrack();
                track.sha256 = hash(node.path("sha256").asText(""), 64);
                track.md5 = hash(node.path("md5").asText(""), 32);
                track.title = limited(node.path("title").asText(""), 200);
                track.subtitle = limited(node.path("subtitle").asText(""), 200);
                track.artist = limited(node.path("artist").asText(""), 200);
                track.genre = limited(node.path("genre").asText(""), 100);
                track.bpm = clamp(node.path("bpm").asDouble(0), 0, 100000, 0);
                track.level = node.path("level").asInt(0);
                track.inMs = Math.max(0L, node.path("inMs").asLong(0));
                track.outMs = node.path("outMs").asLong(-1);
                if (track.outMs >= 0 && track.outMs < track.inMs) {
                    track.outMs = track.inMs;
                }
                track.gainDb = clamp(node.path("gainDb").asDouble(0), -24, 12, 0);
                JsonNode random = node.path("random");
                if (random.isArray() && random.size() <= 256) {
                    track.random = new int[random.size()];
                    for (int i = 0; i < random.size(); i++) {
                        track.random[i] = random.get(i).asInt(1);
                    }
                }
                track.lockSong = node.path("lockSong").asBoolean(false);
                track.lockRange = node.path("lockRange").asBoolean(false);
                track.localPath = node.path("localPath").asText("");
                plan.tracks.add(track);
            }
        }
        JsonNode joins = root.path("transitions");
        if (joins.isArray()) {
            for (JsonNode node : joins) {
                if (plan.transitions.size() >= Math.max(0, plan.tracks.size() - 1)) {
                    break;
                }
                MixTransition join = new MixTransition();
                join.type = enumValue(MixTransition.Type.class, node.path("type").asText(), MixTransition.Type.BLEND);
                join.curve = enumValue(MixTransition.Curve.class, node.path("curve").asText(),
                        MixTransition.Curve.EQUAL_POWER);
                join.overlapMs = Math.max(0L, Math.min(MixTransition.MAX_OVERLAP_MS,
                        node.path("overlapMs").asLong(MixTransition.DEFAULT_BLEND_MS)));
                join.handoffMs = Math.max(0L, node.path("handoffMs").asLong(join.overlapMs / 2));
                join.gapMs = Math.max(0L, Math.min(10_000L, node.path("gapMs").asLong(0)));
                join.locked = node.path("locked").asBoolean(false);
                plan.transitions.add(join);
            }
        }
        plan.normalizeTransitions();
        return plan;
    }

    /**
     * Identity of what is actually played: songs, used parts, joins and rules.
     * Names and descriptions do not change it, so renaming keeps the records.
     */
    public static String playIdentity(MixPlan plan) {
        StringBuilder text = new StringBuilder();
        text.append(MixPlan.FORMAT_VERSION).append('|').append(plan.mode.hint).append('|')
                .append(plan.judge.name()).append('|')
                .append(String.format(Locale.ROOT, "%.4f", plan.totalScale)).append('|')
                .append(plan.normalizeLoudness).append('\n');
        for (MixTrack track : plan.tracks) {
            text.append(track.sha256.isEmpty() ? track.md5 : track.sha256).append('|')
                    .append(track.inMs).append('|').append(track.outMs).append('|')
                    .append(String.format(Locale.ROOT, "%.3f", track.gainDb)).append('|');
            if (track.random != null) {
                for (int value : track.random) {
                    text.append(value).append(',');
                }
            }
            text.append('\n');
        }
        for (MixTransition join : plan.transitions) {
            text.append(join.type.name()).append('|').append(join.curve.name()).append('|')
                    .append(join.overlapMs).append('|').append(join.effectiveHandoffMs()).append('|')
                    .append(join.gapMs).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hash(String value, int length) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[0-9a-f]{" + length + "}") ? normalized : "";
    }

    static String limited(String value, int maxCodePoints) {
        String text = value == null ? "" : value;
        if (text.codePointCount(0, text.length()) <= maxCodePoints) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
