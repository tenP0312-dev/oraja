package bms.player.beatoraja;

import bms.model.*;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacPlayContext;
import bms.player.beatoraja.select.ScoreDataCache;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BMSIRSelectiveLnScoreTest {
    @TempDir Path directory;
    private static final CourseData.CourseDataConstraint[] CONSTRAINTS = {
            CourseData.CourseDataConstraint.NO_SPEED, CourseData.CourseDataConstraint.GAUGE_7KEYS};

    @Test
    void sharedAndConvertedChartsRoundTripThroughModelCatalogBulkCacheAndDeletion() throws Exception {
        for (int authored = -1; authored <= 4; authored++) {
            for (int selected = 0; selected <= 2; selected++) {
                var fixture = fixture("case" + authored + "-" + selected, selected);
                Path chart = chart(authored);
                BMSModel raw = fixture.resource.loadBMSModel(chart, selected);
                SongData song = catalog(raw);
                boolean changed = authored >= 2;
                // Seed LN and the selected OFF mode separately for undefined LN.
                BMSModel ln = fixture.resource.loadBMSModel(chart, 0);
                fixture.data.writeScoreData(score(5, 1), ln, 0, true);
                if (selected != 0 && authored == 0) fixture.data.writeScoreData(score(7, 2), raw, selected, true);
                ScoreDataCache cache = cache(fixture);
                int offClear = authored == 0 && selected != 0 ? 7 : 5;
                assertEquals(offClear, cache.readScoreData(song, selected).getClear());

                fixture.player.setBmsirForceLn(true);
                BMSModel forced = fixture.resource.loadBMSModel(chart, selected);
                assertEquals(changed, BMSIRLongNoteMode.separatesScore(forced));
                assertEquals(changed, BMSIRLongNoteMode.separatesScore(song));
                ScoreData before = fixture.data.readScoreData(forced, 0);
                if (changed) {
                    assertNull(before, "converted CN/HCN must not borrow an ordinary PB");
                    assertNull(cache.readScoreData(song, 0));
                } else {
                    assertEquals(5, before.getClear());
                    assertEquals(5, cache.readScoreData(song, 0).getClear());
                }

                ScoreData live = score(6, 2);
                fixture.data.writeScoreData(live, forced, 0, true);
                assertEquals(authored >= 1 ? 1 : 0, live.getBmsirLongNotePolicy(),
                        "the existing live IR wire marker must remain unchanged");
                assertEquals(changed ? 1 : 0, fixture.data.readScoreData(forced, 0).getBmsirLongNotePolicy());
                cache.update(song, 0);
                assertEquals(6, cache.readScoreData(song, 0).getClear());
                Map<SongData, ScoreData> bulk = new IdentityHashMap<>();
                fixture.data.readScoreDatas(bulk::put, new SongData[]{song}, 0);
                assertEquals(6, bulk.get(song).getClear());
                assertEquals(6, fixture.data.readScoreData(new SongData(forced, false), 0).getClear());

                fixture.player.setBmsirForceLn(false);
                int expectedOff = changed || authored == 0 && selected != 0 ? offClear : 6;
                assertEquals(expectedOff, cache.readScoreData(song, selected).getClear());
                assertEquals(expectedOff, fixture.data.readScoreData(raw, selected).getClear());
                fixture.player.setBmsirForceLn(true);
                fixture.data.deleteScoreData(song, 0);
                assertNull(fixture.data.readScoreData(forced, 0));
                fixture.player.setBmsirForceLn(false);
                if (changed || authored == 0 && selected != 0) {
                    assertEquals(offClear, fixture.data.readScoreData(raw, selected).getClear());
                } else {
                    assertNull(fixture.data.readScoreData(raw, selected));
                }
            }
        }
    }

    @Test
    void mixedBulkReadsUseEachPoolAndRetainExistingForcedBestsWithoutMigratingRows() throws Exception {
        var f = fixture("bulk", 2);
        List<SongData> songs = new ArrayList<>();
        for (int authored = -1; authored <= 4; authored++) {
            BMSModel raw = f.resource.loadBMSModel(chart(authored), 0);
            songs.add(catalog(raw));
            f.data.writeScoreData(score(5, 1), raw, 0, true);
            // Existing releases wrote even unchanged charts into this DB.
            var forcedDb = new ScoreDatabaseAccessor(f.root.resolve("bmsir_forced_ln.db").toString());
            ScoreData previous = score(8, 2);
            previous.setSha256(raw.getSHA256());
            previous.setBmsirLongNotePolicy(authored >= 1 ? 1 : 0);
            forcedDb.setScoreData(previous);
        }
        f.player.setBmsirForceLn(true);
        Map<SongData, ScoreData> bulk = new IdentityHashMap<>();
        f.data.readScoreDatas(bulk::put, songs.toArray(SongData[]::new), 0);
        for (SongData song : songs) {
            int expected = BMSIRLongNoteMode.separatesScore(song) ? 8 : 5;
            assertEquals(expected, bulk.get(song).getClear());
            assertEquals(expected, f.data.readScoreData(song, 0).getClear());
        }
        assertEquals(6, count(f.root.resolve("score.db"), "score"));
        assertEquals(6, count(f.root.resolve("bmsir_forced_ln.db"), "score"));
    }

    @Test
    void coursesShareOnlyWhenEveryChartIsUnchangedAndDeleteOnlyTheSelectedPool() throws Exception {
        for (boolean converted : new boolean[]{false, true}) {
            var f = fixture("course" + converted, 2);
            Path[] charts = {chart(-1), chart(1), chart(converted ? 3 : 0)};
            BMSModel[] raw = new BMSModel[charts.length], forced = new BMSModel[charts.length];
            SongData[] songs = new SongData[charts.length];
            for (int i = 0; i < charts.length; i++) {
                raw[i] = f.resource.loadBMSModel(charts[i], 0);
                songs[i] = catalog(raw[i]);
            }
            for (int option = 0; option < 3; option++) f.data.writeScoreData(score(5, 1), raw, 0, option, CONSTRAINTS, true);
            f.player.setBmsirForceLn(true);
            for (int i = 0; i < charts.length; i++) forced[i] = f.resource.loadBMSModel(charts[i], 0);
            for (int option = 0; option < 3; option++) {
                ScoreData before = f.data.readScoreData(forced, 0, option, CONSTRAINTS);
                if (converted) assertNull(before); else assertEquals(5, before.getClear());
                f.data.writeScoreData(score(6, 2), forced, 0, option, CONSTRAINTS, true);
                assertEquals(6, f.data.readScoreData(songs, 0, option, CONSTRAINTS).getClear());
                assertEquals(6, f.data.readScoreData(forced, 0, option, CONSTRAINTS).getClear());
            }
            f.data.deleteScoreData(songs, 0, CONSTRAINTS);
            for (int option = 0; option < 3; option++) assertNull(f.data.readScoreData(forced, 0, option, CONSTRAINTS));
            f.player.setBmsirForceLn(false);
            for (int option = 0; option < 3; option++) {
                ScoreData remaining = f.data.readScoreData(raw, 0, option, CONSTRAINTS);
                if (converted) assertEquals(5, remaining.getClear()); else assertNull(remaining);
            }
        }
    }

    @Test
    void maniacSingleAndBulkReadsAgreeWithTheModelStoragePool() throws Exception {
        var f = fixture("maniac", 0);
        List<SongData> songs = new ArrayList<>();
        for (int authored = -1; authored <= 3; authored++) {
            Path chart = chart(authored);
            f.player.setBmsirForceLn(false);
            BMSModel raw = f.resource.loadBMSModel(chart, 0);
            SongData song = catalog(raw);
            songs.add(song);
            String hash = "special-" + raw.getSHA256();
            raw.getValues().put(BMSIRManiacPlayContext.MODEL_STORAGE_HASH, hash);
            f.data.writeScoreData(score(5, 1), raw, 0, true);
            f.player.setBmsirForceLn(true);
            BMSModel forced = f.resource.loadBMSModel(chart, 0);
            forced.getValues().put(BMSIRManiacPlayContext.MODEL_STORAGE_HASH, hash);
            if (authored >= 2) assertNull(f.data.readManiacScoreData(song, hash, 0));
            else assertEquals(5, f.data.readManiacScoreData(song, hash, 0).getClear());
            f.data.writeScoreData(score(6, 2), forced, 0, true);
            assertEquals(6, f.data.readManiacScoreData(song, hash, 0).getClear());
            assertEquals(6, f.data.readScoreData(forced, 0).getClear());
            assertNull(f.data.readScoreData(song, 0), "MANIAC must not enter ordinary records");
        }
        Map<SongData, ScoreData> bulk = new IdentityHashMap<>();
        f.data.readManiacScoreDatas(bulk::put, songs.toArray(SongData[]::new), 0, s -> "special-" + s.getSha256());
        for (SongData song : songs) assertEquals(6, bulk.get(song).getClear());
        f.player.setBmsirForceLn(false);
        f.data.readManiacScoreDatas(bulk::put, songs.toArray(SongData[]::new), 0, s -> "special-" + s.getSha256());
        for (SongData song : songs) assertEquals(BMSIRLongNoteMode.separatesScore(song) ? 5 : 6, bulk.get(song).getClear());
    }

    private Fixture fixture(String name, int selected) throws Exception {
        Config config = new Config();
        config.setPlayerpath(directory.toString());
        config.setPlayername(name);
        Path root = Files.createDirectories(directory.resolve(name));
        PlayerConfig player = new PlayerConfig();
        player.setLnmode(selected);
        return new Fixture(root, player, new PlayerResource(config, player, null, null), new PlayDataAccessor(config, player));
    }

    private record Fixture(Path root, PlayerConfig player, PlayerResource resource, PlayDataAccessor data) {}

    private static SongData catalog(BMSModel model) {
        SongData song = new SongData();
        song.setSha256(model.getSHA256());
        song.setFeature(new SongData(model, false).getFeature());
        return song;
    }

    private static ScoreDataCache cache(Fixture f) {
        return new ScoreDataCache() {
            protected boolean isForcedLn() { return f.player.isBmsirForceLn(); }
            protected ScoreData readScoreDatasFromSource(SongData song, int mode) { return f.data.readScoreData(song, mode); }
            protected void readScoreDatasFromSource(ScoreDatabaseAccessor.ScoreDataCollector collector, SongData[] songs, int mode) {
                f.data.readScoreDatas(collector, songs, mode);
            }
        };
    }

    private Path chart(int authored) throws Exception {
        if (authored == 4) return Files.writeString(directory.resolve("mixed.bmson"), """
                {"version":"1.0.0","info":{"title":"Mixed","artist":"test",
                "mode_hint":"beat-7k","init_bpm":120,"judge_rank":100,"total":100,
                "resolution":240,"ln_type":1},"lines":[],"bpm_events":[],"stop_events":[],
                "sound_channels":[{"name":"test.wav","notes":[
                {"x":1,"y":240,"l":240,"t":1},{"x":2,"y":240,"l":240,"t":2},
                {"x":3,"y":240,"l":240,"t":3},{"x":4,"y":240,"l":0}]}]}
                """);
        return Files.writeString(directory.resolve("chart" + authored + ".bms"),
                "#TITLE Selective\n#BPM 120\n#RANK 2\n#WAV01 test.wav\n"
                        + (authored < 0 ? "#00111:0101\n" : "#LNMODE " + authored + "\n#00151:0101\n"));
    }

    private static ScoreData score(int clear, int pgreat) {
        ScoreData score = new ScoreData();
        score.setClear(clear);
        score.setNotes(4);
        score.setEpg(pgreat);
        score.setMinbp(0);
        return score;
    }

    private static int count(Path database, String table) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
