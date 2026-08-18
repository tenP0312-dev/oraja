package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.modmenu.FontAwesomeIcons;
import bms.player.beatoraja.modmenu.ImGuiRenderer;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.song.SongData;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Compact in-game control surface for the authenticated BMS-IR Arena socket.
 */
public final class BMSIRArenaOverlay {
    private static final int MAX_GRAPH_PLAYERS = 8;
    private static final int[] GRAPH_COLORS = {
            ImColor.rgb(101, 183, 255),
            ImColor.rgb(255, 164, 84),
            ImColor.rgb(121, 223, 139),
            ImColor.rgb(255, 211, 106),
            ImColor.rgb(197, 140, 255),
            ImColor.rgb(102, 226, 218),
            ImColor.rgb(255, 155, 208),
            ImColor.rgb(215, 224, 111)
    };
    private static final int GRAPH_BACKGROUND = ImColor.rgba(5, 8, 11, 230);
    private static final int GRAPH_TRACK = ImColor.rgba(255, 255, 255, 24);
    private static final int GRAPH_GUIDE = ImColor.rgba(220, 226, 232, 78);
    private static final int GRAPH_GUIDE_STRONG = ImColor.rgba(255, 255, 255, 135);
    private static final int GRAPH_TEXT = ImColor.rgb(238, 242, 246);
    private static final int GRAPH_TEXT_MUTED = ImColor.rgb(174, 184, 194);
    private static final int GRAPH_SELECTED = ImColor.rgb(255, 255, 255);
    private static final float VIEWPORT_MARGIN = 18.0f;
    private static final float GAMEPLAY_WINDOW_MIN_WIDTH = 260.0f;
    private static final float GAMEPLAY_WINDOW_MAX_WIDTH = 760.0f;
    private static final float GAMEPLAY_WINDOW_MIN_HEIGHT = 300.0f;
    private static final float GAMEPLAY_WINDOW_MAX_HEIGHT = 520.0f;
    private static final float GAMEPLAY_GRAPH_MIN_HEIGHT = 210.0f;
    private static final float MATCH_GRAPH_HEIGHT = 350.0f;
    private static final float GRAPH_PLOT_TOP_PADDING = 8.0f;
    private static final float GRAPH_LABEL_HEIGHT = 110.0f;

    private static boolean confirmWithdrawal;
    private static boolean confirmRoomDisband;
    private static boolean hotkeyCaptureActive;
    private static final Set<Integer> HOTKEY_CAPTURE_KEYS = new LinkedHashSet<>();
    private static final ImString CHAT_INPUT = new ImString(utf8BufferCapacity(200));
    private static final ImString LOBBY_CHAT_INPUT = new ImString(utf8BufferCapacity(200));
    private static final ImString PRIVATE_ROOM_CODE = new ImString(7);
    private static final ImString ROOM_NAME = new ImString(utf8BufferCapacity(40));
    private static final ImString ROOM_PASSWORD = new ImString(65);
    private static final ImString USER_TABLE_ID = new ImString(12);
    private static final ImString USER_TABLE_KEY = new ImString(96);
    // ImString capacity is UTF-8 bytes; reserve four bytes per server-side
    // Unicode code point so Japanese metadata is not cut mid-character.
    private static final ImString MY_TABLE_NAME = new ImString(utf8BufferCapacity(80));
    private static final ImString MY_TABLE_SYMBOL = new ImString(utf8BufferCapacity(16));
    private static final ImString MY_TABLE_DESCRIPTION = new ImString(utf8BufferCapacity(1000));
    private static final ImString MY_TABLE_LEVEL = new ImString(utf8BufferCapacity(32));
    private static final ImString MY_TABLE_COMMENT = new ImString(utf8BufferCapacity(200));
    private static final ImInt MY_TABLE_VISIBILITY = new ImInt(0);
    private static final ImInt MY_TABLE_SELECTION = new ImInt(0);
    private static String loadedMyTableRevision = "";
    private static long loadedMyTableId;
    private static String loadedMyTableChart = "";
    private static boolean createNewMyTable;
    private static boolean confirmMyTableEntryRemoval;
    private static boolean confirmMyTableDraftDiscard;
    private static boolean updateRoomPassword;
    private static String loadedRoomCode = "";
    private static final ImInt SCORE_RULE = new ImInt(0);
    private static final ImInt FORCED_GAUGE = new ImInt(0);
    private static final ImInt CHART_SCOPE = new ImInt(0);
    private static final ImInt RULESET_PROFILE = new ImInt(0);
    private static final ImInt NOMINATION_POLICY = new ImInt(0);
    private static final ImInt SERIES_FORMAT = new ImInt(0);
    private static final ImInt FIRST_TO_WINS = new ImInt(2);
    private static final ImInt NOMINATION_SECONDS = new ImInt(60);
    private static final ImInt OPTION_SECONDS = new ImInt(10);
    private static final ImInt INTERMISSION_SECONDS = new ImInt(0);
    private static final ImInt GRAPH_HIGHLIGHT = new ImInt(0);
    private static final ImInt TARGET_MODE = new ImInt(0);
    private static final ImInt GRAPH_ORDER = new ImInt(0);
    private static final ImInt LANGUAGE = new ImInt(0);
    private static final int[] ROOM_PLAY_MODES = {5, 7, 9, 10, 14};
    private static final Set<String> CUSTOM_LEVELS = new HashSet<>();
    private static final Map<Integer, String> USER_TABLE_KEYS =
            new LinkedHashMap<>();
    private static final String[] RULESET_PROFILES = {"LR2", "oraja"};

    private BMSIRArenaOverlay() {
    }

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    private static String f(String japanese, String english, Object... values) {
        return BMSIRArenaI18n.format(japanese, english, values);
    }

    private static String[] scoreRules() {
        return new String[]{
                "EX SCORE",
                t("BP Arena（CBのみ）", "BP Arena (CB only)"),
                "MAX COMBO"
        };
    }

    private static String[] forcedGauges() {
        return new String[]{t("自由", "Free"), "NORMAL", "HARD", "EXHARD", "HAZARD"};
    }

    private static String[] chartScopes() {
        return new String[]{
                t("通常＋発狂難易度表", "Standard + Insane tables"),
                t("自由選曲", "Free selection"),
                t("カスタム", "Custom")
        };
    }

    private static String[] nominationPolicies() {
        return new String[]{
                t("全員が選曲", "Everyone nominates"),
                t("部屋主だけ選曲", "Host only"),
                t("選曲担当を交代", "Rotate selector")
        };
    }

    private static String[] seriesFormats() {
        return new String[]{
                t("1曲", "Single"),
                t("全員の曲を回す", "All picks"),
                t("N本先取", "First to N")
        };
    }

    public static void render() {
        BMSIRMyTableClient.applyPendingIfSafe();
        if (!BMSIRArenaClient.shouldShowOverlay()) {
            ArenaPresentationController.update(
                    ArenaPresentationState.idle(),
                    null,
                    null
            );
            confirmWithdrawal = false;
            return;
        }
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config == null) {
            return;
        }
        BMSIRArenaLog.setDetailedEnabled(
                config.isBmsirArenaDetailedLogEnabled()
        );
        BMSIRArenaI18n.setLanguage(config.getBmsirArenaLanguage());
        ArenaPresentationController.update(
                BMSIRArenaClient.presentationState(),
                config,
                BMSIRArenaClient::playPresentationSound
        );
        if (config.isBmsirArenaPresentationOverlayEnabled()) {
            renderProminentArenaOverlay(
                    ArenaPresentationController.visibleState()
            );
        }
        if (config.getBmsirArenaOverlayMode() == 2) {
            return;
        }
        if (config.getBmsirArenaOverlayMode() == 1) {
            renderCompactOverlay();
            return;
        }
        if (BMSIRArenaClient.isGameplayState()
                || BMSIRArenaClient.isResultState()) {
            renderGameplayOverlay();
            return;
        }

        ImGui.setNextWindowPos(18, 72, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(680, 760, ImGuiCond.FirstUseEver);
        int flags = ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoBringToFrontOnFocus;
        if (!ImGui.begin(
                "BMS-IR Arena##main-" + currentLayoutKey(),
                flags
        )) {
            ImGui.end();
            return;
        }

        renderConnectionSummary();
        renderModeBanner();
        renderPhaseBanner(false);
        renderPinnedRoomReady(config);
        ImGui.separator();
        if (ImGui.beginChild("##bmsir-arena-scroll-content", 0, 0, false)
                && ImGui.beginTabBar("##bmsir-arena-tabs")) {
            if (ImGui.beginTabItem(t("対戦", "Battle"))) {
                renderQueueActions();
                ImGui.separator();
                renderMatch();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(t("公開ロビー／ルーム", "Lobby / Rooms"))) {
                renderRoomControls(config);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(t("難易度表編集", "Table Editor"))) {
                renderMyDifficultyTableEditor();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(FontAwesomeIcons.Trophy + t(" レートランキング", " Rating"))) {
                renderRanking();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(t("マニュアル", "Manual"))) {
                renderManual();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(t("設定", "Settings"))) {
                renderSettings(config);
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.endChild();
        ImGui.end();
    }

    private static void renderMyDifficultyTableEditor() {
        JsonNode snapshot = BMSIRMyTableClient.snapshotView();
        String revision = snapshot.path("revision").asText("none");
        JsonNode table = snapshot.path("table");
        boolean hasTable = table.isObject();
        JsonNode tables = snapshot.path("tables");
        long selectedTableId = BMSIRMyTableClient.selectedTableId(snapshot);
        boolean canCreate = snapshot.path("can_create").asBoolean(!hasTable);
        boolean canCreateMultiple = snapshot.path("can_create_multiple").asBoolean(false);
        if (!revision.equals(loadedMyTableRevision) || selectedTableId != loadedMyTableId) {
            setMyTableEditorFields(table, canCreateMultiple);
            setMyTableDraftFields(BMSIRMyTableClient.draftTableChange());
            loadedMyTableRevision = revision;
            loadedMyTableId = selectedTableId;
            loadedMyTableChart = "";
            createNewMyTable = false;
            confirmMyTableEntryRemoval = false;
            confirmMyTableDraftDiscard = false;
        }

        boolean busy = BMSIRMyTableClient.isBusy();
        boolean hasDraft = BMSIRMyTableClient.hasDraft();
        ImGui.textWrapped(t(
                "表情報や複数の譜面変更を保留し、最後にまとめて保存すると本体へ1回反映します。",
                "Stage table details and multiple chart changes, then save once for one in-game update."
        ));
        if (tables.isArray() && !tables.isEmpty()) {
            String[] tableOptions = new String[tables.size() + 1];
            long[] tableIds = new long[tables.size() + 1];
            tableOptions[0] = t("編集する表を選択", "Select a table to edit");
            int selectedIndex = 0;
            for (int index = 0; index < tables.size(); index++) {
                JsonNode option = tables.get(index);
                long optionId = option.path("id").asLong(0L);
                tableIds[index + 1] = optionId;
                tableOptions[index + 1] = f(
                        "%s (#%d / %d譜面)",
                        "%s (#%d / %d charts)",
                        option.path("name").asText(t("名称未設定", "Untitled")),
                        optionId,
                        option.path("entry_count").asInt(0)
                );
                if (optionId == selectedTableId) {
                    selectedIndex = index + 1;
                }
            }
            if (!busy) {
                MY_TABLE_SELECTION.set(selectedIndex);
            }
            ImGui.beginDisabled(busy);
            if (ImGui.combo(
                    t("編集対象", "Edit target") + "##my-table-selection",
                    MY_TABLE_SELECTION,
                    tableOptions
            )) {
                int index = MY_TABLE_SELECTION.get();
                if (index > 0 && index < tableIds.length) {
                    createNewMyTable = false;
                    BMSIRMyTableClient.selectTable(tableIds[index]);
                }
            }
            ImGui.endDisabled();
            if (BMSIRMyTableClient.selectionRequired(snapshot)) {
                ImGui.textDisabled(t(
                        "複数の表があります。編集対象を明示的に選んでください。",
                        "Multiple tables are available. Choose the edit target explicitly."
                ));
            }
        }
        ImGui.beginDisabled(busy);
        if (ImGui.button(t("サーバーから再読み込み", "Reload from server"))) {
            BMSIRMyTableClient.requestSnapshot();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (busy) {
            ImGui.textDisabled(t("通信中…", "Working..."));
        } else if (hasTable) {
            ImGui.textDisabled(f(
                    "%d譜面 / revision %s",
                    "%d charts / revision %s",
                    table.path("entries").size(),
                    shortRevision(revision)
            ));
        }

        if (canCreateMultiple) {
            ImGui.sameLine();
            ImGui.beginDisabled(busy || hasDraft);
            if (!createNewMyTable) {
                if (ImGui.button(t("新しい表を作成", "Create another table"))) {
                    createNewMyTable = true;
                    setMyTableEditorFields(null, true);
                    loadedMyTableChart = "";
                    confirmMyTableEntryRemoval = false;
                }
            } else if (ImGui.button(t("新規作成をやめる", "Cancel new table"))) {
                createNewMyTable = false;
                setMyTableEditorFields(table, true);
                loadedMyTableChart = "";
                confirmMyTableEntryRemoval = false;
            }
            ImGui.endDisabled();
        }

        ImGui.separator();
        boolean noOwnedTables = !tables.isArray() || tables.isEmpty();
        boolean creatingTable = createNewMyTable || (!hasTable && noOwnedTables && canCreate);
        boolean showTableForm = hasTable || creatingTable;
        if (showTableForm) {
            inputTextWithIme(t("表名", "Table name"), "my-table-name", MY_TABLE_NAME, 80);
            inputTextWithIme(t("記号", "Symbol"), "my-table-symbol", MY_TABLE_SYMBOL, 16);
            inputTextWithIme(t("説明", "Description"), "my-table-description", MY_TABLE_DESCRIPTION, 1000);
            ImGui.combo(
                    t("公開範囲", "Visibility") + "##my-table-visibility",
                    MY_TABLE_VISIBILITY,
                    new String[]{
                            t("非公開", "Private"),
                            t("限定公開", "Unlisted"),
                            t("公開", "Public")
                    }
            );
            boolean tableActionDisabled = busy || MY_TABLE_NAME.get().isBlank();
            ImGui.beginDisabled(tableActionDisabled);
            if (creatingTable) {
                if (ImGui.button(t("作成して本体へ反映", "Create and apply"))) {
                    BMSIRMyTableClient.createTable(
                            MY_TABLE_NAME.get(),
                            MY_TABLE_SYMBOL.get(),
                            MY_TABLE_DESCRIPTION.get(),
                            visibilityValue(MY_TABLE_VISIBILITY.get())
                    );
                }
            } else if (ImGui.button(t("表情報の変更を保留", "Stage table details"))) {
                BMSIRMyTableClient.stageTableUpdate(
                        MY_TABLE_NAME.get(),
                        MY_TABLE_SYMBOL.get(),
                        MY_TABLE_DESCRIPTION.get(),
                        visibilityValue(MY_TABLE_VISIBILITY.get())
                );
            }
            ImGui.endDisabled();
        } else {
            ImGui.textDisabled(t(
                    "編集対象の表を選ぶと、表情報と譜面編集を表示します。",
                    "Choose a table to show its details and chart editor."
            ));
        }

        ImGui.separator();
        ImGui.text(t("選択中の譜面", "Selected chart"));
        boolean editEntries = hasTable && !creatingTable;
        boolean levelEditable = table.path("level_editable").asBoolean(true);
        SongData selectedSong = BMSIRMyTableClient.selectedSong();
        String selectedKey = BMSIRMyTableClient.chartKey(selectedSong);
        JsonNode selectedEntry = editEntries
                ? BMSIRMyTableClient.entryFor(snapshot, selectedSong)
                : null;
        BMSIRMyTableDraft.EntryChange pendingEntry = editEntries
                ? BMSIRMyTableClient.draftEntryFor(selectedSong)
                : null;
        String editorChartState = revision + ":" + selectedKey + ":"
                + BMSIRMyTableClient.draftGeneration();
        if (!editorChartState.equals(loadedMyTableChart)) {
            if (pendingEntry != null && !pendingEntry.removal()) {
                MY_TABLE_LEVEL.set(pendingEntry.level());
                MY_TABLE_COMMENT.set(pendingEntry.comment());
            } else if (selectedEntry != null) {
                MY_TABLE_LEVEL.set(selectedEntry.path("level").asText(""));
                MY_TABLE_COMMENT.set(selectedEntry.path("comment").asText(""));
            } else {
                MY_TABLE_LEVEL.set(
                        selectedSong != null && selectedSong.getLevel() > 0
                                ? Integer.toString(selectedSong.getLevel())
                                : ""
                );
                MY_TABLE_COMMENT.set("");
            }
            loadedMyTableChart = editorChartState;
            confirmMyTableEntryRemoval = false;
        }
        if (selectedSong == null || selectedKey.isBlank()) {
            ImGui.textDisabled(t(
                    "Music Selectで譜面を選ぶと、ここから追加・更新できます。",
                    "Select a chart in Music Select to add or update it here."
            ));
        } else {
            ImGui.textWrapped(selectedSong.getFullTitle());
            ImGui.textDisabled(selectedKey);
            ImGui.beginDisabled(!levelEditable);
            inputTextWithIme(t("レベル", "Level"), "my-table-level", MY_TABLE_LEVEL, 32);
            ImGui.endDisabled();
            if (!levelEditable) {
                ImGui.textDisabled(t(
                        "この表のレベルはマスター表から同期されます。",
                        "Levels in this table are synchronized from its master table."
                ));
            }
            inputTextWithIme(t("コメント", "Comment"), "my-table-comment", MY_TABLE_COMMENT, 200);
            ImGui.beginDisabled(
                    !editEntries || busy || (levelEditable && MY_TABLE_LEVEL.get().isBlank())
            );
            if (ImGui.button(selectedEntry == null && pendingEntry == null
                    ? t("表への追加を保留", "Stage addition")
                    : t("登録内容の変更を保留", "Stage chart changes"))) {
                BMSIRMyTableClient.stageEntry(
                        selectedSong,
                        MY_TABLE_LEVEL.get(),
                        MY_TABLE_COMMENT.get()
                );
            }
            ImGui.endDisabled();
            if (pendingEntry != null && pendingEntry.removal()) {
                ImGui.sameLine();
                ImGui.textDisabled(t("削除を保留中", "Removal staged"));
                ImGui.sameLine();
                ImGui.beginDisabled(busy);
                if (ImGui.button(t("削除保留を取り消す", "Undo removal") + "##selected-chart")) {
                    BMSIRMyTableClient.undoDraftEntry(pendingEntry.key());
                }
                ImGui.endDisabled();
            } else if (selectedEntry != null || pendingEntry != null) {
                ImGui.sameLine();
                boolean pendingAddition = selectedEntry == null && pendingEntry != null;
                if (pendingAddition) {
                    ImGui.beginDisabled(busy);
                    if (ImGui.button(t("追加保留を取り消す", "Undo addition"))) {
                        BMSIRMyTableClient.stageRemoval(selectedSong);
                    }
                    ImGui.endDisabled();
                } else if (!confirmMyTableEntryRemoval) {
                    ImGui.beginDisabled(busy);
                    if (ImGui.button(t("表から削除", "Remove"))) {
                        confirmMyTableEntryRemoval = true;
                    }
                    ImGui.endDisabled();
                } else {
                    ImGui.textDisabled(t("削除を保留しますか？", "Stage removal?"));
                    if (ImGui.button(t("削除を保留", "Stage removal"))) {
                        BMSIRMyTableClient.stageRemoval(selectedSong);
                        confirmMyTableEntryRemoval = false;
                    }
                    ImGui.sameLine();
                    if (ImGui.button(t("キャンセル", "Cancel") + "##my-table-remove")) {
                        confirmMyTableEntryRemoval = false;
                    }
                }
            }
        }

        if (hasDraft) {
            ImGui.separator();
            ImGui.text(f(
                    "未保存: 表情報 %d件 / 譜面 %d件",
                    "Pending: %d table details / %d charts",
                    BMSIRMyTableClient.hasDraftTableChange() ? 1 : 0,
                    BMSIRMyTableClient.draftEntryCount()
            ));
            if (BMSIRMyTableClient.hasDraftTableChange()) {
                ImGui.textWrapped(t("表情報: 追加・更新", "Table details: update"));
                ImGui.beginDisabled(busy);
                if (ImGui.button(t("取り消す", "Undo") + "##draft-table")) {
                    BMSIRMyTableClient.undoDraftTableChange();
                    setMyTableEditorFields(table, canCreateMultiple);
                }
                ImGui.endDisabled();
            }
            List<BMSIRMyTableDraft.EntryChange> pending = BMSIRMyTableClient.draftEntries();
            if (!pending.isEmpty()) {
                if (ImGui.beginChild(
                        "##my-table-pending-list",
                        0,
                        Math.min(180.0f, 32.0f * pending.size() + 8.0f),
                        true
                )) {
                    for (BMSIRMyTableDraft.EntryChange change : pending) {
                        ImGui.textWrapped((change.removal()
                                ? t("削除: ", "Remove: ")
                                : t("追加・更新: ", "Add/update: ")) + change.title());
                        ImGui.beginDisabled(busy);
                        if (ImGui.button(t("取り消す", "Undo") + "##draft-" + change.key())) {
                            BMSIRMyTableClient.undoDraftEntry(change.key());
                        }
                        ImGui.endDisabled();
                    }
                }
                ImGui.endChild();
            }
            if (BMSIRMyTableClient.draftConflicted()) {
                ImGui.textWrapped(t(
                        "Webまたは別クライアントの更新と競合しました。最新状態と保留一覧を確認してください。",
                        "The table changed elsewhere. Review the latest state and pending list."
                ));
                ImGui.beginDisabled(busy);
                if (ImGui.button(t(
                        "最新状態を基準にして保留内容を再採用",
                        "Rebase pending changes on latest"
                ))) {
                    BMSIRMyTableClient.rebaseDraft();
                }
                ImGui.endDisabled();
            }
            ImGui.beginDisabled(busy || BMSIRMyTableClient.draftConflicted());
            if (ImGui.button(f(
                    "%d件を一括保存して本体へ反映",
                    "Save %d pending changes and apply",
                    BMSIRMyTableClient.draftCount()
            ))) {
                BMSIRMyTableClient.applyChanges();
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            ImGui.beginDisabled(busy);
            if (!confirmMyTableDraftDiscard) {
                if (ImGui.button(t("すべて破棄", "Discard all"))) {
                    confirmMyTableDraftDiscard = true;
                }
            } else {
                ImGui.textDisabled(t("本当に破棄しますか？", "Discard all pending changes?"));
                ImGui.sameLine();
                if (ImGui.button(t("破棄を確定", "Confirm discard"))) {
                    BMSIRMyTableClient.discardDraft();
                    setMyTableEditorFields(table, canCreateMultiple);
                    loadedMyTableChart = "";
                    confirmMyTableDraftDiscard = false;
                }
                ImGui.sameLine();
                if (ImGui.button(t("キャンセル", "Cancel") + "##draft-discard")) {
                    confirmMyTableDraftDiscard = false;
                }
            }
            ImGui.endDisabled();
            ImGui.textDisabled(t(
                    "保留内容はクライアント終了時に破棄されます。表の切替・再読み込み前に保存または破棄してください。",
                    "Drafts are lost when the client exits. Save or discard before switching or reloading."
            ));
        }

        String error = BMSIRMyTableClient.errorMessage();
        String status = BMSIRMyTableClient.statusMessage();
        if (!error.isBlank()) {
            ImGui.separator();
            ImGui.textWrapped(t("エラー: ", "Error: ") + error);
        } else if (!status.isBlank()) {
            ImGui.separator();
            ImGui.textDisabled(status);
        }
        ImGui.textDisabled(t(
                "貼り付け一括登録・並び順・My Dan／コース編集はWeb版を利用してください。空の表は最初の保存後に本体へ表示されます。",
                "Use the Web editor for pasted bulk import, ordering, and My Dan/courses. An empty table appears after its first saved chart."
        ));
    }

    private static void setMyTableEditorFields(JsonNode table, boolean systemDefault) {
        if (table != null && table.isObject()) {
            MY_TABLE_NAME.set(table.path("name").asText(""));
            MY_TABLE_SYMBOL.set(table.path("symbol").asText(""));
            MY_TABLE_DESCRIPTION.set(table.path("description").asText(""));
            MY_TABLE_VISIBILITY.set(
                    visibilityIndex(table.path("visibility").asText("private"))
            );
            return;
        }
        MY_TABLE_NAME.set(t("マイ難易度表", "My Difficulty Table"));
        MY_TABLE_SYMBOL.set("");
        MY_TABLE_DESCRIPTION.set("");
        MY_TABLE_VISIBILITY.set(systemDefault ? 1 : 0);
    }

    private static void setMyTableDraftFields(BMSIRMyTableDraft.TableChange change) {
        if (change == null) {
            return;
        }
        MY_TABLE_NAME.set(change.name());
        MY_TABLE_SYMBOL.set(change.symbol());
        MY_TABLE_DESCRIPTION.set(change.description());
        MY_TABLE_VISIBILITY.set(visibilityIndex(change.visibility()));
    }

    private static int visibilityIndex(String visibility) {
        return switch (visibility == null ? "" : visibility) {
            case "public" -> 2;
            case "unlisted" -> 1;
            default -> 0;
        };
    }

    private static String visibilityValue(int index) {
        return switch (index) {
            case 2 -> "public";
            case 1 -> "unlisted";
            default -> "private";
        };
    }

    private static String shortRevision(String revision) {
        return revision == null || revision.length() < 8
                ? String.valueOf(revision)
                : revision.substring(0, 8);
    }

    private static void renderProminentArenaOverlay(
            ArenaPresentationState state
    ) {
        if (state == null || !state.isActive()) {
            return;
        }
        ImGui.setNextWindowPos(
                ImGuiRenderer.windowWidth / 2.0f,
                Math.max(90.0f, ImGuiRenderer.windowHeight * 0.2f),
                ImGuiCond.FirstUseEver,
                0.5f,
                0.5f
        );
        ImGui.setNextWindowBgAlpha(0.93f);
        int flags = ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoNav;
        if (!ImGui.begin(
                "BMS-IR Arena##bmsir-arena-presentation-" + currentLayoutKey(),
                flags
        )) {
            ImGui.end();
            return;
        }
        int color = switch (state.phase()) {
            case COUNTDOWN -> "START!".equals(state.title())
                    ? ImColor.rgb(85, 227, 161)
                    : ImColor.rgb(255, 190, 98);
            case SONG_SELECTION, OPTION_SELECT ->
                    ImColor.rgb(85, 165, 255);
            case LOADING -> ImColor.rgb(154, 175, 198);
            default -> ImColor.rgb(237, 244, 252);
        };
        ImGui.setWindowFontScale(
                state.phase() == ArenaPresentationState.Phase.COUNTDOWN
                        ? 3.0f
                        : 2.0f
        );
        ImGui.textColored(color, state.title());
        ImGui.setWindowFontScale(1.0f);
        if (!state.detail().isBlank()) {
            ImGui.textWrapped(state.detail());
        }
        if (state.phase() != ArenaPresentationState.Phase.COUNTDOWN
                && state.secondsRemaining() > 0L) {
            ImGui.textColored(
                    phaseCountdownColor(state.secondsRemaining()),
                    phaseCountdownText(state.secondsRemaining())
            );
        }
        if (state.requiredCount() > 0
                && state.phase() != ArenaPresentationState.Phase.LOADING) {
            ImGui.text(
                    t("準備完了 ", "READY ")
                            + state.readyCount()
                            + " / "
                            + state.requiredCount()
            );
        }
        ImGui.end();
    }

    private static void renderPinnedRoomReady(PlayerConfig config) {
        String status = BMSIRArenaClient.queueStatus();
        if (!"private".equals(BMSIRArenaClient.currentMatchMode())
                || !("queued".equals(status)
                        || "reserved".equals(status)
                        || "matched".equals(status))
                || !BMSIRArenaClient.isRoomParticipating()) {
            return;
        }
        boolean ready = BMSIRArenaClient.isRoomReady();
        float width = Math.max(220.0f, ImGui.getContentRegionAvailX() * 0.55f);
        ImGui.beginDisabled(!BMSIRArenaClient.isConnected());
        if (ImGui.button(
                ready ? t("準備OKを解除", "Cancel Ready") : t("準備OK", "Ready"),
                width,
                52.0f
        )) {
            BMSIRArenaClient.requestRoomReady(!ready);
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImBoolean always = new ImBoolean(config.isBmsirArenaAlwaysReady());
        if (ImGui.checkbox(t("ずっとOKにする", "Always Ready"), always)) {
            config.setBmsirArenaAlwaysReady(always.get());
            if (always.get() && !ready) {
                BMSIRArenaClient.requestRoomReady(true);
            }
        }
    }

    public static void toggleVisibility() {
        setVisible(isHidden());
    }

    public static void setVisible(boolean visible) {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config == null) {
            return;
        }
        int current = config.getBmsirArenaOverlayMode();
        if (visible) {
            restoreVisibility();
        } else if (current != 2) {
            config.setBmsirArenaOverlayMode(2);
        }
    }

    public static boolean isHidden() {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        return config != null && config.getBmsirArenaOverlayMode() == 2;
    }

    public static void restoreVisibility() {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config != null) {
            config.setBmsirArenaOverlayMode(restoredVisibleMode(
                    config.getBmsirArenaLastVisibleOverlayMode()
            ));
        }
    }

    static int restoredVisibleMode(int previousVisibleMode) {
        return previousVisibleMode == 1 ? 1 : 0;
    }

    private static void renderCompactOverlay() {
        boolean gameplay = BMSIRArenaClient.isGameplayState();
        String id = gameplay
                ? gameplayWindowId(
                        true,
                        BMSIRArenaClient.currentPlayModeForLayout()
                )
                : "##compact-select-" + currentLayoutKey();
        ImGui.setNextWindowPos(18, 72, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(300, 150, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowBgAlpha(0.88f);
        int flags = ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoNav;
        if (!ImGui.begin("BMS-IR Arena" + id, flags)) {
            ImGui.end();
            return;
        }
        renderModeBanner();
        renderPhaseBanner(true);
        if (BMSIRArenaClient.isFillWaiting()) {
            ImGui.text(String.format(
                    Locale.ROOT,
                    "%d / %d人",
                    BMSIRArenaClient.fillPlayerCount(),
                    BMSIRArenaClient.fillMaxPlayers()
            ));
        } else if (BMSIRArenaClient.currentMatchView().isObject()) {
            List<JsonNode> players = sortedPlayers(
                    BMSIRArenaClient.currentMatchView()
            );
            JsonNode own = players.stream()
                    .filter(player -> player.path("player_id").asInt()
                            == BMSIRArenaClient.currentPlayerId())
                    .findFirst()
                    .orElse(null);
            if (own != null) {
                ImGui.text(ruleMetricLabel(
                        BMSIRArenaClient.currentScoreRule(),
                        own
                ));
            }
        }
        String roomCode = BMSIRArenaClient.currentRoomCode();
        if (!roomCode.isBlank()) {
            ImGui.textDisabled(t("部屋 ", "ROOM ") + roomCode);
        }
        if (!gameplay && ImGui.button(t("通常表示へ", "Full View"))) {
            BMSIRArenaClient.playerConfig().setBmsirArenaOverlayMode(0);
        }
        ImGui.end();
    }

    private static void renderGameplayOverlay() {
        JsonNode match = BMSIRArenaClient.currentMatchView();
        boolean hasMatch = match.isObject() && match.size() > 0;
        if (!hasMatch) {
            renderGameplayStatusOverlay();
            return;
        }

        renderGameplayInfoOverlay(match);
        renderGameplayGraphOverlay(match);
    }

    private static void renderGameplayInfoOverlay(JsonNode match) {
        float width = Math.min(
                410.0f,
                maximumGameplayWindowWidth(ImGuiRenderer.windowWidth)
        );
        ImGui.setNextWindowPos(
                VIEWPORT_MARGIN,
                VIEWPORT_MARGIN,
                ImGuiCond.FirstUseEver
        );
        ImGui.setNextWindowSize(width, 230.0f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowBgAlpha(0.88f);
        int flags = ImGuiWindowFlags.NoNav
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoBringToFrontOnFocus;
        if (!ImGui.begin(
                "BMS-IR Arena Status##gameplay-info-" + currentLayoutKey(),
                flags
        )) {
            ImGui.end();
            return;
        }
        String title = match.path("chart").path("title").asText();
        if (!title.isBlank()) {
            ImGui.textWrapped(title);
        }
        renderPhaseBanner(false);
        renderModeBanner();
        renderRatingResult(match);
        renderChatPreview();
        renderForceEndVote(match);
        ImGui.end();
    }

    private static void renderGameplayGraphOverlay(JsonNode match) {

        float windowWidth = defaultGameplayWindowWidth(ImGuiRenderer.windowWidth);
        float windowHeight = defaultGameplayWindowHeight(ImGuiRenderer.windowHeight);
        ImGui.setNextWindowPos(
                ImGuiRenderer.windowWidth / 2.0f,
                Math.max(VIEWPORT_MARGIN, ImGuiRenderer.windowHeight - VIEWPORT_MARGIN),
                ImGuiCond.FirstUseEver,
                0.5f,
                1.0f
        );
        ImGui.setNextWindowSize(windowWidth, windowHeight, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(
                Math.min(GAMEPLAY_WINDOW_MIN_WIDTH, maximumGameplayWindowWidth(ImGuiRenderer.windowWidth)),
                Math.min(GAMEPLAY_WINDOW_MIN_HEIGHT, maximumGameplayWindowHeight(ImGuiRenderer.windowHeight)),
                maximumGameplayWindowWidth(ImGuiRenderer.windowWidth),
                maximumGameplayWindowHeight(ImGuiRenderer.windowHeight)
        );
        ImGui.setNextWindowBgAlpha(0.88f);
        int flags = ImGuiWindowFlags.NoNav
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoBringToFrontOnFocus;
        if (!ImGui.begin(
                "Arena Score Graph##gameplay-graph-" + currentLayoutKey(),
                flags
        )) {
            ImGui.end();
            return;
        }
        renderScoreGraph(match, Math.max(GAMEPLAY_GRAPH_MIN_HEIGHT, ImGui.getContentRegionAvailY()));
        ImGui.end();
    }

    private static void renderRatingResult(JsonNode match) {
        if (!BMSIRArenaClient.isShowingCompletedResult()
                || !match.path("rated").asBoolean(false)) {
            return;
        }
        for (JsonNode player : match.path("players")) {
            if (player.path("player_id").asInt()
                    != BMSIRArenaClient.currentPlayerId()) {
                continue;
            }
            JsonNode beforeNode = player.hasNonNull("before")
                    ? player.path("before")
                    : player.path("rating_before");
            JsonNode afterNode = player.hasNonNull("after")
                    ? player.path("after")
                    : player.path("rating_after");
            JsonNode deltaNode = player.hasNonNull("delta")
                    ? player.path("delta")
                    : player.path("rating_delta");
            if (!afterNode.isNumber() || !deltaNode.isNumber()) {
                return;
            }
            double before = beforeNode.asDouble(afterNode.asDouble());
            double after = afterNode.asDouble();
            double delta = deltaNode.asDouble();
            int color = delta > 0.0001
                    ? ImColor.rgb(121, 223, 139)
                    : delta < -0.0001
                            ? ImColor.rgb(255, 115, 115)
                            : GRAPH_TEXT_MUTED;
            ImGui.textColored(
                    color,
                    String.format(
                            Locale.ROOT,
                            "RATE %.2f -> %.2f (%+.2f)",
                            before,
                            after,
                            delta
                    )
            );
            return;
        }
    }

    private static void renderGameplayStatusOverlay() {
        boolean filling = BMSIRArenaClient.isFillWaiting();
        float width = Math.min(400.0f, maximumGameplayWindowWidth(ImGuiRenderer.windowWidth));
        ImGui.setNextWindowPos(
                ImGuiRenderer.windowWidth / 2.0f,
                Math.max(VIEWPORT_MARGIN, ImGuiRenderer.windowHeight - VIEWPORT_MARGIN),
                ImGuiCond.FirstUseEver,
                0.5f,
                1.0f
        );
        ImGui.setNextWindowSize(
                width,
                filling ? 150.0f : 120.0f,
                ImGuiCond.FirstUseEver
        );
        ImGui.setNextWindowBgAlpha(0.88f);
        int flags = ImGuiWindowFlags.NoNav
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoBringToFrontOnFocus;
        if (!ImGui.begin(
                "BMS-IR Arena##gameplay-status-" + currentLayoutKey(),
                flags
        )) {
            ImGui.end();
            return;
        }
        renderModeBanner();
        renderPhaseBanner(true);
        if (filling) {
            ImGui.text(String.format(
                    Locale.ROOT,
                    "%d / %d人",
                    BMSIRArenaClient.fillPlayerCount(),
                    BMSIRArenaClient.fillMaxPlayers()
            ));
            ImGui.textDisabled(t(
                    "この待機中の退出はレート・戦績に影響しません",
                    "Leaving during this wait does not affect rating or records"
            ));
        }
        ImGui.end();
    }

    private static void renderPhaseBanner(boolean compact) {
        String action = BMSIRArenaClient.currentPhaseAction();
        if (!action.isBlank()) {
            ImGui.setWindowFontScale(compact ? 1.15f : 1.4f);
            ImGui.textWrapped(action);
            ImGui.setWindowFontScale(1.0f);
        }
        if (BMSIRArenaClient.currentPhaseHasCountdown()) {
            long seconds = BMSIRArenaClient.currentPhaseSecondsRemaining();
            ImGui.setWindowFontScale(compact ? 1.15f : 1.35f);
            ImGui.textColored(
                    phaseCountdownColor(seconds),
                    phaseCountdownText(seconds)
            );
            ImGui.setWindowFontScale(1.0f);
        }
        String playMode = BMSIRArenaClient.currentMatchPlayModeLabel();
        if (!playMode.isBlank()) {
            ImGui.setWindowFontScale(compact ? 1.05f : 1.25f);
            ImGui.textColored(ImColor.rgb(121, 223, 139), playMode);
            ImGui.setWindowFontScale(1.0f);
        }
        ImGui.textColored(
                ImColor.rgb(121, 223, 139),
                "LN MODE: " + BMSIRArenaClient.currentLongnoteMode()
        );
        renderSeriesBanner(compact);
    }

    private static void renderSeriesBanner(boolean compact) {
        String format = BMSIRArenaClient.currentSeriesFormat();
        if ("single".equals(format)) {
            return;
        }
        JsonNode rules = BMSIRArenaClient.rulesView();
        int remaining = rules.path("series_remaining_charts").asInt(-1);
        StringBuilder summary = new StringBuilder(
                seriesFormatLabel(format, BMSIRArenaClient.currentFirstToWins())
        );
        summary.append(t(" / 第", " / Round "))
                .append(Math.max(1, BMSIRArenaClient.currentSeriesRound()))
                .append(t("曲", ""));
        if (remaining >= 0) {
            summary.append(t(" / 残り", " / "))
                    .append(remaining)
                    .append(t("曲", " remaining"));
        }
        ImGui.setWindowFontScale(compact ? 1.0f : 1.15f);
        ImGui.textColored(ImColor.rgb(255, 211, 106), summary.toString());
        ImGui.setWindowFontScale(1.0f);

        JsonNode players = BMSIRArenaClient.currentMatchView().path("players");
        if (!players.isArray() || players.isEmpty()) {
            return;
        }
        List<String> standings = new ArrayList<>();
        for (JsonNode player : players) {
            String name = player.path("name").asText(
                    Integer.toString(player.path("player_id").asInt())
            );
            if ("bo2".equals(format)) {
                int total = player.path("series_max_exscore_total").asInt();
                String rate = total > 0
                        ? String.format(
                                Locale.ROOT,
                                "%.2f%%",
                                player.path("series_exscore_total").asDouble()
                                        * 100.0
                                        / total
                        )
                        : "-";
                int placement = player.path("series_placement").asInt();
                standings.add(
                        name
                                + " "
                                + player.path("series_points").asInt()
                                + t("pt / 参考EX率 ", "pt / EX rate ")
                                + rate
                                + (placement > 0
                                        ? t(" / 総合", " / #") + placement + t("位", "")
                                        : "")
                );
            } else {
                standings.add(
                        name
                                + " "
                                + player.path("series_wins").asInt()
                                + t("勝", " wins")
                );
            }
        }
        ImGui.textWrapped(
                ("bo2".equals(format)
                        ? t("BO2総合: ", "BO2 standings: ")
                        : t("戦績: ", "Record: "))
                        + String.join(" / ", standings)
        );
    }

    static String phaseCountdownText(long seconds) {
        return String.format(
                Locale.ROOT,
                t("残り %02d秒", "%02d sec remaining"),
                Math.max(0L, seconds)
        );
    }

    static int phaseCountdownColor(long seconds) {
        if (seconds <= 5L) {
            return ImColor.rgb(255, 115, 115);
        }
        if (seconds <= 10L) {
            return ImColor.rgb(255, 211, 106);
        }
        return ImColor.rgb(106, 169, 255);
    }

    static float defaultGameplayWindowWidth(int viewportWidth) {
        return Math.min(
                maximumGameplayWindowWidth(viewportWidth),
                Math.min(
                        GAMEPLAY_WINDOW_MAX_WIDTH,
                        Math.max(GAMEPLAY_WINDOW_MIN_WIDTH, viewportWidth * 0.5f)
                )
        );
    }

    static String gameplayWindowId(boolean compact, int playMode) {
        return (compact ? "##compact-play-" : "##gameplay-")
                + BMSIRArenaClient.playModeLayoutKey(playMode);
    }

    static String gameplayWindowId(boolean compact, boolean doublePlay) {
        return (compact ? "##compact-play-" : "##gameplay-")
                + (doublePlay ? "dp" : "sp");
    }

    private static String currentLayoutKey() {
        return BMSIRArenaClient.playModeLayoutKey(
                BMSIRArenaClient.currentPlayModeForLayout()
        );
    }

    static float defaultGameplayWindowHeight(int viewportHeight) {
        return Math.min(
                maximumGameplayWindowHeight(viewportHeight),
                Math.min(
                        GAMEPLAY_WINDOW_MAX_HEIGHT,
                        Math.max(GAMEPLAY_WINDOW_MIN_HEIGHT, viewportHeight * 0.55f)
                )
        );
    }

    private static float maximumGameplayWindowWidth(int viewportWidth) {
        return Math.max(1.0f, viewportWidth - VIEWPORT_MARGIN * 2.0f);
    }

    private static float maximumGameplayWindowHeight(int viewportHeight) {
        return Math.max(1.0f, viewportHeight - VIEWPORT_MARGIN * 2.0f);
    }

    private static void renderScoreGraph(JsonNode match, float height) {
        PlayerConfig graphConfig = BMSIRArenaClient.playerConfig();
        String graphOrder = graphConfig == null
                ? PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_RANK
                : graphConfig.getBmsirArenaGraphOrder();
        boolean fixedEntryOrder =
                PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY.equals(graphOrder);
        List<JsonNode> players = scoreGraphPlayers(match, graphOrder).stream()
                .limit(MAX_GRAPH_PLAYERS)
                .toList();
        if (players.isEmpty()) {
            ImGui.textDisabled(t("参加者を待っています", "Waiting for players"));
            return;
        }

        int totalNotes = Math.max(1, match.path("chart").path("totalnotes").asInt());
        String scoreRule = match.path("rules").path("score_rule").asText(
                BMSIRArenaClient.currentScoreRule()
        );
        ImGui.text(ruleBattleTitle(scoreRule));
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        float width = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float axisWidth = width < 360.0f ? 34.0f : 44.0f;
        float plotLeft = originX + axisWidth;
        float plotRight = originX + width;
        float plotTop = originY + GRAPH_PLOT_TOP_PADDING;
        float plotHeight = scorePlotHeight(height);
        float plotBottom = plotTop + plotHeight;
        float plotWidth = Math.max(1.0f, plotRight - plotLeft);
        ImDrawList drawList = ImGui.getWindowDrawList();
        boolean highlightSelf = graphConfig != null
                && graphConfig.getBmsirArenaGraphHighlight() == 1;
        int ownPlayerId = BMSIRArenaClient.currentPlayerId();
        JsonNode ownPlayer = players.stream()
                .filter(player -> player.path("player_id").asInt()
                        == ownPlayerId)
                .findFirst()
                .orElse(null);
        int ownBattleValue = ownPlayer == null
                ? 0
                : battleValue(scoreRule, ownPlayer, totalNotes);
        int highlightedPlayerId = highlightSelf && ownPlayerId > 0
                ? ownPlayerId
                : players.get(0).path("player_id").asInt();

        drawList.addRectFilled(plotLeft, plotTop, plotRight, plotBottom, GRAPH_BACKGROUND);
        if ("exscore".equals(scoreRule)) {
            drawGuide(drawList, "MAX", 1.0, plotLeft, plotRight, plotTop, plotHeight, true);
            drawGuide(drawList, "AAA", 8.0 / 9.0, plotLeft, plotRight, plotTop, plotHeight, true);
            drawGuide(drawList, "AA", 7.0 / 9.0, plotLeft, plotRight, plotTop, plotHeight, false);
            drawGuide(drawList, "A", 2.0 / 3.0, plotLeft, plotRight, plotTop, plotHeight, false);
        } else if ("minbp".equals(scoreRule)) {
            drawGuide(drawList, "BEST", 1.0, plotLeft, plotRight, plotTop, plotHeight, true);
        } else {
            drawGuide(drawList, "FC", 1.0, plotLeft, plotRight, plotTop, plotHeight, true);
        }

        float columnWidth = plotWidth / players.size();
        for (int index = 0; index < players.size(); index++) {
            JsonNode player = players.get(index);
            int battleValue = battleValue(scoreRule, player, totalNotes);
            int battleMaximum = battleMaximum(scoreRule, player, totalNotes);
            int placement = player.path("placement").asInt(index + 1);
            double rate = player.path("battle_rate").isNumber()
                    ? Math.max(
                            0.0,
                            Math.min(1.0, player.path("battle_rate").asDouble())
                    )
                    : battleRate(battleValue, battleMaximum);
            float centerX = plotLeft + columnWidth * (index + 0.5f);
            float barWidth = Math.max(10.0f, Math.min(48.0f, columnWidth * 0.58f));
            float barLeft = centerX - barWidth / 2.0f;
            float barRight = centerX + barWidth / 2.0f;
            float barTop = scoreBarTop(plotTop, plotBottom, rate);
            int playerId = player.path("player_id").asInt();
            boolean own = playerId == ownPlayerId;
            int colorIndex = scoreGraphColorIndex(
                    match,
                    player,
                    index,
                    graphOrder
            );
            int color = own && !fixedEntryOrder
                    ? ImColor.rgb(255, 80, 80)
                    : GRAPH_COLORS[colorIndex % GRAPH_COLORS.length];
            boolean selected = playerId == highlightedPlayerId;

            drawList.addRectFilled(barLeft, plotTop, barRight, plotBottom, GRAPH_TRACK);
            drawList.addRectFilled(barLeft, barTop, barRight, plotBottom, color);
            drawList.addRect(
                    barLeft - (selected ? 2.0f : 0.0f),
                    Math.max(plotTop, barTop - (selected ? 2.0f : 0.0f)),
                    barRight + (selected ? 2.0f : 0.0f),
                    plotBottom,
                    selected ? GRAPH_SELECTED : color,
                    0.0f,
                    0,
                    selected ? 2.0f : 1.0f
            );
            drawCenteredText(
                    drawList,
                    ruleMetricLabel(scoreRule, player),
                    centerX,
                    Math.max(plotTop + 2.0f, barTop - ImGui.getTextLineHeight() - 3.0f),
                    columnWidth - 4.0f,
                    GRAPH_TEXT
            );

            float labelY = plotBottom + 6.0f;
            drawCenteredText(drawList, "#" + placement, centerX, labelY, columnWidth, color);
            drawCenteredText(
                    drawList,
                    graphPlayerName(player),
                    centerX,
                    labelY + 18.0f,
                    columnWidth - 4.0f,
                    own && !fixedEntryOrder ? ImColor.rgb(255, 115, 115)
                            : selected ? GRAPH_SELECTED : GRAPH_TEXT
            );
            int difference = battleValue - ownBattleValue;
            String rateAndDifference = (
                    "exscore".equals(scoreRule)
                            ? String.format(Locale.ROOT, "%.2f%%", rate * 100.0)
                            : String.format(Locale.ROOT, "%.1f%%", rate * 100.0)
            ) + String.format(Locale.ROOT, " / %+,d", difference);
            drawCenteredText(
                    drawList,
                    rateAndDifference,
                    centerX,
                    labelY + 36.0f,
                    columnWidth - 4.0f,
                    GRAPH_TEXT_MUTED
            );
            drawCenteredText(
                    drawList,
                    "OP " + player.path("play_option_label").asText("-"),
                    centerX,
                    labelY + 54.0f,
                    columnWidth - 4.0f,
                    GRAPH_TEXT_MUTED
            );
            String lamp = graphClearLabel(player);
            if (!lamp.isBlank()) {
                drawCenteredText(
                        drawList,
                        lamp,
                        centerX,
                        labelY + 72.0f,
                        columnWidth - 4.0f,
                        GRAPH_TEXT_MUTED
                );
            }
            if (player.has("series_points")) {
                drawCenteredText(
                        drawList,
                        player.path("series_points").asInt() + " Points",
                        centerX,
                        labelY + 90.0f,
                        columnWidth - 4.0f,
                        GRAPH_TEXT_MUTED
                );
            }
        }
        ImGui.dummy(width, height);
    }

    private static void drawGuide(
            ImDrawList drawList,
            String label,
            double rate,
            float plotLeft,
            float plotRight,
            float plotTop,
            float plotHeight,
            boolean strong
    ) {
        float y = plotTop + (float) (1.0 - rate) * plotHeight;
        int color = strong ? GRAPH_GUIDE_STRONG : GRAPH_GUIDE;
        drawList.addLine(plotLeft, y, plotRight, y, color, strong ? 1.5f : 1.0f);
        drawList.addText(
                plotLeft - ImGui.calcTextSizeX(label) - 7.0f,
                y - ImGui.getTextLineHeight() / 2.0f,
                color,
                label
        );
    }

    private static void drawCenteredText(
            ImDrawList drawList,
            String value,
            float centerX,
            float y,
            float maxWidth,
            int color
    ) {
        String text = fitText(value, maxWidth);
        drawList.addText(centerX - ImGui.calcTextSizeX(text) / 2.0f, y, color, text);
    }

    private static String fitText(String value, float maxWidth) {
        String text = value == null || value.isBlank() ? "-" : value;
        if (maxWidth <= 0.0f || ImGui.calcTextSizeX(text) <= maxWidth) {
            return text;
        }
        String suffix = "..";
        while (!text.isEmpty() && ImGui.calcTextSizeX(text + suffix) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text.isEmpty() ? suffix : text + suffix;
    }

    static double scoreRate(int exscore, int totalNotes) {
        if (totalNotes <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, exscore / (totalNotes * 2.0)));
    }

    static int battleValue(String scoreRule, JsonNode player, int totalNotes) {
        if (player.has("battle_value")) {
            return Math.max(0, player.path("battle_value").asInt());
        }
        return switch (scoreRule) {
            case "minbp" ->
                    Math.max(0, totalNotes - player.path("minbp").asInt());
            case "max_combo" -> Math.max(0, player.path("max_combo").asInt());
            default -> Math.max(0, player.path("exscore").asInt());
        };
    }

    static int battleMaximum(String scoreRule, JsonNode player, int totalNotes) {
        if (player.has("battle_max")) {
            return Math.max(1, player.path("battle_max").asInt());
        }
        return "exscore".equals(scoreRule)
                ? Math.max(1, totalNotes * 2)
                : Math.max(1, totalNotes);
    }

    static double battleRate(int value, int maximum) {
        if (maximum <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value / (double) maximum));
    }

    private static void renderForceEndVote(JsonNode match) {
        JsonNode forceEnd = match.path("force_end");
        if (!forceEnd.path("available").asBoolean(false)) {
            return;
        }
        int required = forceEnd.path("required_player_ids").size();
        int votes = forceEnd.path("vote_player_ids").size();
        boolean voted = false;
        for (JsonNode playerId : forceEnd.path("vote_player_ids")) {
            if (playerId.asInt() == BMSIRArenaClient.currentPlayerId()) {
                voted = true;
                break;
            }
        }
        ImGui.beginDisabled(voted || !BMSIRArenaClient.isConnected());
        if (ImGui.button(
                voted
                        ? t("曲終了に投票済み", "End vote sent") + " (" + votes + "/" + required + ")"
                        : t("この曲を終了する", "End this chart") + " (" + votes + "/" + required + ")"
        )) {
            BMSIRArenaClient.requestForceEndVote();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.textDisabled(t("残っている全員の同意で終了", "Ends when every remaining player agrees"));
    }

    static String ruleMetricLabel(String scoreRule, JsonNode player) {
        return switch (scoreRule) {
            case "minbp" -> "CB " + Math.max(0, player.path("minbp").asInt());
            case "max_combo" ->
                    "COMBO " + Math.max(0, player.path("max_combo").asInt());
            default -> "EX " + Math.max(0, player.path("exscore").asInt());
        };
    }

    static String ruleBattleTitle(String scoreRule) {
        return switch (scoreRule) {
            case "minbp" -> t("コンボ切れ最少勝負", "LOWEST COMBO BREAK WINS");
            case "max_combo" -> t("最大コンボ対決", "MAX COMBO BATTLE");
            default -> t("EXスコア対決", "EX SCORE BATTLE");
        };
    }

    static float scorePlotHeight(float graphHeight) {
        return Math.max(
                1.0f,
                graphHeight - GRAPH_PLOT_TOP_PADDING - GRAPH_LABEL_HEIGHT
        );
    }

    static float scoreBarTop(float plotTop, float plotBottom, double rate) {
        double clampedRate = Math.max(0.0, Math.min(1.0, rate));
        return Math.max(
                plotTop,
                Math.min(
                        plotBottom,
                        plotBottom
                                - (float) clampedRate
                                * Math.max(0.0f, plotBottom - plotTop)
                )
        );
    }

    private static String graphClearLabel(JsonNode player) {
        String finalState = player.path("final_state").asText();
        if ("forfeit".equals(finalState)) {
            return "DNF";
        }
        if (!player.path("finished").asBoolean(false) && finalState.isBlank()) {
            return "";
        }
        return player.path("clear_label").asText("");
    }

    private static void renderConnectionSummary() {
        String connection = BMSIRArenaClient.isConnected()
                ? FontAwesomeIcons.Wifi + t(" 接続中", " Connected")
                : FontAwesomeIcons.Ban + t(" 再接続中", " Reconnecting");
        ImGui.text(connection);
        ImGui.sameLine();
        ImGui.text(String.format(
                Locale.ROOT,
                t("R %d  /  %d戦", "R %d / %d matches"),
                Math.round(BMSIRArenaClient.arenaRating()),
                BMSIRArenaClient.arenaMatchesPlayed()
        ));
        String message = BMSIRArenaClient.arenaUiMessage();
        if (!message.isBlank()) {
            ImGui.textWrapped(message);
        }
    }

    private static void renderModeBanner() {
        String mode = BMSIRArenaClient.currentMatchMode();
        int color = switch (mode) {
            case "casual" -> ImColor.rgb(101, 207, 145);
            case "private" -> ImColor.rgb(211, 164, 255);
            default -> ImColor.rgb(106, 169, 255);
        };
        ImGui.textColored(color, modeDisplayText(mode));
        ImGui.sameLine();
        ImGui.textDisabled(
                "| " + rulesetProfileLabel(BMSIRArenaClient.currentRulesetProfile()) + t("仕様", " rules")
        );
        if ("cpu_bonus".equals(BMSIRArenaClient.currentRatingPolicy())) {
            ImGui.textDisabled(
                    t(
                            "CPU戦: A～MAX固定 / 勝利 +1 / 敗北 -1 / 同点 ±0",
                            "CPU match: A-MAX fixed / win +1 / loss -1 / draw 0"
                    )
            );
        }
    }

    static String modeDisplayText(String mode) {
        return switch (mode) {
            case "casual" -> t("カジュアル  |  レート変動なし", "Casual | Unrated");
            case "private" -> t("プライベート  |  レート変動なし", "Private | Unrated");
            default -> t("レートArena  |  レート変動あり", "Rated Arena | Rating enabled");
        };
    }

    private static void renderQueueActions() {
        String status = BMSIRArenaClient.queueStatus();
        boolean connected = BMSIRArenaClient.isConnected();
        boolean filling = BMSIRArenaClient.isFillWaiting();
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        boolean entryActive = "queued".equals(status)
                || "reserved".equals(status)
                || "matched".equals(status)
                || "withdraw_requested".equals(status);
        if (config != null && !entryActive) {
            ImBoolean allowCpu = new ImBoolean(config.isBmsirArenaAllowCpu());
            if (ImGui.checkbox(t("1人待機中のCPU戦を許可", "Allow CPU match while waiting alone"), allowCpu)) {
                config.setBmsirArenaAllowCpu(allowCpu.get());
            }
            ImGui.sameLine();
            ImGui.textDisabled(
                    allowCpu.get()
                            ? t("人間1人でもCPU3人で開始", "Start with three CPUs when alone")
                            : t("互換性のある人間を待機", "Wait for a compatible human")
            );
            ImBoolean allowHigherSelection = new ImBoolean(
                    config.isBmsirArenaAllowHigherSelection()
            );
            if (ImGui.checkbox(
                    t("高レート基準の選曲を許可", "Allow higher-rating chart limits"),
                    allowHigherSelection
            )) {
                config.setBmsirArenaAllowHigherSelection(
                        allowHigherSelection.get()
                );
            }
            ImGui.textDisabled(
                    t(
                            "ONなら自分の解放済み上限で部屋の選曲上限を下げません",
                            "ON prevents your unlocked limit from lowering the room limit"
                    )
            );
        } else if (entryActive) {
            ImGui.textDisabled(
                    BMSIRArenaClient.currentQueueAllowsCpu()
                            ? t("1人CPU戦: 許可", "Solo CPU match: enabled")
                            : t("1人CPU戦: 無効（互換相手待機）", "Solo CPU match: disabled")
            );
            ImGui.textDisabled(
                    BMSIRArenaClient.currentQueueAllowsHigherSelection()
                            ? t("高レート基準の選曲: 許可", "Higher-rating chart limits: enabled")
                            : t("選曲上限: 自分の解放済み上限を反映", "Chart limit uses your unlocked maximum")
            );
        }
        if (confirmWithdrawal) {
            ImGui.text(
                    filling
                            ? t("このマッチから抜けますか？", "Leave this match?")
                            : t("この対戦を棄権しますか？", "Forfeit this match?")
            );
            if (filling) {
                ImGui.textDisabled(t("レート・戦績には影響しません", "Rating and history are unaffected"));
            }
            ImGui.beginDisabled(!connected);
            if (ImGui.button(
                    filling
                            ? FontAwesomeIcons.TimesCircle + t(" マッチから抜ける", " Leave match")
                            : FontAwesomeIcons.StopCircle + t(" 棄権する", " Forfeit")
            )) {
                BMSIRArenaClient.requestQueueCancel();
                confirmWithdrawal = false;
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button(FontAwesomeIcons.Times + t(" 戻る", " Back"))) {
                confirmWithdrawal = false;
            }
            return;
        }

        if ("queued".equals(status)) {
            ImGui.beginDisabled(!connected);
            if (ImGui.button(FontAwesomeIcons.TimesCircle + t(" 待機を解除", " Leave queue"))) {
                BMSIRArenaClient.requestQueueCancel();
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            refreshButton(connected);
            return;
        }
        if ("reserved".equals(status) || "matched".equals(status)) {
            ImGui.beginDisabled(!connected);
            if (ImGui.button(
                    filling
                            ? FontAwesomeIcons.TimesCircle + t(" マッチから抜ける", " Leave match")
                            : FontAwesomeIcons.StopCircle + t(" 対戦を棄権", " Forfeit")
            )) {
                confirmWithdrawal = true;
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            refreshButton(connected);
            return;
        }
        if ("withdraw_requested".equals(status)) {
            ImGui.beginDisabled();
            ImGui.button(
                    FontAwesomeIcons.UserClock
                            + (filling
                                    ? t(" 退出処理中", " Leaving")
                                    : t(" 棄権処理中", " Forfeiting"))
            );
            ImGui.endDisabled();
            ImGui.sameLine();
            refreshButton(connected);
            return;
        }

        ImGui.beginDisabled(!connected);
        if (ImGui.button(FontAwesomeIcons.SignInAlt + t(" エントリー", " Enter queue"))) {
            BMSIRArenaClient.requestQueueEntry();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        refreshButton(connected);
    }

    private static void refreshButton(boolean connected) {
        ImGui.beginDisabled(!connected);
        if (ImGui.button(FontAwesomeIcons.SyncAlt + "##arena-refresh")) {
            BMSIRArenaClient.requestArenaStatus();
        }
        ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(t("Arena状態を更新", "Refresh Arena state"));
        }
    }

    private static void renderMatch() {
        renderPhaseBanner(false);
        ImGui.separator();
        if (renderNomination()) {
            return;
        }
        boolean filling = renderFillWaiting();
        JsonNode match = BMSIRArenaClient.currentMatchView();
        if (!match.isObject() || match.size() == 0) {
            if (!filling) {
                ImGui.textDisabled(t("現在の試合はありません", "No active match"));
            }
            return;
        }
        if (filling) {
            ImGui.separator();
            ImGui.textDisabled(t("前回の対戦結果", "Previous result"));
        }
        JsonNode chart = match.path("chart");
        String level = chart.path("level").asText();
        String title = chart.path("title").asText(t("選曲中", "Selecting chart"));
        ImGui.textWrapped((level.isBlank() ? "" : level + "  ") + title);
        ImGui.textDisabled(
                BMSIRArenaClient.isShowingCompletedResult()
                        ? "RESULT"
                        : match.path("state").asText("MATCH").toUpperCase(Locale.ROOT)
        );
        if (BMSIRArenaClient.isShowingCompletedResult()) {
            renderRatingChange(match);
            if (ImGui.button(FontAwesomeIcons.Times + t(" 結果を閉じる", " Close result"))) {
                BMSIRArenaClient.dismissResult();
                return;
            }
        }
        if (!"ranked".equals(BMSIRArenaClient.currentMatchMode())) {
            ImGui.textDisabled(
                    scoreRuleLabel(BMSIRArenaClient.currentScoreRule())
                            + " / "
                            + gaugeLabel(BMSIRArenaClient.currentForcedGauge())
                            + " / UNRATED"
            );
        }
        if (renderOptionSelection()) {
            return;
        }

        List<JsonNode> players = sortedPlayers(match);
        if (players.isEmpty()) {
            ImGui.textDisabled(t("参加者を待っています", "Waiting for players"));
            return;
        }
        if (ImGui.collapsingHeader(t("リアルタイムグラフ", "Live score graph"))) {
            renderScoreGraph(match, MATCH_GRAPH_HEIGHT);
        }
        ImGui.separator();

        int tableFlags = ImGuiTableFlags.Borders
                | ImGuiTableFlags.RowBg
                | ImGuiTableFlags.ScrollY;
        if (ImGui.beginTable(
                "##bmsir-arena-match",
                6,
                tableFlags,
                0,
                Math.min(260, 28 + players.size() * 28)
        )) {
            ImGui.tableSetupColumn("#");
            ImGui.tableSetupColumn("Player");
            ImGui.tableSetupColumn(
                    switch (BMSIRArenaClient.currentScoreRule()) {
                        case "minbp" -> "CB";
                        case "max_combo" -> "MAX COMBO";
                        default -> "EX";
                    }
            );
            ImGui.tableSetupColumn("OP");
            ImGui.tableSetupColumn("Lamp");
            ImGui.tableSetupColumn("Rate");
            ImGui.tableHeadersRow();
            for (int index = 0; index < players.size(); index++) {
                JsonNode player = players.get(index);
                int serverPlacement = player.path("placement").asInt(0);
                ImGui.tableNextRow();
                tableText(Integer.toString(
                        serverPlacement > 0 ? serverPlacement : index + 1
                ));
                String name = player.path("name").asText(
                        Integer.toString(player.path("player_id").asInt())
                );
                if (!player.path("connected").asBoolean(true)
                        && !player.path("finished").asBoolean(false)) {
                    name += t("（再接続待ち）", " (reconnecting)");
                }
                if (player.path("player_id").asInt() == BMSIRArenaClient.currentPlayerId()) {
                    name = "> " + name;
                }
                tableText(name);
                tableText(Integer.toString(ruleMetric(player)));
                tableText(player.path("play_option_label").asText("-"));
                tableText(clearLabel(player));
                if (player.hasNonNull("after")) {
                    tableText(String.format(
                            Locale.ROOT,
                            "%d (%+.1f)",
                            Math.round(player.path("after").asDouble()),
                            player.path("delta").asDouble()
                    ));
                } else {
                    tableText("-");
                }
            }
            ImGui.endTable();
        }
    }

    private static boolean renderOptionSelection() {
        if (!BMSIRArenaClient.isOptionSelectionOpen()) {
            return false;
        }
        ImGui.text(t("現在: ", "Current: ") + BMSIRArenaClient.currentOptionLabel());
        ImGui.textDisabled(t(
                "H-RANDOMなどのアシスト系OPは使用できません",
                "Assist options such as H-RANDOM are unavailable"
        ));
        if (
                BMSIRArenaClient.isForceHostOption()
                        && !BMSIRArenaClient.isCurrentRoomHost()
        ) {
            ImGui.textColored(
                    ImColor.rgb(255, 211, 106),
                    t(
                            "部屋主の左右OP・FLIPが全員へ適用されます",
                            "The host's left/right options and FLIP apply to everyone"
                    )
            );
        }
        ImGui.textDisabled(String.format(
                Locale.ROOT,
                "READY %d / %d",
                BMSIRArenaClient.optionReadyCount(),
                BMSIRArenaClient.optionPlayerCount()
        ));
        ImGui.beginDisabled(BMSIRArenaClient.isOptionReadySent());
        if (ImGui.button(
                BMSIRArenaClient.isOptionReadySent()
                        ? t("準備完了", "Ready")
                        : t("このOPで準備完了", "Ready with these options")
        )) {
            BMSIRArenaClient.requestOptionReady();
        }
        ImGui.endDisabled();
        ImGui.textDisabled(t(
                "操作がなければ時間切れ時のOPで自動確定します",
                "The current options are confirmed automatically at timeout"
        ));
        return true;
    }

    private static boolean renderFillWaiting() {
        if (!BMSIRArenaClient.isFillWaiting()) {
            return false;
        }
        ImGui.text(f(
                "現在 %d / %d人",
                "%d / %d players",
                BMSIRArenaClient.fillPlayerCount(),
                BMSIRArenaClient.fillMaxPlayers()
        ));
        for (String player : fillWaitingPlayerLabels(
                BMSIRArenaClient.waitingPlayersView()
        )) {
            ImGui.bulletText(player);
        }
        ImGui.textWrapped(
                t(
                        "この待機中はマッチから抜けても、レート・戦績に影響しません。",
                        "Leaving during this wait does not affect rating or match history."
                )
        );
        return true;
    }

    /** Human participants shown while a reserved match is still filling. */
    static List<String> fillWaitingPlayerLabels(JsonNode match) {
        List<String> labels = new ArrayList<>();
        JsonNode players = match != null && match.isArray()
                ? match
                : match == null ? null : match.path("players");
        if (players == null || !players.isArray()) {
            return labels;
        }
        for (JsonNode player : players) {
            if (player.path("cpu").asBoolean(false)
                    || player.path("is_cpu").asBoolean(false)) {
                continue;
            }
            String name = player.path("name").asText("").trim();
            if (name.isEmpty()) {
                name = Integer.toString(player.path("player_id").asInt());
            }
            long rating = Math.round(player.path("rating_exact").asDouble(
                    player.path("rating").asDouble(0.0)
            ));
            String rank = firstText(player, "dan", "rank", "dan_label", "rank_label");
            String state = firstText(player, "waiting_status", "queue_status", "status");
            if (state.isEmpty()) {
                state = t("待機中", "waiting");
            } else {
                state = waitingStateLabel(state);
            }
            labels.add(name + " / R " + rating
                    + (rank.isEmpty() ? "" : " / " + rank)
                    + " / " + state);
        }
        return labels;
    }

    private static String waitingStateLabel(String state) {
        return switch (state) {
            case "waiting", "queued" -> t("待機中", "waiting");
            case "ready" -> t("準備完了", "ready");
            case "disconnected" -> t("切断中", "disconnected");
            case "arena_off" -> t("Arena無効", "Arena disabled");
            default -> state;
        };
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static boolean renderNomination() {
        JsonNode nomination = BMSIRArenaClient.nominationView();
        if (!nomination.isObject() || nomination.size() == 0) {
            return false;
        }
        if (nomination.has("nominations")) {
            renderRevealedNominations(nomination);
            return true;
        }
        if (!BMSIRArenaClient.isNominationOpen()) {
            return false;
        }

        int targetBand = nomination.path("target_band").asInt(1);
        boolean freeSelection = "free".equals(
                nomination.path("chart_scope").asText(
                        BMSIRArenaClient.currentChartScope()
                )
        );
        boolean customSelection = "custom".equals(
                nomination.path("chart_scope").asText(
                        BMSIRArenaClient.currentChartScope()
                )
        );
        boolean canNominate = nomination.path("can_nominate").asBoolean(true);
        int submittedCount = nomination.path("submitted_count").asInt();
        int requiredCount = nomination.path("required_count").asInt(1);
        boolean quotaComplete = requiredCount <= 0
                || submittedCount >= requiredCount;
        ImGui.spacing();
        ImGui.text(
                freeSelection
                        ? t("選曲可能: 所持している任意の単曲譜面", "Available: any owned single chart")
                        : customSelection
                                ? t("選曲可能: ルームのカスタム難易度表", "Available: room custom tables")
                                : t("選曲可能: ☆1～", "Available: ☆1 to ")
                                        + BMSIRArenaClient.arenaBandLabel(targetBand)
        );
        if (!freeSelection && !customSelection) {
            double referenceRating = nomination.path("reference_rating")
                    .asDouble(1000.0);
            ImGui.textDisabled(f(
                    "基準レート %.0f / 上限 %s",
                    "Reference rating %.0f / limit %s",
                    referenceRating,
                    BMSIRArenaClient.arenaBandLabel(targetBand)
            ));
        }
        ImGui.separator();
        if (requiredCount > 0) {
            ImGui.text(t("選曲進捗: ", "Nominations: ") + submittedCount + " / " + requiredCount);
        }

        SongData current = BMSIRArenaClient.currentNominationSong();
        if (current != null) {
            ImGui.textWrapped(current.getTitle());
            String artist = current.getArtist();
            if (artist != null && !artist.isBlank()) {
                ImGui.textDisabled(artist);
            }
        } else {
            ImGui.textDisabled(t("選択中の楽曲譜面なし", "No chart selected"));
        }
        ImGui.beginDisabled(
                !canNominate
                        || quotaComplete
                        || !BMSIRArenaClient.isConnected()
                        || current == null
        );
        if (ImGui.button(FontAwesomeIcons.Music + t(" この曲を選曲", " Nominate this chart"))) {
            BMSIRArenaClient.requestCurrentChartNomination();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        boolean canDelegate = !freeSelection
                || "single".equals(BMSIRArenaClient.currentSeriesFormat());
        ImGui.beginDisabled(
                !canNominate
                        || quotaComplete
                        || !canDelegate
                        || !BMSIRArenaClient.isConnected()
        );
        if (ImGui.button(FontAwesomeIcons.Dice + t(" 他の人に任せる", " Delegate selection"))) {
            BMSIRArenaClient.requestRandomNomination();
        }
        ImGui.endDisabled();
        if (!canDelegate) {
            ImGui.textDisabled(t(
                    "自由選曲の連戦では各自の重複しない選曲が必要です",
                    "Free-selection series require distinct picks from each player"
            ));
        }

        JsonNode own = nomination.path("your_nomination");
        JsonNode ownList = nomination.path("your_nominations");
        String ownSource = nomination.path("your_source").asText();
        if (own.isObject() && own.size() > 0) {
            ImGui.textWrapped(
                    t("登録済み: ", "Submitted: ")
                            + own.path("level").asText()
                            + "  "
                            + own.path("title").asText()
            );
        } else if ("server_random".equals(ownSource)) {
            ImGui.textDisabled(t("登録済み: サーバーランダム", "Submitted: server random"));
        }
        if (ownList.isArray() && ownList.size() > 1) {
            for (int index = 0; index < ownList.size(); index++) {
                JsonNode item = ownList.get(index);
                ImGui.textDisabled(
                        (index + 1)
                                + ". "
                                + item.path("level").asText()
                                + " "
                                + item.path("title").asText()
                );
            }
        }

        ImGui.separator();
        int flags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg;
        if (ImGui.beginTable("##bmsir-arena-nomination-status", 2, flags)) {
            ImGui.tableSetupColumn("Player");
            ImGui.tableSetupColumn("Status");
            ImGui.tableHeadersRow();
            for (JsonNode player : nomination.path("players")) {
                ImGui.tableNextRow();
                String name = player.path("name").asText(
                        Integer.toString(player.path("player_id").asInt())
                );
                if (player.path("player_id").asInt() == BMSIRArenaClient.currentPlayerId()) {
                    name = "> " + name;
                }
                tableText(name);
                int playerSubmitted = player.path("submitted_count").asInt(
                        player.path("submitted").asBoolean() ? 1 : 0
                );
                int playerRequired = player.path("required_count").asInt(1);
                tableText(
                        playerRequired <= 0
                                ? t("待機", "Waiting")
                                : playerSubmitted + " / " + playerRequired
                );
            }
            ImGui.endTable();
        }
        return true;
    }

    private static void renderRevealedNominations(JsonNode nomination) {
        JsonNode selectedChart = nomination.path("chart");
        int selectedPlayerId = nomination.path("selected_player_id").asInt();
        int rerollCount = nomination.path("reroll_count").asInt();
        ImGui.text(
                rerollCount > 0
                        ? FontAwesomeIcons.Random + t(" 再抽選結果", " Reroll result")
                        : FontAwesomeIcons.CheckCircle + t(" 抽選結果", " Selection result")
        );
        String selectedLevel = selectedChart.path("level").asText();
        String selectedTitle = selectedChart.path("title").asText();
        ImGui.textWrapped(
                (selectedLevel.isBlank() ? "" : selectedLevel + "  ")
                        + selectedTitle
        );
        String selectedArtist = selectedChart.path("artist").asText();
        if (!selectedArtist.isBlank()) {
            ImGui.textDisabled(selectedArtist);
        }
        ImGui.separator();

        int flags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg;
        if (ImGui.beginTable("##bmsir-arena-nominations", 3, flags)) {
            ImGui.tableSetupColumn("Player");
            ImGui.tableSetupColumn("Candidate");
            ImGui.tableSetupColumn("Source");
            ImGui.tableHeadersRow();
            for (JsonNode item : nomination.path("nominations")) {
                ImGui.tableNextRow();
                int playerId = item.path("player_id").asInt();
                String name = item.path("name").asText(Integer.toString(playerId));
                if (playerId == selectedPlayerId) {
                    name = "> " + name;
                }
                JsonNode chart = item.path("chart");
                String level = chart.path("level").asText();
                tableText(name);
                tableText(
                        (level.isBlank() ? "" : level + "  ")
                                + chart.path("title").asText()
                );
                tableText(
                        "player_selected".equals(item.path("source").asText())
                                ? "PLAYER"
                                : "RANDOM"
                );
            }
            ImGui.endTable();
        }
    }

    private static String clearLabel(JsonNode player) {
        if ("forfeit".equals(player.path("final_state").asText())) {
            return t("DNF（棄権）", "DNF (forfeit)");
        }
        return player.path("clear_label").asText("-");
    }

    private static int ruleMetric(JsonNode player) {
        return switch (BMSIRArenaClient.currentScoreRule()) {
            case "minbp" -> player.path("minbp").asInt();
            case "max_combo" -> player.path("max_combo").asInt();
            default -> player.path("exscore").asInt();
        };
    }

    private static String graphPlayerName(JsonNode player) {
        String name = player.path("name").asText(
                Integer.toString(player.path("player_id").asInt())
        );
        if (!player.path("connected").asBoolean(true)
                && !player.path("finished").asBoolean(false)) {
            return name + t("（再接続待ち）", " (reconnecting)");
        }
        return name;
    }

    static List<JsonNode> scoreGraphPlayers(JsonNode match, String graphOrder) {
        if (PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY.equals(graphOrder)) {
            List<JsonNode> players = new ArrayList<>();
            match.path("players").forEach(players::add);
            Map<Integer, Integer> order = scoreGraphEntryOrder(match);
            players.sort(
                    Comparator.comparingInt(
                            (JsonNode value) -> order.getOrDefault(
                                    value.path("player_id").asInt(),
                                    Integer.MAX_VALUE
                            )
                    ).thenComparingInt(value -> value.path("player_id").asInt())
            );
            return players;
        }
        return sortedPlayers(match);
    }

    static int scoreGraphColorIndex(
            JsonNode match,
            JsonNode player,
            int renderedIndex,
            String graphOrder
    ) {
        if (!PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY.equals(graphOrder)) {
            return Math.max(0, renderedIndex);
        }
        return Math.max(
                0,
                scoreGraphEntryOrder(match).getOrDefault(
                        player.path("player_id").asInt(),
                        renderedIndex
                )
        );
    }

    private static Map<Integer, Integer> scoreGraphEntryOrder(JsonNode match) {
        Map<Integer, Integer> order = new LinkedHashMap<>();
        int index = 0;
        for (JsonNode player : match.path("players")) {
            int playerId = player.path("player_id").asInt();
            int entryOrder = player.path("entry_order").asInt(index);
            order.putIfAbsent(playerId, Math.max(0, entryOrder));
            index++;
        }
        return order;
    }

    private static List<JsonNode> sortedPlayers(JsonNode match) {
        List<JsonNode> players = new ArrayList<>();
        match.path("players").forEach(players::add);
        String scoreRule = match.path("rules").path("score_rule").asText(
                BMSIRArenaClient.currentScoreRule()
        );
        Comparator<JsonNode> metric = switch (scoreRule) {
            case "minbp" -> Comparator.comparingInt(
                    value -> value.path("minbp").asInt()
            );
            case "max_combo" -> Comparator.comparingInt(
                    (JsonNode value) -> value.path("max_combo").asInt()
            ).reversed();
            default -> Comparator.comparingInt(
                    (JsonNode value) -> value.path("exscore").asInt()
            ).reversed();
        };
        players.sort(
                Comparator.comparing(
                        (JsonNode value) ->
                                "forfeit".equals(
                                        value.path("final_state").asText()
                                )
                )
                        .thenComparing(metric)
                        .thenComparingInt(
                                value -> value.path("player_id").asInt()
                        )
        );
        return players;
    }

    private static void renderRoomControls(PlayerConfig config) {
        String status = BMSIRArenaClient.queueStatus();
        String mode = BMSIRArenaClient.currentMatchMode();
        boolean inRoom = !"ranked".equals(mode)
                && ("queued".equals(status)
                        || "reserved".equals(status)
                        || "matched".equals(status)
                        || "withdraw_requested".equals(status));
        if (inRoom) {
            String roomCode = BMSIRArenaClient.currentRoomCode();
            if (!roomCode.equals(loadedRoomCode)) {
                loadedRoomCode = roomCode;
                ROOM_NAME.set(BMSIRArenaClient.currentRoomName());
                ROOM_PASSWORD.set("");
                updateRoomPassword = false;
                SCORE_RULE.set(switch (BMSIRArenaClient.currentScoreRule()) {
                    case "minbp" -> 1;
                    case "max_combo" -> 2;
                    default -> 0;
                });
                FORCED_GAUGE.set(switch (BMSIRArenaClient.currentForcedGauge()) {
                    case "normal" -> 1;
                    case "hard" -> 2;
                    case "exhard" -> 3;
                    case "hazard" -> 4;
                    default -> 0;
                });
                CHART_SCOPE.set(
                        switch (BMSIRArenaClient.currentChartScope()) {
                            case "free" -> 1;
                            case "custom" -> 2;
                            default -> 0;
                        }
                );
                loadRoomCustomConfiguration(BMSIRArenaClient.rulesView());
            }
            ImGui.text(
                    (BMSIRArenaClient.isSpectatorPublic()
                            ? t("公開ルーム", "Public room")
                            : t("コード限定ルーム", "Code-only room"))
                            + (BMSIRArenaClient.isCurrentRoomLocked()
                                    ? t(" [鍵あり]", " [locked]")
                                    : t(" [鍵なし]", " [unlocked]"))
            );
            if (!BMSIRArenaClient.currentRoomName().isBlank()) {
                ImGui.textWrapped(BMSIRArenaClient.currentRoomName());
            }
            if (!roomCode.isBlank()) {
                ImGui.text(t("部屋コード: ", "Room code: ") + roomCode);
                ImGui.sameLine();
                if (ImGui.smallButton(t("コピー", "Copy") + "##current-room-code")) {
                    ImGui.setClipboardText(roomCode);
                    ImGuiNotify.info(t("部屋コードをコピーしました", "Room code copied"), 2500);
                }
            }
            ImGui.text(t("勝敗: ", "Scoring: ") + scoreRuleLabel(BMSIRArenaClient.currentScoreRule()));
            ImGui.text(
                    t("試合形式: ", "Format: ")
                            + seriesFormatLabel(
                                    BMSIRArenaClient.currentSeriesFormat(),
                                    BMSIRArenaClient.currentFirstToWins()
                            )
            );
            if (!"single".equals(BMSIRArenaClient.currentSeriesFormat())) {
                ImGui.text(
                        f(
                                "第%d曲",
                                "Round %d",
                                BMSIRArenaClient.currentSeriesRound()
                        )
                );
            }
            ImGui.text(t("ゲージ: ", "Gauge: ") + gaugeLabel(BMSIRArenaClient.currentForcedGauge()));
            ImGui.text(t("判定・ゲージ仕様: ", "Ruleset: ") + rulesetProfileLabel(
                    BMSIRArenaClient.currentRulesetProfile()
            ));
            ImGui.text(
                    t("選曲: ", "Charts: ")
                            + (switch (BMSIRArenaClient.currentChartScope()) {
                                case "free" -> t("自由選曲", "Free selection");
                                case "custom" -> t("カスタム", "Custom");
                                default -> t("通常＋発狂難易度表", "Standard + Insane tables");
                            })
            );
            ImGui.textDisabled(t(
                    "ルーム対戦はレート・レート戦績に影響しません",
                    "Room matches do not affect rating or rated match history"
            ));
            ImBoolean participating = new ImBoolean(
                    BMSIRArenaClient.isRoomParticipating()
            );
            if (ImGui.checkbox(t("次のシリーズに参加する", "Join the next series"), participating)) {
                BMSIRArenaClient.requestRoomParticipation(participating.get());
            }
            if (BMSIRArenaClient.isRoomParticipationPending()) {
                ImGui.textColored(
                        ImColor.rgb(255, 211, 106),
                        t(
                                "参加ONは進行中シリーズ終了後から有効です",
                                "Participation starts after the current series"
                        )
                );
            }
            if (BMSIRArenaClient.isRoomPaused()) {
                ImGui.textColored(
                        ImColor.rgb(121, 223, 139),
                        t("休憩中（全員観戦）", "Paused (everyone spectating)")
                );
                ImGui.textDisabled(t(
                        "2人以上が参加ONになり、全員が準備OKで開始します",
                        "The match starts when 2+ participants are ready"
                ));
            }
            ImGui.textDisabled(t(
                    "上部の準備OKを全員が押すと選曲へ進みます",
                    "Chart selection starts when every participant is ready"
            ));
            ImBoolean stay = new ImBoolean(config.isBmsirArenaStayInRoom());
            if (ImGui.checkbox(t("対戦後もこの部屋に残る", "Stay in this room after matches"), stay)) {
                config.setBmsirArenaStayInRoom(stay.get());
                BMSIRArenaClient.requestRoomStay(stay.get());
            }
            if (BMSIRArenaClient.isCurrentRoomHost()
                    && ImGui.collapsingHeader(t("部屋主の詳細設定", "Host settings"))) {
                ImGui.separator();
                ImGui.text(t("変更すると全員の準備OKを解除します", "Changes clear every player's ready state"));
                inputTextWithIme(t("部屋名", "Room name"), "host-room-name", ROOM_NAME, 40);
                ImGui.combo(t("勝敗ルール", "Scoring"), SCORE_RULE, scoreRules());
                ImGui.combo(t("強制ゲージ", "Forced gauge"), FORCED_GAUGE, forcedGauges());
                ImGui.combo(t("選曲範囲", "Chart scope"), CHART_SCOPE, chartScopes());
                renderAllowedPlayModes();
                if (CHART_SCOPE.get() == 2) {
                    renderCustomRoomSelection();
                }
                renderRulesetProfileSetting(config, "##private-room-ruleset");
                renderPrivateRoomSettings(config);
                ImGui.inputText(
                        BMSIRArenaClient.isCurrentRoomLocked()
                                ? t("新しいパスワード（空欄で解除）", "New password (blank removes it)")
                                : t("新しいパスワード", "New password"),
                        ROOM_PASSWORD
                );
                ImBoolean passwordChange = new ImBoolean(updateRoomPassword);
                if (ImGui.checkbox(t("パスワードを更新", "Update password"), passwordChange)) {
                    updateRoomPassword = passwordChange.get();
                }
                ImGui.beginDisabled(!BMSIRArenaClient.isConnected());
                if (ImGui.button(t("設定を次の曲へ反映", "Apply settings to next chart"))) {
                    applyRoomCustomConfiguration();
                    BMSIRArenaClient.requestRoomSettings(
                            selectedScoreRule(),
                            selectedForcedGauge(),
                            selectedChartScope(),
                            ROOM_NAME.get(),
                            ROOM_PASSWORD.get(),
                            updateRoomPassword
                    );
                    updateRoomPassword = false;
                    ROOM_PASSWORD.set("");
                }
                ImGui.endDisabled();
                ImGui.sameLine();
                if (confirmRoomDisband) {
                    ImGui.textColored(
                            ImColor.rgb(255, 115, 115),
                            t("全員を退出させます", "This will remove every member")
                    );
                    if (ImGui.button(t("解体を確定", "Confirm disband"))) {
                        BMSIRArenaClient.requestRoomDisband();
                        confirmRoomDisband = false;
                    }
                    ImGui.sameLine();
                    if (ImGui.button(t("戻る", "Back") + "##cancel-disband")) {
                        confirmRoomDisband = false;
                    }
                } else if (ImGui.button(t("部屋を解体", "Disband room"))) {
                    confirmRoomDisband = true;
                }
            }
            renderRoomParticipants();
            ImGui.beginDisabled(!BMSIRArenaClient.isConnected());
            if (ImGui.button(t("この部屋から退出", "Leave this room"))) {
                BMSIRArenaClient.requestQueueCancel();
            }
            ImGui.endDisabled();
            ImGui.separator();
            renderMatch();
            ImGui.separator();
            ImGui.text(t("ルームチャット", "Room chat"));
            renderChat(true, 160);
            return;
        }
        confirmRoomDisband = false;
        loadedRoomCode = "";
        renderPublicRoomList();
        ImGui.separator();
        ImGui.text(t("ルームを作成／コードで参加", "Create or join by code"));
        ImGui.combo(t("勝敗ルール", "Scoring"), SCORE_RULE, scoreRules());
        ImGui.combo(t("強制ゲージ", "Forced gauge"), FORCED_GAUGE, forcedGauges());
        ImGui.combo(t("選曲範囲", "Chart scope"), CHART_SCOPE, chartScopes());
        renderAllowedPlayModes();
        if (CHART_SCOPE.get() == 2) {
            renderCustomRoomSelection();
        }
        renderRulesetProfileSetting(config, "##room-entry-ruleset");
        ImBoolean stay = new ImBoolean(config.isBmsirArenaStayInRoom());
        if (ImGui.checkbox(t("対戦後もこの部屋に残る", "Stay in this room after matches"), stay)) {
            config.setBmsirArenaStayInRoom(stay.get());
        }
        ImGui.textDisabled(t("このモードの対戦は常にunratedです", "This mode is always unrated"));

        ImGui.inputText(t("部屋コード", "Room code"), PRIVATE_ROOM_CODE);
        ImGui.sameLine();
        if (ImGui.button(t("貼り付け", "Paste") + "##private-room-code")) {
            String clipboard = ImGui.getClipboardText();
            PRIVATE_ROOM_CODE.set(
                    BMSIRArenaClient.normalizeRoomCode(clipboard)
            );
        }
        ImGui.textDisabled(t(
                "空欄で新規作成、6文字を入力すると既存ルームへ参加",
                "Leave blank to create, or enter six characters to join"
        ));
        if (PRIVATE_ROOM_CODE.get().isBlank()) {
            inputTextWithIme(t("部屋名", "Room name"), "new-room-name", ROOM_NAME, 40);
        } else {
            ImGui.inputText(t("パスワード", "Password"), ROOM_PASSWORD);
        }
        if (PRIVATE_ROOM_CODE.get().isBlank()) {
            ImGui.inputText(t("パスワード（任意）", "Password (optional)"), ROOM_PASSWORD);
        }
        ImBoolean participating = new ImBoolean(
                config.isBmsirArenaRoomParticipating()
        );
        if (ImGui.checkbox(t("参加者として入室", "Join as participant"), participating)) {
            config.setBmsirArenaRoomParticipating(participating.get());
        }
        ImGui.textDisabled(t(
                "OFFなら観戦・チャットだけ行い、参加枠を消費しません",
                "OFF joins as a spectator without occupying a player slot"
        ));
        if (PRIVATE_ROOM_CODE.get().isBlank()) {
            renderPrivateRoomSettings(config);
        }
        boolean canEnter = BMSIRArenaClient.isConnected()
                && !("queued".equals(status)
                        || "reserved".equals(status)
                        || "matched".equals(status));
        ImGui.beginDisabled(!canEnter);
        if (ImGui.button(
                PRIVATE_ROOM_CODE.get().isBlank()
                        ? t("ルームを作成", "Create room")
                        : t("コードで参加", "Join by code")
        )) {
            applyRoomCustomConfiguration();
            BMSIRArenaClient.requestRoomEntry(
                    "private",
                    selectedScoreRule(),
                    selectedForcedGauge(),
                    selectedChartScope(),
                    PRIVATE_ROOM_CODE.get(),
                    ROOM_NAME.get(),
                    ROOM_PASSWORD.get()
            );
        }
        ImGui.endDisabled();
        ImGui.separator();
        ImGui.text(t("公開ロビーチャット", "Public lobby chat"));
        renderLobbyChat(160);
    }

    private static void renderPrivateRoomSettings(PlayerConfig config) {
        renderSeriesFormatSettings(config);
        ImBoolean publicSpectators = new ImBoolean(
                config.isBmsirArenaSpectatorPublic()
        );
        if (ImGui.checkbox(t("公開ロビー一覧・Web観戦へ公開", "List publicly and allow Web spectators"), publicSpectators)) {
            config.setBmsirArenaSpectatorPublic(publicSpectators.get());
        }
        ImGui.textDisabled(t(
                "非公開でも部屋コードを知る人は参加・Web観戦できます",
                "Anyone with the room code can still join or spectate"
        ));
        ImBoolean forceHostOption = new ImBoolean(
                config.isBmsirArenaForceHostOption()
        );
        if (ImGui.checkbox(t("部屋主のOPを他プレイヤーへ強制", "Force host options on players"), forceHostOption)) {
            config.setBmsirArenaForceHostOption(forceHostOption.get());
        }
        ImGui.textDisabled(t(
                "強制ゲージと併用可。S-RANDOMの配置自体は各プレイヤー別です",
                "Compatible with forced gauge; S-RANDOM layouts remain individual"
        ));
        NOMINATION_POLICY.set(
                "host".equals(config.getBmsirArenaNominationPolicy())
                        ? 1
                        : "rotate".equals(config.getBmsirArenaNominationPolicy())
                                ? 2 : 0
        );
        if (SERIES_FORMAT.get() == 0) {
            if (ImGui.combo(
                    t("選曲担当", "Nominator"),
                    NOMINATION_POLICY,
                    nominationPolicies()
            )) {
                config.setBmsirArenaNominationPolicy(switch (
                        NOMINATION_POLICY.get()
                ) {
                    case 1 -> "host";
                    case 2 -> "rotate";
                    default -> "all";
                });
            }
        } else {
            config.setBmsirArenaNominationPolicy("all");
        }
        NOMINATION_SECONDS.set(config.getBmsirArenaNominationSeconds());
        if (ImGui.inputInt(t("選曲時間（10～180秒）", "Nomination time (10-180 sec)"), NOMINATION_SECONDS)) {
            config.setBmsirArenaNominationSeconds(NOMINATION_SECONDS.get());
        }
        OPTION_SECONDS.set(config.getBmsirArenaOptionSeconds());
        if (ImGui.inputInt(t("OP選択時間（5～60秒）", "Option time (5-60 sec)"), OPTION_SECONDS)) {
            config.setBmsirArenaOptionSeconds(OPTION_SECONDS.get());
        }
        INTERMISSION_SECONDS.set(config.getBmsirArenaIntermissionSeconds());
        if (ImGui.inputInt(t("曲間待機（0～60秒）", "Intermission (0-60 sec)"), INTERMISSION_SECONDS)) {
            config.setBmsirArenaIntermissionSeconds(
                    INTERMISSION_SECONDS.get()
            );
        }
    }

    private static void renderAllowedPlayModes() {
        ImGui.separator();
        ImGui.text(t("許可KEY数", "Allowed key modes"));
        for (int index = 0; index < ROOM_PLAY_MODES.length; index++) {
            if (index > 0) {
                ImGui.sameLine();
            }
            int playMode = ROOM_PLAY_MODES[index];
            ImBoolean enabled = new ImBoolean(
                    BMSIRArenaClient.isRoomPlayModeAllowed(playMode)
            );
            if (ImGui.checkbox(
                    playMode + "KEY##room-mode-" + index,
                    enabled
            )) {
                BMSIRArenaClient.setRoomPlayModeAllowed(
                        playMode,
                        enabled.get()
                );
            }
        }
    }

    private static void renderCustomRoomSelection() {
        ImGui.separator();
        if (ImGui.button(t("対応難易度表を更新", "Refresh supported tables"))) {
            BMSIRArenaClient.requestCustomCatalog();
        }
        ImGui.sameLine();
        ImGui.textDisabled(t("選んだ表・レベルを合成します", "Selected tables and levels are combined"));

        if (ImGui.beginTabBar("##arena-custom-selection-tabs")) {
            if (ImGui.beginTabItem(t("対応難易度表", "Supported tables"))) {
                JsonNode catalog = BMSIRArenaClient.customCatalogView();
                if (!catalog.path("tables").isArray()) {
                    ImGui.textDisabled(t(
                            "「対応難易度表を更新」を押してください",
                            "Select Refresh supported tables"
                    ));
                } else {
                    for (JsonNode table : catalog.path("tables")) {
                        String tableKey = table.path("table_key").asText();
                        String name = table.path("name").asText(tableKey);
                        if (ImGui.collapsingHeader(name + "##supported-" + tableKey)) {
                            renderCustomLevels(
                                    "supported:" + tableKey + ":",
                                    table.path("levels")
                            );
                        }
                    }
                }
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(t("マイ難易度表", "My tables"))) {
                ImGui.inputText(t("表ID", "Table ID"), USER_TABLE_ID);
                ImGui.inputText(t("Arena共有キー", "Arena share key"), USER_TABLE_KEY);
                ImGui.textDisabled(t("公開表は共有キーを空欄にします", "Leave the share key blank for public tables"));
                if (ImGui.button(t("表を読み込む", "Load table"))) {
                    try {
                        int tableId = Integer.parseInt(USER_TABLE_ID.get().trim());
                        USER_TABLE_KEYS.put(tableId, USER_TABLE_KEY.get());
                        BMSIRArenaClient.requestCustomUserCatalog(
                                tableId,
                                USER_TABLE_KEY.get()
                        );
                    } catch (NumberFormatException exception) {
                        ImGuiNotify.warning(t("表IDは数字で入力してください", "Table ID must be numeric"));
                    }
                }
                for (JsonNode table : BMSIRArenaClient.customUserCatalogs()) {
                    int tableId = table.path("table_id").asInt();
                    String name = table.path("name").asText(
                            "Table " + tableId
                    );
                    if (ImGui.collapsingHeader(
                            name + " (#" + tableId + ")##user-" + tableId
                    )) {
                        renderCustomLevels(
                                "user:" + tableId + ":",
                                table.path("levels")
                        );
                    }
                }
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }

    private static void loadRoomCustomConfiguration(JsonNode rules) {
        ArrayNode allowedPlayModes = JsonNodeFactory.instance.arrayNode();
        JsonNode modes = rules.path("allowed_play_modes");
        if (modes.isArray()) {
            for (JsonNode mode : modes) {
                int value = mode.asInt();
                for (int supported : ROOM_PLAY_MODES) {
                    if (supported == value) {
                        allowedPlayModes.add(value);
                        break;
                    }
                }
            }
        }
        if (allowedPlayModes.isEmpty()) {
            allowedPlayModes.add(7);
        }
        BMSIRArenaClient.setRoomAllowedPlayModes(allowedPlayModes);

        CUSTOM_LEVELS.clear();
        for (JsonNode table : rules.path("custom_selection").path("tables")) {
            String prefix;
            if ("user".equals(table.path("kind").asText())) {
                prefix = "user:" + table.path("table_id").asInt() + ":";
            } else {
                prefix = "supported:"
                        + table.path("table_key").asText()
                        + ":";
            }
            for (JsonNode level : table.path("levels")) {
                String label = level.asText();
                if (!label.isBlank()) {
                    CUSTOM_LEVELS.add(prefix + label);
                }
            }
        }
    }

    private static void renderCustomLevels(String prefix, JsonNode levels) {
        if (!levels.isArray() || levels.isEmpty()) {
            ImGui.textDisabled(t("Arenaで使用できるBMS譜面がありません", "No Arena-compatible BMS charts"));
            return;
        }
        for (JsonNode level : levels) {
            String label = level.path("label").asText();
            String key = prefix + label;
            ImBoolean selected = new ImBoolean(CUSTOM_LEVELS.contains(key));
            if (ImGui.checkbox(
                    label
                            + " ("
                            + level.path("count").asInt()
                            + ")##"
                            + key,
                    selected
            )) {
                if (selected.get()) {
                    CUSTOM_LEVELS.add(key);
                } else {
                    CUSTOM_LEVELS.remove(key);
                }
            }
        }
    }

    private static void applyRoomCustomConfiguration() {
        ArrayNode modes = BMSIRArenaClient.roomAllowedPlayModesView();
        if (modes.isEmpty()) {
            modes.add(7);
        }
        ObjectNode selection = JsonNodeFactory.instance.objectNode();
        ArrayNode tables = selection.putArray("tables");
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String value : CUSTOM_LEVELS) {
            int separator = value.lastIndexOf(':');
            if (separator <= 0 || separator >= value.length() - 1) {
                continue;
            }
            grouped.computeIfAbsent(
                    value.substring(0, separator),
                    ignored -> new ArrayList<>()
            ).add(value.substring(separator + 1));
        }
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            ObjectNode table = tables.addObject();
            String[] parts = entry.getKey().split(":", 2);
            if ("user".equals(parts[0])) {
                int tableId = Integer.parseInt(parts[1]);
                table.put("kind", "user");
                table.put("table_id", tableId);
                table.put(
                        "share_key",
                        USER_TABLE_KEYS.getOrDefault(tableId, "")
                );
            } else {
                table.put("kind", "supported");
                table.put("table_key", parts[1]);
            }
            ArrayNode levels = table.putArray("levels");
            entry.getValue().stream().sorted().forEach(levels::add);
        }
        BMSIRArenaClient.setRoomCustomConfiguration(modes, selection);
    }

    private static String selectedScoreRule() {
        return switch (SCORE_RULE.get()) {
            case 1 -> "minbp";
            case 2 -> "max_combo";
            default -> "exscore";
        };
    }

    private static String selectedForcedGauge() {
        return switch (FORCED_GAUGE.get()) {
            case 1 -> "normal";
            case 2 -> "hard";
            case 3 -> "exhard";
            case 4 -> "hazard";
            default -> "free";
        };
    }

    private static String selectedChartScope() {
        return switch (CHART_SCOPE.get()) {
            case 1 -> "free";
            case 2 -> "custom";
            default -> "official";
        };
    }

    private static void renderPublicRoomList() {
        ImGui.text(t("公開ルーム", "Public rooms"));
        JsonNode rooms = BMSIRArenaClient.publicRoomsView();
        if (!rooms.isArray() || rooms.isEmpty()) {
            ImGui.textDisabled(t("参加できる公開ルームはありません", "No public rooms are available"));
            return;
        }
        int flags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg;
        if (!ImGui.beginTable("##bmsir-public-rooms", 5, flags)) {
            return;
        }
        ImGui.tableSetupColumn(t("部屋名", "Room"));
        ImGui.tableSetupColumn("HOST");
        ImGui.tableSetupColumn(t("人数", "Players"));
        ImGui.tableSetupColumn(t("ルール", "Rules"));
        ImGui.tableSetupColumn("");
        ImGui.tableHeadersRow();
        for (JsonNode room : rooms) {
            String code = room.path("room_code").asText("");
            boolean locked = room.path("locked").asBoolean(false);
            ImGui.tableNextRow();
            tableText(
                    (locked ? t("[鍵] ", "[LOCKED] ") : "")
                            + room.path("room_name").asText(code)
            );
            tableText(room.path("host_name").asText(""));
            tableText(
                    room.path("participant_count").asInt()
                            + " / "
                            + room.path("member_count").asInt()
            );
            tableText(
                    room.path("ln_mode").asText("LN")
                            + " / "
                            + scoreRuleLabel(room.path("score_rule").asText("exscore"))
                            + " / "
                            + gaugeLabel(room.path("forced_gauge").asText("free"))
            );
            ImGui.tableNextColumn();
            if (ImGui.smallButton(t("選択", "Select") + "##public-room-" + code)) {
                PRIVATE_ROOM_CODE.set(code);
                ROOM_PASSWORD.set("");
            }
        }
        ImGui.endTable();
    }

    private static void renderLobbyChat(float height) {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config != null && config.isBmsirArenaMuteChat()) {
            ImGui.textDisabled(t("チャットは設定でミュートされています", "Chat is muted in settings"));
            return;
        }
        List<JsonNode> messages = BMSIRArenaClient.lobbyChatMessages();
        if (ImGui.beginChild("##bmsir-arena-lobby-chat-log", 0, height, true)) {
            if (messages.isEmpty()) {
                ImGui.textDisabled(t("メッセージはまだありません", "No messages yet"));
            }
            for (JsonNode message : messages) {
                ImGui.textWrapped(
                        message.path("name").asText("?")
                                + ": "
                                + message.path("text").asText()
                );
            }
        }
        ImGui.endChild();
        inputTextWithIme(
                "",
                "bmsir-arena-lobby-chat-input",
                LOBBY_CHAT_INPUT,
                200
        );
        ImGui.sameLine();
        ImGui.beginDisabled(!BMSIRArenaClient.isConnected());
        if (
                ImGui.button(t("送信", "Send") + "##lobby-chat")
                        && !LOBBY_CHAT_INPUT.get().isBlank()
        ) {
            BMSIRArenaClient.requestLobbyChat(LOBBY_CHAT_INPUT.get());
            LOBBY_CHAT_INPUT.set("");
        }
        ImGui.endDisabled();
    }

    private static void renderSeriesFormatSettings(PlayerConfig config) {
        SERIES_FORMAT.set(switch (config.getBmsirArenaSeriesFormat()) {
            case "all_picks" -> 1;
            case "first_to" -> 2;
            default -> 0;
        });
        if (ImGui.combo(t("試合形式", "Match format"), SERIES_FORMAT, seriesFormats())) {
            config.setBmsirArenaSeriesFormat(switch (SERIES_FORMAT.get()) {
                case 1 -> "all_picks";
                case 2 -> "first_to";
                default -> "single";
            });
        }
        if (SERIES_FORMAT.get() == 2) {
            FIRST_TO_WINS.set(config.getBmsirArenaFirstToWins());
            if (ImGui.inputInt(t("先取本数（2～5）", "Wins required (2-5)"), FIRST_TO_WINS)) {
                config.setBmsirArenaFirstToWins(FIRST_TO_WINS.get());
            }
            ImGui.textDisabled(t(
                    "各プレイヤーが先取本数ぶん選曲し、重複なしで抽選します",
                    "Each player nominates the target number of distinct charts"
            ));
        } else if (SERIES_FORMAT.get() == 1) {
            ImGui.textDisabled(t(
                    "全員が1曲ずつ選び、全候補を重複なしで回します",
                    "Every player nominates one chart and all distinct picks are played"
            ));
        }
    }

    private static void renderRulesetProfileSetting(
            PlayerConfig config,
            String idSuffix
    ) {
        RULESET_PROFILE.set(
                "oraja".equals(config.getBmsirRulesetProfile()) ? 1 : 0
        );
        if (ImGui.combo(
                t("判定・ゲージ仕様", "Ruleset") + idSuffix,
                RULESET_PROFILE,
                RULESET_PROFILES
        )) {
            BMSIRArenaClient.setConfiguredRulesetProfile(
                    RULESET_PROFILE.get() == 1 ? "oraja" : "lr2"
            );
        }
    }

    private static String scoreRuleLabel(String rule) {
        return switch (rule) {
            case "minbp" -> t("BP Arena / CB（少ない順）", "BP Arena / CB (lowest first)");
            case "max_combo" -> t("MAX COMBO（多い順）", "MAX COMBO (highest first)");
            default -> t("EX SCORE（多い順）", "EX SCORE (highest first)");
        };
    }

    private static String gaugeLabel(String gauge) {
        return switch (gauge) {
            case "normal" -> "NORMAL";
            case "hard" -> "HARD";
            case "exhard" -> "EXHARD";
            case "hazard" -> "HAZARD";
            default -> t("自由", "Free");
        };
    }

    private static String rulesetProfileLabel(String profile) {
        return "oraja".equals(profile) ? "oraja" : "LR2";
    }

    static String seriesFormatLabel(String format, int firstToWins) {
        return switch (format) {
            case "bo2" -> t("BO2（2曲総合）", "BO2 (two-chart total)");
            case "all_picks" -> t("全員の曲を回す", "All picks");
            case "first_to" -> BMSIRArenaI18n.isEnglish()
                    ? "First to " + Math.max(2, Math.min(5, firstToWins))
                    : Math.max(2, Math.min(5, firstToWins)) + "本先取";
            default -> t("1曲", "Single");
        };
    }

    private static void renderRoomParticipants() {
        JsonNode room = BMSIRArenaClient.roomView();
        JsonNode match = room.isObject() && room.path("players").isArray()
                ? room
                : BMSIRArenaClient.currentMatchView();
        if (!match.isObject() || !match.path("players").isArray()) {
            return;
        }
        ImGui.separator();
        ImGui.text(t("参加者", "Participants"));
        boolean host = BMSIRArenaClient.isCurrentRoomHost();
        boolean single = "single".equals(BMSIRArenaClient.currentSeriesFormat());
        for (JsonNode player : match.path("players")) {
            int playerId = player.path("player_id").asInt();
            StringBuilder label = new StringBuilder(
                    player.path("name").asText(Integer.toString(playerId))
            );
            if (player.path("host").asBoolean()) {
                label.append(" [HOST]");
            }
            if (player.path("selector").asBoolean()) {
                label.append(" [SELECT]");
            }
            if (player.path("ready").asBoolean()) {
                label.append(" [READY]");
            }
            if (player.has("participating") && !player.path("participating").asBoolean()) {
                label.append(t(" [観戦]", " [SPECTATOR]"));
            }
            if (player.path("pending").asBoolean()) {
                label.append(t(" [次戦参加]", " [NEXT MATCH]"));
            }
            int wins = player.path("series_wins").asInt();
            if (!single) {
                label.append("  ").append(wins).append(t("勝", " wins"));
            }
            int roomMatches = player.path("room_matches").asInt();
            if (roomMatches > 0) {
                label.append("  ")
                        .append(player.path("room_wins").asInt())
                        .append("W-")
                        .append(player.path("room_losses").asInt())
                        .append("L-")
                        .append(player.path("room_draws").asInt())
                        .append("D / ")
                        .append(String.format(
                                Locale.ROOT,
                                "%.1f%%",
                                player.path("room_win_rate").asDouble()
                                        * 100.0
                        ));
            }
            ImGui.text(label.toString());
            if (!host || playerId == BMSIRArenaClient.currentPlayerId()) {
                continue;
            }
            ImGui.sameLine();
            if (ImGui.smallButton(t("キック", "Kick") + "##room-kick-" + playerId)) {
                BMSIRArenaClient.requestRoomKick(playerId);
            }
            ImGui.sameLine();
            if (ImGui.smallButton(t("HOST移譲", "Transfer host") + "##room-host-" + playerId)) {
                BMSIRArenaClient.requestRoomTransferHost(playerId);
            }
            if (single) {
                ImGui.sameLine();
                if (ImGui.smallButton(t("選曲担当", "Set selector") + "##room-selector-" + playerId)) {
                    BMSIRArenaClient.requestRoomSetSelector(playerId);
                }
            }
        }
    }

    private static void renderRanking() {
        JsonNode ranking = BMSIRArenaClient.rankingView();
        JsonNode current = ranking.path("current");
        if (current.isObject() && current.size() > 0) {
            ImGui.text(String.format(
                    Locale.ROOT,
                    t("あなた: %d位  /  R %d", "You: #%d / R %d"),
                    current.path("rank").asInt(),
                    Math.round(current.path("rating_exact").asDouble())
            ));
        }
        List<JsonNode> rows = new ArrayList<>();
        ranking.path("rows").forEach(rows::add);
        if (rows.isEmpty()) {
            ImGui.textDisabled(t("ランキング対象の対戦結果はまだありません", "No rated match results yet"));
            return;
        }
        int flags = ImGuiTableFlags.Borders
                | ImGuiTableFlags.RowBg
                | ImGuiTableFlags.ScrollY;
        if (ImGui.beginTable("##bmsir-arena-ranking", 4, flags, 0, 390)) {
            ImGui.tableSetupColumn("#");
            ImGui.tableSetupColumn("Player");
            ImGui.tableSetupColumn("Rate");
            ImGui.tableSetupColumn("Matches");
            ImGui.tableHeadersRow();
            for (JsonNode row : rows) {
                ImGui.tableNextRow();
                tableText(Integer.toString(row.path("rank").asInt()));
                tableText(row.path("name").asText());
                tableText(Long.toString(Math.round(row.path("rating_exact").asDouble())));
                tableText(Integer.toString(row.path("matches_played").asInt()));
            }
            ImGui.endTable();
        }
    }

    private static void renderRatingChange(JsonNode match) {
        if (!match.path("rated").asBoolean(false)) {
            return;
        }
        JsonNode own = null;
        for (JsonNode player : match.path("players")) {
            if (
                    player.path("player_id").asInt()
                            == BMSIRArenaClient.currentPlayerId()
            ) {
                own = player;
                break;
            }
        }
        if (
                own == null
                        || !own.hasNonNull("before")
                        || !own.hasNonNull("after")
        ) {
            return;
        }
        double delta = own.path("delta").asDouble();
        int color = delta > 0.0
                ? ImColor.rgb(121, 223, 139)
                : delta < 0.0
                        ? ImColor.rgb(255, 115, 115)
                        : ImColor.rgb(255, 211, 106);
        ImGui.setWindowFontScale(1.55f);
        ImGui.textColored(
                color,
                ratingChangeText(
                        own.path("before").asDouble(),
                        own.path("after").asDouble(),
                        delta
                )
        );
        ImGui.setWindowFontScale(1.0f);
    }

    static String ratingChangeText(double before, double after, double delta) {
        return String.format(
                Locale.ROOT,
                t("レート %d → %d (%+.1f)", "Rating %d -> %d (%+.1f)"),
                Math.round(before),
                Math.round(after),
                delta
        );
    }

    private static void renderManual() {
        JsonNode manual = BMSIRArenaClient.manualView();
        if (!manual.isObject() || manual.size() == 0) {
            ImGui.textDisabled(t("マニュアルを取得できていません。", "The manual has not been downloaded."));
        } else {
            ImGui.textWrapped(
                    manual.path("title").asText("BMS-IR Arena マニュアル")
            );
            ImGui.sameLine();
            ImGui.textDisabled("v" + manual.path("version").asText(""));
            ImGui.separator();
            for (JsonNode section : manual.path("sections")) {
                if (ImGui.collapsingHeader(section.path("title").asText())) {
                    for (JsonNode item : section.path("items")) {
                        ImGui.bullet();
                        ImGui.textWrapped(item.asText());
                    }
                }
            }
        }
        if (ImGui.button(FontAwesomeIcons.SyncAlt + t(" マニュアルを更新", " Refresh manual"))) {
            BMSIRArenaClient.requestArenaManual();
        }
        ImGui.textDisabled(
                t(
                        "取得済みの内容はローカルに保存され、オフライン時も表示できます。",
                        "Downloaded content is cached locally and remains available offline."
                )
        );
    }

    private static void renderChat(boolean allowInput, float height) {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        ImBoolean muted = new ImBoolean(config != null && config.isBmsirArenaMuteChat());
        if (ImGui.checkbox(t("チャットをミュート", "Mute chat"), muted) && config != null) {
            config.setBmsirArenaMuteChat(muted.get());
        }
        if (muted.get()) {
            ImGui.textDisabled(t("チャットはこの本体でのみ非表示です", "Chat is hidden only on this client"));
            return;
        }
        List<JsonNode> messages = BMSIRArenaClient.chatMessages();
        if (ImGui.beginChild("##bmsir-arena-chat-log", 0, height, true)) {
            if (messages.isEmpty()) {
                ImGui.textDisabled(t("メッセージはまだありません", "No messages yet"));
            }
            for (JsonNode message : messages) {
                ImGui.textWrapped(
                        message.path("name").asText("?")
                                + ": "
                                + message.path("text").asText()
                );
            }
        }
        ImGui.endChild();
        if (
                !allowInput
                        || (!BMSIRArenaClient.isReserved()
                                && BMSIRArenaClient.currentRoomCode().isBlank())
        ) {
            ImGui.textDisabled(t("対戦中の入力は無効です", "Chat input is disabled during play"));
            return;
        }
        inputTextWithIme(
                "",
                "bmsir-arena-chat-input",
                CHAT_INPUT,
                200
        );
        ImGui.sameLine();
        boolean send = ImGui.button(t("送信", "Send"));
        if (send && !CHAT_INPUT.get().isBlank()) {
            BMSIRArenaClient.requestChat(CHAT_INPUT.get());
            CHAT_INPUT.set("");
        }
    }

    private static void renderChatPreview() {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config != null && config.isBmsirArenaMuteChat()) {
            return;
        }
        List<JsonNode> messages = BMSIRArenaClient.chatMessages();
        if (messages.isEmpty()) {
            return;
        }
        ImGui.separator();
        messages.stream()
                .skip(Math.max(0, messages.size() - 2))
                .forEach(message -> ImGui.textWrapped(
                        message.path("name").asText("?")
                                + ": "
                                + message.path("text").asText()
                ));
        ImGui.separator();
    }

    private static void renderSettings(PlayerConfig config) {
        LANGUAGE.set("en".equals(config.getBmsirArenaLanguage()) ? 1 : 0);
        if (ImGui.combo(
                t("本体UI言語", "Client UI language"),
                LANGUAGE,
                new String[]{"日本語", "English"}
        )) {
            config.setBmsirArenaLanguage(LANGUAGE.get() == 1 ? "en" : "ja");
            BMSIRArenaI18n.setLanguage(config.getBmsirArenaLanguage());
            saveSettingsOrWarn();
        }
        ImGui.text(t("表示モード", "Display mode"));
        if (ImGui.radioButton(t("通常", "Full"), config.getBmsirArenaOverlayMode() == 0)) {
            config.setBmsirArenaOverlayMode(0);
        }
        ImGui.sameLine();
        if (ImGui.radioButton(t("コンパクト", "Compact"), config.getBmsirArenaOverlayMode() == 1)) {
            config.setBmsirArenaOverlayMode(1);
        }
        ImGui.sameLine();
        if (ImGui.radioButton(t("非表示", "Hidden"), config.getBmsirArenaOverlayMode() == 2)) {
            setVisible(false);
        }
        renderOverlayHotkeySetting(config);
        ImGui.textDisabled(t(
                "戻らない場合は固定のF5メニューから再表示できます",
                "The fixed F5 menu can restore a hidden overlay"
        ));
        if (ImGui.button(t("Arenaログフォルダを開く", "Open Arena log folder"))) {
            if (!BMSIRArenaLog.openLogFolder()) {
                ImGuiNotify.warning(t(
                        "Arenaログフォルダを開けませんでした",
                        "Could not open the Arena log folder"
                ));
            }
        }
        ImGui.sameLine();
        ImGui.textDisabled(BMSIRArenaLog.logFileName());
        ImGui.textDisabled(t(
                "認証情報とチャット本文はログへ記録しません",
                "Credentials and chat messages are never logged"
        ));
        ImBoolean detailedLog = new ImBoolean(
                config.isBmsirArenaDetailedLogEnabled()
        );
        if (ImGui.checkbox(t("詳細Arenaログ", "Detailed Arena log"), detailedLog)) {
            config.setBmsirArenaDetailedLogEnabled(detailedLog.get());
            BMSIRArenaLog.setDetailedEnabled(detailedLog.get());
            saveSettingsOrWarn();
        }
        ImGui.textDisabled(t(
                "OFFでは接続・状態遷移・警告・エラーだけを記録します",
                "OFF records only connections, state changes, warnings, and errors"
        ));
        GRAPH_HIGHLIGHT.set(config.getBmsirArenaGraphHighlight());
        if (ImGui.combo(
                t("グラフの強調", "Graph highlight"),
                GRAPH_HIGHLIGHT,
                new String[]{t("現在1位", "Leader"), t("自分", "Self")}
        )) {
            config.setBmsirArenaGraphHighlight(GRAPH_HIGHLIGHT.get());
        }
        TARGET_MODE.set(arenaTargetModeIndex(config.getBmsirArenaTargetMode()));
        if (ImGui.combo(
                t("本体ターゲット", "Main target"),
                TARGET_MODE,
                arenaTargetModeLabels()
        )) {
            config.setBmsirArenaTargetMode(arenaTargetMode(TARGET_MODE.get()));
            if (
                    !PlayerConfig.BMSIR_ARENA_TARGET_SPECIFIED.equals(
                            config.getBmsirArenaTargetMode()
                    )
            ) {
                BMSIRArenaClient.setArenaTargetSpecifiedPlayerId(0);
            }
            saveSettingsOrWarn();
        }
        renderArenaTargetPlayerSelector(config);
        GRAPH_ORDER.set(arenaGraphOrderIndex(config.getBmsirArenaGraphOrder()));
        if (ImGui.combo(
                t("グラフの並び", "Graph order"),
                GRAPH_ORDER,
                arenaGraphOrderLabels()
        )) {
            config.setBmsirArenaGraphOrder(arenaGraphOrder(GRAPH_ORDER.get()));
            saveSettingsOrWarn();
        }
        ImGui.textDisabled(t(
                "順位順では自分の棒は赤、入室順固定では色も入室順で固定します",
                "Rank order marks self in red; entry order also fixes player colors"
        ));

        ImBoolean cursor = new ImBoolean(config.isBmsirArenaShowCursor());
        if (ImGui.checkbox(t("プレイ中もマウスカーソルを表示", "Show cursor during play"), cursor)) {
            config.setBmsirArenaShowCursor(cursor.get());
        }
        ImBoolean presentation = new ImBoolean(
                config.isBmsirArenaPresentationOverlayEnabled()
        );
        if (ImGui.checkbox(t("重要フェーズを画面中央に大きく表示", "Show large phase announcements"), presentation)) {
            config.setBmsirArenaPresentationOverlayEnabled(
                    presentation.get()
            );
        }
        ImBoolean countdownSe = new ImBoolean(
                config.isBmsirArenaCountdownSeEnabled()
        );
        if (ImGui.checkbox(t("Arena 3・2・1カウントSE", "Arena 3-2-1 sound"), countdownSe)) {
            config.setBmsirArenaCountdownSeEnabled(countdownSe.get());
        }
        ImBoolean startSe = new ImBoolean(config.isBmsirArenaStartSeEnabled());
        if (ImGui.checkbox(t("Arena開始SE", "Arena start sound"), startSe)) {
            config.setBmsirArenaStartSeEnabled(startSe.get());
        }
        ImBoolean phaseWarning = new ImBoolean(
                config.isBmsirArenaPhaseWarningEnabled()
        );
        if (ImGui.checkbox(t("選曲・OP残り10秒／5秒の警告SE", "10/5 second selection warning sound"), phaseWarning)) {
            config.setBmsirArenaPhaseWarningEnabled(phaseWarning.get());
        }
        int[] notificationVolume = {
                config.getBmsirArenaNotificationSeVolume()
        };
        if (ImGui.sliderInt(
                t("Arena通知SE音量", "Arena sound volume"),
                notificationVolume,
                0,
                100
        )) {
            config.setBmsirArenaNotificationSeVolume(notificationVolume[0]);
        }
        if (ImGui.button(t("Arena通知音をテスト", "Test Arena sound"))) {
            BMSIRArenaClient.playPresentationSound(
                    bms.player.beatoraja.SystemSoundManager.SoundType.ARENA_MATCH_FOUND,
                    config.getBmsirArenaNotificationSeVolume() / 100.0f
            );
        }
        ImBoolean unrestricted = new ImBoolean(
                config.isBmsirArenaUnrestrictedRating()
        );
        if (ImGui.checkbox(t("レート制限なしマッチを許可", "Allow unrestricted rating matches"), unrestricted)) {
            config.setBmsirArenaUnrestrictedRating(unrestricted.get());
        }
        ImGui.textDisabled(t(
                "距離のある即時マッチは相手も許可した場合だけ成立します",
                "A wide-rating instant match requires consent from both players"
        ));
        ImBoolean allowCpu = new ImBoolean(config.isBmsirArenaAllowCpu());
        if (ImGui.checkbox(t("1人待機中のCPU戦を許可", "Allow CPU match while waiting alone"), allowCpu)) {
            config.setBmsirArenaAllowCpu(allowCpu.get());
        }
        ImGui.textDisabled(t(
                "OFFの場合、人間がもう1人来るまで待機します。2人以上ではCPU補充します",
                "OFF waits for a second human; with 2+ humans, CPUs always fill four slots"
        ));
        ImBoolean allowHigherSelection = new ImBoolean(
                config.isBmsirArenaAllowHigherSelection()
        );
        if (ImGui.checkbox(
                t("高レート基準の選曲を許可", "Allow higher-rating chart limits"),
                allowHigherSelection
        )) {
            config.setBmsirArenaAllowHigherSelection(
                    allowHigherSelection.get()
            );
        }
        ImGui.textDisabled(
                t(
                        "ONの場合、自分の解放済み上限は部屋の最低選曲基準から除外されます",
                        "ON excludes your unlocked limit from the room's minimum chart limit"
                )
        );
        ImBoolean mirror = new ImBoolean(config.isBmsirArenaRandomMirror());
        if (ImGui.checkbox(t("同期RANDOMを左右反転して受け取る", "Mirror synchronized RANDOM"), mirror)) {
            config.setBmsirArenaRandomMirror(mirror.get());
        }
        ImBoolean stay = new ImBoolean(config.isBmsirArenaStayInRoom());
        if (ImGui.checkbox(t("ルーム対戦後も部屋に残る", "Stay in room after a match"), stay)) {
            config.setBmsirArenaStayInRoom(stay.get());
            if (!"ranked".equals(BMSIRArenaClient.currentMatchMode())) {
                BMSIRArenaClient.requestRoomStay(stay.get());
            }
        }
        ImGui.separator();
        ImGui.textWrapped(t(
                "Arenaウィンドウの位置とサイズは5／7／9／10／14KEYごと、通常・コンパクト・プレイ中グラフ・ステータスごとに保存されます。",
                "Arena window positions and sizes are saved per 5/7/9/10/14KEY layout and per full, compact, graph, and status window."
        ));
    }

    private static void renderArenaTargetPlayerSelector(PlayerConfig config) {
        if (
                !PlayerConfig.BMSIR_ARENA_TARGET_SPECIFIED.equals(
                        config.getBmsirArenaTargetMode()
                )
        ) {
            return;
        }
        JsonNode match = BMSIRArenaClient.currentMatchView();
        if (!match.path("players").isArray()) {
            ImGui.textDisabled(t(
                    "試合中に対象プレイヤーを選択できます",
                    "Select a target player during a match"
            ));
            return;
        }
        int ownPlayerId = BMSIRArenaClient.currentPlayerId();
        int selectedPlayerId = BMSIRArenaClient.arenaTargetSpecifiedPlayerId();
        boolean rendered = false;
        for (JsonNode player : match.path("players")) {
            int playerId = player.path("player_id").asInt();
            if (playerId <= 0 || playerId == ownPlayerId) {
                continue;
            }
            rendered = true;
            String name = player.path("name").asText(Integer.toString(playerId));
            if (ImGui.radioButton(
                    name + "##arena-target-player-" + playerId,
                    selectedPlayerId == playerId
            )) {
                BMSIRArenaClient.setArenaTargetSpecifiedPlayerId(playerId);
            }
        }
        if (!rendered) {
            ImGui.textDisabled(t(
                    "指定できる対戦相手がいません",
                    "No opponent can be selected"
            ));
        }
    }

    private static int arenaTargetModeIndex(String mode) {
        return switch (mode) {
            case PlayerConfig.BMSIR_ARENA_TARGET_LEADER -> 1;
            case PlayerConfig.BMSIR_ARENA_TARGET_ABOVE -> 2;
            case PlayerConfig.BMSIR_ARENA_TARGET_SPECIFIED -> 3;
            default -> 0;
        };
    }

    private static String[] arenaTargetModeLabels() {
        return new String[]{
                "OFF",
                t("1位の対戦相手", "Leading opponent"),
                t("自分の直上", "Opponent directly above"),
                t("指定プレイヤー", "Selected player")
        };
    }

    private static String[] arenaGraphOrderLabels() {
        return new String[]{
                t("順位順", "Rank order"),
                t("入室順固定", "Fixed entry order")
        };
    }

    private static String arenaTargetMode(int index) {
        return switch (index) {
            case 1 -> PlayerConfig.BMSIR_ARENA_TARGET_LEADER;
            case 2 -> PlayerConfig.BMSIR_ARENA_TARGET_ABOVE;
            case 3 -> PlayerConfig.BMSIR_ARENA_TARGET_SPECIFIED;
            default -> PlayerConfig.BMSIR_ARENA_TARGET_OFF;
        };
    }

    private static int arenaGraphOrderIndex(String order) {
        return PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY.equals(order) ? 1 : 0;
    }

    private static String arenaGraphOrder(int index) {
        return index == 1
                ? PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY
                : PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_RANK;
    }

    private static void saveSettingsOrWarn() {
        if (!BMSIRArenaClient.saveArenaConfig()) {
            ImGuiNotify.warning(t(
                    "Arena設定を保存できませんでした",
                    "Could not save Arena settings"
            ));
        }
    }

    private static void renderOverlayHotkeySetting(PlayerConfig config) {
        ImGui.text(
                t("オーバーレイ表示キー: ", "Overlay hotkey: ")
                        + BMSIRArenaHotkey.label(
                                config.getBmsirArenaOverlayHotkeyKeys()
                        )
        );
        if (hotkeyCaptureActive) {
            ImGui.textColored(
                    ImColor.rgb(255, 211, 106),
                    t(
                            "登録したいキーをすべて押して、全部離してください",
                            "Press every key in the shortcut, then release them"
                    )
            );
            if (!HOTKEY_CAPTURE_KEYS.isEmpty()) {
                ImGui.text(
                        t("入力中: ", "Pressed: ")
                                + BMSIRArenaHotkey.label(capturedHotkeyKeys())
                );
            }
            ImGui.textDisabled(
                    t(
                            "1キー単体も可／Escでキャンセル／解除は下の「解除」ボタン",
                            "A single key is allowed / Esc cancels / Clear removes the shortcut"
                    )
            );
            captureOverlayHotkey(config);
            return;
        }
        if (ImGui.button(t("表示キーを変更", "Change hotkey"))) {
            HOTKEY_CAPTURE_KEYS.clear();
            hotkeyCaptureActive = true;
        }
        ImGui.sameLine();
        if (ImGui.button(t("初期値へ戻す", "Restore default"))) {
            config.setBmsirArenaOverlayHotkeyKeys(
                    BMSIRArenaHotkey.defaultKeys()
            );
            saveHotkeyOrWarn();
        }
        ImGui.sameLine();
        if (ImGui.button(t("解除", "Clear"))) {
            config.setBmsirArenaOverlayHotkeyKeys(new int[0]);
            saveHotkeyOrWarn();
        }
    }

    private static void captureOverlayHotkey(PlayerConfig config) {
        if (Gdx.input.isKeyPressed(Keys.ESCAPE)) {
            HOTKEY_CAPTURE_KEYS.clear();
            hotkeyCaptureActive = false;
            return;
        }
        boolean anyKeyHeld = false;
        for (int keycode = Keys.UNKNOWN + 1; keycode <= Keys.MAX_KEYCODE; keycode++) {
            if (Gdx.input.isKeyPressed(keycode)) {
                anyKeyHeld = true;
                HOTKEY_CAPTURE_KEYS.add(BMSIRArenaHotkey.normalizeKey(keycode));
            }
        }
        if (anyKeyHeld || HOTKEY_CAPTURE_KEYS.isEmpty()) {
            return;
        }
        int[] captured = capturedHotkeyKeys();
        HOTKEY_CAPTURE_KEYS.clear();
        hotkeyCaptureActive = false;
        config.setBmsirArenaOverlayHotkeyKeys(captured);
        BMSIRArenaClient.discardArenaOverlayHotkeyKeys(captured);
        if (saveHotkeyOrWarn()) {
            ImGuiNotify.info(
                    t("Arena表示キーを ", "Arena hotkey changed to ")
                            + BMSIRArenaHotkey.label(captured)
                            + t(" に変更しました", "")
            );
        }
    }

    private static boolean saveHotkeyOrWarn() {
        if (BMSIRArenaClient.saveArenaConfig()) {
            return true;
        }
        ImGuiNotify.warning(t(
                "Arena表示キーを保存できませんでした",
                "Could not save the Arena hotkey"
        ));
        return false;
    }

    private static int[] capturedHotkeyKeys() {
        return HOTKEY_CAPTURE_KEYS.stream().mapToInt(Integer::intValue).toArray();
    }

    public static boolean isHotkeyCaptureActive() {
        return hotkeyCaptureActive;
    }

    static int utf8BufferCapacity(int maxCodePoints) {
        return Math.max(0, maxCodePoints) * 4 + 1;
    }

    private static void inputTextWithIme(
            String label,
            String id,
            ImString value,
            int maxCodePoints
    ) {
        boolean inlineEditorOpen = ArenaInlineTextEditor.isOpenFor(value);
        String widgetLabel = imeInputWidgetLabel(label, id, inlineEditorOpen);
        if (inlineEditorOpen) {
            ImGui.inputText(widgetLabel, value, ImGuiInputTextFlags.ReadOnly);
            return;
        }
        ImGui.inputText(widgetLabel, value);
        if (shouldOpenImeEditor(ImGui.isItemActivated(), ImGui.isItemClicked())) {
            float editorWidth = ImGui.getItemRectSizeX();
            if (!label.isEmpty()) {
                editorWidth -= ImGui.calcTextSizeX(label)
                        + ImGui.getStyle().getItemInnerSpacingX();
            }
            ArenaInlineTextEditor.open(
                    value,
                    maxCodePoints,
                    ImGui.getItemRectMinX(),
                    ImGui.getItemRectMinY(),
                    editorWidth,
                    ImGui.getItemRectSizeY()
            );
        }
    }

    static boolean shouldOpenImeEditor(boolean itemActivated, boolean itemClicked) {
        return itemActivated || itemClicked;
    }

    static String imeInputWidgetLabel(String label, String id, boolean inlineEditorOpen) {
        return label + "##" + id + (inlineEditorOpen ? "-ime-active" : "");
    }

    private static void tableText(String text) {
        ImGui.tableNextColumn();
        ImGui.textUnformatted(text == null || text.isBlank() ? "-" : text);
    }
}
