package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.Version;
import bms.player.beatoraja.ir.IRScoreData;
import bms.player.beatoraja.ir.LeaderboardEntry;
import bms.player.beatoraja.ir.RankingData;
import bms.player.beatoraja.song.SongData;
import com.badlogic.gdx.Gdx;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dedicated transport for isolated MANIAC and Double Battle rankings. */
public final class BMSIRManiacApiClient {
    private static final Logger logger = LoggerFactory.getLogger(BMSIRManiacApiClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, RankingData> CACHE = new ConcurrentHashMap<>();
    private static final String SCORE_PATH = "/api/bmsir-arena/v1/maniac/score";
    private static final String RANKING_PATH = "/api/bmsir-arena/v1/maniac/ranking";
    private static final String GHOST_PATH = "/api/bmsir-arena/v1/maniac/ghost";
    private static final String SYNC_PATH = "/api/bmsir-arena/v1/maniac/sync";
    private static final AtomicBoolean SYNC_RUNNING = new AtomicBoolean();

    private BMSIRManiacApiClient() {
    }

    public static BMSIRManiacSettings effectiveSettings(MainController main, SongData song) {
        if (main == null || song == null || BMSIRArenaClient.isSelectionBlocked()
                || BMSIRArenaClient.isArenaPlayActive()) {
            return null;
        }
        Mode mode = Arrays.stream(Mode.values())
                .filter(value -> value.id == song.getMode())
                .findFirst()
                .orElse(null);
        return BMSIRManiacPlayContext.effectiveSettings(
                main.getPlayerConfig().getBmsirManiacSettings(),
                mode
        );
    }

    public static boolean hasAppliedSettings(MainController main, SongData song) {
        return effectiveSettings(main, song) != null;
    }

    public static boolean hasOnlineRanking(MainController main, SongData song) {
        BMSIRManiacSettings settings = effectiveSettings(main, song);
        return settings != null && onlineRanking(settings);
    }

    public static boolean canSubmit(BMSIRManiacSettings settings) {
        return settings != null && onlineRanking(settings);
    }

    public static boolean shouldSubmit(
            MainController main,
            ScoreData score,
            ScoreData previous,
            boolean completed
    ) {
        IRConfig config = bmsirConfig(main);
        if (config == null || score == null) return false;
        return switch (config.getIrsend()) {
            case IRConfig.IR_SEND_COMPLETE_SONG -> completed;
            case IRConfig.IR_SEND_UPDATE_SCORE -> previous == null
                    || score.getExscore() > previous.getExscore()
                    || score.getClear() > previous.getClear()
                    || score.getCombo() > previous.getCombo()
                    || score.getMinbp() < previous.getMinbp();
            default -> true;
        };
    }

    public static RankingData getCachedRanking(MainController main, SongData song) {
        Identity identity = identity(main, song);
        return identity == null ? null : CACHE.get(identity.cacheKey());
    }

    public static RankingData getOrCreateRanking(MainController main, SongData song) {
        Identity identity = identity(main, song);
        return identity == null ? null : CACHE.computeIfAbsent(
                identity.cacheKey(),
                ignored -> new RankingData()
        );
    }

    public static RankingData loadRanking(MainController main, SongData song) {
        Identity identity = identity(main, song);
        if (identity == null) return null;
        RankingData ranking = CACHE.computeIfAbsent(identity.cacheKey(), ignored -> new RankingData());
        ranking.beginAccess();
        Thread worker = new Thread(() -> {
            try {
                Auth auth = auth(main);
                ObjectNode response = post(main, RANKING_PATH, identityPayload(identity, auth));
                ranking.updateScore(scores(response, auth.playerId()), localScore(main, identity));
            } catch (Exception error) {
                ranking.failAccess();
                logger.warn("MANIAC ranking request failed: {}", error.getMessage());
            }
        }, "bmsir-maniac-ranking");
        worker.setDaemon(true);
        worker.start();
        return ranking;
    }

    public static LeaderboardEntry[] loadLeaderboard(MainController main, SongData song) {
        Identity identity = identity(main, song);
        if (identity == null) return new LeaderboardEntry[0];
        try {
            Auth auth = auth(main);
            ObjectNode response = post(main, RANKING_PATH, identityPayload(identity, auth));
            List<LeaderboardEntry> entries = new ArrayList<>();
            JsonNode items = response.path("items");
            boolean hasOwnOnlineScore = false;
            if (items.isArray()) {
                for (JsonNode item : items) {
                    int playerId = item.path("player_id").asInt();
                    ScoreData score = score(item, response, auth.playerId());
                    hasOwnOnlineScore |= playerId == auth.playerId();
                    entries.add(LeaderboardEntry.newEntryBMSIRManiac(
                            new IRScoreData(score),
                            playerId
                    ));
                }
            }
            ScoreData local = localScore(main, identity);
            if (local != null) {
                LeaderboardEntry ownOnline = entries.stream()
                        .filter(entry -> entry.getBMSIRPlayerId() == auth.playerId())
                        .findFirst()
                        .orElse(null);
                if (!hasOwnOnlineScore || ownOnline == null
                        || local.getExscore() > ownOnline.getIrScore().getExscore()) {
                    entries.remove(ownOnline);
                    local.setPlayer("");
                    entries.add(LeaderboardEntry.newEntryBMSIRManiac(
                            new IRScoreData(local),
                            0
                    ));
                }
            }
            entries.sort(Comparator.comparingInt(
                    (LeaderboardEntry entry) -> entry.getIrScore().getExscore()
            ).reversed());
            return entries.toArray(LeaderboardEntry[]::new);
        } catch (Exception error) {
            logger.warn("MANIAC leaderboard request failed: {}", error.getMessage());
            return new LeaderboardEntry[0];
        }
    }

    public static GhostScore loadGhost(
            MainController main,
            SongData song,
            int targetPlayerId
    ) {
        Identity identity = identity(main, song);
        if (identity == null || targetPlayerId <= 0) return null;
        try {
            Auth auth = auth(main);
            ObjectNode payload = identityPayload(identity, auth);
            payload.put("target_player_id", targetPlayerId);
            ObjectNode response = post(main, GHOST_PATH, payload);
            if (response.path("algorithm_version").asInt()
                    != BMSIRManiacSettings.ALGORITHM_VERSION) {
                throw new IOException("algorithm_mismatch");
            }
            return new GhostScore(
                    response.path("name").asText(""),
                    response.path("ghost").asText(""),
                    response.path("placement_hash").asText(""),
                    response.path("random_seed").asLong(-1L),
                    identity.settings()
            );
        } catch (Exception error) {
            logger.warn("MANIAC ghost request failed: {}", error.getMessage());
            return null;
        }
    }

    public static void syncOwnScoresAsync(MainController main) {
        if (main == null || !SYNC_RUNNING.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                Auth auth = auth(main);
                ObjectNode request = JSON.createObjectNode();
                request.put("player_id", auth.playerId());
                request.put("passmd5", auth.passmd5());
                ObjectNode response = post(main, SYNC_PATH, request);
                JsonNode items = response.path("items");
                int imported = 0;
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String base = item.path("base_sha256").asText("");
                        BMSIRManiacSettings settings = BMSIRManiacSettings.fromCanonicalOptions(
                                item.path("canonical_options").asText("")
                        );
                        if (settings == null || base.isBlank()) continue;
                        ScoreData score = score(
                                item,
                                item.path("virtual_chart_id").asText(""),
                                auth.playerId()
                        );
                        main.getPlayDataAccessor().syncManiacScoreData(
                                settings.storageChartId(base),
                                base,
                                item.path("virtual_chart_id").asText(null),
                                settings,
                                item.path("generation_seed").asText(""),
                                item.path("placement_hash").asText(""),
                                score
                        );
                        imported++;
                    }
                }
                logger.info("MANIAC score sync completed: {} records", imported);
                if (imported > 0 && Gdx.app != null) {
                    Gdx.app.postRunnable(BMSIRArenaClient::refreshManiacScoreDisplay);
                }
            } catch (Exception error) {
                logger.warn("MANIAC score sync failed: {}", error.getMessage());
            } finally {
                SYNC_RUNNING.set(false);
            }
        }, "bmsir-maniac-sync");
        worker.setDaemon(true);
        worker.start();
    }

    public static boolean submitScore(
            MainController main,
            BMSIRManiacPlayContext context,
            ScoreData score,
            RankingData ranking
    ) {
        if (main == null || context == null || score == null
                || !onlineRanking(context.settings())) {
            return false;
        }
        try {
            Auth auth = auth(main);
            Identity identity = new Identity(
                    context.baseHash(),
                    context.settings(),
                    context.virtualHash()
            );
            ObjectNode payload = identityPayload(identity, auth);
            payload.put("placement_hash", context.placementHash());
            payload.put("generation_seed", Long.toUnsignedString(context.generationSeed()));
            payload.put("totalnotes", score.getNotes());
            payload.put("clear", score.getClear());
            payload.put("exscore", score.getExscore());
            payload.put("pg", score.getEpg() + score.getLpg());
            payload.put("gr", score.getEgr() + score.getLgr());
            payload.put("gd", score.getEgd() + score.getLgd());
            payload.put("bd", score.getEbd() + score.getLbd());
            payload.put("pr", score.getEpr() + score.getLpr() + score.getEms() + score.getLms());
            payload.put("minbp", score.getMinbp());
            payload.put("maxcombo", score.getCombo());
            payload.put("opt_this", score.getOption());
            payload.put("random_seed", score.getSeed());
            payload.put("ghost", score.getGhost() == null ? "" : score.getGhost());
            payload.put("replay_hash", replayHash(identity, score, context.placementHash()));
            payload.put("client_version", Version.getVersion());
            payload.put("build_hash", Version.getGitCommitHash());
            ObjectNode response = post(main, SCORE_PATH, payload);
            IRScoreData[] values = scores(response, auth.playerId());
            RankingData cached = CACHE.computeIfAbsent(identity.cacheKey(), ignored -> new RankingData());
            cached.updateScore(values, score);
            if (ranking != null && ranking != cached) ranking.updateScore(values, score);
            return true;
        } catch (Exception error) {
            if (ranking != null) ranking.failAccess();
            logger.warn("MANIAC score submission failed: {}", error.getMessage());
            return false;
        }
    }

    private static ScoreData localScore(MainController main, Identity identity) {
        return main.getPlayDataAccessor().readManiacScoreData(
                identity.settings().storageChartId(identity.baseSha256()),
                main.getPlayerConfig().getLnmode()
        );
    }

    private static Identity identity(MainController main, SongData song) {
        BMSIRManiacSettings settings = effectiveSettings(main, song);
        if (settings == null || !onlineRanking(settings)) return null;
        return new Identity(song.getSha256(), settings, settings.virtualChartId(song.getSha256()));
    }

    private static boolean onlineRanking(BMSIRManiacSettings settings) {
        BMSIRManiacSettings.RankingClass type = settings.rankingClass();
        return type != BMSIRManiacSettings.RankingClass.NORMAL
                && type != BMSIRManiacSettings.RankingClass.LOCAL_ONLY;
    }

    private static ObjectNode identityPayload(Identity identity, Auth auth) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("player_id", auth.playerId());
        payload.put("passmd5", auth.passmd5());
        payload.put("base_sha256", identity.baseSha256());
        payload.put("ranking_class", identity.settings().rankingClass().name());
        payload.put("canonical_options", identity.settings().canonicalOptions());
        payload.put("algorithm_version", BMSIRManiacSettings.ALGORITHM_VERSION);
        if (identity.virtualChartId() == null) payload.putNull("virtual_chart_id");
        else payload.put("virtual_chart_id", identity.virtualChartId());
        return payload;
    }

    private static ObjectNode post(MainController main, String path, ObjectNode payload) throws IOException {
        URL url = endpoint(main, path).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(8_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "BMS-IR-Arena-oraja/" + Version.getVersion());
        byte[] body = JSON.writeValueAsBytes(payload);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        JsonNode parsed;
        try (InputStream input = stream) {
            parsed = input == null ? JSON.createObjectNode() : JSON.readTree(input);
        } finally {
            connection.disconnect();
        }
        if (status < 200 || status >= 300 || !parsed.path("ok").asBoolean(false)) {
            throw new IOException("HTTP " + status + ": " + parsed.path("error").asText("request_failed"));
        }
        return (ObjectNode) parsed;
    }

    private static URI endpoint(MainController main, String path) {
        URI arena = URI.create(main.getPlayerConfig().getBmsirArenaServer());
        String scheme = switch (arena.getScheme().toLowerCase(Locale.ROOT)) {
            case "ws" -> "http";
            case "wss" -> "https";
            default -> arena.getScheme();
        };
        return URI.create(scheme + "://" + arena.getAuthority() + path);
    }

    private static Auth auth(MainController main) {
        IRConfig selected = bmsirConfig(main);
        if (selected == null) throw new IllegalStateException("BMS-IR configuration is missing");
        int playerId = Integer.parseInt(selected.getUserid().trim());
        return new Auth(playerId, md5(selected.getPassword()));
    }

    private static IRConfig bmsirConfig(MainController main) {
        if (main == null || main.getPlayerConfig() == null) return null;
        IRConfig[] configurations = main.getPlayerConfig().getIrconfig();
        if (configurations == null) return null;
        for (IRConfig config : configurations) {
            if (config != null && config.getIrname() != null
                    && config.getIrname().toLowerCase(Locale.ROOT).contains("bms")) {
                return config;
            }
        }
        return null;
    }

    private static IRScoreData[] scores(JsonNode response, int ownPlayerId) {
        JsonNode items = response.path("items");
        if (!items.isArray()) return new IRScoreData[0];
        IRScoreData[] result = new IRScoreData[items.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = new IRScoreData(score(items.get(index), response, ownPlayerId));
        }
        return result;
    }

    private static ScoreData score(JsonNode item, JsonNode response, int ownPlayerId) {
		return score(item, response.path("virtual_chart_id").asText(""), ownPlayerId);
	}

    private static ScoreData score(JsonNode item, String chartId, int ownPlayerId) {
        ScoreData score = new ScoreData();
        score.setSha256(chartId == null ? "" : chartId);
        score.setPlayer(item.path("player_id").asInt() == ownPlayerId
                ? ""
                : item.path("name").asText(""));
        score.setClear(item.path("clear").asInt());
        int exscore = item.path("exscore").asInt();
        score.setEpg(item.has("pg") ? item.path("pg").asInt() : exscore / 2);
        score.setEgr(item.has("gr") ? item.path("gr").asInt() : exscore % 2);
        score.setEgd(item.path("gd").asInt());
        score.setEbd(item.path("bd").asInt());
        score.setEpr(item.path("pr").asInt());
        score.setCombo(item.path("maxcombo").asInt());
        score.setNotes(item.path("totalnotes").asInt());
        score.setPassnotes(score.getNotes());
        score.setMinbp(item.path("minbp").asInt());
        score.setOption(item.path("opt_this").asInt());
        score.setSeed(item.path("random_seed").asLong(-1L));
        score.setGhost(item.path("ghost").asText(""));
        score.setDate(parseDate(item.path("achieved_at").asText("")));
        return score;
    }

    private static long parseDate(String value) {
        try {
            return Instant.parse(value).getEpochSecond();
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }

    private static String replayHash(Identity identity, ScoreData score, String placementHash) {
        String value = identity.cacheKey() + ':' + placementHash + ':' + score.getDate() + ':'
                + score.getExscore() + ':' + score.getClear() + ':' + score.getMinbp() + ':'
                + score.getCombo() + ':' + score.getOption() + ':' + score.getSeed();
        return sha256(value);
    }

    private static String md5(String value) {
        return digest("MD5", value == null ? "" : value);
    }

    private static String sha256(String value) {
        return digest("SHA-256", value);
    }

    private static String digest(String algorithm, String value) {
        try {
            byte[] bytes = MessageDigest.getInstance(algorithm)
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException(algorithm + " is unavailable", error);
        }
    }

    private record Auth(int playerId, String passmd5) {
    }

    public record GhostScore(
            String playerName,
            String ghost,
            String placementHash,
            long randomSeed,
            BMSIRManiacSettings settings
    ) {
    }

    private record Identity(
            String baseSha256,
            BMSIRManiacSettings settings,
            String virtualChartId
    ) {
        private String cacheKey() {
            String ranking = virtualChartId == null
                    ? "bmsir-maniac:v" + BMSIRManiacSettings.ALGORITHM_VERSION
                    + ':' + baseSha256 + ":standard"
                    : virtualChartId;
            return ranking;
        }
    }
}
