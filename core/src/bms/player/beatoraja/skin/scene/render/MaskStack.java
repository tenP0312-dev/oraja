package bms.player.beatoraja.skin.scene.render;

import bms.player.beatoraja.skin.scene.RenderCommand;
import bms.player.beatoraja.skin.scene.SceneProjection;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.math.Matrix4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

/** Applies nested hard rectangle masks through scissor or a stencil stack. */
final class MaskStack {
	private static final Logger logger = LoggerFactory.getLogger(MaskStack.class);
	private static final float EPSILON = 0.01f;
	private final float[][] projected = new float[8][8];
	private final float[] homogeneousPoint = new float[3];
	private final IntBuffer viewport = BufferUtils.newIntBuffer(4);
	private boolean warnedMissingStencil;

	boolean apply(RenderCommand command, ProjectiveBatch batch, Texture texture,
			Matrix4 screenTransform) {
		batch.flush();
		resetState();
		if (command.maskCount == 0) return true;
		viewport.clear();
		Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
		boolean axisAligned = true;
		for (int i = 0; i < command.maskCount; i++) {
			if (!project(command.maskWorld[i], command.maskRect[i], command.maskProjection[i],
					command.maskCamera[i], screenTransform.val, projected[i],
					viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3),
					homogeneousPoint)) {
				return false;
			}
			axisAligned &= isAxisAligned(projected[i]);
		}
		if (axisAligned) {
			return applyScissor(command.maskCount);
		}
		if (Gdx.graphics.getBufferFormat().stencil > 0) {
			applyStencil(command, batch, texture);
			return true;
		}
		if (!warnedMissingStencil) {
			warnedMissingStencil = true;
			logger.warn("No stencil buffer is available; projective scene masks use a conservative scissor fallback");
		}
		return applyScissorBounds(command.maskCount);
	}

	private boolean applyScissor(int count) {
		float left = Float.NEGATIVE_INFINITY;
		float bottom = Float.NEGATIVE_INFINITY;
		float right = Float.POSITIVE_INFINITY;
		float top = Float.POSITIVE_INFINITY;
		for (int i = 0; i < count; i++) {
			float[] p = projected[i];
			left = Math.max(left, Math.min(p[0], p[4]));
			bottom = Math.max(bottom, Math.min(p[1], p[5]));
			right = Math.min(right, Math.max(p[0], p[4]));
			top = Math.min(top, Math.max(p[1], p[5]));
		}
		return enableScissor(left, bottom, right, top);
	}

	private boolean applyScissorBounds(int count) {
		float left = Float.NEGATIVE_INFINITY;
		float bottom = Float.NEGATIVE_INFINITY;
		float right = Float.POSITIVE_INFINITY;
		float top = Float.POSITIVE_INFINITY;
		for (int i = 0; i < count; i++) {
			float localLeft = Float.POSITIVE_INFINITY, localBottom = Float.POSITIVE_INFINITY;
			float localRight = Float.NEGATIVE_INFINITY, localTop = Float.NEGATIVE_INFINITY;
			for (int point = 0; point < 4; point++) {
				localLeft = Math.min(localLeft, projected[i][point * 2]);
				localBottom = Math.min(localBottom, projected[i][point * 2 + 1]);
				localRight = Math.max(localRight, projected[i][point * 2]);
				localTop = Math.max(localTop, projected[i][point * 2 + 1]);
			}
			left = Math.max(left, localLeft); bottom = Math.max(bottom, localBottom);
			right = Math.min(right, localRight); top = Math.min(top, localTop);
		}
		return enableScissor(left, bottom, right, top);
	}

	private boolean enableScissor(float left, float bottom, float right, float top) {
		if (!(right > left && top > bottom)) return false;
		int viewportX = viewport.get(0), viewportY = viewport.get(1);
		int viewportWidth = viewport.get(2), viewportHeight = viewport.get(3);
		int x = Math.max(viewportX, (int) Math.floor(left));
		int y = Math.max(viewportY, (int) Math.floor(bottom));
		int width = Math.min(viewportX + viewportWidth - x,
				(int) Math.ceil(right) - x);
		int height = Math.min(viewportY + viewportHeight - y,
				(int) Math.ceil(top) - y);
		if (width <= 0 || height <= 0) return false;
		Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glScissor(x, y, width, height);
		return true;
	}

	private void applyStencil(RenderCommand command, ProjectiveBatch batch, Texture texture) {
		Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
		Gdx.gl.glStencilMask(0xff);
		Gdx.gl.glClearStencil(0);
		Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
		Gdx.gl.glColorMask(false, false, false, false);
		for (int i = 0; i < command.maskCount; i++) {
			if (i == 0) {
				Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xff);
				Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
			} else {
				Gdx.gl.glStencilFunc(GL20.GL_EQUAL, i, 0xff);
				Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_INCR);
			}
			batch.drawMask(texture, command.maskWorld[i], command.maskRect[i],
					command.maskProjection[i], command.maskCamera[i]);
		}
		Gdx.gl.glColorMask(true, true, true, true);
		Gdx.gl.glStencilMask(0x00);
		Gdx.gl.glStencilFunc(GL20.GL_EQUAL, command.maskCount, 0xff);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
	}

	private static boolean project(float[] world, float[] rect, int projection, float[] camera,
			float[] screen, float[] result, int viewportX, int viewportY,
			int viewportWidth, int viewportHeight, float[] homogeneousPoint) {
		for (int i = 0; i < 4; i++) {
			float x = rect[0] + (i == 1 || i == 2 ? rect[2] : 0);
			float y = rect[1] + (i >= 2 ? rect[3] : 0);
			float worldX = world[0] * x + world[4] * y + world[12];
			float worldY = world[1] * x + world[5] * y + world[13];
			float worldZ = world[2] * x + world[6] * y + world[14];
			if (!SceneProjection.project(worldX, worldY, worldZ, projection, camera,
					homogeneousPoint)) return false;
			worldX = homogeneousPoint[0];
			worldY = homogeneousPoint[1];
			worldZ = homogeneousPoint[2];
			float clipX = screen[0] * worldX + screen[4] * worldY + screen[12] * worldZ;
			float clipY = screen[1] * worldX + screen[5] * worldY + screen[13] * worldZ;
			float projectedW = screen[3] * worldX + screen[7] * worldY + screen[15] * worldZ;
			if (Math.abs(projectedW) <= 0.000001f) return false;
			result[i * 2] = viewportX + (clipX / projectedW + 1) * viewportWidth * 0.5f;
			result[i * 2 + 1] = viewportY + (clipY / projectedW + 1) * viewportHeight * 0.5f;
		}
		return true;
	}

	private static boolean isAxisAligned(float[] point) {
		return close(point[0], point[6]) && close(point[2], point[4])
				&& close(point[1], point[3]) && close(point[5], point[7]);
	}

	private static boolean close(float left, float right) {
		return Math.abs(left - right) <= EPSILON;
	}

	void reset() {
		resetState();
	}

	private static void resetState() {
		Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
		Gdx.gl.glStencilMask(0xff);
		Gdx.gl.glColorMask(true, true, true, true);
	}
}
