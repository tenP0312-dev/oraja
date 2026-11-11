package bms.player.beatoraja.nonstop;

import bms.player.beatoraja.MainController;
import bms.player.beatoraja.arena.bmsir.BMSIRNonstopMixTransport;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Publishing NONSTOP MIX sets on BMS-IR, browsing other players' sets and
 * sending set scores (Issue #535). Requests run on a worker thread; results
 * are handed to the caller's callback, which must only store them (the
 * overlay reads them on the render thread).
 */
public final class MixIrClient {
    private static final Logger logger = LoggerFactory.getLogger(MixIrClient.class);

    private MixIrClient() {
    }

    /** One published set in a list. */
    public static final class Listing {
        public String id = "";
        public int revision;
        public String name = "";
        public String author = "";
        public String description = "";
        public String mode = "";
        public int trackCount;
        public long durationMs;
        public int plays;
        public long updatedAt;
        public boolean own;
        public final List<String> genres = new ArrayList<>();
        public final List<String[]> tracks = new ArrayList<>();
    }

    public record Page(List<Listing> sets, int total) {
    }

    public record Score(String player, int exScore, int maxExScore, int clear, int badPoor, long playedAt) {
    }

    /** Outcome of a request: either a value or a message for the player. */
    public record Result<T>(T value, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    public static void list(MainController main, String query, String mode, String sort, int page,
            Consumer<Result<Page>> callback) {
        run("list", callback, () -> {
            ObjectNode payload = MixJson.mapper().createObjectNode();
            payload.put("action", "list");
            payload.put("query", MixJson.limited(query == null ? "" : query, 80));
            payload.put("mode", mode == null ? "" : mode);
            payload.put("sort", sort == null ? "new" : sort);
            payload.put("page", Math.max(0, page));
            payload.put("page_size", 30);
            BMSIRNonstopMixTransport.Response response = BMSIRNonstopMixTransport.post(main, payload,
                    BMSIRNonstopMixTransport.hasLogin(main));
            if (!response.ok()) {
                return new Result<>(null, errorText(response.error()));
            }
            List<Listing> sets = new ArrayList<>();
            for (JsonNode node : response.body().path("sets")) {
                sets.add(listing(node));
            }
            return new Result<>(new Page(sets, response.body().path("total").asInt(sets.size())), null);
        });
    }

    public static void get(MainController main, String id, Consumer<Result<MixPlan>> callback) {
        run("get", callback, () -> {
            ObjectNode payload = MixJson.mapper().createObjectNode();
            payload.put("action", "get");
            payload.put("id", id);
            BMSIRNonstopMixTransport.Response response = BMSIRNonstopMixTransport.post(main, payload,
                    BMSIRNonstopMixTransport.hasLogin(main));
            if (!response.ok()) {
                return new Result<>(null, errorText(response.error()));
            }
            JsonNode set = response.body().path("set");
            MixPlan plan = MixJson.fromJson(set.path("recipe"));
            // a downloaded set is a new local set bound to the published revision
            plan.id = java.util.UUID.randomUUID().toString();
            plan.irId = set.path("id").asText(id);
            plan.irRevision = set.path("revision").asInt(0);
            if (plan.author.isEmpty()) {
                plan.author = set.path("author_name").asText("");
            }
            return new Result<>(plan, null);
        });
    }

    /** Publishes a new set, or a new revision of the player's own published set. */
    public static void publish(MainController main, MixPlan plan, Consumer<Result<MixPlan>> callback) {
        MixPlan snapshot = plan.copy();
        run("publish", callback, () -> {
            if (!BMSIRNonstopMixTransport.hasLogin(main)) {
                return new Result<>(null, text("BMS-IRのログイン設定が必要です", "A BMS-IR login is required"));
            }
            ObjectNode payload = MixJson.mapper().createObjectNode();
            payload.put("action", "publish");
            if (!snapshot.irId.isEmpty()) {
                payload.put("id", snapshot.irId);
            }
            payload.set("recipe", MixJson.toJson(snapshot, false));
            payload.put("identity", MixJson.playIdentity(snapshot));
            payload.put("duration_ms", estimatedDuration(snapshot));
            BMSIRNonstopMixTransport.Response response = BMSIRNonstopMixTransport.post(main, payload, true);
            if (!response.ok()) {
                return new Result<>(null, errorText(response.error()));
            }
            snapshot.irId = response.body().path("id").asText(snapshot.irId);
            snapshot.irRevision = response.body().path("revision").asInt(snapshot.irRevision + 1);
            return new Result<>(snapshot, null);
        });
    }

    public static void unpublish(MainController main, String id, Consumer<Result<Boolean>> callback) {
        run("unpublish", callback, () -> {
            ObjectNode payload = MixJson.mapper().createObjectNode();
            payload.put("action", "unpublish");
            payload.put("id", id);
            BMSIRNonstopMixTransport.Response response = BMSIRNonstopMixTransport.post(main, payload, true);
            return response.ok() ? new Result<>(true, null) : new Result<>(null, errorText(response.error()));
        });
    }

    public static void ranking(MainController main, String id, int revision, Consumer<Result<List<Score>>> callback) {
        run("ranking", callback, () -> {
            ObjectNode payload = MixJson.mapper().createObjectNode();
            payload.put("action", "ranking");
            payload.put("id", id);
            payload.put("revision", revision);
            BMSIRNonstopMixTransport.Response response = BMSIRNonstopMixTransport.post(main, payload,
                    BMSIRNonstopMixTransport.hasLogin(main));
            if (!response.ok()) {
                return new Result<>(null, errorText(response.error()));
            }
            List<Score> scores = new ArrayList<>();
            for (JsonNode node : response.body().path("scores")) {
                scores.add(new Score(node.path("player_name").asText(""), node.path("ex_score").asInt(0),
                        node.path("max_ex_score").asInt(0), node.path("clear").asInt(0),
                        node.path("bad_poor").asInt(0), node.path("played_at").asLong(0)));
            }
            return new Result<>(scores, null);
        });
    }

    /**
     * Sends a finished play of a published set. Only the revision the set was
     * downloaded or published as is accepted; an edited set has a different identity.
     */
    public static void submitScoreAsync(MainController main, CompiledMix mix, MixResult result) {
        if (!BMSIRNonstopMixTransport.hasLogin(main)) {
            return;
        }
        run("submit_score", (Result<Boolean> outcome) -> MixMainThread.post(() -> {
            if (outcome.ok()) {
                ImGuiNotify.info(text("NONSTOP MIXのスコアをIRに送信しました", "Sent the NONSTOP MIX score to IR"));
            } else {
                ImGuiNotify.warning(text("NONSTOP MIXのスコアを送信できませんでした: ", "Could not send the NONSTOP MIX score: ")
                        + outcome.error());
            }
        }), () -> {
            ObjectNode payload = MixJson.mapper().createObjectNode();
            payload.put("action", "submit_score");
            payload.put("id", result.irId);
            payload.put("revision", result.irRevision);
            payload.put("identity", result.identity);
            payload.put("ex_score", result.exScore);
            payload.put("max_ex_score", result.maxExScore);
            payload.put("clear", result.clear);
            payload.put("gauge", result.gauge);
            payload.put("bad_poor", result.badPoor);
            payload.put("max_combo", result.maxCombo);
            payload.put("notes", result.notes);
            payload.put("finished", result.finished);
            ArrayNode tracks = payload.putArray("tracks");
            for (MixResult.TrackResult track : result.tracks) {
                ObjectNode row = tracks.addObject();
                row.put("sha256", track.sha256);
                row.put("notes", track.notes);
                ArrayNode judges = row.putArray("judges");
                for (int value : track.judges) {
                    judges.add(value);
                }
                row.put("unreached", track.unreached);
            }
            BMSIRNonstopMixTransport.Response response = BMSIRNonstopMixTransport.post(main, payload, true);
            return response.ok() ? new Result<>(true, null) : new Result<>(null, errorText(response.error()));
        });
    }

    /** Rough length of a set; parts without an end count as two minutes. */
    public static long estimatedDuration(MixPlan plan) {
        long total = 0;
        for (int i = 0; i < plan.tracks.size(); i++) {
            MixTrack track = plan.tracks.get(i);
            long out = track.outMs >= 0 ? track.outMs : track.inMs + 120_000L;
            total += Math.max(0L, out - track.inMs);
            if (i > 0) {
                total -= plan.transitions.get(i - 1).placementOverlapMs();
            }
        }
        return Math.max(0L, total);
    }

    private static Listing listing(JsonNode node) {
        Listing listing = new Listing();
        listing.id = node.path("id").asText("");
        listing.revision = node.path("revision").asInt(0);
        listing.name = MixJson.limited(node.path("name").asText(""), 80);
        listing.author = MixJson.limited(node.path("author_name").asText(""), 40);
        listing.description = MixJson.limited(node.path("description").asText(""), 1000);
        listing.mode = node.path("mode").asText("");
        listing.trackCount = node.path("track_count").asInt(0);
        listing.durationMs = node.path("duration_ms").asLong(0);
        listing.plays = node.path("plays").asInt(0);
        listing.updatedAt = node.path("updated_at").asLong(0);
        listing.own = node.path("own").asBoolean(false);
        for (JsonNode genre : node.path("genres")) {
            listing.genres.add(MixJson.limited(genre.asText(""), 40));
        }
        for (JsonNode track : node.path("tracks")) {
            listing.tracks.add(new String[] {
                    track.path("sha256").asText(""), track.path("md5").asText(""),
                    MixJson.limited(track.path("title").asText(""), 200),
                    MixJson.limited(track.path("artist").asText(""), 200)});
        }
        return listing;
    }

    interface Call<T> {
        Result<T> call() throws Exception;
    }

    private static <T> void run(String name, Consumer<Result<T>> callback, Call<T> call) {
        Thread worker = new Thread(() -> {
            Result<T> result;
            try {
                result = call.call();
            } catch (Exception e) {
                logger.warn("NONSTOP MIX IR {} failed: {}", name, e.getMessage());
                result = new Result<>(null, text("IRに接続できませんでした", "Could not reach IR") + " (" + e.getMessage() + ")");
            }
            try {
                callback.accept(result);
            } catch (RuntimeException e) {
                logger.warn("NONSTOP MIX IR {} callback failed", name, e);
            }
        }, "nonstop-mix-ir-" + name);
        worker.setDaemon(true);
        worker.start();
    }

    static String errorText(String error) {
        return switch (error == null ? "" : error) {
            case "unsupported" -> text("このIRサーバーはNONSTOP MIXにまだ対応していません",
                    "This IR server does not support NONSTOP MIX yet");
            case "authentication_failed" -> text("BMS-IRのUser IDまたはパスワード設定を確認してください",
                    "Check the BMS-IR User ID and password settings");
            case "not_found" -> text("セットが見つかりません", "The set was not found");
            case "not_owner" -> text("自分が公開したセットだけ更新・削除できます", "Only your own sets can be changed");
            case "invalid_recipe" -> text("セットの内容が正しくありません", "The set is not valid");
            case "revision_mismatch" -> text("セットが更新されています。最新版を取得してください",
                    "The set was updated; download the latest revision");
            case "client_not_allowed" -> text("このクライアントのビルドではスコアを送信できません",
                    "This client build may not send scores");
            case "rate_limited" -> text("しばらく待ってから再試行してください", "Wait before trying again");
            default -> text("IRエラー: ", "IR error: ") + error;
        };
    }

    private static String text(String japanese, String english) {
        return bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n.text(japanese, english);
    }
}
