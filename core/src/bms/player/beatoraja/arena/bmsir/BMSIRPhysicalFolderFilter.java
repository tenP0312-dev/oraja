package bms.player.beatoraja.arena.bmsir;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Root-level physical-folder visibility policy for Music Select. */
public final class BMSIRPhysicalFolderFilter {
    private BMSIRPhysicalFolderFilter() {
    }

    /**
     * Keeps stored paths stable and ordered while removing null, blank, and exact
     * duplicate entries. Path identity is resolved only when visibility is tested.
     */
    public static String[] normalizeSelections(String[] paths) {
        if (paths == null || paths.length == 0) {
            return new String[0];
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String path : paths) {
            if (path != null && !path.isBlank()) {
                normalized.add(path);
            }
        }
        return normalized.toArray(String[]::new);
    }

    /** OFF preserves the legacy all-visible behavior; ON is an explicit allow-list. */
    public static boolean isVisible(
            String folderPath,
            boolean filterEnabled,
            String[] visibleFolderPaths
    ) {
        if (!filterEnabled) {
            return true;
        }
        if (folderPath == null || folderPath.isBlank()) {
            return false;
        }
        for (String selected : normalizeSelections(visibleFolderPaths)) {
            if (samePath(folderPath, selected)) {
                return true;
            }
        }
        return false;
    }

    /** Matches relative and absolute spellings against the current installation root. */
    public static boolean samePath(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        try {
            return Path.of(first).toAbsolutePath().normalize()
                    .equals(Path.of(second).toAbsolutePath().normalize());
        } catch (InvalidPathException ignored) {
            return first.equals(second);
        }
    }

    public static boolean containsPath(Iterable<String> paths, String target) {
        if (paths == null) {
            return false;
        }
        for (String path : paths) {
            if (samePath(path, target)) {
                return true;
            }
        }
        return false;
    }
}
