package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.input.KeyBoardInputProcesseor;
import com.badlogic.gdx.Input.Keys;
import bms.player.beatoraja.system.RobustFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRArenaConfigStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void playerWriteCreatesAllowListedSidecarWithoutIrCredentials() throws Exception {
        PlayerConfig player = player("player1");
        player.setBmsirArenaEnabled(true);
        player.setBmsirArenaAllowCpu(false);
        player.setBmsirOneBassEnabled(false);
        player.setBmsirStartHerePreviewEnabled(false);
        player.setBmsirDanLocalSyncEnabled(false);
        player.setBmsirStartButtonAction(
                PlayerConfig.BMSIR_SELECT_ACTION_DIFFICULTY
        );
        player.setBmsirSelectButtonAction(
                PlayerConfig.BMSIR_SELECT_ACTION_KEY_MODE
        );
        player.setBmsirSelectDifficultyDisplay(
                PlayerConfig.BMSIR_SELECT_DIFFICULTY_DISPLAY_LR2
        );
        player.setBmsirSelectDifficultyStage(4);
        player.setBmsirSelectKeyModes(new String[]{"7k", "14k"});
        player.setBmsirTableLevelDisplayEnabled(false);
        player.setBmsirHideMissingTableSongs(true);
        player.setBmsirPhysicalFolderFilterEnabled(true);
        player.setBmsirVisiblePhysicalFolderPaths(new String[]{"songs-a", "songs-b"});
        player.setBmsirArenaOverlayHotkeyFunction(9);
        player.setBmsirArenaOverlayHotkeyModifiers(
                KeyBoardInputProcesseor.MASK_ALT
        );
        player.setBmsirArenaOverlayHotkeyKeys(
                new int[]{Keys.Z, Keys.X}
        );
        player.setBmsirArenaTargetMode(PlayerConfig.BMSIR_ARENA_TARGET_LEADER);
        player.setBmsirArenaGraphOrder(PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY);
        player.setBmsirArenaLastVisibleOverlayMode(1);
        player.setBmsirArenaOverlayMode(2);
        player.setBmsirCoverControlMode(PlayerConfig.BMSIR_COVER_CONTROL_EXTENDED);
        player.setBmsirCoverChangeStep(12);
        player.setBmsirCoverHispeedAutoAdjustEnabled(true);
        player.setBmsirLr2HispeedFixEnabled(true);
        player.getPlayConfig(7).getPlayconfig().setBmsirBaseScrollSpeed(137);
        player.getPlayConfig(7).getPlayconfig().setBmsirHispeedReferenceBpm(173);
        player.setBmsirJudgeRankSortEnabled(true);
        player.setBmsirJudgeRankSortSkinNoticeEnabled(false);
        player.setBmsirNumpadActions(new String[]{
                BMSIRNumpadAction.NONE.id(),
                BMSIRNumpadAction.BMS_SEARCH.id(),
                BMSIRNumpadAction.MODE_FILTER.id(),
                BMSIRNumpadAction.JUDGE_MINUS.id(),
                BMSIRNumpadAction.SORT.id(),
                BMSIRNumpadAction.REPLAY.id(),
                BMSIRNumpadAction.KEY_CONFIG.id(),
                BMSIRNumpadAction.SKIN_CONFIG.id(),
                BMSIRNumpadAction.SCREENSHOT.id(),
                BMSIRNumpadAction.JUDGE_PLUS.id()
        });
        player.setBmsirNumpadJudgeTimingStep(7);
        player.setBmsirJudgeTimingRestoreEnabled(true);
        player.setBmsirInfoNotificationsEnabled(false);
        player.getBmsirManiacSettings().setDoubleBattle(true);
        player.getBmsirManiacSettings().setAutoScratch(true);
        player.setBmsirArenaDetailedLogEnabled(true);
        player.setBmsirArenaLanguage("en");
        IRConfig ir = new IRConfig();
        ir.setUserid("arena-user-secret");
        ir.setPassword("arena-password-secret");
        player.setIrconfig(new IRConfig[]{ir});

        PlayerConfig.write(temporaryDirectory.toString(), player);

        Path sidecar = BMSIRArenaConfigStore.sidecarPath(
                temporaryDirectory.toString(),
                "player1"
        );
        String serialized = Files.readString(sidecar);
        assertTrue(serialized.contains("\"enabled\": true"));
        assertTrue(serialized.contains("\"oneBassEnabled\": false"));
        assertTrue(serialized.contains("\"startHerePreviewEnabled\": false"));
        assertTrue(serialized.contains("\"danLocalSyncEnabled\": false"));
        assertTrue(serialized.contains("\"overlayHotkeyFunction\": 9"));
        assertTrue(serialized.contains("\"overlayHotkeyKeys\": ["));
        assertTrue(serialized.contains("\"targetMode\": \"leader\""));
        assertTrue(serialized.contains("\"graphOrder\": \"entry\""));
        assertTrue(serialized.contains("\"schemaVersion\": 20"));
        assertTrue(serialized.contains("\"startButtonAction\": \"difficulty\""));
        assertTrue(serialized.contains("\"selectButtonAction\": \"key_mode\""));
        assertTrue(serialized.contains("\"selectDifficultyDisplay\": \"lr2\""));
        assertTrue(serialized.contains("\"selectDifficultyStage\": 4"));
        assertTrue(serialized.contains("\"selectKeyModes\": ["));
        assertTrue(serialized.contains("\"tableLevelDisplayEnabled\": false"));
        assertTrue(serialized.contains("\"hideMissingTableSongs\": true"));
        assertTrue(serialized.contains("\"physicalFolderFilterEnabled\": true"));
        assertTrue(serialized.contains("\"visiblePhysicalFolderPaths\": ["));
        assertTrue(serialized.contains("\"songs-a\""));
        assertTrue(serialized.contains("\"lastVisibleOverlayMode\": 1"));
        assertTrue(serialized.contains("\"coverControlMode\": \"extended\""));
        assertTrue(serialized.contains("\"coverChangeStep\": 12"));
        assertTrue(serialized.contains("\"coverHispeedAutoAdjustEnabled\": true"));
        assertTrue(serialized.contains("\"lr2HispeedFixEnabled\": true"));
        assertFalse(serialized.contains("pseudoFhsEnabled"));
        assertTrue(serialized.contains("\"baseScrollSpeeds\": ["));
        assertTrue(serialized.contains("\"hispeedReferenceBpms\": ["));
        assertTrue(serialized.contains("137"));
        assertTrue(serialized.contains("173"));
        assertFalse(serialized.contains("iidxFhsEnabled"));
        assertFalse(serialized.contains("iidxFhsSkinNoticeEnabled"));
        assertTrue(serialized.contains("\"judgeRankSortEnabled\": true"));
        assertTrue(serialized.contains("\"judgeRankSortSkinNoticeEnabled\": false"));
        assertTrue(serialized.contains("\"numpadJudgeTimingStep\": 7"));
        assertTrue(serialized.contains("\"judgeTimingRestoreEnabled\": true"));
        assertTrue(serialized.contains("\"infoNotificationsEnabled\": false"));
        assertTrue(serialized.contains("\"doubleBattle\": true"));
        assertTrue(serialized.contains("\"autoScratch\": true"));
        assertTrue(serialized.contains("\"spToDpDifficulty\": 0"));
        assertTrue(serialized.contains("\"detailedLogEnabled\": true"));
        assertTrue(serialized.contains("\"language\": \"en\""));
        assertTrue(serialized.contains("\"bms_search\""));
        assertFalse(serialized.contains("arena-user-secret"));
        assertFalse(serialized.contains("arena-password-secret"));
        assertFalse(serialized.contains("irconfig"));
        assertFalse(serialized.contains("password"));
    }

    @Test
    void sidecarRestoresArenaSettingsAfterCommonConfigWasRewritten() throws Exception {
        PlayerConfig arenaBody = player("player1");
        arenaBody.setBmsirArenaEnabled(true);
        arenaBody.setBmsirArenaAllowHigherSelection(true);
        arenaBody.setBmsirOneBassEnabled(false);
        arenaBody.setBmsirStartHerePreviewEnabled(false);
        arenaBody.setBmsirDanLocalSyncEnabled(false);
        arenaBody.setBmsirStartButtonAction(
                PlayerConfig.BMSIR_SELECT_ACTION_KEY_MODE
        );
        arenaBody.setBmsirSelectButtonAction(
                PlayerConfig.BMSIR_SELECT_ACTION_DIFFICULTY
        );
        arenaBody.setBmsirSelectDifficultyDisplay(
                PlayerConfig.BMSIR_SELECT_DIFFICULTY_DISPLAY_LR2
        );
        arenaBody.setBmsirSelectDifficultyStage(5);
        arenaBody.setBmsirSelectKeyModes(new String[]{"5k", "7k"});
        arenaBody.setBmsirTableLevelDisplayEnabled(false);
        arenaBody.setBmsirHideMissingTableSongs(true);
        arenaBody.setBmsirPhysicalFolderFilterEnabled(true);
        arenaBody.setBmsirVisiblePhysicalFolderPaths(new String[]{"songs-b"});
        arenaBody.setBmsirArenaOverlayHotkeyFunction(8);
        arenaBody.setBmsirArenaOverlayHotkeyModifiers(
                KeyBoardInputProcesseor.MASK_CTRL
        );
        arenaBody.setBmsirArenaOverlayHotkeyKeys(
                new int[]{Keys.CONTROL_RIGHT, Keys.K}
        );
        arenaBody.setBmsirArenaTargetMode(PlayerConfig.BMSIR_ARENA_TARGET_ABOVE);
        arenaBody.setBmsirArenaGraphOrder(PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY);
        arenaBody.setBmsirArenaLastVisibleOverlayMode(1);
        arenaBody.setBmsirArenaOverlayMode(2);
        arenaBody.setBmsirCoverControlMode(PlayerConfig.BMSIR_COVER_CONTROL_LR2);
        arenaBody.setBmsirCoverChangeStep(15);
        arenaBody.setBmsirCoverHispeedAutoAdjustEnabled(true);
        arenaBody.setBmsirLr2HispeedFixEnabled(true);
        arenaBody.getPlayConfig(7).getPlayconfig().setBmsirBaseScrollSpeed(143);
        arenaBody.getPlayConfig(7).getPlayconfig().setBmsirHispeedReferenceBpm(177);
        arenaBody.setBmsirJudgeRankSortEnabled(false);
        arenaBody.setBmsirJudgeRankSortSkinNoticeEnabled(false);
        String[] numpadActions = BMSIRNumpadAction.defaultIds();
        numpadActions[1] = BMSIRNumpadAction.FPS.id();
        arenaBody.setBmsirNumpadActions(numpadActions);
        arenaBody.setBmsirNumpadJudgeTimingStep(4);
        arenaBody.setBmsirJudgeTimingRestoreEnabled(true);
        arenaBody.setBmsirInfoNotificationsEnabled(false);
        arenaBody.getBmsirManiacSettings().setDoubleBattle(true);
        arenaBody.getBmsirManiacSettings().setAutoScratch(true);
        arenaBody.setBmsirArenaDetailedLogEnabled(true);
        arenaBody.setBmsirArenaLanguage("en");
        PlayerConfig.write(temporaryDirectory.toString(), arenaBody);

        PlayerConfig normalBody = player("player1");
        Path commonConfig = temporaryDirectory
                .resolve("player1")
                .resolve("config_player.json");
        RobustFile.write(
                commonConfig,
                PlayerConfig.getConfigJson(normalBody).getBytes(StandardCharsets.UTF_8)
        );

        PlayerConfig restored = PlayerConfig.readPlayerConfig(
                temporaryDirectory.toString(),
                "player1"
        );
        assertTrue(restored.isBmsirArenaEnabled());
        assertTrue(restored.isBmsirArenaAllowHigherSelection());
        assertFalse(restored.isBmsirOneBassEnabled());
        assertFalse(restored.isBmsirStartHerePreviewEnabled());
        assertFalse(restored.isBmsirDanLocalSyncEnabled());
        assertEquals(
                PlayerConfig.BMSIR_SELECT_ACTION_KEY_MODE,
                restored.getBmsirStartButtonAction()
        );
        assertEquals(
                PlayerConfig.BMSIR_SELECT_ACTION_DIFFICULTY,
                restored.getBmsirSelectButtonAction()
        );
        assertEquals(
                PlayerConfig.BMSIR_SELECT_DIFFICULTY_DISPLAY_LR2,
                restored.getBmsirSelectDifficultyDisplay()
        );
        assertEquals(5, restored.getBmsirSelectDifficultyStage());
        assertEquals(
                java.util.List.of("5k", "7k"),
                java.util.Arrays.asList(restored.getBmsirSelectKeyModes())
        );
        assertFalse(restored.isBmsirTableLevelDisplayEnabled());
        assertTrue(restored.isBmsirHideMissingTableSongs());
        assertTrue(restored.isBmsirPhysicalFolderFilterEnabled());
        assertArrayEquals(
                new String[]{"songs-b"},
                restored.getBmsirVisiblePhysicalFolderPaths()
        );
        assertEquals(8, restored.getBmsirArenaOverlayHotkeyFunction());
        assertEquals(
                KeyBoardInputProcesseor.MASK_CTRL,
                restored.getBmsirArenaOverlayHotkeyModifiers()
        );
        assertEquals(
                java.util.List.of(Keys.CONTROL_LEFT, Keys.K),
                java.util.Arrays.stream(restored.getBmsirArenaOverlayHotkeyKeys())
                        .boxed()
                        .toList()
        );
        assertEquals(
                PlayerConfig.BMSIR_ARENA_TARGET_ABOVE,
                restored.getBmsirArenaTargetMode()
        );
        assertEquals(
                PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY,
                restored.getBmsirArenaGraphOrder()
        );
        assertEquals(1, restored.getBmsirArenaLastVisibleOverlayMode());
        assertEquals(
                PlayerConfig.BMSIR_COVER_CONTROL_LR2,
                restored.getBmsirCoverControlMode()
        );
        assertEquals(15, restored.getBmsirCoverChangeStep());
        assertTrue(restored.isBmsirCoverHispeedAutoAdjustEnabled());
        assertTrue(restored.isBmsirLr2HispeedFixEnabled());
        assertEquals(
                143,
                restored.getPlayConfig(7).getPlayconfig().getBmsirBaseScrollSpeed()
        );
        assertEquals(
                177,
                restored.getPlayConfig(7).getPlayconfig().getBmsirHispeedReferenceBpm()
        );
        assertFalse(restored.isBmsirJudgeRankSortEnabled());
        assertFalse(restored.isBmsirJudgeRankSortSkinNoticeEnabled());
        assertEquals(
                BMSIRNumpadAction.FPS.id(),
                restored.getBmsirNumpadActions()[1]
        );
        assertEquals(4, restored.getBmsirNumpadJudgeTimingStep());
        assertTrue(restored.isBmsirJudgeTimingRestoreEnabled());
        assertFalse(restored.isBmsirInfoNotificationsEnabled());
        assertTrue(restored.getBmsirManiacSettings().isDoubleBattle());
        assertTrue(restored.getBmsirManiacSettings().isAutoScratch());
        assertTrue(restored.isBmsirArenaDetailedLogEnabled());
        assertEquals("en", restored.getBmsirArenaLanguage());
    }

    @Test
    void corruptPrimarySidecarFallsBackToRobustBackup() throws Exception {
        PlayerConfig saved = player("player1");
        saved.setBmsirArenaEnabled(true);
        assertTrue(BMSIRArenaConfigStore.write(
                temporaryDirectory.toString(),
                saved
        ));
        Path sidecar = BMSIRArenaConfigStore.sidecarPath(
                temporaryDirectory.toString(),
                "player1"
        );
        Files.writeString(sidecar, "{broken", StandardCharsets.UTF_8);

        PlayerConfig restored = player("player1");
        assertTrue(BMSIRArenaConfigStore.loadOrMigrate(
                temporaryDirectory.toString(),
                "player1",
                restored
        ));
        assertTrue(restored.isBmsirArenaEnabled());
    }

    @Test
    void missingSidecarMigratesExistingArenaValuesOnce() throws Exception {
        PlayerConfig legacy = player("player1");
        legacy.setBmsirArenaEnabled(true);
        legacy.setBmsirArenaStayInRoom(false);

        assertTrue(BMSIRArenaConfigStore.loadOrMigrate(
                temporaryDirectory.toString(),
                "player1",
                legacy
        ));
        assertTrue(Files.isRegularFile(BMSIRArenaConfigStore.sidecarPath(
                temporaryDirectory.toString(),
                "player1"
        )));

        PlayerConfig restored = player("player1");
        assertTrue(BMSIRArenaConfigStore.loadOrMigrate(
                temporaryDirectory.toString(),
                "player1",
                restored
        ));
        assertTrue(restored.isBmsirArenaEnabled());
        assertFalse(restored.isBmsirArenaStayInRoom());
    }

    @Test
    void existingPlayerIsSelectedWhenSystemConfigHasNoPlayerId() throws Exception {
        PlayerConfig.create(temporaryDirectory.toString(), "player1");
        Config config = new Config();
        config.setPlayerpath(temporaryDirectory.toString());
        config.setPlayername(null);

        PlayerConfig.init(config);

        assertEquals("player1", config.getPlayername());
        assertTrue(Files.isRegularFile(BMSIRArenaConfigStore.sidecarPath(
                temporaryDirectory.toString(),
                "player1"
        )));
    }

    @Test
    void missingPlayerIdDoesNotResolveAnInvalidSidecarPath() {
        assertFalse(BMSIRArenaConfigStore.loadOrMigrate(
                temporaryDirectory.toString(),
                null,
                new PlayerConfig()
        ));
    }

    @Test
    void legacySidecarMigratesFunctionAndModifiersIntoAChord() throws Exception {
        Path playerDirectory = temporaryDirectory.resolve("player1");
        Files.createDirectories(playerDirectory);
        RobustFile.write(
                playerDirectory.resolve("bmsir_arena.json"),
                """
                {
                  "schemaVersion": 1,
                  "overlayHotkeyFunction": 12,
                  "overlayHotkeyModifiers": 6
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        PlayerConfig restored = player("player1");
        assertTrue(BMSIRArenaConfigStore.loadOrMigrate(
                temporaryDirectory.toString(),
                "player1",
                restored
        ));
        assertEquals(
                java.util.List.of(Keys.CONTROL_LEFT, Keys.ALT_LEFT, Keys.F12),
                java.util.Arrays.stream(restored.getBmsirArenaOverlayHotkeyKeys())
                        .boxed()
                        .toList()
        );
        assertTrue(restored.isBmsirOneBassEnabled());
        assertTrue(restored.isBmsirStartHerePreviewEnabled());
        assertTrue(restored.isBmsirDanLocalSyncEnabled());
        assertEquals(
                PlayerConfig.BMSIR_SELECT_ACTION_OPTION,
                restored.getBmsirSelectButtonAction()
        );
        assertEquals(
                PlayerConfig.BMSIR_SELECT_DIFFICULTY_DISPLAY_SEPARATE,
                restored.getBmsirSelectDifficultyDisplay()
        );
        assertEquals(
                java.util.List.of(
                        "all", "7k", "14k", "9k", "5k", "10k", "24k", "24k_dp"
                ),
                java.util.Arrays.asList(restored.getBmsirSelectKeyModes())
        );
        assertTrue(restored.isBmsirTableLevelDisplayEnabled());
        assertFalse(restored.isBmsirPhysicalFolderFilterEnabled());
        assertArrayEquals(new String[0], restored.getBmsirVisiblePhysicalFolderPaths());
        assertEquals(
                PlayerConfig.BMSIR_COVER_CONTROL_ORAJA,
                restored.getBmsirCoverControlMode()
        );
        assertEquals(10, restored.getBmsirCoverChangeStep());
        assertFalse(restored.isBmsirCoverHispeedAutoAdjustEnabled());
        assertFalse(restored.isBmsirLr2HispeedFixEnabled());
        assertArrayEquals(
                new int[]{100, 100, 100, 100, 100, 100, 100},
                restored.getBmsirBaseScrollSpeeds()
        );
        assertArrayEquals(
                new int[]{150, 150, 150, 150, 150, 150, 150},
                restored.getBmsirHispeedReferenceBpms()
        );
        assertTrue(restored.isBmsirJudgeRankSortEnabled());
        assertTrue(restored.isBmsirJudgeRankSortSkinNoticeEnabled());
        assertEquals(
                java.util.List.of(
                        "judge_auto",
                        "none",
                        "none",
                        "judge_minus",
                        "none",
                        "none",
                        "none",
                        "skin_config",
                        "same_folder",
                        "judge_plus"
                ),
                java.util.Arrays.asList(restored.getBmsirNumpadActions())
        );
        assertEquals(1, restored.getBmsirNumpadJudgeTimingStep());
        assertFalse(restored.isBmsirJudgeTimingRestoreEnabled());
        assertTrue(restored.isBmsirInfoNotificationsEnabled());
    }

    @Test
    void removedFhsSidecarKeysAreIgnoredAndDroppedOnRewrite() throws Exception {
        Path playerDirectory = temporaryDirectory.resolve("player1");
        Files.createDirectories(playerDirectory);
        RobustFile.write(
                playerDirectory.resolve("bmsir_arena.json"),
                """
                {
                  "schemaVersion": 15,
                  "iidxFhsEnabled": true,
                  "iidxFhsSkinNoticeEnabled": false,
                  "pseudoFhsEnabled": true,
                  "judgeRankSortEnabled": false
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        PlayerConfig restored = player("player1");
        assertTrue(BMSIRArenaConfigStore.loadOrMigrate(
                temporaryDirectory.toString(),
                "player1",
                restored
        ));
        assertFalse(restored.isBmsirJudgeRankSortEnabled());
        assertTrue(BMSIRArenaConfigStore.write(
                temporaryDirectory.toString(),
                restored
        ));

        String rewritten = Files.readString(
                playerDirectory.resolve("bmsir_arena.json")
        );
        assertFalse(rewritten.contains("iidxFhsEnabled"));
        assertFalse(rewritten.contains("iidxFhsSkinNoticeEnabled"));
        assertFalse(rewritten.contains("pseudoFhsEnabled"));
    }

    private PlayerConfig player(String id) throws Exception {
        Files.createDirectories(temporaryDirectory.resolve(id));
        PlayerConfig player = new PlayerConfig();
        player.setId(id);
        return player;
    }
}
