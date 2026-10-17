package bms.player.beatoraja.nonstop;

import java.util.function.Consumer;

/**
 * A set being prepared from Music Select's NONSTOP MIX folder (#539), so it can
 * be started with the decide button like a course. Only one runs at a time;
 * the F5 editor keeps its own preparation.
 */
public final class MixLaunch {
    public enum Kind { PLAY, AUTOPLAY }

    private static volatile MixLaunch current;

    private final String planId;
    private final Kind kind;
    private final String title;
    private volatile MixPreparation job;

    private MixLaunch(String planId, Kind kind, String title) {
        this.planId = planId;
        this.kind = kind;
        this.title = title;
    }

    /**
     * Starts preparing {@code plan}, replacing any earlier launch.
     *
     * @param finished runs on the worker thread when the job ends
     */
    public static MixLaunch start(MixPlan plan, MixLibrary library, Kind kind, Consumer<MixLaunch> finished) {
        MixLaunch previous = current;
        if (previous != null) {
            previous.cancel();
        }
        String title = plan.name == null || plan.name.isBlank() ? "NONSTOP MIX" : plan.name;
        MixLaunch launch = new MixLaunch(plan.id, kind, title);
        current = launch;
        launch.job = MixPreparation.start(plan, library, kind == Kind.PLAY, null, () -> finished.accept(launch));
        return launch;
    }

    public static MixLaunch current() {
        return current;
    }

    /** @return the running launch of this set, or null */
    public static MixLaunch runningFor(String planId) {
        MixLaunch launch = current;
        return launch != null && launch.planId.equals(planId) ? launch : null;
    }

    public void cancel() {
        MixPreparation running = job;
        if (running != null) {
            running.cancel();
        }
        finish();
    }

    /** Stops being the current launch; the caller takes over the prepared result. */
    public void finish() {
        if (current == this) {
            current = null;
        }
    }

    public boolean isCurrent() {
        return current == this;
    }

    public Kind kind() {
        return kind;
    }

    public String title() {
        return title;
    }

    public MixPreparation job() {
        return job;
    }
}
