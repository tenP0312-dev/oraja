package bms.player.beatoraja.play;

import bms.model.*;
import bms.player.beatoraja.*;
import com.badlogic.gdx.utils.Json;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static bms.player.beatoraja.skin.SkinProperty.TIMER_PLAY;

class HispeedModeSwitchTest {
    static void set(Object object, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }

    static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    static LaneRenderer renderer(String mode) throws Exception {
        BMSPlayer player = (BMSPlayer) unsafe().allocateInstance(BMSPlayer.class);
        TimerManager timer = new TimerManager(); timer.setMicroTimer(TIMER_PLAY, 0);
        set(player, MainState.class, "timer", timer);
        LaneRenderer lane = (LaneRenderer) unsafe().allocateInstance(LaneRenderer.class);
        PlayConfig config = new PlayConfig(); config.setBmsirHispeedMode(mode);
        config.setFixhispeed(PlayConfig.FIX_HISPEED_OFF); config.setHispeed(2f);
        config.setBmsirLr2Hispeed(3f); config.setEnablelanecover(false);
        // Floating speed is derived from the saved green number; seed it to match 2.00x here.
        BMSIRFloatingHispeed.rememberGreen(config, 180, 2);
        BMSModel model = new BMSDecoder().decode("#PLAYER 1\n#TITLE T\n#BPM 120\n#WAV01 a.wav\n#00111:01\n".getBytes(StandardCharsets.UTF_8), false, null);
        set(lane, LaneRenderer.class, "main", player); set(lane, LaneRenderer.class, "model", model);
        set(lane, LaneRenderer.class, "config", new PlayerConfig()); set(lane, LaneRenderer.class, "playconfig", config);
        set(lane, LaneRenderer.class, "bmsirLr2HispeedFixEnabled", PlayConfig.HISPEED_MODE_LR2.equals(mode));
        set(lane, LaneRenderer.class, "nowbpm", 180d); set(lane, LaneRenderer.class, "currentscroll", 2d);
        set(lane, LaneRenderer.class, "mainbpm", 180d); set(lane, LaneRenderer.class, "minbpm", 90d); set(lane, LaneRenderer.class, "maxbpm", 240d);
        set(lane, LaneRenderer.class, "pos", 7); set(lane, LaneRenderer.class, "hispeedmargin", .25f);
        return lane;
    }

    @Test void allSixTransitionsKeepTheChartClockAndSeparateMultipliers() throws Exception {
        String[] modes = {PlayConfig.HISPEED_MODE_NORMAL, PlayConfig.HISPEED_MODE_LR2, PlayConfig.HISPEED_MODE_FLOATING};
        for (String from : modes) for (String to : modes) {
            if (from.equals(to)) continue;
            LaneRenderer lane = renderer(from);
            assertTrue(lane.setBmsirHispeedMode(to));
            assertEquals(to, lane.getPlayConfig().getBmsirHispeedMode());
            assertEquals(180, lane.getNowBPM());
            Field pos = LaneRenderer.class.getDeclaredField("pos"); pos.setAccessible(true); assertEquals(7, pos.get(lane));
            assertEquals(PlayConfig.HISPEED_MODE_LR2.equals(to) ? 3f : 2f, lane.getHispeed());
            assertEquals(2f, lane.getPlayConfig().getHispeed()); assertEquals(3f, lane.getPlayConfig().getEffectiveLr2Hispeed());
            assertTrue(lane.getCurrentDuration() > 0);
        }
    }

    @Test void editingOneModeKeepsOtherSpeedModesAndNoSpeedRejectsChanges() throws Exception {
        LaneRenderer lane = renderer(PlayConfig.HISPEED_MODE_LR2);
        lane.setHispeed(4f);
        assertEquals(4f, lane.getHispeed()); assertEquals(2f, lane.getPlayConfig().getHispeed());
        lane.setBmsirHispeedMode(PlayConfig.HISPEED_MODE_FLOATING); lane.changeHispeed(true);
        assertEquals(2.5f, lane.getHispeed()); assertEquals(4f, lane.getPlayConfig().getEffectiveLr2Hispeed());
        lane.setBmsirHispeedMode(PlayConfig.HISPEED_MODE_NORMAL); assertEquals(2f, lane.getHispeed());
        set(lane, LaneRenderer.class, "noSpeedConstraint", true);
        assertFalse(lane.setBmsirHispeedMode(PlayConfig.HISPEED_MODE_FLOATING));
        assertEquals(PlayConfig.HISPEED_MODE_NORMAL, lane.getPlayConfig().getBmsirHispeedMode());
    }

    @Test void replayEventsRestoreModesWithoutRecomputingRecordedFloatingSpeed() throws Exception {
        LaneRenderer lane = renderer(PlayConfig.HISPEED_MODE_NORMAL);
        PlayConfig recorded = lane.getPlayConfig().clone(); recorded.setBmsirHispeedMode(PlayConfig.HISPEED_MODE_FLOATING);
        recorded.setEnablelanecover(true); recorded.setBmsirFloatingHispeed(3.125f); recorded.setBmsirFloatingGreenFrames(29.98765f);
        ReplayLaneState state = ReplayLaneState.capture(2_000_000, recorded, false);
        Json json = new Json(); lane.applyReplayLaneState(json.fromJson(ReplayLaneState.class, json.toJson(state)));
        assertEquals(3.125f, lane.getHispeed()); assertEquals(29.98765f, lane.getPlayConfig().getBmsirFloatingGreenFrames());
        assertEquals(180, lane.getNowBPM());
        recorded.setBmsirHispeedMode(PlayConfig.HISPEED_MODE_LR2); recorded.setBmsirLr2Hispeed(4.5f);
        lane.applyReplayLaneState(ReplayLaneState.capture(3_000_000, recorded, true));
        assertEquals(4.5f, lane.getHispeed()); assertTrue(lane.isBmsirLr2HispeedFixEnabled());
    }

    @Test void legacyModeResolutionAndNewNormalAreUnambiguous() {
        PlayConfig old = new PlayConfig(); assertEquals(PlayConfig.HISPEED_MODE_NORMAL, old.resolveBmsirHispeedMode(false));
        assertEquals(PlayConfig.HISPEED_MODE_LR2, old.resolveBmsirHispeedMode(true));
        old.setBmsirFloatingHispeedEnabled(true); assertEquals(PlayConfig.HISPEED_MODE_FLOATING, old.resolveBmsirHispeedMode(true));
        old.setBmsirHispeedMode(PlayConfig.HISPEED_MODE_NORMAL);
        assertEquals(PlayConfig.HISPEED_MODE_NORMAL, old.resolveBmsirHispeedMode(true));
    }
}
