package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllChartsBarTest {
    @Test
    void containsOnlyRetainedVariantsAsSeparateBars() {
        SongData normal = song("normal", 2, 5);
        SongData hyper = song("hyper", 3, 9);
        SongData unrelated = song("unrelated", 4, 11);
        Map<SongData, Integer> tableLevels = new IdentityHashMap<>();
        tableLevels.put(normal, 4);
        tableLevels.put(hyper, 8);
        SongBar grouped = new SongBar(
                new SongData[]{hyper, normal},
                hyper.getSha256(),
                3,
                tableLevels
        );

        AllChartsBar allCharts = new AllChartsBar(grouped);
        Bar[] children = allCharts.getChildren();

        assertEquals(2, children.length);
        assertEquals(
                Arrays.asList(normal, hyper),
                Arrays.stream(children)
                        .map(child -> ((SongBar) child).getSongData())
                        .toList()
        );
        assertFalse(Arrays.stream(children)
                .map(child -> ((SongBar) child).getSongData())
                .anyMatch(unrelated::equals));
        assertTrue(Arrays.stream(children)
                .allMatch(child -> ((SongBar) child).getDifficultyVariantCount() == 1));
        assertEquals(4, ((SongBar) children[0]).getDisplayLevel());
        assertEquals(8, ((SongBar) children[1]).getDisplayLevel());
        assertFalse(allCharts.isSortable());
        assertTrue(allCharts.preservesChildSongBars());
        assertSame(hyper, grouped.getSongData());
    }

    private static SongData song(String hash, int difficulty, int level) {
        SongData song = new SongData();
        song.setTitle(hash);
        song.setSha256(hash);
        song.setFolder("same-folder");
        song.setMode(7);
        song.setDifficulty(difficulty);
        song.setLevel(level);
        song.setPath("/songs/same-folder/" + hash + ".bms");
        return song;
    }
}
