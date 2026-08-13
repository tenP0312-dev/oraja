package bms.player.beatoraja.config;

import bms.player.beatoraja.MainState;
import bms.player.beatoraja.SpriteBatchHelper;
import bms.player.beatoraja.skin.Skin;
import bms.player.beatoraja.skin.Skin.SkinObjectRenderer;
import bms.player.beatoraja.skin.SkinObject;
import bms.player.beatoraja.skin.SkinObject.SkinOffset;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bms.player.beatoraja.skin.SkinProperty.OFFSET_HIDDEN_COVER;
import static bms.player.beatoraja.skin.SkinProperty.OFFSET_LANECOVER;
import static bms.player.beatoraja.skin.SkinProperty.OFFSET_LIFT;

/**
 * Skin Select object that renders the currently selected skin into a bounded
 * off-screen buffer and displays the result at this object's destination.
 */
public final class SkinPreview extends SkinObject {
	private static final Logger logger = LoggerFactory.getLogger(SkinPreview.class);
	private static final int MAX_BUFFER_DIMENSION = 2048;

	private SkinConfiguration configuration;
	private SpriteBatch previewBatch;
	private FrameBuffer frameBuffer;
	private TextureRegion frameRegion;
	private Skin lastSkin;
	private int bufferWidth;
	private int bufferHeight;
	private boolean disabled;

	@Override
	public void prepare(long time, MainState state) {
		if (configuration == null && state instanceof SkinConfiguration skinConfiguration) {
			configuration = skinConfiguration;
		}
		super.prepare(time, state);
	}

	@Override
	public void draw(SkinObjectRenderer renderer) {
		if (!draw || configuration == null) {
			return;
		}

		Skin previewSkin = configuration.getSelectedSkin();
		if (previewSkin == null) {
			return;
		}
		if (previewSkin != lastSkin) {
			lastSkin = previewSkin;
			disabled = false;
		}
		if (disabled) {
			return;
		}

		SpriteBatch currentBatch = renderer.getSpriteBatch();
		currentBatch.flush();
		currentBatch.end();
		try {
			renderPreview(previewSkin);
		} catch (Throwable e) {
			disabled = true;
			logger.warn("スキンプレビューの描画に失敗したため、このスキンのプレビューを無効化します", e);
		} finally {
			currentBatch.begin();
		}

		if (!disabled && frameRegion != null) {
			draw(renderer, frameRegion);
		}
	}

	private void renderPreview(Skin previewSkin) {
		MainState previewState = configuration.getSelectedSkinState();
		float previewWidth = Math.max(1f, previewSkin.getWidth());
		float previewHeight = Math.max(1f, previewSkin.getHeight());
		int width = bufferDimension(previewWidth, region.width);
		int height = bufferDimension(previewHeight, region.height);
		ensureFrameBuffer(width, height);

		boolean frameBufferBegun = false;
		boolean batchBegun = false;
		SkinOffsetSnapshot[] offsetSnapshots = previewState instanceof SkinPreviewPlayer
				? new SkinOffsetSnapshot[] {
						new SkinOffsetSnapshot(previewState.main.getOffset(OFFSET_LIFT)),
						new SkinOffsetSnapshot(previewState.main.getOffset(OFFSET_LANECOVER)),
						new SkinOffsetSnapshot(previewState.main.getOffset(OFFSET_HIDDEN_COVER))
				}
				: null;
		try {
			frameBuffer.begin();
			frameBufferBegun = true;
			Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
			previewBatch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, previewWidth, previewHeight));
			previewBatch.begin();
			batchBegun = true;
			long stateTime;
			if (previewState instanceof SkinPreviewState statefulPreview) {
				stateTime = statefulPreview.preparePreviewFrame(previewSkin);
			} else {
				previewState.timer.update();
				stateTime = previewState.timer.getNowTime();
			}
			previewSkin.updateCustomObjects(previewState);
			previewSkin.drawAllObjectsSafely(previewBatch, previewState, stateTime);
		} finally {
			if (offsetSnapshots != null) {
				for (SkinOffsetSnapshot snapshot : offsetSnapshots) {
					snapshot.restore();
				}
			}
			try {
				if (batchBegun) {
					previewBatch.end();
				}
			} finally {
				if (frameBufferBegun) {
					frameBuffer.end();
				}
			}
		}
	}

	private static final class SkinOffsetSnapshot {
		private final SkinOffset target;
		private final float x;
		private final float y;
		private final float w;
		private final float h;
		private final float r;
		private final float a;

		private SkinOffsetSnapshot(SkinOffset target) {
			this.target = target;
			x = target.x;
			y = target.y;
			w = target.w;
			h = target.h;
			r = target.r;
			a = target.a;
		}

		private void restore() {
			target.x = x;
			target.y = y;
			target.w = w;
			target.h = h;
			target.r = r;
			target.a = a;
		}
	}

	static int bufferDimension(float skinDimension, float destinationDimension) {
		int skinPixels = Math.max(1, Math.round(Math.abs(skinDimension)));
		int destinationPixels = Math.max(1, Math.round(Math.abs(destinationDimension)));
		return Math.min(MAX_BUFFER_DIMENSION, Math.min(skinPixels, destinationPixels));
	}

	private void ensureFrameBuffer(int width, int height) {
		if (previewBatch == null) {
			previewBatch = SpriteBatchHelper.createSpriteBatch();
		}
		if (frameBuffer != null && bufferWidth == width && bufferHeight == height) {
			return;
		}

		if (frameBuffer != null) {
			frameBuffer.dispose();
		}
		bufferWidth = width;
		bufferHeight = height;
		frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, bufferWidth, bufferHeight, false);
		Texture texture = frameBuffer.getColorBufferTexture();
		frameRegion = new TextureRegion(texture);
		frameRegion.flip(false, true);
		logger.info("スキンプレビュー描画バッファを作成しました : {}x{}", bufferWidth, bufferHeight);
	}

	@Override
	public void dispose() {
		if (frameBuffer != null) {
			frameBuffer.dispose();
			frameBuffer = null;
			frameRegion = null;
		}
		if (previewBatch != null) {
			previewBatch.dispose();
			previewBatch = null;
		}
		lastSkin = null;
		configuration = null;
	}
}
