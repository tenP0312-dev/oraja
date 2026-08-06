package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.CourseData.CourseDataConstraint;
import bms.player.beatoraja.CourseData.TrophyData;
import bms.player.beatoraja.ir.IRChartData;
import bms.player.beatoraja.ir.IRCourseData;
import bms.player.beatoraja.ir.IRTableData;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.system.RobustFile;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.SerializationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-player, last-good cache of class courses received from BMS-IR.
 *
 * The cache deliberately lives beside the player configuration rather than in
 * the user-owned {@code course/} directory.  It can therefore be enabled per
 * player and can never replace a hand-authored course file.
 */
public final class BMSIRDanCourseCache {
    static final String FILE_NAME = "bmsir_dan_courses.json";
    private static final Logger logger =
            LoggerFactory.getLogger(BMSIRDanCourseCache.class);

    private BMSIRDanCourseCache() {
    }

    public static boolean isBmsirPrimaryName(String irName) {
        return irName != null && "bms-ir".equals(irName.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Replaces the managed cache only when the successful response contains at
     * least one usable class course. Empty/non-Dan responses preserve the
     * previous last-good file.
     */
    public static boolean sync(
            String playerPath,
            String playerId,
            IRTableData[] tables
    ) {
        CourseData[] courses = extractClassCourses(tables);
        if (courses.length == 0) {
            logger.info("BMS-IR Dan local sync kept the last-good cache: no class courses received");
            return false;
        }
        Path path = cachePath(playerPath, playerId);
        try {
            Files.createDirectories(path.getParent());
            String serialized = configuredJson().prettyPrint(courses);
            RobustFile.write(path, serialized.getBytes(StandardCharsets.UTF_8));
            logger.info("BMS-IR Dan local sync saved {} courses to {}", courses.length, path);
            return true;
        } catch (IOException | SerializationException e) {
            logger.error(
                    "BMS-IR Dan local sync could not save {}: {}",
                    path,
                    e.getLocalizedMessage()
            );
            return false;
        }
    }

    public static CourseData[] read(String playerPath, String playerId) {
        Path path = cachePath(playerPath, playerId);
        if (!Files.isRegularFile(path)) {
            return CourseData.EMPTY;
        }
        try {
            return RobustFile.load(path, data -> parse(path, data));
        } catch (IOException e) {
            logger.error(
                    "BMS-IR Dan local cache could not be loaded from {}: {}",
                    path,
                    e.getLocalizedMessage()
            );
            return CourseData.EMPTY;
        }
    }

    static Path cachePath(String playerPath, String playerId) {
        return Paths.get(playerPath, playerId, FILE_NAME);
    }

    static CourseData[] extractClassCourses(IRTableData[] tables) {
        if (tables == null || tables.length == 0) {
            return CourseData.EMPTY;
        }
        Map<String, CourseData> deduped = new LinkedHashMap<>();
        for (IRTableData table : tables) {
            if (table == null || table.courses == null) {
                continue;
            }
            for (IRCourseData source : table.courses) {
                CourseData course = convert(table.name, source);
                if (course == null || !course.isClassCourse()) {
                    continue;
                }
                deduped.putIfAbsent(identity(course), course);
            }
        }
        return deduped.values().toArray(CourseData[]::new);
    }

    private static CourseData convert(String tableName, IRCourseData source) {
        if (source == null || source.charts == null || source.charts.length == 0) {
            return null;
        }
        CourseData course = new CourseData();
        course.setName(qualifiedName(tableName, source.name));
        List<SongData> songs = new ArrayList<>();
        for (IRChartData chart : source.charts) {
            if (chart == null) {
                return null;
            }
            SongData song = new SongData();
            song.setMd5(chart.md5);
            song.setSha256(chart.sha256);
            song.setTitle(chart.title);
            song.setSubtitle(chart.subtitle);
            song.setArtist(chart.artist);
            song.setSubartist(chart.subartist);
            song.setGenre(chart.genre);
            song.setUrl(chart.url);
            song.setAppendurl(chart.appendurl);
            if (chart.mode != null) {
                song.setMode(chart.mode.id);
            }
            songs.add(song);
        }
        course.setSong(songs.toArray(SongData[]::new));
        course.setConstraint(
                source.constraint == null
                        ? CourseDataConstraint.EMPTY
                        : Arrays.stream(source.constraint)
                                .filter(java.util.Objects::nonNull)
                                .toArray(CourseDataConstraint[]::new)
        );
        course.setTrophy(
                source.trophy == null
                        ? TrophyData.EMPTY
                        : Arrays.stream(source.trophy)
                                .filter(java.util.Objects::nonNull)
                                .map(item -> {
                                    TrophyData trophy = new TrophyData();
                                    trophy.setName(item.name);
                                    trophy.setMissrate(item.smissrate);
                                    trophy.setScorerate(item.scorerate);
                                    return trophy;
                                })
                                .toArray(TrophyData[]::new)
        );
        course.setRelease(true);
        return course.validate() ? course : null;
    }

    private static String qualifiedName(String tableName, String courseName) {
        String table = tableName == null ? "" : tableName.trim();
        String course = courseName == null ? "" : courseName.trim();
        if (course.isEmpty()) {
            return table.isEmpty() ? "BMS-IR 段位" : table;
        }
        if (table.isEmpty()
                || course.toLowerCase(Locale.ROOT).contains(table.toLowerCase(Locale.ROOT))) {
            return course;
        }
        return table + " " + course;
    }

    private static String identity(CourseData course) {
        // Course score identity is the ordered chart list plus constraints;
        // presentation names may differ between table revisions.
        StringBuilder result = new StringBuilder();
        for (SongData song : course.getSong()) {
            String sha256 = song.getSha256();
            String md5 = song.getMd5();
            result.append(
                    sha256 != null && !sha256.isBlank()
                            ? sha256.toLowerCase(Locale.ROOT)
                            : String.valueOf(md5).toLowerCase(Locale.ROOT)
            ).append('\n');
        }
        for (CourseDataConstraint constraint : course.getConstraint()) {
            result.append(constraint.name()).append('\n');
        }
        return result.toString();
    }

    private static CourseData[] parse(Path path, byte[] data) throws ParseException {
        try {
            CourseData[] parsed = configuredJson().fromJson(
                    CourseData[].class,
                    new String(data, StandardCharsets.UTF_8)
            );
            if (parsed == null || parsed.length == 0) {
                throw new SerializationException("empty course cache");
            }
            CourseData[] valid = Arrays.stream(parsed)
                    .filter(java.util.Objects::nonNull)
                    .filter(CourseData::validate)
                    .filter(CourseData::isClassCourse)
                    .toArray(CourseData[]::new);
            if (valid.length == 0) {
                throw new SerializationException("no valid class courses");
            }
            return valid;
        } catch (RuntimeException e) {
            ParseException failure = new ParseException(
                    "BMS-IR Dan cache parse failed - Path: "
                            + path
                            + ", Log: "
                            + e.getLocalizedMessage(),
                    0
            );
            failure.initCause(e);
            throw failure;
        }
    }

    private static Json configuredJson() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        json.setOutputType(JsonWriter.OutputType.json);
        json.setUsePrototypes(false);
        return json;
    }
}
