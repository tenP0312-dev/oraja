package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.SystemSoundManager.SoundType;

import java.util.HashSet;
import java.util.Set;

/**
 * Detects presentation-only Arena transitions and de-duplicates notification
 * sounds. It never sends a message or changes the Arena start gate.
 */
public final class ArenaPresentationController {
    @FunctionalInterface
    public interface SoundSink {
        void play(SoundType sound, float volume);
    }

    private static final long FLASH_NANOS = 900_000_000L;
    private static ArenaPresentationState previous =
            ArenaPresentationState.idle();
    private static ArenaPresentationState current =
            ArenaPresentationState.idle();
    private static boolean initialized;
    private static long matchFoundFlashUntil;
    private static long startFlashUntil;
    private static long lastCountdownSecond = Long.MAX_VALUE;
    private static int previousReadyCount;
    private static final Set<String> phaseWarnings = new HashSet<>();

    private ArenaPresentationController() {
    }

    public static void update(
            ArenaPresentationState next,
            PlayerConfig config,
            SoundSink soundSink
    ) {
        update(next, config, soundSink, System.nanoTime());
    }

    static void update(
            ArenaPresentationState next,
            PlayerConfig config,
            SoundSink soundSink,
            long nowNanos
    ) {
        next = next == null ? ArenaPresentationState.idle() : next;
        if (!initialized) {
            initialized = true;
            previous = next;
            current = next;
            previousReadyCount = next.readyCount();
            lastCountdownSecond = next.phase()
                    == ArenaPresentationState.Phase.COUNTDOWN
                    ? next.secondsRemaining()
                    : Long.MAX_VALUE;
            return;
        }

        float volume = config == null
                ? 1.0f
                : config.getBmsirArenaNotificationSeVolume() / 100.0f;
        boolean sameMatch = !next.matchId().isBlank()
                && next.matchId().equals(previous.matchId());
        boolean newMatch = !next.matchId().isBlank()
                && !next.matchId().equals(previous.matchId());
        if (newMatch) {
            lastCountdownSecond = Long.MAX_VALUE;
            previousReadyCount = 0;
            phaseWarnings.clear();
            if (previous.phase() == ArenaPresentationState.Phase.MATCHING
                    || !previous.matchId().isBlank()) {
                play(soundSink, SoundType.ARENA_MATCH_FOUND, volume);
                matchFoundFlashUntil = nowNanos + FLASH_NANOS;
            }
        }

        if (sameMatch
                && next.phase() == ArenaPresentationState.Phase.LOADING
                && next.requiredCount() > 0
                && next.readyCount() >= next.requiredCount()
                && previousReadyCount < next.requiredCount()) {
            play(soundSink, SoundType.ARENA_READY, volume);
        }

        if (next.phase() == ArenaPresentationState.Phase.COUNTDOWN) {
            long second = next.secondsRemaining();
            if (config != null
                    && config.isBmsirArenaCountdownSeEnabled()
                    && second >= 1L
                    && second <= 3L
                    && second < lastCountdownSecond) {
                play(soundSink, SoundType.ARENA_COUNTDOWN, volume);
            }
            lastCountdownSecond = Math.min(lastCountdownSecond, second);
        }

        if (sameMatch
                && !previous.startReleased()
                && next.startReleased()) {
            if (config != null && config.isBmsirArenaStartSeEnabled()) {
                play(soundSink, SoundType.ARENA_START, volume);
            }
            startFlashUntil = nowNanos + FLASH_NANOS;
        }

        if (config != null
                && config.isBmsirArenaPhaseWarningEnabled()
                && (next.phase() == ArenaPresentationState.Phase.SONG_SELECTION
                        || next.phase()
                                == ArenaPresentationState.Phase.OPTION_SELECT)
                && (next.secondsRemaining() == 10L
                        || next.secondsRemaining() == 5L)) {
            String warningKey = next.matchId()
                    + ":"
                    + next.phase()
                    + ":"
                    + next.secondsRemaining();
            if (phaseWarnings.add(warningKey)) {
                play(soundSink, SoundType.ARENA_PHASE_WARNING, volume);
            }
        }

        if (previous.isActive()
                && !previous.matchId().isBlank()
                && next.matchId().isBlank()) {
            play(soundSink, SoundType.ARENA_CANCELLED, volume);
        }

        previousReadyCount = next.readyCount();
        previous = next;
        current = next;
    }

    public static ArenaPresentationState visibleState() {
        return visibleState(System.nanoTime());
    }

    static ArenaPresentationState visibleState(long nowNanos) {
        if (nowNanos < startFlashUntil) {
            return current.announcement(
                    ArenaPresentationState.Phase.COUNTDOWN,
                    BMSIRArenaI18n.text("スタート！", "START!"),
                    ""
            );
        }
        if (nowNanos < matchFoundFlashUntil) {
            return current.announcement(
                    ArenaPresentationState.Phase.MATCH_FOUND,
                    BMSIRArenaI18n.text("マッチ成立", "MATCH FOUND"),
                    current.detail()
            );
        }
        return current;
    }

    static void resetForTest() {
        previous = ArenaPresentationState.idle();
        current = ArenaPresentationState.idle();
        initialized = false;
        matchFoundFlashUntil = 0L;
        startFlashUntil = 0L;
        lastCountdownSecond = Long.MAX_VALUE;
        previousReadyCount = 0;
        phaseWarnings.clear();
    }

    private static void play(
            SoundSink sink,
            SoundType sound,
            float volume
    ) {
        if (sink != null && volume > 0.0f) {
            sink.play(sound, volume);
        }
    }
}
