package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.PlayerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRJudgeTimingRestoreTest {

    @Test
    void restoresOnlyWhenEnabledAndAutoAdjustWasOnAtPlayStart() {
        PlayerConfig player = new PlayerConfig();
        player.setJudgetiming(4);
        player.setBmsirJudgeTimingRestoreEnabled(true);
        player.setNotesDisplayTimingAutoAdjust(true);
        BMSIRJudgeTimingRestore restore = new BMSIRJudgeTimingRestore();

        restore.begin(player);
        assertTrue(restore.isActive());
        player.setJudgetiming(9);
        player.setNotesDisplayTimingAutoAdjust(false);

        assertTrue(restore.restore(player));
        assertEquals(4, player.getJudgetiming());
        assertFalse(restore.isActive());
        assertFalse(restore.restore(player));
    }

    @Test
    void defaultOffAndStartWithAutoOffDoNotRestore() {
        PlayerConfig player = new PlayerConfig();
        player.setJudgetiming(2);
        BMSIRJudgeTimingRestore restore = new BMSIRJudgeTimingRestore();

        restore.begin(player);
        player.setJudgetiming(7);
        assertFalse(restore.restore(player));
        assertEquals(7, player.getJudgetiming());

        player.setBmsirJudgeTimingRestoreEnabled(true);
        player.setNotesDisplayTimingAutoAdjust(false);
        restore.begin(player);
        player.setJudgetiming(8);
        assertFalse(restore.restore(player));
        assertEquals(8, player.getJudgetiming());
    }
}
