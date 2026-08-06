package bms.player.beatoraja.input;

import com.badlogic.gdx.Input.Keys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BMSPlayerInputProcessorNumpadTest {
    @Test
    void physicalNumpadKeysAreSeparateFromTheTopNumberRow() {
        for (int number = 0; number < 10; number++) {
            KeyBoardInputProcesseor.ControlKeys key =
                    BMSPlayerInputProcessor.numpadControlKey(number);
            assertEquals(Keys.NUMPAD_0 + number, key.keycode);
        }
        assertNull(BMSPlayerInputProcessor.numpadControlKey(-1));
        assertNull(BMSPlayerInputProcessor.numpadControlKey(10));
    }
}
