package bms.player.beatoraja.nonstop;

import bms.player.beatoraja.TableData;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MixDifficultyTest {

    @Test
    void normalSitsBelowInsaneOnOneAxis() {
        assertEquals(12, MixDifficulty.fromTable("☆12", ""));
        assertEquals(13, MixDifficulty.fromTable("★1", ""));
        assertEquals(37, MixDifficulty.fromTable("★25", ""));
        assertTrue(MixDifficulty.fromTable("★0", "") > 12 && MixDifficulty.fromTable("★0", "") < 13);
        assertTrue(MixDifficulty.fromTable("★???", "") > 12);
        assertTrue(MixDifficulty.fromTable("★★3", "") > 37);
        // second normal / insane tables
        assertEquals(5, MixDifficulty.fromTable("▽5", ""));
        assertEquals(14, MixDifficulty.fromTable("▼2", ""));
        // the symbol may come from the table's tag
        assertEquals(15, MixDifficulty.fromTable("3", "★"));
        // tables of other kinds are not used
        assertTrue(Double.isNaN(MixDifficulty.fromTable("sl3", "sl")));
        assertEquals("☆12", MixDifficulty.label(12));
        assertEquals("★1", MixDifficulty.label(13));
    }

    @Test
    void scaleRangesMapToTheAxis() {
        MixDifficulty.Scale normal = MixDifficulty.Scale.NORMAL;
        MixDifficulty.Scale insane = MixDifficulty.Scale.INSANE;
        assertEquals(Double.NEGATIVE_INFINITY, normal.lower(1));
        assertTrue(normal.upper(12) < insane.lower(1));
        assertTrue(insane.lower(1) < MixDifficulty.fromTable("★0", ""));
        assertEquals(Double.POSITIVE_INFINITY, insane.upper(25));
        // single steps: ★3–★3 holds ★3 only
        double three = MixDifficulty.fromTable("★3", "");
        assertTrue(insane.lower(3) <= three && three <= insane.upper(3));
        assertFalse(insane.lower(3) <= three + 1 && three + 1 <= insane.upper(3));
    }

    @Test
    void tablePlaceWinsAndInsaneChartsWithTheSamePlaylevelAreSeparated() {
        TableData table = table("発狂", "★", folder("★20", "a".repeat(64)));
        Map<String, Double> places = MixDifficulty.index(new TableData[] {table});
        assertEquals(32, MixDifficulty.lookup(places, "A".repeat(64), ""));
        assertTrue(Double.isNaN(MixDifficulty.lookup(places, "b".repeat(64), "")));

        // off-table charts: average and peak density, not #PLAYLEVEL
        double light = MixDifficulty.estimate(900, 120_000L, 0, 12);
        double heavy = MixDifficulty.estimate(2400, 120_000L, 0, 12);
        assertTrue(light <= 12, "7.5 notes/s is a normal chart: " + light);
        assertTrue(heavy > 25, "20 notes/s is high insane: " + heavy);
        double spiky = MixDifficulty.estimate(1500, 120_000L, 40, 12);
        double even = MixDifficulty.estimate(1500, 120_000L, 18, 12);
        assertTrue(spiky > even);
        // nothing known: #PLAYLEVEL
        assertEquals(7, MixDifficulty.estimate(0, 0, 0, 7));
    }

    @Test
    void starTableBeatsSecondaryTables() {
        TableData second = table("第2発狂", "▼", folder("▼1", "c".repeat(64)));
        TableData main = table("発狂", "★", folder("★10", "c".repeat(64)));
        Map<String, Double> places = MixDifficulty.index(new TableData[] {second, main});
        assertEquals(22, MixDifficulty.lookup(places, "c".repeat(64), null));
    }

    private static TableData table(String name, String tag, TableData.TableFolder folder) {
        TableData table = new TableData();
        table.setName(name);
        table.setTag(tag);
        table.setFolder(new TableData.TableFolder[] {folder});
        return table;
    }

    private static TableData.TableFolder folder(String name, String sha256) {
        TableData.TableFolder folder = new TableData.TableFolder();
        folder.setName(name);
        SongData song = new SongData();
        song.setSha256(sha256);
        folder.setSong(new SongData[] {song});
        return folder;
    }
}
