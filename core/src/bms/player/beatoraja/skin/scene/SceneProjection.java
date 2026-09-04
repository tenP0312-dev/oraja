package bms.player.beatoraja.skin.scene;

/** Shared focal-camera reference math used by CPU clipping and mask projection. */
public final class SceneProjection {
	public static final float CAMERA_EPSILON = 0.0001f;

	private SceneProjection() {
	}

	/**
	 * Produces the homogeneous numerator x/y and w expected by the scene shader.
	 */
	public static boolean project(float x, float y, float z, int projection,
			float[] camera, float[] result) {
		if (projection == CompiledScene.PROJECTION_ORTHOGRAPHIC) {
			result[0] = x;
			result[1] = y;
			result[2] = 1;
			return true;
		}
		float denominator = z - camera[2];
		if (denominator <= CAMERA_EPSILON || camera[3] <= 0) {
			return false;
		}
		float clipW = denominator / camera[3];
		result[0] = camera[0] * clipW + x - camera[0];
		result[1] = camera[1] * clipW + y - camera[1];
		result[2] = clipW;
		return true;
	}
}
