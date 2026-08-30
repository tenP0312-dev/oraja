package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRTableKeyModeFilterTest {
    @Test
    void sevenKeySelectionHidesAFullyResolvedFourteenKeyTable() {
        var modes = BMSIRTableKeyModeFilter.analyze(table(
                songs(Mode.BEAT_14K),
                CourseData.EMPTY
        ));

        assertFalse(modes.isVisible(Mode.BEAT_7K));
        assertTrue(modes.isVisible(Mode.BEAT_14K));
    }

    @Test
    void mixedTableRemainsVisibleForEveryContainedMode() {
        var modes = BMSIRTableKeyModeFilter.analyze(table(
                songs(Mode.BEAT_7K, Mode.BEAT_14K),
                CourseData.EMPTY
        ));

        assertTrue(modes.isVisible(Mode.BEAT_7K));
        assertTrue(modes.isVisible(Mode.BEAT_14K));
        assertFalse(modes.isVisible(Mode.POPN_9K));
    }

    @Test
    void allModeKeepsEveryResolvedTableVisible() {
        var modes = BMSIRTableKeyModeFilter.analyze(table(
                songs(Mode.BEAT_14K),
                CourseData.EMPTY
        ));

        assertTrue(modes.isVisible(null));
    }

    @Test
    void courseOnlyTableUsesItsChartModes() {
        CourseData course = new CourseData();
        course.setName("14K course");
        course.setSong(songs(Mode.BEAT_14K));
        var modes = BMSIRTableKeyModeFilter.analyze(table(
                SongData.EMPTY,
                new CourseData[]{course}
        ));

        assertFalse(modes.isVisible(Mode.BEAT_7K));
        assertTrue(modes.isVisible(Mode.BEAT_14K));
    }

    @Test
    void unresolvedEntryKeepsTheWholeTableReachable() {
        SongData unresolved = new SongData();
        unresolved.setMode(0);
        var modes = BMSIRTableKeyModeFilter.analyze(table(
                new SongData[]{song(Mode.BEAT_14K), unresolved},
                CourseData.EMPTY
        ));

        assertTrue(modes.isVisible(Mode.BEAT_7K));
        assertTrue(modes.isVisible(Mode.BEAT_14K));
    }

    private static TableData table(SongData[] folderSongs, CourseData[] courses) {
        TableData.TableFolder folder = new TableData.TableFolder();
        folder.setName("level");
        folder.setSong(folderSongs);
        TableData table = new TableData();
        table.setName("table");
        table.setFolder(folderSongs.length == 0
                ? TableData.TableFolder.EMPTY
                : new TableData.TableFolder[]{folder});
        table.setCourse(courses);
        return table;
    }

    private static SongData[] songs(Mode... modes) {
        SongData[] songs = new SongData[modes.length];
        for (int index = 0; index < modes.length; index++) {
            songs[index] = song(modes[index]);
        }
        return songs;
    }

    private static SongData song(Mode mode) {
        SongData song = new SongData();
        song.setMode(mode.id);
        return song;
    }
}
