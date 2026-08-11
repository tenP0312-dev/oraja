package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRSelectKeyModeTest {
    @Test
    void emptyAndInvalidSelectionsFallBackTo7k() {
        assertArrayEquals(
                new String[]{"7k"},
                BMSIRSelectKeyMode.normalizeIds(new String[]{"bad", ""})
        );
    }

    @Test
    void cyclesConfiguredModesWithoutDependingOnTheCurrentList() {
        assertEquals(
                BMSIRSelectKeyMode.BEAT_14K,
                BMSIRSelectKeyMode.nextConfigured(
                        new String[]{"all", "7k", "14k", "9k"},
                        Mode.BEAT_7K,
                        1
                )
        );
        assertEquals(
                BMSIRSelectKeyMode.POPN_9K,
                BMSIRSelectKeyMode.nextConfigured(
                        new String[]{"all", "7k", "14k", "9k"},
                        Mode.BEAT_14K,
                        1
                )
        );
        assertEquals(
                BMSIRSelectKeyMode.ALL,
                BMSIRSelectKeyMode.nextConfigured(
                        new String[]{"all", "7k", "14k", "9k"},
                        Mode.POPN_9K,
                        1
                )
        );
    }

    @Test
    void startsAtTheConfiguredEdgeWhenTheCurrentModeIsNotSelected() {
        assertEquals(
                BMSIRSelectKeyMode.ALL,
                BMSIRSelectKeyMode.nextConfigured(
                        new String[]{"all", "7k", "14k"},
                        Mode.BEAT_5K,
                        1
                )
        );
        assertEquals(
                BMSIRSelectKeyMode.BEAT_14K,
                BMSIRSelectKeyMode.nextConfigured(
                        new String[]{"all", "7k", "14k"},
                        Mode.BEAT_5K,
                        -1
                )
        );
    }

    @Test
    void aSingleConfiguredModeRemainsTheOnlyCandidate() {
        assertEquals(
                BMSIRSelectKeyMode.BEAT_7K,
                BMSIRSelectKeyMode.nextConfigured(
                        new String[]{"7k"},
                        Mode.BEAT_14K,
                        1
                )
        );
    }

    @Test
    void concreteChecksAreGlobalVisibilityFilters() {
        String[] visible = {"7k", "14k"};

        assertTrue(BMSIRSelectKeyMode.isSongModeVisible(visible, Mode.BEAT_7K.id));
        assertTrue(BMSIRSelectKeyMode.isSongModeVisible(visible, Mode.BEAT_14K.id));
        assertFalse(BMSIRSelectKeyMode.isSongModeVisible(visible, Mode.BEAT_5K.id));
        assertTrue(BMSIRSelectKeyMode.isSongModeVisible(visible, 0));
    }

    @Test
    void legacyAllOnlySelectionKeepsEveryConcreteModeVisible() {
        String[] normalized = BMSIRSelectKeyMode.normalizeIds(new String[]{"all"});

        assertEquals(BMSIRSelectKeyMode.values().length, normalized.length);
        assertTrue(BMSIRSelectKeyMode.isSongModeVisible(normalized, Mode.BEAT_5K.id));
        assertTrue(BMSIRSelectKeyMode.isSongModeVisible(normalized, Mode.KEYBOARD_24K.id));
    }
}
