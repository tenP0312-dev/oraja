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

    /** The former single "Misc Settings" window, split into play, sound, and display. */
    static final String LEGACY_MISC_SETTINGS_ID = "misc_settings";

    public ModMenuSettings normalize() {
        if (itemEnabled == null) {
            itemEnabled = new LinkedHashMap<>();
        }
        Boolean legacyMisc = itemEnabled.get(LEGACY_MISC_SETTINGS_ID);
        if (legacyMisc != null) {
            for (ModMenuItem item : new ModMenuItem[] {
                    ModMenuItem.PLAY_SETTINGS, ModMenuItem.SOUND_SETTINGS, ModMenuItem.DISPLAY_SETTINGS}) {
                itemEnabled.putIfAbsent(item.id, legacyMisc);
            }
        }
        // HI-SPEED was a Play Settings tab; keep a player's existing choice for it.
        Boolean play = itemEnabled.get(ModMenuItem.PLAY_SETTINGS.id);
        if (play != null) {
            itemEnabled.putIfAbsent(ModMenuItem.HISPEED_SETTINGS.id, play);
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
