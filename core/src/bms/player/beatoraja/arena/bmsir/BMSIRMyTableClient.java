package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.Version;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.bar.Bar;
import bms.player.beatoraja.select.bar.SongBar;
import bms.player.beatoraja.song.SongData;
import com.badlogic.gdx.Gdx;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Owner-only My Difficulty Table transport and Music Select hot reload. */
final class BMSIRMyTableClient {
    static final String API_PATH = "/new/api/oraja/my_table.json";
    static final String TABLE_URL = "bmsir://my-difficulty-table";

    private static final Logger logger = LoggerFactory.getLogger(BMSIRMyTableClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final AtomicLong SESSION = new AtomicLong();
    private static final BMSIRMyTableDraft DRAFT = new BMSIRMyTableDraft();

    private static volatile MainController main;
    private static volatile JsonNode snapshot = emptySnapshot();
    private static volatile TableData pendingTable;
    private static volatile long pendingSequence;
    private static volatile long appliedSequence;
    private static volatile boolean requestRunning;
    private static volatile String statusMessage = "";
    private static volatile String errorMessage = "";

    private BMSIRMyTableClient() {
    }

    static synchronized void initialize(MainController controller) {
        long session = SESSION.incrementAndGet();
        main = controller;
        snapshot = emptySnapshot();
        pendingTable = null;
        pendingSequence = 0L;
        appliedSequence = 0L;
        requestRunning = false;
        DRAFT.clear();
        statusMessage = text("マイ難易度表を読み込んでいます", "Loading My Difficulty Table");
        errorMessage = "";
        requestSnapshot(session);
    }

    static synchronized void shutdown() {
        SESSION.incrementAndGet();
        main = null;
        snapshot = emptySnapshot();
        pendingTable = null;
        pendingSequence = 0L;
        appliedSequence = 0L;
        requestRunning = false;
        DRAFT.clear();
        statusMessage = "";
        errorMessage = "";
    }

    static JsonNode snapshotView() {
        return snapshot;
    }

    static boolean isBusy() {
        return requestRunning;
    }

    static String statusMessage() {
        return statusMessage;
    }

    static String errorMessage() {
        return errorMessage;
    }

    static void requestSnapshot() {
        if (rejectDraftLoss()) {
            return;
        }
        requestSnapshot(SESSION.get(), currentTableId());
    }

    private static void requestSnapshot(long expectedSession) {
        requestSnapshot(expectedSession, 0L);
    }

    private static void requestSnapshot(long expectedSession, long tableId) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("action", "snapshot");
        withTableId(payload, tableId);
        submit(payload, expectedSession, text("再読み込みました", "Reloaded"));
    }

    static void selectTable(long tableId) {
        if (tableId <= 0L) {
            errorMessage = text("編集する難易度表を選んでください", "Select a table to edit");
            return;
        }
        if (rejectDraftLoss()) {
            return;
        }
        requestSnapshot(
                SESSION.get(),
                tableId
        );
        statusMessage = text("難易度表を切り替えています", "Switching difficulty table");
    }

    static void createTable(
            String name,
            String symbol,
            String description,
            String visibility
    ) {
        if (rejectDraftLoss()) {
            return;
        }
        ObjectNode payload = tablePayload(
                "create",
                "none",
                name,
                symbol,
                description,
                visibility
        );
        submit(payload, SESSION.get(), text("作成して本体へ反映しました", "Created and applied"));
    }

    static void updateTable(
            String name,
            String symbol,
            String description,
            String visibility
    ) {
        ObjectNode payload = tablePayload(
                "update_table",
                currentRevision(),
                name,
                symbol,
                description,
                visibility
        );
        withTableId(payload, currentTableId());
        submit(payload, SESSION.get(), text("表情報を保存して本体へ反映しました", "Table details saved and applied"));
    }

    static void upsertEntry(SongData song, String level, String comment) {
        if (song == null || chartKey(song).isBlank()) {
            errorMessage = text("選択中の譜面を識別できません", "The selected chart has no usable hash");
            return;
        }
        ObjectNode payload = JSON.createObjectNode();
        payload.put("action", "upsert_entry");
        withTableId(payload, currentTableId());
        payload.put("expected_revision", currentRevision());
        payload.put("md5", normalizedHash(song.getMd5(), 32));
        payload.put("sha256", normalizedHash(song.getSha256(), 64));
        payload.put("level", level == null ? "" : level);
        payload.put("comment", comment == null ? "" : comment);
        submit(payload, SESSION.get(), text("譜面を保存して本体へ反映しました", "Chart saved and applied"));
    }

    static void removeEntry(JsonNode entry) {
        String entryHash = entryIdentity(entry);
        if (entryHash.isBlank()) {
            errorMessage = text("削除対象を識別できません", "The table entry has no usable identity");
            return;
        }
        ObjectNode payload = JSON.createObjectNode();
        payload.put("action", "remove_entry");
        withTableId(payload, currentTableId());
        payload.put("expected_revision", currentRevision());
        payload.put("entry_hash", entryHash);
        submit(payload, SESSION.get(), text("譜面を削除して本体へ反映しました", "Chart removed and applied"));
    }

    static synchronized BMSIRMyTableDraft.StageResult stageTableUpdate(
            String name,
            String symbol,
            String description,
            String visibility
    ) {
        BMSIRMyTableDraft.StageResult result = DRAFT.stageTable(
                currentTableId(),
                currentRevision(),
                snapshot.path("table"),
                name,
                symbol,
                description,
                visibility
        );
        reportStageResult(result, text("表情報の変更を保留しました", "Table detail changes staged"));
        return result;
    }

    static synchronized BMSIRMyTableDraft.StageResult stageEntry(
            SongData song,
            String level,
            String comment
    ) {
        JsonNode authoritative = entryFor(snapshot, song);
        BMSIRMyTableDraft.StageResult result = DRAFT.stageUpsert(
                currentTableId(),
                currentRevision(),
                authoritative,
                song,
                level,
                comment
        );
        reportStageResult(result, authoritative == null
                ? text("譜面の追加を保留しました", "Chart addition staged")
                : text("譜面の変更を保留しました", "Chart changes staged"));
        return result;
    }

    static synchronized BMSIRMyTableDraft.StageResult stageRemoval(SongData song) {
        JsonNode authoritative = entryFor(snapshot, song);
        BMSIRMyTableDraft.StageResult result = DRAFT.stageRemove(
                currentTableId(),
                currentRevision(),
                authoritative,
                song
        );
        reportStageResult(result, authoritative == null
                ? text("保留中の追加を取り消しました", "Pending addition cancelled")
                : text("譜面の削除を保留しました", "Chart removal staged"));
        return result;
    }

    static synchronized BMSIRMyTableDraft.EntryChange draftEntryFor(SongData song) {
        return DRAFT.entryFor(song, entryFor(snapshot, song));
    }

    static synchronized List<BMSIRMyTableDraft.EntryChange> draftEntries() {
        return DRAFT.entries();
    }

    static synchronized boolean hasDraft() {
        return DRAFT.hasChanges();
    }

    static synchronized boolean hasDraftTableChange() {
        return DRAFT.hasTableChange();
    }

    static synchronized BMSIRMyTableDraft.TableChange draftTableChange() {
        return DRAFT.tableChange();
    }

    static synchronized boolean draftConflicted() {
        return DRAFT.conflicted();
    }

    static synchronized int draftCount() {
        return DRAFT.totalCount();
    }

    static synchronized int draftEntryCount() {
        return DRAFT.entryCount();
    }

    static synchronized long draftGeneration() {
        return DRAFT.generation();
    }

    static synchronized void undoDraftEntry(String key) {
        if (DRAFT.undoEntry(key)) {
            statusMessage = text("保留中の変更を取り消しました", "Pending change removed");
            errorMessage = "";
        }
    }

    static synchronized void undoDraftTableChange() {
        if (DRAFT.undoTable()) {
            statusMessage = text("表情報の保留を取り消しました", "Pending table details removed");
            errorMessage = "";
        }
    }

    static synchronized void discardDraft() {
        DRAFT.clear();
        statusMessage = text("保留中の変更をすべて破棄しました", "All pending changes discarded");
        errorMessage = "";
    }

    static synchronized void rebaseDraft() {
        if (DRAFT.rebase(currentTableId(), currentRevision())) {
            statusMessage = text(
                    "最新状態を基準にしました。保留内容を確認して保存してください",
                    "Draft rebased on the latest state; review it before saving"
            );
            errorMessage = "";
        }
    }

    static synchronized void applyChanges() {
        if (!DRAFT.hasChanges()) {
            errorMessage = text("保留中の変更はありません", "There are no pending changes");
            return;
        }
        if (DRAFT.conflicted()) {
            errorMessage = text(
                    "競合後の最新状態を確認し、基準を更新してから保存してください",
                    "Review the latest state and rebase the draft before saving"
            );
            return;
        }
        ObjectNode payload = DRAFT.payload(JSON);
        if (payload == null) {
            errorMessage = text("保留内容を作成できません", "Could not build the pending changes");
            return;
        }
        submit(
                payload,
                SESSION.get(),
                text("変更を一括保存して本体へ反映しました", "Changes saved together and applied"),
                true,
                true
        );
    }

    static String entryIdentity(JsonNode entry) {
        String rawEntryHash = entry == null ? "" : entry.path("entry_hash").asText();
        String entryHash = normalizedHash(rawEntryHash, 32);
        return entryHash.isBlank() ? normalizedHash(rawEntryHash, 64) : entryHash;
    }

    static SongData selectedSong() {
        MainController controller = main;
        if (controller == null || !(controller.getCurrentState() instanceof MusicSelector selector)) {
            return null;
        }
        Bar selected = selector.getBarManager().getSelected();
        return selected instanceof SongBar songBar ? songBar.getSongData() : null;
    }

    static String chartKey(SongData song) {
        if (song == null) {
            return "";
        }
        String md5 = normalizedHash(song.getMd5(), 32);
        if (!md5.isBlank()) {
            return md5;
        }
        return normalizedHash(song.getSha256(), 64);
    }

    static JsonNode entryFor(JsonNode ownerSnapshot, SongData song) {
        if (ownerSnapshot == null || song == null) {
            return null;
        }
        String md5 = normalizedHash(song.getMd5(), 32);
        String sha256 = normalizedHash(song.getSha256(), 64);
        JsonNode entries = ownerSnapshot.path("table").path("entries");
        if (!entries.isArray()) {
            return null;
        }
        for (JsonNode entry : entries) {
            if ((!md5.isBlank() && (
                    md5.equals(normalizedHash(entry.path("entry_hash").asText(), 32))
                            || md5.equals(normalizedHash(entry.path("md5").asText(), 32))
                            || md5.equals(normalizedHash(entry.path("bms_ir_hash").asText(), 32))
            )) || (!sha256.isBlank()
                    && sha256.equals(normalizedHash(entry.path("sha256").asText(), 64)))) {
                return entry;
            }
        }
        return null;
    }

    static ObjectNode tablePayload(
            String action,
            String revision,
            String name,
            String symbol,
            String description,
            String visibility
    ) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("action", action == null ? "" : action);
        payload.put("expected_revision", revision == null ? "" : revision);
        payload.put("name", name == null ? "" : name);
        payload.put("symbol", symbol == null ? "" : symbol);
        payload.put("description", description == null ? "" : description);
        payload.put("visibility", visibility == null ? "private" : visibility);
        return payload;
    }

    static ObjectNode withTableId(ObjectNode payload, long tableId) {
        if (payload != null && tableId > 0L) {
            payload.put("table_id", tableId);
        }
        return payload;
    }

    static long selectedTableId(JsonNode ownerSnapshot) {
        if (ownerSnapshot == null) {
            return 0L;
        }
        long selected = ownerSnapshot.path("selected_table_id").asLong(0L);
        return selected > 0L
                ? selected
                : ownerSnapshot.path("table").path("id").asLong(0L);
    }

    static boolean selectionRequired(JsonNode ownerSnapshot) {
        return ownerSnapshot != null
                && ownerSnapshot.path("selection_required").asBoolean(false);
    }

    static TableData tableData(JsonNode ownerSnapshot) {
        JsonNode table = ownerSnapshot == null ? null : ownerSnapshot.path("table");
        if (table == null || !table.isObject()) {
            return null;
        }
        Map<String, List<SongData>> levels = new LinkedHashMap<>();
        List<SongData> allSongs = new ArrayList<>();
        JsonNode entries = table.path("entries");
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                String md5 = normalizedHash(entry.path("md5").asText(), 32);
                String sha256 = normalizedHash(entry.path("sha256").asText(), 64);
                if (md5.isBlank() && sha256.isBlank()) {
                    continue;
                }
                SongData song = new SongData();
                song.setMd5(md5);
                song.setSha256(sha256);
                String fallback = md5.isBlank() ? sha256 : md5;
                song.setTitle(limited(entry.path("title").asText(fallback), 300));
                song.setArtist(limited(entry.path("artist").asText(""), 300));
                song.setGenre("");
                song.setUrl(limited(entry.path("url").asText(""), 2_000));
                song.setAppendurl(limited(entry.path("url_diff").asText(""), 2_000));
                String mode = entry.path("mode").asText("").trim();
                if (!mode.isEmpty()) {
                    try {
                        song.setMode(Mode.valueOf(mode).id);
                    } catch (IllegalArgumentException ignored) {
                        // Local song lookup supplies mode for known charts.
                    }
                }
                String level = limited(entry.path("level").asText("-"), 32).trim();
                song.setTableLevel(TableData.parseDisplayLevel(level));
                levels.computeIfAbsent(level.isEmpty() ? "-" : level, ignored -> new ArrayList<>())
                        .add(song);
                allSongs.add(song);
            }
        }
        if (levels.isEmpty()) {
            return null;
        }
        String aggregateFolder = limited(table.path("aggregate_folder").asText(""), 32).trim();
        if (!aggregateFolder.isEmpty()) {
            levels.remove(aggregateFolder);
            levels.put(aggregateFolder, new ArrayList<>(allSongs));
        }
        String symbol = limited(table.path("symbol").asText(""), 16).trim();
        TableData data = new TableData();
        data.setUrl(TABLE_URL);
        data.setName(limited(table.path("name").asText("BMS-IR My Difficulty Table"), 80));
        data.setTag(symbol);
        data.setFolder(levels.entrySet().stream().map(item -> {
            TableData.TableFolder folder = new TableData.TableFolder();
            folder.setName(symbol + item.getKey());
            folder.setSong(item.getValue().toArray(SongData[]::new));
            return folder;
        }).toArray(TableData.TableFolder[]::new));
        data.setCourse(CourseData.EMPTY);
        return data;
    }

    static void applyPendingIfSafe() {
        long targetSequence = pendingSequence;
        if (targetSequence == appliedSequence) {
            return;
        }
        MainController controller = main;
        if (controller == null
                || BMSIRArenaClient.isSelectionBlocked()
                || BMSIRArenaClient.isArenaPlayActive()
                || !(controller.getCurrentState() instanceof MusicSelector selector)) {
            return;
        }
        selector.getBarManager().replaceBmsirMyDifficultyTable(pendingTable);
        appliedSequence = targetSequence;
    }

    private static void submit(ObjectNode payload, long expectedSession, String successMessage) {
        submit(payload, expectedSession, successMessage, false, false);
    }

    private static void submit(
            ObjectNode payload,
            long expectedSession,
            String successMessage,
            boolean clearDraftOnSuccess,
            boolean retainDraftOnConflict
    ) {
        MainController controller = main;
        if (controller == null || expectedSession != SESSION.get()) {
            return;
        }
        if (BMSIRArenaClient.isSelectionBlocked() || BMSIRArenaClient.isArenaPlayActive()) {
            errorMessage = text(
                    "Arena進行中や結果待ちの間は難易度表を変更できません",
                    "My Difficulty Table cannot be changed during an Arena transition"
            );
            return;
        }
        synchronized (BMSIRMyTableClient.class) {
            if (requestRunning) {
                errorMessage = text("通信中です", "A request is already running");
                return;
            }
            requestRunning = true;
            errorMessage = "";
        }
        Thread worker = new Thread(() -> {
            try {
                Auth auth = auth(controller);
                payload.put("player_id", auth.playerId());
                payload.put("passmd5", auth.passmd5());
                Response response = post(controller, payload);
                if (expectedSession != SESSION.get() || controller != main) {
                    return;
                }
                JsonNode body = response.body();
                if (response.status() == 409 && body.path("current").path("ok").asBoolean(false)) {
                    acceptSnapshot(body.path("current"), expectedSession);
                    if (retainDraftOnConflict) {
                        DRAFT.markConflict();
                    }
                    errorMessage = "selection_required".equals(body.path("error").asText())
                            ? text("編集する難易度表を選んでください", "Select a table to edit")
                            : text(
                                    "Webまたは別のクライアントで更新されていたため、最新状態を再読み込みしました。内容を確認してもう一度保存してください",
                                    "The table changed elsewhere. The latest state was reloaded; review it and save again"
                            );
                    return;
                }
                if (response.status() < 200
                        || response.status() >= 300
                        || !body.path("ok").asBoolean(false)) {
                    errorMessage = errorText(body.path("error").asText("request_failed"), response.status());
                    return;
                }
                acceptSnapshot(body, expectedSession);
                if (clearDraftOnSuccess) {
                    DRAFT.clear();
                }
                statusMessage = successMessage;
            } catch (Exception error) {
                if (expectedSession == SESSION.get() && controller == main) {
                    logger.warn("My Difficulty Table request failed: {}", error.getMessage());
                    errorMessage = text(
                            "マイ難易度表サーバーへ接続できませんでした",
                            "Could not connect to the My Difficulty Table server"
                    );
                }
            } finally {
                if (expectedSession == SESSION.get() && controller == main) {
                    requestRunning = false;
                }
            }
        }, "bmsir-my-table");
        worker.setDaemon(true);
        worker.start();
    }

    private static void acceptSnapshot(JsonNode candidate, long expectedSession) throws IOException {
        String revision = candidate.path("revision").asText("").trim().toLowerCase(Locale.ROOT);
        JsonNode table = candidate.path("table");
        if (!("none".equals(revision) && table.isNull())
                && !(revision.matches("[0-9a-f]{64}") && table.isObject())) {
            throw new IOException("invalid owner table snapshot");
        }
        JsonNode accepted = candidate.deepCopy();
        TableData data = tableData(accepted);
        synchronized (BMSIRMyTableClient.class) {
            if (expectedSession != SESSION.get()) {
                return;
            }
            snapshot = accepted;
            pendingTable = data;
            pendingSequence++;
        }
        if (Gdx.app != null) {
            Gdx.app.postRunnable(BMSIRMyTableClient::applyPendingIfSafe);
        }
    }

    private static String currentRevision() {
        return snapshot.path("revision").asText("none").trim().toLowerCase(Locale.ROOT);
    }

    private static long currentTableId() {
        return selectedTableId(snapshot);
    }

    private static synchronized boolean rejectDraftLoss() {
        if (!DRAFT.hasChanges()) {
            return false;
        }
        errorMessage = text(
                "未保存の変更があります。先に一括保存するか、すべて破棄してください",
                "Pending changes exist; save them together or discard them first"
        );
        return true;
    }

    private static void reportStageResult(
            BMSIRMyTableDraft.StageResult result,
            String stagedMessage
    ) {
        switch (result) {
            case STAGED -> {
                statusMessage = stagedMessage;
                errorMessage = "";
            }
            case CLEARED -> {
                statusMessage = text(
                        "サーバーと同じ内容になったため保留を解除しました",
                        "Pending change cleared because it matches the server"
                );
                errorMessage = "";
            }
            case NO_CHANGE -> {
                statusMessage = text("変更はありません", "Nothing changed");
                errorMessage = "";
            }
            case FULL -> errorMessage = text(
                    "譜面の保留は64件までです。いったん保存してください",
                    "Up to 64 chart changes can be staged; save this batch first"
            );
            case WRONG_TABLE -> errorMessage = text(
                    "編集対象が変わりました。保留内容を保存または破棄してください",
                    "The edit target changed; save or discard the pending changes"
            );
        }
    }

    private static Response post(MainController controller, ObjectNode payload) throws IOException {
        URL url = BMSIRManiacApiClient.endpoint(
                controller.getPlayerConfig().getBmsirArenaServer(),
                API_PATH
        ).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "BMS-IR-Arena-oraja/" + Version.getArenaClientVersion());
        byte[] requestBody = JSON.writeValueAsBytes(payload);
        connection.setFixedLengthStreamingMode(requestBody.length);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBody);
            }
            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            JsonNode body;
            try (InputStream input = responseStream) {
                if (input == null) {
                    body = JSON.createObjectNode();
                } else {
                    byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                    if (bytes.length > MAX_RESPONSE_BYTES) {
                        throw new IOException("My Difficulty Table response is too large");
                    }
                    body = JSON.readTree(bytes);
                }
            }
            return new Response(status, body == null ? JSON.createObjectNode() : body);
        } finally {
            connection.disconnect();
        }
    }

    private static Auth auth(MainController controller) {
        IRConfig selected = bmsirConfig(controller);
        if (selected == null) {
            throw new IllegalStateException("BMS-IR configuration is missing");
        }
        int playerId = Integer.parseInt(selected.getUserid().trim());
        return new Auth(playerId, md5(selected.getPassword()));
    }

    private static IRConfig bmsirConfig(MainController controller) {
        if (controller == null || controller.getPlayerConfig() == null) {
            return null;
        }
        IRConfig[] configurations = controller.getPlayerConfig().getIrconfig();
        if (configurations == null) {
            return null;
        }
        for (IRConfig config : configurations) {
            if (config != null
                    && config.getIrname() != null
                    && config.getIrname().toLowerCase(Locale.ROOT).contains("bms")) {
                return config;
            }
        }
        return null;
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static String normalizedHash(String value, int length) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[0-9a-f]{" + length + "}") ? normalized : "";
    }

    private static String limited(String value, int maxLength) {
        String text = value == null ? "" : value;
        int codePoints = text.codePointCount(0, text.length());
        if (codePoints <= maxLength) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxLength));
    }

    private static String errorText(String error, int status) {
        return switch (error == null ? "" : error) {
            case "authentication_failed" -> text(
                    "BMS-IRのUser IDまたはパスワード設定を確認してください",
                    "Check the BMS-IR User ID and password settings"
            );
            case "rate_limited" -> text("しばらく待ってから再試行してください", "Wait before trying again");
            case "db_busy" -> text("サーバーが混雑しています。再試行してください", "The server is busy; try again");
            case "name_required" -> text("表名を入力してください", "Enter a table name");
            case "chart_hash_required" -> text("譜面ハッシュを取得できません", "The chart hash is unavailable");
            case "entry_not_found" -> text("対象譜面は表にありません", "The chart is not in the table");
            case "table_not_found" -> text("マイ難易度表が見つかりません", "My Difficulty Table was not found");
            case "selection_required" -> text("編集する難易度表を選んでください", "Select a table to edit");
            case "too_many_changes" -> text("一度に保存できる譜面変更は64件までです", "Up to 64 chart changes can be saved at once");
            case "duplicate_entry" -> text("同じ譜面の変更が重複しています", "The batch contains duplicate chart changes");
            case "changes_required" -> text("保存する変更がありません", "There are no changes to save");
            case "invalid_table_changes", "invalid_changes", "unsupported_change" -> text(
                    "保留内容を作り直してください",
                    "Recreate the pending changes"
            );
            default -> text("保存に失敗しました", "Save failed") + " (HTTP " + status + ")";
        };
    }

    private static JsonNode emptySnapshot() {
        ObjectNode empty = JSON.createObjectNode();
        empty.put("ok", true);
        empty.put("version", 1);
        empty.put("revision", "none");
        empty.putNull("table");
        empty.putArray("tables");
        empty.put("selected_table_id", 0);
        empty.put("selection_required", false);
        empty.put("can_create", true);
        empty.put("can_create_multiple", false);
        return empty;
    }

    private static String text(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    private record Auth(int playerId, String passmd5) {
    }

    private record Response(int status, JsonNode body) {
    }
}
