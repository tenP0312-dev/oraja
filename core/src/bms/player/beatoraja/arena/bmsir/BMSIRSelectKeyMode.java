package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Key-mode filters that may be cycled by a short SELECT press. */
public enum BMSIRSelectKeyMode {
    ALL("all", null, "ALL"),
    BEAT_7K("7k", Mode.BEAT_7K, "7K"),
    BEAT_14K("14k", Mode.BEAT_14K, "14K"),
    POPN_9K("9k", Mode.POPN_9K, "9K"),
    BEAT_5K("5k", Mode.BEAT_5K, "5K"),
    BEAT_10K("10k", Mode.BEAT_10K, "10K"),
    KEYBOARD_24K("24k", Mode.KEYBOARD_24K, "24K"),
    KEYBOARD_24K_DOUBLE("24k_dp", Mode.KEYBOARD_24K_DOUBLE, "24K DP");

    private final String id;
    private final Mode mode;
    private final String label;

    BMSIRSelectKeyMode(String id, Mode mode, String label) {
        this.id = id;
        this.mode = mode;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public Mode mode() {
        return mode;
    }

    public String label() {
        return label;
    }

    public static BMSIRSelectKeyMode fromId(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (BMSIRSelectKeyMode candidate : values()) {
                if (candidate.id.equals(normalized)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Preserve the former built-in mode-cycle order by default. */
    public static String[] defaultIds() {
        return Arrays.stream(values()).map(BMSIRSelectKeyMode::id).toArray(String[]::new);
    }

    public static String[] normalizeIds(String[] values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                BMSIRSelectKeyMode mode = fromId(value);
                if (mode != null) {
                    normalized.add(mode.id);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(BEAT_7K.id);
        }
        return normalized.toArray(String[]::new);
    }

    public static List<BMSIRSelectKeyMode> selected(String[] values) {
        List<BMSIRSelectKeyMode> selected = new ArrayList<>();
        for (String id : normalizeIds(values)) {
            selected.add(fromId(id));
        }
        return List.copyOf(selected);
    }

    public static BMSIRSelectKeyMode nextConfigured(
            String[] values,
            Mode current,
            int direction
    ) {
        List<BMSIRSelectKeyMode> configured = selected(values);
        int currentIndex = -1;
        for (int index = 0; index < configured.size(); index++) {
            if (configured.get(index).mode == current) {
                currentIndex = index;
                break;
            }
        }
        int step = direction >= 0 ? 1 : -1;
        int base = currentIndex >= 0 ? currentIndex : (step > 0 ? -1 : 0);
        return configured.get(Math.floorMod(base + step, configured.size()));
    }
}
