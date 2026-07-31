package bms.player.beatoraja.pattern;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.model.TimeLine;
import bms.player.beatoraja.pattern.LaneShuffleModifier.OneBassLaneRandomShuffleModifier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OneBassPatternTest {
    @Test
    void everySupportedDestinationReceivesTheFirstSourceLane() {
        for (Mode mode : new Mode[]{
                Mode.BEAT_5K,
                Mode.BEAT_7K,
                Mode.BEAT_10K,
                Mode.BEAT_14K,
                Mode.POPN_9K
        }) {
            for (int player = 0; player < mode.player; player++) {
                int[] keys = PatternModifier.getKeysForPlayer(
                        mode,
                        player,
                        false
                );
                for (int target : keys) {
                    BMSModel model = emptyModel(mode);
                    OneBassLaneRandomShuffleModifier modifier =
                            new OneBassLaneRandomShuffleModifier(player, target);
                    modifier.setSeed(123456789L);
                    modifier.modify(model);
                    int[] result = modifier.getRandomPattern(mode);

                    int localTarget = target - mode.key * player / mode.player;
                    assertEquals(keys[0], result[localTarget]);
                    assertPermutationForSide(result, keys, player, mode);
                }
            }
        }
    }

    @Test
    void captureRequiresStartAndExactlyOnePlayableKeyPerSide() {
        assertEquals(-1, OneBassPattern.captureTarget(
                Mode.BEAT_14K, 0, false, lane -> lane == 3
        ));
        assertEquals(3, OneBassPattern.captureTarget(
                Mode.BEAT_14K, 0, true, lane -> lane == 3
        ));
        assertEquals(-1, OneBassPattern.captureTarget(
                Mode.BEAT_14K, 0, true, lane -> lane == 2 || lane == 3
        ));

        int secondSideTarget =
                PatternModifier.getKeysForPlayer(Mode.BEAT_14K, 1, false)[4];
        assertEquals(secondSideTarget, OneBassPattern.captureTarget(
                Mode.BEAT_14K,
                1,
                true,
                lane -> lane == secondSideTarget
        ));
    }

    @Test
    void pmsIsSupportedBut24KeyAndUnknownModesFailClosed() {
        assertTrue(OneBassPattern.isSupportedMode(Mode.POPN_9K));
        assertFalse(OneBassPattern.isSupportedMode(Mode.KEYBOARD_24K));
        assertFalse(OneBassPattern.isSupportedMode(Mode.KEYBOARD_24K_DOUBLE));
        assertEquals(-1, OneBassPattern.captureTarget(
                Mode.KEYBOARD_24K, 0, true, lane -> true
        ));
    }

    private static BMSModel emptyModel(Mode mode) {
        BMSModel model = new BMSModel();
        model.setMode(mode);
        model.setAllTimeLine(new TimeLine[0]);
        return model;
    }

    private static void assertPermutationForSide(
            int[] result,
            int[] keys,
            int player,
            Mode mode
    ) {
        int start = mode.key * player / mode.player;
        Set<Integer> actual = new HashSet<>();
        for (int index = 0; index < keys.length; index++) {
            actual.add(result[index]);
        }
        Set<Integer> expected = new HashSet<>();
        for (int key : keys) {
            expected.add(key);
        }
        assertEquals(expected, actual, "side " + player + " start " + start);
    }
}
