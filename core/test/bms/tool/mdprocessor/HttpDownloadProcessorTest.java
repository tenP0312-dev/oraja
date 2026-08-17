package bms.tool.mdprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HttpDownloadProcessorTest {

	@Test
	void duplicateIdentityUsesBothUrlAndChartHash() {
		DownloadTask task = new DownloadTask(1, "https://example.com/package.zip", "Song",
				"a".repeat(32), DownloadTask.DownloadMode.ArchiveInPlace);

		assertTrue(HttpDownloadProcessor.hasSameTaskIdentity(
				task, "https://example.com/package.zip", "A".repeat(32)));
		assertFalse(HttpDownloadProcessor.hasSameTaskIdentity(
				task, "https://example.com/package.zip", "b".repeat(32)));
		assertFalse(HttpDownloadProcessor.hasSameTaskIdentity(
				task, "https://example.com/other.zip", "a".repeat(32)));
	}

	@Test
	void failedTaskCanBeClaimedForOnlyOneRetry() {
		DownloadTask task = new DownloadTask(2, "https://example.com/package.zip", "Song",
				"a".repeat(32), DownloadTask.DownloadMode.ArchiveInPlace);
		task.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Error);

		assertTrue(HttpDownloadProcessor.claimFailedTaskForRetry(task));
		assertEquals(DownloadTask.DownloadTaskStatus.Prepare, task.getDownloadTaskStatus());
		assertFalse(HttpDownloadProcessor.claimFailedTaskForRetry(task));
	}
}
