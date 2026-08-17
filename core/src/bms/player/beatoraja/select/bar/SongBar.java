package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.song.SongData;
import com.badlogic.gdx.graphics.Pixmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 楽曲バー
 *
 * @author exch
 */
public class SongBar extends SelectableBar {
    /**
     * Chart variants represented by this bar. Ordinary bars contain one chart;
     * LR2-style difficulty bars contain the visible charts from one folder and
     * key mode.
     */
    private final SongData[] songs;
    /** Table-specific levels keyed by the exact chart objects in {@link #songs}. */
    private final Map<SongData, Integer> tableLevels;
    private int songIndex;
    /**
     * バナーデータ
     */
    private Pixmap banner;
    /**
     * ステージファイルデータ
     */
    private Pixmap stagefile;

    public SongBar(SongData song) {
        this(song, null);
    }

    public SongBar(SongData song, Integer tableLevel) {
        this(
                new SongData[]{song},
                song != null ? song.getSha256() : null,
                0,
                singletonTableLevel(song, tableLevel)
        );
    }

    public SongBar(SongData[] songs, String preferredSha256) {
        this(songs, preferredSha256, 0);
    }

    public SongBar(
            SongData[] songs,
            String preferredSha256,
            int difficultyStage
    ) {
        this(songs, preferredSha256, difficultyStage, Collections.emptyMap());
    }

    public SongBar(
            SongData[] songs,
            String preferredSha256,
            int difficultyStage,
            Map<SongData, Integer> tableLevels
    ) {
        this.songs = Arrays.stream(songs)
                .filter(java.util.Objects::nonNull)
                .sorted(SongBar::compareDifficulty)
                .toArray(SongData[]::new);
        if (this.songs.length == 0) {
            throw new IllegalArgumentException("SongBar requires at least one chart");
        }
        this.tableLevels = new IdentityHashMap<>();
        if (tableLevels != null) {
            for (SongData song : this.songs) {
                Integer tableLevel = tableLevels.get(song);
                if (tableLevel != null) {
                    this.tableLevels.put(song, tableLevel);
                }
            }
        }
        selectDifficultyStage(difficultyStage);
        selectSha256(preferredSha256);
    }

    private static Map<SongData, Integer> singletonTableLevel(
            SongData song,
            Integer tableLevel
    ) {
        if (song == null || tableLevel == null) {
            return Collections.emptyMap();
        }
        Map<SongData, Integer> result = new IdentityHashMap<>();
        result.put(song, tableLevel);
        return result;
    }

    public final SongData getSongData() {
        return songs[songIndex];
    }

    public final SongData[] getDifficultyVariants() {
        return songs.clone();
    }

    /** Returns the level shown in Music Select for the active chart. */
    public final int getDisplayLevel() {
        Integer tableLevel = getTableDisplayLevel(getSongData());
        return tableLevel != null ? tableLevel : getSongData().getLevel();
    }

    /** Returns a table override only when this bar was built from one. */
    public final Integer getTableDisplayLevel(SongData song) {
        return tableLevels.get(song);
    }

    public final boolean hasTableDisplayLevel() {
        return getTableDisplayLevel(getSongData()) != null;
    }

    public final int getDifficultyVariantCount() {
        return songs.length;
    }

    public final boolean cycleDifficulty() {
        if (songs.length < 2) {
            return false;
        }
        songIndex = (songIndex + 1) % songs.length;
        clearLoadedContents();
        return true;
    }

    private void selectDifficultyStage(int difficultyStage) {
        songIndex = 0;
        if (difficultyStage < 1 || difficultyStage > 5) {
            return;
        }
        int fallback = -1;
        for (int index = 0; index < songs.length; index++) {
            int difficulty = songs[index].getDifficulty();
            if (difficulty == difficultyStage) {
                songIndex = index;
                return;
            }
            if (difficulty >= 1 && difficulty < difficultyStage) {
                fallback = index;
            }
        }
        if (fallback >= 0) {
            songIndex = fallback;
        }
    }

    private void selectSha256(String preferredSha256) {
        if (preferredSha256 == null || preferredSha256.isBlank()) {
            return;
        }
        for (int index = 0; index < songs.length; index++) {
            if (preferredSha256.equals(songs[index].getSha256())) {
                songIndex = index;
                return;
            }
        }
    }

    private void clearLoadedContents() {
        setScore(null);
        setRivalScore(null);
        banner = null;
        stagefile = null;
        for (int index = 0; index < bms.player.beatoraja.select.MusicSelector.REPLAY; index++) {
            setExistsReplay(index, false);
        }
    }

    public static int compareDifficulty(SongData left, SongData right) {
        int compared = Integer.compare(difficultyOrder(left), difficultyOrder(right));
        if (compared != 0) {
            return compared;
        }
        compared = Integer.compare(left.getLevel(), right.getLevel());
        if (compared != 0) {
            return compared;
        }
        compared = left.getFullTitle().compareToIgnoreCase(right.getFullTitle());
        if (compared != 0) {
            return compared;
        }
        return java.util.Objects.toString(left.getSha256(), "")
                .compareTo(java.util.Objects.toString(right.getSha256(), ""));
    }

    private static int difficultyOrder(SongData song) {
        int difficulty = song.getDifficulty();
        return difficulty >= 1 && difficulty <= 5 ? difficulty : 100 + difficulty;
    }

    public final boolean existsSong() {
		return getSongData().getPath() != null;
    }

    public Pixmap getBanner() {
        return banner;
    }

    public void setBanner(Pixmap banner) {
    	this.banner = banner;
    }

    public Pixmap getStagefile() {
        return stagefile;
    }

    public void setStagefile(Pixmap stagefile) {
    	this.stagefile = stagefile;
    }

    @Override
    public final String getTitle() {
        return getSongData().getFullTitle();
    }

    public int getLamp(boolean isPlayer) {
    	final ScoreData score = isPlayer ? getScore() : getRivalScore();
        if (score != null) {
            return score.getClear();
        }
        return 0;
    }

    /**
     * SongData配列をSongBar配列に変換する
     * @param songs SongData配列
     * @return SongBar配列
     */
    public static SongBar[] toSongBarArray(SongData[] songs) {
        // 重複除外
        // remove duplicates by sha256
        ArrayList<SongData> filteredSongs = new ArrayList<>(Arrays.stream(songs).collect(
                Collectors.toMap(SongData::getSha256, p -> p, (p, q) -> p, LinkedHashMap::new)).values());
        // remove null
        filteredSongs.removeAll(Collections.singleton(null));

        int count = filteredSongs.size();
        SongBar[] result = new SongBar[count--];
        for(SongData song : filteredSongs) {
            if(song != null) {
                result[count--] = new SongBar(song);
            }
        }
        return result;
    }

    protected static SongBar[] toSongBarArray(SongData[] songs, SongData[] elements) {
        return toSongBarArray(songs, elements, null);
    }

    protected static SongBar[] toSongBarArray(
            SongData[] songs,
            SongData[] elements,
            Integer folderTableLevel
    ) {
        // 重複除外
        int count = songs.length;
        int noexistscount = elements.length;
        Map<SongData, Integer> tableLevels = new IdentityHashMap<>();
        for(SongData element : elements) {
            element.setPath(null);
        }

        for(int i = 0;i < songs.length;i++) {
            if(songs[i] == null) {
                continue;
            }
            for(int j = i + 1;j < songs.length;j++) {
                if(songs[j] != null && songs[i].getSha256().equals(songs[j].getSha256())) {
                    songs[j] = null;
                    count--;
                }
            }
            for(int j = 0;j < elements.length;j++) {
                final SongData element = elements[j];
                if(element.getPath() == null && (element.getMd5().length() > 0 && element.getMd5().equals(songs[i].getMd5()))
                        || (element.getSha256().length() > 0 && element.getSha256().equals(songs[i].getSha256()))) {
                    element.setPath(songs[i].getPath());
                    songs[i].merge(element);
                    Integer tableLevel = element.getTableLevel() != null
                            ? element.getTableLevel()
                            : folderTableLevel;
                    if (tableLevel != null) {
                        tableLevels.put(songs[i], tableLevel);
                    }
                    noexistscount--;
                    break;
                }
            }
        }
        SongBar[] result = new SongBar[count + noexistscount];
        noexistscount--;
        for(int i = 0;i < elements.length;i++) {
            if(elements[i].getPath() == null) {
                Integer tableLevel = elements[i].getTableLevel() != null
                        ? elements[i].getTableLevel()
                        : folderTableLevel;
                result[count + (noexistscount--)] = new SongBar(elements[i], tableLevel);
            }
        }
        count--;
        for(SongData song : songs) {
            if(song != null) {
                result[count--] = new SongBar(song, tableLevels.get(song));
            }
        }
        return result;
    }
}
