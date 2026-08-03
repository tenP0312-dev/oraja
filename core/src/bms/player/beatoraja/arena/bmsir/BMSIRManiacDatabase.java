package bms.player.beatoraja.arena.bmsir;

import bms.model.BMSModel;
import bms.player.beatoraja.ScoreData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Additional structured metadata stored beside the compatible score tables. */
public final class BMSIRManiacDatabase {
    private static final Logger logger = LoggerFactory.getLogger(BMSIRManiacDatabase.class);
    private final String url;

    public BMSIRManiacDatabase(String path) {
        url = "jdbc:sqlite:" + path;
        createTables();
    }

    private void createTables() {
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS maniac_score_meta(
                        storage_hash TEXT PRIMARY KEY,
                        base_sha256 TEXT NOT NULL,
                        virtual_chart_id TEXT,
                        ranking_class TEXT NOT NULL,
                        options TEXT NOT NULL,
                        generation_seed TEXT NOT NULL,
                        algorithm_version INTEGER NOT NULL,
                        placement_hash TEXT NOT NULL,
                        best_ex INTEGER NOT NULL DEFAULT 0,
                        source TEXT NOT NULL DEFAULT 'local',
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS maniac_play_history(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        storage_hash TEXT NOT NULL,
                        base_sha256 TEXT NOT NULL,
                        virtual_chart_id TEXT,
                        ranking_class TEXT NOT NULL,
                        options TEXT NOT NULL,
                        generation_seed TEXT NOT NULL,
                        algorithm_version INTEGER NOT NULL,
                        placement_hash TEXT NOT NULL,
                        exscore INTEGER NOT NULL,
                        clear INTEGER NOT NULL,
                        minbp INTEGER NOT NULL,
                        play_option INTEGER NOT NULL,
                        random_seed INTEGER NOT NULL,
                        source TEXT NOT NULL DEFAULT 'local',
                        played_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_maniac_history_chart_time "
                    + "ON maniac_play_history(storage_hash, played_at DESC)");
        } catch (SQLException error) {
            logger.error("MANIAC database initialization failed: {}", error.getMessage());
        }
    }

    public void record(BMSModel model, ScoreData score, boolean bestExImproved, String source) {
        if (model == null || score == null) return;
        Map<String, String> values = model.getValues();
        String storage = values.get(BMSIRManiacPlayContext.MODEL_STORAGE_HASH);
        if (storage == null || storage.isBlank()) return;
        String base = required(values, BMSIRManiacPlayContext.MODEL_BASE_HASH);
        String virtual = values.get(BMSIRManiacPlayContext.MODEL_VIRTUAL_HASH);
        String rankingClass = required(values, BMSIRManiacPlayContext.MODEL_RANKING_CLASS);
        String options = required(values, BMSIRManiacPlayContext.MODEL_OPTIONS);
        String generationSeed = required(values, BMSIRManiacPlayContext.MODEL_GENERATION_SEED);
        int algorithm = parseInt(values.get(BMSIRManiacPlayContext.MODEL_ALGORITHM_VERSION));
        String placement = required(values, BMSIRManiacPlayContext.MODEL_PLACEMENT_HASH);
        String normalizedSource = "ir_sync".equals(source) ? "ir_sync" : "local";
        long now = Instant.now().getEpochSecond();

        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            try (PreparedStatement history = connection.prepareStatement("""
                    INSERT INTO maniac_play_history(
                        storage_hash, base_sha256, virtual_chart_id, ranking_class,
                        options, generation_seed, algorithm_version, placement_hash,
                        exscore, clear, minbp, play_option, random_seed, source, played_at
                    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                int index = 1;
                history.setString(index++, storage);
                history.setString(index++, base);
                history.setString(index++, virtual);
                history.setString(index++, rankingClass);
                history.setString(index++, options);
                history.setString(index++, generationSeed);
                history.setInt(index++, algorithm);
                history.setString(index++, placement);
                history.setInt(index++, score.getExscore());
                history.setInt(index++, score.getClear());
                history.setInt(index++, score.getMinbp());
                history.setInt(index++, score.getOption());
                history.setLong(index++, score.getSeed());
                history.setString(index++, normalizedSource);
                history.setLong(index, now);
                history.executeUpdate();
            }
            try (PreparedStatement meta = connection.prepareStatement("""
                    INSERT INTO maniac_score_meta(
                        storage_hash, base_sha256, virtual_chart_id, ranking_class,
                        options, generation_seed, algorithm_version, placement_hash,
                        best_ex, source, updated_at
                    ) VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(storage_hash) DO UPDATE SET
                        base_sha256=excluded.base_sha256,
                        virtual_chart_id=excluded.virtual_chart_id,
                        ranking_class=excluded.ranking_class,
                        options=CASE WHEN ? THEN excluded.options ELSE maniac_score_meta.options END,
                        generation_seed=excluded.generation_seed,
                        algorithm_version=excluded.algorithm_version,
                        placement_hash=CASE WHEN ? THEN excluded.placement_hash ELSE maniac_score_meta.placement_hash END,
                        best_ex=MAX(maniac_score_meta.best_ex, excluded.best_ex),
                        source=CASE WHEN ? THEN excluded.source ELSE maniac_score_meta.source END,
                        updated_at=excluded.updated_at
                    """)) {
                int index = 1;
                meta.setString(index++, storage);
                meta.setString(index++, base);
                meta.setString(index++, virtual);
                meta.setString(index++, rankingClass);
                meta.setString(index++, options);
                meta.setString(index++, generationSeed);
                meta.setInt(index++, algorithm);
                meta.setString(index++, placement);
                meta.setInt(index++, score.getExscore());
                meta.setString(index++, normalizedSource);
                meta.setLong(index++, now);
                meta.setBoolean(index++, bestExImproved);
                meta.setBoolean(index++, bestExImproved);
                meta.setBoolean(index, bestExImproved);
                meta.executeUpdate();
            }
            connection.commit();
        } catch (SQLException error) {
            logger.error("MANIAC play metadata write failed: {}", error.getMessage());
        }
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
