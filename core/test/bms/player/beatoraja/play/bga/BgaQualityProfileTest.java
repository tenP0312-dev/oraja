package bms.player.beatoraja.play.bga;

import bms.player.beatoraja.Config;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BgaQualityProfileTest {
    @Test void oldAndInvalidConfigsDefaultToBalancedAndRoundTrip() {
        Json json = new Json();
        for (String source : new String[] {"{}", "{bgaQualityMode:null}", "{bgaQualityMode:UNKNOWN}"}) {
            Config config = json.fromJson(Config.class, source);
            config.validate();
            var profile = BgaQualityProfile.from(config);
            assertEquals(60, profile.fps()); assertEquals(1920, profile.maxWidth());
            assertEquals(3, profile.queueLength()); assertEquals(750, profile.preloadWindowMs());
            assertEquals(2, profile.decoders());
            Config restored = json.fromJson(Config.class, Config.getConfigJson(config));
            assertEquals(config.getBgaQualityMode(), restored.getBgaQualityMode());
        }
    }
    @Test void offCannotStartVideoRegardlessOfOverrides() {
        Config config = new Config();
        config.setBgaQualityMode(BgaQualityProfile.Mode.OFF); config.setBgaMaxFps(60);
        assertEquals(0, BgaQualityProfile.from(config).decoders());
        config.setBgaQualityMode(BgaQualityProfile.Mode.QUALITY); config.setBga(Config.BGA_OFF);
        assertEquals(0, BgaQualityProfile.from(config).fps());
    }
    @Test void boundsAndDisplayCapsKeepAspectRatioWithoutUpscaling() {
        Config config = new Config();
        config.setBgaMaxFps(999); config.setBgaMaxActiveDecoders(99);
        config.setBgaPreloadWindowMs(9999); config.setBgaDecodedQueueLength(99);
        config.setBgaNativeMemoryBudgetMb(-1); config.validate();
        var profile = BgaQualityProfile.from(config);
        assertEquals(60, profile.fps()); assertEquals(3, profile.decoders());
        assertEquals(2000, profile.preloadWindowMs()); assertEquals(4, profile.queueLength());
        assertEquals(16L * 1024 * 1024, profile.memoryBytes());
        assertArrayEquals(new int[]{640,360}, profile.dimensions(3840,2160,640,480));
        assertArrayEquals(new int[]{320,240}, profile.dimensions(320,240,1920,1080));
    }
    @Test void performanceDefaultsAndExplicitZeroPreloadArePreserved() {
        Config config = new Config(); config.setBgaQualityMode(BgaQualityProfile.Mode.PERFORMANCE);
        var profile = BgaQualityProfile.from(config);
        assertEquals(30, profile.fps()); assertEquals(1280, profile.maxWidth());
        assertEquals(2, profile.queueLength()); assertEquals(500, profile.preloadWindowMs());
        config.setBgaPreloadResourceCount(0); config.setBgaPreloadWindowMs(0);
        assertEquals(0, BgaQualityProfile.from(config).preloadCount());
    }
}
