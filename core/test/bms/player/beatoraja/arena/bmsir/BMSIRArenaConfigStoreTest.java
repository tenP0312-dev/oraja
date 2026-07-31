package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.input.KeyBoardInputProcesseor;
import bms.player.beatoraja.system.RobustFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
        player.setBmsirArenaOverlayHotkeyFunction(9);
        player.setBmsirArenaOverlayHotkeyModifiers(
                KeyBoardInputProcesseor.MASK_ALT
        );
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
        assertTrue(serialized.contains("\"overlayHotkeyFunction\": 9"));
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
        arenaBody.setBmsirArenaOverlayHotkeyFunction(8);
        arenaBody.setBmsirArenaOverlayHotkeyModifiers(
                KeyBoardInputProcesseor.MASK_CTRL
        );
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
        assertEquals(8, restored.getBmsirArenaOverlayHotkeyFunction());
        assertEquals(
                KeyBoardInputProcesseor.MASK_CTRL,
                restored.getBmsirArenaOverlayHotkeyModifiers()
        );
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

    private PlayerConfig player(String id) throws Exception {
        Files.createDirectories(temporaryDirectory.resolve(id));
        PlayerConfig player = new PlayerConfig();
        player.setId(id);
        return player;
    }
}
