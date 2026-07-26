package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.modmenu.FontAwesomeIcons;
import bms.player.beatoraja.modmenu.ImGuiRenderer;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.song.SongData;

import com.fasterxml.jackson.databind.JsonNode;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Compact in-game control surface for the authenticated BMS-IR Arena socket.
 */
public final class BMSIRArenaOverlay {
    private static final int MAX_GRAPH_PLAYERS = 8;
    private static final int[] GRAPH_COLORS = {
            ImColor.rgb(101, 183, 255),
            ImColor.rgb(255, 115, 115),
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
    private static final float GAMEPLAY_WINDOW_MIN_WIDTH = 420.0f;
    private static final float GAMEPLAY_WINDOW_MAX_WIDTH = 760.0f;
    private static final float GAMEPLAY_WINDOW_MIN_HEIGHT = 300.0f;
    private static final float GAMEPLAY_WINDOW_MAX_HEIGHT = 520.0f;
    private static final float GAMEPLAY_GRAPH_MIN_HEIGHT = 210.0f;
    private static final float MATCH_GRAPH_HEIGHT = 350.0f;
    private static final float GRAPH_PLOT_TOP_PADDING = 8.0f;
    private static final float GRAPH_LABEL_HEIGHT = 92.0f;

    private static boolean confirmWithdrawal;
    private static int lastVisibleMode;
    private static final ImString CHAT_INPUT = new ImString(201);
    private static final ImString PRIVATE_ROOM_CODE = new ImString(7);
    private static final ImInt ROOM_MODE = new ImInt(0);
    private static final ImInt SCORE_RULE = new ImInt(0);
    private static final ImInt FORCED_GAUGE = new ImInt(0);
    private static final ImInt CHART_SCOPE = new ImInt(0);
    private static final String[] ROOM_MODES = {"カジュアル", "プライベート"};
    private static final String[] SCORE_RULES = {"EX SCORE", "BP", "MAX COMBO"};
    private static final String[] FORCED_GAUGES = {
            "自由", "NORMAL", "HARD", "EXHARD", "HAZARD"
    };
    private static final String[] CHART_SCOPES = {"公式発狂表", "自由選曲"};

    private BMSIRArenaOverlay() {
    }

    public static void render() {
        if (!BMSIRArenaClient.shouldShowOverlay()) {
            confirmWithdrawal = false;
            return;
        }
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config == null || config.getBmsirArenaOverlayMode() == 2) {
            return;
        }
        if (config.getBmsirArenaOverlayMode() == 1) {
            renderCompactOverlay();
            return;
        }
        if (BMSIRArenaClient.isGameplayState()) {
            renderGameplayOverlay();
            return;
        }

        ImGui.setNextWindowPos(18, 72, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(680, 760, ImGuiCond.FirstUseEver);
        int flags = ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoBringToFrontOnFocus;
        if (!ImGui.begin("BMS-IR Arena", flags)) {
            ImGui.end();
            return;
        }

        renderConnectionSummary();
        ImGui.separator();
        if (ImGui.beginTabBar("##bmsir-arena-tabs")) {
            if (ImGui.beginTabItem("対戦")) {
                renderQueueActions();
                ImGui.separator();
                renderMatch();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("カジュアル／プラベ")) {
                renderRoomControls(config);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(FontAwesomeIcons.Trophy + " レートランキング")) {
                renderRanking();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("チャット")) {
                renderChat(true);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("設定")) {
                renderSettings(config);
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.end();
    }

    public static void toggleVisibility() {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config == null) {
            return;
        }
        int current = config.getBmsirArenaOverlayMode();
        if (current == 2) {
            restoreVisibility();
        } else {
            lastVisibleMode = current;
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
            config.setBmsirArenaOverlayMode(restoredVisibleMode(lastVisibleMode));
        }
    }

    static int restoredVisibleMode(int previousVisibleMode) {
        return previousVisibleMode == 1 ? 1 : 0;
    }

    private static void renderCompactOverlay() {
        boolean gameplay = BMSIRArenaClient.isGameplayState();
        String id = gameplay
                ? gameplayWindowId(true, BMSIRArenaClient.isCurrentPlayDouble())
                : "##compact-select";
        ImGui.setNextWindowPos(18, 72, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(250, 110, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowBgAlpha(0.88f);
        int flags = ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoNav;
        if (!ImGui.begin("BMS-IR Arena" + id, flags)) {
            ImGui.end();
            return;
        }
        String status = BMSIRArenaClient.arenaUiMessage();
        ImGui.textWrapped(status.isBlank() ? "Arena待機中" : status);
        if (BMSIRArenaClient.isFillWaiting()) {
            ImGui.text(String.format(
                    Locale.ROOT,
                    "%d秒  /  %d / %d人",
                    BMSIRArenaClient.fillSecondsRemaining(),
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
                ImGui.text("EX " + own.path("exscore").asInt());
            }
        }
        if (!gameplay && ImGui.button("通常表示へ")) {
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
                "BMS-IR Arena" + gameplayWindowId(
                        false,
                        BMSIRArenaClient.isCurrentPlayDouble()
                ),
                flags
        )) {
            ImGui.end();
            return;
        }
        String title = match.path("chart").path("title").asText();
        if (!title.isBlank()) {
            ImGui.textWrapped(title);
        }
        renderChatPreview();
        renderScoreGraph(match, Math.max(GAMEPLAY_GRAPH_MIN_HEIGHT, ImGui.getContentRegionAvailY()));
        ImGui.end();
    }

    private static void renderGameplayStatusOverlay() {
        boolean filling = BMSIRArenaClient.isFillWaiting();
        float width = Math.min(360.0f, maximumGameplayWindowWidth(ImGuiRenderer.windowWidth));
        ImGui.setNextWindowPos(
                ImGuiRenderer.windowWidth / 2.0f,
                Math.max(VIEWPORT_MARGIN, ImGuiRenderer.windowHeight - VIEWPORT_MARGIN),
                ImGuiCond.Always,
                0.5f,
                1.0f
        );
        ImGui.setNextWindowSize(width, filling ? 82.0f : 48.0f, ImGuiCond.Always);
        ImGui.setNextWindowBgAlpha(0.88f);
        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoInputs
                | ImGuiWindowFlags.NoNav
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoBringToFrontOnFocus;
        String side = BMSIRArenaClient.isCurrentPlayDouble() ? "dp" : "sp";
        if (!ImGui.begin("BMS-IR Arena##gameplay-status-" + side, flags)) {
            ImGui.end();
            return;
        }
        if (filling) {
            ImGui.text(FontAwesomeIcons.UserClock + " 追加の参加者を待っています");
            ImGui.text(String.format(
                    Locale.ROOT,
                    "開始まで %d秒  /  %d / %d人",
                    BMSIRArenaClient.fillSecondsRemaining(),
                    BMSIRArenaClient.fillPlayerCount(),
                    BMSIRArenaClient.fillMaxPlayers()
            ));
            ImGui.textDisabled("この待機中の退出はレート・戦績に影響しません");
        } else {
            ImGui.text("BMS-IR Arena");
            ImGui.sameLine();
            ImGui.textDisabled(BMSIRArenaClient.arenaUiMessage());
        }
        ImGui.end();
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

    static String gameplayWindowId(boolean compact, boolean doublePlay) {
        return (compact ? "##compact-play-" : "##gameplay-")
                + (doublePlay ? "dp" : "sp");
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
        List<JsonNode> players = sortedPlayers(match).stream()
                .limit(MAX_GRAPH_PLAYERS)
                .toList();
        if (players.isEmpty()) {
            ImGui.textDisabled("参加者を待っています");
            return;
        }

        int totalNotes = Math.max(1, match.path("chart").path("totalnotes").asInt());
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        float width = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float axisWidth = 44.0f;
        float plotLeft = originX + axisWidth;
        float plotRight = originX + width;
        float plotTop = originY + GRAPH_PLOT_TOP_PADDING;
        float plotHeight = scorePlotHeight(height);
        float plotBottom = plotTop + plotHeight;
        float plotWidth = Math.max(1.0f, plotRight - plotLeft);
        ImDrawList drawList = ImGui.getWindowDrawList();

        drawList.addRectFilled(plotLeft, plotTop, plotRight, plotBottom, GRAPH_BACKGROUND);
        drawGuide(drawList, "MAX", 1.0, plotLeft, plotRight, plotTop, plotHeight, true);
        drawGuide(drawList, "AAA", 8.0 / 9.0, plotLeft, plotRight, plotTop, plotHeight, true);
        drawGuide(drawList, "AA", 7.0 / 9.0, plotLeft, plotRight, plotTop, plotHeight, false);
        drawGuide(drawList, "A", 2.0 / 3.0, plotLeft, plotRight, plotTop, plotHeight, false);

        float columnWidth = plotWidth / players.size();
        for (int index = 0; index < players.size(); index++) {
            JsonNode player = players.get(index);
            int exscore = Math.max(0, player.path("exscore").asInt());
            int placement = player.path("placement").asInt(index + 1);
            double rate = scoreRate(exscore, totalNotes);
            float centerX = plotLeft + columnWidth * (index + 0.5f);
            float barWidth = Math.max(10.0f, Math.min(48.0f, columnWidth * 0.58f));
            float barLeft = centerX - barWidth / 2.0f;
            float barRight = centerX + barWidth / 2.0f;
            float barTop = scoreBarTop(plotTop, plotBottom, rate);
            int color = GRAPH_COLORS[index % GRAPH_COLORS.length];
            boolean selected = player.path("player_id").asInt() == BMSIRArenaClient.currentPlayerId();

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
                    Integer.toString(exscore),
                    centerX,
                    Math.max(plotTop + 2.0f, barTop - ImGui.getTextLineHeight() - 3.0f),
                    columnWidth - 4.0f,
                    GRAPH_TEXT
            );

            float labelY = plotBottom + 6.0f;
            drawCenteredText(drawList, "#" + placement, centerX, labelY, columnWidth, color);
            drawCenteredText(
                    drawList,
                    player.path("name").asText(Integer.toString(player.path("player_id").asInt())),
                    centerX,
                    labelY + 18.0f,
                    columnWidth - 4.0f,
                    selected ? GRAPH_SELECTED : GRAPH_TEXT
            );
            drawCenteredText(
                    drawList,
                    String.format(Locale.ROOT, "%.2f%%", rate * 100.0),
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
                ? FontAwesomeIcons.Wifi + " 接続中"
                : FontAwesomeIcons.Ban + " 再接続中";
        ImGui.text(connection);
        ImGui.sameLine();
        ImGui.text(String.format(
                Locale.ROOT,
                "R %d  /  %d戦",
                Math.round(BMSIRArenaClient.arenaRating()),
                BMSIRArenaClient.arenaMatchesPlayed()
        ));
        String message = BMSIRArenaClient.arenaUiMessage();
        if (!message.isBlank()) {
            ImGui.textWrapped(message);
        }
    }

    private static void renderQueueActions() {
        String status = BMSIRArenaClient.queueStatus();
        boolean connected = BMSIRArenaClient.isConnected();
        boolean filling = BMSIRArenaClient.isFillWaiting();
        if (confirmWithdrawal) {
            ImGui.text(
                    filling
                            ? "このマッチから抜けますか？"
                            : "この対戦を棄権しますか？"
            );
            if (filling) {
                ImGui.textDisabled("レート・戦績には影響しません");
            }
            ImGui.beginDisabled(!connected);
            if (ImGui.button(
                    filling
                            ? FontAwesomeIcons.TimesCircle + " マッチから抜ける"
                            : FontAwesomeIcons.StopCircle + " 棄権する"
            )) {
                BMSIRArenaClient.requestQueueCancel();
                confirmWithdrawal = false;
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button(FontAwesomeIcons.Times + " 戻る")) {
                confirmWithdrawal = false;
            }
            return;
        }

        if ("queued".equals(status)) {
            ImGui.beginDisabled(!connected);
            if (ImGui.button(FontAwesomeIcons.TimesCircle + " 待機を解除")) {
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
                            ? FontAwesomeIcons.TimesCircle + " マッチから抜ける"
                            : FontAwesomeIcons.StopCircle + " 対戦を棄権"
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
                            + (filling ? " 退出処理中" : " 棄権処理中")
            );
            ImGui.endDisabled();
            ImGui.sameLine();
            refreshButton(connected);
            return;
        }

        ImGui.beginDisabled(!connected);
        if (ImGui.button(FontAwesomeIcons.SignInAlt + " エントリー")) {
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
            ImGui.setTooltip("Arena状態を更新");
        }
    }

    private static void renderMatch() {
        if (renderNomination()) {
            return;
        }
        boolean filling = renderFillWaiting();
        JsonNode match = BMSIRArenaClient.currentMatchView();
        if (!match.isObject() || match.size() == 0) {
            if (!filling) {
                ImGui.textDisabled("現在の試合はありません");
            }
            return;
        }
        if (filling) {
            ImGui.separator();
            ImGui.textDisabled("前回の対戦結果");
        }
        JsonNode chart = match.path("chart");
        String level = chart.path("level").asText();
        String title = chart.path("title").asText("選曲中");
        ImGui.textWrapped((level.isBlank() ? "" : level + "  ") + title);
        ImGui.textDisabled(
                BMSIRArenaClient.isShowingCompletedResult()
                        ? "RESULT"
                        : match.path("state").asText("MATCH").toUpperCase(Locale.ROOT)
        );
        if (!"ranked".equals(BMSIRArenaClient.currentMatchMode())) {
            ImGui.textDisabled(
                    scoreRuleLabel(BMSIRArenaClient.currentScoreRule())
                            + " / "
                            + gaugeLabel(BMSIRArenaClient.currentForcedGauge())
                            + " / UNRATED"
            );
        }

        List<JsonNode> players = sortedPlayers(match);
        if (players.isEmpty()) {
            ImGui.textDisabled("参加者を待っています");
            return;
        }
        renderScoreGraph(match, MATCH_GRAPH_HEIGHT);
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
                        case "minbp" -> "BP";
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

    private static boolean renderFillWaiting() {
        if (!BMSIRArenaClient.isFillWaiting()) {
            return false;
        }
        ImGui.setWindowFontScale(1.35f);
        ImGui.textWrapped(
                FontAwesomeIcons.UserClock
                        + " 追加の参加者を待っています"
        );
        ImGui.textWrapped(
                FontAwesomeIcons.Clock
                        + " 対戦開始まで "
                        + BMSIRArenaClient.fillSecondsRemaining()
                        + "秒"
        );
        ImGui.setWindowFontScale(1.0f);
        ImGui.text(String.format(
                Locale.ROOT,
                "現在 %d / %d人",
                BMSIRArenaClient.fillPlayerCount(),
                BMSIRArenaClient.fillMaxPlayers()
        ));
        ImGui.textWrapped(
                "この待機中はマッチから抜けても、レート・戦績に影響しません。"
        );
        return true;
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

        long seconds = BMSIRArenaClient.nominationSecondsRemaining();
        int targetBand = nomination.path("target_band").asInt(1);
        boolean freeSelection = "free".equals(
                nomination.path("chart_scope").asText(
                        BMSIRArenaClient.currentChartScope()
                )
        );
        ImGui.setWindowFontScale(1.45f);
        ImGui.textWrapped(FontAwesomeIcons.Music + " 選曲してください");
        ImGui.textWrapped(FontAwesomeIcons.Clock + " 残り " + seconds + "秒");
        ImGui.setWindowFontScale(1.0f);
        ImGui.spacing();
        ImGui.text(
                freeSelection
                        ? "選曲可能: 所持している任意の単曲譜面"
                        : "選曲可能: ★1～★" + targetBand
        );
        ImGui.separator();

        SongData current = BMSIRArenaClient.currentNominationSong();
        if (current != null) {
            ImGui.textWrapped(current.getTitle());
            String artist = current.getArtist();
            if (artist != null && !artist.isBlank()) {
                ImGui.textDisabled(artist);
            }
        } else {
            ImGui.textDisabled("選択中の楽曲譜面なし");
        }
        ImGui.beginDisabled(!BMSIRArenaClient.isConnected() || current == null);
        if (ImGui.button(FontAwesomeIcons.Music + " この曲を選曲")) {
            BMSIRArenaClient.requestCurrentChartNomination();
        }
        ImGui.endDisabled();
        if (!freeSelection) {
            ImGui.sameLine();
            ImGui.beginDisabled(!BMSIRArenaClient.isConnected());
            if (ImGui.button(FontAwesomeIcons.Dice + " ランダムに任せる")) {
                BMSIRArenaClient.requestRandomNomination();
            }
            ImGui.endDisabled();
        }

        JsonNode own = nomination.path("your_nomination");
        String ownSource = nomination.path("your_source").asText();
        if (own.isObject() && own.size() > 0) {
            ImGui.textWrapped(
                    "登録済み: "
                            + own.path("level").asText()
                            + "  "
                            + own.path("title").asText()
            );
        } else if ("server_random".equals(ownSource)) {
            ImGui.textDisabled("登録済み: サーバーランダム");
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
                tableText(player.path("submitted").asBoolean() ? "選曲済み" : "選曲中");
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
                        ? FontAwesomeIcons.Random + " 再抽選結果"
                        : FontAwesomeIcons.CheckCircle + " 抽選結果"
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
            return "DNF（棄権）";
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
            ImGui.text(
                    "private".equals(mode)
                            ? "プライベートルーム"
                            : "カジュアルルーム"
            );
            String roomCode = BMSIRArenaClient.currentRoomCode();
            if (!roomCode.isBlank()) {
                ImGui.text("部屋コード: " + roomCode);
            }
            ImGui.text("勝敗: " + scoreRuleLabel(BMSIRArenaClient.currentScoreRule()));
            ImGui.text("ゲージ: " + gaugeLabel(BMSIRArenaClient.currentForcedGauge()));
            ImGui.text(
                    "選曲: "
                            + ("free".equals(BMSIRArenaClient.currentChartScope())
                                    ? "自由選曲"
                                    : "公式発狂表")
            );
            ImGui.textDisabled("カジュアル／プラベはレート・レート戦績に影響しません");
            ImBoolean stay = new ImBoolean(config.isBmsirArenaStayInRoom());
            if (ImGui.checkbox("対戦後もこの部屋に残る", stay)) {
                config.setBmsirArenaStayInRoom(stay.get());
                BMSIRArenaClient.requestRoomStay(stay.get());
            }
            ImGui.beginDisabled(!BMSIRArenaClient.isConnected());
            if (ImGui.button("この部屋から退出")) {
                BMSIRArenaClient.requestQueueCancel();
            }
            ImGui.endDisabled();
            ImGui.separator();
            renderMatch();
            return;
        }

        ImGui.combo("種別", ROOM_MODE, ROOM_MODES);
        ImGui.combo("勝敗ルール", SCORE_RULE, SCORE_RULES);
        ImGui.combo("強制ゲージ", FORCED_GAUGE, FORCED_GAUGES);
        ImGui.combo("選曲範囲", CHART_SCOPE, CHART_SCOPES);
        ImBoolean stay = new ImBoolean(config.isBmsirArenaStayInRoom());
        if (ImGui.checkbox("対戦後もこの部屋に残る", stay)) {
            config.setBmsirArenaStayInRoom(stay.get());
        }
        ImGui.textDisabled("このモードの対戦は常にunratedです");

        boolean privateMode = ROOM_MODE.get() == 1;
        if (privateMode) {
            ImGui.inputText("部屋コード", PRIVATE_ROOM_CODE);
            ImGui.textDisabled("空欄で新規作成、6文字を入力すると既存部屋へ参加");
        }
        boolean canEnter = BMSIRArenaClient.isConnected()
                && !("queued".equals(status)
                        || "reserved".equals(status)
                        || "matched".equals(status));
        ImGui.beginDisabled(!canEnter);
        if (ImGui.button(
                privateMode
                        ? (PRIVATE_ROOM_CODE.get().isBlank()
                                ? "プラベを作成"
                                : "コードで参加")
                        : "カジュアルへ参加"
        )) {
            BMSIRArenaClient.requestRoomEntry(
                    privateMode ? "private" : "casual",
                    switch (SCORE_RULE.get()) {
                        case 1 -> "minbp";
                        case 2 -> "max_combo";
                        default -> "exscore";
                    },
                    switch (FORCED_GAUGE.get()) {
                        case 1 -> "normal";
                        case 2 -> "hard";
                        case 3 -> "exhard";
                        case 4 -> "hazard";
                        default -> "free";
                    },
                    CHART_SCOPE.get() == 1 ? "free" : "official",
                    privateMode ? PRIVATE_ROOM_CODE.get() : ""
            );
        }
        ImGui.endDisabled();
    }

    private static String scoreRuleLabel(String rule) {
        return switch (rule) {
            case "minbp" -> "BP（少ない順）";
            case "max_combo" -> "MAX COMBO（多い順）";
            default -> "EX SCORE（多い順）";
        };
    }

    private static String gaugeLabel(String gauge) {
        return switch (gauge) {
            case "normal" -> "NORMAL";
            case "hard" -> "HARD";
            case "exhard" -> "EXHARD";
            case "hazard" -> "HAZARD";
            default -> "自由";
        };
    }

    private static void renderRanking() {
        JsonNode ranking = BMSIRArenaClient.rankingView();
        JsonNode current = ranking.path("current");
        if (current.isObject() && current.size() > 0) {
            ImGui.text(String.format(
                    Locale.ROOT,
                    "あなた: %d位  /  R %d",
                    current.path("rank").asInt(),
                    Math.round(current.path("rating_exact").asDouble())
            ));
        }
        List<JsonNode> rows = new ArrayList<>();
        ranking.path("rows").forEach(rows::add);
        if (rows.isEmpty()) {
            ImGui.textDisabled("ランキング対象の対戦結果はまだありません");
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

    private static void renderChat(boolean allowInput) {
        List<JsonNode> messages = BMSIRArenaClient.chatMessages();
        if (ImGui.beginChild("##bmsir-arena-chat-log", 0, 330, true)) {
            if (messages.isEmpty()) {
                ImGui.textDisabled("メッセージはまだありません");
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
        if (!allowInput || !BMSIRArenaClient.isReserved()) {
            ImGui.textDisabled("対戦中の入力は無効です");
            return;
        }
        ImGui.inputText("##bmsir-arena-chat-input", CHAT_INPUT);
        ImGui.sameLine();
        boolean send = ImGui.button("送信");
        if (send && !CHAT_INPUT.get().isBlank()) {
            BMSIRArenaClient.requestChat(CHAT_INPUT.get());
            CHAT_INPUT.set("");
        }
    }

    private static void renderChatPreview() {
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
        ImGui.text("表示モード");
        if (ImGui.radioButton("通常", config.getBmsirArenaOverlayMode() == 0)) {
            config.setBmsirArenaOverlayMode(0);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("コンパクト", config.getBmsirArenaOverlayMode() == 1)) {
            config.setBmsirArenaOverlayMode(1);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("非表示", config.getBmsirArenaOverlayMode() == 2)) {
            config.setBmsirArenaOverlayMode(2);
        }
        ImGui.textDisabled("Ctrl+Shift+F5で切替。戻らない場合はF5メニューから再表示");

        ImBoolean cursor = new ImBoolean(config.isBmsirArenaShowCursor());
        if (ImGui.checkbox("プレイ中もマウスカーソルを表示", cursor)) {
            config.setBmsirArenaShowCursor(cursor.get());
        }
        ImBoolean unrestricted = new ImBoolean(
                config.isBmsirArenaUnrestrictedRating()
        );
        if (ImGui.checkbox("レート制限なしマッチを許可", unrestricted)) {
            config.setBmsirArenaUnrestrictedRating(unrestricted.get());
        }
        ImGui.textDisabled("距離のある即時マッチは相手も許可した場合だけ成立します");
        ImBoolean mirror = new ImBoolean(config.isBmsirArenaRandomMirror());
        if (ImGui.checkbox("同期RANDOMを左右反転して受け取る", mirror)) {
            config.setBmsirArenaRandomMirror(mirror.get());
        }
        ImBoolean stay = new ImBoolean(config.isBmsirArenaStayInRoom());
        if (ImGui.checkbox("カジュアル／プラベで対戦後も部屋に残る", stay)) {
            config.setBmsirArenaStayInRoom(stay.get());
            if (!"ranked".equals(BMSIRArenaClient.currentMatchMode())) {
                BMSIRArenaClient.requestRoomStay(stay.get());
            }
        }
        ImGui.separator();
        ImGui.textWrapped(
                "プレイ画面の位置とサイズはSP用とDP用に別々に保存されます。"
        );
    }

    private static void tableText(String text) {
        ImGui.tableNextColumn();
        ImGui.textUnformatted(text == null || text.isBlank() ? "-" : text);
    }
}
