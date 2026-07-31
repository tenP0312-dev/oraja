package bms.player.beatoraja.pattern;

import bms.model.Mode;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;

/**
 * STARTと片側ちょうど1鍵の保持から、LR2互換ワンバスの一時配置先を得る。
 */
public final class OneBassPattern {
    static final long RANDOM_SEED_BOUND = 65536L * 256L;
    private static final int RANDOM_SEED_ATTEMPTS = 4096;

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

    static boolean seedPlacesFirstSourceAtTarget(
            Mode mode,
            int player,
            int targetLane,
            long seed
    ) {
        if (!isValidTarget(mode, player, targetLane) || seed < 0) {
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
