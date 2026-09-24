package bms.player.beatoraja.ir;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.player.beatoraja.*;
import bms.player.beatoraja.MainController.IRStatus;
import bms.player.beatoraja.song.SongData;

/**
 * IRのランキングデータ
 *
 * @author exch
 */
public class RankingData {
	private static final Logger logger = LoggerFactory.getLogger(RankingData.class);
	/**
	 * 選択されている楽曲の現在のIR順位
	 */
	private int irrank;
	/**
	 * 選択されている楽曲の以前のIR順位
	 */
	private int prevrank;
	/**
	 * 選択されている楽曲のローカルスコアでの想定IR順位
	 */	
	private int localrank;

	/**
	 * IR総プレイ数
	 */
	private int irtotal;
	/**
	 * 各クリアランプ総数
	 */
	private int[] lamps = new int[11];
	/**
	 * 全スコアデータ
	 */
	private IRScoreData[] scores;
	/**
	 * 各スコアデータの順位
	 */
	private int[] scorerankings;
	/**
	 * IRアクセス状態
	 */
	private int state = NONE;
	public static final int NONE = 0;
	public static final int ACCESS = 1;
	public static final int FINISH = 2;
	public static final int FAIL = 3;
	
	/**
	 * 最終更新時間
	 */
	private long lastUpdateTime;
	private final AtomicBoolean accessInFlight = new AtomicBoolean();
	
	public void load(MainState mainstate, Object song) {
		load(mainstate, song, IRRankingContext.from(mainstate.main.getPlayerConfig()));
	}

	public void load(MainState mainstate, Object song, IRRankingContext context) {
		load(mainstate, song, context, false);
	}

	public void load(MainState mainstate, Object song, IRRankingContext context, boolean forceRefresh) {
		if(!(song instanceof SongData || song instanceof CourseData)) {
			return;
		}		
		if ((!forceRefresh && state == FINISH) || !accessInFlight.compareAndSet(false, true)) {
			return;
		}
		if (!hasCachedScores()) state = ACCESS;
		final int lnmode = context.lnmode();
		final boolean forceLn = context.forceLn();
		final ScoreData localScore = mainstate.getScoreDataProperty().getScoreData();
		Thread irprocess = new Thread(() -> {
			try {
				IRStatus[] ir = mainstate.main.getIRStatus();
				if (ir == null || ir.length == 0 || ir[0] == null || ir[0].connection == null) {
					failAccess();
					return;
				}
				IRStatus primary = ir[0];
				if (primary.config == null || !primary.config.isImportrival()) {
					logger.trace("IRランキング取得を設定により省略しました");
					return;
				}
				IRResponse<IRScoreData[]> response = null;
				if(song instanceof SongData songData) {
					response = primary.connection.getPlayData(null, IRChartData.forRanking(songData, lnmode, forceLn));
					// Rival databases have no separate forced-LN chart identity.
					if (response != null && response.isSucceeded() && !(forceLn
							&& bms.player.beatoraja.BMSIRLongNoteMode.separatesScore(songData))) {
						mainstate.main.getRivalDataAccessor().updateAllRivalsScores(
								response.getData(), songData, lnmode);
					}
				} else if(song instanceof CourseData course) {
					response = primary.connection.getCoursePlayData(null, new IRCourseData(course, lnmode, forceLn));
				}
				if(response != null && response.isSucceeded() && response.getData() != null) {
					IRScoreData[] received = response.getData().clone();
					updateScore(received.clone(), localScore);
					if (received.length > 0) {
						for (IRStatus connected : ir) {
							if (connected.config == null || !connected.config.isImportrival()) continue;
							mainstate.main.getPersistentRankingDataStore().save(
										mainstate.main.getPlayerPath(), mainstate.main.getPlayerConfig().getId(),
									connected, context, cacheIdentity(song, context), received);
						}
					}
					logger.trace("IRからのスコア取得成功 : {}", response.getMessage());
				} else {
					logger.warn("IRからのスコア取得失敗 : {}", response == null ? "response unavailable" : response.getMessage());
					if (scores == null && !forceRefresh) state = FAIL;
				}
				lastUpdateTime = System.currentTimeMillis();
			} catch (Exception error) {
				logger.warn("IRランキング取得後の処理に失敗しました", error);
				if (scores == null && !forceRefresh) state = FAIL;
			} finally {
				if (hasCachedScores() && state == ACCESS) state = FINISH;
				if (forceRefresh && hasCachedScores() && state == FAIL) state = FINISH;
				lastUpdateTime = System.currentTimeMillis();
				accessInFlight.set(false);
			}
		});
		irprocess.setName("ir-ranking-load");
		irprocess.setDaemon(true);
		irprocess.start();

	}
	
	public void updateScore(IRScoreData[] scores, ScoreData localscore) {
		if(scores == null) {
			return;
		}
		boolean firstUpdate = this.scores == null;
		
		Arrays.sort(scores, (s1, s2) -> (s2.getExscore() - s1.getExscore()));
		int[] scorerankings = new int[scores.length];
		for(int i = 0;i < scorerankings.length;i++) {
			scorerankings[i] = (i > 0 && scores[i].getExscore() == scores[i - 1].getExscore()) ? scorerankings[i - 1] : i + 1;
		}
		
		if(!firstUpdate) {
			prevrank = irrank;	
		}
		this.scores = scores;
		this.scorerankings = scorerankings;
        irtotal = scores.length;
        Arrays.fill(lamps, 0);
        irrank = 0;
        localrank = 0;
        for(int i = 0;i < scores.length;i++) {
            if(irrank == 0 && scores[i].player.length() == 0) {
            	irrank = scorerankings[i];
            }
            if(localscore != null && localrank == 0 && scores[i].getExscore() <=  localscore.getExscore()) {
            	localrank = scorerankings[i];
            }
            lamps[scores[i].clear.id]++;
        }
        
        if(firstUpdate && localrank != 0) {
        	prevrank = Math.max(irrank, localrank);
        }
        
		state = FINISH;
        lastUpdateTime = System.currentTimeMillis();
	}

	public IRScoreData[] getScoresSnapshot() {
		return scores == null ? null : scores.clone();
	}

	public void restoreCachedScores(IRScoreData[] cachedScores) {
		if (cachedScores != null) {
			updateScore(cachedScores.clone(), null);
			prevrank = irrank;
			localrank = 0;
		}
	}

	public void requestReload() {
		if (!accessInFlight.get()) state = NONE;
	}

	public void requestReloadAfterSubmit() {
		if (!accessInFlight.get() && hasCachedScores()) state = FINISH;
	}

	public boolean hasCachedScores() {
		return scores != null;
	}

	public static String cacheIdentity(Object target, IRRankingContext context) {
		if (target instanceof SongData song) {
			String sha = song.getSha256();
			if (sha == null || sha.length() != 64) return "";
			return "song:" + sha.toLowerCase() + ":" + (context.forceLn()
					&& BMSIRLongNoteMode.separatesScore(song) ? "forced-ln" : "ordinary");
		}
		if (target instanceof CourseData course) {
			StringBuilder identity = new StringBuilder("course:");
			for (SongData song : course.getSong()) {
				if (song == null || song.getSha256() == null || song.getSha256().length() != 64) return "";
				identity.append(song.getSha256().toLowerCase()).append(':');
			}
			if (course.getConstraint() != null) {
				Arrays.stream(course.getConstraint()).forEach(constraint -> identity.append(constraint.name).append(':'));
			}
			return identity.append("name:").append(course.getName() == null ? "" : course.getName()).toString();
		}
		return "";
	}
	
	/**
	 * 選択されている楽曲の現在のIR順位を返す
	 * 
	 * @return 現在のIR順位
	 */
	public int getRank() {
		return irrank;
	}

	/**
	 * 選択されている楽曲の以前のIR順位を返す
	 * 
	 * @return 以前のIR順位
	 */
	public int getPreviousRank() {
		return prevrank;
	}
	
	/**
	 * 選択されている楽曲のローカルスコアでの想定IR順位を返す
	 * 
	 * @return ローカルスコアでの想定IR順位
	 */
	public int getLocalRank() {
		return localrank;
	}
	
	/**
	 * IR上の総プレイ人数を返す
	 * 
	 * @return 総プレイ人数
	 */
	public int getTotalPlayer() {
		return irtotal;
	}

	/**
	 * IR上のindexに対応したプレイヤーのスコアデータを返す

	 * @param index インデックス
	 * @return 対応するスコアデータ。indexに対応したスコアデータが存在しない場合はnull
	 */
	public IRScoreData getScore(int index) {
		if(scores != null && index >= 0 && index < scores.length) {
			return scores[index];			
		}
		return null;
	}
	
	/**
	 * IR上のindexに対応したプレイヤーの順位を返す

	 * @param index インデックス
	 * @return 対応するスコアデータの順位。indexに対応したスコアデータが存在しない場合はInteger.MIN_VALUEl
	 */
	public int getScoreRanking(int index) {
		if(scorerankings != null && index >= 0 && index < scorerankings.length) {
			return scorerankings[index];			
		}
		return Integer.MIN_VALUE;
	}
	
	public int getClearCount(int clearType) {
		return lamps[clearType];
	}
	
	public int getState() {
		return state;
	}

	/** Starts an externally managed ranking request. */
	public void beginAccess() {
		state = ACCESS;
	}

	/** Marks an externally managed ranking request as failed. */
	public void failAccess() {
		state = FAIL;
		lastUpdateTime = System.currentTimeMillis();
	}
	
	/**
	 * RankingDataの最終更新時間を返す
	 * 
	 * @return RankingDataの最終更新時間(ms)
	 */
	public long getLastUpdateTime() {
		return lastUpdateTime;
	}
}
