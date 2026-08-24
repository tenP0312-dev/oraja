package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.MainController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMenuBarTest {
    @Test
    void bmsirPrimaryUsesOnlyTheDedicatedLeaderboardEntry() {
        assertFalse(ContextMenuBar.shouldShowPrimaryIrLeaderboard(statuses("BMS-IR")));
        assertFalse(ContextMenuBar.shouldShowPrimaryIrLeaderboard(statuses(" bms-ir ")));
    }

    @Test
    void anotherConfiguredPrimaryKeepsItsSeparateLeaderboardEntry() {
        assertTrue(ContextMenuBar.shouldShowPrimaryIrLeaderboard(statuses("Other IR")));
        assertTrue(ContextMenuBar.shouldShowPrimaryIrLeaderboard(statuses("")));
    }

    @Test
    void missingPrimaryDoesNotCreateALeaderboardEntry() {
        assertFalse(ContextMenuBar.shouldShowPrimaryIrLeaderboard(null));
        assertFalse(ContextMenuBar.shouldShowPrimaryIrLeaderboard(new MainController.IRStatus[0]));
        assertFalse(ContextMenuBar.shouldShowPrimaryIrLeaderboard(
                new MainController.IRStatus[] {null}
        ));
    }

    private MainController.IRStatus[] statuses(String name) {
        IRConfig config = new IRConfig();
        config.setIrname(name);
        return new MainController.IRStatus[] {
                new MainController.IRStatus(config, null, null)
        };
    }
}
