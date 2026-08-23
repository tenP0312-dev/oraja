package bms.player.beatoraja.select;

import bms.player.beatoraja.input.KeyBoardInputProcesseor.ControlKeys;
import com.badlogic.gdx.Input.Keys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MusicSelectInputCompatibilityTest {
    @Test
    void topRowEightRemainsTheShowAllChartsCompatibilityKey() {
        ControlKeys key = MusicSelectInputProcessor.SHOW_ALL_CHARTS_COMPATIBILITY_KEY;

        assertEquals(ControlKeys.NUM8, key);
        assertEquals(Keys.NUM_8, key.keycode);
        assertNotEquals(Keys.NUMPAD_8, key.keycode);
    }
}
