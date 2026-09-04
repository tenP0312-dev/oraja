package bms.player.beatoraja.skin.scene.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ProjectiveBatchTest {
	@Test
	void defaultQuadKeepsImageUprightForEitherCanvasOrigin() {
		assertArrayEquals(new float[] {0, 0, 1, 0, 1, 1, 0, 1},
				ProjectiveBatch.defaultQuadUv(true));
		assertArrayEquals(new float[] {0, 1, 1, 1, 1, 0, 0, 0},
				ProjectiveBatch.defaultQuadUv(false));
	}
}
