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
    private static final float GRAPH_LABEL_HEIGHT = 92.0f;

    private static boolean confirmWithdrawal;
    private static boolean confirmRoomDisband;
    private static boolean hotkeyCaptureActive;
    private static volatile boolean keyboardInputCaptured;
    private static final Set<Integer> HOTKEY_CAPTURE_KEYS = new LinkedHashSet<>();
    private static final ImString CHAT_INPUT = new ImString(201);
    private static final ImString LOBBY_CHAT_INPUT = new ImString(201);
    private static final ImString PRIVATE_ROOM_CODE = new ImString(7);
    private static final ImString ROOM_NAME = new ImString(41);
    private static final ImString ROOM_PASSWORD = new ImString(65);
    private static final ImString USER_TABLE_ID = new ImString(12);
    private static final ImString USER_TABLE_KEY = new ImString(96);
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
    private static final int[] ROOM_PLAY_MODES = {5, 7, 9, 10, 14};
    private static final Set<String> CUSTOM_LEVELS = new HashSet<>();
    private static final Map<Integer, String> USER_TABLE_KEYS =
            new LinkedHashMap<>();
    private static final String[] SCORE_RULES = {
            "EX SCORE", "BP Arena（CBのみ）", "MAX COMBO"
    };
    private static final String[] FORCED_GAUGES = {
            "自由", "NORMAL", "HARD", "EXHARD", "HAZARD"
    };
    private static final String[] CHART_SCOPES = {
            "通常＋発狂難易度表",
            "自由選曲",
            "カスタム"
    };
    private static final String[] RULESET_PROFILES = {"LR2", "oraja"};
    private static final String[] NOMINATION_POLICIES = {
            "全員が選曲", "部屋主だけ選曲", "選曲担当を交代"
    };
    private static final String[] SERIES_FORMATS = {
            "1曲", "全員の曲を回す", "N本先取"
    };
    private static final String[] ARENA_TARGET_MODES = {
            "OFF", "1位の対戦相手", "自分の直上", "指定プレイヤー"
    };
    private static final String[] ARENA_GRAPH_ORDERS = {
            "順位順", "入室順固定"
    };

    private BMSIRArenaOverlay() {
    }

    public static void render() {
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
        if (BMSIRArenaClient.isGameplayState()) {
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
            if (ImGui.beginTabItem("対戦")) {
                renderQueueActions();
                ImGui.separator();
                renderMatch();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("公開ロビー／ルーム")) {
                renderRoomControls(config);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(FontAwesomeIcons.Trophy + " レートランキング")) {
                renderRanking();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("マニュアル")) {
                renderManual();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("設定")) {
                renderSettings(config);
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.endChild();
        ImGui.end();
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
                    "READY "
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
                ready ? "準備OKを解除" : "準備OK",
                width,
                52.0f
        )) {
            BMSIRArenaClient.requestRoomReady(!ready);
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImBoolean always = new ImBoolean(config.isBmsirArenaAlwaysReady());
        if (ImGui.checkbox("ずっとOKにする", always)) {
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
            ImGui.textDisabled("ROOM " + roomCode);
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
                        BMSIRArenaClient.currentPlayModeForLayout()
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
        renderModeBanner();
        renderChatPreview();
        renderSeriesBanner(true);
        renderForceEndVote(match);
        renderScoreGraph(match, Math.max(GAMEPLAY_GRAPH_MIN_HEIGHT, ImGui.getContentRegionAvailY()));
        ImGui.end();
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
            ImGui.textDisabled("この待機中の退出はレート・戦績に影響しません");
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
        summary.append(" / 第")
                .append(Math.max(1, BMSIRArenaClient.currentSeriesRound()))
                .append("曲");
        if (remaining >= 0) {
            summary.append(" / 残り").append(remaining).append("曲");
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
                                + "pt / 参考EX率 "
                                + rate
                                + (placement > 0 ? " / 総合" + placement + "位" : "")
                );
            } else {
                standings.add(
                        name
                                + " "
                                + player.path("series_wins").asInt()
                                + "勝"
                );
            }
        }
        ImGui.textWrapped(
                ("bo2".equals(format) ? "BO2総合: " : "戦績: ")
                        + String.join(" / ", standings)
        );
    }

    static String phaseCountdownText(long seconds) {
        return String.format(
                Locale.ROOT,
                "残り %02d秒",
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
            ImGui.textDisabled("参加者を待っています");
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
            drawCenteredText(
                    drawList,
                    "exscore".equals(scoreRule)
                            ? String.format(Locale.ROOT, "%.2f%%", rate * 100.0)
                            : String.format(Locale.ROOT, "%.1f%%", rate * 100.0),
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
                        ? "曲終了に投票済み (" + votes + "/" + required + ")"
                        : "この曲を終了する (" + votes + "/" + required + ")"
        )) {
            BMSIRArenaClient.requestForceEndVote();
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.textDisabled("残っている全員の同意で終了");
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
            case "minbp" -> "LOWEST COMBO BREAK WINS";
            case "max_combo" -> "MAX COMBO BATTLE";
            default -> "EX SCORE BATTLE";
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
                "| " + rulesetProfileLabel(BMSIRArenaClient.currentRulesetProfile()) + "仕様"
        );
        if ("cpu_bonus".equals(BMSIRArenaClient.currentRatingPolicy())) {
            ImGui.textDisabled(
                    "CPU戦: A～MAX固定 / 勝利 +1 / 敗北 -1 / 同点 ±0"
            );
        }
    }

    static String modeDisplayText(String mode) {
        return switch (mode) {
            case "casual" -> "カジュアル  |  レート変動なし";
            case "private" -> "プライベート  |  レート変動なし";
            default -> "レートArena  |  レート変動あり";
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
            if (ImGui.checkbox("1人待機中のCPU戦を許可", allowCpu)) {
                config.setBmsirArenaAllowCpu(allowCpu.get());
            }
            ImGui.sameLine();
            ImGui.textDisabled(
                    allowCpu.get()
                            ? "人間1人でもCPU3人で開始"
                            : "互換性のある人間を待機"
            );
            ImBoolean allowHigherSelection = new ImBoolean(
                    config.isBmsirArenaAllowHigherSelection()
            );
            if (ImGui.checkbox(
                    "高レート基準の選曲を許可",
                    allowHigherSelection
            )) {
                config.setBmsirArenaAllowHigherSelection(
                        allowHigherSelection.get()
                );
            }
            ImGui.textDisabled(
                    "ONなら自分の解放済み上限で部屋の選曲上限を下げません"
            );
        } else if (entryActive) {
            ImGui.textDisabled(
                    BMSIRArenaClient.currentQueueAllowsCpu()
                            ? "1人CPU戦: 許可"
                            : "1人CPU戦: 無効（互換相手待機）"
            );
            ImGui.textDisabled(
                    BMSIRArenaClient.currentQueueAllowsHigherSelection()
                            ? "高レート基準の選曲: 許可"
                            : "選曲上限: 自分の解放済み上限を反映"
            );
        }
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
        renderPhaseBanner(false);
        ImGui.separator();
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
        if (BMSIRArenaClient.isShowingCompletedResult()) {
            renderRatingChange(match);
            if (ImGui.button(FontAwesomeIcons.Times + " 結果を閉じる")) {
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
            ImGui.textDisabled("参加者を待っています");
            return;
        }
        if (ImGui.collapsingHeader("リアルタイムグラフ")) {
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
                    name += "（再接続待ち）";
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
        ImGui.text("現在: " + BMSIRArenaClient.currentOptionLabel());
        ImGui.textDisabled("H-RANDOMなどのアシスト系OPは使用できません");
        if (
                BMSIRArenaClient.isForceHostOption()
                        && !BMSIRArenaClient.isCurrentRoomHost()
        ) {
            ImGui.textColored(
                    ImColor.rgb(255, 211, 106),
                    "部屋主の左右OP・FLIPが全員へ適用されます"
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
                        ? "準備完了"
                        : "このOPで準備完了"
        )) {
            BMSIRArenaClient.requestOptionReady();
        }
        ImGui.endDisabled();
        ImGui.textDisabled("操作がなければ時間切れ時のOPで自動確定します");
        return true;
    }

    private static boolean renderFillWaiting() {
        if (!BMSIRArenaClient.isFillWaiting()) {
            return false;
        }
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
                        ? "選曲可能: 所持している任意の単曲譜面"
                        : customSelection
                                ? "選曲可能: ルームのカスタム難易度表"
                                : "選曲可能: ☆1～"
                                        + BMSIRArenaClient.arenaBandLabel(targetBand)
        );
        if (!freeSelection && !customSelection) {
            double referenceRating = nomination.path("reference_rating")
                    .asDouble(1000.0);
            ImGui.textDisabled(String.format(
                    Locale.ROOT,
                    "基準レート %.0f / 上限 %s",
                    referenceRating,
                    BMSIRArenaClient.arenaBandLabel(targetBand)
            ));
        }
        ImGui.separator();
        if (requiredCount > 0) {
            ImGui.text("選曲進捗: " + submittedCount + " / " + requiredCount);
        }

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
        ImGui.beginDisabled(
                !canNominate
                        || quotaComplete
                        || !BMSIRArenaClient.isConnected()
                        || current == null
        );
        if (ImGui.button(FontAwesomeIcons.Music + " この曲を選曲")) {
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
        if (ImGui.button(FontAwesomeIcons.Dice + " 他の人に任せる")) {
            BMSIRArenaClient.requestRandomNomination();
        }
        ImGui.endDisabled();
        if (!canDelegate) {
            ImGui.textDisabled("自由選曲の連戦では各自の重複しない選曲が必要です");
        }

        JsonNode own = nomination.path("your_nomination");
        JsonNode ownList = nomination.path("your_nominations");
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
                                ? "待機"
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

    private static String graphPlayerName(JsonNode player) {
        String name = player.path("name").asText(
                Integer.toString(player.path("player_id").asInt())
        );
        if (!player.path("connected").asBoolean(true)
                && !player.path("finished").asBoolean(false)) {
            return name + "（再接続待ち）";
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
                            ? "公開ルーム"
                            : "コード限定ルーム")
                            + (BMSIRArenaClient.isCurrentRoomLocked()
                                    ? " [鍵あり]"
                                    : " [鍵なし]")
            );
            if (!BMSIRArenaClient.currentRoomName().isBlank()) {
                ImGui.textWrapped(BMSIRArenaClient.currentRoomName());
            }
            if (!roomCode.isBlank()) {
                ImGui.text("部屋コード: " + roomCode);
                ImGui.sameLine();
                if (ImGui.smallButton("コピー##current-room-code")) {
                    ImGui.setClipboardText(roomCode);
                    ImGuiNotify.info("部屋コードをコピーしました", 2500);
                }
            }
            ImGui.text("勝敗: " + scoreRuleLabel(BMSIRArenaClient.currentScoreRule()));
            ImGui.text(
                    "試合形式: "
                            + seriesFormatLabel(
                                    BMSIRArenaClient.currentSeriesFormat(),
                                    BMSIRArenaClient.currentFirstToWins()
                            )
            );
            if (!"single".equals(BMSIRArenaClient.currentSeriesFormat())) {
                ImGui.text(
                        "第" + BMSIRArenaClient.currentSeriesRound() + "曲"
                );
            }
            ImGui.text("ゲージ: " + gaugeLabel(BMSIRArenaClient.currentForcedGauge()));
            ImGui.text("判定・ゲージ仕様: " + rulesetProfileLabel(
                    BMSIRArenaClient.currentRulesetProfile()
            ));
            ImGui.text(
                    "選曲: "
                            + (switch (BMSIRArenaClient.currentChartScope()) {
                                case "free" -> "自由選曲";
                                case "custom" -> "カスタム";
                                default -> "通常＋発狂難易度表";
                            })
            );
            ImGui.textDisabled("ルーム対戦はレート・レート戦績に影響しません");
            ImBoolean participating = new ImBoolean(
                    BMSIRArenaClient.isRoomParticipating()
            );
            if (ImGui.checkbox("次のシリーズに参加する", participating)) {
                BMSIRArenaClient.requestRoomParticipation(participating.get());
            }
            if (BMSIRArenaClient.isRoomParticipationPending()) {
                ImGui.textColored(
                        ImColor.rgb(255, 211, 106),
                        "参加ONは進行中シリーズ終了後から有効です"
                );
            }
            if (BMSIRArenaClient.isRoomPaused()) {
                ImGui.textColored(
                        ImColor.rgb(121, 223, 139),
                        "休憩中（全員観戦）"
                );
                ImGui.textDisabled("2人以上が参加ONになり、全員が準備OKで開始します");
            }
            ImGui.textDisabled("上部の準備OKを全員が押すと選曲へ進みます");
            ImBoolean stay = new ImBoolean(config.isBmsirArenaStayInRoom());
            if (ImGui.checkbox("対戦後もこの部屋に残る", stay)) {
                config.setBmsirArenaStayInRoom(stay.get());
                BMSIRArenaClient.requestRoomStay(stay.get());
            }
            if (BMSIRArenaClient.isCurrentRoomHost()
                    && ImGui.collapsingHeader("部屋主の詳細設定")) {
                ImGui.separator();
                ImGui.text("変更すると全員の準備OKを解除します");
                ImGui.inputText("部屋名", ROOM_NAME);
                ImGui.combo("勝敗ルール", SCORE_RULE, SCORE_RULES);
                ImGui.combo("強制ゲージ", FORCED_GAUGE, FORCED_GAUGES);
                ImGui.combo("選曲範囲", CHART_SCOPE, CHART_SCOPES);
                renderAllowedPlayModes();
                if (CHART_SCOPE.get() == 2) {
                    renderCustomRoomSelection();
                }
                renderRulesetProfileSetting(config, "##private-room-ruleset");
                renderPrivateRoomSettings(config);
                ImGui.inputText(
                        BMSIRArenaClient.isCurrentRoomLocked()
                                ? "新しいパスワード（空欄で解除）"
                                : "新しいパスワード",
                        ROOM_PASSWORD
                );
                ImBoolean passwordChange = new ImBoolean(updateRoomPassword);
                if (ImGui.checkbox("パスワードを更新", passwordChange)) {
                    updateRoomPassword = passwordChange.get();
                }
                ImGui.beginDisabled(!BMSIRArenaClient.isConnected());
                if (ImGui.button("設定を次の曲へ反映")) {
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
                            "全員を退出させます"
                    );
                    if (ImGui.button("解体を確定")) {
                        BMSIRArenaClient.requestRoomDisband();
                        confirmRoomDisband = false;
                    }
                    ImGui.sameLine();
                    if (ImGui.button("戻る##cancel-disband")) {
                        confirmRoomDisband = false;
                    }
                } else if (ImGui.button("部屋を解体")) {
                    confirmRoomDisband = true;
                }
            }
            renderRoomParticipants();
            ImGui.beginDisabled(!BMSIRArenaClient.isConnected());
            if (ImGui.button("この部屋から退出")) {
                BMSIRArenaClient.requestQueueCancel();
            }
            ImGui.endDisabled();
            ImGui.separator();
            renderMatch();
            ImGui.separator();
            ImGui.text("ルームチャット");
            renderChat(true, 160);
            return;
        }
        confirmRoomDisband = false;
        loadedRoomCode = "";
        renderPublicRoomList();
        ImGui.separator();
        ImGui.text("ルームを作成／コードで参加");
        ImGui.combo("勝敗ルール", SCORE_RULE, SCORE_RULES);
        ImGui.combo("強制ゲージ", FORCED_GAUGE, FORCED_GAUGES);
        ImGui.combo("選曲範囲", CHART_SCOPE, CHART_SCOPES);
        renderAllowedPlayModes();
        if (CHART_SCOPE.get() == 2) {
            renderCustomRoomSelection();
        }
        renderRulesetProfileSetting(config, "##room-entry-ruleset");
        ImBoolean stay = new ImBoolean(config.isBmsirArenaStayInRoom());
        if (ImGui.checkbox("対戦後もこの部屋に残る", stay)) {
            config.setBmsirArenaStayInRoom(stay.get());
        }
        ImGui.textDisabled("このモードの対戦は常にunratedです");

        ImGui.inputText("部屋コード", PRIVATE_ROOM_CODE);
        ImGui.sameLine();
        if (ImGui.button("貼り付け##private-room-code")) {
            String clipboard = ImGui.getClipboardText();
            PRIVATE_ROOM_CODE.set(
                    BMSIRArenaClient.normalizeRoomCode(clipboard)
            );
        }
        ImGui.textDisabled("空欄で新規作成、6文字を入力すると既存ルームへ参加");
        ImGui.inputText(
                PRIVATE_ROOM_CODE.get().isBlank() ? "部屋名" : "パスワード",
                PRIVATE_ROOM_CODE.get().isBlank() ? ROOM_NAME : ROOM_PASSWORD
        );
        if (PRIVATE_ROOM_CODE.get().isBlank()) {
            ImGui.inputText("パスワード（任意）", ROOM_PASSWORD);
        }
        ImBoolean participating = new ImBoolean(
                config.isBmsirArenaRoomParticipating()
        );
        if (ImGui.checkbox("参加者として入室", participating)) {
            config.setBmsirArenaRoomParticipating(participating.get());
        }
        ImGui.textDisabled("OFFなら観戦・チャットだけ行い、参加枠を消費しません");
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
                        ? "ルームを作成"
                        : "コードで参加"
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
        ImGui.text("公開ロビーチャット");
        renderLobbyChat(160);
    }

    private static void renderPrivateRoomSettings(PlayerConfig config) {
        renderSeriesFormatSettings(config);
        ImBoolean publicSpectators = new ImBoolean(
                config.isBmsirArenaSpectatorPublic()
        );
        if (ImGui.checkbox("公開ロビー一覧・Web観戦へ公開", publicSpectators)) {
            config.setBmsirArenaSpectatorPublic(publicSpectators.get());
        }
        ImGui.textDisabled("非公開でも部屋コードを知る人は参加・Web観戦できます");
        ImBoolean forceHostOption = new ImBoolean(
                config.isBmsirArenaForceHostOption()
        );
        if (ImGui.checkbox("部屋主のOPを他プレイヤーへ強制", forceHostOption)) {
            config.setBmsirArenaForceHostOption(forceHostOption.get());
        }
        ImGui.textDisabled("強制ゲージと併用可。S-RANDOMの配置自体は各プレイヤー別です");
        NOMINATION_POLICY.set(
                "host".equals(config.getBmsirArenaNominationPolicy())
                        ? 1
                        : "rotate".equals(config.getBmsirArenaNominationPolicy())
                                ? 2 : 0
        );
        if (SERIES_FORMAT.get() == 0) {
            if (ImGui.combo(
                    "選曲担当",
                    NOMINATION_POLICY,
                    NOMINATION_POLICIES
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
        if (ImGui.inputInt("選曲時間（10～180秒）", NOMINATION_SECONDS)) {
            config.setBmsirArenaNominationSeconds(NOMINATION_SECONDS.get());
        }
        OPTION_SECONDS.set(config.getBmsirArenaOptionSeconds());
        if (ImGui.inputInt("OP選択時間（5～60秒）", OPTION_SECONDS)) {
            config.setBmsirArenaOptionSeconds(OPTION_SECONDS.get());
        }
        INTERMISSION_SECONDS.set(config.getBmsirArenaIntermissionSeconds());
        if (ImGui.inputInt("曲間待機（0～60秒）", INTERMISSION_SECONDS)) {
            config.setBmsirArenaIntermissionSeconds(
                    INTERMISSION_SECONDS.get()
            );
        }
    }

    private static void renderAllowedPlayModes() {
        ImGui.separator();
        ImGui.text("許可KEY数");
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
        if (ImGui.button("対応難易度表を更新")) {
            BMSIRArenaClient.requestCustomCatalog();
        }
        ImGui.sameLine();
        ImGui.textDisabled("選んだ表・レベルを合成します");

        if (ImGui.beginTabBar("##arena-custom-selection-tabs")) {
            if (ImGui.beginTabItem("対応難易度表")) {
                JsonNode catalog = BMSIRArenaClient.customCatalogView();
                if (!catalog.path("tables").isArray()) {
                    ImGui.textDisabled("「対応難易度表を更新」を押してください");
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
            if (ImGui.beginTabItem("マイ難易度表")) {
                ImGui.inputText("表ID", USER_TABLE_ID);
                ImGui.inputText("Arena共有キー", USER_TABLE_KEY);
                ImGui.textDisabled("公開表は共有キーを空欄にします");
                if (ImGui.button("表を読み込む")) {
                    try {
                        int tableId = Integer.parseInt(USER_TABLE_ID.get().trim());
                        USER_TABLE_KEYS.put(tableId, USER_TABLE_KEY.get());
                        BMSIRArenaClient.requestCustomUserCatalog(
                                tableId,
                                USER_TABLE_KEY.get()
                        );
                    } catch (NumberFormatException exception) {
                        ImGuiNotify.warning("表IDは数字で入力してください");
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
            ImGui.textDisabled("Arenaで使用できるBMS譜面がありません");
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
        ImGui.text("公開ルーム");
        JsonNode rooms = BMSIRArenaClient.publicRoomsView();
        if (!rooms.isArray() || rooms.isEmpty()) {
            ImGui.textDisabled("参加できる公開ルームはありません");
            return;
        }
        int flags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg;
        if (!ImGui.beginTable("##bmsir-public-rooms", 5, flags)) {
            return;
        }
        ImGui.tableSetupColumn("部屋名");
        ImGui.tableSetupColumn("HOST");
        ImGui.tableSetupColumn("人数");
        ImGui.tableSetupColumn("ルール");
        ImGui.tableSetupColumn("");
        ImGui.tableHeadersRow();
        for (JsonNode room : rooms) {
            String code = room.path("room_code").asText("");
            boolean locked = room.path("locked").asBoolean(false);
            ImGui.tableNextRow();
            tableText(
                    (locked ? "[鍵] " : "")
                            + room.path("room_name").asText(code)
            );
            tableText(room.path("host_name").asText(""));
            tableText(
                    room.path("participant_count").asInt()
                            + " / "
                            + room.path("member_count").asInt()
            );
            tableText(
                    scoreRuleLabel(room.path("score_rule").asText("exscore"))
                            + " / "
                            + gaugeLabel(room.path("forced_gauge").asText("free"))
            );
            ImGui.tableNextColumn();
            if (ImGui.smallButton("選択##public-room-" + code)) {
                PRIVATE_ROOM_CODE.set(code);
                ROOM_PASSWORD.set("");
            }
        }
        ImGui.endTable();
    }

    private static void renderLobbyChat(float height) {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config != null && config.isBmsirArenaMuteChat()) {
            ImGui.textDisabled("チャットは設定でミュートされています");
            return;
        }
        List<JsonNode> messages = BMSIRArenaClient.lobbyChatMessages();
        if (ImGui.beginChild("##bmsir-arena-lobby-chat-log", 0, height, true)) {
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
        ImGui.inputText("##bmsir-arena-lobby-chat-input", LOBBY_CHAT_INPUT);
        ImGui.sameLine();
        ImGui.beginDisabled(!BMSIRArenaClient.isConnected());
        if (
                ImGui.button("送信##lobby-chat")
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
        if (ImGui.combo("試合形式", SERIES_FORMAT, SERIES_FORMATS)) {
            config.setBmsirArenaSeriesFormat(switch (SERIES_FORMAT.get()) {
                case 1 -> "all_picks";
                case 2 -> "first_to";
                default -> "single";
            });
        }
        if (SERIES_FORMAT.get() == 2) {
            FIRST_TO_WINS.set(config.getBmsirArenaFirstToWins());
            if (ImGui.inputInt("先取本数（2～5）", FIRST_TO_WINS)) {
                config.setBmsirArenaFirstToWins(FIRST_TO_WINS.get());
            }
            ImGui.textDisabled("各プレイヤーが先取本数ぶん選曲し、重複なしで抽選します");
        } else if (SERIES_FORMAT.get() == 1) {
            ImGui.textDisabled("全員が1曲ずつ選び、全候補を重複なしで回します");
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
                "判定・ゲージ仕様" + idSuffix,
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
            case "minbp" -> "BP Arena / CB（少ない順）";
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

    private static String rulesetProfileLabel(String profile) {
        return "oraja".equals(profile) ? "oraja" : "LR2";
    }

    static String seriesFormatLabel(String format, int firstToWins) {
        return switch (format) {
            case "bo2" -> "BO2（2曲総合）";
            case "all_picks" -> "全員の曲を回す";
            case "first_to" -> Math.max(2, Math.min(5, firstToWins)) + "本先取";
            default -> "1曲";
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
        ImGui.text("参加者");
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
                label.append(" [観戦]");
            }
            if (player.path("pending").asBoolean()) {
                label.append(" [次戦参加]");
            }
            int wins = player.path("series_wins").asInt();
            if (!single) {
                label.append("  ").append(wins).append("勝");
            }
            ImGui.text(label.toString());
            if (!host || playerId == BMSIRArenaClient.currentPlayerId()) {
                continue;
            }
            ImGui.sameLine();
            if (ImGui.smallButton("キック##room-kick-" + playerId)) {
                BMSIRArenaClient.requestRoomKick(playerId);
            }
            ImGui.sameLine();
            if (ImGui.smallButton("HOST移譲##room-host-" + playerId)) {
                BMSIRArenaClient.requestRoomTransferHost(playerId);
            }
            if (single) {
                ImGui.sameLine();
                if (ImGui.smallButton("選曲担当##room-selector-" + playerId)) {
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
                "レート %d → %d (%+.1f)",
                Math.round(before),
                Math.round(after),
                delta
        );
    }

    private static void renderManual() {
        JsonNode manual = BMSIRArenaClient.manualView();
        if (!manual.isObject() || manual.size() == 0) {
            ImGui.textDisabled("マニュアルを取得できていません。");
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
        if (ImGui.button(FontAwesomeIcons.SyncAlt + " マニュアルを更新")) {
            BMSIRArenaClient.requestArenaManual();
        }
        ImGui.textDisabled(
                "取得済みの内容はローカルに保存され、オフライン時も表示できます。"
        );
    }

    private static void renderChat(boolean allowInput, float height) {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        ImBoolean muted = new ImBoolean(config != null && config.isBmsirArenaMuteChat());
        if (ImGui.checkbox("チャットをミュート", muted) && config != null) {
            config.setBmsirArenaMuteChat(muted.get());
        }
        if (muted.get()) {
            ImGui.textDisabled("チャットはこの本体でのみ非表示です");
            return;
        }
        List<JsonNode> messages = BMSIRArenaClient.chatMessages();
        if (ImGui.beginChild("##bmsir-arena-chat-log", 0, height, true)) {
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
        if (
                !allowInput
                        || (!BMSIRArenaClient.isReserved()
                                && BMSIRArenaClient.currentRoomCode().isBlank())
        ) {
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
            setVisible(false);
        }
        renderOverlayHotkeySetting(config);
        ImGui.textDisabled("戻らない場合は固定のF5メニューから再表示できます");
        if (ImGui.button("Arenaログフォルダを開く")) {
            if (!BMSIRArenaLog.openLogFolder()) {
                ImGuiNotify.warning("Arenaログフォルダを開けませんでした");
            }
        }
        ImGui.sameLine();
        ImGui.textDisabled(BMSIRArenaLog.logFileName());
        ImGui.textDisabled("認証情報とチャット本文はログへ記録しません");
        GRAPH_HIGHLIGHT.set(config.getBmsirArenaGraphHighlight());
        if (ImGui.combo(
                "グラフの強調",
                GRAPH_HIGHLIGHT,
                new String[]{"現在1位", "自分"}
        )) {
            config.setBmsirArenaGraphHighlight(GRAPH_HIGHLIGHT.get());
        }
        TARGET_MODE.set(arenaTargetModeIndex(config.getBmsirArenaTargetMode()));
        if (ImGui.combo("本体ターゲット", TARGET_MODE, ARENA_TARGET_MODES)) {
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
        if (ImGui.combo("グラフの並び", GRAPH_ORDER, ARENA_GRAPH_ORDERS)) {
            config.setBmsirArenaGraphOrder(arenaGraphOrder(GRAPH_ORDER.get()));
            saveSettingsOrWarn();
        }
        ImGui.textDisabled("順位順では自分の棒は赤、入室順固定では色も入室順で固定します");

        ImBoolean cursor = new ImBoolean(config.isBmsirArenaShowCursor());
        if (ImGui.checkbox("プレイ中もマウスカーソルを表示", cursor)) {
            config.setBmsirArenaShowCursor(cursor.get());
        }
        ImBoolean presentation = new ImBoolean(
                config.isBmsirArenaPresentationOverlayEnabled()
        );
        if (ImGui.checkbox("重要フェーズを画面中央に大きく表示", presentation)) {
            config.setBmsirArenaPresentationOverlayEnabled(
                    presentation.get()
            );
        }
        ImBoolean countdownSe = new ImBoolean(
                config.isBmsirArenaCountdownSeEnabled()
        );
        if (ImGui.checkbox("Arena 3・2・1カウントSE", countdownSe)) {
            config.setBmsirArenaCountdownSeEnabled(countdownSe.get());
        }
        ImBoolean startSe = new ImBoolean(config.isBmsirArenaStartSeEnabled());
        if (ImGui.checkbox("Arena開始SE", startSe)) {
            config.setBmsirArenaStartSeEnabled(startSe.get());
        }
        ImBoolean phaseWarning = new ImBoolean(
                config.isBmsirArenaPhaseWarningEnabled()
        );
        if (ImGui.checkbox("選曲・OP残り10秒／5秒の警告SE", phaseWarning)) {
            config.setBmsirArenaPhaseWarningEnabled(phaseWarning.get());
        }
        int[] notificationVolume = {
                config.getBmsirArenaNotificationSeVolume()
        };
        if (ImGui.sliderInt(
                "Arena通知SE音量",
                notificationVolume,
                0,
                100
        )) {
            config.setBmsirArenaNotificationSeVolume(notificationVolume[0]);
        }
        ImBoolean unrestricted = new ImBoolean(
                config.isBmsirArenaUnrestrictedRating()
        );
        if (ImGui.checkbox("レート制限なしマッチを許可", unrestricted)) {
            config.setBmsirArenaUnrestrictedRating(unrestricted.get());
        }
        ImGui.textDisabled("距離のある即時マッチは相手も許可した場合だけ成立します");
        ImBoolean allowCpu = new ImBoolean(config.isBmsirArenaAllowCpu());
        if (ImGui.checkbox("1人待機中のCPU戦を許可", allowCpu)) {
            config.setBmsirArenaAllowCpu(allowCpu.get());
        }
        ImGui.textDisabled("OFFの場合、人間がもう1人来るまで待機します。2人以上ではCPU補充します");
        ImBoolean allowHigherSelection = new ImBoolean(
                config.isBmsirArenaAllowHigherSelection()
        );
        if (ImGui.checkbox(
                "高レート基準の選曲を許可",
                allowHigherSelection
        )) {
            config.setBmsirArenaAllowHigherSelection(
                    allowHigherSelection.get()
            );
        }
        ImGui.textDisabled(
                "ONの場合、自分の解放済み上限は部屋の最低選曲基準から除外されます"
        );
        ImBoolean mirror = new ImBoolean(config.isBmsirArenaRandomMirror());
        if (ImGui.checkbox("同期RANDOMを左右反転して受け取る", mirror)) {
            config.setBmsirArenaRandomMirror(mirror.get());
        }
        ImBoolean stay = new ImBoolean(config.isBmsirArenaStayInRoom());
        if (ImGui.checkbox("ルーム対戦後も部屋に残る", stay)) {
            config.setBmsirArenaStayInRoom(stay.get());
            if (!"ranked".equals(BMSIRArenaClient.currentMatchMode())) {
                BMSIRArenaClient.requestRoomStay(stay.get());
            }
        }
        ImGui.separator();
        ImGui.textWrapped(
                "Arenaウィンドウの位置とサイズは5／7／9／10／14KEYごと、"
                        + "通常・コンパクト・プレイ中グラフ・ステータスごとに保存されます。"
        );
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
            ImGui.textDisabled("試合中に対象プレイヤーを選択できます");
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
            ImGui.textDisabled("指定できる対戦相手がいません");
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
            ImGuiNotify.warning("Arena設定を保存できませんでした");
        }
    }

    private static void renderOverlayHotkeySetting(PlayerConfig config) {
        ImGui.text(
                "オーバーレイ表示キー: "
                        + BMSIRArenaHotkey.label(
                                config.getBmsirArenaOverlayHotkeyKeys()
                        )
        );
        if (hotkeyCaptureActive) {
            ImGui.textColored(
                    ImColor.rgb(255, 211, 106),
                    "登録したいキーをすべて押して、全部離してください"
            );
            if (!HOTKEY_CAPTURE_KEYS.isEmpty()) {
                ImGui.text(
                        "入力中: "
                                + BMSIRArenaHotkey.label(capturedHotkeyKeys())
                );
            }
            ImGui.textDisabled(
                    "1キー単体も可／Escでキャンセル／解除は下の「解除」ボタン"
            );
            captureOverlayHotkey(config);
            return;
        }
        if (ImGui.button("表示キーを変更")) {
            HOTKEY_CAPTURE_KEYS.clear();
            hotkeyCaptureActive = true;
        }
        ImGui.sameLine();
        if (ImGui.button("初期値へ戻す")) {
            config.setBmsirArenaOverlayHotkeyKeys(
                    BMSIRArenaHotkey.defaultKeys()
            );
            saveHotkeyOrWarn();
        }
        ImGui.sameLine();
        if (ImGui.button("解除")) {
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
                    "Arena表示キーを "
                            + BMSIRArenaHotkey.label(captured)
                            + " に変更しました"
            );
        }
    }

    private static boolean saveHotkeyOrWarn() {
        if (BMSIRArenaClient.saveArenaConfig()) {
            return true;
        }
        ImGuiNotify.warning("Arena表示キーを保存できませんでした");
        return false;
    }

    private static int[] capturedHotkeyKeys() {
        return HOTKEY_CAPTURE_KEYS.stream().mapToInt(Integer::intValue).toArray();
    }

    public static boolean isHotkeyCaptureActive() {
        return hotkeyCaptureActive;
    }

    public static boolean isKeyboardInputCaptured() {
        return keyboardInputCaptured || hotkeyCaptureActive;
    }

    public static void updateKeyboardInputCapture(boolean captured) {
        keyboardInputCaptured = captured;
    }

    private static void tableText(String text) {
        ImGui.tableNextColumn();
        ImGui.textUnformatted(text == null || text.isBlank() ? "-" : text);
    }
}
