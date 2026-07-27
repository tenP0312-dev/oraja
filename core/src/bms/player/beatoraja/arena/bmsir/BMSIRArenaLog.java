package bms.player.beatoraja.arena.bmsir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.Desktop;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Bounded, upload-safe Arena diagnostics.
 *
 * Authentication payloads, chat text and release fingerprints are
 * deliberately never copied from the wire message.
 */
final class BMSIRArenaLog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path LOG_PATH = Paths.get("bmsir-arena.log")
            .toAbsolutePath()
            .normalize();
    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_BACKUPS = 5;

    private BMSIRArenaLog() {
    }

    static synchronized void event(String event, Object... details) {
        try {
            rotateIfNeeded();
            ObjectNode line = JSON.createObjectNode();
            line.put("at", Instant.now().toString());
            line.put("event", safeText(event));
            for (int index = 0; index + 1 < details.length; index += 2) {
                String key = safeText(details[index]);
                if (key.isBlank() || sensitiveKey(key)) {
                    continue;
                }
                putValue(line, key, details[index + 1]);
            }
            Files.writeString(
                    LOG_PATH,
                    JSON.writeValueAsString(line) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
            // Diagnostics must never affect gameplay or socket processing.
        }
    }

    static void message(String direction, JsonNode message) {
        if (message == null) {
            event(direction + "_message", "type", "null");
            return;
        }
        JsonNode chart = message.path("chart");
        JsonNode rules = message.path("rules");
        event(
                direction + "_message",
                "type", message.path("type").asText(""),
                "match_id", message.path("match_id").asText(""),
                "state", message.path("state").asText(""),
                "reason", message.path("reason").asText(""),
                "code", message.path("code").asText(""),
                "seq", numberOrNull(message, "seq"),
                "player_count", numberOrNull(message, "player_count"),
                "ready_count", numberOrNull(message, "ready_count"),
                "exscore", numberOrNull(message, "exscore"),
                "processed_notes", numberOrNull(message, "processed_notes"),
                "minbp", numberOrNull(message, "minbp"),
                "max_combo", numberOrNull(message, "max_combo"),
                "play_mode", numberOrNull(message, "play_mode"),
                "play_option", numberOrNull(message, "play_option"),
                "play_option_1p", numberOrNull(message, "play_option_1p"),
                "play_option_2p", numberOrNull(message, "play_option_2p"),
                "flip", message.path("flip").isBoolean()
                        ? message.path("flip").asBoolean()
                        : null,
                "ln_mode", message.path("ln_mode").asText(""),
                "match_mode", rules.path("match_mode").asText(
                        message.path("match_mode").asText("")
                ),
                "score_rule", rules.path("score_rule").asText(
                        message.path("score_rule").asText("")
                ),
                "room_code", rules.path("room_code").asText(
                        message.path("room_code").asText("")
                ),
                "nomination_policy", rules.path("nomination_policy").asText(""),
                "retry_reason", message.path("retry_reason").asText(""),
                "missing_player_count", message.path("missing_player_ids").isArray()
                        ? message.path("missing_player_ids").size()
                        : null,
                "chart_hash", chart.path("md5").asText(
                        message.path("chart_hash").asText("")
                ),
                "players", message.path("players").isArray()
                        ? message.path("players").size()
                        : null
        );
    }

    static boolean openLogFolder() {
        try {
            Path directory = LOG_PATH.getParent();
            if (directory == null || !Files.isDirectory(directory)) {
                return false;
            }
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            Desktop.getDesktop().open(directory.toFile());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static String logFileName() {
        return LOG_PATH.getFileName().toString();
    }

    private static Number numberOrNull(JsonNode message, String field) {
        JsonNode value = message.get(field);
        return value != null && value.isNumber() ? value.numberValue() : null;
    }

    private static void putValue(ObjectNode line, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Boolean item) {
            line.put(key, item);
        } else if (value instanceof Integer item) {
            line.put(key, item);
        } else if (value instanceof Long item) {
            line.put(key, item);
        } else if (value instanceof Float item) {
            line.put(key, item);
        } else if (value instanceof Double item) {
            line.put(key, item);
        } else if (value instanceof Number item) {
            line.put(key, item.doubleValue());
        } else {
            line.put(key, safeText(value));
        }
    }

    private static boolean sensitiveKey(String key) {
        String normalized = key.toLowerCase();
        return normalized.contains("pass")
                || normalized.contains("token")
                || normalized.contains("fingerprint")
                || normalized.contains("client_hash")
                || normalized.contains("plugin_hash")
                || normalized.equals("text");
    }

    private static String safeText(Object value) {
        String text = String.valueOf(value == null ? "" : value)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private static void rotateIfNeeded() throws Exception {
        if (!Files.exists(LOG_PATH) || Files.size(LOG_PATH) < MAX_BYTES) {
            return;
        }
        for (int index = MAX_BACKUPS - 1; index >= 1; index--) {
            Path source = backupPath(index);
            if (Files.exists(source)) {
                Files.move(
                        source,
                        backupPath(index + 1),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
        Files.move(
                LOG_PATH,
                backupPath(1),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private static Path backupPath(int index) {
        return LOG_PATH.resolveSibling(LOG_PATH.getFileName() + "." + index);
    }
}
