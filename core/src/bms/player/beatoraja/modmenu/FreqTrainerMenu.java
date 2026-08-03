package bms.player.beatoraja.modmenu;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.Arrays;
import java.util.List;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;

import static bms.player.beatoraja.modmenu.ImGuiRenderer.*;

public class FreqTrainerMenu {

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    public static ImBoolean FREQ_TRAINER_ENABLED = new ImBoolean(false);

    private static int[] freq = new int[] {100};

    private static List<Integer> buttonVals = Arrays.asList(-10, -5, -1, 100, 1, 5, 10);

    public static void show(ImBoolean showFreqTrainer) {
        float relativeX = windowWidth * 0.47f;
        float relativeY = windowHeight * 0.06f;
        ImGui.setNextWindowPos(relativeX, relativeY, ImGuiCond.FirstUseEver);

        if(ImGui.begin(t("再生速度変更", "Rate Modifier") + "###rate-modifier",
                showFreqTrainer, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(t("譜面の再生速度を指定した割合で変更します。",
                    "Changes chart playback speed by the selected percentage."));

            buttonVals.forEach(value -> {
                if (value == 100) {
                    if(ImGui.button(t("リセット", "Reset"))) {
                        freq[0] = 100;
                    }
                } else {
                    if(ImGui.button((value > 0 ? "+" : "") + value + "%")) {
                        freq[0] = clamp(freq[0] + value);
                    }
                }
                ImGui.sameLine();
            });
            ImGui.newLine();
            ImGui.sliderInt("%",
                    freq,
                    50,
                    200);

            ImGui.text(t("操作", "Controls"));
            ImGui.indent();
            ImGui.checkbox(t("速度変更を有効にする", "Rate Enabled"), FREQ_TRAINER_ENABLED);
            ImGui.sameLine();
            helpMarker(t(
                    "有効時、速度を上げたスコアはローカルへ保存されますがIRには送信されず、リザルトのランプは常にNO PLAYになります。",
                    "When enabled positive rate scores save locally, but are not submitted to IR and the result lamp is always NO PLAY."
            ));

            freq[0] = clamp(freq[0]);
        }
        ImGui.end();
    }

    private static int clamp(int result) {
        return Math.max(50, Math.min(200, result));
    }

    public static boolean isFreqTrainerEnabled() {
        return FREQ_TRAINER_ENABLED.get();
    }

    public static int getFreq() {
        return freq[0];
    }

    public static boolean isFreqNegative() {
        return freq[0] < 100;
    }

    public static String getFreqString() {
        String rate = String.format("%.02f", (freq[0] / 100.0f));
        return "[" + rate + "x]";
    }


}
