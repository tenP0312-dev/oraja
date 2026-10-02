package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

/** Always reachable, even when every filtered entry is disabled. */
final class ModMenuSettingsMenu {
    private ModMenuSettingsMenu() { }

    static void render() {
        var player = BMSIRArenaClient.playerConfig();
        if (player == null || !ImGui.treeNode(t("Mod Menuの有効/無効", "Mod Menu item settings"))) return;
        ModMenuSettings settings = player.getModMenuSettings();
        boolean changed = false;
        ImBoolean filter = new ImBoolean(settings.filterEnabled);
        if (ImGui.checkbox(t("項目フィルターを有効にする", "Enable item filter"), filter)) {
            settings.filterEnabled = filter.get();
            changed = true;
        }
        ImGui.textWrapped(t("OFFの項目は非表示になり、関連機能も停止します。フィルターOFFでは全項目を使えます。",
                "Disabled items are hidden and their features stop. Turning the filter off makes every item available."));
        ImGui.beginDisabled(!settings.filterEnabled);
        for (ModMenuItem item : ModMenuItem.values()) {
            ImBoolean selected = new ImBoolean(settings.isSelected(item));
            if (ImGui.checkbox(t(item.displayName, item.englishName) + "##modmenu_" + item.id, selected)) {
                settings.itemEnabled.put(item.id, selected.get());
                changed = true;
            }
        }
        ImGui.endDisabled();
        if (changed) {
            ImGuiRenderer.enforceModMenuStates();
            BMSIRArenaClient.refreshManiacScoreDisplay();
            if (!BMSIRArenaClient.savePlayerConfig()) {
                ImGuiNotify.warning(t("Mod Menu設定を保存できませんでした", "Could not save Mod Menu settings"));
            }
        }
        ImGui.treePop();
    }

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }
}
