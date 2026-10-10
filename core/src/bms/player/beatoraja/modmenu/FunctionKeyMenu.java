package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.MainController;
import bms.player.beatoraja.MainState;
import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.arena.bmsir.BMSIRNumpadAction;
import bms.player.beatoraja.select.MusicSelectCommand;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.bar.DirectoryBar;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;

/** Named alternatives to keyboard shortcuts. Never synthesize physical key input. */
public final class FunctionKeyMenu {
    private static MainController main;
    private static final ImBoolean visible = new ImBoolean(false);

    private FunctionKeyMenu() {}

    public static void setMain(MainController controller) {
        if (main != controller) visible.set(false);
        main = controller;
    }

    private static String t(String ja, String en) {
        return BMSIRArenaI18n.text(ja, en);
    }

    public static void renderOpenButton() {
        if (main == null || !(main.getCurrentState() instanceof MusicSelector)
                || ImGuiRenderer.isManiacOptionsOpen()) return;
        if (ImGui.button(t("ファンクションキーメニューを開く", "Open function key menu"))) {
            visible.set(true);
        }
    }

    public static void render() {
        if (main == null || !(main.getCurrentState() instanceof MusicSelector)
                || ImGuiRenderer.isManiacOptionsOpen()) {
            visible.set(false);
            return;
        }
        if (!visible.get()) return;
        ImGui.setNextWindowSize(480, 540, ImGuiCond.FirstUseEver);
        if (ImGui.begin(t("ファンクションキー／テンキー", "Function keys / Numpad")
                + "###function-key-menu", visible)) {
            ImGui.textWrapped(t("キーを押さずに、機能名から操作できます。選曲画面専用です。",
                    "Choose a function without pressing a key. Available on Music Select."));
            if (ImGui.beginTabBar("##shortcut-tabs")) {
                if (ImGui.beginTabItem(t("ファンクションキー", "Function keys"))) {
                    ImGui.beginChild("##function-actions", 0, 0, false);
                    functionActions();
                    ImGui.endChild();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem(t("テンキーの機能", "Numpad functions"))) {
                    ImGui.beginChild("##numpad-actions", 0, 0, false);
                    ImGui.textWrapped(t("割り当ての有無にかかわらず、使える機能を表示します。",
                            "Functions are available regardless of your keypad assignments."));
                    for (BMSIRNumpadAction action : BMSIRNumpadAction.values()) {
                        if (action == BMSIRNumpadAction.NONE) continue;
                        ImGui.beginDisabled(!availableOnSelect(action));
                        actionButton(action.label(BMSIRArenaI18n.isEnglish()), action);
                        ImGui.endDisabled();
                    }
                    ImGui.textWrapped(t("判定タイミング操作はプレイ中専用のため、ここでは使えません。",
                            "Judge timing actions require gameplay and are unavailable here."));
                    ImGui.endChild();
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
        }
        ImGui.end();
    }

    static boolean availableOnSelect(BMSIRNumpadAction action) {
        return action != null && action != BMSIRNumpadAction.NONE
                && action != BMSIRNumpadAction.JUDGE_AUTO
                && action != BMSIRNumpadAction.JUDGE_PLUS
                && action != BMSIRNumpadAction.JUDGE_MINUS;
    }

    public static boolean isOpen() {
        return visible.get();
    }

    private static void functionActions() {
        actionButton(t("F1: FPS表示 ON/OFF", "F1: Toggle FPS"), BMSIRNumpadAction.FPS);
        actionButton(t("F2: 曲フォルダ更新", "F2: Refresh folders"), BMSIRNumpadAction.UPDATE_FOLDER);
        ImGui.beginDisabled(!ImGuiRenderer.show(ModMenuItem.MANIAC_OPTIONS));
        button(t("F2長押し: MANIAC OPTIONS", "Hold F2: MANIAC OPTIONS"),
                controller -> ImGuiRenderer.showManiacOptions());
        ImGui.endDisabled();
        actionButton(t("F3: 選択曲のフォルダを開く", "F3: Open chart folder"), BMSIRNumpadAction.OPEN_FOLDER);
        button(t("Ctrl+F3: MD5をコピー", "Ctrl+F3: Copy MD5"),
                controller -> ((MusicSelector) controller.getCurrentState()).execute(MusicSelectCommand.COPY_MD5_HASH));
        button(t("Ctrl+Shift+F3: SHA256をコピー", "Ctrl+Shift+F3: Copy SHA256"),
                controller -> ((MusicSelector) controller.getCurrentState()).execute(MusicSelectCommand.COPY_SHA256_HASH));
        actionButton(t("F4: フルスクリーン ON/OFF", "F4: Toggle fullscreen"), BMSIRNumpadAction.FULLSCREEN);
        actionButton(t("F5: Modメニュー ON/OFF", "F5: Toggle Mod menu"), BMSIRNumpadAction.MOD_MENU);
        actionButton(t("F6: スクリーンショット", "F6: Screenshot"), BMSIRNumpadAction.SCREENSHOT);
        if (ImGui.button(t("F7: スクリーンショットをXへ投稿", "F7: Post screenshot to X"), -1, 0)) {
            ImGui.openPopup("##confirm-shortcut-post");
        }
        if (ImGui.beginPopup("##confirm-shortcut-post")) {
            ImGui.textWrapped(t("設定済みのXアカウントへ画像を投稿します。", "Post an image to the configured X account."));
            if (button(t("投稿する", "Post"), MainController::shareScreenshot)) {
                ImGui.closeCurrentPopup();
            }
            if (ImGui.button(t("キャンセル", "Cancel"))) ImGui.closeCurrentPopup();
            ImGui.endPopup();
        }
        actionButton(t("F8: 曲のお気に入り", "F8: Toggle song favorite"), BMSIRNumpadAction.FAVORITE_SONG);
        actionButton(t("F9: 譜面のお気に入り", "F9: Toggle chart favorite"), BMSIRNumpadAction.FAVORITE_CHART);
        ImGui.beginDisabled(!(main.getCurrentState() instanceof MusicSelector selector
                && selector.getBarManager().getSelected() instanceof DirectoryBar));
        button(t("F10: フォルダのオートプレイ", "F10: Autoplay folder"), controller -> {
            MusicSelector selector = (MusicSelector) controller.getCurrentState();
            if (selector.getBarManager().getSelected() instanceof DirectoryBar) {
                selector.selectSong(BMSPlayerMode.AUTOPLAY);
            }
        });
        ImGui.endDisabled();
        actionButton(t("F11: IR表示", "F11: Open IR"), BMSIRNumpadAction.OPEN_IR);
        actionButton(t("F12: スキンコンフィグ", "F12: Skin configuration"), BMSIRNumpadAction.SKIN_CONFIG);
    }

    private static void actionButton(String label, BMSIRNumpadAction action) {
        button(label, controller -> {
            if (availableOnSelect(action)) controller.executeBmsirNumpadAction(action);
        });
    }

    static boolean canDispatch(MainState expected, MainState current) {
        return expected instanceof MusicSelector && current == expected;
    }

    private static boolean button(String label, java.util.function.Consumer<MainController> action) {
        if (!ImGui.button(label, -1, 0)) return false;
        MainController expectedController = main;
        MainState expectedState = main.getCurrentState();
        // Dispatch before the next frame, outside the active ImGui/skin render.
        MainController.pushOneShotBeforeRenderTask(controller -> {
            if (controller == expectedController && !ImGuiRenderer.isManiacOptionsOpen()
                    && canDispatch(expectedState, controller.getCurrentState())) {
                action.accept(controller);
            }
        });
        return true;
    }
}
