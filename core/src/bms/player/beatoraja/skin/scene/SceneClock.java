package bms.player.beatoraja.skin.scene;

/** Exact absolute-time to scene-tick conversion. */
public final class SceneClock {
	private final long ticksPerSecond;
	private final long cycleTicks;
	private final double playbackRate;
	private final boolean loop;
	private long previousTick = Long.MIN_VALUE;
	private boolean movedBackward;

	public SceneClock(long ticksPerSecond, long cycleTicks, double playbackRate, boolean loop) {
		if (ticksPerSecond <= 0) {
			throw new IllegalArgumentException("ticksPerSecond must be positive");
		}
		if (!Double.isFinite(playbackRate) || playbackRate <= 0) {
			throw new IllegalArgumentException("playbackRate must be finite and positive");
		}
		if (cycleTicks < 0) {
			throw new IllegalArgumentException("cycleTicks cannot be negative");
		}
		this.ticksPerSecond = ticksPerSecond;
		this.cycleTicks = cycleTicks;
		this.playbackRate = playbackRate;
		this.loop = loop;
	}

	public long toTick(long nowMicros, long startMicros) {
		long elapsedMicros = nowMicros - startMicros;
		double scaled = elapsedMicros * playbackRate;
		long tick = floorToLong(scaled * ticksPerSecond / 1_000_000.0);
		if (loop && cycleTicks > 0) {
			tick = positiveModulo(tick, cycleTicks);
		} else if (tick < 0) {
			tick = 0;
		} else if (cycleTicks > 0 && tick > cycleTicks) {
			tick = cycleTicks;
		}
		movedBackward = previousTick != Long.MIN_VALUE && tick < previousTick;
		previousTick = tick;
		return tick;
	}

	public boolean movedBackward() {
		return movedBackward;
	}

	public void reset() {
		previousTick = Long.MIN_VALUE;
		movedBackward = false;
	}

	static long positiveModulo(long value, long modulus) {
		long result = value % modulus;
		return result < 0 ? result + modulus : result;
	}

	private static long floorToLong(double value) {
		if (value >= Long.MAX_VALUE) {
			return Long.MAX_VALUE;
		}
		if (value <= Long.MIN_VALUE) {
			return Long.MIN_VALUE;
		}
		return (long) Math.floor(value);
	}
}
