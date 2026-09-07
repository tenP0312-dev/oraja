package bms.player.beatoraja.arena.bmsir;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRManiacPlayContextTest {
    @Test void previewOwnsPlacementWithoutChangingSavedOptionsOrEnablingOnlinePlay() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setDoubleBattle(true);
        settings.setNantokaMania(true);
        settings.setSevenToNinePreview(true);
        BMSModel model = model(Mode.BEAT_7K);
        BMSIRManiacPlayContext context = BMSIRManiacPlayContext.prepare(settings, model, false);
        assertNotNull(context);
        assertEquals(Mode.POPN_9K, model.getMode());
        assertFalse(context.isDoubleBattleApplied());
        assertFalse(context.settings().isNantokaMania());
        assertFalse(BMSIRManiacApiClient.canSubmit(context.settings()));
        assertFalse(BMSIRManiacPlayContext.allowsDuringArena(settings, Mode.BEAT_7K));
        assertTrue(settings.isDoubleBattle());
        assertTrue(settings.isNantokaMania());
        assertTrue(BMSIRManiacSettings.fromCanonicalOptions(settings.canonicalOptions()).isSevenToNinePreview());
        assertNull(BMSIRManiacPlayContext.prepare(settings, model(Mode.BEAT_7K), true));
        BMSIRManiacSettings previewOnly = new BMSIRManiacSettings();
        previewOnly.setSevenToNinePreview(true);
        assertNull(BMSIRManiacPlayContext.effectiveSettings(previewOnly, Mode.POPN_9K));
        assertNull(BMSIRManiacPlayContext.effectiveSettings(previewOnly, Mode.BEAT_14K));
    }
    @Test
    void doubleBattleTurnsSevenKeysIntoFourteenKeys() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setDoubleBattle(true);
        BMSModel model = model(Mode.BEAT_7K);

        BMSIRManiacPlayContext context =
                BMSIRManiacPlayContext.prepare(settings, model, false);

        assertEquals(Mode.BEAT_14K, model.getMode());
        assertTrue(context.isDoubleBattleApplied());
        assertFalse(context.isDoubleBattleSuspended());
        assertEquals(BMSIRManiacSettings.RankingClass.DOUBLE_BATTLE,
                context.settings().rankingClass());
        assertTrue(model.getAllTimeLines()[0].getNote(8) instanceof NormalNote);
    }

    @Test
    void doubleBattleAutoScratchMovesBothScratchLanesToBackground() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setDoubleBattle(true);
        settings.setAutoScratch(true);
        BMSModel model = model(Mode.BEAT_7K);
        model.getAllTimeLines()[0].setNote(
                Mode.BEAT_7K.scratchKey[0],
                new NormalNote(2)
        );

        BMSIRManiacPlayContext context =
                BMSIRManiacPlayContext.prepare(settings, model, false);

        assertNotNull(context);
        assertTrue(context.settings().isAutoScratch());
        for (int scratch : Mode.BEAT_14K.scratchKey) {
            assertNull(model.getAllTimeLines()[0].getNote(scratch));
        }
        assertTrue(model.getAllTimeLines()[0].getBackGroundNotes().length > 0);
    }

    @Test
    void nativeDoubleChartRemainsNormalAndReportsSuspension() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setDoubleBattle(true);
        BMSModel model = model(Mode.BEAT_14K);

        assertTrue(BMSIRManiacPlayContext.isDoubleBattleSuspended(
                settings,
                model.getMode()
        ));
        assertNull(BMSIRManiacPlayContext.prepare(settings, model, false));
        assertEquals(Mode.BEAT_14K, model.getMode());
    }

    @Test
    void scoreIdentityUsesDoubleBattleOnlyWhenItApplies() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setDoubleBattle(true);

        BMSIRManiacSettings single = BMSIRManiacPlayContext.effectiveSettings(
                settings,
                Mode.BEAT_7K
        );
        BMSIRManiacSettings doublePlay = BMSIRManiacPlayContext.effectiveSettings(
                settings,
                Mode.BEAT_14K
        );

        assertNotNull(single);
        assertTrue(single.isDoubleBattle());
        assertNull(doublePlay);
    }

    @Test
    void otherManiacSettingsRemainActiveWhenDoubleBattleIsSuspended() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setDoubleBattle(true);
        settings.setTornado(30);

        BMSIRManiacSettings applied = BMSIRManiacPlayContext.effectiveSettings(
                settings,
                Mode.BEAT_14K
        );

        assertNotNull(applied);
        assertFalse(applied.isDoubleBattle());
        assertEquals(30, applied.getTornado());
        assertTrue(BMSIRManiacPlayContext.prepare(settings, model(Mode.BEAT_14K), false)
                .isDoubleBattleSuspended());
    }

    @Test
    void arenaAndCourseGuardLeavesChartUntouched() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setExtraMode(2);
        BMSModel model = model(Mode.BEAT_7K);
        assertNull(BMSIRManiacPlayContext.prepare(settings, model, true));
        assertEquals(Mode.BEAT_7K, model.getMode());
    }

    @Test
    void spToDpAppliesOnlyToSupportedSingleBeatCharts() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setSpToDpDifficulty(2);

        BMSModel single = model(Mode.BEAT_7K);
        BMSIRManiacPlayContext context = BMSIRManiacPlayContext.prepare(
                settings,
                single,
                false
        );
        assertNotNull(context);
        assertEquals(Mode.BEAT_14K, single.getMode());
        assertEquals(BMSIRManiacSettings.RankingClass.SP_TO_DP,
                context.settings().rankingClass());

        assertNull(BMSIRManiacPlayContext.effectiveSettings(settings, Mode.BEAT_14K));
        assertNull(BMSIRManiacPlayContext.effectiveSettings(settings, Mode.POPN_9K));
        assertTrue(BMSIRManiacPlayContext.allowsDuringArena(settings, Mode.BEAT_7K));
        assertFalse(BMSIRManiacPlayContext.allowsDuringArena(settings, Mode.BEAT_14K));

        settings.setTornado(20);
        assertFalse(BMSIRManiacPlayContext.allowsDuringArena(settings, Mode.BEAT_7K));
    }

    private static BMSModel model(Mode mode) {
        BMSModel model = new BMSModel();
        model.setMode(mode);
        model.setBpm(120);
        model.setSHA256("chart");
        TimeLine line = new TimeLine(0.0, 0L, mode.key);
        line.setBPM(120);
        line.setNote(0, new NormalNote(1));
        model.setAllTimeLine(new TimeLine[]{line});
        return model;
    }
}
