package bms.player.beatoraja.select;

import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.BMSIRLongNoteMode;
import bms.player.beatoraja.ScoreDatabaseAccessor.ScoreDataCollector;
import bms.player.beatoraja.song.SongData;
import com.badlogic.gdx.utils.*;

/**
 * スコアデータのキャッシュ
 *
 * @author exch
 */
public abstract class ScoreDataCache {

    // TODO ResourcePoolベースに移行する

    /**
     * スコアデータのキャッシュ
     */
    private ObjectMap<String, ScoreData>[] scorecache;

    public ScoreDataCache() {
        scorecache = new ObjectMap[8];
        for (int i = 0; i < scorecache.length; i++) {
            scorecache[i] = new ObjectMap(2000);
        }
    }

    /**
     * 指定した楽曲データ、LN MODEに対するスコアデータを返す
     * @param song 楽曲データ
     * @param lnmode LN MODE
     * @return スコアデータ。存在しない場合はnull
     */
    public ScoreData readScoreData(SongData song, int lnmode) {
        final int cacheindex = cacheIndex(song, lnmode);
        if (scorecache[cacheindex].containsKey(song.getSha256())) {
            return scorecache[cacheindex].get(song.getSha256());
        }
        ScoreData score = readScoreDatasFromSource(song, lnmode);
        scorecache[cacheindex].put(song.getSha256(), score);
        return score;
    }

    /**
     *
     * @param collector
     * @param songs
     * @param lnmode
     */
    public void readScoreDatas(ScoreDataCollector collector, SongData[] songs, int lnmode) {
        // キャッシュからの抽出
        Array<SongData> noscore = null;
        for (SongData song : songs) {
            final int cacheindex = cacheIndex(song, lnmode);

            if (scorecache[cacheindex].containsKey(song.getSha256())) {
                collector.collect(song, scorecache[cacheindex].get(song.getSha256()));
            } else {
            	if(noscore == null) {
            		noscore = new Array<SongData>();
            	}
                noscore.add(song);
            }
        }

        if(noscore == null) {
            return;
        }
        // キャッシュに存在しなかったスコアデータをキャッシュに登録
        final SongData[] noscores = noscore.toArray(SongData.class);

        final ScoreDataCollector cachecollector = (song, score) -> {
            final int cacheindex = cacheIndex(song, lnmode);
            scorecache[cacheindex].put(song.getSha256(), score);
        	collector.collect(song, score);
        };
        readScoreDatasFromSource(cachecollector, noscores, lnmode);
    }

    boolean existsScoreDataCache(SongData song, int lnmode) {
        final int cacheindex = cacheIndex(song, lnmode);
        return scorecache[cacheindex].containsKey(song.getSha256());
    }

    public void clear() {
        for (ObjectMap<?, ?> cache : scorecache) {
            cache.clear();
        }
    }

    public void update(SongData song, int lnmode) {
        // Multiple selector modes can address the same ordinary PB. Invalidate
        // every alias after saving/deleting so an ON/OFF round trip stays fresh.
        for (ObjectMap<String, ScoreData> cache : scorecache) cache.remove(song.getSha256());
        final int cacheindex = cacheIndex(song, lnmode);
        ScoreData score = readScoreDatasFromSource(song, lnmode);
        scorecache[cacheindex].put(song.getSha256(), score);
    }

    protected boolean isForcedLn() { return false; }

    private int cacheIndex(SongData song, int lnmode) {
        return (song.hasAnyLongNote() ? lnmode : 3)
                + (isForcedLn() && BMSIRLongNoteMode.separatesScore(song) ? 4 : 0);
    }

    protected abstract ScoreData readScoreDatasFromSource(SongData songs, int lnmode);

    protected abstract void readScoreDatasFromSource(ScoreDataCollector collector, SongData[] songs, int lnmode);
}
