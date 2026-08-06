package bms.player.beatoraja.arena.bmsir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Validates and caches the server-delivered Arena manual.
 *
 * The payload is deliberately reduced to plain structured text. It never
 * accepts or renders arbitrary HTML supplied by the server.
 */
final class BMSIRArenaManual {
    private static final Path CACHE_PATH =
            Path.of("config", "bmsir-arena-manual.json");
    private static final int MAX_SECTIONS = 32;
    private static final int MAX_ITEMS = 32;
    private static final int MAX_TEXT = 500;

    private BMSIRArenaManual() {
    }

    static JsonNode load(ObjectMapper mapper) {
        try {
            if (!Files.isRegularFile(CACHE_PATH)) {
                return mapper.createObjectNode();
            }
            return sanitize(mapper.readTree(
                    Files.readString(CACHE_PATH, StandardCharsets.UTF_8)
            ), mapper);
        } catch (IOException | RuntimeException ignored) {
            return mapper.createObjectNode();
        }
    }

    static JsonNode accept(JsonNode payload, ObjectMapper mapper) {
        JsonNode sanitized = sanitize(payload, mapper);
        if (!sanitized.isObject() || sanitized.size() == 0) {
            return mapper.createObjectNode();
        }
        save(sanitized, mapper);
        return sanitized;
    }

    static JsonNode sanitize(JsonNode payload, ObjectMapper mapper) {
        if (
                payload == null
                        || !payload.isObject()
                        || payload.path("version").asText("").isBlank()
                        || !payload.path("sections").isArray()
        ) {
            return mapper.createObjectNode();
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("type", "arena_manual");
        result.put("version", bounded(payload.path("version").asText()));
        result.put(
                "title",
                bounded(payload.path("title").asText("BMS-IR Arena マニュアル"))
        );
        ArrayNode sections = result.putArray("sections");
        int sectionCount = 0;
        for (JsonNode section : payload.path("sections")) {
            if (++sectionCount > MAX_SECTIONS || !section.isObject()) {
                break;
            }
            String title = bounded(section.path("title").asText());
            if (title.isBlank() || !section.path("items").isArray()) {
                continue;
            }
            ObjectNode cleanSection = sections.addObject();
            cleanSection.put("title", title);
            ArrayNode items = cleanSection.putArray("items");
            int itemCount = 0;
            for (JsonNode item : section.path("items")) {
                if (++itemCount > MAX_ITEMS) {
                    break;
                }
                if (item.isTextual()) {
                    String text = bounded(item.asText());
                    if (!text.isBlank()) {
                        items.add(text);
                    }
                }
            }
        }
        return sections.isEmpty() ? mapper.createObjectNode() : result;
    }

    private static String bounded(String value) {
        String normalized = value == null ? "" : value.strip();
        return normalized.length() <= MAX_TEXT
                ? normalized
                : normalized.substring(0, MAX_TEXT);
    }

    private static void save(JsonNode payload, ObjectMapper mapper) {
        Path temporary = CACHE_PATH.resolveSibling(
                CACHE_PATH.getFileName() + ".tmp"
        );
        try {
            Files.createDirectories(CACHE_PATH.getParent());
            Files.writeString(
                    temporary,
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(payload),
                    StandardCharsets.UTF_8
            );
            try {
                Files.move(
                        temporary,
                        CACHE_PATH,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary,
                        CACHE_PATH,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignoredDelete) {
                // A stale temporary file is harmless and is overwritten later.
            }
        }
    }
}
