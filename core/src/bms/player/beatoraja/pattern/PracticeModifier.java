package bms.player.beatoraja.pattern;

import bms.model.*;
import bms.player.beatoraja.PlayerConfig;

/**
 * プラクティス時に選択範囲以外の可視ノーツをBGノーツに移動するクラス
 *
 * @author exch
 */
public class PracticeModifier extends PatternModifier {

	/**
	 * 開始時間(ms)
	 */
	private final long start;
	/**
	 * 終了時間(ms)
	 */
	private final long end;

	private final int gaugeType;

	public PracticeModifier(long start, long end, int gaugeType) {
		super(AssistLevel.ASSIST);
		this.start = start;
		this.end = end;
		this.gaugeType = gaugeType;
	}

	@Override
	public void modify(BMSModel model) {
		int totalnotes = model.getTotalNotes();
		final TimeLine[] tls = model.getAllTimeLines();
		for (TimeLine tl : tls) {
			for (int i = 0; i < model.getMode().key; i++) {
				if(tl.getTime() < start || tl.getTime() >= end) {
					moveToBackground(tls, tl, i);
				}
			}
		}
		// Survival gauges use the configured TOTAL directly; only groove gauges
		// need their TOTAL scaled to the selected practice range.
		if (gaugeType < 3 && totalnotes > 0) {
			model.setTotal(model.getTotal() * model.getTotalNotes() / totalnotes);
		}
	}

}
