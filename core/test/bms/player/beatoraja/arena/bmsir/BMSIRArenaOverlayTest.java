package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.PlayerConfig;
import com.badlogic.gdx.Input.Keys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import imgui.ImColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSIRArenaOverlayTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void gameplayLayoutUsesSeparatePersistentIdsForSpAndDp() {
        assertEquals(
                "##gameplay-sp",
                BMSIRArenaOverlay.gameplayWindowId(false, false)
        );
        assertEquals(
                "##gameplay-dp",
                BMSIRArenaOverlay.gameplayWindowId(false, true)
        );
        assertEquals(
                "##compact-play-sp",
                BMSIRArenaOverlay.gameplayWindowId(true, false)
        );
        assertEquals(
                "##compact-play-dp",
                BMSIRArenaOverlay.gameplayWindowId(true, true)
        );
    }

    @Test
    void gameplayLayoutUsesPersistentIdsForEverySupportedKeyCount() {
        assertEquals(
                "##gameplay-5",
                BMSIRArenaOverlay.gameplayWindowId(false, Mode.BEAT_5K.id)
        );
        assertEquals(
                "##gameplay-7",
                BMSIRArenaOverlay.gameplayWindowId(false, Mode.BEAT_7K.id)
        );
        assertEquals(
                "##compact-play-9",
                BMSIRArenaOverlay.gameplayWindowId(true, Mode.POPN_9K.id)
        );
        assertEquals(
                "##gameplay-10",
                BMSIRArenaOverlay.gameplayWindowId(false, Mode.BEAT_10K.id)
        );
        assertEquals(
                "##gameplay-14",
                BMSIRArenaOverlay.gameplayWindowId(false, Mode.BEAT_14K.id)
        );
    }

    @Test
    void scoreRateUsesTheChartMaximumAndClampsInvalidScores() {
        assertEquals(0.0, BMSIRArenaOverlay.scoreRate(0, 100));
        assertEquals(0.5, BMSIRArenaOverlay.scoreRate(100, 100));
        assertEquals(1.0, BMSIRArenaOverlay.scoreRate(200, 100));
        assertEquals(1.0, BMSIRArenaOverlay.scoreRate(250, 100));
        assertEquals(0.0, BMSIRArenaOverlay.scoreRate(-1, 100));
        assertEquals(0.0, BMSIRArenaOverlay.scoreRate(100, 0));
    }

    @Test
    void scoreGraphStaysInsideTheActualPlotBounds() {
        assertEquals(92.0f, BMSIRArenaOverlay.scorePlotHeight(210.0f));
        assertEquals(10.0f, BMSIRArenaOverlay.scoreBarTop(10.0f, 120.0f, 1.0));
        assertEquals(10.0f, BMSIRArenaOverlay.scoreBarTop(10.0f, 120.0f, 1.1));
        assertEquals(120.0f, BMSIRArenaOverlay.scoreBarTop(10.0f, 120.0f, 0.0));
        assertEquals(120.0f, BMSIRArenaOverlay.scoreBarTop(10.0f, 120.0f, -0.1));
    }

    @Test
    void gameplayWindowDefaultsScaleWithinTheViewport() {
        assertEquals(640.0f, BMSIRArenaOverlay.defaultGameplayWindowWidth(1280));
        assertEquals(760.0f, BMSIRArenaOverlay.defaultGameplayWindowWidth(1920));
        assertEquals(320.0f, BMSIRArenaOverlay.defaultGameplayWindowWidth(640));
        assertEquals(396.0f, BMSIRArenaOverlay.defaultGameplayWindowHeight(720));
        assertEquals(520.0f, BMSIRArenaOverlay.defaultGameplayWindowHeight(1080));
        assertEquals(300.0f, BMSIRArenaOverlay.defaultGameplayWindowHeight(360));
    }

    @Test
    void battleGraphsAlwaysGrowTowardTheWinningDirection() throws Exception {
        JsonNode player = JSON.readTree("""
                {
                  "exscore": 180,
                  "minbp": 5,
                  "max_combo": 80
                }
                """);
        assertEquals(180, BMSIRArenaOverlay.battleValue("exscore", player, 100));
        assertEquals(95, BMSIRArenaOverlay.battleValue("minbp", player, 100));
        assertEquals(80, BMSIRArenaOverlay.battleValue("max_combo", player, 100));
        assertEquals(200, BMSIRArenaOverlay.battleMaximum("exscore", player, 100));
        assertEquals(100, BMSIRArenaOverlay.battleMaximum("minbp", player, 100));
        assertEquals(0.95, BMSIRArenaOverlay.battleRate(95, 100));
        assertEquals("CB 5", BMSIRArenaOverlay.ruleMetricLabel("minbp", player));
        assertEquals(
                "COMBO 80",
                BMSIRArenaOverlay.ruleMetricLabel("max_combo", player)
        );
        assertEquals(
                "LOWEST COMBO BREAK WINS",
                BMSIRArenaOverlay.ruleBattleTitle("minbp")
        );
    }

    @Test
    void scoreGraphCanKeepEntryOrderAndColorStableByPlayerId() throws Exception {
        JsonNode match = JSON.readTree("""
                {
                  "players": [
                    {"player_id": 1, "entry_order": 0, "exscore": 100},
                    {"player_id": 2, "entry_order": 1, "exscore": 300},
                    {"player_id": 3, "entry_order": 2, "exscore": 200}
                  ],
                  "rules": {"score_rule": "exscore"}
                }
                """);

        assertEquals(
                java.util.List.of(2, 3, 1),
                BMSIRArenaOverlay.scoreGraphPlayers(
                                match,
                                PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_RANK
                        )
                        .stream()
                        .map(player -> player.path("player_id").asInt())
                        .toList()
        );
        var entryPlayers = BMSIRArenaOverlay.scoreGraphPlayers(
                match,
                PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY
        );
        assertEquals(
                java.util.List.of(1, 2, 3),
                entryPlayers.stream()
                        .map(player -> player.path("player_id").asInt())
                        .toList()
        );
        assertEquals(
                1,
                BMSIRArenaOverlay.scoreGraphColorIndex(
                        match,
                        entryPlayers.get(1),
                        0,
                        PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY
                )
        );
    }

    @Test
    void overlayRecoveryRestoresOnlyAVisibleMode() {
        assertEquals(0, BMSIRArenaOverlay.restoredVisibleMode(0));
        assertEquals(1, BMSIRArenaOverlay.restoredVisibleMode(1));
        assertEquals(0, BMSIRArenaOverlay.restoredVisibleMode(2));
        assertEquals(0, BMSIRArenaOverlay.restoredVisibleMode(-1));
    }

    @Test
    void configurableOverlayHotkeyHasAReadableLabel() {
        assertEquals(
                "Ctrl+Shift+F5",
                BMSIRArenaHotkey.label(
                        new int[]{Keys.CONTROL_LEFT, Keys.SHIFT_RIGHT, Keys.F5}
                )
        );
        assertEquals(
                "Z+X",
                BMSIRArenaHotkey.label(new int[]{Keys.Z, Keys.X})
        );
        assertEquals("Space", BMSIRArenaHotkey.label(new int[]{Keys.SPACE}));
        assertEquals("未設定", BMSIRArenaHotkey.label(new int[0]));
    }

    @Test
    void matchModesAlwaysStateWhetherRatingChanges() {
        assertEquals(
                "レートArena  |  レート変動あり",
                BMSIRArenaOverlay.modeDisplayText("ranked")
        );
        assertEquals(
                "カジュアル  |  レート変動なし",
                BMSIRArenaOverlay.modeDisplayText("casual")
        );
        assertEquals(
                "プライベート  |  レート変動なし",
                BMSIRArenaOverlay.modeDisplayText("private")
        );
    }

    @Test
    void phaseCountdownIsLargeFriendlyAndWarnsNearTheDeadline() {
        assertEquals("残り 08秒", BMSIRArenaOverlay.phaseCountdownText(8));
        assertEquals("残り 00秒", BMSIRArenaOverlay.phaseCountdownText(-1));
        assertEquals(
                ImColor.rgb(106, 169, 255),
                BMSIRArenaOverlay.phaseCountdownColor(11)
        );
        assertEquals(
                ImColor.rgb(255, 211, 106),
                BMSIRArenaOverlay.phaseCountdownColor(10)
        );
        assertEquals(
                ImColor.rgb(255, 211, 106),
                BMSIRArenaOverlay.phaseCountdownColor(6)
        );
        assertEquals(
                ImColor.rgb(255, 115, 115),
                BMSIRArenaOverlay.phaseCountdownColor(5)
        );
    }

    @Test
    void completedRatedResultUsesOneLargeReadableDeltaLine() {
        assertEquals(
                "レート 1000 → 1001 (+1.0)",
                BMSIRArenaOverlay.ratingChangeText(1000, 1001, 1)
        );
        assertEquals(
                "レート 1000 → 999 (-1.0)",
                BMSIRArenaOverlay.ratingChangeText(1000, 999, -1)
        );
        assertEquals(
                "レート 1000 → 1000 (+0.0)",
                BMSIRArenaOverlay.ratingChangeText(1000, 1000, 0)
        );
    }

    @Test
    void ratedBo2HasAnUnambiguousSeriesLabel() {
        assertEquals("BO2（2曲総合）", BMSIRArenaOverlay.seriesFormatLabel("bo2", 2));
    }
}
