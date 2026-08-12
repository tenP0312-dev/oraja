package bms.player.beatoraja.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared location for diagnostic logs owned by the client body.
 */
public final class ClientLogDirectory {
    private static final Path DIRECTORY = Paths.get("logs")
            .toAbsolutePath()
            .normalize();

    private ClientLogDirectory() {
    }

    public static Path path() {
        return DIRECTORY;
    }

    public static Path resolve(String fileName) {
        return DIRECTORY.resolve(fileName);
    }

    public static Path create() throws IOException {
        return Files.createDirectories(DIRECTORY);
    }
}
