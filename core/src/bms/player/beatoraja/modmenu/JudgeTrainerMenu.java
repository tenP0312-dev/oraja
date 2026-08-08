package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import static bms.player.beatoraja.modmenu.ImGuiRenderer.windowHeight;
import static bms.player.beatoraja.modmenu.ImGuiRenderer.windowWidth;

public class JudgeTrainerMenu {
    private static ImBoolean OVERRIDE_CHART_JUDGE = new ImBoolean(false);
    private static ImInt OVERRIDE_JUDGE_RANK = new ImInt(0);

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    public static void show(ImBoolean showJudgeTrainer) {
        float relativeX = windowWidth * 0.455f;
        float relativeY = windowHeight * 0.04f;
        ImGui.setWindowPos(relativeX, relativeY, ImGuiCond.FirstUseEver);

        if (ImGui.begin(t("判定トレーナー", "Judge Trainer") + "###judge-trainer",
                showJudgeTrainer, ImGuiWindowFlags.AlwaysAutoResize)) {
            if (ImGui.checkbox(t("譜面の判定を上書きする", "Override chart's judge"), OVERRIDE_CHART_JUDGE)) {
                JudgeTrainer.setActive(OVERRIDE_CHART_JUDGE.get());
            }
            if (ImGui.combo(t("判定", "Judge"), OVERRIDE_JUDGE_RANK, JudgeTrainer.JUDGE_OPTIONS)) {
                JudgeTrainer.setJudgeRank(OVERRIDE_JUDGE_RANK.get());
            }
            ImGui.end();
        }
    }
}
