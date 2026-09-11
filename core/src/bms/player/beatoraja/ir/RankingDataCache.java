package bms.player.beatoraja.ir;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BooleanSupplier;

import com.badlogic.gdx.utils.ObjectMap;

import bms.model.BMSDecoder;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.BMSIRLongNoteMode;
import bms.player.beatoraja.CourseData.CourseDataConstraint;
import bms.player.beatoraja.song.SongData;

/**
 * IRアクセスデータのキャッシュ
 *
 * @author exch
 */
public class RankingDataCache {

    /**
     * IRアクセスデータのキャッシュ
     */
    private ObjectMap<String, RankingData>[] scorecache;
    private ObjectMap<String, RankingData>[] cscorecache;
    private final BooleanSupplier forceLn;

    public RankingDataCache() {
        this(() -> false);
    }

    public RankingDataCache(BooleanSupplier forceLn) {
        this.forceLn = forceLn;
        scorecache = new ObjectMap[4];
        cscorecache = new ObjectMap[4];
        for (int i = 0; i < scorecache.length; i++) {
            scorecache[i] = new ObjectMap(2000);
            cscorecache[i] = new ObjectMap(100);
        }
    }

    /**
     * 指定した楽曲データ、LN MODEに対するIRアクセスデータを返す
     * @param song 楽曲データ
     * @param lnmode LN MODE
     * @return IRアクセスデータ。存在しない場合はnull
     */
    public RankingData get(SongData song, int lnmode) {
        return get(song, new IRRankingContext(lnmode, forceLn.getAsBoolean()));
    }

    public RankingData get(SongData song, IRRankingContext context) {
        return scorecache[cacheIndex(song, context)].get(chartKey(song, context));
    }

    /**
     * 指定したコースデータ、LN MODEに対するIRアクセスデータを返す
     * @param course コースデータ
     * @param lnmode LN MODE
     * @return IRアクセスデータ。存在しない場合はnull
     */
    public RankingData get(CourseData course, int lnmode) {
        return get(course, new IRRankingContext(lnmode, forceLn.getAsBoolean()));
    }

    public RankingData get(CourseData course, IRRankingContext context) {
        return cscorecache[cacheIndex(course, context)].get(createCourseHash(course, context));
    }

    public void put(SongData song, int lnmode, RankingData iras) {
        put(song, new IRRankingContext(lnmode, forceLn.getAsBoolean()), iras);
    }

    public void put(SongData song, IRRankingContext context, RankingData iras) {
        scorecache[cacheIndex(song, context)].put(chartKey(song, context), iras);
    }
    
    public void put(CourseData course, int lnmode, RankingData iras) {
        put(course, new IRRankingContext(lnmode, forceLn.getAsBoolean()), iras);
    }

    public void put(CourseData course, IRRankingContext context, RankingData iras) {
        cscorecache[cacheIndex(course, context)].put(createCourseHash(course, context), iras);
    }

    private int cacheIndex(SongData song, IRRankingContext context) {
        return BMSIRLongNoteMode.authoredUndefined(song) ? context.lnmode() : 3;
    }

    private int cacheIndex(CourseData course, IRRankingContext context) {
        for (SongData song : course.getSong()) {
            if (BMSIRLongNoteMode.authoredUndefined(song)) return context.lnmode();
        }
        return 3;
    }

    private String chartKey(SongData song, IRRankingContext context) {
        return song.getSha256() + (context.forceLn()
                && BMSIRLongNoteMode.separatesScore(song) ? ":forced-ln" : "");
    }

	private String createCourseHash(CourseData course, IRRankingContext context) {
		StringBuilder sb = new StringBuilder();
		for(SongData song : course.getSong()) {
			if(song.getSha256() != null && song.getSha256().length() == 64) {
				sb.append(chartKey(song, context));
			} else {
				return null;
			}
		}
		for(CourseDataConstraint constraint : course.getConstraint()) {
			sb.append(constraint.name);
		}
		try {
			MessageDigest md = MessageDigest.getInstance("sha-256");
			md.update(sb.toString().getBytes());
			return BMSDecoder.convertHexString(md.digest());
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
