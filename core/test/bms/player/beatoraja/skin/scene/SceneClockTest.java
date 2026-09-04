package bms.player.beatoraja.skin.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneClockTest {
	@Test
	void absoluteClockIsIndependentOfRenderRate() {
		SceneClock clock = new SceneClock(60000, 150000, 1, true);
		assertEquals(75000, clock.toTick(1_250_000, 0));
		assertEquals(75000, clock.toTick(1_250_000, 0));
		assertEquals(75000, clock.toTick(1_250_000, 0));
	}

	@Test
	void exactFrameBoundariesDoNotAccumulateMillisecondRounding() {
		SceneClock clock = new SceneClock(60000, 150000, 1, false);
		assertEquals(500, clock.toTick(8_334, 0));
		assertEquals(1000, clock.toTick(16_667, 0));
		assertEquals(150000, clock.toTick(2_500_000, 0));
	}

	@Test
	void repeatUsesPositiveModuloBeforeTimerStart() {
		SceneClock clock = new SceneClock(60000, 150000, 1, true);
		assertEquals(149940, clock.toTick(999_000, 1_000_000));
		assertEquals(0, clock.toTick(3_500_000, 1_000_000));
	}

	@Test
	void detectsLoopAndSeekBackwards() {
		SceneClock clock = new SceneClock(1000, 1000, 1, true);
		clock.toTick(900_000, 0);
		assertFalse(clock.movedBackward());
		clock.toTick(1_100_000, 0);
		assertTrue(clock.movedBackward());
		clock.reset();
		clock.toTick(100_000, 0);
		assertFalse(clock.movedBackward());
	}
}
