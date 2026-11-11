package bms.player.beatoraja.arena.bmsir;

import bms.model.BMSModel;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.Version;
import bms.player.beatoraja.ir.IRScoreData;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.song.SongDatabaseAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional external ranking server for NANTOKA MANIA plays only. A player opts in
 * by placing {@code bmsir_nantoka_ir.json} ({"server": "https://.../", "token": "..."})
 * in their player folder; without it nothing is sent anywhere. {@code server} is the
 * API base URL, and each request appends its endpoint name (me, score, course, ...).
 */
public final class BMSIRNantokaIr {
    private static final Logger logger = LoggerFactory.getLogger(BMSIRNantokaIr.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String CONFIG_FILE = "bmsir_nantoka_ir.json";
    static final String IMPORT_MARKER = "bmsir_nantoka_ir.imported";
    static final String TITLES_MARKER = "bmsir_nantoka_ir.titles";
    private static final int IMPORT_BATCH = 40;
    private static final AtomicBoolean IMPORT_RUNNING = new AtomicBoolean();
    private static volatile Loaded loaded;

    record Config(URI server, String token) {
    }

    private record Loaded(Path file, long modified, Config config, int[] playerId) {
    }

    private BMSIRNantokaIr() {
    }

    /** NANTOKA MANIA alone, judged by its own rules on an unmodified chart. */
    public static boolean accepts(MainController main, BMSIRManiacSettings settings) {
        return settings != null && settings.isNantokaManiaOnly() && config(main) != null;
    }

    static Config config(MainController main) {
        Path folder = playerFolder(main);
        if (folder == null) return null;
        Path file = folder.resolve(CONFIG_FILE);
        try {
            long modified = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
            Loaded current = loaded;
            if (current != null && current.file().equals(file) && current.modified() == modified) return current.config();
            Config config = modified < 0 ? null : parse(Files.readString(file, StandardCharsets.UTF_8));
            loaded = new Loaded(file, modified, config, new int[] {0});
            return config;
        } catch (Exception error) {
            logger.warn("NANTOKA IR configuration is unreadable: {}", error.getMessage());
            loaded = null;
            return null;
        }
    }

    static Config parse(String text) throws IOException {
        // Older Windows Notepad saves UTF-8 with a byte order mark, which JSON parsers reject.
        JsonNode node = JSON.readTree(text.startsWith("\uFEFF") ? text.substring(1) : text);
        URI server = URI.create(node.path("server").asText("").trim());
        String token = node.path("token").asText("").trim();
        // Plain http only for a server running on this machine during development.
        boolean loopback = "http".equalsIgnoreCase(server.getScheme())
                && ("127.0.0.1".equals(server.getHost()) || "localhost".equalsIgnoreCase(server.getHost()));
        if (!("https".equalsIgnoreCase(server.getScheme()) || loopback) || server.getHost() == null
                || server.getUserInfo() != null || !token.matches("[A-Za-z0-9_-]{32,128}")) {
            throw new IOException("server must be https and token must be the issued value");
        }
        // Endpoints resolve relative to the base, so it must name a directory.
        if (!server.getPath().endsWith("/")) server = URI.create(server + "/");
        return new Config(server, token);
    }

    private static Path playerFolder(MainController main) {
        if (main == null || main.getPlayerConfig() == null || main.getPlayerPath() == null) return null;
        return Path.of(main.getPlayerPath(), main.getPlayerConfig().getId());
    }

    /** Server-side player id, resolved once per configuration so own rows can be recognised. */
    static int playerId(MainController main) throws IOException {
        Config config = config(main);
        Loaded current = loaded;
        if (config == null || current == null) throw new IOException("NANTOKA IR is not configured");
        synchronized (current.playerId()) {
            if (current.playerId()[0] <= 0) {
                current.playerId()[0] = post(main, "me", JSON.createObjectNode()).path("player_id").asInt();
            }
            return current.playerId()[0];
        }
    }

    static ObjectNode chartPayload(String sha256, int judgeVersion, int lnmode) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("sha256", sha256.toLowerCase(Locale.ROOT));
        payload.put("judge_version", judgeVersion);
        payload.put("lnmode", lnmode);
        return payload;
    }

    /** Display name for the server's own views; the score is valid without it. */
    static void putTitle(ObjectNode payload, SongData song) {
        if (song == null || song.getFullTitle() == null || song.getFullTitle().isBlank()) return;
        payload.put("title", song.getFullTitle());
        payload.put("artist", song.getArtist() == null ? "" : song.getArtist());
    }

    static void putScore(ObjectNode payload, ScoreData score) {
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
        if (score.getDate() > 0) payload.put("played_at", score.getDate());
        payload.put("client_version", Version.getVersion());
    }

    /** Same value for a live submission and a later import of the stored best, so the server dedupes it. */
    static String replayHash(String storageHash, ScoreData score) {
        return sha256(storageHash + ':' + score.getDate() + ':' + score.getExscore() + ':'
                + score.getClear() + ':' + score.getMinbp());
    }

    static ObjectNode post(MainController main, String endpoint, ObjectNode payload) throws IOException {
        Config config = config(main);
        if (config == null) throw new IOException("NANTOKA IR is not configured");
        return post(config, endpoint, payload);
    }

    static ObjectNode post(Config config, String endpoint, ObjectNode payload) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) config.server().resolve(endpoint).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + config.token());
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        byte[] body = JSON.writeValueAsBytes(payload);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }
        int status = connection.getResponseCode();
        JsonNode parsed;
        try (InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            parsed = input == null ? JSON.createObjectNode() : JSON.readTree(input);
        } catch (IOException error) {
            parsed = JSON.createObjectNode();
        } finally {
            connection.disconnect();
        }
        if (status < 200 || status >= 300 || !parsed.path("ok").asBoolean(false)) {
            throw new IOException("HTTP " + status + ": " + parsed.path("error").asText(status == 404 ? "not_found_or_token" : "request_failed"));
        }
        return (ObjectNode) parsed;
    }

    /** Dan result under NANTOKA MANIA. Pass/fail lives only on this IR; local course records stay untouched. */
    public static IRScoreData[] submitCourse(
            MainController main,
            CourseData course,
            BMSModel[] models,
            CourseData.CourseDataConstraint[] constraints,
            BMSIRManiacSettings settings,
            int lnmode,
            ScoreData score
    ) throws IOException {
        ObjectNode payload = JSON.createObjectNode();
        String[] stages = Arrays.stream(models).map(model -> model.getSHA256().toLowerCase(Locale.ROOT)).toArray(String[]::new);
        payload.put("course_hash", courseHash(stages, constraints));
        payload.put("title", course.getName() == null ? "" : course.getName());
        ArrayNode stageNodes = payload.putArray("stages");
        for (String stage : stages) stageNodes.add(stage);
        payload.put("judge_version", settings.getNantokaJudgeVersion());
        payload.put("lnmode", lnmode);
        putScore(payload, score);
        payload.put("ghost", "");
        payload.put("replay_hash", sha256("course:" + String.join(",", stages) + ':' + score.getDate() + ':'
                + score.getExscore() + ':' + score.getClear() + ':' + score.getMinbp()));
        return scores(post(main, "course", payload), playerId(main), "");
    }

    static String courseHash(String[] stages, CourseData.CourseDataConstraint[] constraints) {
        String rules = constraints == null ? "" : String.join(",", Arrays.stream(constraints)
                .filter(value -> value != null).map(value -> value.name).sorted().toList());
        return sha256(String.join(",", stages) + '|' + rules);
    }

    static IRScoreData[] scores(JsonNode response, int ownPlayerId, String chartId) {
        JsonNode items = response.path("items");
        if (!items.isArray()) return new IRScoreData[0];
        IRScoreData[] result = new IRScoreData[items.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = new IRScoreData(BMSIRManiacApiClient.score(items.get(index), chartId, ownPlayerId));
        }
        return result;
    }

    /** Uploads local NANTOKA MANIA bests newer than the last import, in small batches. */
    public static void importLocalScoresAsync(MainController main) {
        Path folder = playerFolder(main);
        if (folder == null || config(main) == null || !IMPORT_RUNNING.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                Path marker = folder.resolve(IMPORT_MARKER);
                long since = Files.exists(marker) ? Long.parseLong(Files.readString(marker).trim()) : 0L;
                Path database = folder.resolve("bmsir_maniac.db");
                List<ObjectNode> items = localBests(database, since);
                addTitles(main.getSongDatabase(), items);
                long newest = since;
                int imported = 0;
                for (int start = 0; start < items.size(); start += IMPORT_BATCH) {
                    ObjectNode request = JSON.createObjectNode();
                    ArrayNode batch = request.putArray("items");
                    for (ObjectNode item : items.subList(start, Math.min(items.size(), start + IMPORT_BATCH))) {
                        batch.add(item);
                        newest = Math.max(newest, item.path("played_at").asLong());
                    }
                    imported += post(main, "import", request).path("imported").asInt();
                    Files.writeString(marker, Long.toString(newest));
                }
                logger.info("NANTOKA IR import completed: {} of {} local bests", imported, items.size());
                // Older bests get their names once the song database knows them; the marker
                // remembers how many were sent so unchanged libraries cost no request.
                Path titlesMarker = folder.resolve(TITLES_MARKER);
                List<ObjectNode> all = localBests(database, 0L);
                addTitles(main.getSongDatabase(), all);
                List<ObjectNode> named = all.stream().filter(item -> item.has("title")).toList();
                String sent = Files.exists(titlesMarker) ? Files.readString(titlesMarker).trim() : "";
                if (!named.isEmpty() && !sent.equals(Integer.toString(named.size()))) {
                    for (int start = 0; start < named.size(); start += 400) {
                        ObjectNode request = JSON.createObjectNode();
                        ArrayNode batch = request.putArray("items");
                        for (ObjectNode item : named.subList(start, Math.min(named.size(), start + 400))) {
                            batch.addObject().put("sha256", item.path("sha256").asText())
                                    .put("title", item.path("title").asText())
                                    .put("artist", item.path("artist").asText(""));
                        }
                        post(main, "charts", request);
                    }
                    Files.writeString(titlesMarker, Integer.toString(named.size()));
                    logger.info("NANTOKA IR titles sent: {}", named.size());
                }
            } catch (Exception error) {
                logger.warn("NANTOKA IR import failed: {}", error.getMessage());
            } finally {
                IMPORT_RUNNING.set(false);
            }
        }, "bmsir-nantoka-import");
        worker.setDaemon(true);
        worker.start();
    }

    static void addTitles(SongDatabaseAccessor songs, List<ObjectNode> items) {
        if (songs == null || items.isEmpty()) return;
        String[] hashes = items.stream().map(item -> item.path("sha256").asText()).distinct().toArray(String[]::new);
        java.util.Map<String, SongData> byHash = new java.util.HashMap<>();
        for (SongData song : songs.getSongDatas(hashes)) {
            if (song != null && song.getSha256() != null) byHash.putIfAbsent(song.getSha256().toLowerCase(Locale.ROOT), song);
        }
        for (ObjectNode item : items) putTitle(item, byHash.get(item.path("sha256").asText()));
    }

    static List<ObjectNode> localBests(Path database, long since) throws Exception {
        List<ObjectNode> items = new ArrayList<>();
        if (!Files.exists(database)) return items;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT s.*, m.base_sha256, m.options FROM score s
                     JOIN maniac_score_meta m ON m.storage_hash = s.sha256
                     WHERE s.date > ? ORDER BY s.date""")) {
            statement.setLong(1, since);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    BMSIRManiacSettings settings = BMSIRManiacSettings.fromCanonicalOptions(row.getString("options"));
                    if (settings == null || !settings.isNantokaManiaOnly() || row.getInt("notes") <= 0) continue;
                    ScoreData score = new ScoreData();
                    score.setNotes(row.getInt("notes"));
                    score.setClear(row.getInt("clear"));
                    score.setEpg(row.getInt("epg"));
                    score.setLpg(row.getInt("lpg"));
                    score.setEgr(row.getInt("egr"));
                    score.setLgr(row.getInt("lgr"));
                    score.setEgd(row.getInt("egd"));
                    score.setLgd(row.getInt("lgd"));
                    score.setEbd(row.getInt("ebd"));
                    score.setLbd(row.getInt("lbd"));
                    score.setEpr(row.getInt("epr"));
                    score.setLpr(row.getInt("lpr"));
                    score.setEms(row.getInt("ems"));
                    score.setLms(row.getInt("lms"));
                    score.setMinbp(row.getInt("minbp"));
                    score.setCombo(row.getInt("combo"));
                    score.setOption(row.getInt("option"));
                    score.setSeed(row.getLong("seed"));
                    score.setGhost(row.getString("ghost"));
                    score.setDate(row.getLong("date"));
                    ObjectNode item = chartPayload(row.getString("base_sha256"), settings.getNantokaJudgeVersion(), row.getInt("mode"));
                    putScore(item, score);
                    item.put("replay_hash", replayHash(row.getString("sha256"), score));
                    items.add(item);
                }
            }
        }
        return items;
    }

    static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
