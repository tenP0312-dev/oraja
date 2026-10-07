package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.generated.AudioChartSession;
import bms.player.beatoraja.generated.AudioGridEstimator;
import bms.player.beatoraja.generated.GeneratedChartBuilder;
import bms.player.beatoraja.modmenu.AudioChartMenu;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.select.MusicSelector;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Queue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static bms.player.beatoraja.SystemSoundManager.SoundType.OPTION_CHANGE;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SEARCH;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SONG;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SPECIAL;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_TABLE;

/**
 * One audio file in the generated-chart folder (#436). Entering it starts the
 * analysis; its rows show the result and change settings with the decide
 * button only, like the My Difficulty Table editor, so the whole flow works
 * from a controller. The top row plays, so two presses start a chart with the
 * remembered settings.
 */
public final class GeneratedAudioChartBar extends DirectoryBar {
    private final Path audio;

    public GeneratedAudioChartBar(MusicSelector selector, Path audio) {
        super(selector, true);
        this.audio = audio;
        setSortable(false);
    }

    public Path getAudio() {
        return audio;
    }

    @Override
    public String getTitle() {
        return audio.getFileName().toString();
    }

    @Override
    public int getLamp(boolean isPlayer) {
        return 0;
    }

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    @Override
    public Bar[] getChildren() {
        AudioChartSession session = AudioChartSession.current();
        if (session == null || !session.audio().equals(audio)) {
            session = AudioChartSession.start(audio, false, this::refreshWhenVisible);
        }
        return rows(session, selector != null ? selector.resource.getPlayerConfig() : null).toArray(new Bar[0]);
    }

    /** Rebuilds the rows once the analysis finishes, if this file is still open in Music Select. */
    private void refreshWhenVisible() {
        if (Gdx.app == null || selector == null) {
            return;
        }
        Gdx.app.postRunnable(() -> {
            if (selector.main.getCurrentState() != selector) {
                return;
            }
            Queue<DirectoryBar> directory = selector.getBarManager().getDirectory();
            if (directory.size > 0 && directory.last() instanceof GeneratedAudioChartBar open
                    && open.audio.equals(audio)) {
                selector.getBarManager().updateBar();
            }
        });
    }

    static List<KeyedFunctionBar> rows(AudioChartSession session, PlayerConfig config) {
        List<KeyedFunctionBar> rows = new ArrayList<>();
        switch (session.state()) {
            case ANALYZING -> rows.add(row("status", STYLE_SEARCH,
                    t("解析中: ", "Analyzing: ") + songName(session.audio()) + t("（決定で更新）", " (press to refresh)"),
                    ignored -> { }));
            case FAILED -> {
                rows.add(row("status", STYLE_SEARCH,
                        t("解析できませんでした: ", "Analysis failed: ") + session.error(), ignored -> { }));
                rows.add(row("retry", STYLE_SPECIAL, t("もう一度解析する", "Analyze again"),
                        ignored -> AudioChartSession.close()));
            }
            case READY -> readyRows(session, config, rows);
        }
        return rows;
    }

    private static void readyRows(AudioChartSession session, PlayerConfig config, List<KeyedFunctionBar> rows) {
        GeneratedChartBuilder.Settings settings = AudioChartSession.settings(config);
        AudioGridEstimator.Result result = session.result();
        // the selected row's title is shown large on Music Select, so the play row names the song
        rows.add(new KeyedFunctionBar("play", (selector, self) -> play(selector, session),
                t("プレイ: ", "Play: ") + songName(session.audio()), STYLE_SONG));
        String status = String.format(Locale.ROOT, t("BPM %.2f / 開始 %.0f ms / 信頼度 %.1f", "BPM %.2f / first beat %.0f ms / confidence %.1f"),
                session.bpm(), session.firstBeatSec() * 1000.0, result.confidence());
        if (!result.stableTempo()) {
            status += t(" / テンポ不安定", " / unstable tempo");
        }
        if (session.restoredAdjustment()) {
            status += t(" / 保存した補正", " / saved correction");
        }
        AudioGridEstimator.Candidate octave = result.octaveAlternative();
        if (octave != null && Math.abs(octave.bpm() - session.bpm()) > 0.05
                && Math.abs(result.bpm() - session.bpm()) < 0.05) {
            // half/double tempo is a close call: one press away, right under Play
            String value = String.format(Locale.ROOT, "%.2f", octave.bpm());
            rows.add(row("octave", STYLE_SPECIAL,
                    t("倍/半分の可能性: BPM ", "Maybe half/double tempo: BPM ") + value + t(" にする", ""),
                    ignored -> session.setBpm(octave.bpm())));
        }
        rows.add(row("status", STYLE_SEARCH, status, ignored -> { }));

        rows.add(row("followMusic", STYLE_TABLE,
                t("鳴っている所にだけ置く: ", "Place where the music hits: ") + onOff(settings.followMusic())
                        + t("（決定で切替）", " (press to change)"),
                c -> c.setGeneratedChartFollowMusic(!settings.followMusic()), config));
        if (settings.followMusic()) {
            rows.add(row("density", STYLE_TABLE,
                    t("ノーツの量: ", "Note amount: ") + AudioChartMenu.densityLabel(settings.density())
                            + t("（決定で切替）", " (press to change)"),
                    c -> c.setGeneratedChartDensity(nextDensity(settings.density())), config));
        } else {
            rows.add(row("division", STYLE_TABLE,
                    t("音符: ", "Notes: ") + AudioChartMenu.divisionLabel(settings.division())
                            + t("（決定で切替）", " (press to change)"),
                    c -> c.setGeneratedChartDivision(nextDivision(settings.division())), config));
        }
        rows.add(row("repeatBars", STYLE_TABLE,
                t("繰り返しは同じ配置: ", "Repeat repeated phrases: ") + onOff(settings.repeatBars())
                        + t("（決定で切替）", " (press to change)"),
                c -> c.setGeneratedChartRepeatBars(!settings.repeatBars()), config));
        rows.add(row("minChord", STYLE_TABLE,
                t("最小同時押し: ", "Min chord: ") + settings.minChord() + t("（決定で+1）", " (press for +1)"),
                c -> c.setGeneratedChartChords(nextMinChord(settings.minChord()), settings.maxChord()), config));
        rows.add(row("maxChord", STYLE_TABLE,
                t("最大同時押し: ", "Max chord: ") + settings.maxChord() + t("（決定で+1）", " (press for +1)"),
                c -> c.setGeneratedChartChords(settings.minChord(), nextMaxChord(settings.minChord(), settings.maxChord())), config));
        rows.add(row("scratch", STYLE_TABLE,
                t("皿: ", "Scratch: ") + (settings.scratch() ? t("はっきりしたハイハットの所", "on clear hi-hats") : t("なし", "off"))
                        + t("（決定で切替）", " (press to change)"),
                c -> c.setGeneratedChartScratch(!settings.scratch()), config));
        rows.add(row("reshuffle", STYLE_TABLE, t("配置を変える", "Reshuffle lanes"), ignored -> session.reshuffle()));

        rows.add(row("bpmDouble", STYLE_SPECIAL, t("BPM ×2", "BPM x2"), ignored -> session.scaleBpm(2.0)));
        rows.add(row("bpmHalf", STYLE_SPECIAL, t("BPM ×1/2", "BPM x1/2"), ignored -> session.scaleBpm(0.5)));
        for (AudioGridEstimator.Candidate candidate : result.alternatives()) {
            String value = String.format(Locale.ROOT, "%.2f", candidate.bpm());
            rows.add(row("bpmCandidate" + value, STYLE_SPECIAL,
                    t("BPM候補 ", "BPM candidate ") + value + t(" にする", ""),
                    ignored -> session.setBpm(candidate.bpm())));
        }
        rows.add(row("bpmUp", STYLE_SPECIAL, "BPM +0.01", ignored -> session.setBpm(session.bpm() + 0.01)));
        rows.add(row("bpmDown", STYLE_SPECIAL, "BPM -0.01", ignored -> session.setBpm(session.bpm() - 0.01)));
        rows.add(row("offsetUp", STYLE_SPECIAL, t("開始 +10ms", "First beat +10 ms"), ignored -> session.nudgeMs(10)));
        rows.add(row("offsetDown", STYLE_SPECIAL, t("開始 -10ms", "First beat -10 ms"), ignored -> session.nudgeMs(-10)));
        rows.add(row("halfBeat", STYLE_SPECIAL, t("半拍ずらす（表拍/裏拍）", "Shift half a beat (beat / off-beat)"),
                ignored -> session.shiftHalfBeat()));
        rows.add(row("reset", STYLE_SPECIAL, t("BPMと開始位置を解析結果に戻す", "Reset BPM and first beat"),
                ignored -> session.resetAdjustments()));
    }

    private static KeyedFunctionBar row(String key, int style, String title, Consumer<PlayerConfig> change, PlayerConfig config) {
        return row(key, style, title, ignored -> {
            if (config != null) {
                change.accept(config);
            }
        });
    }

    private static KeyedFunctionBar row(String key, int style, String title, Consumer<Void> change) {
        return new KeyedFunctionBar(key, (selector, self) -> {
            change.accept(null);
            selector.getBarManager().updateBar();
            selector.play(OPTION_CHANGE);
        }, title, style);
    }

    private static void play(MusicSelector selector, AudioChartSession session) {
        if (BMSIRArenaClient.isNominationOpen() || BMSIRArenaClient.isSelectionBlocked()) {
            ImGuiNotify.warning(t("Arenaの対戦準備中は生成譜面をプレイできません",
                    "Generated charts cannot be played while Arena is preparing a match"), 5000);
            return;
        }
        Path chart;
        try {
            chart = session.writeChart(AudioChartSession.settings(selector.resource.getPlayerConfig()));
        } catch (IOException | RuntimeException exception) {
            ImGuiNotify.error(t("譜面を書き出せませんでした: ", "Could not write the chart: ")
                    + exception.getMessage(), 5000);
            return;
        }
        selector.playGeneratedChart(chart);
    }

    static String songName(Path audio) {
        String name = audio.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static String onOff(boolean on) {
        return on ? "ON" : "OFF";
    }

    /** 4 → 8 → 12 → 16 → 24 → 4 */
    static int nextDivision(int division) {
        return switch (division) {
            case 4 -> 8;
            case 8 -> 12;
            case 12 -> 16;
            case 16 -> 24;
            default -> 4;
        };
    }

    /** light → reduced → as the music → dense → light */
    static int nextDensity(int density) {
        return density >= GeneratedChartBuilder.MAX_DENSITY ? GeneratedChartBuilder.MIN_DENSITY : density + 1;
    }

    /** 1 → 2 → … → 7 → 1; the max follows when it falls below. */
    static int nextMinChord(int min) {
        return min >= GeneratedChartBuilder.KEYS ? 1 : min + 1;
    }

    /** max+1, wrapping back to the current min after 7. */
    static int nextMaxChord(int min, int max) {
        return max >= GeneratedChartBuilder.KEYS ? min : max + 1;
    }
}
