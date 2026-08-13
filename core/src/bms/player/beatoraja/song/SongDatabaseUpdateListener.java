package bms.player.beatoraja.song;

import javafx.beans.property.IntegerProperty;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listen current songdata.db's update progress
 */
public class SongDatabaseUpdateListener {
	private final AtomicInteger bmsFiles = new AtomicInteger(0);
	private final AtomicInteger processedBMSFiles = new AtomicInteger(0);
	private final AtomicInteger newBMSFiles = new AtomicInteger(0);
	private final AtomicInteger archivesScanned = new AtomicInteger(0);
	private final AtomicInteger archivesLoaded = new AtomicInteger(0);
	private final AtomicInteger archivesRejected = new AtomicInteger(0);
	private volatile String lastArchiveFailure = "";

	public void addBMSFilesCount(int count) {
		bmsFiles.addAndGet(count);
	}

	public void addProcessedBMSFilesCount(int count) {
		processedBMSFiles.addAndGet(count);
	}

	public void addNewBMSFilesCount(int count) {
		newBMSFiles.addAndGet(count);
	}

	public int getBMSFilesCount() {
		return bmsFiles.get();
	}

	public int getProcessedBMSFilesCount() {
		return processedBMSFiles.get();
	}

	public int getNewBMSFilesCount() {
		return newBMSFiles.get();
	}

	public void archiveLoaded() {
		archivesScanned.incrementAndGet();
		archivesLoaded.incrementAndGet();
	}

	public void archiveRejected(String failure) {
		archivesScanned.incrementAndGet();
		archivesRejected.incrementAndGet();
		if (failure == null) {
			lastArchiveFailure = "";
		} else {
			lastArchiveFailure = failure.length() <= 500 ? failure : failure.substring(0, 497) + "...";
		}
	}

	public int getArchivesScanned() {
		return archivesScanned.get();
	}

	public int getArchivesLoaded() {
		return archivesLoaded.get();
	}

	public int getArchivesRejected() {
		return archivesRejected.get();
	}

	public String getLastArchiveFailure() {
		return lastArchiveFailure;
	}
}
