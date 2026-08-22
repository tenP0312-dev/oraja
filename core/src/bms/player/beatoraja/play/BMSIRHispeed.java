package bms.player.beatoraja.play;

import bms.player.beatoraja.PlayConfig;

/** Shared LR2-style HI-SPEED and live-duration calculations. */
public final class BMSIRHispeed {
    private BMSIRHispeed() {
    }

    public static float appliedHispeed(
            float storedHispeed,
            int baseScrollSpeed,
            int referenceBpm,
            boolean fixed,
            double targetBpm
    ) {
        double applied = storedHispeed * clampBaseScrollSpeed(baseScrollSpeed) / 100.0;
        if (fixed && Double.isFinite(targetBpm) && targetBpm > 0.0) {
            applied *= clampReferenceBpm(referenceBpm) / targetBpm;
        }
        if (!Double.isFinite(applied) || applied <= 0.0) {
            return PlayConfig.HISPEED_MIN;
        }
        return (float) Math.max(
                PlayConfig.HISPEED_MIN,
                Math.min(Float.MAX_VALUE, applied)
        );
    }

    public static int currentDuration(
            float hispeed,
            double bpm,
            double scroll,
            boolean laneCoverEnabled,
            float laneCover,
            boolean liftEnabled,
            float lift
    ) {
        if (!Float.isFinite(hispeed) || hispeed <= 0f
                || !Double.isFinite(bpm) || bpm <= 0.0
                || !Double.isFinite(scroll) || scroll <= 0.0) {
            return PlayConfig.DURATION_MIN;
        }
        double visible = 1.0 - effectiveLaneCover(
                laneCoverEnabled,
                laneCover,
                liftEnabled,
                lift
        );
        double duration = 240000.0 / bpm / hispeed / scroll * visible;
        if (!Double.isFinite(duration)) {
            return PlayConfig.DURATION_MIN;
        }
        if (duration >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(PlayConfig.DURATION_MIN, (int) Math.round(duration));
    }

    public static float hispeedForDuration(
            int duration,
            double bpm,
            double scroll,
            boolean laneCoverEnabled,
            float laneCover,
            boolean liftEnabled,
            float lift
    ) {
        if (duration <= 0
                || !Double.isFinite(bpm) || bpm <= 0.0
                || !Double.isFinite(scroll) || scroll <= 0.0) {
            return PlayConfig.HISPEED_MIN;
        }
        double visible = 1.0 - effectiveLaneCover(
                laneCoverEnabled,
                laneCover,
                liftEnabled,
                lift
        );
        double hispeed = 240000.0 * visible / bpm / scroll / duration;
        if (!Double.isFinite(hispeed) || hispeed <= 0.0) {
            return PlayConfig.HISPEED_MIN;
        }
        return (float) Math.max(
                PlayConfig.HISPEED_MIN,
                Math.min(Float.MAX_VALUE, hispeed)
        );
    }

    public static double effectiveLaneCover(
            boolean laneCoverEnabled,
            float laneCover,
            boolean liftEnabled,
            float lift
    ) {
        double cover = laneCoverEnabled
                ? Math.max(0f, Math.min(1f, laneCover))
                : 0.0;
        double liftAmount = liftEnabled
                ? Math.max(0f, Math.min(1f, lift))
                : 0.0;
        return Math.max(0.0, Math.min(1.0, cover * (1.0 - liftAmount)));
    }

    public static int clampBaseScrollSpeed(int value) {
        return Math.max(
                PlayConfig.BMSIR_BASE_SCROLL_SPEED_MIN,
                Math.min(PlayConfig.BMSIR_BASE_SCROLL_SPEED_MAX, value)
        );
    }

    public static int clampReferenceBpm(int value) {
        return Math.max(
                PlayConfig.BMSIR_HISPEED_REFERENCE_BPM_MIN,
                Math.min(PlayConfig.BMSIR_HISPEED_REFERENCE_BPM_MAX, value)
        );
    }
}
