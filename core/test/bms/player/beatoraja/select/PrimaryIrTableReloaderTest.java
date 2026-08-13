package bms.player.beatoraja.select;

import bms.player.beatoraja.ir.IRResponse;
import bms.player.beatoraja.ir.IRTableData;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimaryIrTableReloaderTest {
    @Test
    void suppressesDuplicatesUntilSuccessfulDataIsApplied() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        PrimaryIrTableReloader reloader = new PrimaryIrTableReloader(tasks::add);
        IRTableData[] expected = {new IRTableData("rival", null, null)};
        List<IRTableData[]> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        PrimaryIrTableReloader.Listener listener = listener(succeeded, failed);

        assertTrue(reloader.start(() -> response(true, "ok", expected), listener));
        assertFalse(reloader.start(() -> response(true, "duplicate", expected), listener));
        assertTrue(reloader.isRunning());

        tasks.remove().run();
        assertArrayEquals(expected, succeeded.get(0));
        assertTrue(failed.isEmpty());
        assertTrue(reloader.isRunning());

        reloader.complete();
        assertFalse(reloader.isRunning());
        assertTrue(reloader.start(() -> response(true, "next", expected), listener));
    }

    @Test
    void failedResponseKeepsTheCoordinatorReusable() {
        PrimaryIrTableReloader reloader = new PrimaryIrTableReloader(Runnable::run);
        List<IRTableData[]> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        assertTrue(reloader.start(
                () -> response(false, "offline", null),
                listener(succeeded, failed)
        ));

        assertTrue(succeeded.isEmpty());
        assertArrayEquals(new String[]{"offline"}, failed.toArray(String[]::new));
        assertFalse(reloader.isRunning());
    }

    @Test
    void exceptionIsReportedAndDoesNotLeaveReloadRunning() {
        PrimaryIrTableReloader reloader = new PrimaryIrTableReloader(Runnable::run);
        List<IRTableData[]> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        assertTrue(reloader.start(
                () -> {
                    throw new IllegalStateException("broken transport");
                },
                listener(succeeded, failed)
        ));

        assertTrue(succeeded.isEmpty());
        assertArrayEquals(
                new String[]{"broken transport"},
                failed.toArray(String[]::new)
        );
        assertFalse(reloader.isRunning());
    }

    private static PrimaryIrTableReloader.Listener listener(
            List<IRTableData[]> succeeded,
            List<String> failed
    ) {
        return new PrimaryIrTableReloader.Listener() {
            @Override
            public void succeeded(IRTableData[] tables) {
                succeeded.add(tables);
            }

            @Override
            public void failed(String message) {
                failed.add(message);
            }
        };
    }

    private static IRResponse<IRTableData[]> response(
            boolean succeeded,
            String message,
            IRTableData[] data
    ) {
        return new IRResponse<>() {
            @Override
            public boolean isSucceeded() {
                return succeeded;
            }

            @Override
            public String getMessage() {
                return message;
            }

            @Override
            public IRTableData[] getData() {
                return data;
            }
        };
    }
}
