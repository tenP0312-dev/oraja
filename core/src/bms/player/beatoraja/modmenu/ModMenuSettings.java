package bms.player.beatoraja.modmenu;

import java.util.LinkedHashMap;

/** Serializable values only; no ImGui/native objects in player JSON. */
public final class ModMenuSettings {
    public boolean filterEnabled = true;
    // libGDX Json needs a concrete no-arg type for hand-authored/legacy JSON.
    public LinkedHashMap<String, Boolean> itemEnabled = new LinkedHashMap<>();
    public boolean songManagerSortLastPlayed;

    public ModMenuSettings() {
        normalize();
    }

    public ModMenuSettings normalize() {
        if (itemEnabled == null) {
            itemEnabled = new LinkedHashMap<>();
        }
        for (ModMenuItem item : ModMenuItem.values()) {
            itemEnabled.putIfAbsent(item.id, true);
        }
        return this;
    }

    public boolean isSelected(ModMenuItem item) {
        Boolean selected = itemEnabled == null ? null : itemEnabled.get(item.id);
        return selected == null || selected;
    }

    public boolean isEnabled(ModMenuItem item) {
        return !filterEnabled || isSelected(item);
    }
}
