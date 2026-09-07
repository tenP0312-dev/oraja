package bms.player.beatoraja;

import bms.model.*;
import bms.player.beatoraja.arena.bmsir.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SevenToNinePreviewStorageTest {
    @TempDir Path directory;

    @Test void trialNeverChangesAnyRecordFileEvenWhenWriterIsCalledDirectly() throws Exception {
        for (boolean forceLn : new boolean[]{false, true}) {
            Config config = new Config();
            config.setPlayerpath(directory.toString());
            config.setPlayername(forceLn ? "forced" : "ordinary");
            Files.createDirectories(directory.resolve(config.getPlayername()));
            PlayerConfig player = new PlayerConfig();
            player.setBmsirForceLn(forceLn);
            PlayDataAccessor data = new PlayDataAccessor(config, player);
            BMSModel ordinary = model();
            ScoreData score = new ScoreData(Mode.BEAT_7K);
            score.setNotes(1); score.setEpg(1); score.setPassnotes(1);
            score.setClear(ClearType.Normal.id); score.setMinbp(0);
            data.writeScoreData(score, ordinary, 0, true);
            Map<Path, byte[]> before = snapshot();

            BMSModel trial = model();
            BMSIRManiacSettings settings = new BMSIRManiacSettings();
            settings.setSevenToNinePreview(true);
            BMSIRManiacPlayContext context = BMSIRManiacPlayContext.prepare(settings, trial, false);
            assertNotNull(context);
            assertFalse(BMSIRManiacApiClient.canSubmit(context.settings()));
            data.writeScoreData(score, trial, 0, true);
            data.writeScoreData(score, trial, 0, false);
            data.wrireReplayData(new ReplayData(), trial, 0, 0);
            assertFalse(data.existsReplayData(trial, 0, 0));
            assertNull(data.readScoreData(trial, 0));
            Map<Path, byte[]> after = snapshot();
            assertEquals(before.keySet(), after.keySet());
            for (Path path : before.keySet()) assertArrayEquals(before.get(path), after.get(path), path.toString());
            assertNotNull(data.readScoreData(ordinary, 0));
        }
    }

    private Map<Path, byte[]> snapshot() throws Exception {
        Map<Path, byte[]> result = new HashMap<>();
        try (var paths = Files.walk(directory)) {
            for (Path p : paths.filter(Files::isRegularFile).toList()) result.put(p, Files.readAllBytes(p));
        }
        return result;
    }

    private static BMSModel model() {
        BMSModel model = new BMSModel(); model.setMode(Mode.BEAT_7K);
        model.setSHA256("b".repeat(64)); model.setBpm(120);
        TimeLine tl = new TimeLine(1, 1_000_000L, 8);
        tl.setBPM(120); tl.setNote(0, new NormalNote(1));
        model.setAllTimeLine(new TimeLine[]{tl});
        return model;
    }
}
