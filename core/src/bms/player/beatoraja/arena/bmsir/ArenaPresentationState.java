package bms.player.beatoraja.arena.bmsir;

/**
 * Immutable, display-only projection of the authoritative Arena client state.
 *
 * <p>It deliberately contains no command or timing logic. In particular,
 * {@code startReleased} is copied from the existing Arena start gate and is
 * never used to release gameplay.</p>
 */
public record ArenaPresentationState(
        Phase phase,
        String title,
        String detail,
        long secondsRemaining,
        int readyCount,
        int requiredCount,
        String matchId,
        boolean startReleased
) {
    public enum Phase {
        IDLE,
        MATCHING,
        MATCH_FOUND,
        SONG_SELECTION,
        OPTION_SELECT,
        LOADING,
        COUNTDOWN,
        PLAYING,
        RESULT
    }

    public ArenaPresentationState {
        phase = phase == null ? Phase.IDLE : phase;
        title = title == null ? "" : title;
        detail = detail == null ? "" : detail;
        secondsRemaining = Math.max(0L, secondsRemaining);
        readyCount = Math.max(0, readyCount);
        requiredCount = Math.max(0, requiredCount);
        matchId = matchId == null ? "" : matchId;
    }

    public static ArenaPresentationState idle() {
        return new ArenaPresentationState(
                Phase.IDLE,
                "",
                "",
                0L,
                0,
                0,
                "",
                false
        );
    }

    public boolean isActive() {
        return phase != Phase.IDLE
                && phase != Phase.PLAYING
                && phase != Phase.RESULT;
    }

    public ArenaPresentationState announcement(
            Phase announcementPhase,
            String announcementTitle,
            String announcementDetail
    ) {
        return new ArenaPresentationState(
                announcementPhase,
                announcementTitle,
                announcementDetail,
                secondsRemaining,
                readyCount,
                requiredCount,
                matchId,
                startReleased
        );
    }
}
