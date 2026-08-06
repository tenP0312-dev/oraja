package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.SystemSoundManager.SoundType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Shares BMSIRArenaI18n's static language field with BMSIRArenaI18nTest and
// BMSIRArenaOverlayTest; see the lock note on BMSIRArenaI18nTest.
@ResourceLock("bmsir-arena-i18n-language")
class ArenaPresentationControllerTest {
    @AfterEach
    void reset() {
        ArenaPresentationController.resetForTest();
        BMSIRArenaI18n.setLanguage("ja");
    }

    @Test
    void phaseSoundsAreMonotonicAndDoNotReplayAfterClockCorrection() {
        BMSIRArenaI18n.setLanguage("en");
        PlayerConfig config = new PlayerConfig();
        List<SoundType> sounds = new ArrayList<>();
        ArenaPresentationController.SoundSink sink =
                (sound, volume) -> sounds.add(sound);

        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.IDLE, "", 0, 0, 0, false),
                config,
                sink,
                0L
        );
        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.MATCHING, "", 0, 0, 0, false),
                config,
                sink,
                1L
        );
        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.MATCH_FOUND, "m1", 0, 0, 2, false),
                config,
                sink,
                2L
        );
        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.LOADING, "m1", 8, 1, 2, false),
                config,
                sink,
                3L
        );
        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.LOADING, "m1", 7, 2, 2, false),
                config,
                sink,
                4L
        );
        for (long second : new long[]{3, 3, 2, 3, 1}) {
            ArenaPresentationController.update(
                    state(
                            ArenaPresentationState.Phase.COUNTDOWN,
                            "m1",
                            second,
                            2,
                            2,
                            false
                    ),
                    config,
                    sink,
                    5L + second
            );
        }
        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.PLAYING, "m1", 0, 2, 2, true),
                config,
                sink,
                20L
        );
        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.PLAYING, "m1", 0, 2, 2, true),
                config,
                sink,
                21L
        );

        assertEquals(
                List.of(
                        SoundType.ARENA_MATCH_FOUND,
                        SoundType.ARENA_READY,
                        SoundType.ARENA_COUNTDOWN,
                        SoundType.ARENA_COUNTDOWN,
                        SoundType.ARENA_COUNTDOWN,
                        SoundType.ARENA_START
                ),
                sounds
        );
        assertEquals(
                "START!",
                ArenaPresentationController.visibleState(100L).title()
        );
    }

    @Test
    void announcementTitlesFollowTheSelectedLanguage() {
        PlayerConfig config = new PlayerConfig();
        List<SoundType> sounds = new ArrayList<>();
        ArenaPresentationController.SoundSink sink =
                (sound, volume) -> sounds.add(sound);

        BMSIRArenaI18n.setLanguage("ja");
        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.IDLE, "", 0, 0, 0, false),
                config,
                sink,
                0L
        );
        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.MATCHING, "", 0, 0, 0, false),
                config,
                sink,
                1L
        );
        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.MATCH_FOUND, "m1", 0, 0, 2, false),
                config,
                sink,
                2L
        );
        assertEquals(
                "マッチ成立",
                ArenaPresentationController.visibleState(2L).title()
        );

        ArenaPresentationController.update(
                state(ArenaPresentationState.Phase.PLAYING, "m1", 0, 2, 2, true),
                config,
                sink,
                20L
        );
        assertEquals(
                "スタート！",
                ArenaPresentationController.visibleState(20L).title()
        );
    }

    @Test
    void firstSnapshotSuppressesReconnectHistoryAndZeroVolumeSuppressesSound() {
        PlayerConfig config = new PlayerConfig();
        List<SoundType> sounds = new ArrayList<>();
        ArenaPresentationController.update(
                state(
                        ArenaPresentationState.Phase.COUNTDOWN,
                        "existing",
                        1,
                        2,
                        2,
                        false
                ),
                config,
                (sound, volume) -> sounds.add(sound),
                100L
        );
        ArenaPresentationController.update(
                state(
                        ArenaPresentationState.Phase.COUNTDOWN,
                        "existing",
                        1,
                        2,
                        2,
                        false
                ),
                config,
                (sound, volume) -> sounds.add(sound),
                101L
        );
        config.setBmsirArenaNotificationSeVolume(0);
        ArenaPresentationController.update(
                state(
                        ArenaPresentationState.Phase.PLAYING,
                        "existing",
                        0,
                        2,
                        2,
                        true
                ),
                config,
                (sound, volume) -> sounds.add(sound),
                102L
        );
        assertEquals(List.of(), sounds);
    }

    @Test
    void warningSoundsOnlyOncePerPhaseAndSecond() {
        PlayerConfig config = new PlayerConfig();
        List<SoundType> sounds = new ArrayList<>();
        ArenaPresentationController.SoundSink sink =
                (sound, volume) -> sounds.add(sound);
        ArenaPresentationController.update(ArenaPresentationState.idle(), config, sink, 0L);
        for (long second : new long[]{10, 10, 5, 5}) {
            ArenaPresentationController.update(
                    state(
                            ArenaPresentationState.Phase.SONG_SELECTION,
                            "m1",
                            second,
                            0,
                            0,
                            false
                    ),
                    config,
                    sink,
                    second
            );
        }
        assertEquals(
                List.of(
                        SoundType.ARENA_PHASE_WARNING,
                        SoundType.ARENA_PHASE_WARNING
                ),
                sounds
        );
    }

    private static ArenaPresentationState state(
            ArenaPresentationState.Phase phase,
            String matchId,
            long seconds,
            int ready,
            int required,
            boolean startReleased
    ) {
        return new ArenaPresentationState(
                phase,
                phase.name(),
                "",
                seconds,
                ready,
                required,
                matchId,
                startReleased
        );
    }
}
