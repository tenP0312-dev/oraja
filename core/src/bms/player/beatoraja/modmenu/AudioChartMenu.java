package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.generated.AudioChartSession;
import bms.player.beatoraja.generated.AudioGridEstimator;
import bms.player.beatoraja.generated.GeneratedChartBuilder;
import bms.player.beatoraja.select.MusicSelector;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import static bms.player.beatoraja.modmenu.ImGuiRenderer.*;

/**
 * Window for a dropped audio file: shows the detected grid, lets the player
 * correct it and pick a difficulty, then starts a generated chart (Issue #433).
 */
public final class AudioChartMenu {

    private static final ImInt DIVISION = new ImInt(8);
    private static final int[] MIN_CHORD = {1};
    private static final int[] MAX_CHORD = {2};
    private static final ImBoolean SCRATCH = new ImBoolean(false);
    private static AudioChartSession loadedFor;
    private static final float[] WARNING = {1.0f, 0.75f, 0.3f, 1.0f};

    private AudioChartMenu() {
    }

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    public static void render() {
        AudioChartSession session = AudioChartSession.current();
        if (session == null || !session.window()) {
            return;
        }
        if (loadedFor != session) {
            // start each window from the remembered settings shared with the Music Select folder
            loadedFor = session;
            GeneratedChartBuilder.Settings remembered = AudioChartSession.settings(BMSIRArenaClient.playerConfig());
            DIVISION.set(remembered.division());
            MIN_CHORD[0] = remembered.minChord();
            MAX_CHORD[0] = remembered.maxChord();
            SCRATCH.set(remembered.scratch());
        }
        ImGui.setNextWindowPos(windowWidth * 0.30f, windowHeight * 0.15f, ImGuiCond.FirstUseEver);
        ImBoolean open = new ImBoolean(true);
        if (ImGui.begin(t("音源から譜面生成", "Chart From Audio") + "###generated-chart",
                open, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(session.audio().getFileName().toString());
            switch (session.state()) {
                case ANALYZING -> ImGui.text(t("解析中…", "Analyzing..."));
                case FAILED -> ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3],
                        t("解析できませんでした: ", "Analysis failed: ") + session.error());
                case READY -> renderReady(session);
            }
        }
        ImGui.end();
        if (!open.get()) {
            AudioChartSession.close();
        }
    }

    private static void renderReady(AudioChartSession session) {
        AudioGridEstimator.Result result = session.result();
        ImGui.separator();
        ImGui.text(String.format(Locale.ROOT, "BPM %.2f", session.bpm()));
        ImGui.sameLine();
        if (ImGui.button("x1/2")) session.scaleBpm(0.5);
        ImGui.sameLine();
        if (ImGui.button("x2")) session.scaleBpm(2.0);
        ImGui.sameLine();
        if (ImGui.button("-0.01")) session.setBpm(session.bpm() - 0.01);
        ImGui.sameLine();
        if (ImGui.button("+0.01")) session.setBpm(session.bpm() + 0.01);
        if (!result.alternatives().isEmpty()) {
            ImGui.text(t("候補:", "Candidates:"));
            for (AudioGridEstimator.Candidate candidate : result.alternatives()) {
                ImGui.sameLine();
                if (ImGui.button(String.format(Locale.ROOT, "%.2f", candidate.bpm()))) {
                    session.setBpm(candidate.bpm());
                }
            }
        }

        ImGui.text(String.format(Locale.ROOT, t("開始位置 %.0f ms", "First beat %.0f ms"),
                session.firstBeatSec() * 1000.0));
        ImGui.sameLine();
        if (ImGui.button("-10ms")) session.nudgeMs(-10);
        ImGui.sameLine();
        if (ImGui.button("+10ms")) session.nudgeMs(10);
        ImGui.sameLine();
        if (ImGui.button(t("半拍ずらす", "Shift half beat"))) session.shiftHalfBeat();
        ImGui.sameLine();
        if (ImGui.button(t("戻す", "Reset"))) session.resetAdjustments();
        ImGui.sameLine();
        helpMarker(t(
                "倍・半分のテンポや、表拍と裏拍の取り違えは自動では決めきれません。ずれていたらここで直してください。",
                "Half/double tempo and beat vs. off-beat cannot always be told apart automatically. Correct them here."));

        ImGui.text(String.format(Locale.ROOT, t("信頼度 %.1f", "Confidence %.1f"), result.confidence()));
        if (!result.stableTempo()) {
            ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3], String.format(Locale.ROOT,
                    t("テンポが一定でない可能性があります(最大ずれ %.0f ms)",
                            "Tempo may not be constant (max drift %.0f ms)"), result.maxDriftMs()));
        }

        ImGui.separator();
        ImGui.text(t("音符", "Notes"));
        ImGui.sameLine();
        ImGui.radioButton(t("4分", "4th"), DIVISION, 4);
        ImGui.sameLine();
        ImGui.radioButton(t("8分", "8th"), DIVISION, 8);
        ImGui.sameLine();
        ImGui.radioButton(t("16分", "16th"), DIVISION, 16);
        ImGui.sliderInt(t("最小同時押し", "Min chord"), MIN_CHORD, 1, GeneratedChartBuilder.KEYS);
        ImGui.sliderInt(t("最大同時押し", "Max chord"), MAX_CHORD, 1, GeneratedChartBuilder.KEYS);
        if (MAX_CHORD[0] < MIN_CHORD[0]) {
            MAX_CHORD[0] = MIN_CHORD[0];
        }
        ImGui.sameLine();
        helpMarker(t(
                "音の強い位置ほど同時押しが多くなります。最小と最大を同じにすると、常にその数の同時押しになります。",
                "Stronger hits get larger chords. Set min and max equal for a constant chord size."));
        ImGui.checkbox(t("皿あり(強いところだけ)", "Scratch on strong hits"), SCRATCH);
        if (ImGui.button(t("配置を変える", "Reshuffle lanes"))) session.reshuffle();
        ImGui.sameLine();
        ImGui.text(t("スコア保存・IR送信なし", "No score saving or IR submission"));

        ImGui.separator();
        if (ImGui.button(t("プレイ", "Play"))) {
            play(session);
        }
    }

    private static void play(AudioChartSession session) {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config != null) {
            config.setGeneratedChartDivision(DIVISION.get());
            config.setGeneratedChartChords(MIN_CHORD[0], MAX_CHORD[0]);
            config.setGeneratedChartScratch(SCRATCH.get());
        }
        Path chart;
        try {
            chart = session.writeChart(new GeneratedChartBuilder.Settings(
                    DIVISION.get(), MIN_CHORD[0], MAX_CHORD[0], SCRATCH.get()));
        } catch (IOException | RuntimeException exception) {
            ImGuiNotify.error(t("譜面を書き出せませんでした: ", "Could not write the chart: ")
                    + exception.getMessage(), 5000);
            return;
        }
        bms.player.beatoraja.MainController.pushOneShotAfterRenderTask(main -> {
            if (main.getCurrentState() instanceof MusicSelector selector) {
                if (selector.playGeneratedChart(chart)) {
                    AudioChartSession.close();
                }
            } else {
                ImGuiNotify.warning(t("選曲画面でのみプレイできます", "Only available in Music Select"), 5000);
            }
        });
    }
}
