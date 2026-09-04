package bms.player.beatoraja.skin.scene;

import bms.player.beatoraja.MainState;
import bms.player.beatoraja.skin.Skin.SkinObjectRenderer;
import bms.player.beatoraja.skin.SkinObject;
import bms.player.beatoraja.skin.property.TimerProperty;
import com.badlogic.gdx.graphics.Texture;

/** A neutral compiled scene inserted at one ordinary destination position. */
public final class SkinSceneObject extends SkinObject implements FrameDrivenSkinObject {
	private final SceneResourceCache.Handle resources;
	private final SceneEvaluator evaluator;
	private final SceneClock clock;
	private final TimerProperty timer;
	private boolean disabled;
	private int renderDrawCalls;
	private int transformedVertices;

	public SkinSceneObject(SceneResourceCache.Handle resources, TimerProperty timer,
			int cycleMillis, float playbackRate, boolean loop) {
		this.resources = resources;
		this.timer = timer;
		CompiledScene scene = resources.scene();
		long cycleTicks = cycleMillis > 0
				? Math.max(1, Math.round(cycleMillis * scene.ticksPerSecond / 1000.0))
				: scene.duration;
		this.clock = new SceneClock(scene.ticksPerSecond, cycleTicks, playbackRate, loop);
		this.evaluator = new SceneEvaluator(scene);
	}

	@Override
	public void prepare(long time, MainState state) {
		prepareFrame(time, state.timer.getNowMicroTime(), state);
	}

	@Override
	public void prepareFrame(long stateTimeMillis, long nowMicros, MainState state) {
		if (disabled) {
			draw = false;
			return;
		}
		super.prepare(stateTimeMillis, state);
		if (!draw) {
			evaluator.reset();
			return;
		}
		long startMicros = 0;
		long clockNowMicros = nowMicros;
		if (timer != null) {
			startMicros = timer.getMicro(state);
			if (startMicros == Long.MIN_VALUE) {
				draw = false;
				evaluator.reset();
				return;
			}
			// TimerProperty values share TimerManager's clock domain. A Skin Select
			// preview may pass a synthetic object time, so use that domain here.
			clockNowMicros = state.timer.getNowMicroTime();
		}
		long tick = clock.toTick(clockNowMicros, startMicros);
		if (clock.movedBackward()) {
			evaluator.reset();
		}
		evaluator.evaluate(tick, region.x, region.y, region.width, region.height,
				angle, color.r, color.g, color.b, color.a);
		draw = evaluator.getCommandCount() > 0;
	}

	@Override
	public void draw(SkinObjectRenderer renderer) {
		renderer.drawScene(this);
	}

	@Override
	public boolean validate() {
		return resources != null && super.validate();
	}

	@Override
	public void resetSkinPreviewCycle() {
		clock.reset();
		evaluator.reset();
	}

	@Override
	public void dispose() {
		resources.close();
		setDisposed();
	}

	public CompiledScene getScene() {
		return resources.scene();
	}

	public Texture getTexture(int index) {
		return resources.texture(index);
	}

	public SceneEvaluator getEvaluator() {
		return evaluator;
	}

	public int getRenderDrawCalls() {
		return renderDrawCalls;
	}

	public int getTransformedVertices() {
		return transformedVertices;
	}

	public void updateRenderMetrics(int drawCalls, int vertices) {
		renderDrawCalls = drawCalls;
		transformedVertices = vertices;
	}

	public void disable(Throwable cause) {
		disabled = true;
		draw = false;
		renderDrawCalls = 0;
		transformedVertices = 0;
	}
}
