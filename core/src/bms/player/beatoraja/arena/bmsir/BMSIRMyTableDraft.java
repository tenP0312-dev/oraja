package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.song.SongData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** In-memory, table-revision-bound draft for one atomic My Difficulty Table save. */
final class BMSIRMyTableDraft {
    static final int MAX_ENTRY_CHANGES = 64;

    enum StageResult {
        STAGED,
        CLEARED,
        NO_CHANGE,
        FULL,
        WRONG_TABLE
    }

    record TableChange(
            String name,
            String symbol,
            String description,
            String visibility
    ) {
    }

    record EntryChange(
            String key,
            String op,
            String title,
            String md5,
            String sha256,
            String entryHash,
            String level,
            String comment
    ) {
        boolean removal() {
            return "remove_entry".equals(op);
        }
    }

    private long tableId;
    private String revision = "none";
    private TableChange tableChange;
    private final LinkedHashMap<String, EntryChange> entryChanges = new LinkedHashMap<>();
    private boolean conflict;
    private long generation;

    synchronized void clear() {
        tableId = 0L;
        revision = "none";
        tableChange = null;
        entryChanges.clear();
        conflict = false;
        generation++;
    }

    synchronized boolean hasChanges() {
        return tableChange != null || !entryChanges.isEmpty();
    }

    synchronized int totalCount() {
        return entryChanges.size() + (tableChange == null ? 0 : 1);
    }

    synchronized int entryCount() {
        return entryChanges.size();
    }

    synchronized boolean hasTableChange() {
        return tableChange != null;
    }

    synchronized TableChange tableChange() {
        return tableChange;
    }

    synchronized long generation() {
        return generation;
    }

    synchronized boolean conflicted() {
        return conflict;
    }

    synchronized long tableId() {
        return tableId;
    }

    synchronized String revision() {
        return revision;
    }

    synchronized List<EntryChange> entries() {
        return new ArrayList<>(entryChanges.values());
    }

    synchronized EntryChange entryFor(SongData song, JsonNode authoritativeEntry) {
        String key = entryKey(song, authoritativeEntry);
        return key.isBlank() ? null : entryChanges.get(key);
    }

    synchronized StageResult stageTable(
            long selectedTableId,
            String selectedRevision,
            JsonNode authoritativeTable,
            String name,
            String symbol,
            String description,
            String visibility
    ) {
        TableChange candidate = new TableChange(
                value(name),
                value(symbol),
                value(description),
                value(visibility).isBlank() ? "private" : value(visibility)
        );
        boolean unchanged = authoritativeTable != null
                && authoritativeTable.isObject()
                && candidate.name().equals(authoritativeTable.path("name").asText(""))
                && candidate.symbol().equals(authoritativeTable.path("symbol").asText(""))
                && candidate.description().equals(authoritativeTable.path("description").asText(""))
                && candidate.visibility().equals(authoritativeTable.path("visibility").asText("private"));
        if (unchanged) {
            if (tableChange == null) {
                return StageResult.NO_CHANGE;
            }
            tableChange = null;
            finishIfEmpty();
            generation++;
            return StageResult.CLEARED;
        }
        if (!bind(selectedTableId, selectedRevision)) {
            return StageResult.WRONG_TABLE;
        }
        tableChange = candidate;
        generation++;
        return StageResult.STAGED;
    }

    synchronized StageResult stageUpsert(
            long selectedTableId,
            String selectedRevision,
            JsonNode authoritativeEntry,
            SongData song,
            String level,
            String comment
    ) {
        String key = entryKey(song, authoritativeEntry);
        if (key.isBlank()) {
            return StageResult.WRONG_TABLE;
        }
        String normalizedLevel = value(level);
        String normalizedComment = value(comment);
        boolean unchanged = authoritativeEntry != null
                && authoritativeEntry.isObject()
                && normalizedLevel.equals(authoritativeEntry.path("level").asText(""))
                && normalizedComment.equals(authoritativeEntry.path("comment").asText(""));
        if (unchanged) {
            if (entryChanges.remove(key) == null) {
                return StageResult.NO_CHANGE;
            }
            finishIfEmpty();
            generation++;
            return StageResult.CLEARED;
        }
        if (!entryChanges.containsKey(key) && entryChanges.size() >= MAX_ENTRY_CHANGES) {
            return StageResult.FULL;
        }
        if (!bind(selectedTableId, selectedRevision)) {
            return StageResult.WRONG_TABLE;
        }
        entryChanges.put(key, new EntryChange(
                key,
                "upsert_entry",
                song == null ? key : value(song.getFullTitle()),
                song == null ? "" : normalizedHash(song.getMd5(), 32),
                song == null ? "" : normalizedHash(song.getSha256(), 64),
                "",
                normalizedLevel,
                normalizedComment
        ));
        generation++;
        return StageResult.STAGED;
    }

    synchronized StageResult stageRemove(
            long selectedTableId,
            String selectedRevision,
            JsonNode authoritativeEntry,
            SongData song
    ) {
        String key = entryKey(song, authoritativeEntry);
        if (key.isBlank()) {
            return StageResult.WRONG_TABLE;
        }
        EntryChange pending = entryChanges.get(key);
        if (authoritativeEntry == null || !authoritativeEntry.isObject()) {
            if (pending != null && !pending.removal()) {
                entryChanges.remove(key);
                finishIfEmpty();
                generation++;
                return StageResult.CLEARED;
            }
            return StageResult.NO_CHANGE;
        }
        if (!entryChanges.containsKey(key) && entryChanges.size() >= MAX_ENTRY_CHANGES) {
            return StageResult.FULL;
        }
        if (!bind(selectedTableId, selectedRevision)) {
            return StageResult.WRONG_TABLE;
        }
        entryChanges.put(key, new EntryChange(
                key,
                "remove_entry",
                song == null ? key : value(song.getFullTitle()),
                "",
                "",
                BMSIRMyTableClient.entryIdentity(authoritativeEntry),
                authoritativeEntry.path("level").asText(""),
                authoritativeEntry.path("comment").asText("")
        ));
        generation++;
        return StageResult.STAGED;
    }

    synchronized boolean undoEntry(String key) {
        if (entryChanges.remove(value(key)) == null) {
            return false;
        }
        finishIfEmpty();
        generation++;
        return true;
    }

    synchronized boolean undoTable() {
        if (tableChange == null) {
            return false;
        }
        tableChange = null;
        finishIfEmpty();
        generation++;
        return true;
    }

    synchronized void markConflict() {
        if (hasChanges()) {
            conflict = true;
            generation++;
        }
    }

    synchronized boolean rebase(long selectedTableId, String selectedRevision) {
        if (!hasChanges() || selectedTableId <= 0L || tableId != selectedTableId) {
            return false;
        }
        revision = value(selectedRevision).toLowerCase();
        conflict = false;
        generation++;
        return true;
    }

    synchronized ObjectNode payload(ObjectMapper json) {
        if (!hasChanges() || tableId <= 0L) {
            return null;
        }
        ObjectNode payload = json.createObjectNode();
        payload.put("action", "apply_changes");
        payload.put("table_id", tableId);
        payload.put("expected_revision", revision);
        if (tableChange != null) {
            ObjectNode table = payload.putObject("table");
            table.put("name", tableChange.name());
            table.put("symbol", tableChange.symbol());
            table.put("description", tableChange.description());
            table.put("visibility", tableChange.visibility());
        }
        ArrayNode changes = payload.putArray("changes");
        for (EntryChange change : entryChanges.values()) {
            ObjectNode item = changes.addObject();
            item.put("op", change.op());
            if (change.removal()) {
                item.put("entry_hash", change.entryHash());
            } else {
                item.put("md5", change.md5());
                item.put("sha256", change.sha256());
                item.put("level", change.level());
                item.put("comment", change.comment());
            }
        }
        return payload;
    }

    private boolean bind(long selectedTableId, String selectedRevision) {
        if (selectedTableId <= 0L) {
            return false;
        }
        if (!hasChanges()) {
            tableId = selectedTableId;
            revision = value(selectedRevision).toLowerCase();
            conflict = false;
            return true;
        }
        return tableId == selectedTableId;
    }

    private void finishIfEmpty() {
        if (!hasChanges()) {
            tableId = 0L;
            revision = "none";
            conflict = false;
        }
    }

    private static String entryKey(SongData song, JsonNode authoritativeEntry) {
        String authoritative = BMSIRMyTableClient.entryIdentity(authoritativeEntry);
        return authoritative.isBlank() ? BMSIRMyTableClient.chartKey(song) : authoritative;
    }

    private static String normalizedHash(String raw, int length) {
        String value = value(raw).toLowerCase();
        return value.matches("[0-9a-f]{" + length + "}") ? value : "";
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
