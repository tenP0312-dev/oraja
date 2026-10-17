package bms.player.beatoraja.modmenu;

import bms.model.Mode;
import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.arena.bmsir.BMSIRNonstopMixTransport;
import bms.player.beatoraja.nonstop.CompiledMix;
import bms.player.beatoraja.nonstop.MixChartSwap;
import bms.player.beatoraja.nonstop.MixCompiler;
import bms.player.beatoraja.nonstop.MixDifficulty;
import bms.player.beatoraja.nonstop.MixGenerator;
import bms.player.beatoraja.nonstop.MixGenreFamily;
import bms.player.beatoraja.nonstop.MixIrClient;
import bms.player.beatoraja.nonstop.MixJoinPlanner;
import bms.player.beatoraja.nonstop.MixJson;
import bms.player.beatoraja.nonstop.MixLibrary;
import bms.player.beatoraja.nonstop.MixLoad;
import bms.player.beatoraja.nonstop.MixMainThread;
import bms.player.beatoraja.nonstop.MixPlan;
import bms.player.beatoraja.nonstop.MixPreparation;
import bms.player.beatoraja.nonstop.MixPreviews;
import bms.player.beatoraja.nonstop.MixResult;
import bms.player.beatoraja.nonstop.MixRuntime;
import bms.player.beatoraja.nonstop.MixSequencer;
import bms.player.beatoraja.nonstop.MixStore;
import bms.player.beatoraja.nonstop.MixTrack;
import bms.player.beatoraja.nonstop.MixTrackAnalysis;
import bms.player.beatoraja.nonstop.MixTransition;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.bar.Bar;
import bms.player.beatoraja.select.bar.SongBar;
import bms.player.beatoraja.song.SongData;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * NONSTOP MIX editor (Issue #535), opened from the F5 Mod menu on Music
 * Select. Mouse first: songs are picked from the library, each song's used
 * part and every join are edited on a time line, joins can be previewed with
 * autoplay or tried, and sets can be generated, saved and shared on BMS-IR.
 */
public final class NonstopMixMenu {
    private static final int HISTORY_LIMIT = 100;
    private static final int COLOR_BG = ImColor.rgba(24, 26, 32, 255);
    private static final int COLOR_ACTIVITY = ImColor.rgba(86, 156, 214, 200);
    private static final int COLOR_ACTIVITY_B = ImColor.rgba(214, 138, 86, 200);
    private static final int COLOR_DENSITY = ImColor.rgba(230, 230, 230, 120);
    private static final int COLOR_BAR = ImColor.rgba(255, 255, 255, 28);
    private static final int COLOR_PHRASE = ImColor.rgba(255, 255, 255, 70);
    private static final int COLOR_LN = ImColor.rgba(255, 196, 0, 220);
    private static final int COLOR_RANGE = ImColor.rgba(120, 220, 140, 45);
    private static final int COLOR_MARKER = ImColor.rgba(120, 220, 140, 255);
    private static final int COLOR_HANDOFF = ImColor.rgba(255, 90, 90, 255);
    private static final int COLOR_GAIN_A = ImColor.rgba(130, 190, 255, 255);
    private static final int COLOR_GAIN_B = ImColor.rgba(255, 170, 110, 255);
    private static final int COLOR_TEXT = ImColor.rgba(230, 230, 230, 255);
    private static final float[] WARNING = {1.0f, 0.75f, 0.3f, 1.0f};
    private static final float[] ERROR = {1.0f, 0.4f, 0.4f, 1.0f};
    private static final float[] GOOD = {0.5f, 0.9f, 0.6f, 1.0f};

    private static MainController main;
    private static MixLibrary library;
    private static MixPlan plan;
    private static final Deque<String> UNDO = new ArrayDeque<>();
    private static final Deque<String> REDO = new ArrayDeque<>();
    private static boolean dirty;
    private static int selectedTrack = -1;
    private static int selectedJoin = -1;
    private static final ImString NAME = new ImString(128);
    private static final ImString DESCRIPTION = new ImString(1024);

    // background work
    private enum Pending { NONE, PLAY, AUTOPLAY_SET, LISTEN, TRY, CHECK }
    private static MixPreparation preparation;
    private static Pending pending = Pending.NONE;
    private static String pendingTitle = "";
    private static List<MixCompiler.Issue> issues = List.of();
    private static String issuesFor = "";
    /** BGA load of the last compiled check (Issue #556); null until a check ran. */
    private static MixLoad load;
    /** A prepared start that waits for the heavy-set question. */
    private record HeavyStart(MixRuntime.Session session, BMSPlayerMode mode, Pending kind) {
    }
    private static HeavyStart heavyStart;
    private static long lastEdit;
    private static boolean checkWanted;
    private static final Map<String, MixTrackAnalysis> ANALYSES = new HashMap<>();
    private static final Set<String> ANALYSING = new HashSet<>();
    private static final Set<String> ANALYSIS_FAILED = new HashSet<>();

    // song search
    private static final ImString SEARCH = new ImString(128);
    private static List<SongData> searchResults = List.of();
    private static int searchSelection = -1;

    // generator
    private static final MixGenerator.Request GENERATE = new MixGenerator.Request();
    private static final ImInt GEN_SCOPE = new ImInt(0);
    private static final int[] GEN_MINUTES = {15};
    private static final ImInt GEN_STYLE = new ImInt(0);
    private static final ImInt GEN_GENRE_RULE = new ImInt(1);
    private static final ImString GEN_GENRE = new ImString(64);
    /** 0: free text genre, n: {@code MixGenreFamily.values()[n - 1]}. */
    private static final ImInt GEN_FAMILY = new ImInt(0);
    /** Sets generated in this session, newest last (Issue #563: their songs are used less next time). */
    private static final java.util.ArrayDeque<MixPlan> RECENT_GENERATED = new java.util.ArrayDeque<>();
    private static final int RECENT_GENERATED_MAX = 5;
    private static final ImString GEN_FILTER = new ImString(64);
    /** 0: LV1–LV12 (normal), 1: ★1–★25 (insane); see {@link MixDifficulty}. */
    private static final ImInt GEN_SCALE = new ImInt(1);
    private static final int[][] GEN_LEVELS = {{1, MixDifficulty.NORMAL_MAX}, {1, MixDifficulty.INSANE_MAX}};
    private static final ImInt GEN_CURVE = new ImInt(1);
    private static final ImInt GEN_BARS = new ImInt(2);
    private static final float[] GEN_TOLERANCE = {3.0f};
    private static final ImBoolean GEN_BLEND = new ImBoolean(true);
    private static final ImBoolean GEN_FADE = new ImBoolean(true);
    private static final ImBoolean GEN_CUT = new ImBoolean(true);
    private static final ImBoolean GEN_REFERENCE = new ImBoolean(false);
    private static final int[] GEN_RANGE = {1, 1};
    private static MixGenerator generator;
    /** The generator whose result was already offered; its notes stay visible. */
    private static MixGenerator consumedGenerator;
    private static MixPlan generated;

    // saved sets
    private static List<MixPlan> savedSets;

    // IR
    private static final ImString IR_QUERY = new ImString(80);
    private static final ImInt IR_SORT = new ImInt(0);
    private static volatile MixIrClient.Page irPage;
    private static volatile String irError = "";
    private static volatile boolean irBusy;
    private static int irSelection = -1;
    private static volatile List<MixIrClient.Score> irRanking;
    private static volatile String irRankingFor = "";

    // records are read from disk at most every few seconds
    private static final Map<String, Object[]> RECORDS = new HashMap<>();

    // result
    private static MixResult shownResult;
    private static final ImBoolean SHOW_RESULT = new ImBoolean(false);

    private NonstopMixMenu() {
    }

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    public static void setMain(MainController controller) {
        main = controller;
        library = MixLibrary.shared(controller);
    }

    private static MixStore store() {
        return MixStore.forPlayer(main.getConfig().getPlayerpath(), main.getConfig().getPlayername());
    }

    private static void ensurePlan() {
        if (plan != null) {
            return;
        }
        MixPlan draft = main != null ? store().openingPlan() : null;
        plan = draft != null ? draft : new MixPlan();
        syncTextFields();
    }

    private static void syncTextFields() {
        NAME.set(plan.name);
        DESCRIPTION.set(plan.description);
    }

    // ------------------------------------------------------------------ history

    /** Records the current state before an edit, so it can be undone. */
    private static void edit() {
        long now = System.currentTimeMillis();
        // a slider drag or typing is one step in the history, not one per frame
        if (now - lastEdit > 400 || UNDO.isEmpty()) {
            UNDO.push(MixJson.toText(plan, true));
            while (UNDO.size() > HISTORY_LIMIT) {
                UNDO.removeLast();
            }
        }
        REDO.clear();
        dirty = true;
        lastEdit = now;
        checkWanted = true;
    }

    private static void afterEdit() {
        plan.updatedAt = System.currentTimeMillis();
        draftPending = true;
    }

    private static boolean draftPending;

    private static void undo() {
        if (UNDO.isEmpty()) {
            return;
        }
        REDO.push(MixJson.toText(plan, true));
        restore(UNDO.pop());
    }

    private static void redo() {
        if (REDO.isEmpty()) {
            return;
        }
        UNDO.push(MixJson.toText(plan, true));
        restore(REDO.pop());
    }

    private static void restore(String text) {
        try {
            plan = MixJson.fromText(text);
            syncTextFields();
            clampSelection();
            dirty = true;
            checkWanted = true;
            lastEdit = System.currentTimeMillis();
            afterEdit();
        } catch (Exception e) {
            ImGuiNotify.error(e.getMessage());
        }
    }

    private static void replacePlan(MixPlan next) {
        plan = next;
        UNDO.clear();
        REDO.clear();
        dirty = false;
        selectedTrack = plan.tracks.isEmpty() ? -1 : 0;
        selectedJoin = -1;
        issues = List.of();
        issuesFor = "";
        load = null;
        checkWanted = true;
        chartShiftNote = "";
        syncTextFields();
        afterEdit();
    }

    private static void clampSelection() {
        if (selectedTrack >= plan.tracks.size()) {
            selectedTrack = plan.tracks.size() - 1;
        }
        if (selectedJoin >= plan.transitions.size()) {
            selectedJoin = plan.transitions.size() - 1;
        }
    }

    // ------------------------------------------------------------------ frame

    public static void show(ImBoolean open) {
        if (main == null) {
            return;
        }
        if (!(main.getCurrentState() instanceof MusicSelector)) {
            // charts are loaded through the player resource; never while a chart is being played
            ImGui.setNextWindowSize(360, 90, ImGuiCond.FirstUseEver);
            if (ImGui.begin("NONSTOP MIX###nonstop-mix", open, ImGuiWindowFlags.None)) {
                ImGui.textWrapped(t("編集は選曲画面で行えます。", "The editor is available in Music Select."));
            }
            ImGui.end();
            return;
        }
        ensurePlan();
        pollJobs();
        ImGui.setNextWindowSize(1000, 680, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(ImGuiRenderer.windowWidth * 0.08f, ImGuiRenderer.windowHeight * 0.06f, ImGuiCond.FirstUseEver);
        if (ImGui.begin("NONSTOP MIX###nonstop-mix", open, ImGuiWindowFlags.None)) {
            renderHeader();
            renderHeavyStart();
            ImGui.separator();
            if (ImGui.beginTabBar("##nonstop-mix-tabs")) {
                if (ImGui.beginTabItem(t("セット編集", "Edit set") + "##nm-edit")) {
                    renderEditor();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem(t("おまかせ生成", "Generate") + "##nm-generate")) {
                    renderGenerator();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem(t("保存済みセット", "Saved sets") + "##nm-saved")) {
                    renderSaved();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem(t("IRで探す・公開", "IR browse / publish") + "##nm-ir")) {
                    renderIr();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem(t("記録", "Records") + "##nm-records")) {
                    renderRecords();
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
        }
        ImGui.end();
    }

    /** Shows the per-song result after a set; called every frame from the renderer. */
    public static void renderResultWindow() {
        MixResult result = MixRuntime.lastResult();
        if (result != null && result != shownResult) {
            shownResult = result;
            SHOW_RESULT.set(true);
        }
        if (!SHOW_RESULT.get() || shownResult == null) {
            return;
        }
        ImGui.setNextWindowSize(560, 420, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(ImGuiRenderer.windowWidth * 0.55f, ImGuiRenderer.windowHeight * 0.1f, ImGuiCond.FirstUseEver);
        if (ImGui.begin(t("NONSTOP MIX リザルト", "NONSTOP MIX result") + "###nonstop-mix-result", SHOW_RESULT)) {
            MixResult r = shownResult;
            ImGui.text(r.name.isEmpty() ? "NONSTOP MIX" : r.name);
            ImGui.text(String.format(Locale.ROOT, "EX %d / %d (%.2f%%)   BP %d   MAX COMBO %d",
                    r.exScore, r.maxExScore, r.rate(), r.badPoor, r.maxCombo));
            ImGui.text(t("クリア: ", "Clear: ") + clearName(r.clear)
                    + (r.finished ? "" : t("（途中まで）", " (not finished)")));
            renderTrackResults(r);
        }
        ImGui.end();
    }

    private static void renderTrackResults(MixResult r) {
        if (ImGui.beginTable("##nm-result-tracks", 9, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg
                | ImGuiTableFlags.ScrollY | ImGuiTableFlags.SizingStretchProp, 0, 0)) {
            ImGui.tableSetupColumn("#", ImGuiTableColumnFlags.WidthFixed, 24);
            ImGui.tableSetupColumn(t("曲", "Song"), ImGuiTableColumnFlags.WidthStretch, 0);
            ImGui.tableSetupColumn("EX", ImGuiTableColumnFlags.WidthFixed, 70);
            ImGui.tableSetupColumn("PG", ImGuiTableColumnFlags.WidthFixed, 40);
            ImGui.tableSetupColumn("GR", ImGuiTableColumnFlags.WidthFixed, 40);
            ImGui.tableSetupColumn("GD", ImGuiTableColumnFlags.WidthFixed, 40);
            ImGui.tableSetupColumn("BD", ImGuiTableColumnFlags.WidthFixed, 40);
            ImGui.tableSetupColumn("PR", ImGuiTableColumnFlags.WidthFixed, 40);
            ImGui.tableSetupColumn(t("未到達", "Left"), ImGuiTableColumnFlags.WidthFixed, 50);
            ImGui.tableHeadersRow();
            for (int i = 0; i < r.tracks.size(); i++) {
                MixResult.TrackResult row = r.tracks.get(i);
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ImGui.text(String.valueOf(i + 1));
                ImGui.tableNextColumn();
                ImGui.text(row.title);
                ImGui.tableNextColumn();
                ImGui.text(String.format(Locale.ROOT, "%d %.1f%%", row.exScore(), row.rate()));
                for (int j = 0; j < 5; j++) {
                    ImGui.tableNextColumn();
                    ImGui.text(String.valueOf(row.judges[j] + (j == 4 ? row.judges[5] : 0)));
                }
                ImGui.tableNextColumn();
                ImGui.text(String.valueOf(row.unreached));
            }
            ImGui.endTable();
        }
    }

    private static String clearName(int clear) {
        return MixResult.clearName(clear);
    }

    // ------------------------------------------------------------------ header

    private static void renderHeader() {
        ImGui.setNextItemWidth(260);
        if (ImGui.inputText(t("セット名", "Set name") + "##nm-name", NAME)) {
            if (!NAME.get().equals(plan.name)) {
                edit();
                plan.name = NAME.get();
                afterEdit();
            }
        }
        ImGui.sameLine();
        if (ImGui.button(t("新規", "New") + "##nm-new")) {
            if (dirty) {
                saveQuietly();
            }
            replacePlan(new MixPlan());
        }
        ImGui.sameLine();
        if (ImGui.button(t("保存", "Save") + "##nm-save")) {
            save();
        }
        ImGui.sameLine();
        ImGui.beginDisabled(UNDO.isEmpty());
        if (ImGui.button(t("元に戻す", "Undo") + "##nm-undo")) {
            undo();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(REDO.isEmpty());
        if (ImGui.button(t("やり直す", "Redo") + "##nm-redo")) {
            redo();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.textDisabled("|");
        ImGui.sameLine();
        boolean busy = preparation != null;
        ImGui.beginDisabled(busy || plan.tracks.isEmpty());
        if (ImGui.button(t("▶ 通しでプレイ", "▶ Play the set") + "##nm-play")) {
            prepare(plan, Pending.PLAY, null, plan.name);
        }
        ImGui.sameLine();
        if (ImGui.button(t("オートで通し再生", "Autoplay the set") + "##nm-auto")) {
            prepare(plan, Pending.AUTOPLAY_SET, null, plan.name);
        }
        ImGui.endDisabled();

        long total = estimatedTotal();
        ImGui.text(String.format(Locale.ROOT, t("%d曲  約%d分%02d秒  %s  判定 %s", "%d songs  about %d:%02d  %s  judge %s"),
                plan.tracks.size(), total / 60_000, total / 1000 % 60, plan.mode.hint, plan.judge.name()));
        if (!plan.irId.isEmpty()) {
            ImGui.sameLine();
            ImGui.textColored(GOOD[0], GOOD[1], GOOD[2], GOOD[3], "IR #" + plan.irId + " rev." + plan.irRevision);
        }
        if (dirty) {
            ImGui.sameLine();
            ImGui.textDisabled(t("（未保存）", "(unsaved)"));
        }
        if (plan.tracks.size() > 12) {
            ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3],
                    t("曲数が多いセットは全曲の音を最初に読み込むため、メモリを多く使い開始まで時間がかかります",
                            "Large sets load every song's sounds up front; they use a lot of memory and take longer to start"));
        }
        MixLoad current = load;
        if (current != null && current.suggestNoBga(plan.noBga)) {
            ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3],
                    String.format(Locale.ROOT, t("長い・重いセットです（%s）。BGAは全曲分を開始時に読み込むため、カクつきや画像の欠けが起きることがあります",
                            "Long or heavy set (%s). Every song's BGA is loaded at start; it may stutter or drop pictures"),
                            t(current.reason(true), current.reason(false))));
            ImGui.sameLine();
            if (ImGui.smallButton(t("BGAを使わない", "Turn BGA off") + "##nm-nobga-quick")) {
                edit();
                plan.noBga = true;
                afterEdit();
            }
        }
        renderPreparation();
    }

    private static void renderPreparation() {
        MixPreparation job = preparation;
        if (job == null) {
            return;
        }
        String label = switch (job.state()) {
            case LOADING -> t("譜面を読み込み中 ", "Loading charts ") + job.progress() + "/" + job.total();
            case LOUDNESS -> t("音量を解析中 ", "Matching loudness ") + job.progress() + "/" + job.total();
            case COMPILING -> t("つなぎを組み立て中", "Building the set");
            default -> "";
        };
        float fraction = job.total() == 0 ? 0f : Math.min(1f, job.progress() / (float) job.total());
        ImGui.progressBar(fraction, 300, 0, label);
        ImGui.sameLine();
        if (ImGui.button(t("中止", "Cancel") + "##nm-cancel-prepare")) {
            job.cancel();
            preparation = null;
            pending = Pending.NONE;
        }
    }

    // ------------------------------------------------------------------ editor

    private static void renderEditor() {
        float avail = ImGui.getContentRegionAvailX();
        float left = Math.max(340, avail * 0.42f);
        ImGui.beginChild("##nm-list", left, 0, true);
        renderTrackList();
        ImGui.endChild();
        ImGui.sameLine();
        ImGui.beginChild("##nm-detail", 0, 0, true);
        if (selectedJoin >= 0 && selectedJoin < plan.transitions.size()) {
            renderJoinDetail(selectedJoin);
        } else if (selectedTrack >= 0 && selectedTrack < plan.tracks.size()) {
            renderTrackDetail(selectedTrack);
        } else {
            ImGui.textWrapped(t("左の一覧から曲かつなぎを選ぶと、ここで細かく調整できます。曲は下の「曲を追加」から入れてください。",
                    "Pick a song or a join on the left to edit it here. Add songs below the list."));
            renderSetSettings();
        }
        ImGui.endChild();
    }

    private static void renderTrackList() {
        // toolbar
        boolean hasSelection = selectedTrack >= 0 && selectedTrack < plan.tracks.size() && selectedJoin < 0;
        ImGui.beginDisabled(!hasSelection || selectedTrack == 0);
        if (ImGui.button("↑##nm-up")) {
            edit();
            plan.moveTrack(selectedTrack, selectedTrack - 1);
            selectedTrack--;
            afterEdit();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(!hasSelection || selectedTrack >= plan.tracks.size() - 1);
        if (ImGui.button("↓##nm-down")) {
            edit();
            plan.moveTrack(selectedTrack, selectedTrack + 1);
            selectedTrack++;
            afterEdit();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(!hasSelection);
        if (ImGui.button(t("外す", "Remove") + "##nm-remove")) {
            edit();
            plan.removeTrack(selectedTrack);
            clampSelection();
            afterEdit();
        }
        ImGui.sameLine();
        if (ImGui.button(t("もう一度使う", "Use again") + "##nm-dup")) {
            edit();
            MixTrack copy = plan.tracks.get(selectedTrack).copy();
            copy.lockSong = false;
            plan.addTrack(selectedTrack + 1, copy);
            afterEdit();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button(t("セット設定", "Set options") + "##nm-set-options")) {
            selectedTrack = -1;
            selectedJoin = -1;
        }

        ImGui.separator();
        float listHeight = Math.max(160, ImGui.getContentRegionAvailY() - 220);
        ImGui.beginChild("##nm-tracks", 0, listHeight, false);
        for (int i = 0; i < plan.tracks.size(); i++) {
            MixTrack track = plan.tracks.get(i);
            ImGui.pushID(i);
            boolean selected = selectedTrack == i && selectedJoin < 0;
            String lock = track.lockSong ? "[固定] " : "";
            String label = String.format(Locale.ROOT, "%2d. %s%s", i + 1, lock, track.displayTitle());
            if (ImGui.selectable(label + "##track", selected)) {
                selectedTrack = i;
                selectedJoin = -1;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(track.artist + " / " + track.genre);
            }
            MixTrackAnalysis analysis = analysis(track);
            String range = rangeText(track, analysis);
            ImGui.textDisabled(String.format(Locale.ROOT, "     BPM %s  Lv%d  %s%s", bpmText(track, analysis), track.level,
                    range, library.find(track) == null ? t("  ※曲がありません", "  * missing") : ""));
            if (i < plan.transitions.size()) {
                MixTransition join = plan.transitions.get(i);
                boolean joinSelected = selectedJoin == i;
                String joinLabel = "      ↳ " + joinText(join) + (join.locked ? t(" [固定]", " [locked]") : "");
                if (hasIssueOnJoin(i)) {
                    joinLabel += t("  ⚠要確認", "  ⚠ check");
                }
                if (ImGui.selectable(joinLabel + "##join", joinSelected)) {
                    selectedJoin = i;
                    selectedTrack = i;
                }
            }
            ImGui.popID();
        }
        if (plan.tracks.isEmpty()) {
            ImGui.textWrapped(t("まだ曲がありません。下の検索か「選曲画面の曲を追加」で入れるか、「おまかせ生成」タブで作れます。",
                    "No songs yet. Add songs below, or make a set in the Generate tab."));
        }
        ImGui.endChild();
        ImGui.separator();
        renderAddSongs();
        renderIssues();
    }

    private static void renderAddSongs() {
        ImGui.text(t("曲を追加", "Add songs"));
        ImGui.sameLine();
        if (ImGui.smallButton(t("選曲画面で選んでいる曲", "Song selected in Music Select") + "##nm-add-selected")) {
            addSelectedSong();
        }
        ImGui.setNextItemWidth(Math.max(120, ImGui.getContentRegionAvailX() - 70));
        boolean enter = ImGui.inputText("##nm-search", SEARCH, imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);
        ImGui.sameLine();
        if (ImGui.button(t("検索", "Search") + "##nm-search-btn") || enter) {
            search();
        }
        ImGui.beginChild("##nm-search-results", 0, 90, false);
        for (int i = 0; i < searchResults.size(); i++) {
            SongData song = searchResults.get(i);
            ImGui.pushID(1000 + i);
            String text = String.format(Locale.ROOT, "%s  [%s] BPM%d Lv%d", song.getTitle() + " " + song.getSubtitle(),
                    song.getGenre(), song.getMaxbpm(), song.getLevel());
            if (ImGui.selectable(text, searchSelection == i, ImGuiSelectableFlags.AllowDoubleClick)) {
                searchSelection = i;
                if (ImGui.isMouseDoubleClicked(0)) {
                    addSong(song);
                }
            }
            ImGui.popID();
        }
        ImGui.endChild();
        ImGui.beginDisabled(searchSelection < 0 || searchSelection >= searchResults.size());
        if (ImGui.button(t("選んだ曲を追加（ダブルクリックでも可）", "Add the chosen song (or double-click)") + "##nm-add")) {
            addSong(searchResults.get(searchSelection));
        }
        ImGui.endDisabled();
    }

    private static void search() {
        String text = SEARCH.get().trim();
        searchSelection = -1;
        if (text.isEmpty() || main.getSongDatabase() == null) {
            searchResults = List.of();
            return;
        }
        List<SongData> results = new ArrayList<>();
        for (SongData song : main.getSongDatabase().getSongDatasByText(text)) {
            if (song.getPath() == null) {
                continue;
            }
            if (!plan.tracks.isEmpty() && song.getMode() != plan.mode.id) {
                continue;
            }
            results.add(song);
            if (results.size() >= 200) {
                break;
            }
        }
        searchResults = results;
    }

    private static void addSelectedSong() {
        if (!(main.getCurrentState() instanceof MusicSelector selector)) {
            ImGuiNotify.warning(t("選曲画面で使えます", "Available in Music Select"));
            return;
        }
        Bar bar = selector.getBarManager().getSelected();
        if (bar instanceof SongBar songBar && songBar.existsSong()) {
            addSong(songBar.getSongData());
        } else {
            ImGuiNotify.warning(t("曲を選んでください", "Select a song first"));
        }
    }

    private static void addSong(SongData song) {
        Mode mode = Mode.getMode(modeHint(song.getMode()));
        if (plan.tracks.isEmpty() && mode != null) {
            plan.mode = mode;
        } else if (mode != null && mode != plan.mode) {
            ImGuiNotify.warning(t("鍵盤モードが違う曲は入れられません", "Songs of another key mode cannot be added"));
            return;
        }
        edit();
        int index = selectedTrack >= 0 ? Math.min(plan.tracks.size(), selectedTrack + 1) : plan.tracks.size();
        MixTrack track = MixLibrary.trackFrom(song);
        plan.addTrack(index, track);
        selectedTrack = index;
        selectedJoin = -1;
        requestAnalysis(track);
        afterEdit();
    }

    private static String modeHint(int id) {
        for (Mode mode : Mode.values()) {
            if (mode.id == id) {
                return mode.hint;
            }
        }
        return Mode.BEAT_7K.hint;
    }

    private static void renderIssues() {
        if (issues.isEmpty()) {
            if (!plan.tracks.isEmpty() && issuesFor.equals(MixJson.playIdentity(plan))) {
                ImGui.textColored(GOOD[0], GOOD[1], GOOD[2], GOOD[3], t("問題は見つかりませんでした", "No problems found"));
            }
            return;
        }
        ImGui.text(t("確認が必要な点", "Things to check"));
        for (MixCompiler.Issue issue : issues) {
            float[] color = issue.severity() == MixCompiler.Severity.ERROR ? ERROR : WARNING;
            String where = issue.track() >= 0 ? String.format(Locale.ROOT, "#%d ", issue.track() + 1) : "";
            String time = issue.setTimeMs() >= 0 ? String.format(Locale.ROOT, " (%s)", clock(issue.setTimeMs())) : "";
            ImGui.textColored(color[0], color[1], color[2], color[3], where + t(issue.japanese(), issue.english()) + time);
            if (ImGui.isItemClicked(0)) {
                if (issue.join() >= 0) {
                    selectedJoin = issue.join();
                    selectedTrack = issue.join();
                } else if (issue.track() >= 0) {
                    selectedTrack = issue.track();
                    selectedJoin = -1;
                }
            }
        }
    }

    private static boolean hasIssueOnJoin(int join) {
        for (MixCompiler.Issue issue : issues) {
            if (issue.join() == join) {
                return true;
            }
        }
        return false;
    }

    private static final Mode[] MODES = {Mode.BEAT_7K, Mode.BEAT_14K, Mode.BEAT_5K, Mode.BEAT_10K, Mode.POPN_9K};

    private static void renderSetSettings() {
        ImGui.separator();
        ImGui.text(t("セットの設定", "Set options"));
        ImInt mode = new ImInt(0);
        String[] modeNames = new String[MODES.length];
        for (int i = 0; i < MODES.length; i++) {
            modeNames[i] = MODES[i].hint;
            if (MODES[i] == plan.mode) {
                mode.set(i);
            }
        }
        ImGui.beginDisabled(!plan.tracks.isEmpty());
        ImGui.setNextItemWidth(220);
        if (ImGui.combo(t("鍵盤モード", "Key mode") + "##nm-mode", mode, modeNames)) {
            edit();
            plan.mode = MODES[mode.get()];
            afterEdit();
        }
        ImGui.endDisabled();
        if (!plan.tracks.isEmpty()) {
            ImGui.sameLine();
            ImGui.textDisabled(t("（曲が入っている間は変えられません）", "(fixed while the set has songs)"));
        }
        ImInt judge = new ImInt(plan.judge.ordinal());
        String[] judges = {t("とてもやさしい", "Very easy"), t("やさしい", "Easy"), t("ふつう", "Normal"), t("むずかしい", "Hard"),
                t("とてもむずかしい", "Very hard"), t("曲の平均", "Average of songs"), t("一番きびしい曲", "Strictest song")};
        ImGui.setNextItemWidth(220);
        if (ImGui.combo(t("判定", "Judge") + "##nm-judge", judge, judges)) {
            edit();
            plan.judge = MixPlan.JudgeRule.values()[judge.get()];
            afterEdit();
        }
        float[] total = {(float) plan.totalScale};
        ImGui.setNextItemWidth(220);
        if (ImGui.sliderFloat(t("ゲージ回復 (TOTAL倍率)", "Gauge recovery (TOTAL scale)") + "##nm-total", total, 0.5f, 2.0f, "x%.2f")) {
            edit();
            plan.totalScale = total[0];
            afterEdit();
        }
        ImBoolean loud = new ImBoolean(plan.normalizeLoudness);
        if (ImGui.checkbox(t("曲ごとの音量をそろえる", "Match each song's loudness") + "##nm-loud", loud)) {
            edit();
            plan.normalizeLoudness = loud.get();
            afterEdit();
        }
        ImGui.sameLine();
        ImGuiRenderer.helpMarker(t("初回は曲ごとに音量解析をするため、開始まで時間がかかります。結果は保存され次回から速くなります。",
                "The first time, each song is analysed before the set starts; results are cached."));
        ImBoolean noBga = new ImBoolean(plan.noBga);
        if (ImGui.checkbox(t("BGAを使わない", "Play without BGA") + "##nm-nobga", noBga)) {
            edit();
            plan.noBga = noBga.get();
            afterEdit();
        }
        ImGui.sameLine();
        ImGuiRenderer.helpMarker(t("BGA・レイヤー・背景画像を読み込まずに遊びます。BGAは全曲分を開始時に読み込むため、長いセットや動画の多いセットで"
                        + "メモリ不足・カクつき・画像の欠けを防げます。記録は同じセットとして残ります。",
                "Plays without BGA, layers and the background image. Every song's BGA is loaded at start, so this avoids memory use,"
                        + " stutter and missing pictures in long or movie-heavy sets. Records stay with the same set."));
        renderChartShift();
        ImGui.text(t("説明（IR公開時に表示）", "Description (shown on IR)"));
        if (ImGui.inputTextMultiline("##nm-description", DESCRIPTION, Math.max(200, ImGui.getContentRegionAvailX()), 70)) {
            if (!DESCRIPTION.get().equals(plan.description)) {
                edit();
                plan.description = DESCRIPTION.get();
                afterEdit();
            }
        }
        ImGui.textDisabled(t("記録は通しプレイの完走・途中終了を区別して、このセット専用に保存されます。",
                "Records are kept for this set only."));
    }

    // ------------------------------------------------------------------ track detail

    private static void renderTrackDetail(int index) {
        MixTrack track = plan.tracks.get(index);
        MixTrackAnalysis analysis = analysis(track);
        ImGui.text(String.format(Locale.ROOT, "%d. %s", index + 1, track.displayTitle()));
        ImGui.textDisabled(track.artist + "   " + track.genre + "   Lv" + track.level + "   BPM " + bpmText(track, analysis));
        if (library.find(track) == null) {
            ImGui.textColored(ERROR[0], ERROR[1], ERROR[2], ERROR[3],
                    t("この曲はライブラリにありません。入手してから楽曲更新をしてください。",
                            "This song is not in your library. Get it and update the song database."));
            return;
        }
        renderChartSwap(index, track);
        if (analysis == null) {
            ImGui.text(ANALYSIS_FAILED.contains(track.sha256) ? t("譜面を読み込めませんでした", "Could not load the chart")
                    : t("譜面を解析しています…", "Analysing the chart..."));
            return;
        }
        ImGui.separator();
        ImGui.text(t("使う区間（左クリックで開始、右クリックで終了を指定）", "Used part (left-click: start, right-click: end)"));
        drawTrackGraph(track, analysis);
        long out = track.resolvedOut(analysis.chartEnd);
        ImBoolean snap = SNAP;
        ImGui.checkbox(t("小節にそろえる", "Snap to bars") + "##nm-snap", snap);

        ImGui.text(t("開始 ", "Start ") + clock(track.inMs));
        ImGui.sameLine(170);
        if (ImGui.smallButton(t("曲の頭", "Beginning") + "##nm-in-0")) {
            setIn(track, 0, analysis);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(t("−1小節", "−1 bar") + "##nm-in-m")) {
            setIn(track, stepBar(analysis, track.inMs, -1), analysis);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(t("+1小節", "+1 bar") + "##nm-in-p")) {
            setIn(track, stepBar(analysis, track.inMs, 1), analysis);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(t("最初のノーツの前", "Before first note") + "##nm-in-first")) {
            int bar = analysis.barIndexAtOrBefore(analysis.firstNoteMs);
            setIn(track, bar >= 0 ? analysis.bars[bar] : 0, analysis);
        }

        ImGui.text(t("終了 ", "End ") + clock(out) + (track.outMs < 0 ? t("（最後まで）", " (to the end)") : ""));
        ImGui.sameLine(170);
        if (ImGui.smallButton(t("最後まで", "To the end") + "##nm-out-end")) {
            edit();
            track.outMs = -1;
            afterEdit();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(t("−1小節", "−1 bar") + "##nm-out-m")) {
            setOut(track, stepBar(analysis, out, -1), analysis);
        }
        ImGui.sameLine();
        if (ImGui.smallButton(t("+1小節", "+1 bar") + "##nm-out-p")) {
            setOut(track, stepBar(analysis, out, 1), analysis);
        }
        ImGui.text(String.format(Locale.ROOT, t("長さ %s / 曲全体 %s", "Length %s / whole song %s"),
                clock(out - track.inMs), clock(analysis.chartEnd)));
        if (analysis.longNoteAcross(track.inMs) || track.outMs >= 0 && analysis.longNoteAcross(track.outMs)) {
            ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3],
                    t("区間の端でロングノーツが切れています。小節を動かしてください。",
                            "A long note is cut at the edge of the part; move the edge by a bar."));
        }

        ImGui.separator();
        float[] gain = {(float) track.gainDb};
        ImGui.setNextItemWidth(220);
        if (ImGui.sliderFloat(t("音量補正", "Volume") + "##nm-gain", gain, -12f, 6f, "%.1f dB")) {
            if (Math.abs(gain[0] - track.gainDb) > 0.001) {
                edit();
                track.gainDb = gain[0];
                afterEdit();
            }
        }
        ImBoolean lockSong = new ImBoolean(track.lockSong);
        if (ImGui.checkbox(t("この曲を固定（おまかせ生成で入れ替えない）", "Keep this song (generator keeps it)") + "##nm-lock-song", lockSong)) {
            edit();
            track.lockSong = lockSong.get();
            afterEdit();
        }
        ImBoolean lockRange = new ImBoolean(track.lockRange);
        if (ImGui.checkbox(t("使う区間を固定", "Keep the used part") + "##nm-lock-range", lockRange)) {
            edit();
            track.lockRange = lockRange.get();
            afterEdit();
        }
        ImGui.separator();
        boolean busy = preparation != null;
        ImGui.beginDisabled(busy);
        if (ImGui.button(t("この曲の区間をオートで聴く", "Listen to this part (autoplay)") + "##nm-listen-track")) {
            prepare(MixPreviews.single(plan, index), Pending.LISTEN, null, track.displayTitle());
        }
        ImGui.sameLine();
        if (ImGui.button(t("ここから最後まで試奏", "Try from here to the end") + "##nm-try-from")) {
            prepare(MixPreviews.from(plan, index), Pending.TRY, null, plan.name);
        }
        ImGui.endDisabled();
    }

    private static final ImBoolean SNAP = new ImBoolean(true);

    // ------------------------------------------------------------------ other charts (差分)

    private static List<SongData> chartChoices = List.of();
    private static String chartChoicesFor = "";
    private static String chartShiftNote = "";

    private static void renderChartSwap(int index, MixTrack track) {
        if (ImGui.smallButton(t("別の差分に替える", "Use another chart of this song") + "##nm-swap-chart")) {
            SongData current = library.find(track);
            chartChoices = current == null ? List.of()
                    : MixChartSwap.alternatives(current, MixChartSwap.charts(library).sameFolder(current), plan.mode.id);
            chartChoicesFor = track.sha256;
            ImGui.openPopup("##nm-swap-chart-popup");
        }
        if (!ImGui.beginPopup("##nm-swap-chart-popup")) {
            return;
        }
        if (!chartChoicesFor.equals(track.sha256)) {
            ImGui.closeCurrentPopup();
        } else if (chartChoices.isEmpty()) {
            ImGui.text(t("同じ曲フォルダにほかの差分がありません", "No other chart of this song in its folder"));
        } else {
            ImGui.textDisabled(t("使う区間・つなぎはそのまま。区間は新しい譜面の長さに収めます。",
                    "The used part and the joins stay; the part is kept inside the new chart."));
            for (int i = 0; i < chartChoices.size(); i++) {
                SongData chart = chartChoices.get(i);
                ImGui.pushID(3000 + i);
                String text = String.format(Locale.ROOT, "%-8s Lv%-3d %5d notes  %s", MixChartSwap.difficultyName(chart.getDifficulty()),
                        chart.getLevel(), chart.getNotes(), chart.getSubtitle() == null ? "" : chart.getSubtitle());
                if (ImGui.selectable(text)) {
                    edit();
                    MixChartSwap.replaceChart(track, chart);
                    requestAnalysis(track);
                    afterEdit();
                    chartChoicesFor = "";
                    ImGui.closeCurrentPopup();
                }
                ImGui.popID();
            }
        }
        ImGui.endPopup();
    }

    private static void renderChartShift() {
        ImGui.beginDisabled(plan.tracks.isEmpty());
        if (ImGui.button(t("全曲を一段易しい差分に", "All songs one chart easier") + "##nm-shift-easier")) {
            shiftSet(-1);
        }
        ImGui.sameLine();
        if (ImGui.button(t("一段難しい差分に", "One chart harder") + "##nm-shift-harder")) {
            shiftSet(1);
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGuiRenderer.helpMarker(t("同じ曲フォルダの差分に替えた新しいセットを別名で保存して開きます。元のセットはそのまま残り、記録も別になります。",
                "Saves and opens a new set that uses the neighbouring chart of each song. The original set and its records stay."));
        if (!chartShiftNote.isEmpty()) {
            ImGui.textWrapped(chartShiftNote);
        }
    }

    private static void shiftSet(int direction) {
        if (dirty) {
            saveQuietly();
        }
        MixChartSwap.Shifted shifted = MixChartSwap.shiftSet(plan, direction, MixChartSwap.charts(library));
        try {
            store().saveSet(shifted.plan());
        } catch (Exception e) {
            ImGuiNotify.error(t("保存できませんでした: ", "Could not save: ") + e.getMessage());
            return;
        }
        replacePlan(shifted.plan());
        savedSets = null;
        for (MixTrack track : plan.tracks) {
            requestAnalysis(track);
        }
        if (shifted.unchanged().isEmpty()) {
            chartShiftNote = "";
        } else {
            List<String> titles = new ArrayList<>();
            for (MixTrack track : shifted.unchanged()) {
                titles.add(track.displayTitle());
            }
            chartShiftNote = (direction < 0 ? t("易しい差分がないためそのままの曲: ", "Songs kept (no easier chart): ")
                    : t("難しい差分がないためそのままの曲: ", "Songs kept (no harder chart): ")) + String.join(" / ", titles);
        }
        ImGuiNotify.success(t("新しいセットとして保存しました: ", "Saved as a new set: ") + plan.name);
    }

    private static long stepBar(MixTrackAnalysis analysis, long ms, int direction) {
        if (analysis.bars.length == 0) {
            return Math.max(0, ms + direction * 2000L);
        }
        int index = analysis.barIndexAtOrBefore(ms);
        boolean onBar = index >= 0 && analysis.bars[index] == ms;
        if (direction < 0) {
            int target = onBar ? index - 1 : index;
            return target >= 0 ? analysis.bars[target] : 0;
        }
        int target = index + 1;
        if (target < analysis.bars.length) {
            return analysis.bars[target];
        }
        long last = analysis.bars[analysis.bars.length - 1];
        long bar = analysis.barMsAtEnd();
        return last + ((ms - last) / bar + 1) * bar;
    }

    private static void setIn(MixTrack track, long ms, MixTrackAnalysis analysis) {
        long value = SNAP.get() ? analysis.nearestBar(ms) : ms;
        value = Math.max(0, Math.min(value, track.resolvedOut(analysis.chartEnd) - 1000));
        if (value != track.inMs) {
            edit();
            track.inMs = value;
            afterEdit();
        }
    }

    private static void setOut(MixTrack track, long ms, MixTrackAnalysis analysis) {
        long value = SNAP.get() ? analysis.nearestBar(ms) : ms;
        value = Math.max(track.inMs + 1000, value);
        if (value != track.outMs) {
            edit();
            track.outMs = value;
            afterEdit();
        }
    }

    private static void drawTrackGraph(MixTrack track, MixTrackAnalysis analysis) {
        float width = Math.max(200, ImGui.getContentRegionAvailX());
        float height = 110;
        float x0 = ImGui.getCursorScreenPosX();
        float y0 = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("##nm-track-graph", width, height);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList draw = ImGui.getWindowDrawList();
        long span = Math.max(1000L, Math.max(analysis.chartEnd, track.resolvedOut(analysis.chartEnd)) + 1000L);
        draw.addRectFilled(x0, y0, x0 + width, y0 + height, COLOR_BG);
        float maxActivity = 1;
        for (float value : analysis.activity) {
            maxActivity = Math.max(maxActivity, value);
        }
        float maxDensity = 1;
        for (float value : analysis.density) {
            maxDensity = Math.max(maxDensity, value);
        }
        float graphHeight = height - 14;
        for (int i = 0; i < analysis.activity.length; i++) {
            float xa = x0 + width * (i * (float) MixTrackAnalysis.BIN_MS / span);
            float xb = x0 + width * ((i + 1) * (float) MixTrackAnalysis.BIN_MS / span);
            float h = graphHeight * analysis.activity[i] / maxActivity;
            draw.addRectFilled(xa, y0 + graphHeight - h, Math.max(xa + 1, xb), y0 + graphHeight, COLOR_ACTIVITY);
            float d = graphHeight * 0.8f * analysis.density[i] / maxDensity;
            draw.addRectFilled(xa, y0 + graphHeight - d, Math.max(xa + 1, xb), y0 + graphHeight - d + 2, COLOR_DENSITY);
        }
        int firstNoteBar = Math.max(0, analysis.barIndexAtOrBefore(analysis.firstNoteMs));
        for (int i = 0; i < analysis.bars.length; i++) {
            float x = x0 + width * (analysis.bars[i] / (float) span);
            boolean phrase = (i - firstNoteBar) % 8 == 0;
            draw.addLine(x, y0, x, y0 + graphHeight, phrase ? COLOR_PHRASE : COLOR_BAR, 1);
        }
        for (long[] ln : analysis.longNotes) {
            float xa = x0 + width * (ln[0] / (float) span);
            float xb = x0 + width * (ln[1] / (float) span);
            draw.addRectFilled(xa, y0 + graphHeight + 3, Math.max(xa + 1, xb), y0 + graphHeight + 9, COLOR_LN);
        }
        long out = track.resolvedOut(analysis.chartEnd);
        float xin = x0 + width * (track.inMs / (float) span);
        float xout = x0 + width * (out / (float) span);
        draw.addRectFilled(xin, y0, xout, y0 + height, COLOR_RANGE);
        draw.addLine(xin, y0, xin, y0 + height, COLOR_MARKER, 2);
        draw.addLine(xout, y0, xout, y0 + height, COLOR_MARKER, 2);
        draw.addText(xin + 3, y0 + 2, COLOR_TEXT, "IN");
        draw.addText(Math.max(x0, xout - 30), y0 + 2, COLOR_TEXT, "OUT");
        if (hovered) {
            long at = (long) ((ImGui.getMousePosX() - x0) / width * span);
            ImGui.setTooltip(clock(at) + (analysis.longNoteAcross(at) ? t("  ロングノーツ中", "  inside a long note") : ""));
            if (ImGui.isMouseClicked(0)) {
                setIn(track, at, analysis);
            } else if (ImGui.isMouseClicked(1)) {
                setOut(track, at, analysis);
            }
        }
    }

    // ------------------------------------------------------------------ join detail

    private static void renderJoinDetail(int j) {
        MixTrack a = plan.tracks.get(j);
        MixTrack b = plan.tracks.get(j + 1);
        MixTransition join = plan.transitions.get(j);
        MixTrackAnalysis aa = analysis(a);
        MixTrackAnalysis ab = analysis(b);
        ImGui.text(String.format(Locale.ROOT, t("つなぎ %d → %d", "Join %d → %d"), j + 1, j + 2));
        ImGui.textDisabled(a.displayTitle() + "  →  " + b.displayTitle());
        if (aa != null && ab != null) {
            double bpmA = aa.endBpm;
            double bpmB = ab.startBpm;
            double diff = Math.abs(bpmA - bpmB) / Math.max(bpmA, bpmB) * 100;
            ImGui.text(String.format(Locale.ROOT, t("テンポ %.1f → %.1f （差 %.1f%%）", "Tempo %.1f → %.1f (%.1f%% apart)"),
                    bpmA, bpmB, diff));
            if (diff > 3 && join.type == MixTransition.Type.BLEND) {
                ImGui.sameLine();
                ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3],
                        t("テンポが離れているので混ぜるとずれて聞こえます", "Far tempos drift apart in a blend"));
            }
        }
        ImGui.separator();
        ImGui.text(t("つなぎ方", "Join type"));
        String[] types = {t("混ぜる（クロスフェード）", "Blend (crossfade)"), t("カット", "Cut"),
                t("フェードアウトして次へ", "Fade out, then next"), t("止めてフェードイン", "Stop, then fade in")};
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                ImGui.sameLine();
            }
            if (ImGui.radioButton(types[i] + "##nm-type" + i, join.type.ordinal() == i)) {
                if (join.type.ordinal() != i) {
                    edit();
                    join.type = MixTransition.Type.values()[i];
                    if (join.type == MixTransition.Type.FADE_OUT || join.type == MixTransition.Type.FADE_IN) {
                        join.overlapMs = Math.min(join.overlapMs, MixTransition.DEFAULT_FADE_MS);
                    } else if (join.type == MixTransition.Type.BLEND && join.overlapMs < 1000) {
                        join.overlapMs = MixTransition.DEFAULT_BLEND_MS;
                        join.handoffMs = join.overlapMs / 2;
                    }
                    afterEdit();
                }
            }
        }
        long barMs = aa != null ? aa.barMsAtEnd() : 2000;
        switch (join.type) {
            case BLEND -> {
                float[] overlap = {join.overlapMs / 1000f};
                ImGui.setNextItemWidth(260);
                if (ImGui.sliderFloat(t("重ねる長さ", "Overlap") + "##nm-overlap", overlap, 0.5f, 40f, "%.2f s")) {
                    edit();
                    long value = Math.round(overlap[0] * 1000);
                    join.handoffMs = Math.round(join.handoffMs * (value / (double) Math.max(1, join.overlapMs)));
                    join.overlapMs = value;
                    afterEdit();
                }
                ImGui.sameLine();
                ImGui.text(String.format(Locale.ROOT, t("= %.1f小節", "= %.1f bars"), join.overlapMs / (double) barMs));
                for (int bars : new int[] {2, 4, 8, 16}) {
                    if (ImGui.smallButton(String.format(Locale.ROOT, t("%d小節", "%d bars"), bars) + "##nm-bars" + bars)) {
                        edit();
                        long value = bars * barMs;
                        join.handoffMs = Math.round(join.handoffMs * (value / (double) Math.max(1, join.overlapMs)));
                        join.overlapMs = value;
                        afterEdit();
                    }
                    ImGui.sameLine();
                }
                ImGui.newLine();
                float[] handoff = {join.effectiveHandoffMs() / 1000f};
                ImGui.setNextItemWidth(260);
                if (ImGui.sliderFloat(t("譜面が次の曲に替わる位置", "Lanes change hands at") + "##nm-handoff", handoff, 0f,
                        join.overlapMs / 1000f, "%.2f s")) {
                    edit();
                    join.handoffMs = Math.round(handoff[0] * 1000);
                    afterEdit();
                }
                ImGui.sameLine();
                ImGuiRenderer.helpMarker(t("音は重なっている間ずっと両方鳴ります。叩く譜面だけがこの位置で次の曲に替わります。ロングノーツをまたぐ位置は使えません。",
                        "Both songs sound during the overlap; only the playable chart switches here. Points inside a long note cannot be used."));
                if (aa != null && ab != null && ImGui.button(t("安全な位置を自動で選ぶ", "Pick a safe point") + "##nm-safe-handoff")) {
                    long found = findSafeHandoff(plan, j, aa, ab, barMs);
                    if (found >= 0) {
                        edit();
                        join.handoffMs = found;
                        afterEdit();
                    } else {
                        ImGuiNotify.warning(t("安全な位置がありません。重ねる長さを変えてください", "No safe point; change the overlap"));
                    }
                }
                curveCombo(join);
            }
            case FADE_OUT, FADE_IN -> {
                float[] fade = {join.overlapMs / 1000f};
                ImGui.setNextItemWidth(260);
                if (ImGui.sliderFloat(t("フェードの長さ", "Fade length") + "##nm-fade", fade, 0.2f, 20f, "%.2f s")) {
                    edit();
                    join.overlapMs = Math.round(fade[0] * 1000);
                    afterEdit();
                }
                gapSlider(join);
                curveCombo(join);
            }
            case CUT -> gapSlider(join);
        }
        ImBoolean locked = new ImBoolean(join.locked);
        if (ImGui.checkbox(t("このつなぎを固定（おまかせ生成で変えない）", "Keep this join (generator keeps it)") + "##nm-lock-join", locked)) {
            edit();
            join.locked = locked.get();
            afterEdit();
        }
        ImGui.separator();
        if (aa != null && ab != null) {
            drawJoinGraph(j, aa, ab);
        } else {
            ImGui.text(t("譜面を解析しています…", "Analysing the charts..."));
        }
        ImGui.separator();
        boolean busy = preparation != null;
        ImGui.beginDisabled(busy);
        if (ImGui.button(t("このつなぎをオートで聴く", "Listen to this join (autoplay)") + "##nm-listen-join")) {
            prepare(MixPreviews.join(plan, j), Pending.LISTEN, MixPreviews::narrowJoin, plan.name);
        }
        ImGui.sameLine();
        if (ImGui.button(t("このつなぎを試奏", "Try this join") + "##nm-try-join")) {
            prepare(MixPreviews.join(plan, j), Pending.TRY, MixPreviews::narrowJoin, plan.name);
        }
        ImGui.sameLine();
        ImGui.beginDisabled(aa == null || ab == null);
        if (ImGui.button(t("このつなぎをおまかせ", "Auto-plan this join") + "##nm-auto-join")) {
            edit();
            MixJoinPlanner.planJoin(plan, j, aa, ab, joinSettings());
            afterEdit();
        }
        ImGui.endDisabled();
        ImGui.endDisabled();
    }

    private static long findSafeHandoff(MixPlan trial, int j, MixTrackAnalysis aa, MixTrackAnalysis ab, long barMs) {
        MixTransition join = trial.transitions.get(j);
        MixTrack a = trial.tracks.get(j);
        MixTrack b = trial.tracks.get(j + 1);
        long overlap = join.overlapMs;
        long aStart = a.resolvedOut(aa.chartEnd) - overlap;
        long best = -1;
        double bestScore = Double.MAX_VALUE;
        for (long offset = 250; offset < overlap; offset += 250) {
            if (aa.longNoteAcross(aStart + offset) || ab.longNoteAcross(b.inMs + offset)) {
                continue;
            }
            boolean onBar = offset % barMs < 3 || barMs - offset % barMs < 3;
            double score = Math.abs(offset - join.handoffMs) / 1000.0 + (onBar ? 0 : 0.5);
            if (score < bestScore) {
                bestScore = score;
                best = offset;
            }
        }
        return best;
    }

    private static void gapSlider(MixTransition join) {
        float[] gap = {join.gapMs / 1000f};
        ImGui.setNextItemWidth(260);
        if (ImGui.sliderFloat(t("曲間の無音", "Silence between songs") + "##nm-gap", gap, 0f, 4f, "%.2f s")) {
            edit();
            join.gapMs = Math.round(gap[0] * 1000);
            afterEdit();
        }
    }

    private static void curveCombo(MixTransition join) {
        String[] curves = {t("直線", "Linear"), t("自然（等パワー）", "Natural (equal power)"),
                t("前の曲を長く残す", "Keep the previous song longer"), t("次の曲を早く上げる", "Bring the next song in early")};
        ImInt curve = new ImInt(join.curve.ordinal());
        ImGui.setNextItemWidth(260);
        if (ImGui.combo(t("音量カーブ", "Volume curve") + "##nm-curve", curve, curves)) {
            edit();
            join.curve = MixTransition.Curve.values()[curve.get()];
            afterEdit();
        }
    }

    private static void drawJoinGraph(int j, MixTrackAnalysis aa, MixTrackAnalysis ab) {
        MixPlan.Layout layout = layoutWithAnalyses();
        if (layout == null) {
            return;
        }
        MixTransition join = plan.transitions.get(j);
        long from = layout.start()[j + 1] - 6000;
        long to = Math.max(layout.end()[j], layout.start()[j + 1] + join.incomingFadeMs()) + 6000;
        long span = Math.max(1000, to - from);
        float width = Math.max(200, ImGui.getContentRegionAvailX());
        float rowHeight = 56;
        float height = rowHeight * 2 + 26;
        float x0 = ImGui.getCursorScreenPosX();
        float y0 = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("##nm-join-graph", width, height);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(x0, y0, x0 + width, y0 + height, COLOR_BG);
        drawLane(draw, aa, layout.offset()[j], from, span, x0, y0, width, rowHeight, COLOR_ACTIVITY,
                plan.tracks.get(j).inMs, plan.tracks.get(j).resolvedOut(aa.chartEnd));
        drawLane(draw, ab, layout.offset()[j + 1], from, span, x0, y0 + rowHeight + 4, width, rowHeight, COLOR_ACTIVITY_B,
                plan.tracks.get(j + 1).inMs, plan.tracks.get(j + 1).resolvedOut(ab.chartEnd));
        // volume curves of both songs
        CompiledLike shape = new CompiledLike(layout, j, join);
        float prevX = -1;
        float prevA = 0;
        float prevB = 0;
        for (int i = 0; i <= 120; i++) {
            long t = from + span * i / 120;
            float x = x0 + width * i / 120f;
            float ga = (float) shape.gainA(t);
            float gb = (float) shape.gainB(t);
            float ya = y0 + rowHeight * (1 - ga);
            float yb = y0 + rowHeight + 4 + rowHeight * (1 - gb);
            if (prevX >= 0) {
                draw.addLine(prevX, prevA, x, ya, COLOR_GAIN_A, 2);
                draw.addLine(prevX, prevB, x, yb, COLOR_GAIN_B, 2);
            }
            prevX = x;
            prevA = ya;
            prevB = yb;
        }
        long handoff = layout.handoff()[j];
        float xh = x0 + width * ((handoff - from) / (float) span);
        draw.addLine(xh, y0, xh, y0 + rowHeight * 2 + 4, COLOR_HANDOFF, 2);
        draw.addText(xh + 3, y0 + rowHeight * 2 + 8, COLOR_HANDOFF, t("譜面交代", "Hand-off"));
        draw.addText(x0 + 4, y0 + 2, COLOR_TEXT, "A: " + shorten(plan.tracks.get(j).displayTitle()));
        draw.addText(x0 + 4, y0 + rowHeight + 6, COLOR_TEXT, "B: " + shorten(plan.tracks.get(j + 1).displayTitle()));
        if (hovered) {
            long at = from + (long) ((ImGui.getMousePosX() - x0) / width * span);
            ImGui.setTooltip(clock(at) + t("  クリックで譜面交代位置をここに", "  click to move the hand-off here"));
            if (ImGui.isMouseClicked(0) && join.type == MixTransition.Type.BLEND) {
                long offset = at - layout.start()[j + 1];
                if (offset >= 0 && offset <= join.overlapMs) {
                    long snapped = SNAP.get() ? Math.round(offset / (double) aa.barMsAtEnd()) * aa.barMsAtEnd() : offset;
                    edit();
                    join.handoffMs = Math.max(0, Math.min(join.overlapMs, snapped));
                    afterEdit();
                }
            }
        }
        ImGui.textDisabled(t("青=前の曲 / 橙=次の曲 / 線=音量 / 黄色=ロングノーツ / 赤=譜面交代",
                "Blue = previous / orange = next / lines = volume / yellow = long notes / red = hand-off"));
    }

    private static void drawLane(ImDrawList draw, MixTrackAnalysis analysis, long offset, long from, long span, float x0,
            float y0, float width, float height, int color, long in, long out) {
        float max = 1;
        for (float value : analysis.activity) {
            max = Math.max(max, value);
        }
        int binFrom = (int) Math.max(0, (from - offset) / MixTrackAnalysis.BIN_MS);
        int binTo = (int) Math.min(analysis.activity.length - 1, (from + span - offset) / MixTrackAnalysis.BIN_MS);
        for (int i = binFrom; i <= binTo; i++) {
            long chart = (long) i * MixTrackAnalysis.BIN_MS;
            if (chart < in || chart > out) {
                continue;
            }
            float xa = x0 + width * ((chart + offset - from) / (float) span);
            float xb = x0 + width * ((chart + MixTrackAnalysis.BIN_MS + offset - from) / (float) span);
            float h = height * analysis.activity[i] / max;
            draw.addRectFilled(xa, y0 + height - h, Math.max(xa + 1, xb), y0 + height, color);
        }
        for (long bar : analysis.bars) {
            long set = bar + offset;
            if (set >= from && set <= from + span && bar >= in && bar <= out) {
                float x = x0 + width * ((set - from) / (float) span);
                draw.addLine(x, y0, x, y0 + height, COLOR_BAR, 1);
            }
        }
        for (long[] ln : analysis.longNotes) {
            if (ln[1] < in || ln[0] > out) {
                continue;
            }
            float xa = x0 + width * ((ln[0] + offset - from) / (float) span);
            float xb = x0 + width * ((ln[1] + offset - from) / (float) span);
            if (xb < x0 || xa > x0 + width) {
                continue;
            }
            draw.addRectFilled(Math.max(x0, xa), y0 + height - 5, Math.min(x0 + width, Math.max(xa + 1, xb)), y0 + height, COLOR_LN);
        }
    }

    /** Volume shape of a join for drawing, computed the same way as {@link CompiledMix#gain}. */
    private record CompiledLike(MixPlan.Layout layout, int j, MixTransition join) {
        double gainA(long t) {
            long end = layout.end()[j];
            return switch (join.type) {
                case BLEND -> t <= layout.start()[j + 1] ? 1 : t >= end ? 0
                        : join.outgoing((t - layout.start()[j + 1]) / (double) Math.max(1, end - layout.start()[j + 1]));
                case FADE_OUT -> t <= end - join.outgoingFadeMs() ? 1 : t >= end ? 0
                        : join.outgoing((t - (end - join.outgoingFadeMs())) / (double) Math.max(1, join.outgoingFadeMs()));
                case CUT, FADE_IN -> t < end ? 1 : 0;
            };
        }

        double gainB(long t) {
            long start = layout.start()[j + 1];
            if (t < start) {
                return 0;
            }
            long fade = join.incomingFadeMs();
            return fade > 0 && t < start + fade ? join.incoming((t - start) / (double) fade) : 1;
        }
    }

    // ------------------------------------------------------------------ generator

    private static void renderGenerator() {
        ImGui.textWrapped(t("条件を選ぶと、曲選び・曲順・使う区間・つなぎまで自動で作ります。結果は「適用」するまで今のセットを変えません。固定した曲やつなぎはそのまま残ります。",
                "Choose conditions and the set is built for you: songs, order, used parts and joins. Nothing changes until you apply it. Locked songs and joins are kept."));
        ImGui.separator();
        String[] scopes = {t("全部おまかせ", "Everything"), t("今の曲で曲順とつなぎ", "Order and joins of current songs"),
                t("曲順はそのまま、つなぎだけ", "Joins only"), t("選んだ範囲の曲だけ入れ替え", "Replace a range of songs")};
        for (int i = 0; i < scopes.length; i++) {
            if (i > 0) {
                ImGui.sameLine();
            }
            ImGui.radioButton(scopes[i] + "##nm-scope" + i, GEN_SCOPE, i);
        }
        int scope = GEN_SCOPE.get();
        if (scope == 0 || scope == 3) {
            ImGui.setNextItemWidth(240);
            ImGui.sliderInt(t("目標の長さ（分）", "Target length (min)") + "##nm-minutes", GEN_MINUTES, 3, 90);
            ImGui.setNextItemWidth(240);
            ImGui.combo(t("ジャンルの決め方", "Genre rule") + "##nm-genre-rule", GEN_GENRE_RULE,
                    new String[] {t("同じジャンルだけ", "Same genre only"), t("近いジャンルも", "Close genres too"),
                            t("ジャンル問わず", "Any genre")});
            ImGui.setNextItemWidth(240);
            ImGui.combo(t("ジャンルの系統", "Genre family") + "##nm-genre-family", GEN_FAMILY, familyLabels());
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(t("系統を選ぶと、表記ゆれ（Drum'n'Bass / DnB / ドラムンベース など）をまとめて扱います",
                        "A family covers the many spellings of a genre (Drum'n'Bass / DnB / ドラムンベース…)"));
            }
            ImGui.beginDisabled(GEN_FAMILY.get() > 0);
            ImGui.setNextItemWidth(240);
            ImGui.inputText(t("ジャンル（空欄=基準曲から）", "Genre (empty = from the reference song)") + "##nm-genre", GEN_GENRE);
            ImGui.sameLine();
            if (selectedTrack >= 0 && selectedTrack < plan.tracks.size()
                    && ImGui.smallButton(t("選択中の曲のジャンル", "Selected song's genre") + "##nm-genre-pick")) {
                GEN_GENRE.set(plan.tracks.get(selectedTrack).genre);
            }
            ImGui.endDisabled();
            renderGenreCount();
            ImGui.setNextItemWidth(240);
            ImGui.inputText(t("曲名・アーティスト等で絞る", "Filter by title / artist") + "##nm-filter", GEN_FILTER);
            ImGui.setNextItemWidth(240);
            ImGui.combo(t("難易度の目盛り", "Difficulty scale") + "##nm-scale", GEN_SCALE,
                    new String[] {t("通常 LV1–LV12", "Normal LV1–LV12"), t("発狂 ★1–★25", "Insane ★1–★25")});
            MixDifficulty.Scale scale = MixDifficulty.Scale.values()[GEN_SCALE.get()];
            int[] levels = GEN_LEVELS[GEN_SCALE.get()];
            ImGui.setNextItemWidth(240);
            if (ImGui.sliderInt2(t("レベルの範囲", "Level range") + "##nm-levels", levels, scale.min, scale.max,
                    scale.prefix + "%d")) {
                levels[0] = Math.max(scale.min, Math.min(levels[0], scale.max));
                levels[1] = Math.max(levels[0], Math.min(levels[1], scale.max));
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(t("難易度表（☆/★）の位置で選びます。表にない譜面はノーツ密度から推定します",
                        "Uses the chart's place in your ☆/★ difficulty tables; charts on no table are estimated from note density"));
            }
            ImGui.setNextItemWidth(240);
            ImGui.combo(t("難しさの流れ", "Difficulty flow") + "##nm-curve-gen", GEN_CURVE,
                    new String[] {t("一定", "Flat"), t("だんだん上げる", "Rising"), t("だんだん下げる", "Falling"),
                            t("山を2回", "Two peaks"), t("最後に山場", "Peak at the end")});
            if (scope == 0) {
                ImGui.checkbox(t("基準曲から始める（セットで選んだ曲、なければ選曲画面で選んでいる曲）",
                        "Start from a reference song (the selected set song, else the song selected in Music Select)")
                        + "##nm-reference", GEN_REFERENCE);
                if (GEN_REFERENCE.get()) {
                    Reference reference = reference();
                    if (reference != null) {
                        ImGui.textDisabled("    → " + reference.track().displayTitle());
                    } else {
                        ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3], "    " + t(
                                "基準曲がありません。セットの曲か、選曲画面で曲を選んでください",
                                "No reference song: select a song in the set or in Music Select"));
                    }
                }
            }
            if (scope == 3) {
                GEN_RANGE[0] = Math.max(1, Math.min(GEN_RANGE[0], Math.max(1, plan.tracks.size())));
                GEN_RANGE[1] = Math.max(GEN_RANGE[0], Math.min(GEN_RANGE[1], Math.max(1, plan.tracks.size())));
                ImGui.setNextItemWidth(240);
                ImGui.sliderInt2(t("入れ替える範囲（曲番号）", "Songs to replace (#)") + "##nm-range", GEN_RANGE, 1,
                        Math.max(1, plan.tracks.size()));
            }
        }
        ImGui.separator();
        ImGui.text(t("つなぎ方", "Joins"));
        ImGui.setNextItemWidth(240);
        ImGui.combo(t("曲の使い方", "How much of each song") + "##nm-style", GEN_STYLE,
                new String[] {t("1曲まるごと", "Whole songs"), t("長め（約2分半）", "Long (~2.5 min)"), t("短め（約1分強）", "Short (~1.25 min)")});
        ImGui.setNextItemWidth(240);
        ImGui.combo(t("混ぜる長さ", "Blend length") + "##nm-bars-gen", GEN_BARS,
                new String[] {t("2小節", "2 bars"), t("4小節", "4 bars"), t("8小節", "8 bars"), t("16小節", "16 bars")});
        ImGui.setNextItemWidth(240);
        ImGui.sliderFloat(t("混ぜてよいテンポ差", "Max tempo gap to blend") + "##nm-tol", GEN_TOLERANCE, 0f, 8f, "%.1f %%");
        ImGui.checkbox(t("混ぜる", "Blend") + "##nm-allow-blend", GEN_BLEND);
        ImGui.sameLine();
        ImGui.checkbox(t("フェード", "Fade") + "##nm-allow-fade", GEN_FADE);
        ImGui.sameLine();
        ImGui.checkbox(t("カット", "Cut") + "##nm-allow-cut", GEN_CUT);
        ImGui.separator();

        boolean running = generator != null && generator.state() == MixGenerator.State.RUNNING;
        ImGui.beginDisabled(running || (scope != 0 && plan.tracks.isEmpty()));
        if (ImGui.button(t("作る", "Generate") + "##nm-generate")) {
            startGenerator();
        }
        ImGui.sameLine();
        if (ImGui.button(t("別の案を作る", "Another idea") + "##nm-reroll")) {
            GENERATE.sequence.seed = System.nanoTime();
            startGenerator();
        }
        ImGui.endDisabled();
        if (running) {
            ImGui.sameLine();
            ImGui.text(generator.step());
            ImGui.sameLine();
            if (ImGui.button(t("中止", "Cancel") + "##nm-gen-cancel")) {
                generator.cancel();
            }
        }
        if (generator != null && generator.state() == MixGenerator.State.FAILED) {
            ImGui.textColored(ERROR[0], ERROR[1], ERROR[2], ERROR[3], generator.error());
        }
        if (generator != null) {
            for (String note : generator.notes()) {
                ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3], note);
            }
        }
        if (generated != null) {
            ImGui.separator();
            ImGui.text(t("できあがった案", "Result"));
            ImGui.beginChild("##nm-generated", 0, Math.max(120, ImGui.getContentRegionAvailY() - 40), true);
            for (int i = 0; i < generated.tracks.size(); i++) {
                MixTrack track = generated.tracks.get(i);
                double axis = consumedGenerator != null ? consumedGenerator.difficulty(track) : Double.NaN;
                ImGui.text(String.format(Locale.ROOT, "%2d. %s  [%s] BPM%.0f %s", i + 1, track.displayTitle(), track.genre,
                        track.bpm, Double.isNaN(axis) ? "Lv" + track.level : MixDifficulty.label(axis)));
                if (i < generated.transitions.size()) {
                    ImGui.textDisabled("      ↳ " + joinText(generated.transitions.get(i)));
                }
            }
            ImGui.endChild();
            if (ImGui.button(t("この案を適用（元に戻せます）", "Apply (can be undone)") + "##nm-apply")) {
                boolean newSet = consumedGenerator != null && MixGenerator.makesNewSet(plan, consumedGenerator.scope());
                if (newSet) {
                    // the current set is kept as it is (saved even if it never was); the result becomes another set
                    saveQuietly();
                }
                edit();
                MixPlan next = generated;
                if (newSet) {
                    next.id = java.util.UUID.randomUUID().toString();
                    next.name = defaultName(next);
                    next.description = "";
                    next.irId = "";
                    next.irRevision = 0;
                } else {
                    next.id = plan.id;
                    next.name = plan.name.isEmpty() ? defaultName(next) : plan.name;
                    next.description = plan.description;
                    next.irId = plan.irId;
                    next.irRevision = plan.irRevision;
                }
                plan = next;
                syncTextFields();
                generated = null;
                selectedTrack = plan.tracks.isEmpty() ? -1 : 0;
                selectedJoin = -1;
                afterEdit();
                ImGuiNotify.success(newSet
                        ? t("新しいセットとして適用しました（前のセットは保存済みセットに残ります）",
                                "Applied as a new set (the previous one stays in Saved sets)")
                        : t("セットに適用しました", "Applied to the set"));
            }
            ImGui.sameLine();
            if (ImGui.button(t("捨てる", "Discard") + "##nm-discard")) {
                generated = null;
            }
        }
    }

    private static String defaultName(MixPlan set) {
        String genre = set.tracks.isEmpty() ? "" : set.tracks.get(0).genre;
        return (genre.isBlank() ? "NONSTOP" : genre) + " MIX " + new SimpleDateFormat("MM/dd HH:mm").format(new Date());
    }

    private static MixJoinPlanner.Settings joinSettings() {
        MixJoinPlanner.Settings settings = new MixJoinPlanner.Settings();
        settings.style = MixJoinPlanner.Style.values()[GEN_STYLE.get()];
        settings.blendBars = new int[] {2, 4, 8, 16}[GEN_BARS.get()];
        settings.blendTolerance = GEN_TOLERANCE[0] / 100.0;
        settings.allowBlend = GEN_BLEND.get();
        settings.allowFade = GEN_FADE.get();
        settings.allowCut = GEN_CUT.get() || !GEN_FADE.get() && !GEN_BLEND.get();
        return settings;
    }

    private static void startGenerator() {
        MixGenerator.Request request = GENERATE;
        request.scope = MixGenerator.Scope.values()[GEN_SCOPE.get()];
        request.joins = joinSettings();
        request.sequence.targetMs = GEN_MINUTES[0] * 60_000L;
        request.sequence.genreRule = MixSequencer.GenreRule.values()[GEN_GENRE_RULE.get()];
        request.sequence.genreFamily = GEN_FAMILY.get() > 0 ? MixGenreFamily.values()[GEN_FAMILY.get() - 1] : null;
        request.sequence.genre = request.sequence.genreFamily != null ? "" : GEN_GENRE.get().trim();
        MixDifficulty.Scale scale = MixDifficulty.Scale.values()[GEN_SCALE.get()];
        int[] levels = GEN_LEVELS[GEN_SCALE.get()];
        request.sequence.minDifficulty = scale.lower(levels[0]);
        request.sequence.maxDifficulty = scale.upper(levels[1]);
        request.sequence.curve = MixSequencer.Curve.values()[GEN_CURVE.get()];
        request.sequence.tempoTolerance = Math.max(0.01, GEN_TOLERANCE[0] / 100.0);
        request.filter = GEN_FILTER.get().trim();
        request.reference = null;
        if (GEN_REFERENCE.get() && request.scope == MixGenerator.Scope.EVERYTHING) {
            Reference reference = reference();
            if (reference == null) {
                ImGuiNotify.warning(t("基準曲がありません。セットの曲か、選曲画面で曲を選んでください",
                        "No reference song: select a song in the set or in Music Select"));
                return;
            }
            Mode mode = Mode.getMode(modeHint(reference.mode()));
            if (mode != null && mode != plan.mode) {
                if (!plan.tracks.isEmpty()) {
                    ImGuiNotify.warning(t("基準曲の鍵盤モードがセットと違います", "The reference song is of another key mode"));
                    return;
                }
                // an empty set takes the reference song's key mode, as adding it would
                plan.mode = mode;
            }
            request.reference = reference.track();
        }
        if (request.reference != null && request.sequence.genre.isEmpty() && request.sequence.genreFamily == null) {
            request.sequence.genre = request.reference.genre;
        }
        request.recentSets = List.copyOf(RECENT_GENERATED);
        request.savedSets = () -> store().listSets();
        request.rangeFrom = GEN_RANGE[0] - 1;
        request.rangeTo = GEN_RANGE[1] - 1;
        generated = null;
        generator = MixGenerator.start(plan, request, library);
    }

    // how many songs the genre condition matches, counted in the background (#565)
    private static String genreCountKey = "";
    private static long genreCountChanged;
    private static volatile int[] genreCounts;
    private static volatile String genreCountsFor = "";
    private static volatile boolean genreCounting;

    private static void renderGenreCount() {
        MixGenreFamily family = GEN_FAMILY.get() > 0 ? MixGenreFamily.values()[GEN_FAMILY.get() - 1] : null;
        String genre = family != null ? "" : GEN_GENRE.get().trim();
        if (family == null && genre.isEmpty()) {
            return;
        }
        String key = plan.mode.id + "|" + (family == null ? "" : family.name()) + "|" + genre;
        long now = System.currentTimeMillis();
        if (!key.equals(genreCountKey)) {
            genreCountKey = key;
            genreCountChanged = now;
        }
        int[] counts = key.equals(genreCountsFor) ? genreCounts : null;
        if (counts == null && !genreCounting && now - genreCountChanged > 400) {
            // typing settles first; the song database is read off the render thread
            genreCounting = true;
            int modeId = plan.mode.id;
            Thread worker = new Thread(() -> {
                try {
                    int[] result = MixGenerator.genreCounts(library, modeId, genre, family);
                    genreCountsFor = key;
                    genreCounts = result;
                } finally {
                    genreCounting = false;
                }
            }, "nonstop-mix-genre-count");
            worker.setDaemon(true);
            worker.start();
        }
        if (counts == null) {
            ImGui.textDisabled(t("曲数を数えています…", "Counting songs..."));
        } else if (family != null) {
            ImGui.textDisabled(String.format(Locale.ROOT, t("この系統の曲: %d曲", "Songs in this family: %d"), counts[0]));
        } else {
            ImGui.textDisabled(String.format(Locale.ROOT,
                    t("同じジャンル %d曲 / 名前の一部が同じ %d曲 / 同じ系統 %d曲",
                            "Same genre: %d / sharing a word: %d / same family: %d"), counts[0], counts[1], counts[2]));
        }
        if (counts != null && GEN_GENRE_RULE.get() == 0 && ImGui.isItemHovered()) {
            ImGui.setTooltip(t("「同じジャンルだけ」で曲が足りないときは、名前の一部が同じジャンル → 同じ系統 → 近いジャンル優先の順に広げます",
                    "When \"Same genre only\" has too few songs, it widens to genres sharing a word, then the family, then the closest genres"));
        }
    }

    private static String[] familyLabels() {
        MixGenreFamily[] families = MixGenreFamily.values();
        String[] labels = new String[families.length + 1];
        labels[0] = t("（自由入力）", "(free text)");
        for (int i = 0; i < families.length; i++) {
            labels[i + 1] = families[i].label(!BMSIRArenaI18n.isEnglish());
        }
        return labels;
    }

    private record Reference(MixTrack track, int mode) {
    }

    /** The reference song: the selected set song, else the song selected in Music Select (Issue #563). */
    private static Reference reference() {
        if (selectedTrack >= 0 && selectedTrack < plan.tracks.size()) {
            return new Reference(plan.tracks.get(selectedTrack), plan.mode.id);
        }
        if (main != null && main.getCurrentState() instanceof MusicSelector selector
                && selector.getBarManager().getSelected() instanceof SongBar songBar && songBar.existsSong()) {
            SongData song = songBar.getSongData();
            return new Reference(MixLibrary.trackFrom(song), song.getMode());
        }
        return null;
    }

    // ------------------------------------------------------------------ saved

    private static void renderSaved() {
        if (savedSets == null) {
            savedSets = store().listSets();
        }
        if (ImGui.button(t("一覧を更新", "Refresh") + "##nm-saved-refresh")) {
            savedSets = store().listSets();
        }
        ImGui.sameLine();
        if (ImGui.button(t("クリップボードのレシピを読み込む", "Import a recipe from the clipboard") + "##nm-import")) {
            try {
                MixPlan imported = MixJson.fromText(ImGui.getClipboardText());
                imported.id = java.util.UUID.randomUUID().toString();
                replacePlan(imported);
                ImGuiNotify.success(t("レシピを読み込みました", "Recipe imported"));
            } catch (Exception e) {
                ImGuiNotify.error(t("レシピを読み込めませんでした: ", "Could not import the recipe: ") + e.getMessage());
            }
        }
        ImGui.sameLine();
        if (ImGui.button(t("今のセットのレシピをコピー", "Copy this set's recipe") + "##nm-export")) {
            ImGui.setClipboardText(MixJson.toText(plan, false));
            ImGuiNotify.success(t("レシピ（音源なし・パスなし）をコピーしました", "Copied the recipe (no audio, no paths)"));
        }
        ImGui.separator();
        if (savedSets.isEmpty()) {
            ImGui.text(t("保存したセットはまだありません", "No saved sets yet"));
            return;
        }
        if (ImGui.beginTable("##nm-saved-table", 5, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.ScrollY, 0, 0)) {
            ImGui.tableSetupColumn(t("名前", "Name"), ImGuiTableColumnFlags.WidthStretch, 0);
            ImGui.tableSetupColumn(t("曲数", "Songs"), ImGuiTableColumnFlags.WidthFixed, 50);
            ImGui.tableSetupColumn(t("更新", "Updated"), ImGuiTableColumnFlags.WidthFixed, 110);
            ImGui.tableSetupColumn(t("自己ベスト", "Best"), ImGuiTableColumnFlags.WidthFixed, 140);
            ImGui.tableSetupColumn("", ImGuiTableColumnFlags.WidthFixed, 200);
            ImGui.tableHeadersRow();
            MixPlan remove = null;
            for (int i = 0; i < savedSets.size(); i++) {
                MixPlan set = savedSets.get(i);
                ImGui.pushID(2000 + i);
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ImGui.text((set.name.isEmpty() ? t("（名前なし）", "(no name)") : set.name) + (set.irId.isEmpty() ? "" : "  [IR]"));
                ImGui.tableNextColumn();
                ImGui.text(String.valueOf(set.tracks.size()));
                ImGui.tableNextColumn();
                ImGui.text(new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date(set.updatedAt)));
                ImGui.tableNextColumn();
                MixResult best = best(records(MixJson.playIdentity(set)));
                ImGui.text(best == null ? "-" : String.format(Locale.ROOT, "%s %.1f%%", clearName(best.clear), best.rate()));
                ImGui.tableNextColumn();
                if (ImGui.smallButton(t("開く", "Open"))) {
                    if (dirty) {
                        saveQuietly();
                    }
                    replacePlan(set.copy());
                }
                ImGui.sameLine();
                if (ImGui.smallButton(t("複製", "Duplicate"))) {
                    MixPlan copy = set.copy();
                    copy.id = java.util.UUID.randomUUID().toString();
                    copy.name = set.name + t(" のコピー", " copy");
                    copy.irId = "";
                    copy.irRevision = 0;
                    try {
                        store().saveSet(copy);
                    } catch (Exception e) {
                        ImGuiNotify.error(e.getMessage());
                    }
                    savedSets = null;
                }
                ImGui.sameLine();
                if (ImGui.smallButton(t("削除", "Delete"))) {
                    ImGui.openPopup("##nm-confirm-delete");
                }
                if (ImGui.beginPopup("##nm-confirm-delete")) {
                    ImGui.text(t("このセットを削除しますか？（記録は残ります）", "Delete this set? (records are kept)"));
                    if (ImGui.button(t("削除する", "Delete"))) {
                        remove = set;
                        ImGui.closeCurrentPopup();
                    }
                    ImGui.sameLine();
                    if (ImGui.button(t("やめる", "Cancel"))) {
                        ImGui.closeCurrentPopup();
                    }
                    ImGui.endPopup();
                }
                ImGui.popID();
                if (savedSets == null) {
                    break;
                }
            }
            ImGui.endTable();
            if (remove != null) {
                try {
                    store().deleteSet(remove);
                } catch (Exception e) {
                    ImGuiNotify.error(e.getMessage());
                }
                savedSets = null;
            }
        }
    }

    private static void save() {
        if (plan.name.isBlank()) {
            plan.name = defaultName(plan);
            syncTextFields();
        }
        try {
            store().saveSet(plan);
            dirty = false;
            savedSets = null;
            ImGuiNotify.success(t("保存しました", "Saved"));
        } catch (Exception e) {
            ImGuiNotify.error(t("保存できませんでした: ", "Could not save: ") + e.getMessage());
        }
    }

    private static void saveQuietly() {
        if (plan.tracks.isEmpty()) {
            return;
        }
        try {
            if (plan.name.isBlank()) {
                plan.name = defaultName(plan);
            }
            store().saveSet(plan);
            savedSets = null;
        } catch (Exception ignored) {
            // the draft still holds the work
        }
    }

    // ------------------------------------------------------------------ IR

    private static void renderIr() {
        ImGui.text(t("IRで公開されたセットを探す", "Find sets published on IR"));
        ImGui.setNextItemWidth(240);
        boolean enter = ImGui.inputText("##nm-ir-query", IR_QUERY, imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);
        ImGui.sameLine();
        ImGui.setNextItemWidth(140);
        ImGui.combo("##nm-ir-sort", IR_SORT, new String[] {t("新しい順", "Newest"), t("人気順", "Popular"), t("自分の公開", "Mine")});
        ImGui.sameLine();
        ImGui.beginDisabled(irBusy);
        if (ImGui.button(t("探す", "Search") + "##nm-ir-search") || enter) {
            irList();
        }
        ImGui.endDisabled();
        if (irBusy) {
            ImGui.sameLine();
            ImGui.text(t("通信中…", "Working..."));
        }
        if (!irError.isEmpty()) {
            ImGui.textColored(ERROR[0], ERROR[1], ERROR[2], ERROR[3], irError);
        }
        MixIrClient.Page page = irPage;
        float listWidth = Math.max(300, ImGui.getContentRegionAvailX() * 0.45f);
        ImGui.beginChild("##nm-ir-list", listWidth, Math.max(200, ImGui.getContentRegionAvailY() - 130), true);
        if (page != null) {
            for (int i = 0; i < page.sets().size(); i++) {
                MixIrClient.Listing set = page.sets().get(i);
                ImGui.pushID(3000 + i);
                String label = String.format(Locale.ROOT, "%s  — %s  (%d曲 %s)", set.name, set.author, set.trackCount,
                        clock(set.durationMs));
                if (ImGui.selectable(label, irSelection == i)) {
                    irSelection = i;
                    irRanking = null;
                }
                ImGui.popID();
            }
            if (page.sets().isEmpty()) {
                ImGui.text(t("見つかりませんでした", "Nothing found"));
            }
        }
        ImGui.endChild();
        ImGui.sameLine();
        ImGui.beginChild("##nm-ir-detail", 0, Math.max(200, ImGui.getContentRegionAvailY() - 130), true);
        if (page != null && irSelection >= 0 && irSelection < page.sets().size()) {
            renderIrDetail(page.sets().get(irSelection));
        } else {
            ImGui.textWrapped(t("左の一覧からセットを選んでください。", "Choose a set on the left."));
        }
        ImGui.endChild();
        ImGui.separator();
        renderPublish();
    }

    private static void renderIrDetail(MixIrClient.Listing set) {
        ImGui.text(set.name);
        ImGui.textDisabled(set.author + "   " + set.mode + "   " + clock(set.durationMs) + "   "
                + String.format(Locale.ROOT, t("%d回プレイ", "%d plays"), set.plays));
        if (!set.description.isEmpty()) {
            ImGui.textWrapped(set.description);
        }
        ImGui.separator();
        int missing = 0;
        for (String[] track : set.tracks) {
            MixTrack probe = new MixTrack();
            probe.sha256 = track[0];
            probe.md5 = track[1];
            boolean have = library.find(probe) != null;
            if (!have) {
                missing++;
            }
            float[] color = have ? GOOD : ERROR;
            ImGui.textColored(color[0], color[1], color[2], color[3], (have ? "✓ " : "✗ ") + track[2] + " / " + track[3]);
        }
        if (missing > 0) {
            ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3],
                    String.format(Locale.ROOT, t("持っていない曲が%d曲あります。入手して楽曲更新すると遊べます。",
                            "%d songs are missing. Get them and update the song database to play."), missing));
        }
        ImGui.beginDisabled(irBusy);
        if (ImGui.button(t("取り込んで編集", "Import and edit") + "##nm-ir-import")) {
            irGet(set.id, false);
        }
        ImGui.sameLine();
        ImGui.beginDisabled(missing > 0 || preparation != null);
        if (ImGui.button(t("そのまま遊ぶ", "Play it") + "##nm-ir-play")) {
            irGet(set.id, true);
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button(t("ランキング", "Ranking") + "##nm-ir-ranking")) {
            irBusy = true;
            irRankingFor = set.id;
            MixIrClient.ranking(main, set.id, set.revision, result -> MixMainThread.post(() -> {
                irBusy = false;
                if (result.ok()) {
                    irRanking = result.value();
                } else {
                    irError = result.error();
                }
            }));
        }
        if (set.own) {
            ImGui.sameLine();
            if (ImGui.button(t("公開をやめる", "Unpublish") + "##nm-ir-unpublish")) {
                irBusy = true;
                MixIrClient.unpublish(main, set.id, result -> MixMainThread.post(() -> {
                    irBusy = false;
                    if (result.ok()) {
                        ImGuiNotify.success(t("公開をやめました", "Unpublished"));
                        irList();
                    } else {
                        irError = result.error();
                    }
                }));
            }
        }
        ImGui.endDisabled();
        List<MixIrClient.Score> ranking = irRanking;
        if (ranking != null && set.id.equals(irRankingFor)) {
            ImGui.separator();
            for (int i = 0; i < ranking.size() && i < 50; i++) {
                MixIrClient.Score score = ranking.get(i);
                ImGui.text(String.format(Locale.ROOT, "%2d. %s  %s  EX %d (%.1f%%)  BP %d", i + 1, score.player(),
                        clearName(score.clear()), score.exScore(),
                        score.maxExScore() == 0 ? 0 : score.exScore() * 100.0 / score.maxExScore(), score.badPoor()));
            }
            if (ranking.isEmpty()) {
                ImGui.text(t("まだ記録がありません", "No scores yet"));
            }
        }
    }

    private static void renderPublish() {
        ImGui.text(t("今のセットを公開", "Publish this set"));
        boolean login = BMSIRNonstopMixTransport.hasLogin(main);
        if (!login) {
            ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3],
                    t("公開とスコア送信にはBMS-IRのログイン設定が必要です", "Publishing and scores need a BMS-IR login"));
        }
        boolean ready = !plan.tracks.isEmpty() && !plan.name.isBlank() && library.missing(plan).isEmpty() && !hasErrors();
        ImGui.beginDisabled(!login || irBusy || !ready);
        String label = plan.irId.isEmpty() ? t("IRに公開する", "Publish on IR") : t("新しい版として公開する", "Publish a new revision");
        if (ImGui.button(label + "##nm-publish")) {
            irBusy = true;
            irError = "";
            MixIrClient.publish(main, plan, result -> MixMainThread.post(() -> {
                irBusy = false;
                if (result.ok()) {
                    plan.irId = result.value().irId;
                    plan.irRevision = result.value().irRevision;
                    afterEdit();
                    saveQuietly();
                    ImGuiNotify.success(t("IRに公開しました", "Published on IR"));
                } else {
                    irError = result.error();
                }
            }));
        }
        ImGui.endDisabled();
        if (!ready) {
            ImGui.sameLine();
            ImGui.textDisabled(t("（名前・曲・問題なしが必要。持っていない曲があると公開できません）",
                    "(needs a name, songs, no problems, and every song in your library)"));
        }
        ImGui.textDisabled(t("公開されるのは曲のハッシュと並び・つなぎの設定だけです。音源やファイルの場所は送りません。",
                "Only chart hashes, order and join settings are published; no audio and no file paths."));
    }

    private static void irList() {
        irBusy = true;
        irError = "";
        String sort = switch (IR_SORT.get()) {
            case 1 -> "popular";
            case 2 -> "mine";
            default -> "new";
        };
        MixIrClient.list(main, IR_QUERY.get(), plan.mode.hint, sort, 0, result -> MixMainThread.post(() -> {
            irBusy = false;
            if (result.ok()) {
                irPage = result.value();
                irSelection = -1;
            } else {
                irError = result.error();
            }
        }));
    }

    private static void irGet(String id, boolean play) {
        irBusy = true;
        irError = "";
        MixIrClient.get(main, id, result -> MixMainThread.post(() -> {
            irBusy = false;
            if (!result.ok()) {
                irError = result.error();
                return;
            }
            if (dirty) {
                saveQuietly();
            }
            replacePlan(result.value());
            saveQuietly();
            if (play) {
                prepare(plan, Pending.PLAY, null, plan.name);
            } else {
                ImGuiNotify.success(t("セットを取り込みました", "Set imported"));
            }
        }));
    }

    // ------------------------------------------------------------------ records

    private static void renderRecords() {
        if (plan.tracks.isEmpty()) {
            ImGui.text(t("セットを開くと記録が表示されます", "Open a set to see its records"));
            return;
        }
        String identity = MixJson.playIdentity(plan);
        List<MixResult> records = records(identity);
        ImGui.textDisabled(t("記録はセットの中身（曲・区間・つなぎ・判定）ごとに分かれます。名前や説明を変えても残ります。",
                "Records belong to the set's content (songs, parts, joins, judge); renaming keeps them."));
        if (records.isEmpty()) {
            ImGui.text(t("このセットの記録はまだありません", "No records for this set yet"));
        }
        for (int i = 0; i < records.size() && i < 30; i++) {
            MixResult r = records.get(i);
            ImGui.pushID(4000 + i);
            String label = String.format(Locale.ROOT, "%s  %s  EX %d (%.2f%%)  BP %d%s",
                    new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date(r.playedAt)), clearName(r.clear), r.exScore,
                    r.rate(), r.badPoor, r.finished ? "" : t("  途中まで", "  not finished"));
            if (ImGui.treeNode(label)) {
                renderTrackResults(r);
                ImGui.treePop();
            }
            ImGui.popID();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<MixResult> records(String identity) {
        long now = System.currentTimeMillis();
        Object[] cached = RECORDS.get(identity);
        if (cached != null && now - (Long) cached[1] < 3000) {
            return (List<MixResult>) cached[0];
        }
        List<MixResult> records = store().records(identity);
        if (RECORDS.size() > 256) {
            RECORDS.clear();
        }
        RECORDS.put(identity, new Object[] {records, now});
        return records;
    }

    private static MixResult best(List<MixResult> records) {
        MixResult best = null;
        for (MixResult result : records) {
            if (best == null || result.clear > best.clear || result.clear == best.clear && result.exScore > best.exScore) {
                best = result;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ background

    private static void prepare(MixPlan source, Pending kind, java.util.function.BiConsumer<MixPlan, long[]> transform,
            String title) {
        if (!(main.getCurrentState() instanceof MusicSelector) && kind != Pending.CHECK) {
            ImGuiNotify.warning(t("選曲画面で使えます", "Available in Music Select"));
            return;
        }
        List<MixTrack> missing = library.missing(source);
        if (!missing.isEmpty()) {
            ImGuiNotify.warning(String.format(Locale.ROOT, t("持っていない曲があります（%d曲）: %s", "%d songs are missing: %s"),
                    missing.size(), missing.get(0).displayTitle()));
            return;
        }
        boolean loudness = kind == Pending.PLAY || kind == Pending.AUTOPLAY_SET;
        preparation = MixPreparation.start(source, library, loudness, transform);
        pending = kind;
        pendingTitle = title == null || title.isBlank() ? "NONSTOP MIX" : title;
    }

    private static void pollJobs() {
        if (draftPending && System.currentTimeMillis() - lastEdit > 1000) {
            draftPending = false;
            store().saveDraft(plan);
        }
        MixPreparation job = preparation;
        if (job != null) {
            switch (job.state()) {
                case READY -> {
                    preparation = null;
                    onPrepared(job);
                }
                case FAILED -> {
                    preparation = null;
                    ImGuiNotify.error(t("準備できませんでした: ", "Could not prepare the set: ") + job.error());
                    pending = Pending.NONE;
                }
                case CANCELLED -> {
                    preparation = null;
                    pending = Pending.NONE;
                }
                default -> {
                }
            }
        }
        MixGenerator gen = generator;
        if (gen != null && gen != consumedGenerator && gen.state() == MixGenerator.State.DONE && gen.result() != null) {
            generated = gen.result();
            consumedGenerator = gen;
            RECENT_GENERATED.addLast(gen.result().copy());
            while (RECENT_GENERATED.size() > RECENT_GENERATED_MAX) {
                RECENT_GENERATED.removeFirst();
            }
        }
        // a quiet background check after edits: compile with the cached charts and list the problems
        if (checkWanted && preparation == null && System.currentTimeMillis() - lastEdit > 700 && !plan.tracks.isEmpty()
                && allAnalysed() && library.missing(plan).isEmpty()) {
            checkWanted = false;
            preparation = MixPreparation.start(plan, library, false, null);
            pending = Pending.CHECK;
        }
    }

    private static void onPrepared(MixPreparation job) {
        CompiledMix mix = job.result();
        Pending kind = pending;
        pending = Pending.NONE;
        if (kind == Pending.CHECK || kind == Pending.PLAY) {
            issues = mix.issues();
            issuesFor = MixJson.playIdentity(job.plan());
            load = mix.load();
            // #RANDOM branches drawn while loading are written back so the set stays the same
            copyRandoms(job.plan());
        }
        if (kind == Pending.CHECK) {
            return;
        }
        if (kind == Pending.PLAY && mix.hasErrors()) {
            ImGuiNotify.error(t("このままでは遊べない問題があります。「確認が必要な点」を見てください",
                    "The set has problems that must be fixed first; see the list"));
            return;
        }
        MixRuntime.Kind runtimeKind = kind == Pending.PLAY ? MixRuntime.Kind.FULL : MixRuntime.Kind.PREVIEW;
        BMSPlayerMode mode = kind == Pending.TRY || kind == Pending.PLAY ? BMSPlayerMode.PLAY : BMSPlayerMode.AUTOPLAY;
        MixRuntime.Session session = new MixRuntime.Session(mix, job.sources(), job.plan(), runtimeKind, pendingTitle);
        if ((kind == Pending.PLAY || kind == Pending.AUTOPLAY_SET) && mix.load().suggestNoBga(job.plan().noBga)) {
            // the whole set's BGA would be loaded at once: ask before starting
            heavyStart = new HeavyStart(session, mode, kind);
            return;
        }
        launch(session, mode, kind);
    }

    private static void renderHeavyStart() {
        HeavyStart waiting = heavyStart;
        if (waiting == null) {
            return;
        }
        String id = t("長いセットの確認", "Long set") + "###nm-heavy-start";
        if (!ImGui.isPopupOpen(id)) {
            ImGui.openPopup(id);
        }
        if (ImGui.beginPopupModal(id, ImGuiWindowFlags.AlwaysAutoResize)) {
            MixLoad heavy = waiting.session().mix().load();
            ImGui.text(t("長い・重いセットです: ", "Long or heavy set: ") + t(heavy.reason(true), heavy.reason(false)));
            ImGui.textWrapped(t("BGAは全曲分を開始時に読み込むため、メモリ不足・カクつき・画像の欠けが起きることがあります。",
                    "Every song's BGA is loaded at start; it may run out of memory, stutter or drop pictures."));
            if (ImGui.button(t("BGAを使わずに開始", "Start without BGA") + "##nm-heavy-nobga")) {
                heavyStart = null;
                ImGui.closeCurrentPopup();
                edit();
                plan.noBga = true;
                afterEdit();
                prepare(plan, waiting.kind(), null, waiting.session().title());
            }
            ImGui.sameLine();
            if (ImGui.button(t("このまま開始", "Start with BGA") + "##nm-heavy-keep")) {
                heavyStart = null;
                ImGui.closeCurrentPopup();
                launch(waiting.session(), waiting.mode(), waiting.kind());
            }
            ImGui.sameLine();
            if (ImGui.button(t("やめる", "Cancel") + "##nm-heavy-cancel")) {
                heavyStart = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private static void launch(MixRuntime.Session session, BMSPlayerMode mode, Pending kind) {
        if (dirty && kind == Pending.PLAY) {
            saveQuietly();
        }
        MainController.pushOneShotAfterRenderTask(controller -> {
            if (controller.getCurrentState() instanceof MusicSelector selector) {
                selector.playNonstopMix(session, mode);
            } else {
                ImGuiNotify.warning(t("選曲画面でのみ開始できます", "Only available in Music Select"));
            }
        });
    }

    private static void copyRandoms(MixPlan prepared) {
        if (prepared.tracks.size() != plan.tracks.size()) {
            return;
        }
        for (int i = 0; i < plan.tracks.size(); i++) {
            MixTrack mine = plan.tracks.get(i);
            MixTrack theirs = prepared.tracks.get(i);
            if (mine.random == null && theirs.random != null && mine.sha256.equals(theirs.sha256)) {
                mine.random = theirs.random.clone();
            }
        }
    }

    private static boolean hasErrors() {
        for (MixCompiler.Issue issue : issues) {
            if (issue.severity() == MixCompiler.Severity.ERROR) {
                return true;
            }
        }
        return !issuesFor.equals(MixJson.playIdentity(plan)) && !plan.tracks.isEmpty() && checkWanted;
    }

    private static boolean allAnalysed() {
        boolean all = true;
        for (MixTrack track : plan.tracks) {
            if (analysis(track) == null) {
                all = false;
            }
        }
        return all;
    }

    private static MixTrackAnalysis analysis(MixTrack track) {
        String key = track.sha256 + "|" + java.util.Arrays.toString(track.random);
        MixTrackAnalysis cached = ANALYSES.get(key);
        if (cached == null) {
            requestAnalysis(track);
        }
        return cached;
    }

    private static void requestAnalysis(MixTrack track) {
        String key = track.sha256 + "|" + java.util.Arrays.toString(track.random);
        if (ANALYSES.containsKey(key) || ANALYSING.contains(key) || ANALYSIS_FAILED.contains(track.sha256)
                || library.find(track) == null) {
            return;
        }
        ANALYSING.add(key);
        MixTrack probe = track.copy();
        Thread worker = new Thread(() -> {
            try {
                MixLibrary.Loaded loaded = library.load(probe);
                MixMainThread.post(() -> {
                    ANALYSING.remove(key);
                    ANALYSES.put(key, loaded.analysis());
                    String filled = probe.sha256 + "|" + java.util.Arrays.toString(probe.random);
                    ANALYSES.put(filled, loaded.analysis());
                    if (track.random == null && probe.random != null) {
                        track.random = probe.random.clone();
                    }
                });
            } catch (RuntimeException e) {
                MixMainThread.post(() -> {
                    ANALYSING.remove(key);
                    ANALYSIS_FAILED.add(track.sha256);
                });
            }
        }, "nonstop-mix-analyse");
        worker.setDaemon(true);
        worker.start();
    }

    private static MixPlan.Layout layoutWithAnalyses() {
        long[] ends = new long[plan.tracks.size()];
        for (int i = 0; i < ends.length; i++) {
            MixTrackAnalysis analysis = analysis(plan.tracks.get(i));
            if (analysis == null) {
                return null;
            }
            ends[i] = analysis.chartEnd;
        }
        return plan.layout(ends);
    }

    private static long estimatedTotal() {
        long[] ends = new long[plan.tracks.size()];
        for (int i = 0; i < ends.length; i++) {
            MixTrackAnalysis analysis = ANALYSES.get(plan.tracks.get(i).sha256 + "|"
                    + java.util.Arrays.toString(plan.tracks.get(i).random));
            ends[i] = analysis != null ? analysis.chartEnd : plan.tracks.get(i).inMs + 120_000L;
        }
        return plan.tracks.isEmpty() ? 0 : plan.layout(ends).totalMs();
    }

    // ------------------------------------------------------------------ text helpers

    private static String joinText(MixTransition join) {
        return switch (join.type) {
            case BLEND -> String.format(Locale.ROOT, t("混ぜる %.1f秒・交代 %.1f秒", "blend %.1fs, hand-off %.1fs"),
                    join.overlapMs / 1000.0, join.effectiveHandoffMs() / 1000.0);
            case CUT -> t("カット", "cut") + (join.gapMs > 0 ? String.format(Locale.ROOT, " +%.1fs", join.gapMs / 1000.0) : "");
            case FADE_OUT -> String.format(Locale.ROOT, t("フェードアウト %.1f秒", "fade out %.1fs"), join.overlapMs / 1000.0);
            case FADE_IN -> String.format(Locale.ROOT, t("フェードイン %.1f秒", "fade in %.1fs"), join.overlapMs / 1000.0);
        };
    }

    private static String rangeText(MixTrack track, MixTrackAnalysis analysis) {
        if (analysis == null) {
            return track.inMs > 0 || track.outMs >= 0 ? clock(track.inMs) + "–" + (track.outMs < 0 ? "end" : clock(track.outMs)) : "";
        }
        return clock(track.inMs) + "–" + clock(track.resolvedOut(analysis.chartEnd));
    }

    private static String bpmText(MixTrack track, MixTrackAnalysis analysis) {
        if (analysis == null) {
            return String.format(Locale.ROOT, "%.0f", track.bpm);
        }
        if (analysis.steadyTempo) {
            return String.format(Locale.ROOT, "%.0f", analysis.mainBpm);
        }
        return String.format(Locale.ROOT, "%.0f→%.0f", analysis.startBpm, analysis.endBpm);
    }

    private static String clock(long ms) {
        long value = Math.max(0, ms);
        return String.format(Locale.ROOT, "%d:%02d.%d", value / 60_000, value / 1000 % 60, value / 100 % 10);
    }

    private static String shorten(String text) {
        return text.length() > 40 ? text.substring(0, 39) + "…" : text;
    }
}
