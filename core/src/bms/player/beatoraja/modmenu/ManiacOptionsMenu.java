package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;
import imgui.ImColor;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import static bms.player.beatoraja.modmenu.ImGuiRenderer.windowHeight;
import static bms.player.beatoraja.modmenu.ImGuiRenderer.windowWidth;

/** One-column LR2-compatible MANIAC OPTIONS editor. */
public final class ManiacOptionsMenu {
    private static final String[] LABELS = {
            "DOUBLE BATTLE",
            "AUTO SCRATCH",
            "RANDOM LINK",
            "WARN DB ON DP",
            "EXTRA MODE",
            "ADD NOTES",
            "ADD LONGNOTES",
            "ADD MINES",
            "HIDDEN / SUDDEN 1P",
            "HIDDEN / SUDDEN 2P",
            "ACCELERATION",
            "SOFTLANDING",
            "EARTHQUAKE",
            "TORNADO",
            "SUPERLOOP",
            "GAMBOL",
            "CHAR",
            "HEARTBEAT",
            "LOUDNESS",
            "NABEATSU",
            "SIN CURVE",
            "WAVE",
            "SPIRAL",
            "SIDEJUMP"
    };
    private static final String[] LEVEL_1_3 = {"OFF", "LEVEL 1", "LEVEL 2", "LEVEL 3"};
    private static final String[] LEVEL_1_2 = {"OFF", "LEVEL 1", "LEVEL 2"};
    private static final String[] HIDDEN_SUDDEN = {"OFF", "HIDDEN", "SUDDEN", "HIDDEN+SUDDEN"};
    private static final String[] ACCELERATION = {"OFF", "ACCELERATION", "DECELERATION", "RANDOM"};
    private static final String[] RANDOM_LINK = {"OFF", "SYNC", "SYMMETRY"};
    private static final String CONTROL_HELP =
            "1KEY: DOWN    2KEY: UP    6KEY: SELECT    7KEY: BACK TO MUSIC SELECT";

    private static BMSIRManiacSettings draft;
    private static String originalOptions;
    private static boolean originalWarning;
    private static int selectedIndex;
    private static boolean scrollToSelection;

    private ManiacOptionsMenu() {
    }

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    static boolean open() {
        PlayerConfig player = BMSIRArenaClient.playerConfig();
        if (player == null) {
            return false;
        }
        draft = new BMSIRManiacSettings(player.getBmsirManiacSettings());
        originalOptions = draft.canonicalOptions();
        originalWarning = draft.isWarnDoubleBattleOnDp();
        selectedIndex = Math.max(0, Math.min(selectedIndex, LABELS.length - 1));
        scrollToSelection = true;
        return true;
    }

    static void close(ImBoolean visible) {
        visible.set(false);
        PlayerConfig player = BMSIRArenaClient.playerConfig();
        BMSIRManiacSettings selected = draft;
        draft = null;
        if (player == null || selected == null) {
            originalOptions = null;
            return;
        }

        selected.validate();
        boolean scoreSettingsChanged = !selected.canonicalOptions().equals(originalOptions);
        boolean configChanged = scoreSettingsChanged
                || selected.isWarnDoubleBattleOnDp() != originalWarning;
        originalOptions = null;
        if (!configChanged) {
            return;
        }

        player.setBmsirManiacSettings(selected);
        if (!BMSIRArenaClient.saveArenaConfig()) {
            ImGuiNotify.warning(t(
                    "マニアックオプションを保存できませんでした",
                    "MANIAC OPTIONS could not be saved"
            ));
        }
        if (scoreSettingsChanged) {
            BMSIRArenaClient.refreshManiacScoreDisplay();
        }
    }

    static void moveSelection(int delta) {
        if (draft == null || delta == 0) {
            return;
        }
        selectedIndex = Math.floorMod(selectedIndex + delta, LABELS.length);
        scrollToSelection = true;
    }

    static void cycleSelection() {
        if (draft == null) {
            return;
        }
        switch (selectedIndex) {
            case 0 -> setDoubleBattle(!draft.isDoubleBattle());
            case 1 -> setAutoScratch(!draft.isAutoScratch());
            case 2 -> setRandomLink((randomLinkIndex() + 1) % RANDOM_LINK.length);
            case 3 -> draft.setWarnDoubleBattleOnDp(!draft.isWarnDoubleBattleOnDp());
            case 4 -> setExtraMode((draft.getExtraMode() + 1) % LEVEL_1_3.length);
            case 5 -> setAddNotes(nextPercent(draft.getAddNotes()));
            case 6 -> setAddLongNotes(nextPercent(draft.getAddLongNotes()));
            case 7 -> draft.setAddMines(nextPercent(draft.getAddMines()));
            case 8 -> draft.setHiddenSudden1P((draft.getHiddenSudden1P() + 1) % HIDDEN_SUDDEN.length);
            case 9 -> draft.setHiddenSudden2P((draft.getHiddenSudden2P() + 1) % HIDDEN_SUDDEN.length);
            case 10 -> draft.setAcceleration((draft.getAcceleration() + 1) % ACCELERATION.length);
            case 11 -> draft.setSoftLanding((draft.getSoftLanding() + 1) % LEVEL_1_2.length);
            case 12 -> draft.setEarthquake(nextPercent(draft.getEarthquake()));
            case 13 -> draft.setTornado(nextPercent(draft.getTornado()));
            case 14 -> draft.setSuperLoop(nextPercent(draft.getSuperLoop()));
            case 15 -> draft.setGambol((draft.getGambol() + 1) % LEVEL_1_2.length);
            case 16 -> draft.setCharacter(nextPercent(draft.getCharacter()));
            case 17 -> draft.setHeartbeat(nextPercent(draft.getHeartbeat()));
            case 18 -> draft.setLoudness(nextPercent(draft.getLoudness()));
            case 19 -> draft.setNabeatsu(nextPercent(draft.getNabeatsu()));
            case 20 -> draft.setSinCurve(nextPercent(draft.getSinCurve()));
            case 21 -> draft.setWave(nextPercent(draft.getWave()));
            case 22 -> draft.setSpiral(nextPercent(draft.getSpiral()));
            case 23 -> draft.setSideJump(nextPercent(draft.getSideJump()));
            default -> {
            }
        }
    }

    public static void show(ImBoolean visible) {
        if (draft == null && !open()) {
            visible.set(false);
            return;
        }

        ImGui.setNextWindowPos(0.0f, 0.0f, ImGuiCond.Always);
        ImGui.setNextWindowSize(windowWidth, windowHeight, ImGuiCond.Always);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, ImColor.rgb(0, 0, 0));
        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoInputs;
        if (ImGui.begin("MANIAC OPTIONS###maniac-options-screen", flags)) {
            ImGui.setWindowFontScale(2.0f);
            float left = Math.max(24.0f, windowWidth * 0.06f);
            float contentWidth = Math.max(300.0f, windowWidth - left * 2.0f);
            boolean showDescription = contentWidth >= 900.0f;
            float listWidth = showDescription
                    ? Math.min(900.0f, contentWidth * 0.58f)
                    : contentWidth;
            float descriptionGap = 48.0f;
            float footerHeight = 72.0f;

            ImGui.setCursorPosX(left);
            ImGui.setCursorPosY(Math.max(24.0f, windowHeight * 0.08f));
            ImGui.textUnformatted("MANIAC OPTIONS");
            ImGui.setCursorPosX(left);
            ImGui.separator();

            float listHeight = Math.max(120.0f, windowHeight - ImGui.getCursorPosY() - footerHeight - 24.0f);
            ImGui.setCursorPosX(left);
            ImGui.beginChild("###maniac-options-list", listWidth, listHeight, false);
            float valueColumn = Math.max(300.0f, listWidth - 260.0f);
            for (int index = 0; index < LABELS.length; index++) {
                boolean selected = index == selectedIndex;
                if (selected) {
                    ImGui.pushStyleColor(ImGuiCol.Text, ImColor.rgb(118, 219, 153));
                }
                ImGui.textUnformatted((selected ? "> " : "  ") + LABELS[index]);
                ImGui.sameLine(valueColumn);
                ImGui.textUnformatted("[" + value(index) + "]");
                if (selected && scrollToSelection) {
                    ImGui.setScrollHereY(0.5f);
                }
                if (selected) {
                    ImGui.popStyleColor();
                }
            }
            scrollToSelection = false;
            ImGui.endChild();

            if (showDescription) {
                ImGui.sameLine();
                ImGui.beginChild(
                        "###maniac-options-description",
                        contentWidth - listWidth - descriptionGap,
                        listHeight,
                        false
                );
                ImGui.textUnformatted(LABELS[selectedIndex]);
                ImGui.separator();
                ImGui.textWrapped(description(selectedIndex));
                ImGui.endChild();
            }

            ImGui.setCursorPosX(left);
            ImGui.setCursorPosY(windowHeight - footerHeight);
            ImGui.separator();
            ImGui.setCursorPosX(left);
            ImGui.textDisabled(CONTROL_HELP);
        }
        ImGui.end();
        ImGui.popStyleColor();
    }

    private static void setDoubleBattle(boolean enabled) {
        draft.setDoubleBattle(enabled);
        if (enabled) {
            draft.setExtraMode(0);
            draft.setAddNotes(0);
            draft.setAddLongNotes(0);
        }
    }

    private static void setAutoScratch(boolean enabled) {
        if (enabled && !draft.isDoubleBattle()) {
            setDoubleBattle(true);
        }
        draft.setAutoScratch(enabled);
    }

    private static void setExtraMode(int value) {
        draft.setExtraMode(value);
        if (value > 0) {
            draft.setAddNotes(0);
            draft.setAddLongNotes(0);
            draft.setDoubleBattle(false);
        }
    }

    private static void setAddNotes(int value) {
        draft.setAddNotes(value);
        if (value > 0) {
            draft.setExtraMode(0);
            draft.setAddLongNotes(0);
            draft.setDoubleBattle(false);
        }
    }

    private static void setAddLongNotes(int value) {
        draft.setAddLongNotes(value);
        if (value > 0) {
            draft.setExtraMode(0);
            draft.setAddNotes(0);
            draft.setDoubleBattle(false);
        }
    }

    private static int nextPercent(int value) {
        return value >= 100 ? 0 : Math.max(0, value) + 10;
    }

    private static int randomLinkIndex() {
        return switch (draft.getRandomLink()) {
            case BMSIRManiacSettings.RANDOM_LINK_SYNC -> 1;
            case BMSIRManiacSettings.RANDOM_LINK_SYMMETRY -> 2;
            default -> 0;
        };
    }

    private static void setRandomLink(int value) {
        draft.setRandomLink(switch (value) {
            case 1 -> BMSIRManiacSettings.RANDOM_LINK_SYNC;
            case 2 -> BMSIRManiacSettings.RANDOM_LINK_SYMMETRY;
            default -> BMSIRManiacSettings.RANDOM_LINK_OFF;
        });
    }

    private static String value(int index) {
        return switch (index) {
            case 0 -> draft.isDoubleBattle() ? "ON" : "OFF";
            case 1 -> draft.isAutoScratch() ? "ON" : "OFF";
            case 2 -> RANDOM_LINK[randomLinkIndex()];
            case 3 -> draft.isWarnDoubleBattleOnDp() ? "ON" : "OFF";
            case 4 -> LEVEL_1_3[draft.getExtraMode()];
            case 5 -> percentValue(draft.getAddNotes());
            case 6 -> percentValue(draft.getAddLongNotes());
            case 7 -> percentValue(draft.getAddMines());
            case 8 -> HIDDEN_SUDDEN[draft.getHiddenSudden1P()];
            case 9 -> HIDDEN_SUDDEN[draft.getHiddenSudden2P()];
            case 10 -> ACCELERATION[draft.getAcceleration()];
            case 11 -> LEVEL_1_2[draft.getSoftLanding()];
            case 12 -> percentValue(draft.getEarthquake());
            case 13 -> percentValue(draft.getTornado());
            case 14 -> percentValue(draft.getSuperLoop());
            case 15 -> LEVEL_1_2[draft.getGambol()];
            case 16 -> percentValue(draft.getCharacter());
            case 17 -> percentValue(draft.getHeartbeat());
            case 18 -> percentValue(draft.getLoudness());
            case 19 -> percentValue(draft.getNabeatsu());
            case 20 -> percentValue(draft.getSinCurve());
            case 21 -> percentValue(draft.getWave());
            case 22 -> percentValue(draft.getSpiral());
            case 23 -> percentValue(draft.getSideJump());
            default -> "OFF";
        };
    }

    private static String description(int index) {
        return switch (index) {
            case 0 -> t("SP譜面を1P・2Pの両側へ複製します。", "Duplicates an SP chart across both sides.");
            case 1 -> t("DOUBLE BATTLEの両側の皿を自動演奏します。", "Autoplays both scratch lanes in Double Battle.");
            case 2 -> t("OFFは左右独立、SYNCは同じ配置、SYMMETRYは2P側を左右反転します。", "OFF uses independent sides, SYNC uses the same placement, and SYMMETRY mirrors side 2.");
            case 3 -> t("DP譜面でDOUBLE BATTLEが適用されない時に警告します。", "Warns when Double Battle is suspended on a native DP chart.");
            case 4 -> t("LR2互換のEXTRA MODEで譜面を生成します。", "Generates an LR2-compatible EXTRA MODE chart.");
            case 5 -> t("通常ノーツを指定割合で追加します。", "Adds normal notes at the selected percentage.");
            case 6 -> t("ロングノーツを指定割合で追加します。", "Adds long notes at the selected percentage.");
            case 7 -> t("地雷ノーツを指定割合で追加します。", "Adds mines at the selected percentage.");
            case 8 -> t("1P側のHIDDEN / SUDDEN表示を変更します。", "Changes the HIDDEN / SUDDEN effect for side 1.");
            case 9 -> t("2P側のHIDDEN / SUDDEN表示を変更します。", "Changes the HIDDEN / SUDDEN effect for side 2.");
            case 10 -> t("ノーツの移動速度を加速・減速・ランダム化します。", "Accelerates, decelerates, or randomizes note speed.");
            case 11 -> t("スクロール変化を滑らかにします。", "Softens scroll-speed changes.");
            case 12 -> t("レーン全体を揺らします。", "Shakes the lane display.");
            case 13 -> t("ノーツ表示を旋回させます。", "Rotates the note display.");
            case 14 -> t("ノーツ表示を繰り返しループさせます。", "Loops the note display repeatedly.");
            case 15 -> t("判定窓をLR2 GAMBOL仕様で厳しくします。", "Tightens the judgment window using LR2 GAMBOL rules.");
            case 16 -> t("ノーツを文字表示に変化させます。", "Replaces notes with character-style rendering.");
            case 17 -> t("ノーツを鼓動するように表示します。", "Pulses notes like a heartbeat.");
            case 18 -> t("キー音の音量に応じて表示を変化させます。", "Changes rendering according to keysound loudness.");
            case 19 -> t("小節番号に応じて表示を変化させます。", "Changes rendering according to the measure number.");
            case 20 -> t("ノーツを正弦波状に揺らします。", "Moves notes along a sine curve.");
            case 21 -> t("ノーツを上下に波打たせます。", "Moves notes in a vertical wave.");
            case 22 -> t("ノーツを螺旋状に移動させます。", "Moves notes in a spiral.");
            case 23 -> t("ノーツを左右に跳ねさせます。", "Makes notes jump sideways.");
            default -> "";
        };
    }

    private static String percentValue(int value) {
        return value <= 0 ? "OFF" : value + "%";
    }
}
