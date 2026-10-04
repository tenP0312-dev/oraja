package bms.player.beatoraja.audio;

import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.AudioConfig.EqualizerMode;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class LiveOutputEqualizerTest {
    @Test void updatesExistingStreamAndKeepsEachPublishedConfigurationIndependent() {
        AudioConfig config = new AudioConfig();
        LiveOutputEqualizer eq = new LiveOutputEqualizer(config, 48000, 2);
        config.setEqualizerMode(EqualizerMode.SWITCH);
        config.setEqualizerPreamp(-12);
        eq.update(config);
        config.setEqualizerPreamp(0);
        float[] samples = {1, -1};
        eq.process(samples);
        assertEquals(Math.pow(10, -12.0 / 20), samples[0], 1e-7);
        assertEquals(-samples[0], samples[1]);
        eq.update(config);
        samples = new float[]{1, -1};
        eq.process(samples);
        assertArrayEquals(new float[]{1, -1}, samples);

        config.setEqualizerMode(EqualizerMode.LR2);
        config.setLr2EqualizerGains(new double[]{0, 0, 0, 12, 0, 0, 0});
        eq.update(config);
        float[] impulse = new float[400];
        impulse[0] = 0.1f;
        eq.process(impulse);
        assertNotEquals(0.1f, impulse[0]);
        for (int i = 1; i < impulse.length; i += 2) assertEquals(0, impulse[i]);

        config.setEqualizerMode(EqualizerMode.OFF);
        eq.update(config);
        float[] silence = new float[400];
        eq.process(silence);
        assertArrayEquals(new float[400], silence);
    }

    @Test void concurrentUpdatesNeverSplitStereoOrApplyPartialSettings() throws Exception {
        AudioConfig config = new AudioConfig();
        config.setEqualizerMode(EqualizerMode.SWITCH);
        LiveOutputEqualizer eq = new LiveOutputEqualizer(config, 48000, 2);
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 2000; i++) {
                config.setEqualizerPreamp(i % 2 == 0 ? -12 : 0);
                eq.update(config);
            }
        });
        writer.start();
        for (int i = 0; i < 2000; i++) {
            float[] samples = new float[512];
            Arrays.fill(samples, 1);
            eq.process(samples);
            assertTrue(samples[0] == 1 || Math.abs(samples[0] - Math.pow(10, -12.0 / 20)) < 1e-7);
            for (float sample : samples) assertEquals(samples[0], sample);
        }
        writer.join();
    }
}
