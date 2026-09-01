package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.arena.lobby.GraphMenu;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaOverlay;
import bms.player.beatoraja.Version;
import bms.player.beatoraja.controller.Lwjgl3ControllerManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
    private static volatile int windowScreenX;
    private static volatile int windowScreenY;

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
    private static final float MY_TABLE_BATCH_INDICATOR_DEFAULT_FONT_SCALE = 1.300f;
    private static final float MY_TABLE_BATCH_INDICATOR_DEFAULT_X_RATIO = 0.346f;
    private static final float MY_TABLE_BATCH_INDICATOR_DEFAULT_Y_RATIO = 0.094f;
    private static final float MY_TABLE_BATCH_INDICATOR_DEFAULT_WIDTH_RATIO = 0.249f;
    private static final float MY_TABLE_BATCH_INDICATOR_DEFAULT_HEIGHT_RATIO = 0.120f;
    private static final float[] MY_TABLE_BATCH_INDICATOR_FONT_SCALE = {
            MY_TABLE_BATCH_INDICATOR_DEFAULT_FONT_SCALE};
    private static float myTableBatchIndicatorX;
    private static float myTableBatchIndicatorY;
    private static float myTableBatchIndicatorWidth;
    private static float myTableBatchIndicatorHeight;
    private static boolean myTableBatchIndicatorSessionActive;
    private static boolean myTableBatchIndicatorLayoutEditingLastFrame;
    private static boolean myTableBatchIndicatorResetLayoutRequested;


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
        int[] x = new int[1];
        int[] y = new int[1];
        GLFW.glfwGetWindowPos(windowHandle, x, y);
        windowScreenX = x[0];
        windowScreenY = y[0];
        windowWidth = Gdx.graphics.getWidth();
        windowHeight = Gdx.graphics.getHeight();
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

		if (SHOW_SKIN_WIDGET_MANAGER.get()) {
			SkinWidgetManager.show(SHOW_SKIN_WIDGET_MANAGER);
		}
        if (SHOW_MANIAC_OPTIONS.get()) {
            ManiacOptionsMenu.show(SHOW_MANIAC_OPTIONS);
        }
        renderMyDifficultyTableBatchIndicator();
        BMSIRArenaOverlay.render();
        ImGuiNotify.renderNotifications();
    }

    /**
     * A non-interactive indicator keeps the controller batch-edit state visible
     * without requiring every music-select skin to add a dedicated text widget.
     */
    private static void saveMyDifficultyTableBatchIndicatorLayout() {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config == null || windowWidth <= 0 || windowHeight <= 0) {
            return;
        }
        config.setBmsirMyTableBatchOverlayFontScale(MY_TABLE_BATCH_INDICATOR_FONT_SCALE[0]);
        config.setBmsirMyTableBatchOverlayXRatio(myTableBatchIndicatorX / windowWidth);
        config.setBmsirMyTableBatchOverlayYRatio(myTableBatchIndicatorY / windowHeight);
        config.setBmsirMyTableBatchOverlayWidthRatio(myTableBatchIndicatorWidth / windowWidth);
        config.setBmsirMyTableBatchOverlayHeightRatio(myTableBatchIndicatorHeight / windowHeight);
        if (!BMSIRArenaClient.saveArenaConfig()) {
            ImGuiNotify.warning(t(
                    "マイ難易度表オーバーレイの表示設定を保存できませんでした",
                    "Could not save the My Difficulty Table overlay layout"
            ));
        }
    }

    private static void renderMyDifficultyTableBatchIndicator() {
        if (!BMSIRArenaClient.isMyDifficultyTableBatchEditing()) {
            if (myTableBatchIndicatorSessionActive
                    && myTableBatchIndicatorLayoutEditingLastFrame) {
                saveMyDifficultyTableBatchIndicatorLayout();
            }
            myTableBatchIndicatorSessionActive = false;
            myTableBatchIndicatorLayoutEditingLastFrame = false;
            myTableBatchIndicatorResetLayoutRequested = false;
            return;
        }
        boolean layoutEditing = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);

        if (!myTableBatchIndicatorSessionActive) {
            PlayerConfig config = BMSIRArenaClient.playerConfig();
            if (config != null) {
                MY_TABLE_BATCH_INDICATOR_FONT_SCALE[0] = config.getBmsirMyTableBatchOverlayFontScale();
                myTableBatchIndicatorX = windowWidth * config.getBmsirMyTableBatchOverlayXRatio();
                myTableBatchIndicatorY = windowHeight * config.getBmsirMyTableBatchOverlayYRatio();
                myTableBatchIndicatorWidth = windowWidth * config.getBmsirMyTableBatchOverlayWidthRatio();
                myTableBatchIndicatorHeight = windowHeight * config.getBmsirMyTableBatchOverlayHeightRatio();
            } else {
                MY_TABLE_BATCH_INDICATOR_FONT_SCALE[0] = MY_TABLE_BATCH_INDICATOR_DEFAULT_FONT_SCALE;
                resetMyDifficultyTableBatchIndicatorLayout();
            }
            myTableBatchIndicatorSessionActive = true;
        }
        // The upper center is left open by the default music-select skins, above
        // their difficulty-table caption and clear of the song list.
        ImGui.setNextWindowPos(
                windowWidth * 0.50f,
                windowHeight * 0.16f,
                ImGuiCond.FirstUseEver,
                0.50f,
                0.50f
        );
        if (!layoutEditing || myTableBatchIndicatorResetLayoutRequested) {
            ImGui.setNextWindowPos(
                    myTableBatchIndicatorX,
                    myTableBatchIndicatorY,
                    ImGuiCond.Always
            );
            ImGui.setNextWindowSize(
                    myTableBatchIndicatorWidth,
                    myTableBatchIndicatorHeight,
                    ImGuiCond.Always
            );
        }
        ImGui.setNextWindowBgAlpha(0.84f);
        int flags = ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoInputs
                | ImGuiWindowFlags.NoNav
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoBringToFrontOnFocus;
        flags &= ~ImGuiWindowFlags.AlwaysAutoResize;
        if (layoutEditing) {
            flags &= ~(ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoDecoration
                    | ImGuiWindowFlags.NoInputs | ImGuiWindowFlags.NoSavedSettings);
            flags |= ImGuiWindowFlags.NoCollapse;
        }
        String windowName = layoutEditing
                ? t("マイ難易度表オーバーレイ", "My Difficulty Table Overlay")
                        + "##my-difficulty-table-batch-indicator"
                : "##my-difficulty-table-batch-indicator";
        if (ImGui.begin(windowName, flags)) {
            BMSIRArenaClient.MyDifficultyTableBatchSummary summary =
                    BMSIRArenaClient.myDifficultyTableBatchSummary();
            boolean confirmationOpen = BMSIRArenaClient.isMyDifficultyTableBatchConfirmationOpen();
            ImGui.setWindowFontScale(MY_TABLE_BATCH_INDICATOR_FONT_SCALE[0]);
            ImGui.textUnformatted(
                    t("マイ難易度表：", "My Difficulty Table: ")
                            + BMSIRArenaClient.myDifficultyTableBatchDisplayName()
                            + t("を編集中", " (editing)")
            );
            if (confirmationOpen) {
                var levelSummaries = BMSIRArenaClient.myDifficultyTableBatchLevelSummaries();
                if (levelSummaries.isEmpty()) {
                    ImGui.textUnformatted(t("保留中の変更はありません", "There are no pending changes"));
                } else {
                    for (BMSIRArenaClient.MyDifficultyTableBatchLevelSummary level : levelSummaries) {
                        BMSIRArenaClient.MyDifficultyTableBatchSummary levelSummary = level.summary();
                        ImGui.textUnformatted(level.displayName()
                                + t("：追加 ", ": add ") + levelSummary.additions()
                                + t("・変更 ", " / change ") + levelSummary.changes()
                                + t("・削除 ", " / remove ") + levelSummary.deletions());
                    }
                }
            } else {
                ImGui.textUnformatted(t("保留：追加 ", "Pending: add ") + summary.additions()
                        + t("・変更 ", " / change ") + summary.changes()
                        + t("・削除 ", " / remove ") + summary.deletions());
            }
            BMSIRArenaClient.MyDifficultyTableEditorState editorState =
                    BMSIRArenaClient.myDifficultyTableEditorState(null);
            if (editorState.busy()) {
                ImGui.textDisabled(t("サーバーへ反映中です…", "Applying changes to the server…"));
            } else if (!editorState.errorMessage().isBlank()) {
                ImGui.textColored(
                        ImColor.rgb(255, 110, 110),
                        editorState.errorMessage()
                );
            }
            if (!confirmationOpen) {
                ImGui.textDisabled(t(
                        "ランプ：HARD=現在 / EASY=別レベル / EX-HARD=追加・変更予定",
                        "Lamp: HARD=current / EASY=other level / EX-HARD=pending add/change"
                ));
                ImGui.textDisabled(t(
                        "FAILED=削除予定 / NO PLAY=未登録",
                        "FAILED=pending removal / NO PLAY=not registered"
                ));
                ImGui.textDisabled(t(
                        "クリック / Enter / 1・3・5・7：保留切替",
                        "Click / Enter / 1 / 3 / 5 / 7: toggle pending edit"
                ));
                ImGui.textDisabled(t(
                        "START + SELECT 長押し：反映確認",
                        "Hold START + SELECT: review and apply"
                ));
            }
            if (layoutEditing) {
                ImGui.separator();
                ImGui.textDisabled(t(
                        "タイトルバーをドラッグ：移動 / 端をドラッグ：サイズ変更",
                        "Drag the title bar to move / drag an edge to resize"
                ));
                ImGui.sliderFloat(t("文字サイズ", "Text size"), MY_TABLE_BATCH_INDICATOR_FONT_SCALE, 0.8f, 3.0f);
                if (ImGui.button(t("デフォルトに戻す", "Restore defaults"))) {
                    MY_TABLE_BATCH_INDICATOR_FONT_SCALE[0] = MY_TABLE_BATCH_INDICATOR_DEFAULT_FONT_SCALE;
                    resetMyDifficultyTableBatchIndicatorLayout();
                    myTableBatchIndicatorResetLayoutRequested = true;
                } else if (myTableBatchIndicatorResetLayoutRequested) {
                    myTableBatchIndicatorResetLayoutRequested = false;
                } else {
                    myTableBatchIndicatorX = ImGui.getWindowPosX();
                    myTableBatchIndicatorY = ImGui.getWindowPosY();
                    myTableBatchIndicatorWidth = ImGui.getWindowSizeX();
                    myTableBatchIndicatorHeight = ImGui.getWindowSizeY();
                }
            }
        }
        ImGui.end();
        if (!layoutEditing && myTableBatchIndicatorLayoutEditingLastFrame) {
            saveMyDifficultyTableBatchIndicatorLayout();
        }
        myTableBatchIndicatorLayoutEditingLastFrame = layoutEditing;
    }

    private static void resetMyDifficultyTableBatchIndicatorLayout() {
        myTableBatchIndicatorX = windowWidth * MY_TABLE_BATCH_INDICATOR_DEFAULT_X_RATIO;
        myTableBatchIndicatorY = windowHeight * MY_TABLE_BATCH_INDICATOR_DEFAULT_Y_RATIO;
        myTableBatchIndicatorWidth = windowWidth * MY_TABLE_BATCH_INDICATOR_DEFAULT_WIDTH_RATIO;
        myTableBatchIndicatorHeight = windowHeight * MY_TABLE_BATCH_INDICATOR_DEFAULT_HEIGHT_RATIO;
    }


    public static void end() {
        ImGuiIO io = ImGui.getIO();
        ImGuiInputCapture.updateFromImGui(
                io.getWantCaptureKeyboard(),
                io.getWantTextInput(),
                io.getWantCaptureMouse(),
                ImGui.isAnyItemFocused(),
                ImGui.isAnyItemActive()
        );
        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());

        if (ImGuiInputCapture.isKeyboardCaptured() || ImGuiInputCapture.isMouseCaptured()) {
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

    public static int getWindowScreenX() {
        return windowScreenX;
    }

    public static int getWindowScreenY() {
        return windowScreenY;
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
