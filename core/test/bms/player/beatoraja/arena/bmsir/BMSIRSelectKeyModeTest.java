package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BMSIRSelectKeyModeTest {
    @Test
    void emptyAndInvalidSelectionsFallBackTo7k() {
        assertArrayEquals(
                new String[]{"7k"},
                BMSIRSelectKeyMode.normalizeIds(new String[]{"bad", ""})
        );
    }

    @Test
    void skipsUnavailableConfiguredModesInCycleOrder() {
        assertEquals(
                BMSIRSelectKeyMode.BEAT_14K,
                BMSIRSelectKeyMode.nextAvailable(
                        new String[]{"all", "7k", "14k", "9k"},
                        Mode.BEAT_7K,
                        Set.of(Mode.BEAT_14K.id, Mode.POPN_9K.id),
                        true,
                        false,
                        1
                )
        );
        assertEquals(
                BMSIRSelectKeyMode.POPN_9K,
                BMSIRSelectKeyMode.nextAvailable(
                        new String[]{"all", "7k", "14k", "9k"},
                        Mode.BEAT_14K,
                        Set.of(Mode.BEAT_14K.id, Mode.POPN_9K.id),
                        true,
                        false,
                        1
                )
        );
        assertEquals(
                BMSIRSelectKeyMode.ALL,
                BMSIRSelectKeyMode.nextAvailable(
                        new String[]{"all", "7k", "14k", "9k"},
                        Mode.POPN_9K,
                        Set.of(Mode.BEAT_14K.id, Mode.POPN_9K.id),
                        true,
                        false,
                        1
                )
        );
    }

    @Test
    void returnsNothingWhenTheCurrentListHasNoConfiguredMode() {
        assertNull(BMSIRSelectKeyMode.nextAvailable(
                new String[]{"7k", "14k"},
                Mode.BEAT_7K,
                Set.of(Mode.POPN_9K.id),
                true,
                false,
                1
        ));
        assertNull(BMSIRSelectKeyMode.nextAvailable(
                new String[]{"all", "7k"},
                Mode.BEAT_7K,
                Set.of(Mode.BEAT_7K.id),
                false,
                false,
                1
        ));
    }
}
