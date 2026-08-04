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
            "SIDEJUMP",
            "RANDOM LINK",
            "WARN DB ON DP"
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
            case 1 -> setExtraMode((draft.getExtraMode() + 1) % LEVEL_1_3.length);
            case 2 -> setAddNotes(nextPercent(draft.getAddNotes()));
            case 3 -> setAddLongNotes(nextPercent(draft.getAddLongNotes()));
            case 4 -> draft.setAddMines(nextPercent(draft.getAddMines()));
            case 5 -> draft.setHiddenSudden1P((draft.getHiddenSudden1P() + 1) % HIDDEN_SUDDEN.length);
            case 6 -> draft.setHiddenSudden2P((draft.getHiddenSudden2P() + 1) % HIDDEN_SUDDEN.length);
            case 7 -> draft.setAcceleration((draft.getAcceleration() + 1) % ACCELERATION.length);
            case 8 -> draft.setSoftLanding((draft.getSoftLanding() + 1) % LEVEL_1_2.length);
            case 9 -> draft.setEarthquake(nextPercent(draft.getEarthquake()));
            case 10 -> draft.setTornado(nextPercent(draft.getTornado()));
            case 11 -> draft.setSuperLoop(nextPercent(draft.getSuperLoop()));
            case 12 -> draft.setGambol((draft.getGambol() + 1) % LEVEL_1_2.length);
            case 13 -> draft.setCharacter(nextPercent(draft.getCharacter()));
            case 14 -> draft.setHeartbeat(nextPercent(draft.getHeartbeat()));
            case 15 -> draft.setLoudness(nextPercent(draft.getLoudness()));
            case 16 -> draft.setNabeatsu(nextPercent(draft.getNabeatsu()));
            case 17 -> draft.setSinCurve(nextPercent(draft.getSinCurve()));
            case 18 -> draft.setWave(nextPercent(draft.getWave()));
            case 19 -> draft.setSpiral(nextPercent(draft.getSpiral()));
            case 20 -> draft.setSideJump(nextPercent(draft.getSideJump()));
            case 21 -> setRandomLink((randomLinkIndex() + 1) % RANDOM_LINK.length);
            case 22 -> draft.setWarnDoubleBattleOnDp(!draft.isWarnDoubleBattleOnDp());
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
            float left = Math.max(24.0f, windowWidth * 0.12f);
            float contentWidth = Math.min(620.0f, Math.max(300.0f, windowWidth - left * 2.0f));
            float footerHeight = 46.0f;

            ImGui.setCursorPosX(left);
            ImGui.setCursorPosY(Math.max(24.0f, windowHeight * 0.08f));
            ImGui.textUnformatted("MANIAC OPTIONS");
            ImGui.setCursorPosX(left);
            ImGui.separator();

            float listHeight = Math.max(120.0f, windowHeight - ImGui.getCursorPosY() - footerHeight - 24.0f);
            ImGui.setCursorPosX(left);
            ImGui.beginChild("###maniac-options-list", contentWidth, listHeight, false);
            float valueColumn = Math.max(190.0f, contentWidth - 150.0f);
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
            case 1 -> LEVEL_1_3[draft.getExtraMode()];
            case 2 -> percentValue(draft.getAddNotes());
            case 3 -> percentValue(draft.getAddLongNotes());
            case 4 -> percentValue(draft.getAddMines());
            case 5 -> HIDDEN_SUDDEN[draft.getHiddenSudden1P()];
            case 6 -> HIDDEN_SUDDEN[draft.getHiddenSudden2P()];
            case 7 -> ACCELERATION[draft.getAcceleration()];
            case 8 -> LEVEL_1_2[draft.getSoftLanding()];
            case 9 -> percentValue(draft.getEarthquake());
            case 10 -> percentValue(draft.getTornado());
            case 11 -> percentValue(draft.getSuperLoop());
            case 12 -> LEVEL_1_2[draft.getGambol()];
            case 13 -> percentValue(draft.getCharacter());
            case 14 -> percentValue(draft.getHeartbeat());
            case 15 -> percentValue(draft.getLoudness());
            case 16 -> percentValue(draft.getNabeatsu());
            case 17 -> percentValue(draft.getSinCurve());
            case 18 -> percentValue(draft.getWave());
            case 19 -> percentValue(draft.getSpiral());
            case 20 -> percentValue(draft.getSideJump());
            case 21 -> RANDOM_LINK[randomLinkIndex()];
            case 22 -> draft.isWarnDoubleBattleOnDp() ? "ON" : "OFF";
            default -> "OFF";
        };
    }

    private static String percentValue(int value) {
        return value <= 0 ? "OFF" : value + "%";
    }
}
