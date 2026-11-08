package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.MainController;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.select.MusicSelector;
import imgui.ImGui;
import imgui.type.ImInt;

/**
 * Difficulty filter selector for the F5 overlay.
 *
 * The filter key (NUMPAD 0 by default) cycles ALL → BEGINNER … INSANE, and an
 * accidental press made most songs look as if they had disappeared. The
 * overlay shows the current filter and returns it to ALL in one step.
 */
public final class DifficultyFilterMenu {
    private static MainController main;
    private static final ImInt SELECTED = new ImInt(0);

    private DifficultyFilterMenu() {
    }

    public static void setMain(MainController controller) {
        main = controller;
    }

    /** Display name of a filter value (0 = all, 1..5 = BEGINNER..INSANE). */
    public static String label(int filter) {
        return switch (filter) {
            case 1 -> "BEGINNER";
            case 2 -> "NORMAL";
            case 3 -> "HYPER";
            case 4 -> "ANOTHER";
            case 5 -> "INSANE";
            default -> BMSIRArenaI18n.text("すべて", "ALL");
        };
    }

    private static String[] labels() {
        String[] values = new String[6];
        for (int i = 0; i < values.length; i++) values[i] = label(i);
        return values;
    }

    /** Renders the selector while the music select screen is active. */
    public static void render() {
        MusicSelector selector = main != null && main.getCurrentState() instanceof MusicSelector current
                ? current : null;
        if (selector == null || selector.getBarManager() == null) return;
        int current = selector.getBarManager().getDifficultyFilter();
        SELECTED.set(current);
        if (ImGui.combo(BMSIRArenaI18n.text("難易度フィルター", "Difficulty Filter"), SELECTED, labels())
                && SELECTED.get() != current) {
            selector.getBarManager().setDifficultyFilter(SELECTED.get());
        }
    }
}
