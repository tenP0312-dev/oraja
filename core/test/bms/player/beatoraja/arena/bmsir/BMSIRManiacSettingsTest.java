package bms.player.beatoraja.arena.bmsir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BMSIRManiacSettingsTest {
    @Test
    public void canonicalOptionsRoundTripForIrSync() {
        BMSIRManiacSettings source = new BMSIRManiacSettings();
        source.setExtraMode(2);
        source.setTornado(30);

        BMSIRManiacSettings restored = BMSIRManiacSettings.fromCanonicalOptions(
                source.canonicalOptions()
        );

        assertEquals(source.canonicalOptions(), restored.canonicalOptions());
        assertEquals(BMSIRManiacSettings.RankingClass.EXTRA, restored.rankingClass());
    }

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
    public void autoScratchUsesItsOwnDoubleBattleIdentity() {
        BMSIRManiacSettings manual = new BMSIRManiacSettings();
        manual.setDoubleBattle(true);
        BMSIRManiacSettings assisted = new BMSIRManiacSettings(manual);
        assisted.setAutoScratch(true);

        assertFalse(manual.canonicalOptions().contains("autoscratch"));
        assertTrue(assisted.canonicalOptions().contains("autoscratch=true"));
        assertNotEquals(manual.storageChartId("abc"), assisted.storageChartId("abc"));
        assertNotEquals(manual.virtualChartId("abc"), assisted.virtualChartId("abc"));
        assertTrue(BMSIRManiacSettings.fromCanonicalOptions(
                assisted.canonicalOptions()
        ).isAutoScratch());
    }

    @Test
    public void autoScratchCannotRemainEnabledWithoutDoubleBattle() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setDoubleBattle(true);
        settings.setAutoScratch(true);
        settings.setDoubleBattle(false);

        assertFalse(settings.isAutoScratch());
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

    @Test
    public void selectingExtraModeClearsIncompatibleChartGeneration() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAddNotes(30);
        settings.setAddLongNotes(40);
        settings.setDoubleBattle(true);
        settings.setAutoScratch(true);
        settings.setRandomLink(BMSIRManiacSettings.RANDOM_LINK_SYNC);

        settings.selectExtraMode(3);

        assertEquals(3, settings.getExtraMode());
        assertEquals(0, settings.getAddNotes());
        assertEquals(0, settings.getAddLongNotes());
        assertFalse(settings.isDoubleBattle());
        assertFalse(settings.isAutoScratch());
        assertEquals(BMSIRManiacSettings.RANDOM_LINK_OFF, settings.getRandomLink());
        assertEquals(BMSIRManiacSettings.RankingClass.EXTRA, settings.rankingClass());
    }

    @Test
    public void selectingDoubleBattleClearsIncompatibleChartGeneration() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setExtraMode(2);
        settings.setAddNotes(30);
        settings.setAddLongNotes(40);

        settings.selectDoubleBattle(true, true);

        assertTrue(settings.isDoubleBattle());
        assertTrue(settings.isAutoScratch());
        assertEquals(0, settings.getExtraMode());
        assertEquals(0, settings.getAddNotes());
        assertEquals(0, settings.getAddLongNotes());
        assertEquals(BMSIRManiacSettings.RankingClass.DOUBLE_BATTLE, settings.rankingClass());
    }

    @Test
    public void spToDpHasCanonicalAndStorageIdentityWithoutChangingLegacyIdentity() {
        BMSIRManiacSettings legacy = new BMSIRManiacSettings();
        legacy.setTornado(30);
        assertFalse(legacy.canonicalOptions().contains("sp2dp"));

        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setSpToDpDifficulty(2);
        BMSIRManiacSettings restored = BMSIRManiacSettings.fromCanonicalOptions(
                settings.canonicalOptions()
        );

        assertEquals(2, restored.getSpToDpDifficulty());
        assertEquals(BMSIRManiacSettings.RankingClass.SP_TO_DP, restored.rankingClass());
        assertTrue(BMSIRManiacApiClient.canSubmit(restored));
        assertNotEquals(settings.storageChartId("chart"), legacy.storageChartId("chart"));
        assertNotEquals(settings.virtualChartId("chart"), null);
    }

    @Test
    public void spToDpAndDoubleBattleAreMutuallyExclusive() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setDoubleBattle(true);
        settings.setSpToDpDifficulty(3);
        assertFalse(settings.isDoubleBattle());
        assertEquals(3, settings.getSpToDpDifficulty());

        settings.selectDoubleBattle(true, false);
        assertEquals(0, settings.getSpToDpDifficulty());
        assertTrue(settings.isDoubleBattle());
    }
}
