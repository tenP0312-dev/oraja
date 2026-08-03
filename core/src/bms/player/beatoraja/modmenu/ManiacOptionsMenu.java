package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.function.IntConsumer;

import static bms.player.beatoraja.modmenu.ImGuiRenderer.windowHeight;
import static bms.player.beatoraja.modmenu.ImGuiRenderer.windowWidth;

/** One-column LR2-compatible MANIAC OPTIONS editor opened by holding F2. */
public final class ManiacOptionsMenu {
    private static final String[] OFF_LEVEL_1_3 = {"OFF", "LEVEL 1", "LEVEL 2", "LEVEL 3"};
    private static final String[] OFF_LEVEL_1_2 = {"OFF", "LEVEL 1", "LEVEL 2"};
    private static final String[] HIDDEN_SUDDEN = {"OFF", "HIDDEN", "SUDDEN", "HIDDEN+SUDDEN"};
    private static final String[] ACCELERATION = {"OFF", "ACCELERATION", "DECELERATION", "RANDOM"};
    private static final String[] RANDOM_LINK = {"OFF", "SYNC", "SYMMETRY"};
    private static final String[] PERCENT = {
            "OFF", "10%", "20%", "30%", "40%", "50%",
            "60%", "70%", "80%", "90%", "100%"
    };

    private ManiacOptionsMenu() {
    }

    public static void show(ImBoolean visible) {
        PlayerConfig player = BMSIRArenaClient.playerConfig();
        if (player == null) {
            visible.set(false);
            return;
        }
        BMSIRManiacSettings settings = player.getBmsirManiacSettings();
        settings.validate();

        ImGui.setNextWindowPos(windowWidth * 0.5f, windowHeight * 0.06f, ImGuiCond.FirstUseEver, 0.5f, 0.0f);
        ImGui.setNextWindowSizeConstraints(360.0f, 300.0f, Math.max(420.0f, windowWidth * 0.48f), windowHeight * 0.88f);
        if (ImGui.begin("MANIAC OPTIONS", visible, ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            ImGui.textDisabled("F2: short press updates folders / hold opens this window");
            ImGui.separator();

            combo("HIDDEN / SUDDEN 1P", settings.getHiddenSudden1P(), HIDDEN_SUDDEN, settings::setHiddenSudden1P);
            combo("HIDDEN / SUDDEN 2P", settings.getHiddenSudden2P(), HIDDEN_SUDDEN, settings::setHiddenSudden2P);
            combo("EXTRA MODE", settings.getExtraMode(), OFF_LEVEL_1_3, value -> {
                settings.setExtraMode(value);
                if (value > 0) {
                    settings.setAddNotes(0);
                    settings.setAddLongNotes(0);
                    settings.setDoubleBattle(false);
                }
            });
            percent("ADD NOTES", settings.getAddNotes(), value -> {
                settings.setAddNotes(value);
                if (value > 0) {
                    settings.setExtraMode(0);
                    settings.setAddLongNotes(0);
                    settings.setDoubleBattle(false);
                }
            });
            percent("ADD LONGNOTES", settings.getAddLongNotes(), value -> {
                settings.setAddLongNotes(value);
                if (value > 0) {
                    settings.setExtraMode(0);
                    settings.setAddNotes(0);
                    settings.setDoubleBattle(false);
                }
            });
            percent("ADD MINES", settings.getAddMines(), settings::setAddMines);
            combo("ACCELERATION", settings.getAcceleration(), ACCELERATION, settings::setAcceleration);
            combo("SOFTLANDING", settings.getSoftLanding(), OFF_LEVEL_1_2, settings::setSoftLanding);
            percent("EARTHQUAKE", settings.getEarthquake(), settings::setEarthquake);
            percent("TORNADO", settings.getTornado(), settings::setTornado);
            percent("SUPERLOOP", settings.getSuperLoop(), settings::setSuperLoop);
            combo("GAMBOL", settings.getGambol(), OFF_LEVEL_1_2, settings::setGambol);
            percent("CHAR", settings.getCharacter(), settings::setCharacter);
            percent("HEARTBEAT", settings.getHeartbeat(), settings::setHeartbeat);
            percent("LOUDNESS", settings.getLoudness(), settings::setLoudness);
            percent("NABEATSU", settings.getNabeatsu(), settings::setNabeatsu);
            percent("SIN CURVE", settings.getSinCurve(), settings::setSinCurve);
            percent("WAVE", settings.getWave(), settings::setWave);
            percent("SPIRAL", settings.getSpiral(), settings::setSpiral);
            percent("SIDEJUMP", settings.getSideJump(), settings::setSideJump);

            ImGui.separator();
            ImBoolean doubleBattle = new ImBoolean(settings.isDoubleBattle());
            if (ImGui.checkbox("DOUBLE BATTLE", doubleBattle)) {
                settings.setDoubleBattle(doubleBattle.get());
                if (doubleBattle.get()) {
                    settings.setExtraMode(0);
                    settings.setAddNotes(0);
                    settings.setAddLongNotes(0);
                }
                save();
            }
            ImGui.textDisabled(
                    "EXTRA / ADD NOTES / ADD LONGNOTES / DB are mutually exclusive"
            );
            int link = switch (settings.getRandomLink()) {
                case BMSIRManiacSettings.RANDOM_LINK_SYNC -> 1;
                case BMSIRManiacSettings.RANDOM_LINK_SYMMETRY -> 2;
                default -> 0;
            };
            combo("RANDOM LINK", link, RANDOM_LINK, value -> settings.setRandomLink(switch (value) {
                case 1 -> BMSIRManiacSettings.RANDOM_LINK_SYNC;
                case 2 -> BMSIRManiacSettings.RANDOM_LINK_SYMMETRY;
                default -> BMSIRManiacSettings.RANDOM_LINK_OFF;
            }));
            ImGui.textWrapped("1P / 2P random options are changed in the normal DP OPTION panel.");
            ImBoolean warning = new ImBoolean(settings.isWarnDoubleBattleOnDp());
            if (ImGui.checkbox("Warn when DB is suspended on a DP chart", warning)) {
                settings.setWarnDoubleBattleOnDp(warning.get());
                save();
            }

            ImGui.separator();
            ImGui.text("Ranking: " + settings.rankingKey());
            String detail = settings.detailedOptionText();
            ImGui.textWrapped(detail.isEmpty() ? "All options are OFF" : detail);
        }
        ImGui.end();
    }

    private static void percent(String label, int value, IntConsumer setter) {
        combo(label, value <= 0 ? 0 : value / 10, PERCENT, selected -> setter.accept(selected * 10));
    }

    private static void combo(String label, int value, String[] choices, IntConsumer setter) {
        ImInt selected = new ImInt(Math.max(0, Math.min(value, choices.length - 1)));
        if (ImGui.combo(label, selected, choices)) {
            setter.accept(selected.get());
            save();
        }
    }

    private static void save() {
        if (!BMSIRArenaClient.saveArenaConfig()) {
            ImGuiNotify.warning("MANIAC OPTIONS could not be saved");
        }
    }
}
