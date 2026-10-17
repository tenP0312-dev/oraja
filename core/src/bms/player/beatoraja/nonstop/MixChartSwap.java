package bms.player.beatoraja.nonstop;

import bms.player.beatoraja.song.SongData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Swaps the chart (差分) of a track for another chart of the same song, one at
 * a time from the editor or for a whole set at once (Issue #535). Another chart
 * of the same song is any chart of the same key mode in the same song folder.
 */
public final class MixChartSwap {
    /** A track keeps at least this much of its used part after a swap. */
    private static final long MIN_PART_MS = 1000L;

    /** Easier first: level, then note count, then the chart's #DIFFICULTY. */
    public static final Comparator<SongData> EASIER_FIRST = Comparator.comparingInt(SongData::getLevel)
            .thenComparingInt(SongData::getNotes)
            .thenComparingInt(SongData::getDifficulty);

    /** Where the charts of the player's library come from. */
    public interface Charts {
        /** The chart a track uses now, or null when it is not in the library. */
        SongData current(MixTrack track);

        /** Every chart in the same song folder as the given chart (it may be included). */
        SongData[] sameFolder(SongData chart);
    }

    /** The result of {@link #shiftSet}. */
    public record Shifted(MixPlan plan, List<MixTrack> unchanged) {
    }

    private MixChartSwap() {
    }

    /** A library backed by the song database. */
    public static Charts charts(MixLibrary library) {
        return new Charts() {
            @Override
            public SongData current(MixTrack track) {
                return library.find(track);
            }

            @Override
            public SongData[] sameFolder(SongData chart) {
                if (chart.getFolder() == null || chart.getFolder().isEmpty() || library.main() == null
                        || library.main().getSongDatabase() == null) {
                    return new SongData[] {chart};
                }
                return library.main().getSongDatabase().getSongDatas("folder", chart.getFolder());
            }
        };
    }

    /**
     * The other playable charts of the same song and key mode, easier first.
     * Duplicates of one chart and the current chart itself are left out.
     */
    public static List<SongData> alternatives(SongData current, SongData[] folder, int mode) {
        List<SongData> result = new ArrayList<>();
        if (current == null || folder == null) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        seen.add(key(current));
        for (SongData chart : folder) {
            if (chart == null || chart.getPath() == null || chart.getMode() != mode) {
                continue;
            }
            if (seen.add(key(chart))) {
                result.add(chart);
            }
        }
        result.sort(EASIER_FIRST);
        return result;
    }

    /**
     * The chart one step easier (direction &lt; 0) or harder (direction &gt; 0)
     * than the current one, or null when there is none. Charts that rank the same
     * as the current one are not a step.
     */
    public static SongData step(SongData current, List<SongData> alternatives, int direction) {
        SongData best = null;
        for (SongData chart : alternatives) {
            int order = EASIER_FIRST.compare(chart, current);
            if (direction < 0 && order < 0 && (best == null || EASIER_FIRST.compare(chart, best) > 0)) {
                best = chart;
            } else if (direction > 0 && order > 0 && (best == null || EASIER_FIRST.compare(chart, best) < 0)) {
                best = chart;
            }
        }
        return best;
    }

    /**
     * Points a track at another chart. The used part and the track's own
     * settings stay; the part is clamped to the new chart's length when known.
     * The fixed #RANDOM branch belongs to the old chart and is dropped.
     */
    public static void replaceChart(MixTrack track, SongData chart) {
        track.sha256 = chart.getSha256() == null ? "" : chart.getSha256();
        track.md5 = chart.getMd5() == null ? "" : chart.getMd5();
        track.title = nonNull(chart.getTitle());
        track.subtitle = nonNull(chart.getSubtitle());
        track.artist = nonNull(chart.getArtist());
        track.genre = nonNull(chart.getGenre());
        track.bpm = chart.getMaxbpm();
        track.level = chart.getLevel();
        track.localPath = nonNull(chart.getPath());
        track.random = null;
        clamp(track, chart.getLength());
    }

    /** Keeps the used part inside a chart of the given length (ms); a non-positive length is unknown. */
    static void clamp(MixTrack track, long chartEnd) {
        if (chartEnd <= 0) {
            return;
        }
        if (track.outMs >= chartEnd) {
            track.outMs = -1;
        }
        long out = track.outMs >= 0 ? track.outMs : chartEnd;
        if (track.inMs > out - MIN_PART_MS) {
            track.inMs = Math.max(0L, out - MIN_PART_MS);
        }
    }

    /**
     * A new copy of the set with every song one step easier or harder. Songs
     * without such a chart (or not in the library) stay as they are and are
     * returned in {@link Shifted#unchanged()}. The copy gets a new id and is not
     * linked to the IR set it may have come from.
     */
    public static Shifted shiftSet(MixPlan source, int direction, Charts charts) {
        MixPlan copy = source.copy();
        copy.id = UUID.randomUUID().toString();
        copy.irId = "";
        copy.irRevision = 0;
        copy.createdAt = System.currentTimeMillis();
        copy.updatedAt = copy.createdAt;
        copy.name = (source.name == null ? "" : source.name) + (direction < 0 ? "(易)" : "(難)");
        List<MixTrack> unchanged = new ArrayList<>();
        for (int i = 0; i < copy.tracks.size(); i++) {
            MixTrack track = copy.tracks.get(i);
            track.localPath = source.tracks.get(i).localPath;
            SongData current = charts.current(track);
            SongData next = current == null ? null
                    : step(current, alternatives(current, charts.sameFolder(current), copy.mode.id), direction);
            if (next == null) {
                unchanged.add(track);
            } else {
                replaceChart(track, next);
            }
        }
        return new Shifted(copy, unchanged);
    }

    /** Short name of a chart's #DIFFICULTY. */
    public static String difficultyName(int difficulty) {
        return switch (difficulty) {
            case 1 -> "BEGINNER";
            case 2 -> "NORMAL";
            case 3 -> "HYPER";
            case 4 -> "ANOTHER";
            case 5 -> "INSANE";
            default -> "-";
        };
    }

    private static String key(SongData chart) {
        String sha = chart.getSha256();
        return sha != null && !sha.isEmpty() ? sha : "md5:" + chart.getMd5();
    }

    private static String nonNull(String text) {
        return text == null ? "" : text;
    }
}
