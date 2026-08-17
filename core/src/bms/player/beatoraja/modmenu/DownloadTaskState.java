package bms.player.beatoraja.modmenu;

import bms.tool.mdprocessor.DownloadTask;
import bms.tool.mdprocessor.HttpDownloadProcessor;

import java.util.Map;
import java.util.HashMap;

public class DownloadTaskState {
    public static final Map<Integer, DownloadTask> runningDownloadTasks =
        new HashMap<Integer, DownloadTask>();
    public static final Map<Integer, DownloadTask> expiredTasks =
        new HashMap<Integer, DownloadTask>();

    private static HttpDownloadProcessor httpDownloadProcessor;

    public static void initialize(HttpDownloadProcessor httpDownloadProcessor) {
        DownloadTaskState.httpDownloadProcessor = httpDownloadProcessor;
        runningDownloadTasks.clear();
        expiredTasks.clear();
        lastSnapshot = System.nanoTime();
    }

    private static long lastSnapshot = 0;

    public static void update() {
        long now = System.nanoTime();
        // no reason to check very often (1s)
        if ((now - lastSnapshot) < 1000000000L) {
            return;
        }
        lastSnapshot = now;

        reconcile(httpDownloadProcessor.getAllTasks(), now);
    }

    static void reconcile(Map<Integer, DownloadTask> tasks, long now) {
        for (var taskEntry : tasks.entrySet()) {
            int id = taskEntry.getKey();
            DownloadTask task = taskEntry.getValue();
            boolean finished = task.getDownloadTaskStatus().getValue() >=
                               DownloadTask.DownloadTaskStatus.Extracted.getValue();
            boolean expired = finished && (5000000000L < now - task.getTimeFinished());

            if (expired) {
                runningDownloadTasks.remove(id);
                expiredTasks.put(id, task);
            }
            else {
                expiredTasks.remove(id);
                runningDownloadTasks.put(id, task);
            }
        }
    }
}
