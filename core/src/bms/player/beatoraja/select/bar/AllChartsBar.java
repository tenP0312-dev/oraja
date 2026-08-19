package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.song.SongData;

import java.util.Arrays;

/**
 * Virtual folder containing only the chart variants retained by one grouped
 * song bar.
 */
public final class AllChartsBar extends ContainerBar {

    public AllChartsBar(SongBar source) {
        super(source.getTitle(), createChildren(source));
        setSortable(false);
    }

    private static Bar[] createChildren(SongBar source) {
        return Arrays.stream(source.getDifficultyVariants())
                .map(song -> chartBar(source, song))
                .toArray(Bar[]::new);
    }

    private static SongBar chartBar(SongBar source, SongData song) {
        return new SongBar(
                song,
                source.getTableDisplayLevel(song),
                source.getTableComment(song)
        );
    }

    @Override
    public boolean preservesChildSongBars() {
        return true;
    }
}
