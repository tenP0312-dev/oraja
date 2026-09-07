package bms.player.beatoraja.select;

import bms.model.Mode;
import bms.player.beatoraja.PlayerConfig;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicSelectConstantTest {
    @Test
    void freshProfilesDisableBothAssistOptionsForEveryMode() {
        PlayerConfig player = new PlayerConfig();
        assertEquals(0, player.getScrollMode());
        for (Mode mode : Mode.values()) {
            assertFalse(player.getPlayConfig(mode).getPlayconfig().isEnableConstant(), mode.name());
        }
    }

    @Test
    void existingScrollModesAndPerModeConstantSettingsSurviveSavingIndependently() {
        for (int scroll = 0; scroll <= 2; scroll++) {
            for (boolean constant : new boolean[]{false, true}) {
                PlayerConfig player = new PlayerConfig();
                player.setScrollMode(scroll);
                player.getPlayConfig(Mode.BEAT_7K).getPlayconfig().setEnableConstant(constant);
                player.getPlayConfig(Mode.BEAT_14K).getPlayconfig().setEnableConstant(!constant);
                PlayerConfig restored = new Json().fromJson(PlayerConfig.class, PlayerConfig.getConfigJson(player));
                assertEquals(scroll, restored.getScrollMode());
                assertEquals(constant, restored.getPlayConfig(Mode.BEAT_7K).getPlayconfig().isEnableConstant());
                assertEquals(!constant, restored.getPlayConfig(Mode.BEAT_14K).getPlayconfig().isEnableConstant());
            }
        }
    }
}
