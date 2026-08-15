package bms.player.beatoraja.arena.bmsir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/** Standalone macOS Swing entry point, launched with the same bundled JRE. */
public final class ArenaInlineTextEditorHelper {
    private ArenaInlineTextEditorHelper() {
    }

    public static void main(String[] args) {
        int exitCode = run();
        System.exit(exitCode);
    }

    private static int run() {
        try (
                DataInputStream input = new DataInputStream(System.in);
                DataOutputStream output = new DataOutputStream(System.out)
        ) {
            ArenaInlineTextEditorProtocol.Request request =
                    ArenaInlineTextEditorProtocol.readRequest(input);
            CountDownLatch finished = new CountDownLatch(1);
            ArenaInlineTextEditorWindow.openStandalone(
                    request,
                    () -> ProcessHandle.of(request.parentPid())
                            .map(ProcessHandle::isAlive)
                            .orElse(false),
                    () -> writeReady(output),
                    result -> writeResult(output, result, finished),
                    error -> writeFailure(output, finished)
            );
            finished.await();
            return 0;
        } catch (Exception error) {
            return 1;
        }
    }

    private static boolean writeReady(DataOutputStream output) {
        try {
            ArenaInlineTextEditorProtocol.writeReady(output);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private static void writeResult(
            DataOutputStream output,
            String result,
            CountDownLatch finished
    ) {
        try {
            if (result == null) {
                ArenaInlineTextEditorProtocol.writeCancel(output);
            } else {
                ArenaInlineTextEditorProtocol.writeAccept(output, result);
            }
        } catch (IOException ignored) {
            // The parent process may have exited or timed out.
        } finally {
            finished.countDown();
        }
    }

    private static void writeFailure(
            DataOutputStream output,
            CountDownLatch finished
    ) {
        try {
            ArenaInlineTextEditorProtocol.writeError(output);
        } catch (IOException ignored) {
            // The parent process may have exited or timed out.
        } finally {
            finished.countDown();
        }
    }
}
