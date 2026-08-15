package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.modmenu.ImGuiInputCapture;
import bms.player.beatoraja.modmenu.ImGuiRenderer;
import com.badlogic.gdx.Gdx;
import imgui.type.ImString;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Places an IME-capable native editor directly over an Arena ImGui field. */
final class ArenaInlineTextEditor {
    private static final Logger logger = LoggerFactory.getLogger(ArenaInlineTextEditor.class);
    private static final long STARTUP_TIMEOUT_SECONDS = 5;
    private static final AtomicReference<Session> ACTIVE_SESSION = new AtomicReference<>();
    private static final ExecutorService HELPER_EXECUTOR =
            Executors.newSingleThreadExecutor(new EditorThreadFactory("bmsir-ime-helper"));
    private static final ScheduledExecutorService WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(new EditorThreadFactory("bmsir-ime-watchdog"));

    private ArenaInlineTextEditor() {
    }

    static void open(
            ImString target,
            int maxCodePoints,
            float itemX,
            float itemY,
            float itemWidth,
            float itemHeight
    ) {
        Session session = new Session(target, maxCodePoints);
        if (!ACTIVE_SESSION.compareAndSet(null, session)) {
            return;
        }
        ImGuiInputCapture.setExternalEditorOpen(true);
        session.setStartupTimeout(WATCHDOG.schedule(
                () -> startupTimedOut(session),
                STARTUP_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));

        String initialValue = target.get();
        int relativeX = Math.round(itemX);
        int relativeY = Math.round(itemY);
        int width = Math.max(120, Math.round(itemWidth));
        int height = Math.max(24, Math.round(itemHeight));
        if (isMacOs(System.getProperty("os.name", ""))) {
            ArenaInlineTextEditorProtocol.Request request =
                    new ArenaInlineTextEditorProtocol.Request(
                            ProcessHandle.current().pid(),
                            initialValue,
                            maxCodePoints,
                            ImGuiRenderer.getWindowScreenX() + relativeX,
                            ImGuiRenderer.getWindowScreenY() + relativeY,
                            width,
                            height
                    );
            HELPER_EXECUTOR.execute(() -> runMacOsHelper(session, request));
            return;
        }

        ArenaInlineTextEditorWindow.openEmbedded(
                initialValue,
                maxCodePoints,
                relativeX,
                relativeY,
                width,
                height,
                () -> !session.isCompleted(),
                session::markReady,
                result -> complete(session, result),
                error -> fail(session, "embedded", error)
        );
    }

    static boolean isOpenFor(ImString target) {
        Session session = ACTIVE_SESSION.get();
        return session != null && session.target == target;
    }

    static String limitCodePoints(String value, int maxCodePoints) {
        String text = value == null ? "" : value;
        int limit = Math.max(0, maxCodePoints);
        int count = text.codePointCount(0, text.length());
        if (count <= limit) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, limit));
    }

    static boolean isMacOs(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("mac");
    }

    static List<String> helperCommand(String javaHome, String classPath) {
        String executable = Path.of(javaHome, "bin", "java").toString();
        return List.of(
                executable,
                "-Dapple.awt.UIElement=true",
                "-cp",
                classPath,
                ArenaInlineTextEditorHelper.class.getName()
        );
    }

    private static void runMacOsHelper(
            Session session,
            ArenaInlineTextEditorProtocol.Request request
    ) {
        Process process = null;
        try {
            process = new ProcessBuilder(helperCommand(
                    System.getProperty("java.home"),
                    System.getProperty("java.class.path")
            ))
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            if (!session.attachProcess(process)) {
                return;
            }
            try (
                    DataOutputStream output = new DataOutputStream(process.getOutputStream());
                    DataInputStream input = new DataInputStream(process.getInputStream())
            ) {
                ArenaInlineTextEditorProtocol.writeRequest(output, request);
                output.flush();
                output.close();

                int signal = ArenaInlineTextEditorProtocol.readSignal(input);
                if (signal != ArenaInlineTextEditorProtocol.SIGNAL_READY) {
                    throw new IllegalStateException("IME helper did not become ready");
                }
                if (!session.markReady()) {
                    return;
                }

                signal = ArenaInlineTextEditorProtocol.readSignal(input);
                if (signal == ArenaInlineTextEditorProtocol.SIGNAL_ACCEPT) {
                    complete(
                            session,
                            ArenaInlineTextEditorProtocol.readAcceptedText(input)
                    );
                } else if (signal == ArenaInlineTextEditorProtocol.SIGNAL_CANCEL) {
                    complete(session, null);
                } else {
                    throw new IllegalStateException("IME helper failed while editing");
                }
            }
        } catch (Exception error) {
            fail(session, "macos_helper", error);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    private static void startupTimedOut(Session session) {
        if (session.isReady() || session.isCompleted()) {
            return;
        }
        logger.warn("Arena inline IME editor startup timed out");
        complete(session, null);
    }

    private static void fail(Session session, String mode, Throwable error) {
        if (session.isCompleted()) {
            return;
        }
        logger.warn("Arena inline IME editor could not be opened ({})", mode, error);
        complete(session, null);
    }

    private static void complete(Session session, String result) {
        if (!session.tryComplete()) {
            return;
        }
        session.cancelStartupTimeout();
        session.destroyProcess();
        String limitedResult = result == null
                ? null
                : limitCodePoints(result, session.maxCodePoints);
        Runnable apply = () -> {
            if (!ACTIVE_SESSION.compareAndSet(session, null)) {
                return;
            }
            if (limitedResult != null) {
                session.target.set(limitedResult);
            }
            ImGuiInputCapture.setExternalEditorOpen(false);
        };
        if (Gdx.app != null) {
            try {
                Gdx.app.postRunnable(apply);
                return;
            } catch (RuntimeException error) {
                logger.warn("Arena inline IME result could not be queued", error);
            }
        }
        apply.run();
    }

    static final class Session {
        private final ImString target;
        private final int maxCodePoints;
        private final AtomicBoolean ready = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();
        private final AtomicReference<Process> process = new AtomicReference<>();
        private volatile ScheduledFuture<?> startupTimeout;

        Session(ImString target, int maxCodePoints) {
            this.target = target;
            this.maxCodePoints = maxCodePoints;
        }

        boolean markReady() {
            if (completed.get()) {
                return false;
            }
            ready.set(true);
            cancelStartupTimeout();
            return !completed.get();
        }

        boolean isReady() {
            return ready.get();
        }

        boolean tryComplete() {
            return completed.compareAndSet(false, true);
        }

        boolean isCompleted() {
            return completed.get();
        }

        boolean attachProcess(Process launchedProcess) {
            process.set(launchedProcess);
            if (!completed.get()) {
                return true;
            }
            destroyProcess();
            return false;
        }

        void destroyProcess() {
            Process launchedProcess = process.getAndSet(null);
            if (launchedProcess != null && launchedProcess.isAlive()) {
                launchedProcess.destroy();
            }
        }

        void setStartupTimeout(ScheduledFuture<?> timeout) {
            startupTimeout = timeout;
            if (ready.get() || completed.get()) {
                cancelStartupTimeout();
            }
        }

        void cancelStartupTimeout() {
            ScheduledFuture<?> timeout = startupTimeout;
            if (timeout != null) {
                timeout.cancel(false);
                startupTimeout = null;
            }
        }
    }

    private static final class EditorThreadFactory implements ThreadFactory {
        private final String name;

        private EditorThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        }
    }
}
