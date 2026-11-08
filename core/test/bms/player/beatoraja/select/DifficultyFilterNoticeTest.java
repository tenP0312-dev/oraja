package bms.player.beatoraja.select;

import org.junit.jupiter.api.Test;

import bms.player.beatoraja.modmenu.DifficultyFilterMenu;

import static org.junit.jupiter.api.Assertions.*;

/** A filter change must say which difficulty is shown, so a stray key press is not mistaken for missing songs. */
class DifficultyFilterNoticeTest {
    @Test void everyFilterValueHasALabelAndANotice() {
        String[] expected = { null, "BEGINNER", "NORMAL", "HYPER", "ANOTHER", "INSANE" };
        for (int filter = 1; filter <= 5; filter++) {
            assertEquals(expected[filter], DifficultyFilterMenu.label(filter));
            assertTrue(BarManager.difficultyFilterNotice(filter).contains(expected[filter]));
        }
        String all = DifficultyFilterMenu.label(0);
        assertFalse(all.isBlank());
        assertTrue(BarManager.difficultyFilterNotice(0).contains(all));
        assertNotEquals(BarManager.difficultyFilterNotice(0), BarManager.difficultyFilterNotice(4));
    }
}
