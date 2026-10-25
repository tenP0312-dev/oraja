package bms.player.beatoraja.arena.bmsir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BMSIRManiacSettingsTest {
    @Test
    public void addNotesVersionsKeepOldIdentitiesAndRoundTripNewPlacement() {
        BMSIRManiacSettings current = new BMSIRManiacSettings();
        current.setAddNotes(100);
        BMSIRManiacSettings restored = BMSIRManiacSettings.fromCanonicalOptions(current.canonicalOptions());
        assertNotNull(restored);
        assertEquals(current.canonicalOptions(), restored.canonicalOptions());
        assertEquals("add-notes-100-p2", restored.rankingKey());
        assertEquals("bmsir-maniac-v1-a295fc44e65abc8f3d4cb90bb896a7f17a250bf631c6645a52046c2922fbeefd",
                restored.virtualChartId("a".repeat(64)));
        BMSIRManiacSettings legacy = BMSIRManiacSettings.fromCanonicalOptions(
                current.canonicalOptions().replace(",notes_version=2", ""));
        assertNotNull(legacy);
        assertEquals(1, legacy.getAddNotesPlacementVersion());
        assertEquals("add-notes-100", legacy.rankingKey());
        assertNotEquals(current.virtualChartId("chart"), legacy.virtualChartId("chart"));
        assertNotEquals(current.storageChartId("chart"), legacy.storageChartId("chart"));
        assertNotEquals(current.generationSeed("chart"), legacy.generationSeed("chart"));
        assertNull(BMSIRManiacSettings.fromCanonicalOptions(
                current.canonicalOptions().replace("notes_version=2", "notes_version=3")));
        assertNull(BMSIRManiacSettings.fromCanonicalOptions(
                current.canonicalOptions().replace("notes=100", "notes=0")));
        assertEquals(1, new bms.player.beatoraja.ReplayData().bmsirAddNotesPlacementVersion);
    }
    @Test
    public void canonicalOptionsRoundTripForIrSync() {
        BMSIRManiacSettings source = new BMSIRManiacSettings();
        source.setExtraMode(2);
        source.setHiddenSudden1P(1);

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
        assertEquals(30, settings.getAddMines());
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
        legacy.setHiddenSudden1P(1);
        assertFalse(legacy.canonicalOptions().contains("sp2dp"));

        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setSpToDpDifficulty(2);
        assertTrue(settings.canonicalOptions().contains("sp2dp_version=2"));
        BMSIRManiacSettings restored = BMSIRManiacSettings.fromCanonicalOptions(
                settings.canonicalOptions()
        );

        assertEquals(2, restored.getSpToDpDifficulty());
        assertEquals(BMSIRManiacSettings.RankingClass.SP_TO_DP, restored.rankingClass());
        assertTrue(BMSIRManiacApiClient.canSubmit(restored));
        assertNotEquals(settings.storageChartId("chart"), legacy.storageChartId("chart"));
        assertNotEquals(settings.virtualChartId("chart"), null);

        String legacyCanonical = settings.canonicalOptions()
                .replace(",sp2dp_version=2", "");
        BMSIRManiacSettings legacySpToDp = BMSIRManiacSettings.fromCanonicalOptions(
                legacyCanonical
        );
        assertNotNull(legacySpToDp);
        assertEquals(legacyCanonical, legacySpToDp.canonicalOptions());
        assertNotEquals(
                settings.virtualChartId("chart"),
                legacySpToDp.virtualChartId("chart")
        );
        assertNull(BMSIRManiacSettings.fromCanonicalOptions(
                settings.canonicalOptions().replace("sp2dp_version=2", "sp2dp_version=3")
        ));
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

    @Test
    public void removedVisualOptionsKeepTheLegacyCanonicalFormat() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setHiddenSudden1P(1);

        // Existing local score keys hash this exact string, so removed options stay pinned at zero.
        assertEquals("hs1=1,hs2=0,extra=0,notes=0,long=0,mines=0,accel=0,soft=0,"
                        + "earthquake=0,tornado=0,superloop=0,gambol=0,char=0,heartbeat=0,"
                        + "loudness=0,nabeatsu=0,sin=0,wave=0,spiral=0,sidejump=0,"
                        + "db=false,link=off,seed=fixed,algorithm=1",
                settings.canonicalOptions());
    }

    @Test
    public void legacyRemovedVisualOptionValuesAreIgnored() {
        String legacy = "hs1=1,hs2=0,extra=0,notes=0,long=0,mines=0,accel=2,soft=1,"
                + "earthquake=30,tornado=30,superloop=30,gambol=0,char=30,heartbeat=30,"
                + "loudness=0,nabeatsu=30,sin=30,wave=30,spiral=30,sidejump=30,"
                + "db=false,link=off,seed=fixed,algorithm=1";
        BMSIRManiacSettings restored = BMSIRManiacSettings.fromCanonicalOptions(legacy);
        assertNotNull(restored);
        assertEquals(1, restored.getHiddenSudden1P());
        assertTrue(restored.canonicalOptions().contains("earthquake=0,tornado=0"));
        assertNull(BMSIRManiacSettings.fromCanonicalOptions(legacy.replace("hs1=1", "hs1=0")));

        com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
        json.setIgnoreUnknownFields(true);
        BMSIRManiacSettings stored = json.fromJson(BMSIRManiacSettings.class,
                "{\"tornado\":30,\"softLanding\":2,\"acceleration\":1,\"hiddenSudden2P\":2}");
        assertEquals(2, stored.validate().getHiddenSudden2P());
        assertEquals(BMSIRManiacSettings.RankingClass.MANIAC_STANDARD, stored.rankingClass());
    }
}
