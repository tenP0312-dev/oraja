package bms.player.beatoraja.bmsir;

import bms.model.BMSModel;
import bms.player.beatoraja.song.SongData;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Work-folder policy for authoring and other disposable test plays.
 *
 * <p>The legacy marker comparison is based on path components so that both
 * separators and archive virtual paths are supported. The configured work
 * directory uses normalized native paths, matching the platform where the
 * launcher selected and stored the BMS root.
 */
public final class BMSIRTestPlayFolder {
    public static final String DIRECTORY_NAME = "_BMSIR_TESTPLAY";

    private BMSIRTestPlayFolder() {
    }

    public static boolean contains(String path) {
        return contains(path, "");
    }

    public static boolean contains(String path, String workDirectory) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        for (String component : normalized.split("/+")) {
            if (DIRECTORY_NAME.equalsIgnoreCase(component)) {
                return true;
            }
        }
        return isBelowConfiguredWorkDirectory(path, workDirectory);
    }

    public static boolean contains(BMSModel model) {
        return model != null && contains(model.getPath());
    }

    public static boolean contains(BMSModel model, String workDirectory) {
        return model != null && contains(model.getPath(), workDirectory);
    }

    public static boolean contains(SongData song) {
        return song != null && contains(song.getPath());
    }

    public static boolean contains(SongData song, String workDirectory) {
        return song != null && contains(song.getPath(), workDirectory);
    }

    public static boolean containsAny(BMSModel[] models) {
        return containsAny(models, "");
    }

    public static boolean containsAny(BMSModel[] models, String workDirectory) {
        if (models == null) {
            return false;
        }
        for (BMSModel model : models) {
            if (contains(model, workDirectory)) {
                return true;
            }
        }
        return false;
    }

    static boolean isBelowConfiguredWorkDirectory(String path, String workDirectory) {
        if (workDirectory == null || workDirectory.isBlank()) {
            return false;
        }
        try {
            Path chart = Path.of(path).toAbsolutePath().normalize();
            Path configured = Path.of(workDirectory).toAbsolutePath().normalize();
            return chart.startsWith(configured);
        } catch (InvalidPathException exception) {
            return false;
        }
    }
}
