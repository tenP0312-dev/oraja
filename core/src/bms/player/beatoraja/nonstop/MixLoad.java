package bms.player.beatoraja.nonstop;

import bms.player.beatoraja.play.bga.BGAProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * How heavy a compiled set is for the BGA player (Issue #556). The player loads
 * every BGA of the merged chart when play starts (all movies are opened and the
 * image cache holds 256 pictures), so a long set with many BGA files can run out
 * of memory, stutter or drop pictures. This only estimates; it never changes the set.
 *
 * @param songs    number of songs in the set
 * @param totalMs  length of the set (ms)
 * @param movies   BGA movie files of all songs
 * @param images   BGA image files of all songs
 */
public record MixLoad(int songs, long totalMs, int movies, int images) {
    /** More songs than this is a long set (sounds are also loaded up front). */
    public static final int SONG_LIMIT = 12;
    /** Longer than this is a long set. */
    public static final long LENGTH_LIMIT_MS = 20 * 60_000L;
    /** This many movies are opened at once when play starts. */
    public static final int MOVIE_LIMIT = 6;
    /** Close to the player's image cache of 256 pictures. */
    public static final int IMAGE_LIMIT = 200;

    private static final List<String> MOVIE_EXTENSIONS = Arrays.asList(BGAProcessor.mov_extension);

    /** Counts the BGA files of every song, as the BGA player would load them. */
    static MixLoad of(int songs, long totalMs, List<String[]> bgaLists) {
        int movies = 0;
        int images = 0;
        for (String[] list : bgaLists) {
            if (list == null) {
                continue;
            }
            for (String name : list) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (isMovie(name)) {
                    movies++;
                } else {
                    images++;
                }
            }
        }
        return new MixLoad(songs, totalMs, movies, images);
    }

    static boolean isMovie(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && MOVIE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public boolean longSet() {
        return songs > SONG_LIMIT || totalMs > LENGTH_LIMIT_MS;
    }

    public boolean heavyBga() {
        return movies >= MOVIE_LIMIT || images >= IMAGE_LIMIT;
    }

    /** True when the set uses BGA and is long or BGA-heavy, so turning BGA off is worth suggesting. */
    public boolean suggestNoBga(boolean noBga) {
        return !noBga && movies + images > 0 && (longSet() || heavyBga());
    }

    /** Why the set is heavy, for the warning text. */
    public String reason(boolean japanese) {
        List<String> parts = new ArrayList<>();
        if (songs > SONG_LIMIT) {
            parts.add(japanese ? songs + "曲" : songs + " songs");
        }
        if (totalMs > LENGTH_LIMIT_MS) {
            parts.add(japanese ? "約" + totalMs / 60_000 + "分" : "about " + totalMs / 60_000 + " min");
        }
        if (movies > 0) {
            parts.add(japanese ? "BGA動画" + movies + "本" : movies + " BGA movies");
        }
        if (images > 0) {
            parts.add(japanese ? "BGA画像" + images + "枚" : images + " BGA images");
        }
        return String.join(japanese ? "・" : ", ", parts);
    }
}
