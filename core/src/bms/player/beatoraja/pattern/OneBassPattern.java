package bms.player.beatoraja.pattern;

import bms.model.Mode;

import java.util.function.IntPredicate;

/**
 * STARTと片側ちょうど1鍵の保持から、LR2互換ワンバスの一時配置先を得る。
 */
public final class OneBassPattern {
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

    public static boolean isSupportedMode(Mode mode) {
        return mode == Mode.BEAT_5K
                || mode == Mode.BEAT_7K
                || mode == Mode.BEAT_10K
                || mode == Mode.BEAT_14K
                || mode == Mode.POPN_9K;
    }
}
