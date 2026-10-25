package bms.player.beatoraja.play;

import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;

/** Allocation-free LR2-style MANIAC HIDDEN/SUDDEN visibility and GAMBOL judge windows. */
final class BMSIRManiacVisualEffects {
    private BMSIRManiacVisualEffects() {
    }

    static void apply(
            Transform output,
            BMSIRManiacSettings settings,
            int lane,
            int laneCount,
            int playerCount,
            float x,
            float y,
            float width,
            float height,
            float judgeY,
            float topY
    ) {
        output.set(x, y, width, height, true);
        if (settings == null) return;
        float laneHeight = Math.max(1f, topY - judgeY);
        float position = clamp((y - judgeY) / laneHeight);
        int player = playerCount == 2 && lane >= laneCount / 2 ? 1 : 0;
        int hiddenSudden = player == 0
                ? settings.getHiddenSudden1P()
                : settings.getHiddenSudden2P();
        output.visible = visible(hiddenSudden, position);
    }

    static void applyGambol(long[][] table, int level) {
        if (table == null || table.length < 3 || level <= 0) return;
        int[] windows = level == 1
                ? new int[]{12_000, 24_000, 60_000}
                : new int[]{12_000, 12_000, 12_000};
        for (int index = 0; index < windows.length; index++) {
            if (table[index] == null || table[index].length < 2) continue;
            table[index][0] = -windows[index];
            table[index][1] = windows[index];
        }
    }

    private static boolean visible(int mode, float position) {
        return switch (mode) {
            case 1 -> position >= 0.60f;
            case 2 -> position <= 0.60f;
            case 3 -> position >= 0.55f && position <= 0.65f;
            default -> true;
        };
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static final class Transform {
        float x;
        float y;
        float width;
        float height;
        boolean visible;

        void set(float x, float y, float width, float height, boolean visible) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.visible = visible;
        }
    }
}
