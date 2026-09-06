package bms.player.beatoraja;

import bms.model.*;
import bms.player.beatoraja.input.KeyInputLog;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BMSIRLongNoteReplayTest {
    @TempDir Path directory;

    @Test
    void legacyAndForcedReplaysKeepTheirPolicyAndSelectedMode() throws Exception {
        Path chart = Files.writeString(directory.resolve("authored.bms"),
                "#TITLE Replay\n#BPM 120\n#WAV01 test.wav\n#LNMODE 3\n#00151:0101\n");
        Config config = new Config();
        config.setPlayerpath(directory.toString());
        config.setPlayername("test");
        Files.createDirectories(directory.resolve("test/replay"));
        PlayDataAccessor data = new PlayDataAccessor(config);
        BMSModel raw = new BMSDecoder().decode(new ChartInformation(chart, 1, null));
        ReplayData legacy = replay(false, 1);
        data.wrireReplayData(legacy, raw, 1, 0);
        BMSModel forced = new BMSDecoder().decode(new ChartInformation(chart, 1, null));
        BMSIRLongNoteMode.apply(forced);
        assertTrue(data.existsReplayData(forced, 1, 0));
        assertFalse(data.readReplayData(forced, 1, 0).bmsirForcedLongNotes);

        data.wrireReplayData(replay(true, 1), forced, 1, 0);
        ReplayData stored = data.readReplayData(forced, 1, 0);
        assertTrue(stored.bmsirForcedLongNotes);
        assertEquals(1, stored.mode);
        assertTrue(Files.exists(directory.resolve("test/replay/" + raw.getSHA256() + ".brd")),
                "legacy authored-mode replay must remain intact");

        BMSModel[] course = {forced};
        data.wrireReplayData(new ReplayData[]{replay(true, 1)}, course, 1, 0,
                new CourseData.CourseDataConstraint[0]);
        assertTrue(data.readReplayData(course, 1, 0, new CourseData.CourseDataConstraint[0])[0].bmsirForcedLongNotes);

        // A forced LN replay in a different slot is not a legacy fallback for CN/HCN.
        BMSModel ln = new BMSDecoder().decode(new ChartInformation(chart, 0, null));
        BMSIRLongNoteMode.apply(ln);
        data.wrireReplayData(replay(true, 0), ln, 0, 1);
        assertTrue(data.existsReplayData(ln, 0, 1));
        assertFalse(data.existsReplayData(forced, 1, 1));
        assertFalse(data.existsReplayData(new bms.player.beatoraja.song.SongData(forced, false),
                raw.getSHA256(), 1, 1));
        assertNull(data.readReplayData(forced, 1, 1));
    }

    @Test
    void missingMarkerRetainsLegacyPolicyAndInvalidForcedModesAreRejected() {
        ReplayData old = new Json().fromJson(ReplayData.class, "{\"mode\":0}");
        assertFalse(old.bmsirForcedLongNotes);
        assertFalse(replay(true, -1).validate());
        assertFalse(replay(true, 3).validate());
        assertTrue(replay(true, 2).validate());
    }

    private static ReplayData replay(boolean forced, int mode) {
        ReplayData replay = new ReplayData();
        replay.mode = mode;
        replay.bmsirForcedLongNotes = forced;
        replay.keylog = new KeyInputLog[]{new KeyInputLog(1000, 0, true), new KeyInputLog(2000, 0, false)};
        return replay;
    }
}
