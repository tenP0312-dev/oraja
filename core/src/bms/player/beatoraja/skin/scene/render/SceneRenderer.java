package bms.player.beatoraja.skin.scene.render;

import bms.player.beatoraja.skin.scene.RenderCommand;
import bms.player.beatoraja.skin.scene.SceneEvaluator;
import bms.player.beatoraja.skin.scene.SkinSceneObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bridges one scene object into the legacy destination draw order. */
public final class SceneRenderer {
	private static final Logger logger = LoggerFactory.getLogger(SceneRenderer.class);
	private final SpriteBatch sprite;
	private final ProjectiveBatch batch;
	private final MaskStack masks = new MaskStack();
	private final Matrix4 rootTransform = new Matrix4();
	private final Matrix4 modelTransform = new Matrix4();
	private final Matrix4 combinedTransform = new Matrix4();

	public SceneRenderer(SpriteBatch sprite) {
		this.sprite = sprite;
		this.batch = new ProjectiveBatch();
	}

	public void draw(SkinSceneObject object) {
		boolean resumeSprite = sprite.isDrawing();
		boolean renderFailed = false;
		batch.beginMetrics();
		try {
			if (resumeSprite) sprite.end();
			Gdx.gl.glDisable(GL20.GL_CULL_FACE);
			Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
			masks.begin();
			SceneEvaluator evaluator = object.getEvaluator();
			rootTransform.set(evaluator.getRootTransform());
			modelTransform.set(sprite.getTransformMatrix()).mul(rootTransform);
			combinedTransform.set(sprite.getProjectionMatrix()).mul(modelTransform);
			batch.setCombinedTransform(combinedTransform);
			RenderCommand[] commands = evaluator.getCommands();
			for (int i = 0; i < evaluator.getCommandCount(); i++) {
				RenderCommand command = commands[i];
				if (masks.apply(command, batch, object.getTexture(command.textureIndex),
						combinedTransform)) {
					batch.draw(object, command);
				}
			}
			batch.flush();
		} catch (Throwable error) {
			renderFailed = true;
			object.disable(error);
			logger.warn("Disabled skin scene {} after a render failure", object.getName(), error);
		} finally {
			if (renderFailed) {
				batch.discard();
			} else {
				try {
					batch.flush();
				} catch (Throwable error) {
					object.disable(error);
					logger.warn("Disabled skin scene {} while flushing renderer state",
							object.getName(), error);
				}
			}
			try {
				masks.reset();
				ProjectiveBatch.restoreBlend();
				Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
			} finally {
				object.updateRenderMetrics(batch.getDrawCalls(), batch.getTransformedVertices());
				if (resumeSprite && !sprite.isDrawing()) sprite.begin();
			}
		}
	}

	public void dispose() {
		batch.dispose();
	}
}
