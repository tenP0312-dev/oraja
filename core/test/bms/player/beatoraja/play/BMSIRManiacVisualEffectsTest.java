package bms.player.beatoraja.play;

import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRManiacVisualEffectsTest {
    @Test
    void hiddenAndSuddenUseTheCorrectPlayerSide() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setHiddenSudden1P(1);
        settings.setHiddenSudden2P(2);
        BMSIRManiacVisualEffects.Transform output =
                new BMSIRManiacVisualEffects.Transform();

        apply(output, settings, 0, 16, 2, 20f);
        assertFalse(output.visible);
        apply(output, settings, 0, 16, 2, 80f);
        assertTrue(output.visible);
        apply(output, settings, 8, 16, 2, 80f);
        assertFalse(output.visible);
        apply(output, settings, 8, 16, 2, 20f);
        assertTrue(output.visible);
    }

    @Test
    void accelerationUsesLr2CurveEndpoints() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAcceleration(1);
        BMSIRManiacVisualEffects.Transform output =
                new BMSIRManiacVisualEffects.Transform();
        apply(output, settings, 0, 8, 1, 50f);
        assertEquals((float) Math.sin(Math.PI / 4.0) * 100f, output.y, 0.001f);
    }

    @Test
    void gambolOverridesOnlyTheScoringWindows() {
        long[][] table = {
                {-20_000, 20_000}, {-60_000, 60_000}, {-150_000, 150_000},
                {-280_000, 220_000}, {-150_000, 500_000}
        };
        BMSIRManiacVisualEffects.applyGambol(table, 2);
        assertEquals(-8_000, table[0][0]);
        assertEquals(12_000, table[2][1]);
        assertEquals(-280_000, table[3][0]);
    }

    private static void apply(
            BMSIRManiacVisualEffects.Transform output,
            BMSIRManiacSettings settings,
            int lane,
            int lanes,
            int players,
            float y
    ) {
        BMSIRManiacVisualEffects.apply(
                output,
                settings,
                lane,
                lanes,
                players,
                0,
                0L,
                0f,
                y,
                10f,
                4f,
                0f,
                100f
        );
    }
}
