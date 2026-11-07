package bms.player.beatoraja.ir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.JsonValue;

import bms.model.BMSDecoder;
import bms.player.beatoraja.MainController.IRStatus;
import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.system.RobustFile;

/**
 * Disk-backed cache for successful IR rankings.
 *
 * Each profile keeps one SQLite file with one row per ranking (cache base
 * key). Loading or saving one chart reads or writes only that row. The former
 * single JSON file was parsed and rewritten in full for every chart, which
 * allocated hundreds of megabytes for a 50 MB cache on each save. The old
 * file is migrated once, in one transaction, and kept as {@code .migrated}.
 */
public final class PersistentRankingDataStore {
    private static final Logger logger = LoggerFactory.getLogger(PersistentRankingDataStore.class);
    private static final int FORMAT_VERSION = 2;
    private static final String LEGACY_FILE_NAME = "ir-ranking-cache.json";
    private static final String DATABASE_FILE_NAME = "ir-ranking-cache.sqlite";
    private static final String LEGACY_MIGRATED_KEY = "legacy_json_migrated";
    private static final long LEGACY_READ_LIMIT_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_SCORES = 100_000;
    static final int MAX_ENTRIES = 20_000;

    private final Json json;
    private final int maxEntries;
    private final Map<Path, Connection> connections = new HashMap<>();
    /** Profiles whose cache could not be opened; skipped until the next start. */
    private final java.util.Set<Path> unavailable = new java.util.HashSet<>();
    /** Monotonic order for rows saved within the same millisecond. */
    private long sequence = System.currentTimeMillis() * 1000L;

    public PersistentRankingDataStore() {
        this(MAX_ENTRIES);
    }

    PersistentRankingDataStore(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
		json = new Json();
        json.setIgnoreUnknownFields(true);
        json.setOutputType(JsonWriter.OutputType.json);
        json.setUsePrototypes(false);
        // Never instantiate a class named inside the cached JSON. The former
        // writer tagged list elements with their class; name the element type
        // here instead so those files still read as Entry objects.
        json.setTypeName(null);
        json.setElementType(CacheFile.class, "entries", Entry.class);
        // IRGaugeHistory is a record, so the default no-arg JSON reader cannot restore it.
        json.setSerializer(IRGaugeHistory.class, new Json.Serializer<IRGaugeHistory>() {
            public void write(Json writer, IRGaugeHistory history, Class knownType) {
                writer.writeObjectStart();
                writeGauge(writer, "easy", history.easy()); writeGauge(writer, "groove", history.groove());
                writeGauge(writer, "hard", history.hard()); writeGauge(writer, "exhard", history.exhard());
                writer.writeObjectEnd();
            }
            public IRGaugeHistory read(Json reader, JsonValue value, Class type) {
                return new IRGaugeHistory(readGauge(value, "easy"), readGauge(value, "groove"),
                        readGauge(value, "hard"), readGauge(value, "exhard"));
            }
        });
    }

    private static void writeGauge(Json writer, String name, java.util.List<Float> values) {
        writer.writeArrayStart(name);
        if (values != null) for (Float value : values) writer.writeValue(value, Float.class);
        writer.writeArrayEnd();
    }

    private static java.util.List<Float> readGauge(JsonValue history, String name) {
        java.util.ArrayList<Float> values = new java.util.ArrayList<>();
        JsonValue channel = history == null ? null : history.get(name);
        // The former default writer stored a list as {"class": ..., "items": [...]}.
        if (channel != null && channel.isObject() && channel.get("items") != null) channel = channel.get("items");
        if (channel == null || !(channel.isArray() || channel.isObject())) return values;
        for (JsonValue value : channel) {
            if (value.isObject() && value.has("value")) values.add(value.getFloat("value"));
            else if (value.isNumber()) values.add(value.asFloat());
        }
        return values;
    }

    public synchronized IRScoreData[] load(
            String playerPath, String playerId, IRStatus ir, IRRankingContext context, Object target) {
        String key = cacheKey(ir, context, target);
        if (key == null) return null;
        return loadRow(playerPath, playerId, key, key, false);
    }

    public synchronized IRScoreData[] load(String playerPath, String playerId, IRStatus ir,
            IRRankingContext context, String targetIdentity) {
        return load(playerPath, playerId, ir, context, (Object) targetIdentity);
    }

    public synchronized IRScoreData[] loadScoped(String playerPath, String playerId, IRStatus ir,
            IRRankingContext context, Object target, String scope) {
        String baseKey = cacheKey(ir, context, target);
        String key = cacheKey(ir, context, scopedTarget(target, scope));
        if (baseKey == null || key == null) return null;
        return loadRow(playerPath, playerId, baseKey, key, false);
    }

    /** Offline fallback only: never treat this result as a verified current profile. */
    public synchronized IRScoreData[] loadLastGood(String playerPath, String playerId, IRStatus ir,
            IRRankingContext context, Object target) {
        String baseKey = cacheKey(ir, context, target);
        if (baseKey == null) return null;
        return loadRow(playerPath, playerId, baseKey, null, true);
    }

    private static Object scopedTarget(Object target, String scope) {
        String identity = targetIdentity(target);
        return identity.isEmpty() || scope == null || scope.isEmpty() ? target : identity + "\nreceive:" + scope;
    }

    public synchronized boolean contains(String playerPath, String playerId, IRStatus ir,
            IRRankingContext context, String targetIdentity) {
        return load(playerPath, playerId, ir, context, targetIdentity) != null;
    }

    public synchronized void save(
            String playerPath, String playerId, IRStatus ir, IRRankingContext context,
            Object target, IRScoreData[] scores) {
        saveScoped(playerPath, playerId, ir, context, target, "", scores);
    }

    public synchronized void saveScoped(
            String playerPath, String playerId, IRStatus ir, IRRankingContext context,
            Object target, String scope, IRScoreData[] scores) {
        if (scores == null || scores.length > MAX_SCORES) return;
        for (IRScoreData score : scores) {
            if (score == null || score.clear == null || score.player == null
                    || score.clear.id < 0 || score.clear.id >= 11) return;
        }
        String baseKey = cacheKey(ir, context, target);
        String key = cacheKey(ir, context, scopedTarget(target, scope));
        if (key == null || baseKey == null) return;
        Entry entry = new Entry();
        entry.key = key;
        entry.baseKey = baseKey;
        entry.savedAt = System.currentTimeMillis();
        entry.scores = Arrays.stream(scores).map(ScoreEntry::new).toArray(ScoreEntry[]::new);
        try {
            Connection connection = connection(playerPath, playerId);
            if (connection == null) return;
            String payload = json.toJson(entry);
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT INTO ranking_entry(base_key, entry_key, saved_at, seq, payload) VALUES(?,?,?,?,?) "
                    + "ON CONFLICT(base_key) DO UPDATE SET entry_key=excluded.entry_key, "
                    + "saved_at=excluded.saved_at, seq=excluded.seq, payload=excluded.payload")) {
                upsert.setString(1, baseKey);
                upsert.setString(2, key);
                upsert.setLong(3, entry.savedAt);
                upsert.setLong(4, nextSequence());
                upsert.setString(5, payload);
                upsert.executeUpdate();
            }
            trimOldest(connection);
        } catch (SQLException | RuntimeException error) {
            logger.warn("IR ranking cache could not be saved: {}", error.getMessage());
        }
    }

    public synchronized void save(String playerPath, String playerId, IRStatus ir,
            IRRankingContext context, String targetIdentity, IRScoreData[] scores) {
        save(playerPath, playerId, ir, context, (Object) targetIdentity, scores);
    }

    /** Closes every profile database. The store reopens a database on next use. */
    public synchronized void close() {
        for (Connection connection : connections.values()) {
            try {
                connection.close();
            } catch (SQLException error) {
                logger.warn("IR ranking cache could not be closed: {}", error.getMessage());
            }
        }
        connections.clear();
        unavailable.clear();
    }

    private IRScoreData[] loadRow(String playerPath, String playerId, String baseKey, String key, boolean anyScope) {
        try {
            Connection connection = connection(playerPath, playerId);
            if (connection == null) return null;
            String sql = anyScope
                    ? "SELECT payload FROM ranking_entry WHERE base_key=?"
                    : "SELECT payload FROM ranking_entry WHERE base_key=? AND entry_key=?";
            try (PreparedStatement select = connection.prepareStatement(sql)) {
                select.setString(1, baseKey);
                if (!anyScope) select.setString(2, key);
                try (ResultSet row = select.executeQuery()) {
                    if (!row.next()) return null;
                    Entry entry = parseEntry(row.getString(1));
                    if (entry == null || !valid(entry.scores)) return null;
                    return Arrays.stream(entry.scores).map(ScoreEntry::toScore).toArray(IRScoreData[]::new);
                }
            }
        } catch (SQLException | RuntimeException error) {
            logger.warn("IR ranking cache could not be loaded; fetching the requested ranking again: {}", error.getMessage());
            return null;
        }
    }

    private Entry parseEntry(String payload) {
        if (payload == null) return null;
        try {
            return json.fromJson(Entry.class, payload);
        } catch (RuntimeException error) {
            logger.warn("Ignoring an unreadable IR ranking cache entry: {}", error.getMessage());
            return null;
        }
    }

    /** Returns null, without logging again, for a profile whose cache already failed to open. */
    private Connection connection(String playerPath, String playerId) throws SQLException {
        Path directory = Paths.get(playerPath, playerId).toAbsolutePath().normalize();
        Connection connection = connections.get(directory);
        if (connection != null && !connection.isClosed()) return connection;
        if (unavailable.contains(directory)) return null;
        try {
            connection = openProfile(directory);
        } catch (SQLException | RuntimeException error) {
            // Do not re-read a large former JSON cache on every chart after a failure.
            unavailable.add(directory);
            throw error;
        }
        connections.put(directory, connection);
        return connection;
    }

    private Connection openProfile(Path directory) throws SQLException {
        Connection connection;
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new SQLException("IR ranking cache directory is unavailable: " + error.getMessage(), error);
        }
        Path database = directory.resolve(DATABASE_FILE_NAME);
        try {
            connection = open(database);
        } catch (SQLException error) {
            // A damaged cache must not disable IR rankings; set it aside and start empty.
            logger.warn("IR ranking cache database is unreadable; starting a new one: {}", error.getMessage());
            setAside(database);
            connection = open(database);
        }
        try {
            migrateLegacyJson(connection, directory);
        } catch (SQLException | RuntimeException error) {
            connection.close();
            throw error;
        }
        return connection;
    }

    private static Connection open(Path database) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(2000);
        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + database);
        Connection connection = source.getConnection();
        try {
            initialize(connection);
            try (Statement statement = connection.createStatement();
                 ResultSet check = statement.executeQuery("PRAGMA quick_check")) {
                if (!check.next() || !"ok".equalsIgnoreCase(check.getString(1))) {
                    throw new SQLException("IR ranking cache failed its integrity check");
                }
            }
        } catch (SQLException | RuntimeException error) {
            connection.close();
            throw error;
        }
        return connection;
    }

    private static void setAside(Path database) {
        for (String suffix : new String[] { "", "-wal", "-shm" }) {
            Path file = database.resolveSibling(database.getFileName() + suffix);
            if (!Files.exists(file)) continue;
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException error) {
                logger.warn("Damaged IR ranking cache could not be renamed: {}", error.getMessage());
            }
        }
    }

    private static void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ranking_entry("
                    + "base_key TEXT PRIMARY KEY, entry_key TEXT NOT NULL, saved_at INTEGER NOT NULL, "
                    + "seq INTEGER NOT NULL, payload TEXT NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS ranking_entry_age ON ranking_entry(saved_at, seq)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS cache_meta(name TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
    }

    /**
     * Moves the former JSON cache into SQLite once. All entries and the
     * completion marker commit in one transaction; on any failure nothing is
     * committed and the JSON file stays in place for the next attempt.
     */
    private void migrateLegacyJson(Connection connection, Path directory) throws SQLException {
        if (metaValue(connection, LEGACY_MIGRATED_KEY) != null) return;
        Path legacy = directory.resolve(LEGACY_FILE_NAME);
        CacheFile file = readLegacy(legacy);
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int migrated = 0;
            if (file != null) {
                Map<String, Entry> newest = new HashMap<>();
                for (Entry entry : file.entries) {
                    if (entry == null || entry.key == null || !valid(entry.scores)) continue;
                    String baseKey = entry.baseKey != null ? entry.baseKey : entry.key;
                    Entry previous = newest.get(baseKey);
                    if (previous == null || entry.savedAt > previous.savedAt) newest.put(baseKey, entry);
                }
                java.util.List<Map.Entry<String, Entry>> ordered = new java.util.ArrayList<>(newest.entrySet());
                ordered.sort((left, right) -> Long.compare(left.getValue().savedAt, right.getValue().savedAt));
                if (ordered.size() > maxEntries) ordered = ordered.subList(ordered.size() - maxEntries, ordered.size());
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT OR REPLACE INTO ranking_entry(base_key, entry_key, saved_at, seq, payload) VALUES(?,?,?,?,?)")) {
                    for (Map.Entry<String, Entry> item : ordered) {
                        Entry entry = item.getValue();
                        entry.baseKey = item.getKey();
                        insert.setString(1, item.getKey());
                        insert.setString(2, entry.key);
                        insert.setLong(3, entry.savedAt);
                        insert.setLong(4, nextSequence());
                        insert.setString(5, json.toJson(entry));
                        insert.addBatch();
                        migrated++;
                    }
                    insert.executeBatch();
                }
            }
            try (PreparedStatement marker = connection.prepareStatement(
                    "INSERT OR REPLACE INTO cache_meta(name, value) VALUES(?, ?)")) {
                marker.setString(1, LEGACY_MIGRATED_KEY);
                marker.setString(2, Integer.toString(migrated));
                marker.executeUpdate();
            }
            connection.commit();
            if (file != null) {
                logger.info("Moved {} IR ranking cache entries from JSON to SQLite", migrated);
            }
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        retireLegacy(legacy);
    }

    private CacheFile readLegacy(Path legacy) {
        if (!Files.isRegularFile(legacy) && !Files.isRegularFile(legacy.resolveSibling(LEGACY_FILE_NAME + ".bak"))) {
            return null;
        }
        try {
            if (Files.isRegularFile(legacy) && Files.size(legacy) > LEGACY_READ_LIMIT_BYTES) {
                logger.warn("Former IR ranking cache exceeds the read limit; starting with an empty cache");
                return null;
            }
            return RobustFile.load(legacy, bytes -> {
                try {
                    CacheFile parsed = json.fromJson(CacheFile.class, new String(bytes, StandardCharsets.UTF_8));
                    if (parsed == null || parsed.version != FORMAT_VERSION || parsed.entries == null) {
                        throw new java.text.ParseException("unsupported or invalid ranking cache", 0);
                    }
                    return parsed;
                } catch (RuntimeException error) {
                    java.text.ParseException failure = new java.text.ParseException("invalid ranking cache: " + error.getMessage(), 0);
                    failure.initCause(error);
                    throw failure;
                }
            });
        } catch (IOException | RuntimeException error) {
            logger.warn("Former IR ranking cache could not be read; starting with an empty cache: {}", error.getMessage());
            return null;
        }
    }

    private static void retireLegacy(Path legacy) {
        for (Path file : new Path[] { legacy, legacy.resolveSibling(LEGACY_FILE_NAME + ".bak") }) {
            if (!Files.isRegularFile(file)) continue;
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".migrated"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException error) {
                logger.warn("Former IR ranking cache was migrated but could not be renamed: {}", error.getMessage());
            }
        }
    }

    private static String metaValue(Connection connection, String name) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT value FROM cache_meta WHERE name=?")) {
            select.setString(1, name);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        }
    }

    private void trimOldest(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM ranking_entry")) {
            if (!count.next() || count.getInt(1) <= maxEntries) return;
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM ranking_entry WHERE base_key IN (SELECT base_key FROM ranking_entry "
                + "ORDER BY saved_at DESC, seq DESC LIMIT -1 OFFSET ?)")) {
            delete.setInt(1, maxEntries);
            delete.executeUpdate();
        }
    }

    private long nextSequence() {
        sequence = Math.max(sequence + 1, System.currentTimeMillis() * 1000L);
        return sequence;
    }

    private static boolean valid(ScoreEntry[] scores) {
        if (scores == null) return false;
        if (scores.length > MAX_SCORES) return false;
        for (ScoreEntry score : scores) {
            if (score == null || score.clear == null || score.player == null
                    || score.clear.id < 0 || score.clear.id >= 11) return false;
        }
        return true;
    }

    private static String cacheKey(IRStatus status, IRRankingContext context, Object target) {
        if (status == null || status.config == null || status.player == null || status.player.id == null || target == null) return null;
        IRConfig config = status.config;
		String targetKey = targetIdentity(target);
		if (targetKey.isEmpty()) return null;
		String identity = String.join("\n", nullToEmpty(config.getIrname()),
				nullToEmpty(status.player.id), Integer.toString(context.lnmode()),
				Boolean.toString(context.forceLn()), targetKey);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return BMSDecoder.convertHexString(digest);
        } catch (NoSuchAlgorithmException error) {
            return null;
        }
    }

    private static String targetIdentity(Object target) {
        if (target instanceof String identity) return identity;
        if (target instanceof SongData song) {
            String sha = song.getSha256();
            if (sha == null || sha.length() != 64) return "";
            return "song:" + sha.toLowerCase();
        }
        if (target instanceof CourseData course) {
            SongData[] songs = course.getSong();
            if (songs == null || songs.length == 0) return "";
            StringBuilder identity = new StringBuilder("course:v2:");
            for (SongData song : songs) {
                if (song == null || song.getSha256() == null || song.getSha256().length() != 64) return "";
                identity.append(song.getSha256().toLowerCase()).append(':');
            }
            if (course.getConstraint() != null) {
                Arrays.stream(course.getConstraint()).forEach(constraint -> identity.append(constraint.name).append(':'));
            }
            return identity.append("name:").append(nullToEmpty(course.getName())).toString();
        }
        return "";
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    /** Former single-file JSON format, read only for the one-time migration. */
    public static class CacheFile {
        public int version = FORMAT_VERSION;
        public java.util.ArrayList<Entry> entries = new java.util.ArrayList<>();
    }

    public static class Entry {
        public String key;
        public String baseKey;
        public long savedAt;
        public ScoreEntry[] scores;
    }

    public static class ScoreEntry {
        public String sha256;
        public int lntype;
        public int bmsirLongNotePolicy;
        public String player;
        public bms.player.beatoraja.ClearType clear;
        public long date;
        public int epg, lpg, egr, lgr, egd, lgd, ebd, lbd, epr, lpr, ems, lms;
        public long avgjudge;
        public int maxcombo, notes, passnotes, minbp, option;
        public long seed;
        public int assist, gauge;
        public bms.player.beatoraja.input.BMSPlayerInputDevice.Type deviceType;
        public bms.player.beatoraja.play.JudgeAlgorithm judgeAlgorithm;
        public bms.player.beatoraja.play.BMSPlayerRule rule;
        public String skin;
        public IRGaugeHistory gaugeHistory;

        public ScoreEntry() { }

        ScoreEntry(IRScoreData score) {
            sha256 = score.sha256; lntype = score.lntype; bmsirLongNotePolicy = score.bmsirLongNotePolicy;
            player = score.player; clear = score.clear; date = score.date;
            epg = score.epg; lpg = score.lpg; egr = score.egr; lgr = score.lgr;
            egd = score.egd; lgd = score.lgd; ebd = score.ebd; lbd = score.lbd;
            epr = score.epr; lpr = score.lpr; ems = score.ems; lms = score.lms;
            avgjudge = score.avgjudge; maxcombo = score.maxcombo; notes = score.notes;
            passnotes = score.passnotes; minbp = score.minbp; option = score.option;
            seed = score.seed; assist = score.assist; gauge = score.gauge;
            deviceType = score.deviceType; judgeAlgorithm = score.judgeAlgorithm;
            rule = score.rule; skin = score.skin; gaugeHistory = score.gaugeHistory;
        }

        IRScoreData toScore() {
            IRScoreData score = new IRScoreData(sha256, lntype, bmsirLongNotePolicy, player, clear, date,
                    epg, lpg, egr, lgr, egd, lgd, ebd, lbd, epr, lpr, ems, lms, avgjudge,
                    maxcombo, notes, passnotes, minbp, option, seed, assist, gauge,
                    deviceType, judgeAlgorithm, rule, skin);
            score.gaugeHistory = gaugeHistory;
            return score;
        }
    }
}
