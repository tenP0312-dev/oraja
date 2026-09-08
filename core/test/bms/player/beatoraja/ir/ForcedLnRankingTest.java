package bms.player.beatoraja.ir;

import bms.model.*;
import bms.player.beatoraja.BMSIRLongNoteMode;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ForcedLnRankingTest {
    @TempDir Path directory;

    private SongData song(int authored, int selected, boolean force) throws Exception {
        Path path = directory.resolve("chart-" + authored + ".bms");
        Files.writeString(path, "#TITLE Ranking\n#BPM 120\n#WAV01 test.wav\n"
                + (authored < 0 ? "#00111:0101\n"
                : "#LNMODE " + authored + "\n#00151:0101\n"));
        BMSModel model = ChartDecoder.getDecoder(path).decode(new ChartInformation(path, selected, null));
        if (force) BMSIRLongNoteMode.apply(model);
        return new SongData(model, false);
    }

    private SongData catalog(SongData loaded) {
        SongData result = new SongData();
        result.setSha256(loaded.getSha256());
        result.setMd5(loaded.getMd5());
        result.setFeature(loaded.getFeature());
        result.setMode(loaded.getMode());
        return result;
    }

    @Test
    void catalogRequestsUseLnMetadataAndRestoreAuthoredFlagsWithoutMutation() throws Exception {
        for (int authored = -1; authored <= 3; authored++) {
            for (int selected = 0; selected <= 2; selected++) {
                SongData source = catalog(song(authored, selected, false));
                int features = source.getFeature();
                IRChartData on = IRChartData.forRanking(source, selected, true);
                assertEquals(0, on.lntype);
                assertFalse(on.hasCN);
                assertFalse(on.hasHCN);
                assertEquals(authored >= 0, on.hasLN);
                assertEquals(authored >= 0, on.hasUndefinedLN);
                IRChartData off = IRChartData.forRanking(source, selected, false);
                assertEquals(selected, off.lntype);
                assertEquals(authored == 2, off.hasCN);
                assertEquals(authored == 3, off.hasHCN);
                assertEquals(features, source.getFeature());
                assertEquals(source.getSha256(), on.sha256);
                assertEquals(source.getMd5(), on.md5);
            }
        }
    }

    @Test
    void convertedChartsSeparateCachesAndLoadedResultsReuseCatalogEntry() throws Exception {
        AtomicBoolean force = new AtomicBoolean();
        RankingDataCache cache = new RankingDataCache(force::get);
        for (int authored = -1; authored <= 3; authored++) {
            SongData source = catalog(song(authored, 0, false));
            SongData loaded = song(authored, 0, true);
            RankingData ordinary = new RankingData();
            force.set(false);
            cache.put(source, 0, ordinary);
            force.set(true);
            if (authored >= 2) assertNull(cache.get(source, 0));
            else assertSame(ordinary, cache.get(source, 0));
            RankingData forced = new RankingData();
            cache.put(source, 0, forced);
            assertSame(forced, cache.get(loaded, 0));
            force.set(false);
            assertSame(authored >= 2 ? ordinary : forced, cache.get(source, 0));
            force.set(true);
            assertSame(forced, cache.get(source, 0));
        }
    }

    @Test
    void undefinedNotesRetainModeSpecificCaches() throws Exception {
        SongData source = catalog(song(0, 2, false));
        AtomicBoolean force = new AtomicBoolean();
        RankingDataCache cache = new RankingDataCache(force::get);
        RankingData hcn = new RankingData();
        cache.put(source, 2, hcn);
        force.set(true);
        assertNull(cache.get(source, 0));
        cache.put(source, 0, new RankingData());
        force.set(false);
        assertSame(hcn, cache.get(source, 2));
    }

    @Test
    void bmsonMixedTypesUseTheSameEntryBeforeAndAfterDecoding() throws Exception {
        Path path = Files.writeString(directory.resolve("mixed.bmson"), """
                {"version":"1.0.0","info":{"title":"Mixed","artist":"test",
                "mode_hint":"beat-7k","init_bpm":120,"judge_rank":100,"total":100,
                "resolution":240,"ln_type":3},"lines":[],"bpm_events":[],"stop_events":[],
                "sound_channels":[{"name":"test.wav","notes":[
                {"x":1,"y":0,"l":240,"t":1},{"x":2,"y":0,"l":240,"t":2},
                {"x":3,"y":0,"l":240,"t":3},{"x":4,"y":0,"l":0}]}]}
                """);
        BMSModel model = ChartDecoder.getDecoder(path).decode(new ChartInformation(path, 0, null));
        SongData source = catalog(new SongData(model, false));
        IRChartData before = IRChartData.forRanking(source, 2, true);
        BMSIRLongNoteMode.apply(model);
        SongData loaded = new SongData(model, false);
        IRChartData after = IRChartData.forRanking(loaded, 0, true);
        assertFalse(before.hasCN);
        assertFalse(before.hasHCN);
        assertEquals(before.lntype, after.lntype);
        assertEquals(before.sha256, after.sha256);
        RankingDataCache cache = new RankingDataCache(() -> true);
        RankingData ranking = new RankingData();
        cache.put(source, 0, ranking);
        assertSame(ranking, cache.get(loaded, 0));
    }

    @Test
    void mixedCourseSeparatesCacheAndUsesForcedMetadataForEveryChart() throws Exception {
        CourseData course = new CourseData();
        course.setSong(new SongData[]{catalog(song(1, 0, false)), catalog(song(2, 0, false))});
        AtomicBoolean force = new AtomicBoolean();
        RankingDataCache cache = new RankingDataCache(force::get);
        RankingData ordinary = new RankingData();
        cache.put(course, 0, ordinary);
        force.set(true);
        assertNull(cache.get(course, 0));
        RankingData forced = new RankingData();
        cache.put(course, 0, forced);
        assertSame(forced, cache.get(course, 0));
        IRCourseData request = new IRCourseData(course, 2, true);
        assertEquals(0, request.lntype);
        for (IRChartData chart : request.charts) {
            assertEquals(0, chart.lntype);
            assertFalse(chart.hasCN);
            assertFalse(chart.hasHCN);
        }
        force.set(false);
        assertSame(ordinary, cache.get(course, 0));
    }
}
