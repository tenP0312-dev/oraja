package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.camera.FFmpegCameraProcess;
import bms.player.beatoraja.camera.FFmpegCameraProcess.Device;
import bms.player.beatoraja.camera.HandCameraDeviceResolver;
import bms.player.beatoraja.camera.HandCameraPresets;
import bms.player.beatoraja.camera.HandCameraSettings;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import java.util.List;

import static bms.player.beatoraja.modmenu.ImGuiRenderer.windowHeight;
import static bms.player.beatoraja.modmenu.ImGuiRenderer.windowWidth;

/**
 * F5 menu editor for every hand camera setting. Edits the same Config as the launcher's Video page; the
 * capture follows on the next frame and the config is saved with the normal periodic and exit saves.
 */
public final class HandCameraMenu {
    private static MainController main;
    /** Enumerated on a background thread only when the operator presses Refresh, as in the launcher. */
    private static volatile List<Device> devices = List.of();
    private static volatile boolean refreshing, refreshFailed, refreshed;

    private HandCameraMenu() { }

    public static void setMain(MainController main) { HandCameraMenu.main = main; }

    private static String t(String japanese, String english) { return BMSIRArenaI18n.text(japanese, english); }

    public static void show(ImBoolean open) {
        ImGui.setNextWindowPos(windowWidth * 0.5f, windowHeight * 0.16f, ImGuiCond.FirstUseEver);
        if (ImGui.begin(t("手元カメラ", "Hand Camera") + "###hand-camera", open, ImGuiWindowFlags.AlwaysAutoResize)) {
            if (main != null) render(main.getConfig());
        }
        ImGui.end();
    }

    private static void render(Config config) {
        ImBoolean enabled = new ImBoolean(config.isHandCameraEnabled());
        if (ImGui.checkbox(t("手元カメラを有効にする", "Enable hand camera"), enabled)) {
            config.setHandCameraEnabled(enabled.get());
        }
        ImBoolean bgaFrame = new ImBoolean(config.isHandCameraBgaFrame());
        if (ImGui.checkbox(t("BGA枠に表示（カメラ部品のないスキン向け）",
                "Show in the BGA frame (for skins without a camera part)"), bgaFrame)) {
            config.setHandCameraBgaFrame(bgaFrame.get());
        }
        renderDevice(config);

        var sizes = HandCameraPresets.sizes(config.getHandCameraWidth(), config.getHandCameraHeight());
        var currentSize = new HandCameraPresets.Size(config.getHandCameraWidth(), config.getHandCameraHeight());
        if (ImGui.beginCombo(t("サイズ（幅x高さ）", "Size (width x height)") + "##hand-camera-size",
                currentSize.toString())) {
            for (var size : sizes) {
                if (ImGui.selectable(size + "##hand-camera-size-" + size, size.equals(currentSize))) {
                    config.setHandCameraWidth(size.width());
                    config.setHandCameraHeight(size.height());
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.beginCombo("FPS##hand-camera-fps", Integer.toString(config.getHandCameraFps()))) {
            for (int fps : HandCameraPresets.fps(config.getHandCameraFps())) {
                if (ImGui.selectable(fps + "##hand-camera-fps-" + fps, fps == config.getHandCameraFps())) {
                    config.setHandCameraFps(fps);
                }
            }
            ImGui.endCombo();
        }
        ImBoolean mirror = new ImBoolean(config.isHandCameraMirror());
        if (ImGui.checkbox(t("左右を反転する", "Mirror horizontally"), mirror)) {
            config.setHandCameraMirror(mirror.get());
        }

        ImGui.separator();
        var manager = main.getHandCameraManager();
        HandCameraSettings settings = HandCameraSettings.from(config);
        ImGui.textWrapped(!settings.usable()
                ? t("状態: 未使用（有効にしてカメラを選んでください）", "Status: off (enable it and select a camera)")
                : manager != null && manager.isReceiving()
                        ? t("状態: 映像を受信中", "Status: receiving video")
                        : t("状態: 待機中（カメラを表示する画面で、挿すと自動でつながります）",
                                "Status: waiting (connects automatically on a screen that shows the camera)"));
        ImGui.textDisabled(t("サイズとFPSはカメラが対応する値を選んでください。ランチャーの「画面」でも変更できます。",
                "Choose a size and FPS the camera supports. Also available on the launcher's Video page."));
    }

    private static void renderDevice(Config config) {
        Device saved = HandCameraDeviceResolver.saved(HandCameraSettings.from(config));
        List<Device> list = devices;
        Device current = HandCameraDeviceResolver.refreshed(saved, list);
        String preview = current.displayName().isEmpty() ? t("未選択", "Not selected") : current.displayName();
        if (ImGui.beginCombo(t("使用するカメラ", "Camera") + "##hand-camera-device", preview)) {
            if (list.isEmpty()) {
                ImGui.textDisabled(t("「カメラ一覧を更新」を押してください", "Press Refresh cameras"));
            }
            for (int i = 0; i < list.size(); i++) {
                Device device = list.get(i);
                // Identically named cameras need distinct ImGui IDs.
                if (ImGui.selectable(device.displayName() + "##hand-camera-device-" + i, device.equals(current))) {
                    HandCameraDeviceResolver.remember(config, device);
                }
            }
            ImGui.endCombo();
        }
        ImGui.beginDisabled(refreshing);
        if (ImGui.button(t("カメラ一覧を更新", "Refresh cameras") + "##hand-camera-refresh")) refresh();
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.textDisabled(refreshing ? t("確認しています…", "Finding cameras...")
                : refreshFailed ? t("一覧を取得できませんでした。OSのカメラ許可を確認してください。",
                        "Could not list cameras. Check OS camera permission.")
                : refreshed && list.isEmpty() ? t("カメラが見つかりません", "No cameras found") : "");
    }

    private static void refresh() {
        refreshing = true;
        Thread thread = new Thread(() -> {
            try {
                devices = FFmpegCameraProcess.devices();
                refreshFailed = false;
            } catch (Exception | LinkageError error) {
                refreshFailed = true;
            } finally {
                refreshed = true;
                refreshing = false;
            }
        }, "hand-camera-menu-device-list");
        thread.setDaemon(true);
        thread.start();
    }
}
