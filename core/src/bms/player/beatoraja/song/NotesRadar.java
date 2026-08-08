package bms.player.beatoraja.song;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.MineNote;
import bms.model.TimeLine;

/**
 * 譜面傾向を表すレーダーチャート用の指標。
 *
 * IIDXのような、選曲画面での譜面傾向可視化を想定した簡易実装。
 * 各値は基準値に対する割合を0〜{@link #MAX_RADAR}にクリップして表す。
 */
public class NotesRadar {

	private static final int MAX_RADAR = 200;

	/**
	 * ノーツ密度(ノーツ数/秒)
	 */
	public final int notes;
	/**
	 * 同時押し比率
	 */
	public final int chord;
	/**
	 * 瞬間最大密度(1秒あたり)
	 */
	public final int peak;
	/**
	 * スクラッチ比率
	 */
	public final int scratch;
	/**
	 * BPM変化率
	 */
	public final int soflan;
	/**
	 * ロングノーツ比率
	 */
	public final int charge;

	public NotesRadar(BMSModel model) {
		notes = calculateNotes(model);
		chord = calculateChord(model);
		peak = calculatePeak(model);
		scratch = calculateScratch(model);
		soflan = calculateSoflan(model);
		charge = calculateCharge(model);
	}

	private int normalize(double value, double base) {
		return Math.min(MAX_RADAR, (int) (value / base * 100.0f));
	}

	private int calculateNotes(BMSModel model) {
		double density = (double) model.getTotalNotes() / ((double) model.getLastTime() / 1000.0f);
		return normalize(density, 12.0f);
	}

	private int calculateChord(BMSModel model) {
		int chordCount = 0;
		int chordNotes = 0;
		for (TimeLine tl : model.getAllTimeLines()) {
			int simultaneous = 0;
			for (int i = 0; i < model.getMode().key; i++) {
				if (tl.getNote(i) != null && !(tl.getNote(i) instanceof MineNote)) {
					simultaneous++;
				}
			}
			if (simultaneous >= 2) {
				chordCount++;
				chordNotes += simultaneous;
			}
		}
		if (model.getTotalNotes() == 0) {
			return 0;
		}
		return normalize((double) chordNotes / (double) model.getTotalNotes(), 0.5f);
	}

	private int calculatePeak(BMSModel model) {
		int[] notesPerSecond = new int[model.getLastTime() / 1000 + 2];
		for (TimeLine tl : model.getAllTimeLines()) {
			int second = tl.getTime() / 1000;
			if (second < 0) {
				continue;
			}
			if (second >= notesPerSecond.length) {
				second = notesPerSecond.length - 1;
			}
			int simultaneous = 0;
			for (int i = 0; i < model.getMode().key; i++) {
				if (tl.getNote(i) != null && !(tl.getNote(i) instanceof MineNote)) {
					simultaneous++;
				}
			}
			notesPerSecond[second] += simultaneous;
		}
		int peak = 0;
		for (int count : notesPerSecond) {
			peak = Math.max(peak, count);
		}
		return normalize(peak, 25.0f);
	}

	private int calculateScratch(BMSModel model) {
		int[] scratchKeys = model.getMode().scratchKey;
		if (scratchKeys.length == 0) {
			return 0;
		}
		int scratchNotes = 0;
		for (TimeLine tl : model.getAllTimeLines()) {
			for (int key : scratchKeys) {
				if (tl.getNote(key) != null && !(tl.getNote(key) instanceof MineNote)) {
					scratchNotes++;
				}
			}
		}
		if (model.getTotalNotes() == 0) {
			return 0;
		}
		return normalize((double) scratchNotes / (double) model.getTotalNotes(), 0.15);
	}

	private int calculateSoflan(BMSModel model) {
		if (model.getMinBPM() == model.getMaxBPM()) {
			return 0;
		}
		double ratio = model.getMaxBPM() / model.getMinBPM();
		return normalize(ratio - 1.0f, 1.0f);
	}

	private int calculateCharge(BMSModel model) {
		int longNotes = 0;
		for (TimeLine tl : model.getAllTimeLines()) {
			for (int i = 0; i < model.getMode().key; i++) {
				if (tl.getNote(i) instanceof LongNote) {
					longNotes++;
				}
			}
		}
		if (model.getTotalNotes() == 0) {
			return 0;
		}
		return normalize((double) longNotes / (double) model.getTotalNotes(), 0.2);
	}

	@Override
	public String toString() {
		return String.format("NOTES:%d CHORD:%d PEAK:%d SCRATCH:%d SOFLAN:%d CHARGE:%d",
				notes, chord, peak, scratch, soflan, charge);
	}
}
