package bms.player.beatoraja.modmenu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import bms.tool.mdprocessor.DownloadTask;

class DownloadTaskStateTest {

	@BeforeEach
	void clearTaskViews() {
		DownloadTaskState.runningDownloadTasks.clear();
		DownloadTaskState.expiredTasks.clear();
	}

	@Test
	void failedTaskReturnsToRunningViewWhenRetried() {
		DownloadTask task = new DownloadTask(7, "https://example.com/song.zip", "Song",
				"0".repeat(32), DownloadTask.DownloadMode.ArchiveInPlace);
		task.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Error);
		long afterExpiry = task.getTimeFinished() + 6_000_000_000L;

		DownloadTaskState.reconcile(Map.of(task.getId(), task), afterExpiry);

		assertTrue(DownloadTaskState.expiredTasks.containsKey(task.getId()));
		assertFalse(DownloadTaskState.runningDownloadTasks.containsKey(task.getId()));

		task.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Prepare);
		DownloadTaskState.reconcile(Map.of(task.getId(), task), afterExpiry + 1);

		assertFalse(DownloadTaskState.expiredTasks.containsKey(task.getId()));
		assertTrue(DownloadTaskState.runningDownloadTasks.containsKey(task.getId()));
	}
}
