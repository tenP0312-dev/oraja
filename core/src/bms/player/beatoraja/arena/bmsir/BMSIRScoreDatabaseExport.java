package bms.player.beatoraja.arena.bmsir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Creates a non-destructive score.db snapshot that excludes Arena-only scores. */
public final class BMSIRScoreDatabaseExport {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private BMSIRScoreDatabaseExport() {
    }

    public static ExportResult export(Path playerDirectory) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path source = playerDirectory.resolve("score.db").toAbsolutePath().normalize();
        Path maniac = playerDirectory.resolve("bmsir_maniac.db").toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("score.db was not found: " + source);
        }
        String stamp = LocalDateTime.now().format(FILE_TIME);
        Path output = uniqueOutput(playerDirectory, stamp);
        int normalScores;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            requireIntegrity(connection, "source score.db");
            normalScores = count(connection, "score");
            try (Statement statement = connection.createStatement()) {
                statement.execute("VACUUM INTO '" + sqlQuote(output.toString()) + "'");
            }
        }
        int maniacScores = 0;
        if (Files.isRegularFile(maniac)) {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + maniac)) {
                requireIntegrity(connection, "bmsir_maniac.db");
                maniacScores = count(connection, "score");
            }
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + output)) {
            requireIntegrity(connection, "exported score.db");
            int exported = count(connection, "score");
            if (exported != normalScores) {
                throw new IllegalStateException(
                        "Exported score count mismatch: " + normalScores + " != " + exported
                );
            }
        }
        return new ExportResult(output, normalScores, maniacScores);
    }

    private static Path uniqueOutput(Path directory, String stamp) {
        Path output = directory.resolve("score-vanilla-" + stamp + ".db");
        int suffix = 2;
        while (Files.exists(output)) {
            output = directory.resolve("score-vanilla-" + stamp + '-' + suffix++ + ".db");
        }
        return output.toAbsolutePath().normalize();
    }

    private static void requireIntegrity(Connection connection, String label) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                throw new IllegalStateException(label + " failed SQLite integrity_check");
            }
        }
    }

    private static int count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static String sqlQuote(String value) {
        return value.replace("'", "''");
    }

    public record ExportResult(Path path, int normalScores, int excludedManiacScores) {
    }
}
