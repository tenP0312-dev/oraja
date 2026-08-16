package bms.player.beatoraja.pattern;

import bms.model.Mode;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;

/**
 * STARTと片側ちょうど1鍵の保持から、LR2互換ワンバスの一時配置先を得る。
 */
public final class OneBassPattern {
    static final long RANDOM_SEED_BOUND = 65536L * 256L;
    private static final int RANDOM_SEED_ATTEMPTS = 4096;
    private static final long JAVA_RANDOM_MULTIPLIER = 0x5DEECE66DL;
    private static final long JAVA_RANDOM_ADDEND = 0xBL;
    private static final long JAVA_RANDOM_MASK = (1L << 48) - 1;
    private static final Map<Integer, long[]> STANDARD_SEEDS_BY_RANK =
            new ConcurrentHashMap<>();

    private OneBassPattern() {
    }

    public static int captureTarget(
            Mode mode,
            int player,
            boolean startPressed,
            IntPredicate keyPressed
    ) {
        if (
                !isSupportedMode(mode)
                        || !startPressed
                        || keyPressed == null
                        || player < 0
                        || player >= mode.player
        ) {
            return -1;
        }
        int target = -1;
        for (int lane : PatternModifier.getKeysForPlayer(mode, player, false)) {
            if (!keyPressed.test(lane)) {
                continue;
            }
            if (target >= 0) {
                return -1;
            }
            target = lane;
        }
        return target;
    }

    /**
     * 元1鍵が指定先へ配置される、通常RANDOMとして再現可能なseedを選ぶ。
     * 既存seedが条件を満たす場合はそのまま使い、それ以外は同じ24bit空間から
     * rejection samplingする。返したseedだけでIRの譜面借用も最終配置を再現できる。
     */
    public static long selectReplayableSeed(
            Mode mode,
            int player,
            int targetLane,
            long preferredSeed
    ) {
        if (!isValidTarget(mode, player, targetLane)) {
            return -1;
        }
        if (
                preferredSeed > 0
                        && preferredSeed < RANDOM_SEED_BOUND
                        && seedPlacesFirstSourceAtTarget(
                                mode,
                                player,
                                targetLane,
                                preferredSeed
                        )
        ) {
            return preferredSeed;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < RANDOM_SEED_ATTEMPTS; attempt++) {
            long candidate = random.nextLong(1, RANDOM_SEED_BOUND);
            if (seedPlacesFirstSourceAtTarget(mode, player, targetLane, candidate)) {
                return candidate;
            }
        }

        // 通常は平均鍵数回で決まる。乱数源に異常があってもプレイを失敗させず、
        // 同じ有限seed空間を決定的に探索して必ず通常RANDOMのseedへ着地させる。
        long start = preferredSeed > 0 && preferredSeed < RANDOM_SEED_BOUND
                ? preferredSeed
                : 1;
        for (long offset = 0; offset < RANDOM_SEED_BOUND - 1; offset++) {
            long candidate = 1 + (start - 1 + offset) % (RANDOM_SEED_BOUND - 1);
            if (seedPlacesFirstSourceAtTarget(mode, player, targetLane, candidate)) {
                return candidate;
            }
        }
        return -1;
    }

    /**
     * 借用配置が実在する時だけそれを2レーン交換の基準にし、seedのない
     * option-only借用では新しい通常RANDOM seedを選ぶ。
     */
    public static long selectReplayableSeed(
            Mode mode,
            int player,
            int targetLane,
            long preferredSeed,
            boolean preserveBorrowedPlacement
    ) {
        if (preserveBorrowedPlacement && isStandardRandomSeed(preferredSeed)) {
            return selectBorrowedReplayableSeed(
                    mode,
                    player,
                    targetLane,
                    preferredSeed
            );
        }
        return selectReplayableSeed(mode, player, targetLane, preferredSeed);
    }

    public static boolean isStandardRandomSeed(long seed) {
        return seed >= 0 && seed < RANDOM_SEED_BOUND;
    }

    public static long borrowedSeedForLaneOrder(
            Map<Integer, Long> seedByLaneOrder,
            int laneOrder
    ) {
        if (seedByLaneOrder == null) {
            return -1;
        }
        Long seed = seedByLaneOrder.get(laneOrder);
        return seed != null && isStandardRandomSeed(seed) ? seed : -1;
    }

    /**
     * 借用した通常RANDOM配置を基準に元1鍵と指定先だけを交換し、その完成配置を
     * 通常の24bit seedだけで再現できるseedへ再符号化する。
     */
    public static long selectBorrowedReplayableSeed(
            Mode mode,
            int player,
            int targetLane,
            long borrowedSeed
    ) {
        if (
                !isValidTarget(mode, player, targetLane)
                        || !isStandardRandomSeed(borrowedSeed)
        ) {
            return -1;
        }

        int[] keys = PatternModifier.getKeysForPlayer(mode, player, false);
        int[] finalSources = standardPermutation(keys, borrowedSeed);
        int targetIndex = indexOf(keys, targetLane);
        int sourceIndex = indexOf(finalSources, keys[0]);
        if (targetIndex < 0 || sourceIndex < 0) {
            return -1;
        }
        if (sourceIndex == targetIndex) {
            return borrowedSeed;
        }

        int swap = finalSources[targetIndex];
        finalSources[targetIndex] = finalSources[sourceIndex];
        finalSources[sourceIndex] = swap;
        return standardSeedForPermutation(keys, finalSources);
    }

    static boolean seedPlacesFirstSourceAtTarget(
            Mode mode,
            int player,
            int targetLane,
            long seed
    ) {
        if (
                !isValidTarget(mode, player, targetLane)
                        || seed < 0
                        || seed >= RANDOM_SEED_BOUND
        ) {
            return false;
        }
        int[] keys = PatternModifier.getKeysForPlayer(mode, player, false);
        int[] remaining = keys.clone();
        int remainingCount = remaining.length;
        Random random = new Random(seed);
        for (int destination : keys) {
            int selectedIndex = random.nextInt(remainingCount);
            int source = remaining[selectedIndex];
            if (destination == targetLane) {
                return source == keys[0];
            }
            System.arraycopy(
                    remaining,
                    selectedIndex + 1,
                    remaining,
                    selectedIndex,
                    remainingCount - selectedIndex - 1
            );
            remainingCount--;
        }
        return false;
    }

    static int[] standardPermutation(Mode mode, int player, long seed) {
        if (
                !isSupportedMode(mode)
                        || player < 0
                        || player >= mode.player
                        || seed < 0
                        || seed >= RANDOM_SEED_BOUND
        ) {
            return new int[0];
        }
        return standardPermutation(
                PatternModifier.getKeysForPlayer(mode, player, false),
                seed
        );
    }

    private static int[] standardPermutation(int[] keys, long seed) {
        int[] remaining = keys.clone();
        int[] sources = new int[keys.length];
        int remainingCount = remaining.length;
        Random random = new Random(seed);
        for (int destination = 0; destination < keys.length; destination++) {
            int selectedIndex = random.nextInt(remainingCount);
            sources[destination] = remaining[selectedIndex];
            System.arraycopy(
                    remaining,
                    selectedIndex + 1,
                    remaining,
                    selectedIndex,
                    remainingCount - selectedIndex - 1
            );
            remainingCount--;
        }
        return sources;
    }

    private static long standardSeedForPermutation(
            int[] keys,
            int[] sources
    ) {
        int rank = permutationRank(keys, sources);
        if (rank < 0) {
            return -1;
        }
        long[] seeds = STANDARD_SEEDS_BY_RANK.computeIfAbsent(
                keys.length,
                OneBassPattern::buildStandardSeedLookup
        );
        return seeds[rank];
    }

    private static int permutationRank(int[] keys, int[] sources) {
        if (keys.length == 0 || keys.length != sources.length) {
            return -1;
        }
        int[] remaining = keys.clone();
        int remainingCount = remaining.length;
        int rank = 0;
        for (int source : sources) {
            int selectedIndex = indexOf(remaining, remainingCount, source);
            if (selectedIndex < 0) {
                return -1;
            }
            rank = rank * remainingCount + selectedIndex;
            System.arraycopy(
                    remaining,
                    selectedIndex + 1,
                    remaining,
                    selectedIndex,
                    remainingCount - selectedIndex - 1
            );
            remainingCount--;
        }
        return rank;
    }

    private static long[] buildStandardSeedLookup(int laneCount) {
        int permutationCount = factorial(laneCount);
        long[] seeds = new long[permutationCount];
        Arrays.fill(seeds, -1L);
        int missing = permutationCount;
        for (long seed = 0; seed < RANDOM_SEED_BOUND && missing > 0; seed++) {
            int rank = javaRandomPermutationRank(laneCount, seed);
            if (seeds[rank] < 0) {
                seeds[rank] = seed;
                missing--;
            }
        }
        return seeds;
    }

    static int javaRandomPermutationRank(int laneCount, long seed) {
        if (laneCount <= 0) {
            return -1;
        }
        long state = (seed ^ JAVA_RANDOM_MULTIPLIER) & JAVA_RANDOM_MASK;
        int rank = 0;
        for (int remaining = laneCount; remaining > 1; remaining--) {
            int selected;
            if ((remaining & -remaining) == remaining) {
                state = nextJavaRandomState(state);
                int bits = (int) (state >>> 17);
                selected = (int) ((remaining * (long) bits) >> 31);
            } else {
                int bits;
                int value;
                do {
                    state = nextJavaRandomState(state);
                    bits = (int) (state >>> 17);
                    value = bits % remaining;
                } while (bits - value + (remaining - 1) < 0);
                selected = value;
            }
            rank = rank * remaining + selected;
        }
        return rank;
    }

    static boolean hasCompleteStandardSeedCoverage(int laneCount) {
        long[] seeds = STANDARD_SEEDS_BY_RANK.computeIfAbsent(
                laneCount,
                OneBassPattern::buildStandardSeedLookup
        );
        for (long seed : seeds) {
            if (seed < 0) {
                return false;
            }
        }
        return true;
    }

    private static long nextJavaRandomState(long state) {
        return (state * JAVA_RANDOM_MULTIPLIER + JAVA_RANDOM_ADDEND)
                & JAVA_RANDOM_MASK;
    }

    private static int factorial(int value) {
        int result = 1;
        for (int factor = 2; factor <= value; factor++) {
            result *= factor;
        }
        return result;
    }

    private static int indexOf(int[] values, int target) {
        return indexOf(values, values.length, target);
    }

    private static int indexOf(int[] values, int length, int target) {
        for (int index = 0; index < length; index++) {
            if (values[index] == target) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isValidTarget(Mode mode, int player, int targetLane) {
        if (
                !isSupportedMode(mode)
                        || player < 0
                        || player >= mode.player
        ) {
            return false;
        }
        for (int lane : PatternModifier.getKeysForPlayer(mode, player, false)) {
            if (lane == targetLane) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSupportedMode(Mode mode) {
        return mode == Mode.BEAT_5K
                || mode == Mode.BEAT_7K
                || mode == Mode.BEAT_10K
                || mode == Mode.BEAT_14K
                || mode == Mode.POPN_9K;
    }
}
