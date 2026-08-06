package bms.player.beatoraja.select;

import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.select.bar.Bar;
import bms.player.beatoraja.select.bar.DirectoryBar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BarManagerScoreInvalidationTest {
    @Test
    void clearsVisibleSongAndFolderScoresBeforeModeReload() {
        TestBar song = new TestBar();
        song.setScore(new ScoreData());
        TestDirectory folder = new TestDirectory();
        folder.setScore(new ScoreData());
        folder.getLamps()[5] = 2;
        folder.getRanks()[10] = 3;

        BarManager.clearPlayerScores(new Bar[]{song, folder});

        assertNull(song.getScore());
        assertNull(folder.getScore());
        assertArrayEquals(new int[11], folder.getLamps());
        assertArrayEquals(new int[28], folder.getRanks());
    }

    private static final class TestBar extends Bar {
        @Override
        public String getTitle() {
            return "song";
        }

        @Override
        public int getLamp(boolean isPlayer) {
            return getScore() == null ? 0 : getScore().getClear();
        }
    }

    private static final class TestDirectory extends DirectoryBar {
        private TestDirectory() {
            super(null);
        }

        @Override
        public String getTitle() {
            return "folder";
        }

        @Override
        public Bar[] getChildren() {
            return new Bar[0];
        }
    }
}
