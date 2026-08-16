package bms.player.beatoraja.pattern;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.model.TimeLine;
import bms.player.beatoraja.pattern.LaneShuffleModifier.OneBassLaneRandomShuffleModifier;
import bms.player.beatoraja.pattern.LaneShuffleModifier.LaneRandomShuffleModifier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void selectedSeedIsAStandardRandomSeedForTheFinalOneBassPlacement() {
        for (Mode mode : new Mode[]{
                Mode.BEAT_5K,
                Mode.BEAT_7K,
                Mode.BEAT_10K,
                Mode.BEAT_14K,
                Mode.POPN_9K
        }) {
            for (int player = 0; player < mode.player; player++) {
                int[] keys = PatternModifier.getKeysForPlayer(mode, player, false);
                for (int target : keys) {
                    long seed = OneBassPattern.selectReplayableSeed(
                            mode,
                            player,
                            target,
                            123456L
                    );
                    assertTrue(seed > 0 && seed < OneBassPattern.RANDOM_SEED_BOUND);
                    assertTrue(OneBassPattern.seedPlacesFirstSourceAtTarget(
                            mode,
                            player,
                            target,
                            seed
                    ));

                    BMSModel standardModel = emptyModel(mode);
                    LaneRandomShuffleModifier standard =
                            new LaneRandomShuffleModifier(player, false);
                    standard.setSeed(seed);
                    standard.modify(standardModel);

                    BMSModel oneBassModel = emptyModel(mode);
                    OneBassLaneRandomShuffleModifier oneBass =
                            new OneBassLaneRandomShuffleModifier(player, target);
                    oneBass.setSeed(seed);
                    oneBass.modify(oneBassModel);

                    assertArrayEquals(
                            standard.getRandomPattern(mode),
                            oneBass.getRandomPattern(mode),
                            "mode=" + mode + " player=" + player + " target=" + target
                    );
                }
            }
        }
    }

    @Test
    void matchingPreferredSeedIsPreserved() {
        Mode mode = Mode.BEAT_7K;
        int target = PatternModifier.getKeysForPlayer(mode, 0, false)[3];
        long matching = -1;
        for (long seed = 1; seed < OneBassPattern.RANDOM_SEED_BOUND; seed++) {
            if (OneBassPattern.seedPlacesFirstSourceAtTarget(mode, 0, target, seed)) {
                matching = seed;
                break;
            }
        }
        assertTrue(matching >= 0);
        assertEquals(
                matching,
                OneBassPattern.selectReplayableSeed(mode, 0, target, matching)
        );
    }

    @Test
    void borrowedPlacementIsPreservedExceptForTheRequiredTwoLaneSwap() {
        for (Mode mode : new Mode[]{
                Mode.BEAT_5K,
                Mode.BEAT_7K,
                Mode.BEAT_10K,
                Mode.BEAT_14K,
                Mode.POPN_9K
        }) {
            for (int player = 0; player < mode.player; player++) {
                int[] keys = PatternModifier.getKeysForPlayer(mode, player, false);
                long borrowedSeed = player == 0 ? 123456L : 654321L;
                int[] borrowed = OneBassPattern.standardPermutation(
                        mode,
                        player,
                        borrowedSeed
                );
                for (int targetIndex = 0; targetIndex < keys.length; targetIndex++) {
                    int[] expected = borrowed.clone();
                    int sourceIndex = indexOf(expected, keys[0]);
                    int swap = expected[targetIndex];
                    expected[targetIndex] = expected[sourceIndex];
                    expected[sourceIndex] = swap;

                    long selectedSeed = OneBassPattern.selectBorrowedReplayableSeed(
                            mode,
                            player,
                            keys[targetIndex],
                            borrowedSeed
                    );

                    assertTrue(
                            selectedSeed >= 0
                                    && selectedSeed < OneBassPattern.RANDOM_SEED_BOUND
                    );
                    assertArrayEquals(
                            expected,
                            OneBassPattern.standardPermutation(
                                    mode,
                                    player,
                                    selectedSeed
                            ),
                            "mode=" + mode + " player=" + player
                                    + " target=" + keys[targetIndex]
                    );
                    assertTrue(differenceCount(borrowed, expected) <= 2);
                }
            }
        }
    }

    @Test
    void borrowedSeedAlreadyPlacingOneBassIsRetained() {
        Mode mode = Mode.BEAT_14K;
        int player = 1;
        long borrowedSeed = 7654321L;
        int[] keys = PatternModifier.getKeysForPlayer(mode, player, false);
        int[] borrowed = OneBassPattern.standardPermutation(
                mode,
                player,
                borrowedSeed
        );
        int target = keys[indexOf(borrowed, keys[0])];

        assertEquals(
                borrowedSeed,
                OneBassPattern.selectBorrowedReplayableSeed(
                        mode,
                        player,
                        target,
                        borrowedSeed
                )
        );
    }

    @Test
    void zeroIsAValidBorrowed24BitSeed() {
        Mode mode = Mode.BEAT_7K;
        int[] keys = PatternModifier.getKeysForPlayer(mode, 0, false);
        int[] borrowed = OneBassPattern.standardPermutation(mode, 0, 0);
        int target = keys[indexOf(borrowed, keys[0])];

        assertEquals(0, OneBassPattern.selectBorrowedReplayableSeed(
                mode,
                0,
                target,
                0
        ));
    }

    @Test
    void seedlessBorrowedOptionFallsBackToANewReplayableSeed() {
        Mode mode = Mode.BEAT_14K;
        for (int player = 0; player < mode.player; player++) {
            int target = PatternModifier.getKeysForPlayer(
                    mode,
                    player,
                    false
            )[player == 0 ? 2 : 4];
            long selected = OneBassPattern.selectReplayableSeed(
                    mode,
                    player,
                    target,
                    -1,
                    true
            );

            assertTrue(selected > 0 && selected < OneBassPattern.RANDOM_SEED_BOUND);
            assertTrue(OneBassPattern.seedPlacesFirstSourceAtTarget(
                    mode,
                    player,
                    target,
                    selected
            ));
        }
    }

    @Test
    void borrowedLaneOrderResolvesToItsStandardSeedForGhostBattle() {
        assertEquals(1917L, OneBassPattern.borrowedSeedForLaneOrder(
                Map.of(4375162, 1917L),
                4375162
        ));
        assertEquals(-1L, OneBassPattern.borrowedSeedForLaneOrder(null, 4375162));
        assertEquals(-1L, OneBassPattern.borrowedSeedForLaneOrder(
                Map.of(4375162, -1L),
                4375162
        ));
        assertEquals(-1L, OneBassPattern.borrowedSeedForLaneOrder(
                Map.of(1234567, 1917L),
                4375162
        ));
    }

    @Test
    void everySupportedSidePermutationHasAStandard24BitSeed() {
        assertTrue(OneBassPattern.hasCompleteStandardSeedCoverage(5));
        assertTrue(OneBassPattern.hasCompleteStandardSeedCoverage(7));
        assertTrue(OneBassPattern.hasCompleteStandardSeedCoverage(9));
    }

    @Test
    void allocationFreePermutationRankMatchesJavaRandom() {
        for (int laneCount : new int[]{5, 7, 9}) {
            for (long seed : new long[]{0L, 1L, 123456L, 7654321L}) {
                Random random = new Random(seed);
                int expected = 0;
                for (int remaining = laneCount; remaining > 1; remaining--) {
                    expected = expected * remaining + random.nextInt(remaining);
                }
                assertEquals(
                        expected,
                        OneBassPattern.javaRandomPermutationRank(laneCount, seed)
                );
            }
        }
    }

    @Test
    void invalidBorrowedSeedsAndUnsupportedModesFailClosed() {
        int target = PatternModifier.getKeysForPlayer(
                Mode.BEAT_7K,
                0,
                false
        )[0];
        assertEquals(-1, OneBassPattern.selectBorrowedReplayableSeed(
                Mode.BEAT_7K,
                0,
                target,
                -1
        ));
        assertEquals(-1, OneBassPattern.selectBorrowedReplayableSeed(
                Mode.BEAT_7K,
                0,
                target,
                OneBassPattern.RANDOM_SEED_BOUND
        ));
        assertEquals(-1, OneBassPattern.selectBorrowedReplayableSeed(
                Mode.KEYBOARD_24K,
                0,
                0,
                123456L
        ));
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

    private static int indexOf(int[] values, int target) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == target) {
                return index;
            }
        }
        return -1;
    }

    private static int differenceCount(int[] left, int[] right) {
        int differences = 0;
        for (int index = 0; index < left.length; index++) {
            if (left[index] != right[index]) {
                differences++;
            }
        }
        return differences;
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
