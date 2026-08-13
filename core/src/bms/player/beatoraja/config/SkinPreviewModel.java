package bms.player.beatoraja.config;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.ClearType;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.PlayerData;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.ReplayData;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.play.GrooveGauge;
import bms.player.beatoraja.song.SongData;

import com.badlogic.gdx.utils.FloatArray;

import java.util.ArrayList;
import java.util.List;

/** Creates deterministic in-memory content for live skin previews. */
public final class SkinPreviewModel {
	static final double BPM = 150.0;
	static final long LEAD_IN_MICROS = 800_000L;
	static final long STEP_MICROS = 200_000L;
	static final int STEP_COUNT = 64;
	private static final long MEASURE_MICROS = Math.round(60_000_000d / BPM * 4d);

	private SkinPreviewModel() {}

	public static BMSModel create(Mode mode) {
		BMSModel model = new BMSModel();
		model.setMode(mode);
		model.setPlayer(mode.player);
		model.setTitle("SKIN PREVIEW");
		model.setSubTitle("VIRTUAL SESSION");
		model.setArtist("BMS-IR Arena oraja");
		model.setGenre("PREVIEW");
		model.setBpm(BPM);
		model.setPlaylevel("12");
		model.setDifficulty(3);
		model.setJudgerank(2);
		model.setTotal(300);
		model.setMD5("00000000000000000000000000000000");
		model.setSHA256("0000000000000000000000000000000000000000000000000000000000000000");

		List<TimeLine> timelines = new ArrayList<>();
		LongNote pendingLongNote = null;
		int pendingLongNoteLane = -1;
		for (int step = 0; step < STEP_COUNT; step++) {
			long time = LEAD_IN_MICROS + step * STEP_MICROS;
			TimeLine timeline = new TimeLine((double) time / MEASURE_MICROS, time, mode.key);
			timeline.setBPM(BPM);
			timeline.setScroll(1.0);
			timeline.setSectionLine(time % MEASURE_MICROS == 0L);

			int primaryLane = playableLane(mode, step * 5 + step / 4, pendingLongNoteLane);
			if (step % 16 == 4) {
				pendingLongNote = new LongNote(0);
				pendingLongNote.setType(LongNote.TYPE_CHARGENOTE);
				timeline.setNote(primaryLane, pendingLongNote);
				pendingLongNoteLane = primaryLane;
			} else if (step % 16 == 10 && pendingLongNote != null) {
				LongNote end = new LongNote(0);
				end.setType(LongNote.TYPE_CHARGENOTE);
				timeline.setNote(pendingLongNoteLane, end);
				pendingLongNote.setPair(end);
				pendingLongNote = null;
				pendingLongNoteLane = -1;
			} else {
				timeline.setNote(primaryLane, new NormalNote(0));
			}

			if (step % 4 == 0 && mode.key > 1) {
				int chordLane = playableLane(
						mode, primaryLane + Math.max(2, mode.key / 3), pendingLongNoteLane);
				if (chordLane != primaryLane && timeline.getNote(chordLane) == null) {
					timeline.setNote(chordLane, new NormalNote(0));
				}
			}
			timelines.add(timeline);
		}
		model.setAllTimeLine(timelines.toArray(TimeLine[]::new));
		return model;
	}

	public static PlayerResource createResource(Config config, PlayerConfig player, Mode mode) {
		BMSModel model = create(mode);
		PlayerResource resource = new PlayerResource(
				new SkinPreviewAudioDriver(), config, player, null);
		resource.setSkinPreviewModel(model);
		resource.setPlayMode(BMSPlayerMode.AUTOPLAY);
		resource.setReplayData(new ReplayData());
		PlayerData playerData = new PlayerData();
		playerData.setPlaycount(4321);
		playerData.setClear(3456);
		playerData.setEpg(654321);
		playerData.setLpg(432123);
		playerData.setEgr(87654);
		playerData.setLgr(76543);
		playerData.setEgd(5432);
		playerData.setLgd(4321);
		playerData.setEbd(876);
		playerData.setLbd(765);
		playerData.setEpr(654);
		playerData.setLpr(543);
		playerData.setEms(432);
		playerData.setLms(321);
		playerData.setPlaytime(987654);
		playerData.setMaxcombo(2468);
		resource.setPlayerData(playerData);
		return resource;
	}

	static ResultData createResultResource(
			Config config, PlayerConfig player, Mode mode, boolean courseResult) {
		PlayerResource resource = createResource(config, player, mode);
		resource.setPlayMode(BMSPlayerMode.PLAY);

		int totalNotes;
		if (courseResult) {
			BMSModel[] course = new BMSModel[4];
			String[] titles = {
					"PREVIEW OPENING", "PREVIEW MIDDLE",
					"PREVIEW CLIMAX", "PREVIEW FINAL"
			};
			totalNotes = 0;
			for (int i = 0; i < course.length; i++) {
				course[i] = create(mode);
				course[i].setTitle(titles[i]);
				course[i].setPlaylevel(String.valueOf(8 + i * 2));
				totalNotes += course[i].getTotalNotes();
			}
			CourseData courseData = new CourseData();
			courseData.setName("VIRTUAL PREVIEW COURSE");
			courseData.setSong(course);
			courseData.setConstraint(new CourseData.CourseDataConstraint[] {
					CourseData.CourseDataConstraint.CLASS
			});
			resource.setSkinPreviewCourse(course, courseData);
		} else {
			totalNotes = resource.getBMSModel().getTotalNotes();
		}

		ScoreData score = createResultScore(mode, totalNotes, true);
		ScoreData oldScore = createResultScore(mode, totalNotes, false);
		ScoreData targetScore = createResultScore(mode, totalNotes, false);
		targetScore.setPlayer("PREVIEW RIVAL");
		targetScore.setEpg(Math.max(0, targetScore.getEpg() - 2));
		targetScore.setLpg(targetScore.getLpg() + 2);
		resource.setScoreData(score);
		resource.setRivalScoreData(targetScore);
		resource.setTargetScoreData(targetScore);
		if (courseResult) {
			resource.setCourseScoreData(score);
		}
		resource.setCombo(score.getCombo());
		resource.setMaxcombo(score.getCombo());
		resource.setUpdateScore(false);
		resource.setUpdateCourseScore(false);

		GrooveGauge gauge = GrooveGauge.create(
				resource.getBMSModel(), GrooveGauge.NORMAL, resource);
		FloatArray[] gaugeHistory = createGaugeHistory(gauge.getGaugeTypeLength(), 0);
		resource.setGauge(gaugeHistory);
		for (int type = 0; type < gauge.getGaugeTypeLength(); type++) {
			gauge.setValue(type, gaugeHistory[type].peek());
		}
		resource.setGrooveGauge(gauge);
		if (courseResult) {
			for (int song = 0; song < 4; song++) {
				resource.addCourseGauge(createGaugeHistory(gauge.getGaugeTypeLength(), song));
			}
		}
		return new ResultData(resource, oldScore);
	}

	private static ScoreData createResultScore(Mode mode, int notes, boolean current) {
		ScoreData score = new ScoreData(mode);
		int pgreat = notes * (current ? 72 : 62) / 100;
		int great = notes * (current ? 18 : 20) / 100;
		int good = notes * (current ? 6 : 9) / 100;
		int bad = notes * (current ? 2 : 4) / 100;
		int poor = Math.max(0, notes - pgreat - great - good - bad);
		score.setNotes(notes);
		score.setEpg(pgreat * 3 / 5);
		score.setLpg(pgreat - score.getEpg());
		score.setEgr(great / 2);
		score.setLgr(great - score.getEgr());
		score.setEgd(good / 2);
		score.setLgd(good - score.getEgd());
		score.setEbd(bad / 2);
		score.setLbd(bad - score.getEbd());
		score.setEpr(poor / 2);
		score.setLpr(poor - score.getEpr());
		score.setCombo(Math.max(1, notes - (current ? 7 : 19)));
		score.setMinbp(current ? 12 : 31);
		score.setPassnotes(notes);
		score.setClear(current ? ClearType.Hard.id : ClearType.Normal.id);
		score.setAvgjudge(current ? -1800L : 4300L);
		score.setAvg(current ? -900L : 2800L);
		score.setStddev(current ? 9200L : 13400L);
		score.setTotalDuration((long) notes * Math.abs(score.getAvgjudge()));
		return score;
	}

	private static FloatArray[] createGaugeHistory(int gaugeTypes, int courseSong) {
		FloatArray[] history = new FloatArray[gaugeTypes];
		for (int type = 0; type < gaugeTypes; type++) {
			history[type] = new FloatArray(48);
			for (int point = 0; point < 48; point++) {
				float progress = point / 47f;
				float wave = (float) Math.sin((point + courseSong * 7) * 0.31f) * 7f;
				history[type].add(Math.max(2f, Math.min(100f,
						24f + courseSong * 8f + progress * 54f + wave)));
			}
		}
		return history;
	}

	static final class ResultData {
		final PlayerResource resource;
		final ScoreData oldScore;

		ResultData(PlayerResource resource, ScoreData oldScore) {
			this.resource = resource;
			this.oldScore = oldScore;
		}
	}

	public static SongData createSong(Mode mode, String title, int difficulty, int level) {
		BMSModel model = create(mode);
		model.setTitle(title);
		model.setDifficulty(difficulty);
		model.setPlaylevel(String.valueOf(level));
		SongData song = new SongData(model, false);
		song.setPath("skin-preview://" + difficulty + "/preview.bms");
		return song;
	}

	private static int playableLane(Mode mode, int seed, int blockedLane) {
		int lane = Math.floorMod(seed, mode.key);
		for (int attempts = 0;
				attempts < mode.key && (mode.isScratchKey(lane) || lane == blockedLane);
				attempts++) {
			lane = (lane + 1) % mode.key;
		}
		return lane;
	}
}
