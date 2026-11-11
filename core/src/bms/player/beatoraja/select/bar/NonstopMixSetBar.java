package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.nonstop.CompiledMix;
import bms.player.beatoraja.nonstop.MixIrClient;
import bms.player.beatoraja.nonstop.MixLaunch;
import bms.player.beatoraja.nonstop.MixLibrary;
import bms.player.beatoraja.nonstop.MixPlan;
import bms.player.beatoraja.nonstop.MixPreparation;
import bms.player.beatoraja.nonstop.MixResult;
import bms.player.beatoraja.nonstop.MixRuntime;
import bms.player.beatoraja.nonstop.MixTrack;
import bms.player.beatoraja.select.MusicSelector;
import com.badlogic.gdx.Gdx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static bms.player.beatoraja.SystemSoundManager.SoundType.OPTION_CHANGE;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_COURSE;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_MISSING;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SEARCH;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SONG;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SPECIAL;
import static bms.player.beatoraja.select.bar.NonstopMixFolderBar.t;

/**
 * One NONSTOP MIX set in Music Select (#539). Its rows play the set or play it
 * on autoplay, show the preparation progress with a cancel row, and list the
 * songs. When the set is ready it starts by itself, like a course.
 */
public final class NonstopMixSetBar extends DirectoryBar {
    private final MixPlan plan;
    private final MixResult best;
    private List<MixTrack> missing;

    public NonstopMixSetBar(MusicSelector selector, MixPlan plan, MixResult best) {
        super(selector, true);
        this.plan = plan;
        this.best = best;
        setSortable(false);
    }

    public MixPlan getPlan() {
        return plan;
    }

    @Override
    public String getTitle() {
        return title(plan);
    }

    static String title(MixPlan plan) {
        return plan.name == null || plan.name.isBlank() ? t("（名前のないセット）", "(untitled set)") : plan.name;
    }

    @Override
    public int getLamp(boolean isPlayer) {
        return isPlayer && best != null ? best.clear : 0;
    }

    /** The bar is drawn every frame, so the library lookup runs once. */
    List<MixTrack> missing() {
        if (missing == null) {
            MixLibrary library = NonstopMixFolderBar.library(selector);
            missing = library == null ? List.of() : library.missing(plan);
        }
        return missing;
    }

    public boolean existsAllSongs() {
        return missing().isEmpty();
    }

    @Override
    public Bar[] getChildren() {
        // songs may have been added since the folder was opened
        missing = null;
        return rows(plan, missing(), best, MixLaunch.runningFor(plan.id)).toArray(new Bar[0]);
    }

    static List<KeyedFunctionBar> rows(MixPlan plan, List<MixTrack> missing, MixResult best, MixLaunch running) {
        List<KeyedFunctionBar> rows = new ArrayList<>();
        if (running != null) {
            rows.add(row("status", STYLE_SEARCH, status(running.job()) + t("（決定で更新）", " (press to refresh)"),
                    (selector, self) -> refresh(selector)));
            rows.add(row("cancel", STYLE_SPECIAL, t("準備をやめる", "Stop preparing"), (selector, self) -> {
                running.cancel();
                refresh(selector);
            }));
        } else if (plan.tracks.isEmpty()) {
            rows.add(row("play", STYLE_MISSING, t("曲が入っていません", "The set has no songs"),
                    (selector, self) -> refresh(selector)));
        } else if (!missing.isEmpty()) {
            rows.add(row("play", STYLE_MISSING,
                    String.format(Locale.ROOT, t("持っていない曲があります（%d曲）", "%d songs are missing"), missing.size()),
                    (selector, self) -> refresh(selector)));
        } else {
            // the selected row's title is shown large on Music Select, so the play row names the set
            rows.add(row("play", STYLE_COURSE, t("プレイ: ", "Play: ") + title(plan),
                    (selector, self) -> start(selector, plan, MixLaunch.Kind.PLAY)));
            rows.add(row("autoplay", STYLE_SPECIAL, t("オートで通し再生", "Autoplay the set"),
                    (selector, self) -> start(selector, plan, MixLaunch.Kind.AUTOPLAY)));
        }
        rows.add(row("summary", STYLE_SEARCH, summary(plan), (selector, self) -> refresh(selector)));
        rows.add(row("best", STYLE_SEARCH, bestText(best), (selector, self) -> refresh(selector)));
        Set<MixTrack> absent = Collections.newSetFromMap(new IdentityHashMap<>());
        absent.addAll(missing);
        for (int i = 0; i < plan.tracks.size(); i++) {
            MixTrack track = plan.tracks.get(i);
            boolean lacking = absent.contains(track);
            rows.add(row("track" + i, lacking ? STYLE_MISSING : STYLE_SONG,
                    (i + 1) + ". " + track.displayTitle() + (lacking ? t("（持っていない）", " (missing)") : ""),
                    (selector, self) -> refresh(selector)));
        }
        return rows;
    }

    static String status(MixPreparation job) {
        if (job == null) {
            return t("準備しています", "Preparing");
        }
        return switch (job.state()) {
            case LOADING -> String.format(Locale.ROOT, t("曲を読み込んでいます %d/%d", "Loading songs %d/%d"),
                    job.progress(), job.total());
            case LOUDNESS -> t("音量をそろえています", "Matching loudness");
            case COMPILING -> t("セットを組み立てています", "Building the set");
            case READY -> t("まもなく始まります", "Starting");
            case FAILED -> t("準備できませんでした", "Could not prepare the set");
            case CANCELLED -> t("やめました", "Stopped");
        };
    }

    static String summary(MixPlan plan) {
        long seconds = MixIrClient.estimatedDuration(plan) / 1000;
        return String.format(Locale.ROOT, t("%d曲 / 約%d分%02d秒 / 判定 %s", "%d songs / about %d:%02d / judge %s"),
                plan.tracks.size(), seconds / 60, seconds % 60, judgeName(plan.judge));
    }

    static String judgeName(MixPlan.JudgeRule judge) {
        return switch (judge) {
            case AVERAGE -> t("曲の平均", "song average");
            case STRICTEST -> t("一番きびしい曲", "strictest song");
            default -> judge.name().replace('_', ' ');
        };
    }

    static String bestText(MixResult best) {
        if (best == null) {
            return t("自己ベスト: まだ記録がありません", "Best: no record yet");
        }
        return String.format(Locale.ROOT, t("自己ベスト: %s / EX %d/%d (%.1f%%)", "Best: %s / EX %d/%d (%.1f%%)"),
                MixResult.clearName(best.clear), best.exScore, best.maxExScore, best.rate());
    }

    private static KeyedFunctionBar row(String key, int style, String title,
            java.util.function.BiConsumer<MusicSelector, FunctionBar> action) {
        return new KeyedFunctionBar(key, action, title, style);
    }

    private static void refresh(MusicSelector selector) {
        selector.getBarManager().updateBar();
        selector.play(OPTION_CHANGE);
    }

    static boolean arenaBlocks() {
        return BMSIRArenaClient.blocksLocalOneBass() || bms.player.beatoraja.arena.client.Client.connected.get()
                || BMSIRArenaClient.isNominationOpen() || BMSIRArenaClient.isSelectionBlocked();
    }

    private static void start(MusicSelector selector, MixPlan plan, MixLaunch.Kind kind) {
        if (arenaBlocks()) {
            ImGuiNotify.warning(t("Arena接続中はNONSTOP MIXを遊べません",
                    "NONSTOP MIX is not available while connected to Arena"), 5000);
            return;
        }
        MixLibrary library = NonstopMixFolderBar.library(selector);
        List<MixTrack> lacking = library.missing(plan);
        if (!lacking.isEmpty()) {
            ImGuiNotify.warning(String.format(Locale.ROOT, t("持っていない曲があります（%d曲）: %s", "%d songs are missing: %s"),
                    lacking.size(), lacking.get(0).displayTitle()), 5000);
            refresh(selector);
            return;
        }
        MixLaunch.start(plan, library, kind,
                launch -> Gdx.app.postRunnable(() -> finished(selector, plan, launch)));
        refresh(selector);
    }

    /** Runs on the render thread once the preparation ended. */
    private static void finished(MusicSelector selector, MixPlan stored, MixLaunch launch) {
        if (!launch.isCurrent()) {
            // cancelled, or replaced by another set
            return;
        }
        launch.finish();
        MixPreparation job = launch.job();
        switch (job.state()) {
            case READY -> {
                CompiledMix mix = job.result();
                if (launch.kind() == MixLaunch.Kind.PLAY && mix.hasErrors()) {
                    ImGuiNotify.error(t("このセットには遊べない問題があります。F5のNONSTOP MIXで確認してください",
                            "The set has problems that must be fixed first; open it in F5 > NONSTOP MIX"), 6000);
                    NonstopMixFolderBar.refreshNow(selector);
                    return;
                }
                keepRandoms(selector, stored, job.plan());
                if (selector.main.getCurrentState() != selector || !NonstopMixFolderBar.showing(selector)) {
                    // the player left the NONSTOP MIX folder while it was loading
                    ImGuiNotify.info(t("NONSTOP MIXの準備ができましたが、フォルダを離れたので始めませんでした",
                            "The NONSTOP MIX set was ready, but you left its folder, so it did not start"), 4000);
                    return;
                }
                boolean full = launch.kind() == MixLaunch.Kind.PLAY;
                MixRuntime.Session session = new MixRuntime.Session(mix, job.sources(), job.plan(),
                        full ? MixRuntime.Kind.FULL : MixRuntime.Kind.PREVIEW, launch.title());
                if (!selector.playNonstopMix(session, full ? BMSPlayerMode.PLAY : BMSPlayerMode.AUTOPLAY)) {
                    NonstopMixFolderBar.refreshNow(selector);
                }
            }
            case FAILED -> {
                ImGuiNotify.error(t("準備できませんでした: ", "Could not prepare the set: ") + job.error(), 6000);
                NonstopMixFolderBar.refreshNow(selector);
            }
            default -> NonstopMixFolderBar.refreshNow(selector);
        }
    }

    /**
     * #RANDOM branches drawn while loading are written back to the saved set,
     * as the editor does, so the set and its records stay the same next time.
     */
    static boolean copyRandoms(MixPlan stored, MixPlan prepared) {
        if (stored.tracks.size() != prepared.tracks.size()) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < stored.tracks.size(); i++) {
            MixTrack mine = stored.tracks.get(i);
            MixTrack theirs = prepared.tracks.get(i);
            if (mine.random == null && theirs.random != null && mine.sha256.equals(theirs.sha256)) {
                mine.random = theirs.random.clone();
                changed = true;
            }
        }
        return changed;
    }

    private static void keepRandoms(MusicSelector selector, MixPlan stored, MixPlan prepared) {
        if (!copyRandoms(stored, prepared)) {
            return;
        }
        try {
            NonstopMixFolderBar.store(selector).saveSet(stored);
        } catch (IOException e) {
            ImGuiNotify.warning(t("セットを保存できませんでした: ", "Could not save the set: ") + e.getMessage(), 5000);
        }
    }
}
