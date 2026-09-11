package bms.player.beatoraja.play;

import bms.model.*;
import bms.player.beatoraja.*;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;
import bms.player.beatoraja.input.KeyInputLog;
import bms.player.beatoraja.ir.IRScoreData;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Gauge creation reads the global rule profile changed by other test classes")
class CourseGaugePolicyTest {
    @TempDir Path directory;

    private BMSModel chart() throws Exception {
        Path path = Files.writeString(directory.resolve("dan.bms"),
                "#TITLE Dan\n#BPM 120\n#TOTAL 200\n#WAV01 test.wav\n#00111:01010101\n");
        return new BMSDecoder().decode(new ChartInformation(path, 0, null));
    }

    @Test
    void settingsRoundTripWithoutSplittingChartOrRankingIdentity() {
        Json json = new Json();
        BMSIRManiacSettings settings = json.fromJson(BMSIRManiacSettings.class, "{}");
        assertFalse(settings.isCourseGauge());
        assertEquals(100, settings.getCourseGaugeInitialValue());
        String ordinary = settings.storageChartId("abc");
        settings.setCourseGauge(true);
        settings.setCourseGaugeInitialValue(42);
        assertFalse(settings.isActive());
        assertEquals(ordinary, settings.storageChartId("abc"));
        settings.setExtraMode(2);
        String transformed = settings.storageChartId("abc");
        String ranking = settings.virtualChartId("abc");
        long seed = settings.generationSeed("abc");
        settings.setCourseGauge(false);
        settings.setCourseGaugeInitialValue(2);
        assertEquals(transformed, settings.storageChartId("abc"));
        assertEquals(ranking, settings.virtualChartId("abc"));
        assertEquals(seed, settings.generationSeed("abc"));
        settings.setCourseGauge(true);
        BMSIRManiacSettings restored = json.fromJson(BMSIRManiacSettings.class, json.toJson(settings));
        assertTrue(restored.isCourseGauge());
        assertEquals(2, new BMSIRManiacSettings(restored).getCourseGaugeInitialValue());
        restored.setCourseGaugeInitialValue(-1);
        assertEquals(2, restored.getCourseGaugeInitialValue());
        restored.setCourseGaugeInitialValue(999);
        assertEquals(100, restored.getCourseGaugeInitialValue());
        restored.setCourseGaugeInitialValue(55);
        assertEquals(54, restored.getCourseGaugeInitialValue());
    }

    @Test
    void gaugeMatchesExistingCourseForEveryOrdinarySelection() throws Exception {
        BMSModel model = chart();
        for (int type = GrooveGauge.ASSISTEASY; type <= GrooveGauge.HAZARD; type++) {
            GrooveGauge existing = GrooveGauge.create(model, type, 1, null);
            GrooveGauge single = CourseGaugePolicy.create(model, type, 100);
            assertEquals(existing.getType(), single.getType());
            for (int judge : new int[]{0, 1, 2, 3, 4, 5, 0, 0, 4}) {
                existing.update(judge);
                single.update(judge);
                for (int i = GrooveGauge.CLASS; i <= GrooveGauge.EXHARDCLASS; i++) {
                    assertEquals(existing.getValue(i), single.getValue(i));
                }
            }
        }
        GrooveGauge low = CourseGaugePolicy.create(model, GrooveGauge.NORMAL, 2);
        for (int i = GrooveGauge.CLASS; i <= GrooveGauge.EXHARDCLASS; i++) {
            assertEquals(2f, low.getValue(i));
        }
        for (int i = 0; i < 100; i++) low.update(4);
        assertEquals(0f, low.getValue());
        assertFalse(low.isQualified());
    }

    @Test
    void replayUsesExistingStorageAndRestoresInitialValueInsteadOfLiveSettings() throws Exception {
        Config config = new Config();
        config.setPlayerpath(directory.toString());
        config.setPlayername("test");
        Files.createDirectories(directory.resolve("test/replay"));
        PlayDataAccessor data = new PlayDataAccessor(config);
        BMSModel model = chart();
        ReplayData replay = new ReplayData();
        replay.keylog = new KeyInputLog[]{new KeyInputLog(100, 0, true)};
        replay.gauge = GrooveGauge.NORMAL;
        replay.bmsirCourseGaugeInitialValue = 42;
        data.wrireReplayData(replay, model, 0, 0);
        ReplayData restored = data.readReplayData(model, 0, 0);
        assertNotNull(restored);
        BMSIRManiacSettings live = new BMSIRManiacSettings();
        assertEquals(42, CourseGaugePolicy.initialValue(live, restored, true, false));
        live.setCourseGauge(true);
        live.setCourseGaugeInitialValue(2);
        assertEquals(42, CourseGaugePolicy.initialValue(live, restored, true, false));
        ReplayData old = new Json().fromJson(ReplayData.class, "{gauge:2}");
        assertEquals(0, CourseGaugePolicy.initialValue(live, old, true, false));
        assertEquals(0, CourseGaugePolicy.initialValue(live, null, true, false));
        assertEquals(0, CourseGaugePolicy.initialValue(live, restored, true, true));
        assertEquals(0, CourseGaugePolicy.initialValue(live, null, false, true));
        assertEquals(2, CourseGaugePolicy.initialValue(live, null, false, false));
    }

    @Test
    void perSongCourseRulesPreserveSpecialLampsAndSuppressOrdinaryAndAssistClear() {
        for (ClearType lamp : new ClearType[]{ClearType.Normal, ClearType.Hard, ClearType.ExHard}) {
            assertEquals(lamp, CourseGaugePolicy.clearType(false, false, true, 0, false, 1, 1, lamp));
            assertEquals(ClearType.Failed,
                    CourseGaugePolicy.clearType(true, false, true, 0, false, 1, 1, lamp));
        }
        assertEquals(ClearType.NoPlay.id, CourseGaugePolicy.perSongClear(true, ClearType.Failed.id));
        assertEquals(ClearType.Failed.id, CourseGaugePolicy.perSongClear(false, ClearType.Failed.id));
        for (int assist : new int[]{1, 2}) {
            assertEquals(ClearType.Failed, CourseGaugePolicy.clearType(true, false, true,
                    assist, true, 0, 0, ClearType.Normal));
        }
        assertEquals(ClearType.FullCombo,
                CourseGaugePolicy.clearType(true, false, true, 0, true, 1, 1, ClearType.Normal));
        assertEquals(ClearType.Perfect,
                CourseGaugePolicy.clearType(true, false, true, 0, true, 0, 1, ClearType.Normal));
        assertEquals(ClearType.Max,
                CourseGaugePolicy.clearType(true, false, true, 0, true, 0, 0, ClearType.Normal));
        assertEquals(ClearType.Failed,
                CourseGaugePolicy.clearType(true, true, true, 0, true, 0, 0, ClearType.Normal));
        assertEquals(ClearType.Failed,
                CourseGaugePolicy.clearType(true, false, false, 0, true, 0, 0, ClearType.Normal));
    }

    @Test
    void ordinaryDatabaseAndIrKeepScoreAndBpWithoutAwardingNormalLamp() throws Exception {
        Config config = new Config();
        config.setPlayerpath(directory.toString());
        config.setPlayername("scores");
        Files.createDirectories(directory.resolve("scores"));
        PlayDataAccessor data = new PlayDataAccessor(config);
        BMSModel model = chart();
        ScoreData previous = new ScoreData();
        previous.setClear(ClearType.Easy.id);
        previous.setEpg(1);
        previous.setMinbp(3);
        data.writeScoreData(previous, model, 0, true);
        ScoreData attempt = new ScoreData();
        attempt.setClear(CourseGaugePolicy.perSongClear(true, ClearType.Failed.id));
        attempt.setEpg(3);
        attempt.setMinbp(1);
        attempt.setGauge(GrooveGauge.CLASS);
        data.writeScoreData(attempt, model, 0, true);
        ScoreData stored = data.readScoreData(model, 0);
        assertEquals(ClearType.Easy.id, stored.getClear());
        assertEquals(6, stored.getExscore());
        assertEquals(1, stored.getMinbp());
        IRScoreData submitted = new IRScoreData(attempt);
        assertEquals(ClearType.NoPlay, submitted.clear);
        assertEquals(3, submitted.epg);
        assertEquals(1, submitted.minbp);
        assertEquals(GrooveGauge.CLASS, submitted.gauge);
    }
}
