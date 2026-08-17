package bms.player.beatoraja.select;

import bms.model.Mode;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.ir.IRChartData;
import bms.player.beatoraja.ir.IRCourseData;
import bms.player.beatoraja.ir.IRTableData;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarManagerPrimaryIrTableTest {
    @Test
    void replacesOnlyManagedPrimaryIrBlockAtItsOriginalPosition() {
        Object local = new Object();
        Object oldRecommend = new Object();
        Object myDifficulty = new Object();
        Object oldRival = new Object();
        Object favorite = new Object();
        Object newRecommend = new Object();
        Object newRival = new Object();
        Set<Object> managed = Collections.newSetFromMap(new IdentityHashMap<>());
        managed.add(oldRecommend);
        managed.add(oldRival);

        List<Object> result = BarManager.replaceManagedTables(
                List.of(local, oldRecommend, myDifficulty, oldRival, favorite),
                managed,
                List.of(newRecommend, newRival)
        );

        assertEquals(5, result.size());
        assertSame(local, result.get(0));
        assertSame(newRecommend, result.get(1));
        assertSame(newRival, result.get(2));
        assertSame(myDifficulty, result.get(3));
        assertSame(favorite, result.get(4));
    }

    @Test
    void automaticRefreshWaitsAtRootWithoutDelayingManualRefresh() {
        assertFalse(BarManager.canApplyPrimaryIrTables(false, 1));
        assertTrue(BarManager.canApplyPrimaryIrTables(false, 0));
        assertTrue(BarManager.canApplyPrimaryIrTables(true, 2));
    }

    @Test
    void convertsFreshIrFoldersAndCoursesWithoutAStoredTableUrl() {
        SongData song = new SongData();
        song.setSha256("sha256");
        song.setMd5("md5");
        song.setTitle("title");
        song.setSubtitle("subtitle");
        song.setArtist("artist");
        song.setSubartist("subartist");
        song.setGenre("genre");
        song.setMode(Mode.BEAT_14K.id);
        IRChartData chart = new IRChartData(song);
        CourseData course = new CourseData();
        course.setName("course");
        course.setSong(new SongData[]{song});
        course.setConstraint(new CourseData.CourseDataConstraint[]{
                CourseData.CourseDataConstraint.CLASS
        });
        IRTableData source = new IRTableData(
                "BMS-IR Rival",
                new IRTableData.IRTableFolder[]{
                        new IRTableData.IRTableFolder("level", new IRChartData[]{chart})
                },
                new IRCourseData[]{new IRCourseData(course)}
        );

        TableData converted = BarManager.convertIrTable(source);

        assertEquals("BMS-IR Rival", converted.getName());
        assertEquals("", converted.getUrl());
        assertEquals("level", converted.getFolder()[0].getName());
        assertEquals("sha256", converted.getFolder()[0].getSong()[0].getSha256());
        assertNull(converted.getFolder()[0].getSong()[0].getTableLevel());
        assertEquals("subtitle", converted.getFolder()[0].getSong()[0].getSubtitle());
        assertEquals("subartist", converted.getFolder()[0].getSong()[0].getSubartist());
        assertEquals(Mode.BEAT_14K, chart.mode);
        assertEquals(Mode.BEAT_14K.id, converted.getFolder()[0].getSong()[0].getMode());
        assertEquals("course", converted.getCourse()[0].getName());
        assertEquals(
                CourseData.CourseDataConstraint.CLASS,
                converted.getCourse()[0].getConstraint()[0]
        );
    }

    @Test
    void rejectsEmptyOrInvalidRefreshBeforeReplacingLastGoodTables() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BarManager.validateIrTableResponse(new IRTableData[0], true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> BarManager.validateIrTableResponse(
                        new IRTableData[]{new IRTableData("empty", null, null)},
                        true
                )
        );
    }
}
