package bms.player.beatoraja.nonstop;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Hands results of background work to the render thread, where the overlay and notifications live. */
public final class MixMainThread {
    private static final ConcurrentLinkedQueue<Runnable> TASKS = new ConcurrentLinkedQueue<>();

    private MixMainThread() {
    }

    public static void post(Runnable task) {
        TASKS.add(task);
    }

    /** Runs the queued tasks; called once per frame from the overlay renderer. */
    public static void drain() {
        Runnable task;
        int budget = 64;
        while (budget-- > 0 && (task = TASKS.poll()) != null) {
            task.run();
        }
    }
}
