package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.CourseData.CourseDataConstraint;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.ir.IRTableData;
import bms.player.beatoraja.song.SongData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRDanCourseCacheTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void successfulSyncStoresOnlyClassCoursesAndQualifiesTheirNames() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("player1"));
        IRTableData table = table(
                "GENOSIDE 2018",
                course("発狂九段", CourseDataConstraint.MIRROR, "a"),
                course("同一構成の別名", CourseDataConstraint.MIRROR, "a"),
                course("Score Attack", CourseDataConstraint.GAUGE_LR2, "b")
        );

        assertTrue(BMSIRDanCourseCache.sync(
                temporaryDirectory.toString(),
                "player1",
                new IRTableData[]{table}
        ));

        CourseData[] cached = BMSIRDanCourseCache.read(
                temporaryDirectory.toString(),
                "player1"
        );
        assertEquals(1, cached.length);
        assertEquals("GENOSIDE 2018 発狂九段", cached[0].getName());
        assertTrue(cached[0].isClassCourse());
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", cached[0].getSong()[0].getMd5());
    }

    @Test
    void emptyOrNonDanResponsePreservesLastGoodCache() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("player1"));
        assertTrue(BMSIRDanCourseCache.sync(
                temporaryDirectory.toString(),
                "player1",
                new IRTableData[]{table(
                        "Solomon",
                        course("Solomon 段位", CourseDataConstraint.RANDOM, "c")
                )}
        ));
        byte[] before = Files.readAllBytes(BMSIRDanCourseCache.cachePath(
                temporaryDirectory.toString(),
                "player1"
        ));

        assertFalse(BMSIRDanCourseCache.sync(
                temporaryDirectory.toString(),
                "player1",
                new IRTableData[]{table(
                        "Score Attack",
                        course("Only score", CourseDataConstraint.GAUGE_LR2, "d")
                )}
        ));
        assertEquals(
                new String(before, StandardCharsets.UTF_8),
                Files.readString(BMSIRDanCourseCache.cachePath(
                        temporaryDirectory.toString(),
                        "player1"
                ))
        );
        assertEquals(1, BMSIRDanCourseCache.read(
                temporaryDirectory.toString(),
                "player1"
        ).length);
    }

    @Test
    void corruptPrimaryCacheFallsBackToRobustBackup() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("player1"));
        assertTrue(BMSIRDanCourseCache.sync(
                temporaryDirectory.toString(),
                "player1",
                new IRTableData[]{table(
                        "Dystopia",
                        course("Dystopia 段位", CourseDataConstraint.CLASS, "e")
                )}
        ));
        Files.writeString(
                BMSIRDanCourseCache.cachePath(temporaryDirectory.toString(), "player1"),
                "{broken",
                StandardCharsets.UTF_8
        );

        CourseData[] restored = BMSIRDanCourseCache.read(
                temporaryDirectory.toString(),
                "player1"
        );
        assertEquals(1, restored.length);
        assertEquals("Dystopia 段位", restored[0].getName());
    }

    @Test
    void cacheIsPlayerLocalAndPrimaryNameMatchIsExact() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("player1"));
        Files.createDirectories(temporaryDirectory.resolve("player2"));
        assertTrue(BMSIRDanCourseCache.sync(
                temporaryDirectory.toString(),
                "player1",
                new IRTableData[]{table(
                        "BMS-IR",
                        course("段位", CourseDataConstraint.CLASS, "f")
                )}
        ));
        assertEquals(0, BMSIRDanCourseCache.read(
                temporaryDirectory.toString(),
                "player2"
        ).length);
        assertTrue(BMSIRDanCourseCache.isBmsirPrimaryName(" BMS-IR "));
        assertFalse(BMSIRDanCourseCache.isBmsirPrimaryName("Other BMS"));
    }

    private IRTableData table(String name, CourseData... courses) {
        TableData table = new TableData();
        table.setName(name);
        table.setFolder(TableData.TableFolder.EMPTY);
        table.setCourse(courses);
        return new IRTableData(table);
    }

    private CourseData course(
            String name,
            CourseDataConstraint constraint,
            String hashCharacter
    ) {
        SongData song = new SongData();
        song.setTitle(name + " chart");
        song.setMd5(hashCharacter.repeat(32));
        CourseData course = new CourseData();
        course.setName(name);
        course.setSong(new SongData[]{song});
        course.setConstraint(new CourseDataConstraint[]{constraint});
        return course;
    }
}
