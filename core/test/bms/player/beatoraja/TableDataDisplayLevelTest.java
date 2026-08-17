package bms.player.beatoraja;

import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TableDataDisplayLevelTest {
    @Test
    void extractsTheFirstAsciiIntegerFromTableLabels() {
        assertEquals(6, TableData.parseDisplayLevel("発狂6"));
        assertEquals(6, TableData.parseDisplayLevel("★06"));
        assertEquals(12, TableData.parseDisplayLevel("sl12"));
        assertEquals(1, TableData.parseDisplayLevel("01.1"));
        assertEquals(0, TableData.parseDisplayLevel("st0"));
    }

    @Test
    void rejectsLabelsWithoutARepresentableInteger() {
        assertNull(TableData.parseDisplayLevel(null));
        assertNull(TableData.parseDisplayLevel(""));
        assertNull(TableData.parseDisplayLevel("全曲"));
        assertNull(TableData.parseDisplayLevel("99999999999999999999"));
    }

    @Test
    void persistsTableEntryDisplayLevelInTheTableCache(@TempDir Path tempDir) {
        SongData song = new SongData();
        song.setTitle("Chart");
        song.setSha256("a".repeat(64));
        song.setTableLevel(6);
        TableData.TableFolder folder = new TableData.TableFolder();
        folder.setName("発狂6");
        folder.setSong(new SongData[]{song});
        TableData table = new TableData();
        table.setName("Table");
        table.setFolder(new TableData.TableFolder[]{folder});
        table.setCourse(CourseData.EMPTY);
        Path cache = tempDir.resolve("table.json");

        TableData.write(cache, table);
        TableData restored = TableData.read(cache);

        assertEquals(6, restored.getFolder()[0].getSong()[0].getTableLevel());
    }
}
