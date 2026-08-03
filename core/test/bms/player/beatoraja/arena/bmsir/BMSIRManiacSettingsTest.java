package bms.player.beatoraja.arena.bmsir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BMSIRManiacSettingsTest {
    @Test
    public void defaultsAreNormalAndInactive() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        assertFalse(settings.isActive());
        assertEquals(BMSIRManiacSettings.RankingClass.NORMAL, settings.rankingClass());
        assertNull(settings.virtualChartId("abc"));
        assertTrue(settings.isWarnDoubleBattleOnDp());
    }

    @Test
    public void dedicatedModesHaveStableDistinctVirtualIds() {
        BMSIRManiacSettings extra = new BMSIRManiacSettings();
        extra.setExtraMode(2);
        BMSIRManiacSettings notes = new BMSIRManiacSettings();
        notes.setAddNotes(30);

        assertEquals(BMSIRManiacSettings.RankingClass.EXTRA, extra.rankingClass());
        assertEquals(extra.virtualChartId("ABC"), extra.virtualChartId("abc"));
        assertNotEquals(extra.virtualChartId("abc"), notes.virtualChartId("abc"));
        assertEquals(extra.generationSeed("abc"), extra.generationSeed("ABC"));
    }

    @Test
    public void complexOrFreeSeedModesStayLocalOnly() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setExtraMode(1);
        settings.setAddNotes(20);
        assertEquals(BMSIRManiacSettings.RankingClass.LOCAL_ONLY, settings.rankingClass());

        settings = new BMSIRManiacSettings();
        settings.setDoubleBattle(true);
        settings.setAddLongNotes(40);
        assertEquals(BMSIRManiacSettings.RankingClass.LOCAL_ONLY, settings.rankingClass());

        settings = new BMSIRManiacSettings();
        settings.setExtraMode(1);
        settings.setGenerationSeedOverride(42L);
        assertEquals(42L, settings.generationSeed("abc"));
        assertEquals(BMSIRManiacSettings.RankingClass.LOCAL_ONLY, settings.rankingClass());
    }

    @Test
    public void percentagesAreRoundedAndClampedToMenuSteps() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAddMines(26);
        settings.setWave(999);
        assertEquals(30, settings.getAddMines());
        assertEquals(100, settings.getWave());
        assertEquals(BMSIRManiacSettings.RankingClass.MANIAC_STANDARD, settings.rankingClass());
    }
}
