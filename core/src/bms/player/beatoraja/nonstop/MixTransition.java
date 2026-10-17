package bms.player.beatoraja.nonstop;

/**
 * How two neighbouring songs are joined. The audio join and the moment the
 * playable chart changes hands are stored separately (Issue #535 §6).
 */
public final class MixTransition {
    public static final long DEFAULT_BLEND_MS = 8000L;
    public static final long DEFAULT_FADE_MS = 3000L;
    /** De-click fade applied to sounds still ringing after a cut. */
    public static final long CUT_RELEASE_MS = 80L;
    public static final long MAX_OVERLAP_MS = 60_000L;

    public enum Type {
        /** Both songs play together while the volume crosses over. */
        BLEND,
        /** The next song starts exactly where the previous one stops. */
        CUT,
        /** The previous song fades out; the next one starts after it. */
        FADE_OUT,
        /** The previous song stops; the next one fades in from silence. */
        FADE_IN
    }

    public enum Curve {
        LINEAR, EQUAL_POWER,
        /** Keeps the outgoing song loud until late in the join. */
        LATE,
        /** Brings the incoming song up early in the join. */
        EARLY
    }

    public Type type = Type.BLEND;
    public Curve curve = Curve.EQUAL_POWER;
    /**
     * BLEND: how long both songs overlap. FADE_OUT / FADE_IN: the fade length.
     * CUT: ignored.
     */
    public long overlapMs = DEFAULT_BLEND_MS;
    /**
     * Where the playable chart changes hands, measured from the start of the
     * next song (set time). Only meaningful within the overlap of a BLEND.
     */
    public long handoffMs = DEFAULT_BLEND_MS / 2;
    /** Silence inserted between songs for FADE_OUT / CUT (ms). */
    public long gapMs;
    /** The generator must keep this join as it is. */
    public boolean locked;

    /** Length by which the next song is pulled back over the previous one. */
    public long placementOverlapMs() {
        return switch (type) {
            case BLEND -> clamp(overlapMs);
            case CUT, FADE_OUT, FADE_IN -> -Math.max(0L, gapMs);
        };
    }

    /** Fade-out window for the outgoing song, relative to its own end (negative = before it). */
    public long outgoingFadeMs() {
        return switch (type) {
            case BLEND -> clamp(overlapMs);
            case FADE_OUT -> clamp(overlapMs);
            case CUT, FADE_IN -> CUT_RELEASE_MS;
        };
    }

    /** Fade-in window for the incoming song from its own start. */
    public long incomingFadeMs() {
        return switch (type) {
            case BLEND -> clamp(overlapMs);
            case FADE_IN -> clamp(overlapMs);
            case CUT, FADE_OUT -> 0L;
        };
    }

    /** Clamps the hand-off into the window that this join allows. */
    public long effectiveHandoffMs() {
        if (type != Type.BLEND) {
            return 0L;
        }
        return Math.max(0L, Math.min(clamp(overlapMs), handoffMs));
    }

    /**
     * Volume of the incoming song at {@code x} (0..1) through its fade; the
     * outgoing song uses {@code incoming(1 - x)} so the pair is symmetric.
     */
    public double incoming(double x) {
        double t = Math.max(0.0, Math.min(1.0, x));
        return switch (curve) {
            case LINEAR -> t;
            case EQUAL_POWER -> Math.sin(t * Math.PI / 2.0);
            case LATE -> t * t;
            case EARLY -> Math.sqrt(t);
        };
    }

    public double outgoing(double x) {
        double t = Math.max(0.0, Math.min(1.0, x));
        return switch (curve) {
            case LINEAR -> 1.0 - t;
            case EQUAL_POWER -> Math.cos(t * Math.PI / 2.0);
            case LATE -> Math.sqrt(1.0 - t);
            case EARLY -> (1.0 - t) * (1.0 - t);
        };
    }

    MixTransition copyUnlockedDefaults() {
        MixTransition fresh = new MixTransition();
        fresh.type = type;
        fresh.curve = curve;
        fresh.overlapMs = overlapMs;
        fresh.handoffMs = handoffMs;
        fresh.gapMs = gapMs;
        return fresh;
    }

    public MixTransition copy() {
        MixTransition copy = copyUnlockedDefaults();
        copy.locked = locked;
        return copy;
    }

    private static long clamp(long value) {
        return Math.max(0L, Math.min(MAX_OVERLAP_MS, value));
    }
}
