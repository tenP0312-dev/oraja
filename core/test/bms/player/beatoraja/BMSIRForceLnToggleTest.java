package bms.player.beatoraja;

import bms.model.*;
import bms.player.beatoraja.song.SongData;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BMSIRForceLnToggleTest {
    @TempDir Path directory;

    @Test
    void settingDefaultsOffAndRoundTripsWithoutLosingSelectedMode() {
        Json json = new Json();
        PlayerConfig config = json.fromJson(PlayerConfig.class, "{\"lnmode\":2}");
        assertFalse(config.isBmsirForceLn());
        config.setBmsirForceLn(true);
        assertEquals(0, config.getLnmode());
        PlayerConfig restored = json.fromJson(PlayerConfig.class, json.toJson(config));
        assertTrue(restored.isBmsirForceLn());
        assertEquals(2, restored.getSelectedLnmode());
        restored.setBmsirForceLn(false);
        assertEquals(2, restored.getLnmode());
    }

    @Test
    void realLoaderOnlyForcesLnWhenEnabledAcrossBothEntrypoints() throws Exception {
        PlayerConfig player = new PlayerConfig();
        PlayerResource resource = new PlayerResource(new Config(), player, null, null);
        for (int authored = 0; authored <= 3; authored++) {
            Path chart = chart(authored);
            for (int selected = 0; selected <= 2; selected++) {
                player.setLnmode(selected);
                for (boolean force : new boolean[]{false, true, false}) {
                    player.setBmsirForceLn(force);
                    for (BMSModel model : List.of(resource.loadBMSModel(chart, selected),
                            resource.loadBMSModel(new ChartInformation(chart, selected, null)))) {
                        assertEquals(force ? 0 : selected, model.getLntype());
                        assertEquals(force, BMSIRLongNoteMode.isApplied(model));
                        int effective = force ? 1 : authored == 0 ? selected + 1 : authored;
                        assertEquals(effective == 1 ? 1 : 2, model.getTotalNotes());
                        assertEquals(force ? 0 : authored, model.getLnmode());
                    }
                }
            }
        }
    }

    @Test
    void onOffOnKeepsIndependentBestsInSingleBulkAndCourseReads() throws Exception {
        Config config = new Config();
        config.setPlayerpath(directory.toString());
        config.setPlayername("player");
        Files.createDirectories(directory.resolve("player"));
        PlayerConfig player = new PlayerConfig();
        player.setLnmode(2);
        PlayerResource resource = new PlayerResource(config, player, null, null);
        PlayDataAccessor data = new PlayDataAccessor(config, player);
        Path chart = chart(3);
        BMSModel raw = resource.loadBMSModel(chart, 2);
        SongData song = new SongData(raw, false);
        var constraints = new CourseData.CourseDataConstraint[0];
        data.writeScoreData(score(5), raw, 2, true);
        data.writeScoreData(score(5), new BMSModel[]{raw}, 2, 0, constraints, true);
        player.setBmsirForceLn(true);
        BMSModel forced = resource.loadBMSModel(chart, 2);
        assertNull(data.readScoreData(forced, 0));
        data.writeScoreData(score(3), forced, 0, true);
        data.writeScoreData(score(3), new BMSModel[]{forced}, 0, 0, constraints, true);
        for (boolean force : new boolean[]{false, true, false, true}) {
            player.setBmsirForceLn(force);
            int mode = player.getLnmode(), clear = force ? 3 : 5;
            assertEquals(clear, data.readScoreData(song, mode).getClear());
            assertEquals(clear, data.readScoreData(force ? forced : raw, mode).getClear());
            Map<SongData, ScoreData> scores = new IdentityHashMap<>();
            data.readScoreDatas(scores::put, new SongData[]{song}, mode);
            assertEquals(clear, scores.get(song).getClear());
            assertEquals(clear, data.readScoreData(new SongData[]{song}, mode, 0, constraints).getClear());
        }
        // Updating OFF must still work after an ON score was saved.
        player.setBmsirForceLn(false);
        data.writeScoreData(score(7), raw, 2, true);
        assertEquals(7, data.readScoreData(raw, 2).getClear());
        player.setBmsirForceLn(true);
        assertEquals(3, data.readScoreData(forced, 0).getClear());
        data.deleteScoreData(forced, 0);
        assertNull(data.readScoreData(forced, 0));
        player.setBmsirForceLn(false);
        assertEquals(7, data.readScoreData(raw, 2).getClear());
    }

    @Test
    void bmsonAndReplayUseRecordedPolicyInsteadOfCurrentSwitch() throws Exception {
        Path chart = Files.writeString(directory.resolve("mixed.bmson"), """
                {"version":"1.0.0","info":{"title":"Mixed","artist":"test",
                "mode_hint":"beat-7k","init_bpm":120,"judge_rank":100,"total":100,
                "resolution":240,"ln_type":3},"lines":[],"bpm_events":[],"stop_events":[],
                "sound_channels":[{"name":"test.wav","notes":[
                {"x":1,"y":240,"l":240,"t":1},{"x":2,"y":240,"l":240,"t":2},
                {"x":3,"y":240,"l":240,"t":3},{"x":4,"y":240,"l":0}]}]}
                """);
        PlayerConfig player = new PlayerConfig();
        player.setLnmode(2);
        PlayerResource resource = new PlayerResource(new Config(), player, null, null);
        BMSModel raw = resource.loadBMSModel(chart, 2);
        assertEquals(6, raw.getTotalNotes());
        var rawIr = new bms.player.beatoraja.ir.IRChartData(new SongData(raw, false));
        assertTrue(rawIr.hasCN);
        assertTrue(rawIr.hasHCN);
        assertFalse(rawIr.hasUndefinedLN);
        player.setBmsirForceLn(true);
        BMSModel forced = resource.loadBMSModel(chart, 2);
        assertEquals(4, forced.getTotalNotes());
        var forcedIr = new bms.player.beatoraja.ir.IRChartData(new SongData(forced, false));
        assertEquals(0, forcedIr.lntype);
        assertFalse(forcedIr.hasCN);
        assertFalse(forcedIr.hasHCN);
        assertEquals(raw.getSHA256(), forced.getSHA256());
        var field = PlayerResource.class.getDeclaredField("model");
        field.setAccessible(true);
        field.set(resource, forced);
        ReplayData old = new ReplayData();
        old.mode = 2;
        BMSModel restored = resource.loadBMSModelForReplay(old);
        assertFalse(BMSIRLongNoteMode.isApplied(restored));
        assertEquals(6, restored.getTotalNotes());
        player.setBmsirForceLn(false);
        ReplayData on = new ReplayData();
        on.mode = 0;
        on.bmsirForcedLongNotes = true;
        restored = resource.loadBMSModelForReplay(on);
        assertTrue(BMSIRLongNoteMode.isApplied(restored));
        assertEquals(4, restored.getTotalNotes());
    }

    @Test
    void scoreCacheDoesNotReuseTheOppositeSwitchState() {
        PlayerConfig player = new PlayerConfig();
        SongData song = new SongData();
        song.setSha256("cache");
        song.setFeature(SongData.FEATURE_HELLCHARGENOTE);
        var cache = new bms.player.beatoraja.select.ScoreDataCache() {
            protected boolean isForcedLn() { return player.isBmsirForceLn(); }
            protected ScoreData readScoreDatasFromSource(SongData s, int mode) {
                return score(isForcedLn() ? 3 : 7);
            }
            protected void readScoreDatasFromSource(ScoreDatabaseAccessor.ScoreDataCollector collector,
                    SongData[] songs, int mode) {
                for (SongData s : songs) collector.collect(s, readScoreDatasFromSource(s, mode));
            }
        };
        for (boolean force : new boolean[]{false, true, false, true}) {
            player.setBmsirForceLn(force);
            assertEquals(force ? 3 : 7, cache.readScoreData(song, 0).getClear());
            cache.readScoreDatas((s, value) -> assertEquals(force ? 3 : 7, value.getClear()),
                    new SongData[]{song}, 0);
        }
    }

    private Path chart(int authored) throws Exception {
        return Files.writeString(directory.resolve("chart" + authored + ".bms"),
                "#TITLE Toggle\n#BPM 120\n#RANK 2\n#WAV01 test.wav\n#LNMODE " + authored + "\n#00151:0101\n");
    }

    private ScoreData score(int clear) {
        ScoreData score = new ScoreData();
        score.setClear(clear);
        score.setNotes(2);
        score.setMinbp(0);
        return score;
    }
}
