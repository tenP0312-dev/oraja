package bms.player.beatoraja.skin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinBPMGraphTest {

	@Test
	void findsTheMaximumPositiveSpeedInsteadOfTheMinimum() {
		double[][] changes = {
				{120.0, 0.0},
				{240.0, 1_000.0},
				{0.0, 2_000.0},
				{-120.0, 3_000.0}
		};

		assertEquals(240.0, SkinBPMGraph.maximumSpeed(changes));
	}
}
