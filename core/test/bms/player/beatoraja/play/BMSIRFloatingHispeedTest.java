package bms.player.beatoraja.play;

import bms.player.beatoraja.PlayConfig;
import bms.player.beatoraja.ReplayLaneState;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BMSIRFloatingHispeedTest {
    private PlayConfig config() {
        PlayConfig pc = new PlayConfig();
        pc.setBmsirFloatingHispeedEnabled(true);
        pc.setEnablelanecover(false);
        return pc;
    }

    @Test void multiplierAndGreenExamplesIncludeCoverAndLift() {
        PlayConfig pc = config();
        assertEquals(583, BMSIRFloatingHispeed.greenNumber(BMSIRFloatingHispeed.visibleFrames(pc, 150, 1, true)));
        assertEquals(1.64799648f, BMSIRFloatingHispeed.appliedHispeed(pc), 0.000001);
        pc.setEnablelanecover(true); pc.setLanecover(90f / 723f);
        assertEquals(511, BMSIRFloatingHispeed.greenNumber(BMSIRFloatingHispeed.visibleFrames(pc, 150, 1, true)));
        pc.setEnablelanecover(false); pc.setEnablelift(true); pc.setLift(90f / 723f);
        assertEquals(511, BMSIRFloatingHispeed.greenNumber(BMSIRFloatingHispeed.visibleFrames(pc, 150, 1, true)));
        assertEquals(1.88230877f, BMSIRFloatingHispeed.appliedHispeed(pc), 0.000001);
        pc.setEnablelanecover(true); pc.setLanecover(90f / 633f);
        assertEquals(438, BMSIRFloatingHispeed.greenNumber(BMSIRFloatingHispeed.visibleFrames(pc, 150, 1, true)));
        pc.setEnablehidden(true); pc.setHidden(90f / 633f);
        assertEquals(365, BMSIRFloatingHispeed.greenNumber(BMSIRFloatingHispeed.visibleFrames(pc, 150, 1, true)));
    }

    @Test void keyChangeKeepsUnroundedSavedGreenAndCoverRestoresIt() {
        PlayConfig pc = config(); pc.setEnablelanecover(true); pc.setLanecover(0.2f);
        BMSIRFloatingHispeed.rememberGreen(pc, 150);
        float saved = pc.getBmsirFloatingGreenFrames();
        BMSIRFloatingHispeed.changeMultiplier(pc, 0.5f);
        assertEquals(2.5f, pc.getBmsirFloatingHispeed());
        assertEquals(saved, pc.getBmsirFloatingGreenFrames());
        BMSIRFloatingHispeed.moveScratch(pc, 1, 150);
        assertEquals(saved, pc.getBmsirFloatingGreenFrames());
        assertEquals(saved, BMSIRFloatingHispeed.visibleFrames(pc, 150, 1, true), 0.00001);
        assertTrue(pc.getBmsirFloatingHispeed() < 2f);
    }

    @Test void speedKeysMoveGreenSoTheNextSongKeepsTheNewSpeed() {
        PlayConfig pc = config();
        BMSIRFloatingHispeed.rememberGreen(pc, 150);
        BMSIRFloatingHispeed.changeGreen(pc, 0.5f);
        assertEquals(2.5f, pc.getBmsirFloatingHispeed());
        assertEquals(pc.getBmsirFloatingGreenFrames(), BMSIRFloatingHispeed.visibleFrames(pc, 150, 1, true), 0.00001);
        pc.setBmsirFloatingHispeed(9f);
        BMSIRFloatingHispeed.restoreGreen(pc, 150);
        assertEquals(2.5f, pc.getBmsirFloatingHispeed(), 0.00001);
        float green = pc.getBmsirFloatingGreenFrames();
        BMSIRFloatingHispeed.changeGreen(pc, Float.NaN);
        assertEquals(green, pc.getBmsirFloatingGreenFrames());
    }

    @Test void nakedScratchChangesMultiplierAndRemembersGreen() {
        PlayConfig pc = config();
        BMSIRFloatingHispeed.moveScratch(pc, 150, 150);
        assertEquals(3f, pc.getBmsirFloatingHispeed(), 0.00001);
        BMSIRFloatingHispeed.changeMultiplier(pc, Float.NaN);
        assertEquals(3f, pc.getBmsirFloatingHispeed(), 0.00001);
        assertEquals(BMSIRFloatingHispeed.visibleFrames(pc, 150, 1, true), pc.getBmsirFloatingGreenFrames());
        BMSIRFloatingHispeed.changeMultiplier(pc, 100);
        assertEquals(10f, pc.getBmsirFloatingHispeed());
        BMSIRFloatingHispeed.changeMultiplier(pc, -100);
        assertEquals(0.5f, pc.getBmsirFloatingHispeed());
    }

    @Test void bothCoversMoveTogetherAndRespectBounds() {
        PlayConfig pc = config(); pc.setEnablelanecover(true); pc.setEnablehidden(true);
        pc.setLanecover(0.2f); pc.setHidden(0.2f);
        BMSIRFloatingHispeed.moveScratch(pc, 1000, 150);
        assertEquals(361f / 723f, pc.getLanecover(), 0.000001);
        assertEquals(pc.getLanecover(), pc.getHidden());
        BMSIRFloatingHispeed.moveScratch(pc, -1000, 150);
        assertEquals(30f / 723f, pc.getLanecover(), 0.000001);
    }

    @Test void initialCoverNormalizationLimitsLiftAndEqualizesPairedCovers() {
        PlayConfig pc = config(); pc.setEnablelanecover(true); pc.setEnablehidden(true);
        pc.setLanecover(1); pc.setHidden(0.1f);
        BMSIRFloatingHispeed.normalizeCovers(pc);
        assertEquals(361f / 723f, pc.getLanecover(), 0.000001);
        assertEquals(pc.getLanecover(), pc.getHidden());
        pc.setEnablelift(true); pc.setLift(1);
        BMSIRFloatingHispeed.normalizeCovers(pc);
        assertEquals(600f / 723f, pc.getLift(), 0.000001);
        assertTrue(Float.isFinite(BMSIRFloatingHispeed.appliedHispeed(pc)));
    }

    @Test void liftMovesWithoutMovingConfiguredTopAndHiddenTopKeepsSpeed() {
        PlayConfig pc = config(); pc.setEnablelift(true); pc.setLift(0.1f);
        pc.setEnablelanecover(true); pc.setLanecover(0.2f);
        double top = (1 - pc.getLift()) * pc.getLanecover();
        BMSIRFloatingHispeed.toggleCover(pc, 150);
        assertFalse(pc.isBmsirFloatingCoverVisible());
        float speed = pc.getBmsirFloatingHispeed();
        BMSIRFloatingHispeed.moveScratch(pc, -10, 150);
        assertEquals(speed, pc.getBmsirFloatingHispeed());
        assertEquals(top, (1 - pc.getLift()) * pc.getLanecover(), 0.000001);
        BMSIRFloatingHispeed.moveScratch(pc, -1000, 150);
        assertEquals(600f / 723f, pc.getLift(), 0.000001);
        BMSIRFloatingHispeed.toggleCover(pc, 150);
        assertTrue(pc.isBmsirFloatingCoverVisible());
        assertTrue(Float.isFinite(pc.getBmsirFloatingHispeed()));
    }

    @Test void badTempoAndCollapsedLaneRemainFinite() {
        PlayConfig pc = config(); pc.setEnablelanecover(true); pc.setLanecover(1);
        assertEquals(0, BMSIRFloatingHispeed.visibleFrames(pc, 150, 1, true));
        pc.setBmsirFloatingGreenFrames(0);
        BMSIRFloatingHispeed.restoreGreen(pc, 150);
        assertEquals(0.5f, pc.getBmsirFloatingHispeed());
        float speed = pc.getBmsirFloatingHispeed(), green = pc.getBmsirFloatingGreenFrames();
        for (double bpm : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            BMSIRFloatingHispeed.restoreGreen(pc, bpm); BMSIRFloatingHispeed.rememberGreen(pc, bpm);
            assertEquals(0, BMSIRFloatingHispeed.visibleFrames(pc, bpm, 1, true));
            assertEquals(speed, pc.getBmsirFloatingHispeed()); assertEquals(green, pc.getBmsirFloatingGreenFrames());
        }
        assertEquals(0, BMSIRFloatingHispeed.visibleFrames(pc, 150, 0, true));
    }

    @Test void tempoChangesAffectDisplayedGreenButNotStoredSpeedOrGreen() {
        PlayConfig pc = config(); BMSIRFloatingHispeed.rememberGreen(pc, 150);
        float speed = pc.getBmsirFloatingHispeed(), green = pc.getBmsirFloatingGreenFrames();
        assertEquals(1166, BMSIRFloatingHispeed.greenNumber(BMSIRFloatingHispeed.visibleFrames(pc, 75, 1, true)));
        assertEquals(292, BMSIRFloatingHispeed.greenNumber(BMSIRFloatingHispeed.visibleFrames(pc, 300, 1, true)));
        assertEquals(speed, pc.getBmsirFloatingHispeed()); assertEquals(green, pc.getBmsirFloatingGreenFrames());
    }

    @Test void coverAdjustmentKeepsGreenAtCurrentPositiveScroll() {
        PlayConfig pc = config(); pc.setEnablelanecover(true); pc.setLanecover(0.2f);
        BMSIRFloatingHispeed.rememberGreen(pc, 150, 2);
        float saved = pc.getBmsirFloatingGreenFrames();
        BMSIRFloatingHispeed.moveScratch(pc, 1, 150, 2);
        assertEquals(saved, BMSIRFloatingHispeed.visibleFrames(pc, 150, 2, true), 0.000001);
        float multiplier = pc.getBmsirFloatingHispeed();
        BMSIRFloatingHispeed.restoreGreen(pc, 150, 0);
        assertEquals(multiplier, pc.getBmsirFloatingHispeed());
    }

    @Test void replayRoundTripKeepsFractionalGreenAndCoverStateSeparateFromLegacyValues() {
        PlayConfig pc = config(); pc.setBmsirFloatingHispeed(3.125f); pc.setBmsirFloatingGreenFrames(30.123456f);
        pc.setBmsirFloatingCoverVisible(false); pc.setHispeed(7); pc.setDuration(650);
        ReplayLaneState recorded = ReplayLaneState.capture(1000, pc, false);
        Json json = new Json(); ReplayLaneState loaded = json.fromJson(ReplayLaneState.class, json.toJson(recorded));
        PlayConfig restored = new PlayConfig(); loaded.apply(restored);
        assertTrue(restored.isBmsirFloatingHispeedEnabled()); assertEquals(3.125f, restored.getBmsirFloatingHispeed());
        assertEquals(30.123456f, restored.getBmsirFloatingGreenFrames()); assertFalse(restored.isBmsirFloatingCoverVisible());
        assertEquals(7f, restored.getHispeed()); assertEquals(650, restored.getDuration());
        assertTrue(recorded.sameDisplay(loaded));
        restored.setBmsirFloatingHispeed(3.5f);
        assertFalse(recorded.sameDisplay(ReplayLaneState.capture(1000, restored, false)));
        PlayConfig old = json.fromJson(PlayConfig.class, "{hispeed:4,fixhispeed:5}"); old.validate();
        assertFalse(old.isBmsirFloatingHispeedEnabled()); assertEquals(PlayConfig.FIX_HISPEED_STARTBPM, old.getFixhispeed());
    }

    @Test void transitionsUseTimeRatherThanRenderingFrameCount() {
        BMSIRFloatingHispeed.Transition covered = new BMSIRFloatingHispeed.Transition(2, 0);
        covered.update(4, true, 0);
        assertEquals(2 + 2 * Math.sin(Math.PI / 4), covered.update(4, true, 133), 0.01);
        assertEquals(4, covered.update(4, true, 267), 0.000001);
        BMSIRFloatingHispeed.Transition single = new BMSIRFloatingHispeed.Transition(2, 0);
        BMSIRFloatingHispeed.Transition many = new BMSIRFloatingHispeed.Transition(2, 0);
        single.update(4, false, 0); many.update(4, false, 0);
        for (int i = 10; i < 100; i += 10) many.update(4, false, i);
        assertEquals(single.update(4, false, 100), many.update(4, false, 100), 0.000001);
    }
}
