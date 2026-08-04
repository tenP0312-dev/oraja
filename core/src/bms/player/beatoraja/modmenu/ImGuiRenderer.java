package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.arena.lobby.GraphMenu;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaOverlay;
import bms.player.beatoraja.Version;
import bms.player.beatoraja.controller.Lwjgl3ControllerManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.controllers.Controller;

import imgui.*;
import imgui.extension.implot.ImPlot;
import imgui.flag.*;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;

import imgui.type.ImBoolean;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;


public class ImGuiRenderer {

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    private static long windowHandle;

    public static int windowWidth;
    public static int windowHeight;

    private static ImGuiImplGlfw imGuiGlfw;
    private static ImGuiImplGl3 imGuiGl3;

    private static Lwjgl3ControllerManager manager;

    private static InputProcessor tmpProcessor;

    private static ImBoolean SHOW_MOD_MENU = new ImBoolean(false);
    private static ImBoolean SHOW_RANDOM_TRAINER = new ImBoolean(false);
    private static ImBoolean SHOW_FREQ_PLUS = new ImBoolean(false);
    private static ImBoolean SHOW_JUDGE_TRAINER = new ImBoolean(false);
    private static ImBoolean SHOW_SONG_MANAGER = new ImBoolean(false);
    private static ImBoolean SHOW_DOWNLOAD_MENU = new ImBoolean(false);
    private static ImBoolean SHOW_ARENA_MENU = new ImBoolean(false);
    private static ImBoolean SHOW_GRAPH_MENU = new ImBoolean(false);
    private static ImBoolean SHOW_SKIN_WIDGET_MANAGER = new ImBoolean(false);
    private static ImBoolean SHOW_PERFORMANCE_MONITOR = new ImBoolean(false);
    private static ImBoolean SHOW_SKIN_MENU = new ImBoolean(false);
    private static ImBoolean SHOW_MISC_SETTING = new ImBoolean(false);
    private static ImBoolean SHOW_MANIAC_OPTIONS = new ImBoolean(false);


    public static void init() {
        Lwjgl3Graphics lwjglGraphics = ((Lwjgl3Graphics) Gdx.graphics);

        imGuiGlfw = new ImGuiImplGlfw();
        imGuiGl3 = new ImGuiImplGl3();
        manager = new Lwjgl3ControllerManager();

        windowHandle = lwjglGraphics.getWindow().getWindowHandle();
        windowWidth = lwjglGraphics.getWidth();
        windowHeight = lwjglGraphics.getHeight();

        ImGui.createContext();
        ImPlot.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename("layout.ini");
        io.addConfigFlags(ImGuiConfigFlags.NoMouseCursorChange);
        io.getFonts().addFontDefault();

        final ImFontGlyphRangesBuilder rangesBuilder = new ImFontGlyphRangesBuilder(); // Glyphs ranges provide
        rangesBuilder.addRanges(io.getFonts().getGlyphRangesDefault());
        rangesBuilder.addRanges(io.getFonts().getGlyphRangesCyrillic());
        rangesBuilder.addRanges(io.getFonts().getGlyphRangesJapanese());
        rangesBuilder.addRanges(FontAwesomeIcons._IconRange);
        // TODO: After ImGUI 1.92, manual glyph setup is no longer required. We can delete this garbage line after
        // ImGui-java has upgraded to 1.92 or above
        // This line is provided for "reverse difficult table lookup" feature. Because some difficult tables' symbol
        // is not baked in above glyph ranges, this line manually adds them into the ranges. Otherwise, the symbol
        // would be rendered as a '?' in ImGUI window.
        rangesBuilder.addText(
                "☆★▽▼白黒◆◎縦≡田⇒●∽"
                        + "αβγδεζηθικλμνξοπρστυφχψω"
                        + "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ"
                        + "←↑→↓↔↕↖↗↘↙"
                        + "±×÷≠≤≥∞≈≒≡∴∵○●□■△▲▽▼◇◆◎"
        );

        // Font config for additional fonts
        // This is a natively allocated struct so don't forget to call destroy after atlas is built
        final ImFontConfig fontConfig = new ImFontConfig();
        fontConfig.setMergeMode(true);  // Enable merge mode to merge cyrillic, japanese and icons with default font

        final short[] glyphRanges = rangesBuilder.buildRanges();
        io.getFonts().addFontFromMemoryTTF(loadFromResources("skin/default/VL-Gothic-Regular.ttf"), 14, fontConfig, glyphRanges); // japanese glyphs
        io.getFonts().addFontFromMemoryTTF(loadFromClassPath("resources/fa-regular-400.ttf"), 14, fontConfig, glyphRanges);
        io.getFonts().addFontFromMemoryTTF(loadFromClassPath("resources/fa-solid-900.ttf"), 14, fontConfig, glyphRanges);
        io.getFonts().build();

        fontConfig.destroy();
        imGuiGlfw.init(windowHandle, true);
        imGuiGl3.init("#version 150");
    }

    public static void start() {
        if (tmpProcessor != null) {
           Gdx.input.setInputProcessor(tmpProcessor);
            tmpProcessor = null;
        }
        imGuiGl3.newFrame();
        imGuiGlfw.newFrame();
        ImGui.newFrame();
    }

    public static void render() {
        // Relative from top left corner, so 44% from the left, 2% from the top
        float relativeX = windowWidth * 0.44f;
        float relativeY = windowHeight * 0.02f;
        ImGui.setNextWindowPos(relativeX, relativeY, ImGuiCond.Once);

        if (SHOW_MOD_MENU.get()) {
            ImGui.begin("Arena oraja", ImGuiWindowFlags.AlwaysAutoResize);

            ImGui.checkbox(t("再生速度変更", "Show Rate Modifier Window"), SHOW_FREQ_PLUS);
            ImGui.checkbox(t("RANDOM配置指定", "Show Random Trainer Window"), SHOW_RANDOM_TRAINER);
            ImGui.checkbox(t("判定トレーナー", "Show Judge Trainer Window"), SHOW_JUDGE_TRAINER);
            if (ImGui.checkbox(t("スキン設定", "Show Skin Configuration Window"), SHOW_SKIN_MENU)) { SkinMenu.invalidate(); }
            ImGui.checkbox(t("スキンウィジェット管理", "Show Skin Widget Manager Window"), SHOW_SKIN_WIDGET_MANAGER);
            ImGui.checkbox(t("楽曲管理", "Show Song Manager Window"), SHOW_SONG_MANAGER);
            ImGui.checkbox(t("ダウンロード状況", "Show Download Tasks Window"), SHOW_DOWNLOAD_MENU);
            if (ImGui.checkbox(t("パフォーマンスモニター", "Show Performance Monitor Window"), SHOW_PERFORMANCE_MONITOR) &&
                SHOW_PERFORMANCE_MONITOR.get()) {
                PerformanceMonitor.reloadEventTree();
            }
            ImGui.checkbox(t("その他設定", "Show Misc Setting Window"), SHOW_MISC_SETTING);
            ImGui.checkbox(t("従来Arenaメニュー", "Show Legacy Arena Menu"), SHOW_ARENA_MENU);
            ImGui.checkbox(t("従来Arenaグラフ", "Show Legacy Arena Graph"), SHOW_GRAPH_MENU);
            ImGui.separator();
            ImBoolean showBmsirArenaOverlay = new ImBoolean(
                    !BMSIRArenaOverlay.isHidden()
            );
            if (ImGui.checkbox(
                    t("BMS-IR Arenaオーバーレイ", "Show BMS-IR Arena Overlay"),
                    showBmsirArenaOverlay
            )) {
                BMSIRArenaOverlay.setVisible(showBmsirArenaOverlay.get());
                if (!BMSIRArenaClient.saveArenaConfig()) {
                    ImGuiNotify.warning(t(
                            "Arena表示設定を保存できませんでした",
                            "Could not save the Arena display setting"
                    ));
                }
            }

            if (SHOW_FREQ_PLUS.get()) {
                FreqTrainerMenu.show(SHOW_FREQ_PLUS);
            }
            if (SHOW_RANDOM_TRAINER.get()) {
                RandomTrainerMenu.show(SHOW_RANDOM_TRAINER);
            }
            if (SHOW_JUDGE_TRAINER.get()) {
                JudgeTrainerMenu.show(SHOW_JUDGE_TRAINER);
            }
            if (SHOW_SONG_MANAGER.get()) {
                SongManagerMenu.show(SHOW_SONG_MANAGER);
            }
            // TODO: This menu should based on config. Should not be rendered if user doesn't flag the http download feature
            if (SHOW_DOWNLOAD_MENU.get()) {
                DownloadTaskMenu.show(SHOW_DOWNLOAD_MENU);
            }
            if (SHOW_SKIN_WIDGET_MANAGER.get()) {
                SkinWidgetManager.focus = true;
                SkinWidgetManager.show(SHOW_SKIN_WIDGET_MANAGER);
            } else {
                SkinWidgetManager.focus = false;
            }
            if (SHOW_PERFORMANCE_MONITOR.get()) {
                PerformanceMonitor.show(SHOW_PERFORMANCE_MONITOR);
            }
            if (SHOW_SKIN_MENU.get()) {
                SkinMenu.show(SHOW_SKIN_MENU);
            }
            if (SHOW_MISC_SETTING.get()) {
                MiscSettingMenu.show(SHOW_MISC_SETTING);
            }
            if (SHOW_ARENA_MENU.get()) {
                ArenaMenu.show(SHOW_ARENA_MENU);
            } else {
                ArenaMenu.isFocused = false;
            }
            if (SHOW_GRAPH_MENU.get()) {
                GraphMenu.show(SHOW_GRAPH_MENU);
            }


            if (ImGui.treeNode(t("Arena oraja デバッグ情報", "Arena oraja Debug Information"))) {
                float axis;

                ImGui.text("Commit hash: " + Version.getGitCommitHash());
                ImGui.text("GLFW version: " + GLFW.glfwGetVersionString());
                for (Controller con : manager.getControllers()) {
                    ImGui.text("Controller Name: " + con.getName());
                    ImGui.text("Axis: " + con.getAxis(0));
                }
                ImGui.treePop();
            }
            ImGui.end();
        }

        if (SHOW_MANIAC_OPTIONS.get()) {
            ManiacOptionsMenu.show(SHOW_MANIAC_OPTIONS);
        }
        BMSIRArenaOverlay.render();
        ImGuiNotify.renderNotifications();
    }


    public static void end() {
        BMSIRArenaOverlay.updateKeyboardInputCapture(
                ImGui.getIO().getWantCaptureKeyboard()
                        || ImGui.getIO().getWantTextInput()
        );
        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());

        if (ImGui.getIO().getWantCaptureKeyboard() || ImGui.getIO().getWantCaptureMouse()) {
            tmpProcessor = Gdx.input.getInputProcessor();
            Gdx.input.setInputProcessor(null);
        }
    }

    public static void dispose() {
        imGuiGl3.shutdown();
        imGuiGl3 = null;
        imGuiGlfw.shutdown();
        imGuiGlfw = null;
        ImGui.destroyContext();
        ImPlot.destroyContext();
    }

    public static Boolean getShowModMenu() {
        return SHOW_MOD_MENU.get();
    }

    public static void toggleMenu() {
        SHOW_MOD_MENU.set(!SHOW_MOD_MENU.get());
    }

    public static void showManiacOptions() {
        if (!SHOW_MANIAC_OPTIONS.get() && ManiacOptionsMenu.open()) {
            SHOW_MANIAC_OPTIONS.set(true);
        }
    }

    public static boolean isManiacOptionsOpen() {
        return SHOW_MANIAC_OPTIONS.get();
    }

    public static void closeManiacOptions() {
        if (SHOW_MANIAC_OPTIONS.get()) {
            ManiacOptionsMenu.close(SHOW_MANIAC_OPTIONS);
        }
    }

    public static void moveManiacOptionsSelection(int delta) {
        if (SHOW_MANIAC_OPTIONS.get()) {
            ManiacOptionsMenu.moveSelection(delta);
        }
    }

    public static void cycleManiacOption() {
        if (SHOW_MANIAC_OPTIONS.get()) {
            ManiacOptionsMenu.cycleSelection();
        }
    }

    public static void helpMarker(String desc) {
        ImGui.textDisabled("(?)");
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.pushTextWrapPos(ImGui.getFontSize() * 35.0f);
            ImGui.textUnformatted(desc);
            ImGui.popTextWrapPos();
            ImGui.endTooltip();
        }

    }

    private static byte[] loadFromClassPath(String name) {
        try (InputStream is = ImGuiRenderer.class.getClassLoader().getResourceAsStream(name)) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] loadFromResources(String name) {
        try {
            return Files.readAllBytes(Gdx.files.internal(name).file().toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
