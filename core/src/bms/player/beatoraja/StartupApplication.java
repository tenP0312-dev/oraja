package bms.player.beatoraja;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;

import bms.player.beatoraja.system.TimingDiagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

final class StartupApplication implements ApplicationListener {
    private static final Logger logger = LoggerFactory.getLogger(StartupApplication.class);

    private final MainController main;
    private final Config config;
    private final Config.DisplayMode requestedDisplayMode;
    private final Graphics.DisplayMode fullscreenMode;
    private final List<StartupTask> tasks;

    private StartupProgressRenderer progress;
    private int taskIndex;
    private int taskEntryIndex = -1;
    private boolean taskWasPresented;
    private boolean ready;
    private boolean failed;

    StartupApplication(
            MainController main,
            Config config,
            Config.DisplayMode requestedDisplayMode,
            Graphics.DisplayMode fullscreenMode
    ) {
        this.main = main;
        this.config = config;
        this.requestedDisplayMode = requestedDisplayMode;
        this.fullscreenMode = fullscreenMode;
        this.tasks = main.createStartupTasks();
        TimingDiagnostics.configure(config);
    }

    @Override
    public void create() {
        logger.info("Starting {}", Version.getArenaDisplayName());
        logger.info("[Build info] Commit: {}", Version.getGitCommitHash());
        progress = new StartupProgressRenderer(config);
        progress.addCompleted("FFmpeg初期化", "");
        progress.addCompleted("ゲームウィンドウ", "");
        progress.addCompleted("プレイヤー設定", main.getPlayerConfig().getId());
    }

    @Override
    public void render() {
        if (ready) {
            long timingStarted = TimingDiagnostics.renderStarted();
            try (var perf = PerformanceMetrics.get().Watch("render")) {
                main.beforeRender();
                main.render();
                main.afterRender();
            } finally {
                TimingDiagnostics.finish(
                        TimingDiagnostics.Metric.RENDER_DURATION,
                        timingStarted
                );
            }
            return;
        }

        progress.render();
        if (failed) {
            return;
        }
        if (taskIndex >= tasks.size()) {
            if (requestedDisplayMode == Config.DisplayMode.FULLSCREEN
                    && fullscreenMode != null) {
                Gdx.graphics.setFullscreenMode(fullscreenMode);
            }
            progress.dispose();
            progress = null;
            ready = true;
            return;
        }

        if (taskEntryIndex < 0) {
            taskEntryIndex = progress.addWaiting(tasks.get(taskIndex).label);
            return;
        }
        if (!taskWasPresented) {
            progress.running(taskEntryIndex);
            taskWasPresented = true;
            return;
        }

        StartupTask task = tasks.get(taskIndex);
        try {
            progress.complete(taskEntryIndex, task.operation.run());
            taskIndex++;
            taskEntryIndex = -1;
            taskWasPresented = false;
        } catch (Throwable error) {
            String reason = error.getLocalizedMessage();
            if (reason == null || reason.isBlank()) {
                reason = error.getClass().getSimpleName();
            }
            logger.error("Startup task failed: {}", task.label, error);
            if (task.fatal) {
                progress.error(taskEntryIndex, reason);
                failed = true;
            } else {
                progress.complete(taskEntryIndex, StartupTask.Result.skip(reason));
                taskIndex++;
                taskEntryIndex = -1;
                taskWasPresented = false;
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        if (ready) {
            main.resize(width, height);
        } else if (progress != null) {
            progress.resize(width, height);
        }
    }

    @Override
    public void pause() {
        if (ready) {
            main.pause();
        }
    }

    @Override
    public void resume() {
        if (ready) {
            main.resume();
        }
    }

    @Override
    public void dispose() {
        if (progress != null) {
            progress.dispose();
        }
        main.dispose();
    }
}
