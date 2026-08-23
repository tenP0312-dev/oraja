package bms.player.beatoraja.launcher;

import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

import bms.player.beatoraja.song.SongDatabaseUpdateListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongUpdateSummaryDialogTest {
	@Test
	void formatsLocalizedCountsWithGroupingAndJapaneseUnit() {
		ResourceBundle english = ResourceBundle.getBundle("resources.UIResources", Locale.ROOT);
		ResourceBundle japanese = ResourceBundle.getBundle("resources.UIResources", Locale.JAPAN);

		assertEquals("1,248", SongUpdateSummaryDialog.formatCount(english, 1_248));
		assertEquals("1,248件", SongUpdateSummaryDialog.formatCount(japanese, 1_248));
	}

	@Test
	void accessibleSummaryKeepsFullChartResultBeforeArchiveBreakdown() {
		ResourceBundle japanese = ResourceBundle.getBundle("resources.UIResources", Locale.JAPAN);
		SongDatabaseUpdateListener listener = new SongDatabaseUpdateListener();
		listener.addBMSFilesCount(1_248);
		listener.addProcessedBMSFilesCount(1_248);
		listener.addNewBMSFilesCount(3);
		for (int index = 0; index < 56; index++) {
			listener.archiveLoaded();
		}

		String summary = SongUpdateSummaryDialog.accessibleSummary(japanese, listener);

		assertTrue(summary.indexOf("検出 1,248件") < summary.indexOf("確認 56件"));
		assertTrue(summary.contains("処理済み 1,248件"));
		assertTrue(summary.contains("追加・更新 3件"));
		assertTrue(summary.contains("読み込み完了 56件"));
		assertTrue(summary.contains("読み込み不可 0件"));
	}

	@Test
	void accessibleSummaryOmitsArchiveBreakdownWhenNothingWasScanned() {
		ResourceBundle english = ResourceBundle.getBundle("resources.UIResources", Locale.ROOT);
		SongDatabaseUpdateListener listener = new SongDatabaseUpdateListener();
		listener.addBMSFilesCount(12);
		listener.addProcessedBMSFilesCount(12);

		String summary = SongUpdateSummaryDialog.accessibleSummary(english, listener);

		assertTrue(summary.contains("Detected 12"));
		assertTrue(summary.contains("Processed 12"));
		assertFalse(summary.contains("Checked"));
		assertFalse(summary.contains("Could not load"));
	}

	@Test
	void archiveFailureUsesPlainLanguageAndIncludesTheExistingReason() {
		ResourceBundle japanese = ResourceBundle.getBundle("resources.UIResources", Locale.JAPAN);
		SongDatabaseUpdateListener listener = new SongDatabaseUpdateListener();
		listener.archiveRejected("暗号化されています");

		String summary = SongUpdateSummaryDialog.accessibleSummary(japanese, listener);

		assertTrue(summary.contains("読み込み不可 1件"));
		assertTrue(summary.contains("暗号化されています"));
		assertFalse(summary.contains("拒否"));
	}
}
