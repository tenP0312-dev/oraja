package bms.player.beatoraja.arena.bmsir;

import com.badlogic.gdx.Input.Keys;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRArenaHotkeyTest {
    @Test
    void normalizesRightModifiersDuplicatesAndInvalidCodes() {
        assertArrayEquals(
                new int[]{Keys.CONTROL_LEFT, Keys.SHIFT_LEFT, Keys.K},
                BMSIRArenaHotkey.normalizeKeys(
                        new int[]{
                                Keys.CONTROL_RIGHT,
                                Keys.CONTROL_LEFT,
                                Keys.SHIFT_RIGHT,
                                Keys.K,
                                Keys.UNKNOWN,
                                Keys.MAX_KEYCODE + 1
                        }
                )
        );
    }

    @Test
    void exactMatcherAcceptsSingleAndArbitraryChordsButRejectsExtraKeys() {
        assertTrue(exact(new int[]{Keys.SPACE}, Keys.SPACE));
        assertTrue(exact(new int[]{Keys.Z, Keys.X}, Keys.Z, Keys.X));
        assertTrue(exact(
                new int[]{Keys.CONTROL_LEFT, Keys.K},
                Keys.CONTROL_RIGHT,
                Keys.K
        ));
        assertFalse(exact(new int[]{Keys.Z}, Keys.SHIFT_LEFT, Keys.Z));
        assertFalse(exact(new int[]{Keys.Z, Keys.X}, Keys.Z));
        assertFalse(exact(new int[0]));
    }

    @Test
    void legacyDefaultAndClearKeysRemainCompatible() {
        assertArrayEquals(
                BMSIRArenaHotkey.defaultKeys(),
                BMSIRArenaHotkey.fromLegacy(5, 3)
        );
        assertTrue(BMSIRArenaHotkey.isClearChord(new int[]{Keys.BACKSPACE}));
        assertTrue(BMSIRArenaHotkey.isClearChord(new int[]{Keys.FORWARD_DEL}));
        assertFalse(BMSIRArenaHotkey.isClearChord(
                new int[]{Keys.CONTROL_LEFT, Keys.BACKSPACE}
        ));
    }

    @Test
    void everyValidKeyboardCodeHasASafeLabel() {
        for (int key = Keys.UNKNOWN + 1; key <= Keys.MAX_KEYCODE; key++) {
            assertNotNull(BMSIRArenaHotkey.label(new int[]{key}));
        }
    }

    private static boolean exact(int[] configured, int... pressed) {
        Set<Integer> down = new HashSet<>();
        Arrays.stream(pressed).forEach(down::add);
        return BMSIRArenaHotkey.isExactPressed(configured, down::contains);
    }
}
