package bms.player.beatoraja.ir;

import bms.model.*;
import bms.player.beatoraja.*;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class IRLongNoteContractTest {
    @TempDir Path directory;

    private SongData song(int authored, int selected) throws Exception {
        Path path = Files.writeString(directory.resolve("chart" + authored + ".bms"),
                "#TITLE Contract\n#BPM 120\n#WAV01 test.wav\n#LNMODE " + authored + "\n#00112:0101\n#00151:0101\n");
        return new SongData(ChartDecoder.getDecoder(path).decode(new ChartInformation(path, selected, null)), false);
    }

    @Test void catalogAndLoadedRequestsUseActualInterpretationWithoutMutation() throws Exception {
        for (int authored = 0; authored <= 3; authored++) {
            SongData loaded = song(authored, 2);
            SongData catalog = new SongData();
            catalog.setPath(loaded.getPath()); catalog.setSha256(loaded.getSha256());
            catalog.setMd5(loaded.getMd5()); catalog.setFeature(loaded.getFeature()); catalog.setNotes(loaded.getNotes());
            for (int selected = 0; selected <= 2; selected++) {
                IRChartData on = IRChartData.forRanking(catalog, selected, true);
                assertEquals(0, on.bmsirRankingLnType);
                assertEquals(3, on.bmsirRankingNotes);
                IRChartData off = IRChartData.forRanking(catalog, selected, false);
                int expectedType = authored == 0 ? selected : authored - 1;
                assertEquals(expectedType, off.bmsirRankingLnType);
                assertEquals(expectedType == 0 ? 3 : 4, off.bmsirRankingNotes);
                assertEquals(off.bmsirRankingNotes, IRChartData.forRanking(loaded, selected, false).bmsirRankingNotes);
            }
            assertEquals(2, loaded.getBMSModel().getLntype());
            assertEquals(loaded.getNotes(), catalog.getNotes());
        }
    }

    @Test void liveTypeSurvivesLocalStorageModeAndIrRoundTrip() throws Exception {
        BMSModel model = song(2, 0).getBMSModel();
        IRLongNoteContract actual = IRLongNoteContract.fromModel(model, 0, false);
        ScoreData play = new ScoreData(); play.setMode(0); play.setNotes(actual.notes());
        play.setBmsirEffectiveLnType(actual.lntype());
        IRScoreData wire = new IRScoreData(play);
        assertEquals(1, wire.lntype); assertEquals(0, wire.bmsirScoreMode);
        assertEquals(1, wire.convertToScoreData().getBmsirEffectiveLnType());
        play.setMode(32121); play.setBmsirEffectiveLnType(2);
        IRScoreData course = new IRScoreData(play);
        assertEquals(2, course.lntype); assertEquals(32121, course.bmsirScoreMode);
        ScoreData older = new ScoreData(); older.setMode(0); older.setNotes(4);
        assertEquals(-1, older.getBmsirEffectiveLnType());
        assertEquals(0, new IRScoreData(older).lntype);
    }

    @Test void retryQueueRetainsPlayTypeNotesAndDateAfterSourceObjectsChange() throws Exception {
        SongData chart = song(2, 0);
        ScoreData play = new ScoreData(); play.setMode(0); play.setBmsirEffectiveLnType(1);
        play.setNotes(4); play.setDate(100);
        play.setBmsirFloatingHispeedUsed(true);
        java.util.concurrent.atomic.AtomicReference<IRScoreData> sent = new java.util.concurrent.atomic.AtomicReference<>();
        IRConnection ir = (IRConnection)java.lang.reflect.Proxy.newProxyInstance(IRConnection.class.getClassLoader(),
                new Class<?>[]{IRConnection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendPlayData")) {
                        sent.set((IRScoreData)args[1]);
                        return new IRResponse<Object>() {
                            public boolean isSucceeded() { return true; }
                            public String getMessage() { return "fixture"; }
                            public Object getData() { return null; }
                        };
                    }
                    return null;
                });
        MainController.IRSendStatus queue = new MainController.IRSendStatus(ir, chart, play);
        play.setBmsirEffectiveLnType(0); play.setNotes(3); play.setDate(200);
        play.setBmsirFloatingHispeedUsed(false);
        chart.setNotes(3);
        assertTrue(queue.send());
        assertEquals(1, sent.get().lntype); assertEquals(4, sent.get().notes); assertEquals(100, sent.get().date);
        assertTrue(sent.get().bmsirFloatingHispeedUsed);
    }

    @Test void invalidRowsNeverReplaceACompatibleRanking() {
        RankingData ranking = new RankingData();
        ranking.setContract(new IRLongNoteContract(0, 1625));
        ScoreData good = new ScoreData(); good.setPlayer("fixture"); good.setNotes(1625); good.setMode(0);
        ranking.updateScore(new IRScoreData[]{new IRScoreData(good)}, null);
        ScoreData wrong = new ScoreData(); wrong.setPlayer("fixture"); wrong.setNotes(1640); wrong.setMode(0);
        assertFalse(RankingData.matchesContract(new IRScoreData[]{new IRScoreData(wrong)}, 0, 1625));
        ranking.updateScore(new IRScoreData[]{new IRScoreData(wrong)}, null);
        assertEquals(1625, ranking.getScore(0).notes);
        ranking.restoreCachedScores(new IRScoreData[]{new IRScoreData(wrong)});
        assertEquals(1625, ranking.getScore(0).notes);
    }

    @Test void changedOrMissingCatalogFileIsUnresolvedRatherThanGuessed() throws Exception {
        SongData loaded = song(2, 0), catalog = new SongData();
        catalog.setPath(loaded.getPath()); catalog.setSha256(loaded.getSha256()); catalog.setNotes(4);
        Files.writeString(Path.of(loaded.getPath()), "#TITLE Different\n#BPM 120\n#00111:01\n");
        assertEquals(-1, IRChartData.forRanking(catalog, 0, true).bmsirRankingNotes);
        Files.delete(Path.of(loaded.getPath()));
        assertEquals(-1, IRChartData.forRanking(catalog, 0, true).bmsirRankingNotes);
    }

    @Test void oldStoredScoresStayUnknownAndNewBestsRetainTheirLiveType() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path db = directory.resolve("score.db");
        try (var con = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db)) {
            con.createStatement().execute("CREATE TABLE score(sha256 TEXT, mode INTEGER, notes INTEGER)");
            con.createStatement().execute("INSERT INTO score VALUES('" + "a".repeat(64) + "',0,1640)");
        }
        ScoreDatabaseAccessor accessor = new ScoreDatabaseAccessor(db.toString()); accessor.createTable();
        try (var con = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db);
             var row = con.createStatement().executeQuery("SELECT mode,notes,bmsirEffectiveLnType FROM score")) {
            assertTrue(row.next()); assertEquals(0, row.getInt(1)); assertEquals(1640, row.getInt(2)); assertEquals(-1, row.getInt(3));
        }
        ScoreData score = new ScoreData(); score.setSha256("b".repeat(64)); score.setMode(0);
        score.setNotes(4); score.setBmsirEffectiveLnType(1);
        accessor.setScoreData(score);
        assertEquals(1, accessor.getScoreData(score.getSha256(), 0).getBmsirEffectiveLnType());
    }

    @Test void mixedBmsonAndCourseUseAuthoredTypesUntilForced() throws Exception {
        Path path = Files.writeString(directory.resolve("mixed.bmson"), """
            {"version":"1.0.0","info":{"title":"Mixed","artist":"fixture","mode_hint":"beat-7k",
            "init_bpm":120,"judge_rank":100,"total":100,"resolution":240,"ln_type":3},
            "lines":[],"bpm_events":[],"stop_events":[],"sound_channels":[{"name":"test.wav","notes":[
            {"x":1,"y":0,"l":240,"t":1},{"x":2,"y":0,"l":240,"t":2},{"x":3,"y":0,"l":240,"t":3},
            {"x":4,"y":0,"l":0}]}]}
            """);
        SongData mixed = new SongData(ChartDecoder.getDecoder(path).decode(new ChartInformation(path, 0, null)), false);
        IRChartData off = IRChartData.forRanking(mixed, 0, false), on = IRChartData.forRanking(mixed, 2, true);
        assertEquals(2, off.bmsirRankingLnType); assertEquals(6, off.bmsirRankingNotes);
        assertEquals(0, on.bmsirRankingLnType); assertEquals(4, on.bmsirRankingNotes);
        CourseData course = new CourseData(); course.setSong(new SongData[]{mixed, song(2, 0)});
        assertEquals(7, IRCourseData.forRanking(course, 0, true).bmsirRankingNotes);
        assertEquals(10, IRCourseData.forRanking(course, 0, false).bmsirRankingNotes);
    }
}
