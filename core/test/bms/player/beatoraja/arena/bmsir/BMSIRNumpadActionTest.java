package bms.player.beatoraja.arena.bmsir;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSIRNumpadActionTest {
    @Test
    void defaultsMatchTheIidxInspiredAssignments() {
        String[] defaults = BMSIRNumpadAction.defaultIds();

        assertEquals(BMSIRNumpadAction.JUDGE_AUTO.id(), defaults[0]);
        assertEquals(BMSIRNumpadAction.JUDGE_MINUS.id(), defaults[3]);
        assertEquals(BMSIRNumpadAction.SKIN_CONFIG.id(), defaults[7]);
        assertEquals(BMSIRNumpadAction.JUDGE_PLUS.id(), defaults[9]);
        assertEquals(
                6,
                Arrays.stream(defaults)
                        .filter(BMSIRNumpadAction.NONE.id()::equals)
                        .count()
        );
    }

    @Test
    void unknownActionsNormalizeToNoneWithoutLosingMissingDefaults() {
        String[] normalized = BMSIRNumpadAction.normalizeIds(
                new String[]{"unknown", "FPS"}
        );

        assertEquals(BMSIRNumpadAction.NONE.id(), normalized[0]);
        assertEquals(BMSIRNumpadAction.FPS.id(), normalized[1]);
        assertEquals(BMSIRNumpadAction.JUDGE_MINUS.id(), normalized[3]);
        assertEquals(BMSIRNumpadAction.SKIN_CONFIG.id(), normalized[7]);
        assertEquals(BMSIRNumpadAction.JUDGE_PLUS.id(), normalized[9]);
    }
}
