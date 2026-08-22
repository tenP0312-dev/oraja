package bms.player.beatoraja.play;

import bms.player.beatoraja.PlayConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSPlayerLiveLaneSettingsTest {
    @Test
    void fixedHispeedPersistsTheLiveDurationAndCompleteCoverState() {
        PlayConfig saved = new PlayConfig();
        saved.setFixhispeed(PlayConfig.FIX_HISPEED_MAINBPM);
        saved.setHispeed(1.0f);
        saved.setDuration(500);

        PlayConfig live = new PlayConfig();
        live.setFixhispeed(PlayConfig.FIX_HISPEED_MAINBPM);
        live.setHispeed(2.5f);
        live.setDuration(384);
        live.setLanecover(0.42f);
        live.setEnablelanecover(false);
        live.setLift(0.18f);
        live.setEnablelift(true);
        live.setHidden(0.27f);
        live.setEnablehidden(true);

        BMSPlayer.copyLiveLaneSettings(saved, live);

        assertEquals(384, saved.getDuration());
        assertEquals(1.0f, saved.getHispeed());
        assertEquals(0.42f, saved.getLanecover());
        assertFalse(saved.isEnablelanecover());
        assertEquals(0.18f, saved.getLift());
        assertTrue(saved.isEnablelift());
        assertEquals(0.27f, saved.getHidden());
        assertTrue(saved.isEnablehidden());
    }

    @Test
    void unfixedHispeedPersistsTheLiveMultiplierWithoutReplacingDuration() {
        PlayConfig saved = new PlayConfig();
        saved.setFixhispeed(PlayConfig.FIX_HISPEED_OFF);
        saved.setHispeed(1.0f);
        saved.setDuration(500);

        PlayConfig live = new PlayConfig();
        live.setFixhispeed(PlayConfig.FIX_HISPEED_OFF);
        live.setHispeed(3.25f);
        live.setDuration(275);

        BMSPlayer.copyLiveLaneSettings(saved, live);

        assertEquals(3.25f, saved.getHispeed());
        assertEquals(500, saved.getDuration());
    }

    @Test
    void lr2OverridePersistsStoredHispeedDurationAndBaseScrollTogether() {
        PlayConfig saved = new PlayConfig();
        saved.setFixhispeed(PlayConfig.FIX_HISPEED_MAINBPM);

        PlayConfig live = new PlayConfig();
        live.setHispeed(2.75f);
        live.setDuration(431);
        live.setBmsirBaseScrollSpeed(137);

        BMSPlayer.copyLiveLaneSettings(saved, live, true);

        assertEquals(2.75f, saved.getHispeed());
        assertEquals(431, saved.getDuration());
        assertEquals(137, saved.getBmsirBaseScrollSpeed());
    }
}
