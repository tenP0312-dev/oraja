package bms.player.beatoraja.system;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientLogDirectoryTest {
    @Test
    void resolvesClientDiagnosticsUnderPortableLogsDirectory() {
        assertEquals(
                Paths.get("logs").toAbsolutePath().normalize(),
                ClientLogDirectory.path()
        );
        assertEquals(
                ClientLogDirectory.path().resolve("beatoraja_log.xml"),
                ClientLogDirectory.resolve("beatoraja_log.xml")
        );
        assertEquals(
                ClientLogDirectory.path().resolve("bmsir-arena.log"),
                ClientLogDirectory.resolve("bmsir-arena.log")
        );
    }
}
