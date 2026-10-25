package bms.player.beatoraja.skin.property;

import bms.player.beatoraja.PlayConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HispeedSettingEventsTest {
    @Test void selectionEventsChangeOnlyTheChosenSpeedMode() {
        PlayConfig pc = new PlayConfig(); pc.setHispeed(2f); pc.setBmsirLr2Hispeed(3f);
        pc.setBmsirHispeedMode(PlayConfig.HISPEED_MODE_FLOATING);
        assertTrue(EventFactory.changeConfiguredHispeed(pc, true, true));
        assertEquals(2.5f, pc.getBmsirFloatingHispeed()); assertEquals(2f, pc.getHispeed());
        // Floating keys keep the speed ratio by moving the saved green number (30 * 2 / 2.5).
        assertEquals(3f, pc.getEffectiveLr2Hispeed()); assertEquals(24f, pc.getBmsirFloatingGreenFrames(), .00001);
        assertTrue(EventFactory.changeConfiguredDuration(pc, true, 5));
        assertEquals(24.3f, pc.getBmsirFloatingGreenFrames(), .00001); assertEquals(500, pc.getDuration());
        pc.setBmsirHispeedMode(PlayConfig.HISPEED_MODE_LR2);
        assertTrue(EventFactory.changeConfiguredHispeed(pc, false, true));
        assertEquals(3.25f, pc.getEffectiveLr2Hispeed()); assertEquals(2f, pc.getHispeed());
        assertFalse(EventFactory.changeConfiguredDuration(pc, false, 10)); assertEquals(500, pc.getDuration());
        pc.setBmsirHispeedMode(PlayConfig.HISPEED_MODE_NORMAL);
        assertTrue(EventFactory.changeConfiguredHispeed(pc, true, false)); assertEquals(1.75f, pc.getHispeed());
    }
}
