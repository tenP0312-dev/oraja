package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.ScoreDataProperty;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.Version;
import bms.player.beatoraja.pattern.LR2RandomPattern;
import bms.player.beatoraja.select.bar.Bar;
import bms.player.beatoraja.select.bar.DirectoryBar;
import bms.player.beatoraja.select.bar.SongBar;
import bms.player.beatoraja.song.SongData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRArenaClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void arenaIdentityUsesOneVersionForDisplayAndWireProtocol() {
        assertEquals("0.4.14.45", Version.getArenaClientVersion());
        assertEquals(
                Version.getArenaClientVersion(),
                BMSIRArenaClient.clientVersion()
        );
        assertEquals("Arena oraja 0.4.14.45", Version.getArenaDisplayName());
    }

    @Test
    void idleArenaConnectionDoesNotBlockOrdinaryOneBass() {
        assertFalse(BMSIRArenaClient.blocksLocalOneBass(false, false));
        assertTrue(BMSIRArenaClient.blocksLocalOneBass(true, false));
        assertTrue(BMSIRArenaClient.blocksLocalOneBass(false, true));
        assertTrue(BMSIRArenaClient.blocksLocalOneBass(true, true));
    }

    @Test
    void arenaOverlaySettingsHaveSafeDefaultsAndClampTheMode() {
        PlayerConfig config = new PlayerConfig();
        assertEquals(0, config.getBmsirArenaOverlayMode());
        assertFalse(config.isBmsirArenaShowCursor());
        assertFalse(config.isBmsirArenaUnrestrictedRating());
        assertTrue(config.isBmsirArenaAllowCpu());
        assertFalse(config.isBmsirArenaAllowHigherSelection());
        assertFalse(config.isBmsirArenaRandomMirror());
        assertTrue(config.isBmsirArenaStayInRoom());
        assertTrue(config.isBmsirArenaRoomParticipating());
        assertFalse(config.isBmsirArenaSpectatorPublic());
        assertFalse(config.isBmsirArenaForceHostOption());
        assertFalse(config.isBmsirArenaMuteChat());
        assertFalse(config.isBmsirArenaAlwaysReady());
        assertEquals(0, config.getBmsirArenaGraphHighlight());
        assertEquals(
                PlayerConfig.BMSIR_ARENA_TARGET_OFF,
                config.getBmsirArenaTargetMode()
        );
        assertEquals(
                PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_RANK,
                config.getBmsirArenaGraphOrder()
        );
        assertTrue(config.isBmsirArenaPresentationOverlayEnabled());
        assertTrue(config.isBmsirArenaCountdownSeEnabled());
        assertTrue(config.isBmsirArenaStartSeEnabled());
        assertTrue(config.isBmsirArenaPhaseWarningEnabled());
        assertEquals(100, config.getBmsirArenaNotificationSeVolume());
        assertEquals("all", config.getBmsirArenaNominationPolicy());
        assertEquals("single", config.getBmsirArenaSeriesFormat());
        assertEquals(2, config.getBmsirArenaFirstToWins());
        assertEquals(60, config.getBmsirArenaNominationSeconds());
        assertEquals(10, config.getBmsirArenaOptionSeconds());
        assertEquals(0, config.getBmsirArenaIntermissionSeconds());
        assertEquals("lr2", config.getBmsirRulesetProfile());
        assertEquals(0, config.getBmsirArenaLastVisibleOverlayMode());
        assertFalse(config.isBmsirCoverHispeedAutoAdjustEnabled());
        assertFalse(config.isBmsirJudgeTimingRestoreEnabled());
        assertTrue(config.isBmsirInfoNotificationsEnabled());

        config.setBmsirCoverChangeStep(2000);
        assertEquals(1000, config.getBmsirCoverChangeStep());
        config.setBmsirCoverChangeStep(0);
        assertEquals(1, config.getBmsirCoverChangeStep());
        config.setBmsirArenaLastVisibleOverlayMode(2);
        assertEquals(1, config.getBmsirArenaLastVisibleOverlayMode());

        config.setBmsirArenaOverlayMode(99);
        assertEquals(2, config.getBmsirArenaOverlayMode());
        config.setBmsirArenaOverlayMode(-1);
        assertEquals(0, config.getBmsirArenaOverlayMode());
        config.setBmsirArenaNominationPolicy("rotate");
        assertEquals("rotate", config.getBmsirArenaNominationPolicy());
        config.setBmsirArenaSeriesFormat("first_to");
        config.setBmsirArenaFirstToWins(99);
        assertEquals("first_to", config.getBmsirArenaSeriesFormat());
        assertEquals(5, config.getBmsirArenaFirstToWins());
        config.setBmsirArenaGraphHighlight(99);
        assertEquals(1, config.getBmsirArenaGraphHighlight());
        config.setBmsirArenaTargetMode("ABOVE");
        assertEquals(
                PlayerConfig.BMSIR_ARENA_TARGET_ABOVE,
                config.getBmsirArenaTargetMode()
        );
        config.setBmsirArenaTargetMode("unknown");
        assertEquals(
                PlayerConfig.BMSIR_ARENA_TARGET_OFF,
                config.getBmsirArenaTargetMode()
        );
        config.setBmsirArenaGraphOrder("entry");
        assertEquals(
                PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY,
                config.getBmsirArenaGraphOrder()
        );
        config.setBmsirArenaGraphOrder("unknown");
        assertEquals(
                PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_RANK,
                config.getBmsirArenaGraphOrder()
        );
        config.setBmsirArenaNotificationSeVolume(999);
        assertEquals(100, config.getBmsirArenaNotificationSeVolume());
        config.setBmsirArenaNotificationSeVolume(-1);
        assertEquals(0, config.getBmsirArenaNotificationSeVolume());
    }

    @Test
    void arenaTargetSelectionUsesLiveOpponentExscoreOrderAndFallbacks() throws Exception {
        var match = JSON.readTree("""
                {
                  "players": [
                    {"player_id": 1, "name": "Self", "exscore": 180},
                    {"player_id": 2, "name": "Lead", "exscore": 200},
                    {"player_id": 3, "name": "Low", "exscore": 170},
                    {"player_id": 4, "name": "CPU", "exscore": 190, "test_bot": true}
                  ]
                }
                """);

        assertEquals(
                2,
                BMSIRArenaClient.arenaTargetPlayer(
                        match,
                        PlayerConfig.BMSIR_ARENA_TARGET_LEADER,
                        1,
                        0
                ).path("player_id").asInt()
        );
        assertEquals(
                4,
                BMSIRArenaClient.arenaTargetPlayer(
                        match,
                        PlayerConfig.BMSIR_ARENA_TARGET_ABOVE,
                        1,
                        0
                ).path("player_id").asInt()
        );
        assertEquals(
                3,
                BMSIRArenaClient.arenaTargetPlayer(
                        match,
                        PlayerConfig.BMSIR_ARENA_TARGET_SPECIFIED,
                        1,
                        3
                ).path("player_id").asInt()
        );
        assertEquals(
                2,
                BMSIRArenaClient.arenaTargetPlayer(
                        match,
                        PlayerConfig.BMSIR_ARENA_TARGET_SPECIFIED,
                        1,
                        99
                ).path("player_id").asInt()
        );
    }

    @Test
    void targetScoreProgressCanBeRecomputedAfterArenaLiveTarget() {
        ScoreDataProperty property = new ScoreDataProperty();
        property.setTargetScore(100, 180, 100);
        property.updateLiveTargetScore(120);

        assertEquals(120, property.getNowRivalScore());

        property.setTargetScore(100, 180, 100);
        property.refreshTargetScoreProgress(25);

        assertEquals(25, property.getNowBestScore());
        assertEquals(45, property.getNowRivalScore());
        assertEquals(180, property.getRivalScore());
    }

    @Test
    void rankedQueueMessageCarriesTheSavedCpuPreference() {
        PlayerConfig config = new PlayerConfig();
        config.setBmsirArenaUnrestrictedRating(true);
        config.setBmsirArenaAllowCpu(false);
        config.setBmsirArenaAllowHigherSelection(true);

        var message = BMSIRArenaClient.queueEntryMessage(config);

        assertEquals("queue_entry", message.path("type").asText());
        assertEquals("lr2", message.path("ruleset_profile").asText());
        assertTrue(message.path("unrestricted_rating").asBoolean());
        assertFalse(message.path("allow_cpu").asBoolean());
        assertTrue(message.path("allow_higher_selection").asBoolean());
        assertTrue(
                BMSIRArenaClient.queueEntryMessage(null)
                        .path("allow_cpu")
                        .asBoolean()
        );
        assertFalse(
                BMSIRArenaClient.queueEntryMessage(null)
                        .path("allow_higher_selection")
                        .asBoolean()
        );
    }

    @Test
    void forcedGaugeNamesMapToNativeGaugeOptions() {
        assertEquals(-1, BMSIRArenaClient.forcedGaugeOption("free"));
        assertEquals(2, BMSIRArenaClient.forcedGaugeOption("normal"));
        assertEquals(3, BMSIRArenaClient.forcedGaugeOption("hard"));
        assertEquals(4, BMSIRArenaClient.forcedGaugeOption("exhard"));
        assertEquals(5, BMSIRArenaClient.forcedGaugeOption("hazard"));
    }

    @Test
    void liveBpFallsBackFromUnsetStoredMinimumToJudgeCounts() {
        ScoreData score = new ScoreData();
        score.addJudgeCount(3, true, 2);
        score.addJudgeCount(4, false, 3);
        score.addJudgeCount(5, true, 1);
        assertEquals(6, BMSIRArenaClient.arenaMinBp(score));

        score.setMinbp(4);
        assertEquals(4, BMSIRArenaClient.arenaMinBp(score));
    }

    @Test
    void bpArenaUsesComboBreakJudgesOnly() {
        ScoreData score = new ScoreData();
        score.addJudgeCount(3, true, 2);
        score.addJudgeCount(4, false, 3);
        score.addJudgeCount(5, true, 7);

        assertEquals(5, BMSIRArenaClient.arenaComboBreak(score));
    }

    @Test
    void arenaAllowsSRandomButRejectsAssistOptions() {
        assertTrue(BMSIRArenaClient.isAllowedArenaRandom(0));
        assertTrue(BMSIRArenaClient.isAllowedArenaRandom(1));
        assertTrue(BMSIRArenaClient.isAllowedArenaRandom(2));
        assertTrue(BMSIRArenaClient.isAllowedArenaRandom(3));
        assertTrue(BMSIRArenaClient.isAllowedArenaRandom(4));
        assertTrue(BMSIRArenaClient.isAllowedArenaRandom(5));
        assertFalse(BMSIRArenaClient.isAllowedArenaRandom(6));
        assertTrue(BMSIRArenaClient.usesSynchronizedRandomSeed(2));
        assertFalse(BMSIRArenaClient.usesSynchronizedRandomSeed(4));
    }

    @Test
    void synchronizedRandomCanPreserveOrMirrorTheServerSeed() {
        long seed = 123456789L;
        assertEquals(seed, BMSIRArenaClient.synchronizedRandomSeed(seed, false));

        long mirrored = BMSIRArenaClient.synchronizedRandomSeed(seed, true);
        assertEquals(
                new StringBuilder(
                        LR2RandomPattern.getRajaLaneOrder(seed, false)
                ).reverse().toString(),
                LR2RandomPattern.getRajaLaneOrder(mirrored, false)
        );
    }

    @Test
    void spOptionDoesNotIncludeStaleSecondSideOrFlipSettings() {
        PlayerConfig config = new PlayerConfig();
        config.setRandom(2);
        config.setRandom2(1);
        config.setDoubleoption(1);

        assertEquals(
                2,
                BMSIRArenaClient.encodePlayOption(config, Mode.BEAT_7K.id)
        );
        assertEquals(
                112,
                BMSIRArenaClient.encodePlayOption(config, Mode.BEAT_14K.id)
        );
    }

    @Test
    void serverLockedOptionsAreAppliedForSpAndDp() {
        PlayerConfig config = new PlayerConfig();
        BMSIRArenaClient.applyLockedPlayOption(
                config,
                Mode.BEAT_14K.id,
                112
        );
        assertEquals(2, config.getRandom());
        assertEquals(1, config.getRandom2());
        assertEquals(1, config.getDoubleoption());
        assertEquals(
                "RAN / MIR / FLIP",
                BMSIRArenaClient.playOptionLabel(112, Mode.BEAT_14K.id)
        );
        assertEquals(
                "RAN / -",
                BMSIRArenaClient.playOptionLabel(2, Mode.BEAT_14K.id)
        );

        BMSIRArenaClient.applyLockedPlayOption(
                config,
                Mode.BEAT_7K.id,
                3
        );
        assertEquals(3, config.getRandom());
        assertEquals(0, config.getRandom2());
        assertEquals(0, config.getDoubleoption());
        assertEquals(
                "R-RANDOM",
                BMSIRArenaClient.playOptionLabel(3, Mode.BEAT_7K.id)
        );
        assertEquals(
                "S-RANDOM",
                BMSIRArenaClient.playOptionLabel(4, Mode.BEAT_7K.id)
        );
    }

    @Test
    void roomCodesAcceptClipboardWhitespaceAndModeLabelsCoverArenaKeys() {
        assertEquals(
                "ABC234",
                BMSIRArenaClient.normalizeRoomCode(" a b c 2 3 4\n")
        );
        assertEquals(
                "5KEY / SINGLE PLAY",
                BMSIRArenaClient.playModeLabel(Mode.BEAT_5K.id)
        );
        assertEquals(
                "7KEY / SINGLE PLAY",
                BMSIRArenaClient.playModeLabel(Mode.BEAT_7K.id)
        );
        assertEquals(
                "9KEY / PMS",
                BMSIRArenaClient.playModeLabel(Mode.POPN_9K.id)
        );
        assertEquals(
                "10KEY / DOUBLE PLAY",
                BMSIRArenaClient.playModeLabel(Mode.BEAT_10K.id)
        );
        assertEquals(
                "14KEY / DOUBLE PLAY",
                BMSIRArenaClient.playModeLabel(Mode.BEAT_14K.id)
        );
    }

    @Test
    void phaseBannerAlwaysNamesTheActionRequiredNow() {
        assertEquals(
                "OPを選んでください",
                BMSIRArenaClient.phaseAction(
                        true, false, false, false, false,
                        "", false, false, "matched"
                )
        );
        assertEquals(
                "曲を選んでください",
                BMSIRArenaClient.phaseAction(
                        false, false, true, true, false,
                        "", false, false, "matched"
                )
        );
        assertEquals(
                "部屋主の選曲を待っています",
                BMSIRArenaClient.phaseAction(
                        false, false, true, false, false,
                        "", false, false, "matched"
                )
        );
        assertEquals(
                "ほかの参加者の読込を待っています",
                BMSIRArenaClient.phaseAction(
                        false, false, false, false, false,
                        "loading", true, false, "matched"
                )
        );
        assertEquals(
                "対戦開始を待っています",
                BMSIRArenaClient.phaseAction(
                        false, false, false, false, false,
                        "countdown", false, false, "matched"
                )
        );
        assertEquals(
                "対戦相手を待っています",
                BMSIRArenaClient.phaseAction(
                        false, false, false, false, false,
                        "", false, false, "queued"
                )
        );
    }

    @Test
    void completedFinalUsesTheServerSelectedChartTotal() {
        ScoreData score = new ScoreData();
        score.setNotes(100);
        score.setPassnotes(95);

        assertEquals(
                100,
                BMSIRArenaClient.finalProcessedNotes(score, false, 100)
        );
        assertEquals(
                95,
                BMSIRArenaClient.finalProcessedNotes(score, true, 100)
        );

        score.setPassnotes(120);
        assertEquals(
                100,
                BMSIRArenaClient.finalProcessedNotes(score, true, 100)
        );
    }

    @Test
    void privateRoomPlayModesKeepFourteenKeyAsTheOnlySelection() {
        BMSIRArenaClient.setRoomAllowedPlayModes(
                JSON.createArrayNode().add(7)
        );
        BMSIRArenaClient.setRoomPlayModeAllowed(14, true);
        BMSIRArenaClient.setRoomPlayModeAllowed(7, false);

        assertEquals(1, BMSIRArenaClient.roomAllowedPlayModesView().size());
        assertEquals(14, BMSIRArenaClient.roomAllowedPlayModesView().get(0).asInt());
        BMSIRArenaClient.setRoomAllowedPlayModes(
                JSON.createArrayNode().add(7)
        );
    }

    @Test
    void chartCheckReportsOnlyAConsistentCachedNoteCount() {
        SongData song = new SongData();
        song.setNotes(100);

        assertEquals(100, BMSIRArenaClient.chartCheckTotalNotes(song, 100));
        assertEquals(0, BMSIRArenaClient.chartCheckTotalNotes(song, 101));
        assertEquals(0, BMSIRArenaClient.chartCheckTotalNotes(null, 100));
    }

    @Test
    void arenaStatusUpdatesOverlayQueueRatingAndRanking() throws Exception {
        BMSIRArenaClient.receiveArenaStatus(JSON.readTree("""
                {
                  "player": {
                    "rating_exact": 1234.5,
                    "matches_played": 9,
                    "queue": {
                      "status": "queued",
                      "match_mode": "casual",
                      "score_rule": "minbp",
                      "forced_gauge": "hard",
                      "chart_scope": "free",
                      "room_code": "C123456"
                    }
                  },
                  "ranking": {
                    "current": {"rank": 3},
                    "rows": [{"rank": 1, "player_id": 7}]
                  }
                }
                """));

        assertEquals(1234.5, BMSIRArenaClient.arenaRating());
        assertEquals(9, BMSIRArenaClient.arenaMatchesPlayed());
        assertEquals("queued", BMSIRArenaClient.queueStatus());
        assertEquals("casual", BMSIRArenaClient.currentMatchMode());
        assertEquals("minbp", BMSIRArenaClient.currentScoreRule());
        assertEquals("hard", BMSIRArenaClient.currentForcedGauge());
        assertEquals("free", BMSIRArenaClient.currentChartScope());
        assertEquals("C123456", BMSIRArenaClient.currentRoomCode());
        assertEquals(3, BMSIRArenaClient.rankingView().path("current").path("rank").asInt());
    }

    @Test
    void nominationCountdownRoundsUpAndStopsAtZero() {
        assertEquals(
                60,
                BMSIRArenaClient.nominationCountdownSeconds(61_000, 1_000)
        );
        assertEquals(
                1,
                BMSIRArenaClient.nominationCountdownSeconds(61_000, 60_999)
        );
        assertEquals(
                0,
                BMSIRArenaClient.nominationCountdownSeconds(61_000, 61_000)
        );
    }

    @Test
    void delayedNominationFolderRequestExpiresWithItsSelectionPhase() {
        assertTrue(BMSIRArenaClient.nominationSelectionRequestIsCurrent(
                true,
                "match-a",
                "match-a",
                15,
                15,
                "official",
                "official"
        ));
        assertFalse(BMSIRArenaClient.nominationSelectionRequestIsCurrent(
                false,
                "match-a",
                "match-a",
                15,
                15,
                "official",
                "official"
        ));
        assertFalse(BMSIRArenaClient.nominationSelectionRequestIsCurrent(
                true,
                "match-b",
                "match-a",
                15,
                15,
                "official",
                "official"
        ));
        assertFalse(BMSIRArenaClient.nominationSelectionRequestIsCurrent(
                true,
                "match-a",
                "match-a",
                14,
                15,
                "official",
                "official"
        ));
        assertFalse(BMSIRArenaClient.nominationSelectionRequestIsCurrent(
                true,
                "match-a",
                "match-a",
                15,
                15,
                "custom",
                "official"
        ));
    }

    @Test
    void fillCountdownRoundsUpAndStopsAtZero() {
        assertEquals(
                30,
                BMSIRArenaClient.fillCountdownSeconds(31_000, 1_000)
        );
        assertEquals(
                1,
                BMSIRArenaClient.fillCountdownSeconds(31_000, 30_999)
        );
        assertEquals(
                0,
                BMSIRArenaClient.fillCountdownSeconds(31_000, 31_000)
        );
    }

    @Test
    void arenaStartRemainsBlockedUntilReadyAndServerEpoch() {
        assertTrue(BMSIRArenaClient.arenaStartReleased(
                false,
                false,
                0L,
                1_000L
        ));
        assertFalse(BMSIRArenaClient.arenaStartReleased(
                true,
                false,
                2_000L,
                2_000L
        ));
        assertFalse(BMSIRArenaClient.arenaStartReleased(
                true,
                true,
                2_000L,
                1_999L
        ));
        assertTrue(BMSIRArenaClient.arenaStartReleased(
                true,
                true,
                2_000L,
                2_000L
        ));
    }

    @Test
    void onlyIncompleteBo2ResultsCarryAForcedExitDeadline() throws Exception {
        var firstRound = JSON.readTree("""
                {
                  "return_to_select_at": 1015.25,
                  "series": {"series_format": "bo2", "complete": false}
                }
                """);
        var finalRound = JSON.readTree("""
                {
                  "return_to_select_at": 1015.25,
                  "series": {"series_format": "bo2", "complete": true}
                }
                """);
        var nonBo2 = JSON.readTree("""
                {
                  "return_to_select_at": 1015.25,
                  "series": {"series_format": "first_to", "complete": false}
                }
                """);

        assertEquals(
                1_015_250L,
                BMSIRArenaClient.interRoundResultExitDeadlineMillis(firstRound)
        );
        assertEquals(
                0L,
                BMSIRArenaClient.interRoundResultExitDeadlineMillis(finalRound)
        );
        assertEquals(
                0L,
                BMSIRArenaClient.interRoundResultExitDeadlineMillis(nonBo2)
        );
        assertFalse(BMSIRArenaClient.interRoundResultDeadlineReached(
                1_015_250L,
                1_015_249L
        ));
        assertTrue(BMSIRArenaClient.interRoundResultDeadlineReached(
                1_015_250L,
                1_015_250L
        ));
    }

    @Test
    void matchScopedMessagesRejectMissingAndDifferentMatchIds() throws Exception {
        assertTrue(BMSIRArenaClient.matchMessageMatches(
                "match-a",
                JSON.readTree("{\"match_id\":\"match-a\"}")
        ));
        assertFalse(BMSIRArenaClient.matchMessageMatches(
                "match-a",
                JSON.readTree("{\"match_id\":\"match-b\"}")
        ));
        assertFalse(BMSIRArenaClient.matchMessageMatches(
                "",
                JSON.readTree("{\"match_id\":\"match-a\"}")
        ));
        assertFalse(BMSIRArenaClient.matchMessageMatches("match-a", null));
    }

    @Test
    void duplicateArenaErrorsNotifyOncePerMatchAndMessage() {
        BMSIRArenaClient.resetArenaErrorNotifications();
        try {
            assertTrue(BMSIRArenaClient.shouldShowArenaError(
                    "match-a",
                    "invalid_live",
                    "processed notes exceed chart total"
            ));
            assertFalse(BMSIRArenaClient.shouldShowArenaError(
                    "match-a",
                    "invalid_live",
                    "processed notes exceed chart total"
            ));
            assertTrue(BMSIRArenaClient.shouldShowArenaError(
                    "match-a",
                    "invalid_live",
                    "Arena max combo exceeds processed notes"
            ));
            assertTrue(BMSIRArenaClient.shouldShowArenaError(
                    "match-b",
                    "invalid_live",
                    "processed notes exceed chart total"
            ));
            assertTrue(BMSIRArenaClient.shouldShowArenaError(
                    "",
                    "authentication_failed",
                    "Arena authentication failed."
            ));
            assertTrue(BMSIRArenaClient.shouldShowArenaError(
                    "",
                    "authentication_failed",
                    "Arena authentication failed."
            ));
        } finally {
            BMSIRArenaClient.resetArenaErrorNotifications();
        }
    }

    @Test
    void clockOffsetUsesThePingRoundTripMidpoint() {
        assertEquals(
                100L,
                BMSIRArenaClient.clockOffsetMillis(
                        2.1,
                        1_900L,
                        2_100L
                )
        );
    }

    @Test
    void officialArenaLevelsExcludeZeroUnknownAndOutOfRangeFolders() {
        assertEquals(1, BMSIRArenaClient.officialArenaLevel("★1"));
        assertEquals(25, BMSIRArenaClient.officialArenaLevel("★25"));
        assertEquals(-1, BMSIRArenaClient.officialArenaLevel("★0"));
        assertEquals(-1, BMSIRArenaClient.officialArenaLevel("★26"));
        assertEquals(-1, BMSIRArenaClient.officialArenaLevel("★???"));
        assertEquals(-1, BMSIRArenaClient.officialArenaLevel("▼1"));
    }

    @Test
    void officialArenaTableMatchesTheKnownNameOrUrl() {
        assertTrue(
                BMSIRArenaClient.isOfficialArenaTable(
                        "発狂BMS難易度表",
                        ""
                )
        );
        assertTrue(
                BMSIRArenaClient.isOfficialArenaTable(
                        "",
                        "https://darksabun.club/table/archive/insane1/"
                )
        );
        assertFalse(
                BMSIRArenaClient.isOfficialArenaTable(
                        "NEW GENERATION 発狂難易度表",
                        "http://rattoto10.jounin.jp/table_insane.html"
                )
        );
    }

    @Test
    void normalArenaTableMatchesTheKnownNameOrUrl() {
        assertTrue(
                BMSIRArenaClient.isNormalArenaTable(
                        "GENOCIDE 通常難易度表",
                        ""
                )
        );
        assertTrue(
                BMSIRArenaClient.isNormalArenaTable(
                        "",
                        "https://darksabun.club/table/archive/normal1/"
                )
        );
        assertFalse(
                BMSIRArenaClient.isNormalArenaTable(
                        "NEW GENERATION 通常難易度表",
                        "https://example.invalid/normal"
                )
        );
    }

    @Test
    void nominationCandidatesCombineNormalThenOfficialThroughTheCeiling() {
        SongData normalOne = song("a");
        SongData normalTwelve = song("b");
        SongData normalThirteen = song("e");
        SongData officialOne = song("c");
        SongData officialTwo = song("d");
        TableData normal = new TableData();
        normal.setUrl("https://darksabun.club/table/archive/normal1/");
        normal.setFolder(new TableData.TableFolder[]{
                folder("☆0", song("z")),
                folder("☆1", normalOne),
                folder("☆12", normalTwelve),
                folder("☆13", normalThirteen),
                folder("☆14", song("x"))
        });
        TableData official = new TableData();
        official.setName("発狂BMS難易度表");
        official.setFolder(new TableData.TableFolder[]{
                folder("★1", officialOne),
                folder("★2", officialTwo),
                folder("★???", song("x"))
        });

        SongData[] candidates =
                BMSIRArenaClient.nominationCandidateElements(
                        new TableData[]{official, normal},
                        14
                );

        assertEquals(4, candidates.length);
        assertEquals("a", candidates[0].getMd5());
        assertEquals("b", candidates[1].getMd5());
        assertEquals("e", candidates[2].getMd5());
        assertEquals("c", candidates[3].getMd5());
        Map<Integer, SongData[]> levels =
                BMSIRArenaClient.nominationCandidateElementsByLevel(
                        new TableData[]{official, normal},
                        15
                );
        assertEquals(List.of(1, 12, 13, 14, 15), List.copyOf(levels.keySet()));
        assertEquals(1, levels.get(1).length);
        assertEquals(1, levels.get(12).length);
        assertEquals(1, levels.get(13).length);
        assertEquals(1, levels.get(14).length);
        assertEquals(1, levels.get(15).length);
        Map<Integer, SongData[]> initialLevels =
                BMSIRArenaClient.nominationCandidateElementsByLevel(
                        new TableData[]{official, normal},
                        10
                );
        assertEquals(List.of(1), List.copyOf(initialLevels.keySet()));
    }

    @Test
    void nominationFoldersSplitLevelsAndRetainOnlyPlayableLocalPaths() {
        SongData ownedOne = song("a");
        ownedOne.setSha256("sha-a");
        ownedOne.setPath("/songs/a/chart.bms");
        SongData ownedTwo = song("b");
        ownedTwo.setSha256("sha-b");
        ownedTwo.setPath("/songs/b/chart.bms");
        SongData missing = song("c");
        Map<Integer, SongData[]> candidates = new LinkedHashMap<>();
        candidates.put(1, new SongData[]{song("a"), missing});
        candidates.put(2, new SongData[]{song("b")});

        Map<Integer, SongData[]> playable =
                BMSIRArenaClient.playableOwnedSongsByLevel(
                        candidates,
                        new SongData[]{ownedOne, ownedTwo, ownedOne}
                );
        BMSIRArenaClient.ArenaNominationRootBar root =
                new BMSIRArenaClient.ArenaNominationRootBar(
                        null,
                        playable
                );
        Bar[] levelFolders = root.getChildren();

        assertEquals(2, levelFolders.length);
        assertEquals("☆1 (1譜面)", levelFolders[0].getTitle());
        assertEquals("☆2 (1譜面)", levelFolders[1].getTitle());
        assertTrue(((DirectoryBar) levelFolders[0]).usesTableFolderStyle());
        assertTrue(((DirectoryBar) levelFolders[1]).usesTableFolderStyle());
        Bar[] levelOneSongs = ((DirectoryBar) levelFolders[0]).getChildren();
        Bar[] levelTwoSongs = ((DirectoryBar) levelFolders[1]).getChildren();
        assertEquals(1, levelOneSongs.length);
        assertEquals(1, levelTwoSongs.length);
        assertEquals(
                "/songs/a/chart.bms",
                ((SongBar) levelOneSongs[0]).getSongData().getPath()
        );
        assertEquals(
                "/songs/b/chart.bms",
                ((SongBar) levelTwoSongs[0]).getSongData().getPath()
        );
        assertEquals("/songs/a/chart.bms", ownedOne.getPath());
        assertEquals("/songs/b/chart.bms", ownedTwo.getPath());
    }

    @Test
    void archiveVirtualPathsRemainPlayableArenaPossessions() {
        SongData archived = song("archive-md5");
        archived.setSha256("a".repeat(64));
        archived.setPath("/songs/pack.zip!-Pack/chart.bms");

        SongData[] playable = BMSIRArenaClient.playableOwnedSongs(
                new SongData[]{archived}
        );

        assertArrayEquals(new SongData[]{archived}, playable);
        assertEquals("/songs/pack.zip!-Pack/chart.bms", playable[0].getPath());
    }

    @Test
    void cpuChoosesAcrossTheInclusiveSixBandRange() {
        SongData below = song("below");
        SongData floor = song("floor");
        SongData middle = song("middle");
        SongData ceiling = song("ceiling");
        SongData above = song("above");
        Map<Integer, SongData[]> owned = new LinkedHashMap<>();
        owned.put(4, new SongData[]{below});
        owned.put(5, new SongData[]{floor});
        owned.put(7, new SongData[]{middle});
        owned.put(10, new SongData[]{ceiling});
        owned.put(11, new SongData[]{above});

        SongData[] candidates = BMSIRArenaClient.ownedCpuChartsInRange(
                owned,
                5,
                10
        );
        SongData selected = BMSIRArenaClient.randomOwnedCpuChart(
                owned,
                5,
                10
        );

        assertArrayEquals(
                new SongData[]{floor, middle, ceiling},
                candidates
        );
        assertTrue(
                selected == floor || selected == middle || selected == ceiling,
                "CPU must choose from every owned chart inside the six-band range"
        );
    }

    private static TableData.TableFolder folder(
            String name,
            SongData... songs
    ) {
        TableData.TableFolder folder = new TableData.TableFolder();
        folder.setName(name);
        folder.setSong(songs);
        return folder;
    }

    private static SongData song(String md5) {
        SongData song = new SongData();
        song.setMd5(md5);
        return song;
    }
}
