package bms.player.beatoraja.system;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacSleepInhibitorTest {
    @Test
    void holdsDisplayAndIdleSleepUntilTheGameProcessExits() {
        assertEquals(List.of("/usr/bin/caffeinate", "-d", "-i", "-w", "4321"), MacSleepInhibitor.command(4321));
    }

    @Test
    void runsOnlyOnMacOs() {
        assertTrue(MacSleepInhibitor.isMac("Mac OS X"));
        assertFalse(MacSleepInhibitor.isMac("Windows 11"));
        assertFalse(MacSleepInhibitor.isMac("Linux"));
        assertFalse(MacSleepInhibitor.isMac(null));
    }
}
