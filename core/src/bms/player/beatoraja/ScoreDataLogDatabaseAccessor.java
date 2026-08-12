package bms.player.beatoraja;

import java.sql.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteConfig.SynchronousMode;

/**
 * スコアデータログデータベースアクセサ
 * 
 * @author omi
 */
public class ScoreDataLogDatabaseAccessor extends SQLiteDatabaseAccessor {
	private static final Logger logger = LoggerFactory.getLogger(ScoreDataLogDatabaseAccessor.class);
	private static final String TABLE_NAME = "scoredatalog";
	private static final Column[] SCORE_DATA_LOG_COLUMNS = {
			new Column("sha256", "TEXT", 1, 0),
			new Column("mode", "INTEGER"),
			new Column("clear", "INTEGER"),
			new Column("epg", "INTEGER"),
			new Column("lpg", "INTEGER"),
			new Column("egr", "INTEGER"),
			new Column("lgr", "INTEGER"),
			new Column("egd", "INTEGER"),
			new Column("lgd", "INTEGER"),
			new Column("ebd", "INTEGER"),
			new Column("lbd", "INTEGER"),
			new Column("epr", "INTEGER"),
			new Column("lpr", "INTEGER"),
			new Column("ems", "INTEGER"),
			new Column("lms", "INTEGER"),
			new Column("notes", "INTEGER"),
			new Column("combo", "INTEGER"),
			new Column("minbp", "INTEGER"),
			new Column("avgjudge", "INTEGER", 1, 0, String.valueOf(Integer.MAX_VALUE)),
			new Column("playcount", "INTEGER"),
			new Column("clearcount", "INTEGER"),
			new Column("trophy", "TEXT"),
			new Column("ghost", "TEXT"),
			new Column("option", "INTEGER"),
			new Column("seed", "INTEGER"),
			new Column("random", "INTEGER"),
			new Column("date", "INTEGER"),
			new Column("state", "INTEGER"),
			new Column("scorehash", "TEXT")
	};

	private final ResultSetHandler<List<Column>> columnHandler = new BeanListHandler<>(Column.class);

	private final QueryRunner qr;

	public ScoreDataLogDatabaseAccessor(String path) throws ClassNotFoundException {
		super(new Table(TABLE_NAME, SCORE_DATA_LOG_COLUMNS));

		Class.forName("org.sqlite.JDBC");
		SQLiteConfig conf = new SQLiteConfig();
		conf.setSharedCache(true);
		conf.setSynchronous(SynchronousMode.OFF);
		// conf.setJournalMode(JournalMode.MEMORY);
		SQLiteDataSource ds = new SQLiteDataSource(conf);
		ds.setUrl("jdbc:sqlite:" + path);
		qr = new QueryRunner(ds);
		
		try {
			migrateLegacyPrimaryKey();
			this.validate(qr);
		} catch (SQLException e) {
			logger.error("scoredatalog.dbの初期化に失敗しました", e);
		}
	}

	public void setScoreDataLog(ScoreData score) {
		setScoreDataLog(new ScoreData[] { score });
	}

	public void setScoreDataLog(ScoreData[] scores) {
		try (Connection con = qr.getDataSource().getConnection()) {
			con.setAutoCommit(false);
			for (ScoreData score : scores) {
				this.insert(qr, con, TABLE_NAME, score);
			}
			con.commit();
		} catch (Exception e) {
			logger.error("スコア更新時の例外", e);
		}
	}

	private void migrateLegacyPrimaryKey() throws SQLException {
		try (Connection connection = qr.getDataSource().getConnection()) {
			if (!existsTable(connection, TABLE_NAME)) {
				return;
			}
			List<Column> columns = qr.query(
					connection,
					"PRAGMA table_info('" + TABLE_NAME + "');",
					columnHandler
			);
			if (columns.stream().noneMatch(column -> column.getPk() > 0)) {
				return;
			}

			String legacyTable = TABLE_NAME + "_legacy_pk_" + System.currentTimeMillis();
			connection.setAutoCommit(false);
			try {
				qr.update(connection, "ALTER TABLE " + TABLE_NAME + " RENAME TO " + legacyTable);
				createScoreDataLogTable(connection);

				Set<String> legacyColumns = new HashSet<>();
				for (Column column : qr.query(
						connection,
						"PRAGMA table_info('" + legacyTable + "');",
						columnHandler
				)) {
					legacyColumns.add(column.getName());
				}
				StringJoiner commonColumns = new StringJoiner(",");
				for (Column column : SCORE_DATA_LOG_COLUMNS) {
					if (legacyColumns.contains(column.getName())) {
						commonColumns.add(column.getName());
					}
				}
				if (commonColumns.length() > 0) {
					String names = commonColumns.toString();
					qr.update(connection, "INSERT INTO " + TABLE_NAME + " (" + names + ") SELECT " + names + " FROM " + legacyTable);
				}
				qr.update(connection, "DROP TABLE " + legacyTable);
				connection.commit();
				logger.info("scoredatalog.dbを全プレー履歴形式へ移行しました");
			} catch (SQLException e) {
				connection.rollback();
				throw e;
			} finally {
				connection.setAutoCommit(true);
			}
		}
	}

	private void createScoreDataLogTable(Connection connection) throws SQLException {
		StringBuilder sql = new StringBuilder("CREATE TABLE [").append(TABLE_NAME).append("] (");
		for (int i = 0; i < SCORE_DATA_LOG_COLUMNS.length; i++) {
			Column column = SCORE_DATA_LOG_COLUMNS[i];
			if (i > 0) {
				sql.append(',');
			}
			sql.append('[').append(column.getName()).append("] ").append(column.getType());
			if (column.getNotnull() == 1) {
				sql.append(" NOT NULL");
			}
			if (column.getDefaultval() != null && !column.getDefaultval().isEmpty()) {
				sql.append(" DEFAULT ").append(column.getDefaultval());
			}
		}
		qr.update(connection, sql.append(");").toString());
	}

	private boolean existsTable(Connection connection, String tableName) throws SQLException {
		return !qr.query(
				connection,
				"SELECT * FROM sqlite_master WHERE name = ? and type='table';",
				new MapListHandler(),
				tableName
		).isEmpty();
	}
}
