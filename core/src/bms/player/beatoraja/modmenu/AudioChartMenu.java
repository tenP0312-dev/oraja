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

    private static final ImInt DENSITY = new ImInt(GeneratedChartBuilder.DEFAULT_DENSITY);
    private static final int[] MIN_CHORD = {1};
    private static final int[] MAX_CHORD = {2};
    private static final ImBoolean SCRATCH = new ImBoolean(false);
    private static final ImBoolean FOLLOW_MUSIC = new ImBoolean(true);
    private static final ImInt DIVISION = new ImInt(8);
    private static final ImBoolean REPEAT_BARS = new ImBoolean(true);
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
            DENSITY.set(remembered.density());
            MIN_CHORD[0] = remembered.minChord();
            MAX_CHORD[0] = remembered.maxChord();
            SCRATCH.set(remembered.scratch());
            FOLLOW_MUSIC.set(remembered.followMusic());
            DIVISION.set(remembered.division());
            REPEAT_BARS.set(remembered.repeatBars());
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

        AudioGridEstimator.Candidate octave = result.octaveAlternative();
        if (octave != null) {
            ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3],
                    t("倍/半分のテンポの可能性があります", "This may be at half/double tempo"));
            ImGui.sameLine();
            if (ImGui.button(String.format(Locale.ROOT, "BPM %.2f", octave.bpm()))) {
                session.setBpm(octave.bpm());
            }
        }
        if (session.restoredAdjustment()) {
            ImGui.text(t("前回の補正を使っています", "Using your saved correction"));
        }
        ImGui.text(String.format(Locale.ROOT, t("信頼度 %.1f", "Confidence %.1f"), result.confidence()));
        if (!result.stableTempo()) {
            ImGui.textColored(WARNING[0], WARNING[1], WARNING[2], WARNING[3], String.format(Locale.ROOT,
                    t("テンポが一定でない可能性があります(最大ずれ %.0f ms)",
                            "Tempo may not be constant (max drift %.0f ms)"), result.maxDriftMs()));
        }

        ImGui.separator();
        ImGui.checkbox(t("鳴っている所にだけ置く", "Place notes where the music hits"), FOLLOW_MUSIC);
        if (FOLLOW_MUSIC.get()) {
            ImGui.text(t("ノーツの量", "Note amount"));
            for (int level = GeneratedChartBuilder.MIN_DENSITY; level <= GeneratedChartBuilder.MAX_DENSITY; level++) {
                ImGui.sameLine();
                ImGui.radioButton(densityLabel(level), DENSITY, level);
            }
        } else {
            ImGui.text(t("音符", "Notes"));
            for (int division : new int[] {4, 8, 12, 16, 24}) {
                ImGui.sameLine();
                ImGui.radioButton(divisionLabel(division), DIVISION, division);
            }
        }
        ImGui.checkbox(t("繰り返しは同じ配置にする", "Repeat the layout of repeated phrases"), REPEAT_BARS);
        ImGui.sliderInt(t("最小同時押し", "Min chord"), MIN_CHORD, 1, GeneratedChartBuilder.KEYS);
        ImGui.sliderInt(t("最大同時押し", "Max chord"), MAX_CHORD, 1, GeneratedChartBuilder.KEYS);
        if (MAX_CHORD[0] < MIN_CHORD[0]) {
            MAX_CHORD[0] = MIN_CHORD[0];
        }
        ImGui.sameLine();
        helpMarker(t(
                "音が鳴っている所にノーツを置き、強い音ほど同時押しが多くなります。同じフレーズの繰り返しは同じ配置になります。最小と最大を同じにすると常にその数の同時押しです。",
                "Notes go where the music hits; stronger hits get larger chords and repeated phrases repeat their layout. Set min and max equal for a constant chord size."));
        ImGui.checkbox(t("皿あり(はっきりしたハイハットの所)", "Scratch on clear hi-hats"), SCRATCH);
        if (ImGui.button(t("配置を変える", "Reshuffle lanes"))) session.reshuffle();
        ImGui.sameLine();
        ImGui.text(t("スコア保存・IR送信なし", "No score saving or IR submission"));

        ImGui.separator();
        if (ImGui.button(t("プレイ", "Play"))) {
            play(session);
        }
    }

    public static String divisionLabel(int division) {
        return t(division + "分", division + "th");
    }

    public static String densityLabel(int level) {
        return switch (level) {
            case 1 -> t("少なめ", "Light");
            case 2 -> t("やや少なめ", "Reduced");
            case 3 -> t("曲どおり", "As the music");
            default -> t("多め", "Dense");
        };
    }

    private static void play(AudioChartSession session) {
        PlayerConfig config = BMSIRArenaClient.playerConfig();
        if (config != null) {
            config.setGeneratedChartDensity(DENSITY.get());
            config.setGeneratedChartChords(MIN_CHORD[0], MAX_CHORD[0]);
            config.setGeneratedChartScratch(SCRATCH.get());
            config.setGeneratedChartFollowMusic(FOLLOW_MUSIC.get());
            config.setGeneratedChartDivision(DIVISION.get());
            config.setGeneratedChartRepeatBars(REPEAT_BARS.get());
        }
        Path chart;
        try {
            chart = session.writeChart(new GeneratedChartBuilder.Settings(FOLLOW_MUSIC.get(), DENSITY.get(),
                    DIVISION.get(), REPEAT_BARS.get(), MIN_CHORD[0], MAX_CHORD[0], SCRATCH.get()));
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
