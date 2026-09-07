package bms.player.beatoraja;

import bms.player.beatoraja.play.NantokaManiaRules;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Stream;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import bms.model.BMSModel;
import bms.model.TimeLine;
import static bms.player.beatoraja.ClearType.*;
import static bms.player.beatoraja.CourseData.CourseDataConstraint.*;

import bms.player.beatoraja.CourseData.CourseDataConstraint;
import bms.player.beatoraja.ScoreData.SongTrophy;
import bms.player.beatoraja.ScoreDatabaseAccessor.ScoreDataCollector;
import bms.player.beatoraja.ScoreLogDatabaseAccessor.ScoreLog;
import bms.player.beatoraja.ir.LR2IRAccessor;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacPlayContext;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacDatabase;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.badlogic.gdx.utils.StringBuilder;

/**
 * プレイデータアクセス用クラス
 * 
 * @author exch
 */
public final class PlayDataAccessor {
	private static final Logger logger = LoggerFactory.getLogger(PlayDataAccessor.class);

	// TODO スコアハッシュを付与するかどうかの判定(前のスコアハッシュの正当性を確認できなかった時)
	// TODO BATTLEは別ハッシュで登録したい}			

	private final String hashkey;

	/**
	 * プレイヤー名
	 */
	private final String player;
	/**
	 * プレイヤーデータのルートパス
	 */
	private final String playerpath;
	/**
	 * スコアデータベースアクセサ
	 */
	private ScoreDatabaseAccessor scoredb;
	private ScoreDatabaseAccessor maniacScoredb;
	private ScoreDatabaseAccessor forcedScoredb;
	private ScoreDatabaseAccessor forcedManiacScoredb;
	private BMSIRManiacDatabase maniacMetadata;
	private BMSIRManiacDatabase forcedManiacMetadata;
	private ScoreLogDatabaseAccessor forcedScorelogdb;
	private ScoreDataLogDatabaseAccessor forcedScoredatalogdb;
	private final PlayerConfig playerConfig;
	/**
	 * スコアログアクセサ
	 */
	private ScoreLogDatabaseAccessor scorelogdb;
	/**
	 * スコアデータログアクセサ
	 */
	private ScoreDataLogDatabaseAccessor scoredatalogdb;


	private static final String[] replay = { "", "C", "H" };

	public PlayDataAccessor(Config config) {
		this(config, null);
	}

	public PlayDataAccessor(Config config, PlayerConfig playerConfig) {
		this.player = config.getPlayername();
		this.playerpath = config.getPlayerpath();
		this.playerConfig = playerConfig;

		try {
			Class.forName("org.sqlite.JDBC");
			scoredb = new ScoreDatabaseAccessor(playerpath + File.separatorChar + player + File.separatorChar + "score.db");
			scoredb.createTable();
			forcedScoredb = new ScoreDatabaseAccessor(playerpath + File.separatorChar + player
					+ File.separatorChar + "bmsir_forced_ln.db");
			forcedScoredb.createTable();
			forcedManiacScoredb = new ScoreDatabaseAccessor(playerpath + File.separatorChar + player
					+ File.separatorChar + "bmsir_forced_ln_maniac.db");
			forcedManiacScoredb.createTable();
			forcedManiacMetadata = new BMSIRManiacDatabase(playerpath + File.separatorChar + player
					+ File.separatorChar + "bmsir_forced_ln_maniac.db");
			forcedScorelogdb = new ScoreLogDatabaseAccessor(playerpath + File.separatorChar + player
					+ File.separatorChar + "bmsir_forced_ln_scorelog.db");
			forcedScoredatalogdb = new ScoreDataLogDatabaseAccessor(playerpath + File.separatorChar + player
					+ File.separatorChar + "bmsir_forced_ln_scoredatalog.db");
			maniacScoredb = new ScoreDatabaseAccessor(playerpath + File.separatorChar + player
					+ File.separatorChar + "bmsir_maniac.db");
			maniacScoredb.createTable();
			maniacMetadata = new BMSIRManiacDatabase(playerpath + File.separatorChar + player
					+ File.separatorChar + "bmsir_maniac.db");
			scorelogdb = new ScoreLogDatabaseAccessor(playerpath + File.separatorChar + player + File.separatorChar + "scorelog.db");
			scoredatalogdb = new ScoreDataLogDatabaseAccessor(playerpath + File.separatorChar + player + File.separatorChar + "scoredatalog.db");
			// Share scoredb to LR2IR
			LR2IRAccessor.setScoreDatabaseAccessor(scoredb);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		hashkey = "";
	}

	public PlayerData readPlayerData() {
		return scoredb.getPlayerData();
	}

	public PlayerData readTodayPlayerData() {
		PlayerData[] pd = scoredb.getPlayerDatas(2);
		if (pd.length > 1) {
			pd[0].setPlaycount(pd[0].getPlaycount() - pd[1].getPlaycount());
			pd[0].setClear(pd[0].getClear() - pd[1].getClear());
			pd[0].setEpg(pd[0].getEpg() - pd[1].getEpg());
			pd[0].setLpg(pd[0].getLpg() - pd[1].getLpg());
			pd[0].setEgr(pd[0].getEgr() - pd[1].getEgr());
			pd[0].setLgr(pd[0].getLgr() - pd[1].getLgr());
			pd[0].setEgd(pd[0].getEgd() - pd[1].getEgd());
			pd[0].setLgd(pd[0].getLgd() - pd[1].getLgd());
			pd[0].setEbd(pd[0].getEbd() - pd[1].getEbd());
			pd[0].setLbd(pd[0].getLbd() - pd[1].getLbd());
			pd[0].setEpr(pd[0].getEpr() - pd[1].getEpr());
			pd[0].setLpr(pd[0].getLpr() - pd[1].getLpr());
			pd[0].setEms(pd[0].getEms() - pd[1].getEms());
			pd[0].setLms(pd[0].getLms() - pd[1].getLms());
			pd[0].setPlaytime(pd[0].getPlaytime() - pd[1].getPlaytime());
			return pd[0];
		} else if (pd.length == 1) {
			return pd[0];
		}
		return null;
	}

	/**
	 * 指定されたスコアデータを元にプレイヤーデータを更新する
	 * 
	 * @param score スコアデータ
	 * @param time プレイ時間
	 */
	public void updatePlayerData(ScoreData score, long time) {
		PlayerData pd = readPlayerData();
		pd.setEpg(pd.getEpg() + score.getEpg());
		pd.setLpg(pd.getLpg() + score.getLpg());
		pd.setEgr(pd.getEgr() + score.getEgr());
		pd.setLgr(pd.getLgr() + score.getLgr());
		pd.setEgd(pd.getEgd() + score.getEgd());
		pd.setLgd(pd.getLgd() + score.getLgd());
		pd.setEbd(pd.getEbd() + score.getEbd());
		pd.setLbd(pd.getLbd() + score.getLbd());
		pd.setEpr(pd.getEpr() + score.getEpr());
		pd.setLpr(pd.getLpr() + score.getLpr());
		pd.setEms(pd.getEms() + score.getEms());
		pd.setLms(pd.getLms() + score.getLms());

		pd.setPlaycount(pd.getPlaycount() + 1);
		if (score.getClear() > Failed.id) {
			pd.setClear(pd.getClear() + 1);
		}
		pd.setPlaytime(pd.getPlaytime() + time);
		scoredb.setPlayerData(pd);
	}

	/**
	 * スコアデータを読み込む
	 * 
	 * @param model
	 *            対象のモデル
	 * @param lnmode
	 *            LNモード
	 * @return スコアデータ
	 */
	public ScoreData readScoreData(BMSModel model, int lnmode) {
		if (bms.player.beatoraja.pattern.BMSIRSevenToNineModifier.isApplied(model)) return null;
		String hash = scoreHash(model);
		boolean ln = model.containsUndefinedLongNote();
		return BMSIRLongNoteMode.compatibleScore(
				scoreDatabase(model).getScoreData(hash, ln ? lnmode : 0),
				BMSIRLongNoteMode.separatesScore(model));
	}

	public ScoreData readScoreData(SongData song, int lnmode) {
		return BMSIRLongNoteMode.compatibleScore(
				scoreDatabase(song, false).getScoreData(song.getSha256(), songScoreMode(song, lnmode)),
				separatesScore(song));
	}

	/**
	 * スコアデータを読み込む
	 *
	 * @param hash
	 *            楽曲のハッシュ値
	 * @param ln
	 *            対象のbmsが未定義LNを含む場合はtrueを入れる
	 * @param lnmode
	 *            LNモード
	 * @return スコアデータ
	 */
	public ScoreData readScoreData(String hash, boolean ln, int lnmode) {
		// Hash-only callers have no chart metadata and address the ordinary pool.
		return scoredb.getScoreData(hash, ln ? lnmode : 0);
	}

	public ScoreData readManiacScoreData(String storageHash, int lnmode) {
		ScoreData score = maniacScoredb.getScoreData(storageHash, lnmode);
		return score != null || lnmode == 0
				? score
				: maniacScoredb.getScoreData(storageHash, 0);
	}

	public ScoreData readManiacScoreData(SongData song, String storageHash, int lnmode) {
		ScoreDatabaseAccessor database = scoreDatabase(song, true);
		int mode = songScoreMode(song, lnmode);
		ScoreData score = database.getScoreData(storageHash, mode);
		if (score == null && mode != 0 && !separatesScore(song)) {
			score = database.getScoreData(storageHash, 0);
		}
		return BMSIRLongNoteMode.compatibleScore(score, separatesScore(song));
	}

	public void readManiacScoreDatas(
			ScoreDataCollector collector,
			SongData[] songs,
			int lnmode,
			Function<SongData, String> storageHashProvider
	) {
		for (boolean separate : new boolean[]{false, true}) {
			SongData[] group = Arrays.stream(songs).filter(s -> separatesScore(s) == separate).toArray(SongData[]::new);
			if (group.length == 0) continue;
			(separate ? forcedManiacScoredb : maniacScoredb).getScoreDatasByHash(
					collector, group, lnmode, storageHashProvider, true, separate);
		}
	}

	public void syncManiacScoreData(
			String storageHash,
			String baseSha256,
			String virtualChartId,
			BMSIRManiacSettings settings,
			String generationSeed,
			String placementHash,
			ScoreData incoming
	) {
		if (storageHash == null || storageHash.isBlank() || incoming == null || settings == null) {
			return;
		}
		// The remote MANIAC sync does not identify forced-LN policy; retain it in the ordinary pool.
		if (forceLn()) return;
		int mode = Math.max(0, incoming.getMode());
		ScoreData stored = maniacScoredb.getScoreData(storageHash, mode);
		int playcount = stored == null ? 0 : stored.getPlaycount();
		int clearcount = stored == null ? 0 : stored.getClearcount();
		if (stored == null) {
			stored = new ScoreData();
			stored.setSha256(storageHash);
			stored.setMode(mode);
			stored.setNotes(incoming.getNotes());
			stored.setPassnotes(incoming.getPassnotes());
			stored.setMinbp(Integer.MAX_VALUE);
		}
		stored.setNotes(Math.max(stored.getNotes(), incoming.getNotes()));
		stored.setPassnotes(Math.max(stored.getPassnotes(), incoming.getPassnotes()));
		stored.update(incoming, true);
		stored.setDate(Math.max(stored.getDate(), incoming.getDate()));
		stored.setPlaycount(playcount);
		stored.setClearcount(clearcount);
		stored.setScorehash(getScoreHash(stored));
		maniacScoredb.setScoreData(stored);
		if (maniacMetadata != null) {
			maniacMetadata.recordSynced(
					storageHash,
					baseSha256,
					virtualChartId,
					settings,
					generationSeed,
					placementHash,
					incoming.getExscore(),
					incoming.getDate()
			);
		}
	}

	/**
	 * スコアデータをまとめて読み込み、collectorに渡す
	 * @param collector スコアデータのcollector
	 * @param songs 楽曲データ
	 * @param lnmode LNモード
	 */
	public void readScoreDatas(ScoreDataCollector collector, SongData[] songs, int lnmode) {
		for (boolean separate : new boolean[]{false, true}) {
			SongData[] group = Arrays.stream(songs).filter(s -> separatesScore(s) == separate).toArray(SongData[]::new);
			if (group.length == 0) continue;
			(separate ? forcedScoredb : scoredb).getScoreDatas(collector, group, lnmode, separate, separate);
		}
	}

	public List<ScoreData> readScoreDatas(String sql) {
		return scoredb.getScoreDatas(sql);
	}

	/**
	 * スコアデータを書き込む
	 * 
	 * @param newscore
	 *            スコアデータ
	 * @param model
	 *            対象のモデル
	 * @param lnmode
	 *            LNモード
	 * @param updateScore
	 *            プレイ回数のみ反映する場合はfalse
	 */
	public void writeScoreData(ScoreData newscore, BMSModel model, int lnmode, boolean updateScore) {
		if (bms.player.beatoraja.pattern.BMSIRSevenToNineModifier.isApplied(model)) return;
		String hash = scoreHash(model);
		ScoreDatabaseAccessor targetDatabase = scoreDatabase(model);
		if (newscore == null) {
			return;
		}
		boolean changedLnMode = BMSIRLongNoteMode.separatesScore(model);
		ScoreData score = BMSIRLongNoteMode.compatibleScore(
				targetDatabase.getScoreData(hash, model.containsUndefinedLongNote() ? lnmode : 0), changedLnMode);
		int previousEx = score == null ? -1 : score.getExscore();

		if (score == null) {
			score = new ScoreData();
			score.setMode(model.containsUndefinedLongNote() ? lnmode : 0);
		}
		score.setSha256(hash);
		if (changedLnMode) score.setBmsirLongNotePolicy(BMSIRLongNoteMode.SCORE_POLICY);
		// Keep the live IR wire marker independent from the shared local PB.
		newscore.setBmsirLongNotePolicy(BMSIRLongNoteMode.isApplied(model)
				&& BMSIRLongNoteMode.changesAuthoredMode(model)
				? BMSIRLongNoteMode.SCORE_POLICY : score.getBmsirLongNotePolicy());
		if (updateScore) {
			score.setNotes(NantokaManiaRules.totalNotes(model));
		}

		if (newscore.getClear() > Failed.id) {
			score.setClearcount(score.getClearcount() + 1);
		}

		ScoreLog log = updateScore(score, newscore, hash, updateScore);
		
		Set<SongTrophy> l = new HashSet<SongTrophy>();
		for(char c : score.getTrophy() != null ? score.getTrophy().toCharArray() : new char[0]) {
			SongTrophy trophy = SongTrophy.getTrophy(c);
			if(trophy != null) {
				l.add(trophy);
			}
		}

		Set<SongTrophy> newTrophies = new HashSet<SongTrophy>();
		// クリアトロフィー
		int clear = newscore.getClear();
		if(newscore.getGauge() != -1) {			
			if(clear >= Hard.id){
				if(clear == ExHard.id) {
					newTrophies.add(SongTrophy.EXHARD);
				}
				newTrophies.add(SongTrophy.HARD);
			} else {
				if(clear >= Normal.id) {
					newTrophies.add(SongTrophy.GROOVE);
				}
				newTrophies.add(SongTrophy.EASY);
			}
		}
			
		// オプショントロフィー
		// TODO FLIPの扱いは？
		final SongTrophy[] optionTrophy = {SongTrophy.NORMAL,SongTrophy.MIRROR,SongTrophy.RANDOM, SongTrophy.R_RANDOM
				,SongTrophy.S_RANDOM, SongTrophy.SPIRAL, SongTrophy.H_RANDOM, SongTrophy.ALL_SCR, SongTrophy.EX_RANDOM
				,SongTrophy.EX_S_RANDOM};
			
		if(clear >= Easy.id) {
			newTrophies.add(optionTrophy[Math.max(newscore.getOption() % 10, (newscore.getOption() / 10) % 10)]);
		}

		// newscore のトロフィーをマージ
		l.addAll(newTrophies);
		
		StringBuilder sb = new StringBuilder();
		for(SongTrophy trophy : l) {
			sb.append(trophy.character);
		}
		score.setTrophy(sb.toString());

		score.setPlaycount(score.getPlaycount() + 1);
		score.setDate(Calendar.getInstance(TimeZone.getDefault()).getTimeInMillis() / 1000L);
		score.setScorehash(getScoreHash(score));
		targetDatabase.setScoreData(score);
		if (targetDatabase == maniacScoredb && maniacMetadata != null) {
			maniacMetadata.record(model, newscore, newscore.getExscore() > previousEx, "local");
		}
		if (targetDatabase == forcedManiacScoredb && forcedManiacMetadata != null) {
			forcedManiacMetadata.record(model, newscore, newscore.getExscore() > previousEx, "local");
		}
		ScoreLogDatabaseAccessor playLog = targetDatabase == forcedScoredb ? forcedScorelogdb : scorelogdb;
		ScoreDataLogDatabaseAccessor dataLog = targetDatabase == forcedScoredb ? forcedScoredatalogdb : scoredatalogdb;
		boolean ordinary = targetDatabase == scoredb || targetDatabase == forcedScoredb;
		if (ordinary && log.getSha256() != null && playLog != null) {
			log.setMode(score.getMode());
			log.setDate(score.getDate());
			playLog.setScoreLog(log);
		}

		if (ordinary && dataLog != null) {
			StringBuilder newScoresb = new StringBuilder();
			for(SongTrophy trophy : newTrophies) {
				newScoresb.append(trophy.character);
			}
			newscore.setTrophy(newScoresb.toString());
			newscore.setMode(score.getMode());
			newscore.setDate(score.getDate());
			newscore.setPlaycount(score.getPlaycount());
			newscore.setClearcount(score.getClearcount());
			newscore.setScorehash(getScoreHash(newscore));

			dataLog.setScoreDataLog(newscore);
		}

		// 楽曲のプレイ時間算出(秒)
		long time = 0;
		for (TimeLine tl : model.getAllTimeLines()) {
			for (int i = 0; i < model.getMode().key; i++) {
				if (tl.getNote(i) != null && tl.getNote(i).getState() != 0) {
					time = tl.getMicroTime() / 1000000;
				}
			}
		}
		updatePlayerData(newscore, time, targetDatabase);
		logger.info("スコアデータベース更新完了 ");
	}

	private boolean forceLn() { return playerConfig != null && playerConfig.isBmsirForceLn(); }

	private boolean separatesScore(SongData song) {
		return forceLn() && BMSIRLongNoteMode.separatesScore(song);
	}

	private int songScoreMode(SongData song, int lnmode) {
		return (separatesScore(song) || BMSIRLongNoteMode.authoredUndefined(song)) ? lnmode : 0;
	}

	private ScoreDatabaseAccessor scoreDatabase(SongData song, boolean maniac) {
		return separatesScore(song) ? (maniac ? forcedManiacScoredb : forcedScoredb)
				: (maniac ? maniacScoredb : scoredb);
	}

	private ScoreDatabaseAccessor scoreDatabase(BMSModel model) {
		boolean maniac = model != null && model.getValues().containsKey(BMSIRManiacPlayContext.MODEL_STORAGE_HASH);
		return BMSIRLongNoteMode.separatesScore(model)
				? (maniac ? forcedManiacScoredb : forcedScoredb)
				: (maniac ? maniacScoredb : scoredb);
	}

	private static String scoreHash(BMSModel model) {
		String special = model.getValues().get(BMSIRManiacPlayContext.MODEL_STORAGE_HASH);
		return special == null || special.isBlank() ? model.getSHA256() : special;
	}

	private void updatePlayerData(ScoreData score, long time, ScoreDatabaseAccessor database) {
		PlayerData pd = database.getPlayerData();
		pd.setEpg(pd.getEpg() + score.getEpg());
		pd.setLpg(pd.getLpg() + score.getLpg());
		pd.setEgr(pd.getEgr() + score.getEgr());
		pd.setLgr(pd.getLgr() + score.getLgr());
		pd.setEgd(pd.getEgd() + score.getEgd());
		pd.setLgd(pd.getLgd() + score.getLgd());
		pd.setEbd(pd.getEbd() + score.getEbd());
		pd.setLbd(pd.getLbd() + score.getLbd());
		pd.setEpr(pd.getEpr() + score.getEpr());
		pd.setLpr(pd.getLpr() + score.getLpr());
		pd.setEms(pd.getEms() + score.getEms());
		pd.setLms(pd.getLms() + score.getLms());
		pd.setPlaycount(pd.getPlaycount() + 1);
		if (score.getClear() > Failed.id) pd.setClear(pd.getClear() + 1);
		pd.setPlaytime(pd.getPlaytime() + time);
		database.setPlayerData(pd);
	}
	
	public ScoreData readScoreData(String hash, boolean ln, int lnmode, int option,
			CourseData.CourseDataConstraint[] constraint) {
		int hispeed = 0;
		int judge = 0;
		int gauge = 0;
		for (CourseData.CourseDataConstraint c : constraint) {
			switch(c) {
			case NO_SPEED:
				hispeed = 1;
				break;
			case NO_GOOD:
				judge = 1;
				break;
			case NO_GREAT:
				judge = 2;
				break;
			case GAUGE_LR2:
				gauge = 1;
				break;
			case GAUGE_5KEYS:
				gauge = 2;
				break;
			case GAUGE_7KEYS:
				gauge = 3;
				break;
			case GAUGE_9KEYS:
				gauge = 4;
				break;
			case GAUGE_24KEYS:
				gauge = 5;
				break;
			default:
				break;
			}
		}
		return scoredb.getScoreData(hash, (ln ? lnmode : 0) + option * 10 + hispeed * 100 + judge * 1000 + gauge * 10000);
	}

	public ScoreData readScoreData(BMSModel[] models, int lnmode, int option,
			CourseData.CourseDataConstraint[] constraint) {
		String[] hash = new String[models.length];
		boolean ln = false;
		for (int i = 0; i < models.length; i++) {
			hash[i] = models[i].getSHA256();
			ln |= models[i].containsUndefinedLongNote();
		}
		boolean separate = Arrays.stream(models).anyMatch(BMSIRLongNoteMode::separatesScore);
		return BMSIRLongNoteMode.compatibleScore((separate ? forcedScoredb : scoredb).getScoreData(
				String.join("", hash), courseScoreMode(ln, lnmode, option, constraint)), separate);
	}

	public ScoreData readScoreData(SongData[] songs, int lnmode, int option,
			CourseData.CourseDataConstraint[] constraint) {
		boolean separate = Arrays.stream(songs).anyMatch(this::separatesScore);
		boolean ln = separate || Arrays.stream(songs).anyMatch(BMSIRLongNoteMode::authoredUndefined);
		String hash = String.join("", Arrays.stream(songs).map(SongData::getSha256).toArray(String[]::new));
		return BMSIRLongNoteMode.compatibleScore((separate ? forcedScoredb : scoredb).getScoreData(
				hash, courseScoreMode(ln, lnmode, option, constraint)), separate);
	}

	public ScoreData readScoreData(String[] hashes, boolean ln, int lnmode, int option,
			CourseData.CourseDataConstraint[] constraint) {
		String hash = "";
		for (String s : hashes) {
			hash += s;
		}
		return readScoreData(hash, ln, lnmode, option, constraint);
	}

	/**
	 * コーススコアデータを書き込む
	 */
	public void writeScoreData(ScoreData newscore, BMSModel[] models, int lnmode, int option,
			CourseData.CourseDataConstraint[] constraint, boolean updateScore) {
		String hash = "";
		int totalnotes = 0;
		boolean ln = false;
		for (BMSModel model : models) {
			hash += model.getSHA256();
			totalnotes += NantokaManiaRules.totalNotes(model);
			ln |= model.containsUndefinedLongNote();
		}
		if (newscore == null) {
			return;
		}
		int hispeed = 0;
		int judge = 0;
		int gauge = 0;
		for (CourseData.CourseDataConstraint c : constraint) {
			switch(c) {
			case NO_SPEED:
				hispeed = 1;
				break;
			case NO_GOOD:
				judge = 1;
				break;
			case NO_GREAT:
				judge = 2;
				break;
			case GAUGE_LR2:
				gauge = 1;
				break;
			case GAUGE_5KEYS:
				gauge = 2;
				break;
			case GAUGE_7KEYS:
				gauge = 3;
				break;
			case GAUGE_9KEYS:
				gauge = 4;
				break;
			case GAUGE_24KEYS:
				gauge = 5;
				break;
			default:
				break;
			}
		}
		boolean changedLnMode = Arrays.stream(models).anyMatch(BMSIRLongNoteMode::separatesScore);
		ScoreDatabaseAccessor targetDatabase = changedLnMode ? forcedScoredb : scoredb;
		ScoreData score = BMSIRLongNoteMode.compatibleScore(targetDatabase.getScoreData(hash,
				(ln ? lnmode : 0) + option * 10 + hispeed * 100 + judge * 1000 + gauge * 10000), changedLnMode);

		if (score == null) {
			score = new ScoreData();
			score.setMode((ln ? lnmode : 0) + option * 10 + hispeed * 100 + judge * 1000 + gauge * 10000);
		}
		score.setSha256(hash);
		score.setNotes(totalnotes);
		if (changedLnMode) score.setBmsirLongNotePolicy(BMSIRLongNoteMode.SCORE_POLICY);
		newscore.setBmsirLongNotePolicy(Arrays.stream(models).anyMatch(m ->
				BMSIRLongNoteMode.isApplied(m) && BMSIRLongNoteMode.changesAuthoredMode(m))
				? BMSIRLongNoteMode.SCORE_POLICY : score.getBmsirLongNotePolicy());

		if (newscore.getClear() != Failed.id) {
			score.setClearcount(score.getClearcount() + 1);
		}
		
		ScoreLog log = updateScore(score, newscore, hash, updateScore);

		score.setPlaycount(score.getPlaycount() + 1);
		score.setDate(Calendar.getInstance(TimeZone.getDefault()).getTimeInMillis() / 1000L);
		score.setScorehash(getScoreHash(score));
		targetDatabase.setScoreData(score);
		ScoreLogDatabaseAccessor courseLog = changedLnMode ? forcedScorelogdb : scorelogdb;
		if (log.getSha256() != null && courseLog != null) {
			log.setMode(score.getMode());
			log.setDate(score.getDate());
			courseLog.setScoreLog(log);
		}

		logger.info("スコアデータベース更新完了 ");

	}

	private String getScoreHash(ScoreData score) {
		byte[] cipher_byte;
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update((hashkey + score.getSha256() + "," + score.getExscore() + "," + score.getEpg() + ","
					+ score.getLpg() + "," + score.getEgr() + "," + score.getLgr() + "," + score.getEgd() + ","
					+ score.getLgd() + "," + score.getEbd() + "," + score.getLbd() + "," + score.getEpr() + ","
					+ score.getLpr() + "," + score.getEms() + "," + score.getLms() + "," + score.getClear() + ","
					+ score.getMinbp() + "," + score.getCombo() + "," + score.getMode() + "," + score.getClearcount()
					+ "," + score.getPlaycount() + "," + score.getOption() + "," + score.getRandom() + ","
					+ score.getTrophy() + "," + score.getDate()).getBytes());
			cipher_byte = md.digest();
			StringBuilder sb = new StringBuilder(2 * cipher_byte.length);
			for (byte b : cipher_byte) {
				sb.append(String.format("%02x", b & 0xff));
			}
			return "035" + sb.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private ScoreLog updateScore(ScoreData score, ScoreData newscore, String hash, boolean updateScore) {
		ScoreLog log = new ScoreLog();
		
		log.setOldclear(score.getClear());
		log.setClear(score.getClear());
		if (score.getClear() < newscore.getClear()) {
			log.setSha256(hash);
			log.setClear(newscore.getClear());
		}
		log.setOldscore(score.getExscore());
		log.setScore(score.getExscore());
		if (score.getExscore() < newscore.getExscore() && updateScore) {
			log.setSha256(hash);
			log.setScore(newscore.getExscore());
		}
		log.setOldminbp(score.getMinbp());
		log.setMinbp(score.getMinbp());
		if (score.getMinbp() > newscore.getMinbp() && updateScore) {
			log.setSha256(hash);
			log.setMinbp(newscore.getMinbp());
		}
		log.setOldavgjudge(score.getAvgjudge());
		log.setAvgjudge(score.getAvgjudge());
		if (score.getAvgjudge() > newscore.getAvgjudge() && updateScore) {
			log.setSha256(hash);
			log.setAvgjudge(newscore.getAvgjudge());
		}
		log.setOldcombo(score.getCombo());
		log.setCombo(score.getCombo());
		if (score.getCombo() < newscore.getCombo() && updateScore) {
			log.setSha256(hash);
			log.setCombo(newscore.getCombo());
		}
		
		score.update(newscore, updateScore);

		return log;
	}

	public void deleteScoreData(BMSModel model, int lnmode) {
		scoreDatabase(model).deleteScoreData(scoreHash(model), model.containsUndefinedLongNote() ? lnmode : 0);
	}

	public void deleteScoreData(String sha256, boolean undefinedLongNote, int lnmode) {
		scoredb.deleteScoreData(sha256, undefinedLongNote ? lnmode : 0);
	}

	public void deleteScoreData(SongData song, int lnmode) {
		scoreDatabase(song, false).deleteScoreData(song.getSha256(), songScoreMode(song, lnmode));
	}

	public void deleteScoreData(
			SongData[] songs,
			int lnmode,
			CourseData.CourseDataConstraint[] constraints
	) {
		StringBuilder hash = new StringBuilder();
		boolean undefinedLongNote = false;
		for (SongData song : songs) {
			hash.append(song.getSha256());
			undefinedLongNote |= separatesScore(song) || BMSIRLongNoteMode.authoredUndefined(song);
		}
		for (int option = 0; option < 3; option++) {
			(Arrays.stream(songs).anyMatch(this::separatesScore) ? forcedScoredb : scoredb).deleteScoreData(
					hash.toString(),
					courseScoreMode(undefinedLongNote, lnmode, option, constraints)
			);
		}
	}

	static int courseScoreMode(
			boolean undefinedLongNote,
			int lnmode,
			int option,
			CourseData.CourseDataConstraint[] constraints
	) {
		int hispeed = 0;
		int judge = 0;
		int gauge = 0;
		for (CourseData.CourseDataConstraint constraint : constraints) {
			switch (constraint) {
			case NO_SPEED -> hispeed = 1;
			case NO_GOOD -> judge = 1;
			case NO_GREAT -> judge = 2;
			case GAUGE_LR2 -> gauge = 1;
			case GAUGE_5KEYS -> gauge = 2;
			case GAUGE_7KEYS -> gauge = 3;
			case GAUGE_9KEYS -> gauge = 4;
			case GAUGE_24KEYS -> gauge = 5;
			default -> {
			}
			}
		}
		return (undefinedLongNote ? lnmode : 0)
				+ option * 10
				+ hispeed * 100
				+ judge * 1000
				+ gauge * 10000;
	}

	public boolean existsReplayData(BMSModel model, int lnmode, int index) {
		if (bms.player.beatoraja.pattern.BMSIRSevenToNineModifier.isApplied(model)) return false;
		return Files.exists(Paths.get(replayReadPath(model, lnmode, index) + ".brd"));
	}

	public boolean existsReplayData(SongData song, String hash, int lnmode, int index) {
		boolean authoredUndefined = song.getBMSModel() == null ? song.hasUndefinedLongNote()
				: BMSIRLongNoteMode.authoredUndefined(song.getBMSModel());
		return ((forceLn() || BMSIRLongNoteMode.isApplied(song.getBMSModel()))
				&& existsReplayData(BMSIRLongNoteMode.replayHash(song, hash), song.hasAnyLongNote(), lnmode, index))
				|| existsReplayData(hash, authoredUndefined, lnmode, index);
	}

	public boolean existsReplayData(String hash, boolean ln, int lnmode, int index) {
		return Files.exists(Paths.get(this.getReplayDataFilePath(hash, ln, lnmode, index) + ".brd"));
	}

	public boolean existsReplayData(BMSModel[] models, int lnmode, int index,
			CourseData.CourseDataConstraint[] constraint) {
		String[] hash = new String[models.length];
		boolean ln = false;
		for (int i = 0; i < models.length; i++) {
			BMSModel model = models[i];
			hash[i] = BMSIRLongNoteMode.replayHash(model, model.getSHA256());
			ln |= model.containsUndefinedLongNote();
		}
		return existsReplayData(hash, ln, lnmode, index, constraint)
				|| existsReplayData(Arrays.stream(models).map(BMSModel::getSHA256).toArray(String[]::new),
						Arrays.stream(models).anyMatch(BMSIRLongNoteMode::authoredUndefined),
						lnmode, index, constraint);
	}

	public boolean existsReplayData(String[] hash, boolean ln, int lnmode, int index,
			CourseData.CourseDataConstraint[] constraint) {
		return Files.exists(Paths.get(this.getReplayDataFilePath(hash, ln, lnmode, index, constraint) + ".brd"));
	}

	/**
	 * リプレイデータを読み込む
	 * 
	 * @param model
	 *            対象のBMS
	 * @param lnmode
	 *            LNモード
	 * @return リプレイデータ
	 */
	public ReplayData readReplayData(BMSModel model, int lnmode, int index) {
		if (existsReplayData(model, lnmode, index)) {
			Json json = new Json();
			json.setIgnoreUnknownFields(true);
			try {
				String path = replayReadPath(model, lnmode, index);
				ReplayData result = null;
				if (Files.exists(Paths.get(path + ".brd"))) {
					result =  json.fromJson(ReplayData.class, new BufferedInputStream(
							new GZIPInputStream(Files.newInputStream(Paths.get(path + ".brd")))));
				}
				if(result != null && result.validate()) {
					return result;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * リプレイデータを書き込む
	 * 
	 * @param rd
	 *            リプレイデータ
	 * @param model
	 *            対象のBMS
	 * @param lnmode
	 *            LNモード
	 */
	public void wrireReplayData(ReplayData rd, BMSModel model, int lnmode, int index) {
		if (bms.player.beatoraja.pattern.BMSIRSevenToNineModifier.isApplied(model)) return;
		File replaydir = new File(this.getReplayDataFolder());
		if (!replaydir.exists()) {
			replaydir.mkdirs();
		}
		Json json = new Json();
		json.setOutputType(OutputType.json);
		try {
			String path = this.getReplayDataFilePath(model, lnmode, index) + ".brd";
			rd.shrink();
			OutputStreamWriter fw = new OutputStreamWriter(
					new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(path))), "UTF-8");
			fw.write(json.prettyPrint(rd));
			fw.flush();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public ReplayData[] readReplayData(BMSModel[] models, int lnmode, int index,
			CourseData.CourseDataConstraint[] constraint) {
		String[] hashes = new String[models.length];
		boolean ln = false;
		for (int i = 0; i < models.length; i++) {
			hashes[i] = BMSIRLongNoteMode.replayHash(models[i], models[i].getSHA256());
			ln |= models[i].containsUndefinedLongNote();
		}
		if (existsReplayData(hashes, ln, lnmode, index, constraint)) {
			return readReplayData(hashes, ln, lnmode, index, constraint);
		}
		return readReplayData(Arrays.stream(models).map(BMSModel::getSHA256).toArray(String[]::new),
				Arrays.stream(models).anyMatch(BMSIRLongNoteMode::authoredUndefined),
				lnmode, index, constraint);
	}

	/**
	 * コースリプレイデータを読み込む
	 * 
	 * @param hash
	 *            対象のBMSハッシュ群
	 * @param lnmode
	 *            LNモード
	 * @return リプレイデータ
	 */
	public ReplayData[] readReplayData(String[] hash, boolean ln, int lnmode, int index,
			CourseData.CourseDataConstraint[] constraint) {
		if (existsReplayData(hash, ln, lnmode, index, constraint)) {
			Json json = new Json();
			json.setIgnoreUnknownFields(true);
			try {
				String path = this.getReplayDataFilePath(hash, ln, lnmode, index, constraint);
				ReplayData[] result = null;
				if (Files.exists(Paths.get(path + ".brd"))) {
					result = json.fromJson(ReplayData[].class, new BufferedInputStream(
							new GZIPInputStream(Files.newInputStream(Paths.get(path + ".brd")))));
				}
				if(result != null) {
					for(ReplayData rd : result) {
						if(rd == null || !rd.validate()) {
							return null;
						}
					}
					return result;
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public void wrireReplayData(ReplayData[] rd, BMSModel[] models, int lnmode, int index,
			CourseData.CourseDataConstraint[] constraint) {
		String[] hashes = new String[models.length];
		boolean ln = false;
		for (int i = 0; i < models.length; i++) {
			hashes[i] = BMSIRLongNoteMode.replayHash(models[i], models[i].getSHA256());
			ln |= models[i].containsUndefinedLongNote();
		}
		this.wrireReplayData(rd, hashes, ln, lnmode, index, constraint);

	}

	/**
	 * コースリプレイデータを書き込む
	 * 
	 * @param rd
	 *            リプレイデータ
	 * @param hash
	 *            対象のBMSハッシュ群
	 * @param lnmode
	 *            LNモード
	 */
	public void wrireReplayData(ReplayData[] rd, String[] hash, boolean ln, int lnmode, int index,
			CourseData.CourseDataConstraint[] constraint) {
		Json json = new Json();
		json.setOutputType(OutputType.json);
		try {
			String path = this.getReplayDataFilePath(hash, ln, lnmode, index, constraint) + ".brd";
			Stream.of(rd).forEach(ReplayData::shrink);
			OutputStreamWriter fw = new OutputStreamWriter(
					new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(path))), "UTF-8");
			fw.write(json.prettyPrint(rd));
			fw.flush();
			fw.close();
			logger.info("コースリプレイを保存:{}", path);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void deleteReplayData(BMSModel model, int lnmode, int index) {
		if (existsReplayData(model, lnmode, index)) {
			try {
				Files.deleteIfExists(Paths.get(replayReadPath(model, lnmode, index) + ".brd"));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private String getReplayDataFilePath(BMSModel model, int lnmode, int index) {
		return getReplayDataFilePath(replayHash(model), model.containsUndefinedLongNote(), lnmode, index);
	}

	private String replayReadPath(BMSModel model, int lnmode, int index) {
		String current = getReplayDataFilePath(model, lnmode, index);
		if (Files.exists(Paths.get(current + ".brd"))) return current;
		return getReplayDataFilePath(baseReplayHash(model), BMSIRLongNoteMode.authoredUndefined(model), lnmode, index);
	}

	private String replayHash(BMSModel model) {
		return BMSIRLongNoteMode.replayHash(model, baseReplayHash(model));
	}

	private String baseReplayHash(BMSModel model) {
		String marked = model.getValues().get(BMSIRManiacPlayContext.MODEL_STORAGE_HASH);
		if (marked != null && !marked.isBlank()) return marked;
		if (playerConfig == null) return model.getSHA256();
		BMSIRManiacSettings selected = BMSIRManiacPlayContext.effectiveSettings(
				playerConfig.getBmsirManiacSettings(), model.getMode());
		return selected != null
				? selected.storageChartId(model.getSHA256())
				: model.getSHA256();
	}

	private String getReplayDataFilePath(String hash, boolean ln, int lnmode, int index) {
		return this.getReplayDataFolder() + File.separatorChar
				+ (ln ? replay[lnmode] : "") + hash + (index > 0 ? "_" + index : "");
	}

	private String getReplayDataFilePath(String[] hashes, boolean ln, int lnmode, int index,
			CourseData.CourseDataConstraint[] constraint) {
		StringBuilder hash = new StringBuilder();
		for (String s : hashes) {
			hash.append(s.substring(0, 10));
		}
		StringBuilder sb = new StringBuilder();
		for (CourseData.CourseDataConstraint c : constraint) {
			if (c != CLASS && c != MIRROR && c != RANDOM) {
				for(int i = 0;i < CourseDataConstraint.values().length;i++) {
					if(c == CourseDataConstraint.values()[i]) {
						sb.append(String.format("%02d", i + 1));
						break;
					}
				}
			}
		}
		return this.getReplayDataFolder() + File.separatorChar
				+ (ln ? replay[lnmode] : "") + hash + (sb.length() > 0 ? "_" + sb.toString() : "")
				+ (index > 0 ? "_" + index : "");
	}

	private String getReplayDataFolder() {
		return playerpath + File.separatorChar + player + File.separatorChar + "replay";
	}
}
