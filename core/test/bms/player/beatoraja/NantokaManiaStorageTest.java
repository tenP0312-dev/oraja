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
