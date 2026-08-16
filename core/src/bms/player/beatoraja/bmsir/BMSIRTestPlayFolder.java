package bms.player.beatoraja.bmsir;

import bms.model.BMSModel;
import bms.player.beatoraja.song.SongData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reserved folder policy for authoring and other disposable test plays.
 *
 * <p>The comparison is deliberately based on path components rather than the
 * host file system. Song paths can use Windows separators while running on
 * another platform, and archive-backed charts use virtual path components.
 */
public final class BMSIRTestPlayFolder {
    public static final String DIRECTORY_NAME = "_BMSIR_TESTPLAY";

    private BMSIRTestPlayFolder() {
    }

    public static boolean contains(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        for (String component : normalized.split("/+")) {
            if (DIRECTORY_NAME.equalsIgnoreCase(component)) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(BMSModel model) {
        return model != null && contains(model.getPath());
    }

    public static boolean contains(SongData song) {
        return song != null && contains(song.getPath());
    }

    public static boolean containsAny(BMSModel[] models) {
        if (models == null) {
            return false;
        }
        for (BMSModel model : models) {
            if (contains(model)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates the reserved child below an existing configured BMS root.
     * Existing test-play directories are accepted unchanged.
     */
    public static Path createUnder(Path root) throws IOException {
        if (root == null) {
            throw new IOException("BMS root is not configured");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            throw new IOException("BMS root is not an existing directory: " + normalizedRoot);
        }
        Path testPlayDirectory = normalizedRoot.resolve(DIRECTORY_NAME);
        Files.createDirectories(testPlayDirectory);
        if (!Files.isDirectory(testPlayDirectory)) {
            throw new IOException("Test-play path is not a directory: " + testPlayDirectory);
        }
        return testPlayDirectory;
    }
}
