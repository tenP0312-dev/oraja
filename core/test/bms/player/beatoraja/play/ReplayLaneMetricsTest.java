package bms.player.beatoraja.play;

import bms.model.*;
import bms.player.beatoraja.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import static org.junit.jupiter.api.Assertions.*;
import static bms.player.beatoraja.skin.SkinProperty.TIMER_PLAY;

class ReplayLaneMetricsTest {
    private static void set(Object object, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    @Test void activeReplayKeepsCurrentBpmInsteadOfPreviewAnchor() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe)unsafeField.get(null);
        BMSPlayer player = (BMSPlayer)unsafe.allocateInstance(BMSPlayer.class);
        TimerManager timer = new TimerManager(); timer.setMicroTimer(TIMER_PLAY, 0);
        set(player, MainState.class, "timer", timer);
        LaneRenderer renderer = (LaneRenderer)unsafe.allocateInstance(LaneRenderer.class);
        BMSModel model = new BMSDecoder().decode(("#PLAYER 1\n#TITLE T\n#BPM 120\n#WAV01 a.wav\n#00111:01\n")
                .getBytes(Charset.forName("MS932")), false, null);
        set(renderer, LaneRenderer.class, "main", player);
        set(renderer, LaneRenderer.class, "model", model);
        set(renderer, LaneRenderer.class, "startHerePreview", StartHerePreviewData.build(model));
        set(renderer, LaneRenderer.class, "playconfig", new PlayConfig());
        set(renderer, LaneRenderer.class, "nowbpm", 240.0);
        set(renderer, LaneRenderer.class, "currentscroll", 1.0);
        PlayConfig recorded = new PlayConfig(); recorded.setHispeed(2); recorded.setEnablelanecover(false);
        renderer.applyReplayLaneState(ReplayLaneState.capture(0, recorded, false));
        assertEquals(240.0, renderer.getNowBPM()); assertEquals(500, renderer.getCurrentDuration());
        set(renderer, LaneRenderer.class, "nowbpm", 480.0);
        renderer.applyReplayLaneState(ReplayLaneState.capture(100, recorded, false));
        assertEquals(480.0, renderer.getNowBPM()); assertEquals(250, renderer.getCurrentDuration());
        recorded.setBmsirFloatingHispeedEnabled(true);
        recorded.setBmsirFloatingHispeed(2);
        recorded.setBmsirFloatingGreenFrames(30.12345f);
        set(renderer, LaneRenderer.class, "nowbpm", 150.0);
        renderer.applyReplayLaneState(ReplayLaneState.capture(100, recorded, false));
        assertEquals(150, renderer.getNowBPM()); assertEquals(971, renderer.getCurrentDuration());
        assertEquals(583, renderer.floatingGreenNumber(150, true));
        renderer.changeHispeed(true);
        assertEquals(2.5f, renderer.getHispeed()); assertEquals(150, renderer.getNowBPM());
        assertEquals(30.12345f * 2f / 2.5f, renderer.getPlayConfig().getBmsirFloatingGreenFrames(), 0.0001);
        assertEquals(777, renderer.getCurrentDuration());
        renderer.moveFloatingScratch(150);
        assertEquals(3.5f, renderer.getHispeed(), 0.00001);
        assertEquals(150, renderer.getNowBPM());
        assertEquals(33.28717f, renderer.getPlayConfig().getBmsirFloatingGreenFrames(), 0.0001);
    }
}
