package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.song.SongData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRMyTableDraftTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REVISION = "d".repeat(64);
    private static final String MD5_A = "a".repeat(32);
    private static final String MD5_B = "b".repeat(32);
    private static final String SHA_C = "c".repeat(64);

    @Test
    void coalescesChangesAndBuildsOneOrderedBatchPayload() {
        ObjectNode snapshot = snapshot();
        BMSIRMyTableDraft draft = new BMSIRMyTableDraft();
        SongData existing = song(MD5_A, "", "Existing");
        SongData added = song(MD5_B, "", "Added");
        SongData removed = song("", SHA_C, "Removed bmson");

        assertEquals(
                BMSIRMyTableDraft.StageResult.STAGED,
                draft.stageTable(
                        12L,
                        REVISION,
                        snapshot.path("table"),
                        "Renamed",
                        "R",
                        "One batch",
                        "unlisted"
                )
        );
        assertEquals(
                BMSIRMyTableDraft.StageResult.STAGED,
                draft.stageUpsert(
                        12L,
                        REVISION,
                        entry(snapshot, 0),
                        existing,
                        "10",
                        "first"
                )
        );
        draft.stageUpsert(12L, REVISION, entry(snapshot, 0), existing, "11", "latest");
        draft.stageUpsert(12L, REVISION, null, added, "12", "new");
        draft.stageRemove(12L, REVISION, entry(snapshot, 1), removed);

        assertEquals(4, draft.totalCount());
        assertEquals(3, draft.entryCount());
        ObjectNode payload = draft.payload(JSON);
        assertNotNull(payload);
        assertEquals("apply_changes", payload.path("action").asText());
        assertEquals(12L, payload.path("table_id").asLong());
        assertEquals(REVISION, payload.path("expected_revision").asText());
        assertEquals("Renamed", payload.path("table").path("name").asText());
        assertEquals(3, payload.path("changes").size());
        assertEquals(MD5_A, payload.path("changes").get(0).path("md5").asText());
        assertEquals("11", payload.path("changes").get(0).path("level").asText());
        assertEquals("latest", payload.path("changes").get(0).path("comment").asText());
        assertEquals(MD5_B, payload.path("changes").get(1).path("md5").asText());
        assertEquals("remove_entry", payload.path("changes").get(2).path("op").asText());
        assertEquals("e".repeat(32), payload.path("changes").get(2).path("entry_hash").asText());
    }

    @Test
    void matchingServerStateAndRemovingANewAdditionClearPendingChanges() {
        ObjectNode snapshot = snapshot();
        BMSIRMyTableDraft draft = new BMSIRMyTableDraft();
        SongData existing = song(MD5_A, "", "Existing");
        SongData added = song(MD5_B, "", "Added");

        assertEquals(
                BMSIRMyTableDraft.StageResult.NO_CHANGE,
                draft.stageUpsert(
                        12L,
                        REVISION,
                        entry(snapshot, 0),
                        existing,
                        "1",
                        "first[[BR]]second"
                )
        );
        draft.stageUpsert(12L, REVISION, null, added, "2", "");
        assertTrue(draft.hasChanges());
        assertEquals(
                BMSIRMyTableDraft.StageResult.CLEARED,
                draft.stageRemove(12L, REVISION, null, added)
        );
        assertFalse(draft.hasChanges());
        assertNull(draft.payload(JSON));
    }

    @Test
    void conflictRequiresExplicitRebaseAndSixtyFourChartBoundaryIsEnforced() {
        BMSIRMyTableDraft draft = new BMSIRMyTableDraft();
        for (int index = 0; index < BMSIRMyTableDraft.MAX_ENTRY_CHANGES; index++) {
            SongData song = song(String.format("%032x", index + 1), "", "Chart " + index);
            assertEquals(
                    BMSIRMyTableDraft.StageResult.STAGED,
                    draft.stageUpsert(12L, REVISION, null, song, "1", "")
            );
        }
        assertEquals(
                BMSIRMyTableDraft.StageResult.FULL,
                draft.stageUpsert(
                        12L,
                        REVISION,
                        null,
                        song("f".repeat(32), "", "Overflow"),
                        "1",
                        ""
                )
        );
        draft.markConflict();
        assertTrue(draft.conflicted());
        String newRevision = "9".repeat(64);
        assertTrue(draft.rebase(12L, newRevision));
        assertFalse(draft.conflicted());
        assertEquals(newRevision, draft.payload(JSON).path("expected_revision").asText());
    }

    private static SongData song(String md5, String sha256, String title) {
        SongData song = new SongData();
        song.setMd5(md5);
        song.setSha256(sha256);
        song.setTitle(title);
        return song;
    }

    private static JsonNode entry(ObjectNode snapshot, int index) {
        return snapshot.path("table").path("entries").get(index);
    }

    private static ObjectNode snapshot() {
        ObjectNode snapshot = JSON.createObjectNode();
        snapshot.put("revision", REVISION);
        ObjectNode table = snapshot.putObject("table");
        table.put("id", 12L);
        table.put("name", "Owner Table");
        table.put("symbol", "★");
        table.put("description", "");
        table.put("visibility", "private");
        ArrayNode entries = table.putArray("entries");
        entries.addObject()
                .put("entry_hash", MD5_A)
                .put("md5", MD5_A)
                .put("comment", "first\nsecond")
                .put("level", "1");
        entries.addObject()
                .put("entry_hash", "e".repeat(32))
                .put("bms_ir_hash", "e".repeat(32))
                .put("sha256", SHA_C)
                .put("level", "2");
        return snapshot;
    }
}
