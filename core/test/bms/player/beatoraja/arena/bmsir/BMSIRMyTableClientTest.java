package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.TableData;
import bms.player.beatoraja.song.SongData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRMyTableClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MD5 = "a".repeat(32);
    private static final String SHA256 = "b".repeat(64);
    private static final String BMSON_KEY = "c".repeat(32);

    @Test
    void convertsAuthoritativeSnapshotToStableTableFolders() {
        ObjectNode snapshot = snapshot();

        TableData data = BMSIRMyTableClient.tableData(snapshot);

        assertNotNull(data);
        assertEquals(BMSIRMyTableClient.TABLE_URL, data.getUrl());
        assertEquals("Owner Table", data.getName());
        assertEquals("★", data.getTag());
        assertEquals(2, data.getFolder().length);
        assertEquals("★1", data.getFolder()[0].getName());
        assertEquals(MD5, data.getFolder()[0].getSong()[0].getMd5());
        assertEquals("★2", data.getFolder()[1].getName());
        assertEquals(SHA256, data.getFolder()[1].getSong()[0].getSha256());
        assertEquals(0, data.getCourse().length);
    }

    @Test
    void matchesSelectedChartsByMd5OrBmsonSha256() {
        ObjectNode snapshot = snapshot();
        SongData bms = new SongData();
        bms.setMd5(MD5.toUpperCase());
        SongData bmson = new SongData();
        bmson.setSha256(SHA256);

        JsonNode bmsEntry = BMSIRMyTableClient.entryFor(snapshot, bms);
        JsonNode bmsonEntry = BMSIRMyTableClient.entryFor(snapshot, bmson);

        assertNotNull(bmsEntry);
        assertEquals("1", bmsEntry.path("level").asText());
        assertNotNull(bmsonEntry);
        assertEquals(BMSON_KEY, bmsonEntry.path("entry_hash").asText());
    }

    @Test
    void keepsRawLegacyBmsonEntryIdentityAvailableForRemoval() {
        ObjectNode snapshot = snapshot();
        JsonNode bmsonEntry = snapshot.path("table").path("entries").get(1);
        ((ObjectNode) bmsonEntry).put("entry_hash", SHA256);
        SongData bmson = new SongData();
        bmson.setSha256(SHA256);

        JsonNode selected = BMSIRMyTableClient.entryFor(snapshot, bmson);

        assertNotNull(selected);
        assertEquals(SHA256, selected.path("entry_hash").asText());
        assertEquals(SHA256, BMSIRMyTableClient.entryIdentity(selected));
    }

    @Test
    void keepsMutationRevisionAndMetadataInPayload() {
        ObjectNode payload = BMSIRMyTableClient.tablePayload(
                "update_table",
                "d".repeat(64),
                "Renamed",
                "R",
                "Description",
                "unlisted"
        );

        assertEquals("update_table", payload.path("action").asText());
        assertEquals("d".repeat(64), payload.path("expected_revision").asText());
        assertEquals("Renamed", payload.path("name").asText());
        assertEquals("R", payload.path("symbol").asText());
        assertEquals("Description", payload.path("description").asText());
        assertEquals("unlisted", payload.path("visibility").asText());
    }

    @Test
    void targetsOnlyTheExplicitlySelectedTable() {
        ObjectNode payload = BMSIRMyTableClient.withTableId(
                JSON.createObjectNode().put("action", "update_table"),
                37L
        );
        ObjectNode untargeted = BMSIRMyTableClient.withTableId(
                JSON.createObjectNode().put("action", "create"),
                0L
        );

        assertEquals(37L, payload.path("table_id").asLong());
        assertTrue(untargeted.path("table_id").isMissingNode());
    }

    @Test
    void readsSelectionStateFromMultiTableSnapshot() {
        ObjectNode snapshot = snapshot();
        snapshot.put("selected_table_id", 37L);
        snapshot.put("selection_required", false);

        assertEquals(37L, BMSIRMyTableClient.selectedTableId(snapshot));

        snapshot.put("selected_table_id", 0L);
        ((ObjectNode) snapshot.path("table")).put("id", 0L);
        snapshot.put("selection_required", true);
        assertEquals(0L, BMSIRMyTableClient.selectedTableId(snapshot));
        assertTrue(BMSIRMyTableClient.selectionRequired(snapshot));
    }

    @Test
    void omitsMusicSelectBarUntilTableHasAChart() {
        ObjectNode snapshot = snapshot();
        ((ArrayNode) snapshot.path("table").path("entries")).removeAll();
        assertNull(BMSIRMyTableClient.tableData(snapshot));

        snapshot.putNull("table");
        snapshot.put("revision", "none");
        assertNull(BMSIRMyTableClient.tableData(snapshot));
    }

    private static ObjectNode snapshot() {
        ObjectNode snapshot = JSON.createObjectNode();
        snapshot.put("ok", true);
        snapshot.put("revision", "d".repeat(64));
        ObjectNode table = snapshot.putObject("table");
        table.put("id", 12L);
        table.put("name", "Owner Table");
        table.put("symbol", "★");
        table.put("visibility", "private");
        ArrayNode entries = table.putArray("entries");
        entries.addObject()
                .put("entry_hash", MD5)
                .put("md5", MD5)
                .put("title", "BMS Chart")
                .put("artist", "Artist")
                .put("level", "1");
        entries.addObject()
                .put("entry_hash", BMSON_KEY)
                .put("bms_ir_hash", BMSON_KEY)
                .put("sha256", SHA256)
                .put("title", "bmson Chart")
                .put("artist", "Artist")
                .put("level", "2");
        return snapshot;
    }
}
