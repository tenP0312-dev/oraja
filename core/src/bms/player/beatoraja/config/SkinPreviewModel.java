package bms.player.beatoraja.config;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.ReplayData;
import bms.player.beatoraja.song.SongData;

import java.util.ArrayList;
import java.util.List;

/** Creates deterministic in-memory content for live skin previews. */
public final class SkinPreviewModel {
	static final double BPM = 150.0;
	static final long STEP_MICROS = 200_000L;
	static final int STEP_COUNT = 64;

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
			long time = 800_000L + step * STEP_MICROS;
			TimeLine timeline = new TimeLine(step / 8.0, time, mode.key);
			timeline.setBPM(BPM);
			timeline.setScroll(1.0);
			timeline.setSectionLine(step % 8 == 0);

			int primaryLane = playableLane(mode, step * 5 + step / 4);
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
				int chordLane = playableLane(mode, primaryLane + Math.max(2, mode.key / 3));
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
		return resource;
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

	private static int playableLane(Mode mode, int seed) {
		int lane = Math.floorMod(seed, mode.key);
		for (int attempts = 0; attempts < mode.key && mode.isScratchKey(lane); attempts++) {
			lane = (lane + 1) % mode.key;
		}
		return lane;
	}
}
