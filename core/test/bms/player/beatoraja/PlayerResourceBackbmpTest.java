package bms.player.beatoraja;

import bms.model.BMSModel;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerResourceBackbmpTest {
    @Test
    void databaseBackbmpFillsAChartThatDoesNotDeclareOne() {
        BMSModel model = new BMSModel();
        SongData song = new SongData();
        song.setBackbmp("database.png");

        PlayerResource.preferStoredBackbmp(model, song);

        assertEquals("database.png", model.getBackbmp());
    }

    @Test
    void authoredBackbmpKeepsPriority() {
        BMSModel model = new BMSModel();
        model.setBackbmp("chart.png");
        SongData song = new SongData();
        song.setBackbmp("database.png");

        PlayerResource.preferStoredBackbmp(model, song);

        assertEquals("chart.png", model.getBackbmp());
    }
}
