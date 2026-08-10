package bms.player.beatoraja.select;

import bms.model.Mode;
import bms.player.beatoraja.select.bar.Bar;
import bms.player.beatoraja.select.bar.SongBar;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BarManagerDifficultyGroupingTest {
    @Test
    void groupsOnlyChartsFromTheSameFolderAndKeyMode() {
        SongData normal = song("normal", "same-folder", Mode.BEAT_7K.id, 2, 5);
        SongData hyper = song("hyper", "same-folder", Mode.BEAT_7K.id, 3, 9);
        SongData doubleChart = song("double", "same-folder", Mode.BEAT_14K.id, 3, 9);
        SongData unrelated = song("unrelated", "other-folder", Mode.BEAT_7K.id, 1, 3);

        Bar[] grouped = BarManager.groupDifficultyBars(new Bar[]{
                new SongBar(hyper),
                new SongBar(doubleChart),
                new SongBar(normal),
                new SongBar(unrelated)
        }, normal.getSha256());

        assertEquals(3, grouped.length);
        SongBar sameSong = (SongBar) grouped[0];
        assertEquals(2, sameSong.getDifficultyVariantCount());
        assertSame(normal, sameSong.getSongData());
        assertSame(doubleChart, ((SongBar) grouped[1]).getSongData());
        assertSame(unrelated, ((SongBar) grouped[2]).getSongData());
    }

    @Test
    void cyclesDifficultyThenLevelAndWraps() {
        SongData another = song("another", "folder", Mode.BEAT_7K.id, 3, 8);
        SongData normal = song("normal", "folder", Mode.BEAT_7K.id, 2, 5);
        SongData hyper = song("hyper", "folder", Mode.BEAT_7K.id, 3, 9);
        SongBar grouped = new SongBar(
                new SongData[]{hyper, normal, another},
                null
        );

        assertSame(normal, grouped.getSongData());
        grouped.cycleDifficulty();
        assertSame(another, grouped.getSongData());
        grouped.cycleDifficulty();
        assertSame(hyper, grouped.getSongData());
        grouped.cycleDifficulty();
        assertSame(normal, grouped.getSongData());
    }

    @Test
    void blankFoldersRemainSeparateCharts() {
        SongData first = song("first", "", Mode.BEAT_7K.id, 1, 1);
        SongData second = song("second", "", Mode.BEAT_7K.id, 2, 2);

        Bar[] grouped = BarManager.groupDifficultyBars(
                new Bar[]{new SongBar(first), new SongBar(second)},
                null
        );

        assertEquals(2, grouped.length);
        assertEquals(
                Arrays.asList(first, second),
                Arrays.stream(grouped)
                        .map(bar -> ((SongBar) bar).getSongData())
                        .toList()
        );
    }

    private static SongData song(
            String hash,
            String folder,
            int mode,
            int difficulty,
            int level
    ) {
        SongData song = new SongData();
        song.setTitle(hash);
        song.setSha256(hash);
        song.setFolder(folder);
        song.setMode(mode);
        song.setDifficulty(difficulty);
        song.setLevel(level);
        song.setPath("/songs/" + folder + "/" + hash + ".bms");
        return song;
    }
}
