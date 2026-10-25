package bms.player.beatoraja.play;

import bms.player.beatoraja.PlayConfig;

/** Floating multiplier, visible time and cover operations; independent of legacy speed settings. */
public final class BMSIRFloatingHispeed {
    public static final float MIN = 0.5f;
    public static final float MAX = 10f;
    public static final float KEY_STEP = 0.5f;
    public static final float SCRATCH_STEP = 1f / 150f;
    // Full-lane milliseconds * BPM at 1.00x. The host's four-beat lane uses 240000.
    private static final double LANE_TIME = 723.0 * 290.054199 / (1.5 * 480.0) * 1000.0;

    private BMSIRFloatingHispeed() { }

    public static float clampMultiplier(float value) {
        return Float.isFinite(value) ? Math.max(MIN, Math.min(MAX, value)) : 2f;
    }

    public static float displayMultiplier(PlayConfig pc) {
        return pc.isBmsirFloatingHispeedEnabled() ? pc.getBmsirFloatingHispeed()
                : PlayConfig.HISPEED_MODE_LR2.equals(pc.getBmsirHispeedMode()) ? pc.getEffectiveLr2Hispeed() : pc.getHispeed();
    }

    public static boolean hasCover(PlayConfig pc) {
        return ((pc.isEnablelanecover() || pc.isEnablehidden()) && pc.isBmsirFloatingCoverVisible())
                || pc.isEnablelift();
    }

    public static double visibleFraction(PlayConfig pc, boolean covers) {
        double lift = pc.isEnablelift() ? pc.getLift() : 0;
        double top = covers && pc.isEnablelanecover() ? pc.getLanecover() : 0;
        double bottom = covers && pc.isEnablehidden() ? pc.getHidden() : 0;
        return Math.max(0, (1.0 - lift) * Math.max(0, 1.0 - top - bottom));
    }

    public static float appliedHispeed(PlayConfig pc) {
        return appliedHispeed(pc, pc.getBmsirFloatingHispeed());
    }

    public static float appliedHispeed(PlayConfig pc, float multiplier) {
        double remaining = pc.isEnablelift() ? 1.0 - pc.getLift() : 1.0;
        return (float) (240000.0 / LANE_TIME * clampMultiplier(multiplier)
                / Math.max(1.0 / 723.0, remaining));
    }

    public static float visibleFrames(PlayConfig pc, double bpm, double scroll, boolean covers) {
        if (!Double.isFinite(bpm) || bpm <= 0 || !Double.isFinite(scroll) || scroll <= 0) return 0f;
        double visible = visibleFraction(pc, covers && pc.isBmsirFloatingCoverVisible());
        if (visible <= 1.0 / 723.0) return 0f;
        return (float) (LANE_TIME * visible * 0.06 / bpm / scroll
                / clampMultiplier(pc.getBmsirFloatingHispeed()));
    }

    public static int greenNumber(float frames) {
        if (!Float.isFinite(frames) || frames <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(frames * 10f));
    }

    public static void rememberGreen(PlayConfig pc, double bpm) {
        rememberGreen(pc, bpm, 1);
    }

    public static void rememberGreen(PlayConfig pc, double bpm, double scroll) {
        if (Double.isFinite(bpm) && bpm > 0 && Double.isFinite(scroll) && scroll > 0) {
            double visible = visibleFraction(pc, true);
            pc.setBmsirFloatingGreenFrames(visible <= 1.0 / 723.0 ? 0f : (float) (LANE_TIME * visible * 0.06
                    / bpm / scroll / clampMultiplier(pc.getBmsirFloatingHispeed())));
        }
    }

    public static void restoreGreen(PlayConfig pc, double bpm) {
        restoreGreen(pc, bpm, 1);
    }

    public static void restoreGreen(PlayConfig pc, double bpm, double scroll) {
        if (!Double.isFinite(bpm) || bpm <= 0 || !Double.isFinite(scroll) || scroll <= 0) return;
        float multiplier = (float) (LANE_TIME * visibleFraction(pc, true) * 0.06
                / bpm / scroll / Math.max(1f, pc.getBmsirFloatingGreenFrames()));
        pc.setBmsirFloatingHispeed(clampMultiplier(multiplier));
    }

    /** Speed keys and direct speed edits move the saved green number by the matching multiplier ratio. */
    public static void changeGreen(PlayConfig pc, float multiplierDelta) {
        if (!Float.isFinite(multiplierDelta)) return;
        setMultiplierKeepingRatio(pc, pc.getBmsirFloatingHispeed() + multiplierDelta);
    }

    public static void setMultiplierKeepingRatio(PlayConfig pc, float multiplier) {
        if (!Float.isFinite(multiplier)) return;
        float previous = clampMultiplier(pc.getBmsirFloatingHispeed());
        float next = clampMultiplier(multiplier);
        pc.setBmsirFloatingHispeed(next);
        float green = pc.getBmsirFloatingGreenFrames();
        if (Float.isFinite(green) && green > 0) {
            pc.setBmsirFloatingGreenFrames(Math.max(1f, Math.min(600f, green * previous / next)));
        }
    }

    public static void changeMultiplier(PlayConfig pc, float delta) {
        if (!Float.isFinite(delta)) return;
        pc.setBmsirFloatingHispeed(clampMultiplier(pc.getBmsirFloatingHispeed() + delta));
        // Keys intentionally keep the previously stored visible time.
    }

    public static void moveScratch(PlayConfig pc, float ticks, double bpm) {
        moveScratch(pc, ticks, bpm, 1);
    }

    public static void moveScratch(PlayConfig pc, float ticks, double bpm, double scroll) {
        if (ticks == 0 || !Float.isFinite(ticks)) return;
        double liftPixels = pc.isEnablelift() ? pc.getLift() * 723.0 : 0.0;
        double lanePixels = Math.max(1.0, 723.0 - liftPixels);
        if (pc.isEnablelanecover() && pc.isBmsirFloatingCoverVisible()) {
            double max = pc.isEnablehidden() && !pc.isEnablelift() ? 361 : lanePixels;
            pc.setLanecover((float) (clampPixels(pc.getLanecover() * lanePixels + ticks, 30, max) / lanePixels));
            if (pc.isEnablehidden()) {
                pc.setHidden((float) (clampPixels(pc.getHidden() * lanePixels + ticks, 30, max) / lanePixels));
            }
        } else if (pc.isEnablehidden() && pc.isBmsirFloatingCoverVisible()) {
            pc.setHidden(clampCover((float) (clampPixels(pc.getHidden() * lanePixels - ticks, 30, 723) / lanePixels)));
        } else if (pc.isEnablelift()) {
            double next = clampPixels(liftPixels - ticks, 0, 600);
            double nextLane = 723.0 - next;
            pc.setLift((float) (next / 723.0));
            // Keep existing cover edges at the same absolute position as LIFT moves.
            pc.setLanecover(clampCover((float) (pc.getLanecover() * lanePixels / nextLane)));
            pc.setHidden(clampCover((float) (pc.getHidden() * lanePixels / nextLane)));
            if (pc.isEnablelanecover() && !pc.isBmsirFloatingCoverVisible()) return;
        } else {
            changeMultiplier(pc, ticks * SCRATCH_STEP);
            rememberGreen(pc, bpm, scroll);
            return;
        }
        restoreGreen(pc, bpm, scroll);
    }

    private static float clampCover(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static double clampPixels(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void normalizeCovers(PlayConfig pc) {
        double oldLane = 723.0 * (1.0 - (pc.isEnablelift() ? pc.getLift() : 0));
        double lift = pc.isEnablelift() ? clampPixels(pc.getLift() * 723.0, 0, 600) : 0;
        if (pc.isEnablelift()) pc.setLift((float) (lift / 723.0));
        double lane = 723.0 - lift;
        double max = pc.isEnablelanecover() && pc.isEnablehidden() && !pc.isEnablelift() ? 361 : lane;
        double top = pc.getLanecover() * oldLane;
        double bottom = pc.getHidden() * oldLane;
        if (pc.isEnablelanecover()) pc.setLanecover((float) (clampPixels(top == 0 ? 90 : top, 30, max) / lane));
        if (pc.isEnablehidden()) {
            pc.setHidden((float) (clampPixels(bottom == 0 ? 90 : bottom, 30, max) / lane));
            if (pc.isEnablelanecover() && !pc.isEnablelift()) pc.setHidden(pc.getLanecover());
        }
    }

    public static void toggleCover(PlayConfig pc, double bpm) {
        toggleCover(pc, bpm, 1);
    }

    public static void toggleCover(PlayConfig pc, double bpm, double scroll) {
        if (pc.isEnablelanecover() && pc.isEnablelift()) {
            if (pc.isBmsirFloatingCoverVisible()) {
                pc.setBmsirFloatingSuddenLiftVisiblePixels((float) (723.0 * (1 - pc.getLift()) * (1 - pc.getLanecover())));
            } else if (pc.getBmsirFloatingSuddenLiftVisiblePixels() >= 0) {
                double remaining = 723.0 * (1 - pc.getLift());
                double top = remaining - pc.getBmsirFloatingSuddenLiftVisiblePixels();
                if (top > 0) pc.setLanecover(clampCover((float) (top / Math.max(1, remaining))));
                rememberGreen(pc, bpm, scroll);
            }
        }
        pc.setBmsirFloatingCoverVisible(!pc.isBmsirFloatingCoverVisible());
        if (!pc.isEnablelanecover() && !pc.isEnablehidden()) pc.setEnablelanecover(true);
        if (hasCover(pc)) restoreGreen(pc, bpm, scroll);
    }

    /** A virtual 60 Hz transition, independent of the rendering frame rate. */
    public static final class Transition {
        private float value;
        private float start;
        private float target;
        private long began;
        private long last;

        public Transition(float multiplier, long time) {
            value = start = target = multiplier;
            began = last = time;
        }

        public float update(float next, boolean cover, long time) {
            time = Math.max(last, time);
            if (cover) {
                double t = Math.min(1, (time - began) * 0.06 / 16.0);
                value = (float) (start + (target - start) * Math.sin(t * Math.PI / 2));
            } else {
                double residual = Math.pow(1.0 - Math.sin(Math.PI / 32), (time - last) * 0.06);
                value = (float) (target + (value - target) * residual);
            }
            if (next != target) {
                start = value;
                target = next;
                began = time;
            }
            last = time;
            return value;
        }
    }
}
