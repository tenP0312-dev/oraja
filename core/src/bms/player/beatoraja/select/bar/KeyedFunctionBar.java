package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.select.MusicSelector;

import java.util.function.BiConsumer;

/**
 * A function row whose label shows a changing value (for example
 * "Notes: 8th"). The bar list keeps the cursor on the row with the same key
 * when it is rebuilt, instead of matching the label.
 */
public final class KeyedFunctionBar extends FunctionBar {
    private final String selectionKey;

    public KeyedFunctionBar(String selectionKey, BiConsumer<MusicSelector, FunctionBar> function,
            String title, int displayBarType) {
        super(function, title, displayBarType);
        this.selectionKey = selectionKey;
    }

    public String getSelectionKey() {
        return selectionKey;
    }

    public static boolean sameRow(Bar previous, Bar candidate) {
        if (previous instanceof KeyedFunctionBar keyed && candidate instanceof KeyedFunctionBar other) {
            return keyed.selectionKey.equals(other.selectionKey);
        }
        return candidate.getClass() == previous.getClass() && candidate.getTitle().equals(previous.getTitle());
    }
}
