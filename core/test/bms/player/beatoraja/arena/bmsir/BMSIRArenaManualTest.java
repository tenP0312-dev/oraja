package bms.player.beatoraja.arena.bmsir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRArenaManualTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsOnlyBoundedStructuredText() throws Exception {
        JsonNode payload = mapper.readTree("""
                {
                  "type": "arena_manual",
                  "version": "1",
                  "title": "<b>Manual</b>",
                  "sections": [
                    {
                      "title": "CPU",
                      "items": ["plain text", {"html": "<script>x</script>"}]
                    }
                  ]
                }
                """);

        JsonNode sanitized = BMSIRArenaManual.sanitize(payload, mapper);

        assertEquals("<b>Manual</b>", sanitized.path("title").asText());
        assertEquals("plain text", sanitized.path("sections").get(0)
                .path("items").get(0).asText());
        assertEquals(1, sanitized.path("sections").get(0)
                .path("items").size());
        assertFalse(sanitized.has("html"));
    }

    @Test
    void rejectsMissingVersionOrSections() throws Exception {
        assertTrue(BMSIRArenaManual.sanitize(
                mapper.readTree("{\"sections\":[]}"),
                mapper
        ).isEmpty());
        assertTrue(BMSIRArenaManual.sanitize(
                mapper.readTree("{\"version\":\"1\"}"),
                mapper
        ).isEmpty());
    }
}
