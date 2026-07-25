package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.modmenu.FontAwesomeIcons;
import bms.player.beatoraja.modmenu.ImGuiRenderer;

import com.fasterxml.jackson.databind.JsonNode;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Compact in-game control surface for the authenticated BMS-IR Arena socket.
 */
public final class BMSIRArenaOverlay {
    private static boolean confirmWithdrawal;

    private BMSIRArenaOverlay() {
    }

    public static void render() {
        if (!BMSIRArenaClient.shouldShowOverlay()) {
            confirmWithdrawal = false;
            return;
        }
        if (BMSIRArenaClient.isGameplayState()) {
            renderGameplayOverlay();
            return;
        }

        ImGui.setNextWindowPos(18, 72, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(480, 520, ImGuiCond.FirstUseEver);
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
            if (ImGui.beginTabItem(FontAwesomeIcons.Trophy + " レートランキング")) {
                renderRanking();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.end();
    }

    private static void renderGameplayOverlay() {
        ImGui.setNextWindowPos(
                Math.max(18, ImGuiRenderer.windowWidth - 378),
                18,
                ImGuiCond.Always
        );
        ImGui.setNextWindowBgAlpha(0.88f);
        int flags = ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoInputs
                | ImGuiWindowFlags.NoNav
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoBringToFrontOnFocus;
        if (!ImGui.begin("BMS-IR Arena##gameplay", flags)) {
            ImGui.end();
            return;
        }
        JsonNode match = BMSIRArenaClient.currentMatchView();
        ImGui.text("BMS-IR Arena");
        if (!match.isObject() || match.size() == 0) {
            ImGui.sameLine();
            ImGui.textDisabled(BMSIRArenaClient.arenaUiMessage());
            ImGui.end();
            return;
        }
        String title = match.path("chart").path("title").asText();
        if (!title.isBlank()) {
            ImGui.textWrapped(title);
        }
        renderGameplayPlayers(match);
        ImGui.end();
    }

    private static void renderGameplayPlayers(JsonNode match) {
        List<JsonNode> players = sortedPlayers(match);
        if (players.isEmpty()) {
            ImGui.textDisabled("参加者を待っています");
            return;
        }
        int flags = ImGuiTableFlags.BordersInnerH | ImGuiTableFlags.RowBg;
        if (ImGui.beginTable("##bmsir-arena-gameplay", 4, flags, 350, 0)) {
            ImGui.tableSetupColumn("#");
            ImGui.tableSetupColumn("Player");
            ImGui.tableSetupColumn("EX");
            ImGui.tableSetupColumn("OP");
            ImGui.tableHeadersRow();
            int previousEx = Integer.MIN_VALUE;
            int placement = 0;
            for (int index = 0; index < players.size(); index++) {
                JsonNode player = players.get(index);
                int exscore = player.path("exscore").asInt();
                if (exscore != previousEx) {
                    placement = index + 1;
                    previousEx = exscore;
                }
                ImGui.tableNextRow();
                tableText(Integer.toString(placement));
                String name = player.path("name").asText();
                if (player.path("player_id").asInt() == BMSIRArenaClient.currentPlayerId()) {
                    name = "> " + name;
                }
                tableText(name);
                tableText(Integer.toString(exscore));
                tableText(player.path("play_option_label").asText("-"));
            }
            ImGui.endTable();
        }
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
        if (confirmWithdrawal) {
            ImGui.text("この対戦を棄権しますか？");
            ImGui.beginDisabled(!connected);
            if (ImGui.button(FontAwesomeIcons.StopCircle + " 棄権する")) {
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
            if (ImGui.button(FontAwesomeIcons.StopCircle + " 対戦を棄権")) {
                confirmWithdrawal = true;
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            refreshButton(connected);
            return;
        }
        if ("withdraw_requested".equals(status)) {
            ImGui.beginDisabled();
            ImGui.button(FontAwesomeIcons.UserClock + " 棄権処理中");
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
        JsonNode match = BMSIRArenaClient.currentMatchView();
        if (!match.isObject() || match.size() == 0) {
            ImGui.textDisabled("現在の試合はありません");
            return;
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

        List<JsonNode> players = sortedPlayers(match);
        if (players.isEmpty()) {
            ImGui.textDisabled("参加者を待っています");
            return;
        }

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
            ImGui.tableSetupColumn("EX");
            ImGui.tableSetupColumn("OP");
            ImGui.tableSetupColumn("Lamp");
            ImGui.tableSetupColumn("Rate");
            ImGui.tableHeadersRow();
            int previousEx = Integer.MIN_VALUE;
            int placement = 0;
            for (int index = 0; index < players.size(); index++) {
                JsonNode player = players.get(index);
                int exscore = player.path("exscore").asInt();
                if (exscore != previousEx) {
                    placement = index + 1;
                    previousEx = exscore;
                }
                int serverPlacement = player.path("placement").asInt(0);
                ImGui.tableNextRow();
                tableText(Integer.toString(serverPlacement > 0 ? serverPlacement : placement));
                String name = player.path("name").asText(
                        Integer.toString(player.path("player_id").asInt())
                );
                if (player.path("player_id").asInt() == BMSIRArenaClient.currentPlayerId()) {
                    name = "> " + name;
                }
                tableText(name);
                tableText(Integer.toString(exscore));
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

    private static String clearLabel(JsonNode player) {
        if ("forfeit".equals(player.path("final_state").asText())) {
            return "DNF（棄権）";
        }
        return player.path("clear_label").asText("-");
    }

    private static List<JsonNode> sortedPlayers(JsonNode match) {
        List<JsonNode> players = new ArrayList<>();
        match.path("players").forEach(players::add);
        players.sort(
                Comparator.comparingInt((JsonNode value) -> value.path("exscore").asInt())
                        .reversed()
                        .thenComparingInt(value -> value.path("player_id").asInt())
        );
        return players;
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

    private static void tableText(String text) {
        ImGui.tableNextColumn();
        ImGui.textUnformatted(text == null || text.isBlank() ? "-" : text);
    }
}
