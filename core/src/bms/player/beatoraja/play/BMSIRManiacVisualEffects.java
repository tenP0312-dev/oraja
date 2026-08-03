package bms.player.beatoraja.play;

import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;

/** Allocation-free LR2-style MANIAC note coordinate effects. */
final class BMSIRManiacVisualEffects {
    private static final double TWO_PI = Math.PI * 2.0;

    private BMSIRManiacVisualEffects() {
    }

    static void apply(
            Transform output,
            BMSIRManiacSettings settings,
            int lane,
            int laneCount,
            int playerCount,
            int noteIndex,
            long now,
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
        if (!output.visible) return;

        int acceleration = settings.getAcceleration();
        if (acceleration > 0) {
            int selected = acceleration == 3
                    ? (((noteIndex + lane) & 1) == 0 ? 1 : 2)
                    : acceleration;
            float transformed = selected == 1
                    ? (float) Math.sin(position * Math.PI / 2.0)
                    : (float) (1.0 - Math.cos(position * Math.PI / 2.0));
            output.y = judgeY + transformed * laneHeight;
            position = transformed;
        }

        float globalX = earthquakeX(settings, now) + nabeatsuX(settings, now);
        float globalY = earthquakeY(settings, now) + nabeatsuY(settings, now);
        output.x += globalX;
        output.y += globalY;

        int heartbeat = settings.getHeartbeat();
        if (heartbeat > 0) {
            float pulse = (float) Math.sin(
                    Math.floorMod(now + (long) (noteIndex + lane * 2) * 50L, 500L)
                            / 500.0 * TWO_PI
            );
            output.width += pulse * heartbeat * 0.02f * width;
            output.height += pulse * heartbeat * 0.01f * height;
            output.x -= (output.width - width) / 2f;
            output.y -= (output.height - height) / 2f;
        }

        int tornado = settings.getTornado();
        if (tornado > 0) {
            double phase = ((double) lane / Math.max(1, laneCount)
                    + Math.floorMod(now, 1000L) / 1000.0) * TWO_PI;
            output.x += tornado * position * laneHeight * 0.005f * (float) Math.sin(phase);
        }
        int superLoop = settings.getSuperLoop();
        if (superLoop > 0) {
            long duration = Math.max(1000L, (120L - superLoop) * 50L);
            double angle = Math.floorMod(now, duration) / (double) duration * TWO_PI;
            float radius = position * laneHeight * superLoop / 500f;
            output.x += (float) Math.sin(angle) * radius;
            output.y += (float) (Math.cos(angle) - 1.0) * radius;
        }
        int sinCurve = settings.getSinCurve();
        if (sinCurve > 0) {
            output.x += (float) Math.sin((sinCurve / 10.0) * Math.PI * position)
                    * sinCurve;
        }
        int spiral = settings.getSpiral();
        if (spiral > 0) {
            float radius = position * spiral;
            double angle = Math.floorMod(
                    now + (long) (noteIndex * 7 + lane) * 30L,
                    300L
            ) / 300.0 * TWO_PI;
            float direction = (noteIndex & 1) == 0 ? 1f : -1f;
            output.x += (float) Math.sin(angle) * radius * direction;
            output.y += (float) Math.cos(angle) * radius * direction;
        }
        int sideJump = settings.getSideJump();
        if (sideJump > 0) {
            float radius = position * sideJump;
            double angle = Math.floorMod(
                    now + lane * 50L + noteIndex * 77L,
                    500L
            ) / 500.0 * TWO_PI;
            float direction = (noteIndex & 1) == 0 ? 1f : -1f;
            output.x += (float) Math.sin(angle) * radius * direction;
            output.y -= Math.abs((float) Math.cos(angle) * radius * direction);
        }
        int wave = settings.getWave();
        if (wave > 0) {
            double phase = Math.floorMod(now + lane * 100L, 1000L)
                    / 1000.0 * TWO_PI;
            output.y += wave * position * position * (float) Math.sin(phase);
        }
        int character = settings.getCharacter();
        int interval = (100 - character) * 2 / 10 + 2;
        if (character > 0 && Math.floorMod(lane + noteIndex, interval) == 0) {
            output.y = judgeY + (output.y - judgeY) * 3f;
        }
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

    private static float earthquakeX(BMSIRManiacSettings settings, long now) {
        return settings.getEarthquake() * (float) Math.sin(
                Math.floorMod(now, 1000L) / 1000.0 * TWO_PI
        );
    }

    private static float earthquakeY(BMSIRManiacSettings settings, long now) {
        return settings.getEarthquake() * 0.75f * (float) Math.sin(
                Math.floorMod(now, 1234L) / 1234.0 * TWO_PI
        );
    }

    private static float nabeatsuX(BMSIRManiacSettings settings, long now) {
        if (!nabeatsuActive(settings, now)) return 0f;
        long period = Math.round(Math.sin(Math.floorMod(now, 12_345L)
                / 12_345.0 * TWO_PI) * 200.0 + 1_000.0);
        return settings.getNabeatsu() * (float) Math.sin(
                Math.floorMod(now, period) / (double) period * TWO_PI
        );
    }

    private static float nabeatsuY(BMSIRManiacSettings settings, long now) {
        if (!nabeatsuActive(settings, now)) return 0f;
        long period = Math.round(Math.sin(Math.floorMod(now, 10_000L)
                / 10_000.0 * TWO_PI) * 300.0 + 1_234.0);
        return settings.getNabeatsu() * 0.75f * (float) Math.sin(
                Math.floorMod(now, period) / (double) period * TWO_PI
        );
    }

    private static boolean nabeatsuActive(BMSIRManiacSettings settings, long now) {
        int value = settings.getNabeatsu();
        if (value <= 0 || now < 1000L) return false;
        int second = (int) ((now / 1000L) % 60L);
        return second % 3 == 0
                || (value >= 50 && (second >= 30 && second < 40 || second % 10 == 3));
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
