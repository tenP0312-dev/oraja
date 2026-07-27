package bms.player.beatoraja.arena.bmsir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSIRArenaOverlayTest {
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
        assertEquals(110.0f, BMSIRArenaOverlay.scorePlotHeight(210.0f));
        assertEquals(10.0f, BMSIRArenaOverlay.scoreBarTop(10.0f, 120.0f, 1.0));
        assertEquals(10.0f, BMSIRArenaOverlay.scoreBarTop(10.0f, 120.0f, 1.1));
        assertEquals(120.0f, BMSIRArenaOverlay.scoreBarTop(10.0f, 120.0f, 0.0));
        assertEquals(120.0f, BMSIRArenaOverlay.scoreBarTop(10.0f, 120.0f, -0.1));
    }

    @Test
    void gameplayWindowDefaultsScaleWithinTheViewport() {
        assertEquals(640.0f, BMSIRArenaOverlay.defaultGameplayWindowWidth(1280));
        assertEquals(760.0f, BMSIRArenaOverlay.defaultGameplayWindowWidth(1920));
        assertEquals(420.0f, BMSIRArenaOverlay.defaultGameplayWindowWidth(640));
        assertEquals(396.0f, BMSIRArenaOverlay.defaultGameplayWindowHeight(720));
        assertEquals(520.0f, BMSIRArenaOverlay.defaultGameplayWindowHeight(1080));
        assertEquals(300.0f, BMSIRArenaOverlay.defaultGameplayWindowHeight(360));
    }

    @Test
    void overlayRecoveryRestoresOnlyAVisibleMode() {
        assertEquals(0, BMSIRArenaOverlay.restoredVisibleMode(0));
        assertEquals(1, BMSIRArenaOverlay.restoredVisibleMode(1));
        assertEquals(0, BMSIRArenaOverlay.restoredVisibleMode(2));
        assertEquals(0, BMSIRArenaOverlay.restoredVisibleMode(-1));
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
}
