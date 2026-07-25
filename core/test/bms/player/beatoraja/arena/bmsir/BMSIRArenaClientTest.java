package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSIRArenaClientTest {

    @Test
    void spOptionDoesNotIncludeStaleSecondSideOrFlipSettings() {
        PlayerConfig config = new PlayerConfig();
        config.setRandom(2);
        config.setRandom2(1);
        config.setDoubleoption(1);

        assertEquals(
                2,
                BMSIRArenaClient.encodePlayOption(config, Mode.BEAT_7K.id)
        );
        assertEquals(
                112,
                BMSIRArenaClient.encodePlayOption(config, Mode.BEAT_14K.id)
        );
    }

    @Test
    void completedFinalUsesTheServerSelectedChartTotal() {
        ScoreData score = new ScoreData();
        score.setNotes(100);
        score.setPassnotes(95);

        assertEquals(
                100,
                BMSIRArenaClient.finalProcessedNotes(score, false, 100)
        );
        assertEquals(
                95,
                BMSIRArenaClient.finalProcessedNotes(score, true, 100)
        );
    }

    @Test
    void chartCheckReportsOnlyAConsistentCachedNoteCount() {
        SongData song = new SongData();
        song.setNotes(100);

        assertEquals(100, BMSIRArenaClient.chartCheckTotalNotes(song, 100));
        assertEquals(0, BMSIRArenaClient.chartCheckTotalNotes(song, 101));
        assertEquals(0, BMSIRArenaClient.chartCheckTotalNotes(null, 100));
    }
}
