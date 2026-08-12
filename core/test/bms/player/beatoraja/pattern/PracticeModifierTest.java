package bms.player.beatoraja.pattern;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PracticeModifierTest {

	@Test
	void scalesTotalForGrooveGaugeButKeepsConfiguredTotalForHardGauge() {
		BMSModel groove = modelWithOneOfTwoNotesInsidePracticeRange();
		new PracticeModifier(0, 500, 2).modify(groove);
		assertEquals(50.0, groove.getTotal());

		BMSModel hard = modelWithOneOfTwoNotesInsidePracticeRange();
		new PracticeModifier(0, 500, 3).modify(hard);
		assertEquals(100.0, hard.getTotal());
	}

	private static BMSModel modelWithOneOfTwoNotesInsidePracticeRange() {
		BMSModel model = new BMSModel();
		model.setMode(Mode.BEAT_7K);
		model.setTotal(100.0);
		TimeLine inside = new TimeLine(0.0, 0, Mode.BEAT_7K.key);
		inside.setNote(0, new NormalNote(1));
		TimeLine outside = new TimeLine(1.0, 1_000_000, Mode.BEAT_7K.key);
		outside.setNote(0, new NormalNote(2));
		model.setAllTimeLine(new TimeLine[]{inside, outside});
		return model;
	}
}
