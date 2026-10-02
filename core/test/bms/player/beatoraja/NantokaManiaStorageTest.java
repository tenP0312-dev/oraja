package bms.player.beatoraja;

import bms.model.*;
import bms.player.beatoraja.arena.bmsir.*;
import bms.player.beatoraja.input.KeyInputLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class NantokaManiaStorageTest {
    @TempDir Path directory;

    @Test void v2RecordsStaySeparateAndReplayFallbackUsesV1WithoutOverwritingIt() throws Exception {
        Config config = new Config(); config.setPlayerpath(directory.toString()); config.setPlayername("p1");
        Files.createDirectories(directory.resolve("p1/replay"));
        PlayerConfig player = new PlayerConfig(); PlayDataAccessor data = new PlayDataAccessor(config, player);
        var oldSettings = new BMSIRManiacSettings(); oldSettings.setNantokaMania(true);
        oldSettings.setNantokaJudgeVersion(1);
        BMSModel old = model(); BMSIRManiacPlayContext.prepare(oldSettings, old, false);
        ReplayData oldReplay = replay(); oldReplay.bmsirManiacSettings = oldSettings;
        oldReplay.bmsirManiacAlgorithmVersion = 1;
        data.wrireReplayData(oldReplay, old, 0, 0);
        var newSettings = new BMSIRManiacSettings(); newSettings.setNantokaMania(true);
        player.setBmsirManiacSettings(newSettings);
        BMSModel current = model(); BMSIRManiacPlayContext.prepare(newSettings, current, false);
        ScoreData oldScore = new ScoreData(Mode.BEAT_7K);
        oldScore.setSha256(old.getSHA256()); oldScore.setNotes(1); oldScore.setEpg(1);
        oldScore.setPassnotes(1); oldScore.setClear(ClearType.Normal.id); oldScore.setMinbp(0);
        data.writeScoreData(oldScore, old, 0, true);
        assertNull(data.readScoreData(current, 0));
        ScoreData newScore = new ScoreData(Mode.BEAT_7K);
        newScore.setSha256(current.getSHA256()); newScore.setNotes(1); newScore.setEgr(1);
        newScore.setPassnotes(1); newScore.setClear(ClearType.Normal.id); newScore.setMinbp(0);
        data.writeScoreData(newScore, current, 0, true);
        assertEquals(2, data.readScoreData(old, 0).getExscore());
        assertEquals(1, data.readScoreData(current, 0).getExscore());
        assertTrue(data.existsReplayData(current, 0, 0));
        assertEquals(1, data.readReplayData(current, 0, 0).bmsirManiacSettings.getNantokaJudgeVersion());
        assertEquals(1, data.readReplayData(model(), 0, 0).bmsirNantokaJudgeVersion);
        ReplayData newReplay = replay(); newReplay.bmsirManiacSettings = newSettings;
        newReplay.bmsirManiacAlgorithmVersion = 1; newReplay.bmsirNantokaJudgeVersion = 2;
        data.wrireReplayData(newReplay, current, 0, 0);
        assertEquals(2, data.readReplayData(current, 0, 0).bmsirManiacSettings.getNantokaJudgeVersion());
        assertEquals(1, data.readReplayData(old, 0, 0).bmsirManiacSettings.getNantokaJudgeVersion());
        data.deleteReplayData(current, 0, 0);
        assertTrue(data.existsReplayData(old, 0, 0));
        assertEquals(1, data.readReplayData(current, 0, 0).bmsirNantokaJudgeVersion);
    }

    @Test void replayVersionValidationRejectsUnsupportedVersion() {
        ReplayData invalid = replay();
        invalid.bmsirManiacSettings = new BMSIRManiacSettings(); invalid.bmsirManiacSettings.setNantokaMania(true);
        invalid.bmsirManiacAlgorithmVersion = 1; invalid.bmsirNantokaJudgeVersion = 3;
        assertFalse(invalid.validate());
    }


    @Test void specialScoreAndReplayNeverOverwriteOrdinaryRecords() throws Exception {
        Config config = new Config(); config.setPlayerpath(directory.toString()); config.setPlayername("p1");
        Files.createDirectories(directory.resolve("p1/replay"));
        PlayerConfig player = new PlayerConfig();
        PlayDataAccessor data = new PlayDataAccessor(config, player);
        BMSModel normal = model();
        BMSModel mania = model();
        var settings = new BMSIRManiacSettings(); settings.setNantokaMania(true);
        BMSIRManiacPlayContext.prepare(settings, mania, false);
        ScoreData score = new ScoreData(Mode.BEAT_7K);
        score.setSha256(mania.getSHA256()); score.setNotes(1); score.setEpg(1);
        score.setPassnotes(1); score.setClear(ClearType.Normal.id); score.setMinbp(0);
        data.writeScoreData(score, mania, 0, true);
        assertNotNull(data.readScoreData(mania, 0));
        assertNull(data.readScoreData(normal, 0));
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("p1/score.db"));
             var query = db.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM score")) {
            assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
        }

        ReplayData special = replay(); special.bmsirManiacSettings = settings;
        special.bmsirNantokaJudgeVersion = settings.getNantokaJudgeVersion();
        special.bmsirNantokaInitialHeldKeys = new int[]{0, 7};
        special.bmsirManiacAlgorithmVersion = BMSIRManiacSettings.ALGORITHM_VERSION;
        data.wrireReplayData(special, mania, 0, 0);
        assertTrue(data.existsReplayData(mania, 0, 0));
        assertFalse(data.existsReplayData(normal, 0, 0));
        assertTrue(data.readReplayData(mania, 0, 0).bmsirManiacSettings.isNantokaMania());
        assertArrayEquals(new int[]{0, 7}, data.readReplayData(mania, 0, 0).bmsirNantokaInitialHeldKeys);
        player.setBmsirManiacSettings(settings);
        assertTrue(data.existsReplayData(model(), 0, 0)); // Before model markers are applied.
        player.setBmsirManiacSettings(new BMSIRManiacSettings());
        data.wrireReplayData(replay(), normal, 0, 0);
        data.deleteReplayData(mania, 0, 0);
        assertFalse(data.existsReplayData(mania, 0, 0));
        assertNotNull(data.readReplayData(normal, 0, 0));
    }

    private static ReplayData replay() {
        ReplayData replay = new ReplayData();
        replay.keylog = new KeyInputLog[]{new KeyInputLog(1_000_000L, 0, true), new KeyInputLog(1_010_000L, 0, false)};
        return replay;
    }

    private static BMSModel model() {
        BMSModel model = new BMSModel(); model.setMode(Mode.BEAT_7K); model.setSHA256("a".repeat(64));
        model.setBpm(120); TimeLine line = new TimeLine(1, 1_000_000L, 8);
        line.setBPM(120); line.setNote(0, new NormalNote(1)); model.setAllTimeLine(new TimeLine[]{line});
        return model;
    }
}
