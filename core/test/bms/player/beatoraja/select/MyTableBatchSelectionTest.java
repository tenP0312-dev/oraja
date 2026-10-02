package bms.player.beatoraja.select;

import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.RandomCourseData;
import bms.player.beatoraja.select.bar.*;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyTableBatchSelectionTest {
    @Test void batchEditingConsumesSongsAndEveryCourseLaunchRoute() {
        Bar[] playable = {
            new SongBar(new SongData()),
            new GradeBar(new CourseData()),
            new RandomCourseBar(new RandomCourseData()),
            new ExecutableBar(new SongData[0], null, "Random song")
        };
        for (Bar bar : playable) {
            assertTrue(MusicSelector.blocksBatchPlaySelection(bar, true), bar.getClass().getSimpleName());
            assertFalse(MusicSelector.blocksBatchPlaySelection(bar, false), bar.getClass().getSimpleName());
        }
    }

    @Test void batchEditingRetainsFolderNavigationAndConfirmationActions() {
        DirectoryBar folder = new DirectoryBar(null) {
            @Override public String getTitle() { return "Folder"; }
            @Override public Bar[] getChildren() { return new Bar[0]; }
        };
        FunctionBar apply = new FunctionBar((selector, bar) -> { }, "Apply", FunctionBar.STYLE_FOLDER);
        assertFalse(MusicSelector.blocksBatchPlaySelection(folder, true));
        assertFalse(MusicSelector.blocksBatchPlaySelection(apply, true));
    }
}
