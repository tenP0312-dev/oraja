package bms.player.beatoraja.select;

import java.util.OptionalLong;

/**
 * Chooses a useful generated-preview window from the existing per-second song
 * distribution.
 *
 * <p>The weighting and search bounds are adapted from BMZ Player's GPLv3
 * generated preview implementation at commit
 * 4c98cad3fb18210dca82413d8261ab62fb797248.</p>
 */
final class GeneratedPreviewSelector {

    static final long PREVIEW_DURATION_MS = 18_000;

    private static final int DENSITY_WINDOW_SECONDS = 8;
    private static final long PREVIEW_LEAD_MS = 500;

    private GeneratedPreviewSelector() {
    }

    static OptionalLong selectStartMs(int[][] distribution, long lengthMs) {
        int lengthSeconds = secondsFromMs(lengthMs);
        int distributionLength = effectiveDistributionLength(distribution);
        if (distributionLength == 0 && lengthSeconds == 0) {
            return OptionalLong.of(0L);
        }
        if (distributionLength == 0) {
            return OptionalLong.of(fallbackStartSecond(lengthSeconds) * 1_000L);
        }

        int window = Math.max(1, Math.min(DENSITY_WINDOW_SECONDS, distributionLength));
        int latestStart = Math.max(0, distributionLength - window);
        int firstStart = Math.min(distributionLength * 25 / 100, latestStart);
        int lastStart = Math.min(distributionLength * 80 / 100, latestStart);
        int bestStart = firstStart;
        double bestDensity = 0.0;
        double targetCenter = distributionLength * 0.55;

        for (int start = firstStart; start <= lastStart; start++) {
            double density = 0.0;
            for (int second = start; second < start + window; second++) {
                density += weightedNotes(distribution[second]);
            }
            double center = start + window * 0.5;
            double score = density - Math.abs(center - targetCenter) * 0.001;
            double bestCenter = bestStart + window * 0.5;
            double bestScore = bestDensity - Math.abs(bestCenter - targetCenter) * 0.001;
            if (score > bestScore) {
                bestStart = start;
                bestDensity = density;
            }
        }

        int selectedStart = bestDensity > 0.0
                ? bestStart
                : fallbackStartSecond(Math.max(lengthSeconds, distributionLength));
        return OptionalLong.of(Math.max(0L, selectedStart * 1_000L - PREVIEW_LEAD_MS));
    }

    private static int effectiveDistributionLength(int[][] distribution) {
        if (distribution == null) {
            return 0;
        }
        int length = distribution.length;
        while (length > 0 && isEmpty(distribution[length - 1])) {
            length--;
        }
        return length;
    }

    private static boolean isEmpty(int[] second) {
        if (second == null) {
            return true;
        }
        for (int value : second) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static double weightedNotes(int[] second) {
        if (second == null || second.length < 6) {
            return 0.0;
        }
        long tapLike = nonNegative(second, 0)
                + nonNegative(second, 2)
                + nonNegative(second, 3)
                + nonNegative(second, 5);
        long longBodies = nonNegative(second, 1) + nonNegative(second, 4);
        return tapLike + longBodies * 0.25;
    }

    private static long nonNegative(int[] values, int index) {
        return Math.max(0, values[index]);
    }

    private static int fallbackStartSecond(int lengthSeconds) {
        if (lengthSeconds <= 0) {
            return 0;
        }
        int target = lengthSeconds * 45 / 100;
        int latest = Math.max(0, lengthSeconds - (int) (PREVIEW_DURATION_MS / 1_000));
        return Math.min(target, latest);
    }

    private static int secondsFromMs(long lengthMs) {
        if (lengthMs <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (lengthMs + 999L) / 1_000L);
    }
}
