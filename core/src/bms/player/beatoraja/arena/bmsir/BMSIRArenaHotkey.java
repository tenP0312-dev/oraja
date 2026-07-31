package bms.player.beatoraja.arena.bmsir;

import com.badlogic.gdx.Input.Keys;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.IntPredicate;

/** Keyboard-chord normalization and matching for the Arena overlay shortcut. */
public final class BMSIRArenaHotkey {
    private static final int MAX_CHORD_KEYS = Keys.MAX_KEYCODE;
    private static final int[] DEFAULT_KEYS = {
            Keys.CONTROL_LEFT,
            Keys.SHIFT_LEFT,
            Keys.F5
    };

    private BMSIRArenaHotkey() {
    }

    public static int[] defaultKeys() {
        return DEFAULT_KEYS.clone();
    }

    /** Converts the 0.4.1 F-key/modifier representation into a key chord. */
    public static int[] fromLegacy(int functionNumber, int modifiers) {
        List<Integer> keys = new ArrayList<>();
        if ((modifiers & 2) != 0) {
            keys.add(Keys.CONTROL_LEFT);
        }
        if ((modifiers & 1) != 0) {
            keys.add(Keys.SHIFT_LEFT);
        }
        if ((modifiers & 4) != 0) {
            keys.add(Keys.ALT_LEFT);
        }
        keys.add(Keys.F1 + Math.max(0, Math.min(11, functionNumber - 1)));
        return normalizeKeys(keys.stream().mapToInt(Integer::intValue).toArray());
    }

    public static int normalizeKey(int keycode) {
        return switch (keycode) {
            case Keys.CONTROL_RIGHT -> Keys.CONTROL_LEFT;
            case Keys.SHIFT_RIGHT -> Keys.SHIFT_LEFT;
            case Keys.ALT_RIGHT -> Keys.ALT_LEFT;
            default -> keycode;
        };
    }

    public static int[] normalizeKeys(int[] keycodes) {
        if (keycodes == null) {
            return null;
        }
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (int keycode : keycodes) {
            int logical = normalizeKey(keycode);
            if (logical <= Keys.UNKNOWN || logical > Keys.MAX_KEYCODE) {
                continue;
            }
            normalized.add(logical);
            if (normalized.size() >= MAX_CHORD_KEYS) {
                break;
            }
        }
        return normalized.stream().mapToInt(Integer::intValue).toArray();
    }

    public static boolean isExactPressed(
            int[] configuredKeys,
            IntPredicate rawKeyPressed
    ) {
        int[] keys = normalizeKeys(configuredKeys);
        return isExactNormalizedPressed(keys, rawKeyPressed);
    }

    /** Allocation-free matcher for chords already normalized by PlayerConfig. */
    public static boolean isExactNormalizedPressed(
            int[] keys,
            IntPredicate rawKeyPressed
    ) {
        if (keys == null || keys.length == 0) {
            return false;
        }
        for (int key : keys) {
            if (!logicalKeyPressed(key, rawKeyPressed)) {
                return false;
            }
        }
        for (int raw = Keys.UNKNOWN + 1; raw <= Keys.MAX_KEYCODE; raw++) {
            if (
                    rawKeyPressed.test(raw)
                            && !contains(keys, normalizeKey(raw))
            ) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(int[] keys, int target) {
        for (int key : keys) {
            if (key == target) {
                return true;
            }
        }
        return false;
    }

    private static boolean logicalKeyPressed(
            int logicalKey,
            IntPredicate rawKeyPressed
    ) {
        if (logicalKey == Keys.CONTROL_LEFT) {
            return rawKeyPressed.test(Keys.CONTROL_LEFT)
                    || rawKeyPressed.test(Keys.CONTROL_RIGHT);
        }
        if (logicalKey == Keys.SHIFT_LEFT) {
            return rawKeyPressed.test(Keys.SHIFT_LEFT)
                    || rawKeyPressed.test(Keys.SHIFT_RIGHT);
        }
        if (logicalKey == Keys.ALT_LEFT) {
            return rawKeyPressed.test(Keys.ALT_LEFT)
                    || rawKeyPressed.test(Keys.ALT_RIGHT);
        }
        return rawKeyPressed.test(logicalKey);
    }

    public static String label(int[] keycodes) {
        int[] keys = normalizeKeys(keycodes);
        if (keys == null || keys.length == 0) {
            return "未設定";
        }
        List<String> parts = new ArrayList<>();
        for (int key : keys) {
            String label = switch (key) {
                case Keys.CONTROL_LEFT -> "Ctrl";
                case Keys.SHIFT_LEFT -> "Shift";
                case Keys.ALT_LEFT -> "Alt";
                default -> Keys.toString(key);
            };
            parts.add(label == null || label.isBlank() ? "Key " + key : label);
        }
        return String.join("+", parts);
    }
}
