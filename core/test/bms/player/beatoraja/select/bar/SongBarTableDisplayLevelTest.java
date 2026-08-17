package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SongBarTableDisplayLevelTest {
    @Test
    void keepsTableLevelsOnBarsWithoutChangingLocalPlayLevel() {
        SongData local = localSong("a", 12);
        SongBar levelSix = SongBar.toSongBarArray(
                new SongData[]{local},
                new SongData[]{tableEntry("a", 6)},
                null
        )[0];
        SongBar levelNine = SongBar.toSongBarArray(
                new SongData[]{local},
                new SongData[]{tableEntry("a", 9)},
                null
        )[0];

        assertEquals(6, levelSix.getDisplayLevel());
        assertEquals(9, levelNine.getDisplayLevel());
        assertEquals(12, local.getLevel());
        assertNull(local.getTableLevel());
    }

    @Test
    void preservesEachEntryLevelInsideAnAggregateFolder() {
        SongData first = localSong("a", 11);
        SongData second = localSong("b", 12);

        SongBar[] aggregate = SongBar.toSongBarArray(
                new SongData[]{first, second},
                new SongData[]{tableEntry("a", 6), tableEntry("b", 1)},
                null
        );

        assertEquals(6, displayLevelFor(aggregate, "a"));
        assertEquals(1, displayLevelFor(aggregate, "b"));
    }

    @Test
    void showsTableLevelForMissingSongsAndSupportsLegacyFolderFallback() {
        SongData missing = tableEntry("missing", 6);
        SongBar missingBar = SongBar.toSongBarArray(
                SongData.EMPTY,
                new SongData[]{missing},
                null
        )[0];
        SongData legacy = tableEntry("legacy", null);
        SongBar legacyBar = SongBar.toSongBarArray(
                new SongData[]{localSong("legacy", 12)},
                new SongData[]{legacy},
                4
        )[0];

        assertFalse(missingBar.existsSong());
        assertEquals(6, missingBar.getDisplayLevel());
        assertEquals(4, legacyBar.getDisplayLevel());
    }

    @Test
    void disabledTableLevelDisplayUsesTheLocalPlayLevel() {
        SongData local = localSong("local", 12);
        SongBar localBar = SongBar.toSongBarArray(
                new SongData[]{local},
                new SongData[]{tableEntry("local", 6)},
                null,
                false
        )[0];
        SongBar missingBar = SongBar.toSongBarArray(
                SongData.EMPTY,
                new SongData[]{tableEntry("missing", 9)},
                null,
                false
        )[0];

        assertEquals(12, localBar.getDisplayLevel());
        assertFalse(localBar.hasTableDisplayLevel());
        assertFalse(missingBar.hasTableDisplayLevel());
    }

    private static int displayLevelFor(SongBar[] bars, String sha256) {
        for (SongBar bar : bars) {
            if (sha256.equals(bar.getSongData().getSha256())) {
                return bar.getDisplayLevel();
            }
        }
        throw new AssertionError("missing bar " + sha256);
    }

    private static SongData localSong(String sha256, int level) {
        SongData song = tableEntry(sha256, null);
        song.setLevel(level);
        song.setPath("/songs/" + sha256 + ".bms");
        return song;
    }

    private static SongData tableEntry(String sha256, Integer tableLevel) {
        SongData song = new SongData();
        song.setTitle(sha256);
        song.setSha256(sha256);
        song.setTableLevel(tableLevel);
        return song;
    }
}
