package bms.player.beatoraja;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import bms.player.beatoraja.AudioConfig.EqualizerMode;
import static org.junit.jupiter.api.Assertions.*;

class AudioConfigEqualizerTest {
    @Test void legacyConfigDefaultsToOffAndFlat() {
        AudioConfig config = new Json().fromJson(AudioConfig.class, "{}");
        assertEquals(EqualizerMode.OFF, config.getEqualizerMode());
        assertArrayEquals(new double[4], config.getSwitchEqualizerGains());
        assertArrayEquals(new double[7], config.getLr2EqualizerGains());
        assertEquals(0, config.getEqualizerPreamp());
    }

    @Test void bothBanksAndPreampRoundTripInSystemConfig() {
        Config config = new Config();
        AudioConfig audio = new AudioConfig();
        config.setAudioConfig(audio);
        audio.setEqualizerMode(EqualizerMode.LR2);
        audio.setSwitchEqualizerGains(new double[] {1, -2, 3, -4});
        audio.setLr2EqualizerGains(new double[] {1, 2, 3, 4, 5, 6, 7});
        audio.setEqualizerPreamp(-6.5);
        Config restored = new Json().fromJson(Config.class, Config.getConfigJson(config));
        assertEquals(EqualizerMode.LR2, restored.getAudioConfig().getEqualizerMode());
        assertArrayEquals(audio.getSwitchEqualizerGains(), restored.getAudioConfig().getSwitchEqualizerGains());
        assertArrayEquals(audio.getLr2EqualizerGains(), restored.getAudioConfig().getLr2EqualizerGains());
        assertEquals(-6.5, restored.getAudioConfig().getEqualizerPreamp());
    }

    @Test void corruptSettingsAreRepairedAndBanksAreDefensiveCopies() {
        AudioConfig audio = new AudioConfig();
        double[] gains = {99, -99, Double.NaN, Double.POSITIVE_INFINITY, 5};
        audio.setSwitchEqualizerGains(gains);
        gains[0] = 0;
        assertArrayEquals(new double[] {12, -12, 0, 0}, audio.getSwitchEqualizerGains());
        audio.getSwitchEqualizerGains()[0] = 0;
        assertEquals(12, audio.getSwitchEqualizerGains()[0]);
        audio.setLr2EqualizerGains(new double[] {2});
        assertArrayEquals(new double[] {2, 0, 0, 0, 0, 0, 0}, audio.getLr2EqualizerGains());
        audio.setEqualizerPreamp(Double.NEGATIVE_INFINITY);
        audio.setEqualizerMode(null);
        audio.validate();
        assertEquals(0, audio.getEqualizerPreamp());
        assertEquals(EqualizerMode.OFF, audio.getEqualizerMode());
        audio.setLr2EqualizerGains(null);
        assertArrayEquals(new double[7], audio.getLr2EqualizerGains());
        audio.setEqualizerPreamp(-100);
        assertEquals(-24, audio.getEqualizerPreamp());
    }
}
