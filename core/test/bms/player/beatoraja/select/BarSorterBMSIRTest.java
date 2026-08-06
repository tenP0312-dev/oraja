package bms.player.beatoraja.select;

import bms.player.beatoraja.select.bar.SongBar;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BarSorterBMSIRTest {
	@Test
	void equalLevelsUseTitleOrder() {
		SongBar alpha = song("Alpha", 12, 100);
		SongBar beta = song("Beta", 12, 50);

		assertTrue(BarSorter.LEVEL.sorter.compare(alpha, beta) < 0);
	}

	@Test
	void judgeSortUsesJudgeThenTitle() {
		SongBar hard = song("Zeta", 12, 50);
		SongBar normalAlpha = song("Alpha", 12, 100);
		SongBar normalBeta = song("Beta", 12, 100);

		assertTrue(BarSorter.JUDGE.sorter.compare(hard, normalAlpha) < 0);
		assertTrue(BarSorter.JUDGE.sorter.compare(normalAlpha, normalBeta) < 0);
	}

	private static SongBar song(String title, int level, int judge) {
		SongData song = new SongData();
		song.setTitle(title);
		song.setLevel(level);
		song.setJudge(judge);
		song.setPath(title + ".bms");
		return new SongBar(song);
	}
}
