package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.arena.bmsir.BMSIRKeyProfileStore.Profile;
import bms.player.beatoraja.arena.bmsir.BMSIRKeyProfileStore.Scope;
import bms.player.beatoraja.modmenu.ImGuiNotify;

import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Music-select overlay tab that manages the shared key / play-setting profiles. */
final class BMSIRKeyProfilePanel {
    private static final ImInt SELECTED = new ImInt(0);
    private static final ImInt SCOPE = new ImInt(0);
    private static final ImString NAME = new ImString(BMSIRKeyProfileStore.MAX_NAME_LENGTH + 8);

    private static List<Profile> profiles = new ArrayList<>();
    private static String loadedFrom;
    private static String loadError;
    private static boolean confirmApply;
    private static boolean confirmDelete;

    private BMSIRKeyProfilePanel() {
    }

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    static void render(PlayerConfig config) {
        String root = BMSIRArenaClient.playerRootPath();
        if (config == null || root == null || root.isBlank()) {
            ImGui.textDisabled(t("プレイヤー設定を読み込み中です", "Player settings are loading"));
            return;
        }
        if (!root.equals(loadedFrom)) {
            reload(root);
        }
        ImGui.textWrapped(t(
                "全プレイヤー共通で、キー割当(キーボード/コントローラー/MIDI)とハイスピ・レーンカバー等のプレイ設定を名前付きで保存できます。",
                "Save key assignment (keyboard/controller/MIDI) and play settings such as HI-SPEED and lane cover under a name, shared by every player."
        ));
        if (loadError != null) {
            ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, loadError);
            ImGui.textWrapped(t(
                    "ファイルは変更していません。修復するか退避してから再読込してください。",
                    "The file was left untouched. Repair or move it aside, then reload."
            ));
            if (ImGui.button(t("再読込", "Reload") + "##keyprofile-reload")) {
                reload(root);
            }
            return;
        }

        boolean locked = BMSIRArenaClient.isSelectionBlocked() || BMSIRArenaClient.isReserved();
        renderSelector();
        Profile selected = selectedProfile();

        ImGui.separator();
        ImGui.setNextItemWidth(220.0f);
        ImGui.inputText(t("名前", "Name") + "##keyprofile-name", NAME);

        if (ImGui.button(t("現在の設定を新規保存", "Save current as new") + "##keyprofile-new")) {
            saveNew(root, config, NAME.get());
        }
        ImGui.sameLine();
        ImGui.beginDisabled(selected == null);
        if (ImGui.button(t("現在の設定で上書き", "Overwrite") + "##keyprofile-overwrite")) {
            overwrite(root, config, selected);
        }
        ImGui.sameLine();
        if (ImGui.button(t("複製", "Duplicate") + "##keyprofile-duplicate")) {
            duplicate(root, selected, NAME.get());
        }
        ImGui.sameLine();
        if (ImGui.button(t("名前変更", "Rename") + "##keyprofile-rename")) {
            rename(root, selected, NAME.get());
        }
        ImGui.endDisabled();

        ImGui.separator();
        ImGui.text(t("適用範囲", "Apply scope"));
        ImGui.setNextItemWidth(320.0f);
        ImGui.combo("##keyprofile-scope", SCOPE, new String[]{
                t("全モード(キー割当+プレイ設定)", "All modes (keys + play settings)"),
                t("現在のモードのみ", "Current mode only"),
                t("キー割当のみ(全モード)", "Key assignment only (all modes)")
        });

        ImGui.beginDisabled(selected == null || locked);
        if (ImGui.button(t("このプロファイルを適用", "Apply profile") + "##keyprofile-apply")) {
            confirmApply = true;
            confirmDelete = false;
        }
        ImGui.endDisabled();
        if (locked) {
            ImGui.sameLine();
            ImGui.textDisabled(t("対戦予約中・結果待ちは適用できません", "Unavailable while queued or awaiting a result"));
        }
        ImGui.sameLine();
        ImGui.beginDisabled(selected == null);
        if (ImGui.button(t("削除", "Delete") + "##keyprofile-delete")) {
            confirmDelete = true;
            confirmApply = false;
        }
        ImGui.endDisabled();

        if (selected != null && confirmApply) {
            ImGui.textWrapped(t(
                    "現在の設定が「" + selected.getName() + "」で置き換わります。保存していない現在の設定は失われます。",
                    "Current settings will be replaced by \"" + selected.getName()
                            + "\". Unsaved current settings are lost."
            ));
            if (ImGui.button(t("適用する", "Apply") + "##keyprofile-apply-ok")) {
                confirmApply = false;
                applySelected(config, selected, locked);
            }
            ImGui.sameLine();
            if (ImGui.button(t("やめる", "Cancel") + "##keyprofile-apply-cancel")) {
                confirmApply = false;
            }
        }
        if (selected != null && confirmDelete) {
            ImGui.textWrapped(t(
                    "「" + selected.getName() + "」を削除します。元に戻せません。",
                    "Delete \"" + selected.getName() + "\". This cannot be undone."
            ));
            if (ImGui.button(t("削除する", "Delete") + "##keyprofile-delete-ok")) {
                confirmDelete = false;
                delete(root, selected);
            }
            ImGui.sameLine();
            if (ImGui.button(t("やめる", "Cancel") + "##keyprofile-delete-cancel")) {
                confirmDelete = false;
            }
        }
    }

    private static void renderSelector() {
        if (profiles.isEmpty()) {
            ImGui.textDisabled(t("保存済みのプロファイルはありません", "No saved profiles"));
            return;
        }
        String[] names = new String[profiles.size()];
        for (int index = 0; index < names.length; index++) {
            names[index] = profiles.get(index).getName();
        }
        if (SELECTED.get() >= names.length || SELECTED.get() < 0) {
            SELECTED.set(0);
        }
        ImGui.setNextItemWidth(320.0f);
        if (ImGui.combo(t("プロファイル", "Profile") + "##keyprofile-select", SELECTED, names)) {
            confirmApply = false;
            confirmDelete = false;
            NAME.set(names[SELECTED.get()]);
        }
    }

    private static Profile selectedProfile() {
        int index = SELECTED.get();
        return index >= 0 && index < profiles.size() ? profiles.get(index) : null;
    }

    private static void reload(String root) {
        try {
            profiles = BMSIRKeyProfileStore.load(root);
            loadError = null;
        } catch (IOException e) {
            profiles = new ArrayList<>();
            loadError = t("プロファイルを読み込めません: ", "Could not read profiles: ")
                    + e.getLocalizedMessage();
        }
        loadedFrom = root;
    }

    private static boolean validName(String raw, Profile except) {
        String name = BMSIRKeyProfileStore.normalizeName(raw);
        if (name.isEmpty()) {
            ImGuiNotify.warning(t("名前を入力してください", "Enter a name"));
            return false;
        }
        if (BMSIRKeyProfileStore.nameTaken(profiles, name, except)) {
            ImGuiNotify.warning(t("同じ名前のプロファイルがあります", "A profile with that name exists"));
            return false;
        }
        return true;
    }

    /** Persists the candidate list; the live list only changes when that succeeds. */
    private static boolean commit(String root, List<Profile> candidate) {
        if (!BMSIRKeyProfileStore.save(root, candidate)) {
            ImGuiNotify.warning(t("プロファイルを保存できませんでした", "Could not save profiles"));
            return false;
        }
        profiles = candidate;
        return true;
    }

    private static void saveNew(String root, PlayerConfig config, String name) {
        if (!validName(name, null)) {
            return;
        }
        List<Profile> candidate = new ArrayList<>(profiles);
        candidate.add(BMSIRKeyProfileStore.snapshot(name, config));
        if (commit(root, candidate)) {
            SELECTED.set(candidate.size() - 1);
            ImGuiNotify.info(t("プロファイルを保存しました", "Profile saved"));
        }
    }

    private static void overwrite(String root, PlayerConfig config, Profile selected) {
        List<Profile> candidate = new ArrayList<>(profiles);
        int index = candidate.indexOf(selected);
        candidate.set(index, BMSIRKeyProfileStore.snapshot(selected.getName(), config));
        if (commit(root, candidate)) {
            ImGuiNotify.info(t("プロファイルを上書きしました", "Profile overwritten"));
        }
    }

    private static void duplicate(String root, Profile selected, String name) {
        if (!validName(name, null)) {
            return;
        }
        List<Profile> candidate = new ArrayList<>(profiles);
        candidate.add(BMSIRKeyProfileStore.duplicate(selected, name));
        if (commit(root, candidate)) {
            SELECTED.set(candidate.size() - 1);
        }
    }

    private static void rename(String root, Profile selected, String name) {
        if (!validName(name, selected)) {
            return;
        }
        List<Profile> candidate = new ArrayList<>(profiles);
        int index = candidate.indexOf(selected);
        Profile renamed = BMSIRKeyProfileStore.duplicate(selected, name);
        candidate.set(index, renamed);
        commit(root, candidate);
    }

    private static void delete(String root, Profile selected) {
        List<Profile> candidate = new ArrayList<>(profiles);
        candidate.remove(selected);
        if (commit(root, candidate)) {
            SELECTED.set(Math.max(0, SELECTED.get() - 1));
        }
    }

    private static void applySelected(PlayerConfig config, Profile selected, boolean locked) {
        if (locked) {
            return;
        }
        Scope scope = switch (SCOPE.get()) {
            case 1 -> Scope.CURRENT_MODE;
            case 2 -> Scope.KEYS_ONLY;
            default -> Scope.ALL;
        };
        int applied = BMSIRKeyProfileStore.apply(
                selected,
                config,
                scope,
                BMSIRArenaClient.currentPlayModeForLayout()
        );
        if (applied == 0) {
            ImGuiNotify.warning(t("適用できる設定がありません", "Nothing to apply"));
            return;
        }
        BMSIRArenaClient.refreshInputAfterKeyProfile();
        if (!BMSIRArenaClient.savePlayerConfig()) {
            ImGuiNotify.warning(t(
                    "適用しましたが設定ファイルへ保存できませんでした",
                    "Applied, but the player settings file could not be saved"
            ));
            return;
        }
        ImGuiNotify.info(t("プロファイルを適用しました", "Profile applied"));
    }
}
