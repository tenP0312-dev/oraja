package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.song.SongData;

import java.util.LinkedHashSet;
import java.util.Set;

/** Safe key-mode summary for root difficulty-table visibility. */
public final class BMSIRTableKeyModeFilter {
    private BMSIRTableKeyModeFilter() {
    }

    public record TableModes(Set<Integer> knownModeIds, boolean hasUnresolvedMode) {
        public TableModes {
            knownModeIds = Set.copyOf(knownModeIds);
        }

        public boolean isVisible(Mode selectedMode) {
            return selectedMode == null
                    || hasUnresolvedMode
                    || knownModeIds.isEmpty()
                    || knownModeIds.contains(selectedMode.id);
        }
    }

    public static TableModes analyze(TableData table) {
        if (table == null) {
            return new TableModes(Set.of(), true);
        }
        Set<Integer> knownModes = new LinkedHashSet<>();
        boolean unresolved = false;
        TableData.TableFolder[] folders = table.getFolder();
        if (folders == null) {
            unresolved = true;
        } else {
            for (TableData.TableFolder folder : folders) {
                unresolved |= folder == null
                        || collectModes(folder.getSong(), knownModes);
            }
        }
        CourseData[] courses = table.getCourse();
        if (courses == null) {
            unresolved = true;
        } else {
            for (CourseData course : courses) {
                unresolved |= course == null
                        || collectModes(course.getSong(), knownModes);
            }
        }
        return new TableModes(knownModes, unresolved);
    }

    private static boolean collectModes(SongData[] songs, Set<Integer> knownModes) {
        if (songs == null) {
            return true;
        }
        boolean unresolved = false;
        for (SongData song : songs) {
            if (song == null || !isKnownModeId(song.getMode())) {
                unresolved = true;
            } else {
                knownModes.add(song.getMode());
            }
        }
        return unresolved;
    }

    private static boolean isKnownModeId(int modeId) {
        for (Mode mode : Mode.values()) {
            if (mode.id == modeId) {
                return true;
            }
        }
        return false;
    }
}
