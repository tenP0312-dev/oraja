package bms.player.beatoraja.select;

import bms.player.beatoraja.ir.IRResponse;
import bms.player.beatoraja.ir.IRTableData;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Runs one Primary IR table fetch at a time and keeps it active until applied. */
final class PrimaryIrTableReloader {
    interface Listener {
        void succeeded(IRTableData[] tables);

        void failed(String message);
    }

    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();

    PrimaryIrTableReloader(Executor executor) {
        this.executor = executor;
    }

    boolean start(
            Supplier<IRResponse<IRTableData[]>> fetch,
            Listener listener
    ) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            executor.execute(() -> fetch(fetch, listener));
        } catch (RuntimeException error) {
            running.set(false);
            listener.failed(message(error));
        }
        return true;
    }

    void complete() {
        running.set(false);
    }

    boolean isRunning() {
        return running.get();
    }

    private void fetch(
            Supplier<IRResponse<IRTableData[]>> fetch,
            Listener listener
    ) {
        try {
            IRResponse<IRTableData[]> response = fetch.get();
            if (response == null || !response.isSucceeded() || response.getData() == null) {
                complete();
                listener.failed(
                        response == null || response.getMessage() == null
                                ? "No response"
                                : response.getMessage()
                );
                return;
            }
            // Success stays single-flight until the render thread applies it.
            listener.succeeded(response.getData());
        } catch (Throwable error) {
            complete();
            listener.failed(message(error));
        }
    }

    private static String message(Throwable error) {
        String message = error.getLocalizedMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }
}
