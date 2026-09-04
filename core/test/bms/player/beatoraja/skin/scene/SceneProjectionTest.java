package bms.player.beatoraja.skin.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneProjectionTest {
	private static final float TOLERANCE = 0.0001f;

	@Test
	void focalPlaneIsOneToOneAndDepthChangesScale() {
		float[] camera = {50, 50, -100, 100};
		float[] projected = new float[3];
		assertTrue(SceneProjection.project(75, 25, 0,
				CompiledScene.PROJECTION_FOCAL, camera, projected));
		assertEquals(75, projected[0] / projected[2], TOLERANCE);
		assertEquals(25, projected[1] / projected[2], TOLERANCE);

		assertTrue(SceneProjection.project(75, 25, 100,
				CompiledScene.PROJECTION_FOCAL, camera, projected));
		assertEquals(62.5f, projected[0] / projected[2], TOLERANCE);
		assertEquals(37.5f, projected[1] / projected[2], TOLERANCE);
	}

	@Test
	void rejectsCameraPlaneAndPointsBehindIt() {
		float[] camera = {0, 0, -100, 100};
		float[] projected = new float[3];
		assertFalse(SceneProjection.project(0, 0, -100,
				CompiledScene.PROJECTION_FOCAL, camera, projected));
		assertFalse(SceneProjection.project(0, 0, -101,
				CompiledScene.PROJECTION_FOCAL, camera, projected));
	}
}
