package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.song.SongData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRPrimaryIrTableCacheTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesAndReadsValidTablesPerPlayer() {
        TableData[] source = {table("BMS-IR Rival", "chart")};

        assertTrue(BMSIRPrimaryIrTableCache.sync(
                temporaryDirectory.toString(),
                "player1",
                source
        ));

        TableData[] restored = BMSIRPrimaryIrTableCache.read(
                temporaryDirectory.toString(),
                "player1"
        );
        assertEquals(1, restored.length);
        assertEquals("BMS-IR Rival", restored[0].getName());
        assertEquals("chart", restored[0].getFolder()[0].getSong()[0].getTitle());
        assertEquals(Mode.BEAT_7K.id, restored[0].getFolder()[0].getSong()[0].getMode());
        assertEquals(0, BMSIRPrimaryIrTableCache.read(
                temporaryDirectory.toString(),
                "player2"
        ).length);
    }

    @Test
    void invalidOrEmptyRefreshKeepsTheLastGoodFile() throws Exception {
        TableData[] source = {table("BMS-IR Dan", "course chart")};
        assertTrue(BMSIRPrimaryIrTableCache.sync(
                temporaryDirectory.toString(),
                "player1",
                source
        ));
        Path path = BMSIRPrimaryIrTableCache.cachePath(
                temporaryDirectory.toString(),
                "player1"
        );
        byte[] before = Files.readAllBytes(path);

        assertFalse(BMSIRPrimaryIrTableCache.sync(
                temporaryDirectory.toString(),
                "player1",
                new TableData[0]
        ));
        assertArrayEquals(before, Files.readAllBytes(path));
    }

    @Test
    void corruptPrimaryFileFallsBackToTheBackup() throws Exception {
        assertTrue(BMSIRPrimaryIrTableCache.sync(
                temporaryDirectory.toString(),
                "player1",
                new TableData[]{table("BMS-IR Popular", "saved")}
        ));
        Path path = BMSIRPrimaryIrTableCache.cachePath(
                temporaryDirectory.toString(),
                "player1"
        );
        Files.writeString(path, "{broken", StandardCharsets.UTF_8);

        TableData[] restored = BMSIRPrimaryIrTableCache.read(
                temporaryDirectory.toString(),
                "player1"
        );
        assertEquals(1, restored.length);
        assertEquals("BMS-IR Popular", restored[0].getName());
    }

    private static TableData table(String name, String title) {
        SongData song = new SongData();
        song.setMd5("11111111111111111111111111111111");
        song.setTitle(title);
        song.setMode(Mode.BEAT_7K.id);

        TableData.TableFolder folder = new TableData.TableFolder();
        folder.setName("folder");
        folder.setSong(new SongData[]{song});

        TableData table = new TableData();
        table.setName(name);
        table.setFolder(new TableData.TableFolder[]{folder});
        return table;
    }
}
