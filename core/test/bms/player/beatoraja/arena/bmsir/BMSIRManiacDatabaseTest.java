package bms.player.beatoraja.arena.bmsir;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.PlayDataAccessor;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ScoreData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRManiacDatabaseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void transformedPlayWritesOnlyManiacDatabaseAndMetadata() throws Exception {
        Config config = new Config();
        config.setPlayerpath(temporaryDirectory.toString());
        config.setPlayername("player1");
        java.nio.file.Files.createDirectories(temporaryDirectory.resolve("player1"));
        PlayerConfig player = new PlayerConfig();
        player.setId("player1");
        PlayDataAccessor accessor = new PlayDataAccessor(config, player);
        BMSModel model = model();
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAddMines(100);
        BMSIRManiacPlayContext context = BMSIRManiacPlayContext.prepare(settings, model, false);
        context.updatePlacement(model);
        ScoreData score = new ScoreData();
        score.setEpg(10);
        score.setClear(5);
        score.setMinbp(1);
        accessor.writeScoreData(score, model, 0, true);

        assertNull(accessor.readScoreData(model.getSHA256(), false, 0));
        assertEquals(20, accessor.readScoreData(model, 0).getExscore());
        Path db = temporaryDirectory.resolve("player1/bmsir_maniac.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            assertEquals(1, connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM maniac_score_meta").getInt(1));
            assertEquals(1, connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM maniac_play_history").getInt(1));
            assertTrue(connection.createStatement()
                    .executeQuery("PRAGMA integrity_check").getString(1).equals("ok"));
        }
    }

    private static BMSModel model() {
        BMSModel model = new BMSModel();
        model.setSHA256("base-chart");
        model.setMode(Mode.BEAT_7K);
        TimeLine first = new TimeLine(0, 0, Mode.BEAT_7K.key);
        TimeLine second = new TimeLine(1, 500_000, Mode.BEAT_7K.key);
        first.setNote(0, new NormalNote(1));
        second.setNote(0, new NormalNote(2));
        model.setAllTimeLine(new TimeLine[]{first, second});
        return model;
    }
}
