package bms.player.beatoraja.skin.scene;

import bms.player.beatoraja.skin.scene.CompiledScene.CameraTrack;
import bms.player.beatoraja.skin.scene.CompiledScene.ColorTrack;
import bms.player.beatoraja.skin.scene.CompiledScene.FrameTrack;
import bms.player.beatoraja.skin.scene.CompiledScene.Instance;
import bms.player.beatoraja.skin.scene.CompiledScene.RectTrack;
import bms.player.beatoraja.skin.scene.CompiledScene.TransformTrack;

import java.util.Arrays;
import java.util.IdentityHashMap;

/** Traverses a compiled scene into a reusable command buffer. */
public final class SceneEvaluator {
	private static final float EPSILON = 0.000001f;

	private final CompiledScene scene;
	private final RenderCommand[] commands;
	private int commandCount;

	private final float[][] worldStack;
	private final float[][] localStack;
	private final float[][] mulStack;
	private final float[][] addStack;
	private final float[][] hslStack;
	private final float[][] localMulStack;
	private final float[][] localAddStack;
	private final float[][] localHslStack;
	private final int[] projectionStack;
	private final float[][] cameraStack;
	private final float[][] localCameraStack;
	private final float[][] localRectStack;
	private final float[][] trsTranslation;
	private final float[][] trsRotation;
	private final float[][] trsScale;
	private final float[][] trsPivot;

	private final float[][] maskWorld;
	private final float[][] maskRect;
	private final int[] maskProjection;
	private final float[][] maskCamera;
	private final float[] rootTransform = new float[16];
	private final IdentityHashMap<Object, KeyCursor> keyCursors = new IdentityHashMap<>();

	private long evaluatedTicks;
	private int evaluatedLeaves;

	public SceneEvaluator(CompiledScene scene) {
		this.scene = scene;
		commands = new RenderCommand[scene.maxCommands];
		for (int i = 0; i < commands.length; i++) {
			commands[i] = new RenderCommand(scene.maxMaskDepth);
		}
		int stackSize = scene.maxDepth + 2;
		worldStack = matrixStack(stackSize);
		localStack = matrixStack(stackSize);
		mulStack = vectorStack(stackSize, 4);
		addStack = vectorStack(stackSize, 4);
		hslStack = vectorStack(stackSize, 3);
		localMulStack = vectorStack(stackSize, 4);
		localAddStack = vectorStack(stackSize, 4);
		localHslStack = vectorStack(stackSize, 3);
		projectionStack = new int[stackSize];
		cameraStack = vectorStack(stackSize, 4);
		localCameraStack = vectorStack(stackSize, 4);
		localRectStack = vectorStack(stackSize, 4);
		trsTranslation = vectorStack(stackSize, 3);
		trsRotation = vectorStack(stackSize, 4);
		trsScale = vectorStack(stackSize, 3);
		trsPivot = vectorStack(stackSize, 3);
		maskWorld = matrixStack(Math.max(1, scene.maxMaskDepth));
		maskRect = vectorStack(Math.max(1, scene.maxMaskDepth), 4);
		maskProjection = new int[Math.max(1, scene.maxMaskDepth)];
		maskCamera = vectorStack(Math.max(1, scene.maxMaskDepth), 4);
		for (CompiledScene.Clip clip : scene.clips) {
			for (Instance instance : clip.instances) {
				registerCursor(instance.transform);
				registerCursor(instance.color);
				registerCursor(instance.camera);
				registerCursor(instance.clipRect);
				registerCursor(instance.frameTrack);
			}
		}
		reset();
	}

	/**
	 * Evaluates the scene at an exact tick.
	 *
	 * @param rootAngleDegrees destination rotation around its center
	 */
	public int evaluate(long tick, float x, float y, float width, float height,
			float rootAngleDegrees, float rootR, float rootG, float rootB, float rootA) {
		long start = System.nanoTime();
		commandCount = 0;
		setIdentity(worldStack[0]);
		setRootTransform(rootTransform, x, y, width, height, rootAngleDegrees);
		set(mulStack[0], rootR, rootG, rootB, rootA);
		set(addStack[0], 0, 0, 0, 0);
		set(hslStack[0], 0, 0, 0);
		projectionStack[0] = CompiledScene.PROJECTION_ORTHOGRAPHIC;
		set(cameraStack[0], 0, 0, -1, 1);
		evaluateClip(scene.rootClip, tick, 1, 0);
		evaluatedLeaves = commandCount;
		evaluatedTicks = (System.nanoTime() - start) / 1000;
		return commandCount;
	}

	private void evaluateClip(int clipIndex, long clipTick, int stackDepth, int maskCount) {
		CompiledScene.Clip clip = scene.clips[clipIndex];
		for (Instance instance : clip.instances) {
			if (clipTick < instance.start || clipTick >= instance.end) {
				continue;
			}
			sampleTransform(instance.transform, clipTick, localStack[stackDepth], stackDepth);
			multiply(worldStack[stackDepth], worldStack[stackDepth - 1], localStack[stackDepth]);

			sampleColor(instance.color, clipTick, localMulStack[stackDepth],
					localAddStack[stackDepth], localHslStack[stackDepth]);
			composeColor(stackDepth);
			if (mulStack[stackDepth][3] <= 0 && addStack[stackDepth][3] <= 0) {
				continue;
			}

			if (instance.camera != null) {
				sampleCamera(instance.camera, clipTick, localCameraStack[stackDepth]);
				projectionStack[stackDepth] = instance.camera.projection;
				copy(cameraStack[stackDepth], localCameraStack[stackDepth], 4);
			} else {
				projectionStack[stackDepth] = projectionStack[stackDepth - 1];
				copy(cameraStack[stackDepth], cameraStack[stackDepth - 1], 4);
			}

			int nextMaskCount = maskCount;
			if (instance.clipRect != null) {
				sampleRect(instance.clipRect, clipTick, localRectStack[stackDepth]);
				copy(maskWorld[nextMaskCount], worldStack[stackDepth], 16);
				copy(maskRect[nextMaskCount], localRectStack[stackDepth], 4);
				maskProjection[nextMaskCount] = projectionStack[stackDepth];
				copy(maskCamera[nextMaskCount], cameraStack[stackDepth], 4);
				nextMaskCount++;
			}

			if (instance.kind == CompiledScene.KIND_CLIP) {
				long childTick = childTick(instance, clipTick, scene.clips[instance.reference]);
				evaluateClip(instance.reference, childTick, stackDepth + 1, nextMaskCount);
			} else if (commandCount < commands.length) {
				buildCommand(commands[commandCount++], instance, stackDepth, nextMaskCount);
			}
		}
	}

	private long childTick(Instance instance, long parentTick, CompiledScene.Clip child) {
		FrameTrack frames = instance.frameTrack;
		if (frames != null) {
			int index = findKey(frames, frames.times, parentTick);
			if (parentTick >= frames.times[index] && parentTick < frames.ends[index]) {
				return (long) Math.floor(frames.frames[index] * scene.ticksPerSecond
						/ (double) child.framesPerSecond);
			}
		}
		double elapsed = (parentTick - instance.start + instance.startOffset)
				* instance.playbackRate;
		long result = (long) Math.floor(elapsed);
		if (instance.repeat && child.duration > 0) {
			return SceneClock.positiveModulo(result, child.duration);
		}
		return Math.max(0, Math.min(result, child.duration));
	}

	private void buildCommand(RenderCommand command, Instance instance, int depth, int masks) {
		command.textureIndex = instance.reference;
		command.meshIndex = instance.mesh;
		command.blend = instance.blend;
		command.premultipliedAlpha = scene.textures[instance.reference].premultipliedAlpha;
		copy(command.world, worldStack[depth], 16);
		copy(command.colorMul, mulStack[depth], 4);
		copy(command.colorAdd, addStack[depth], 4);
		copy(command.hsl, hslStack[depth], 3);
		copy(command.uvRect, instance.uvRect, 4);
		command.projection = projectionStack[depth];
		copy(command.camera, cameraStack[depth], 4);
		command.maskCount = masks;
		for (int i = 0; i < masks; i++) {
			copy(command.maskWorld[i], maskWorld[i], 16);
			copy(command.maskRect[i], maskRect[i], 4);
			command.maskProjection[i] = maskProjection[i];
			copy(command.maskCamera[i], maskCamera[i], 4);
		}
	}

	private void composeColor(int depth) {
		float[] parentMul = mulStack[depth - 1];
		float[] parentAdd = addStack[depth - 1];
		float[] childMul = localMulStack[depth];
		float[] childAdd = localAddStack[depth];
		for (int i = 0; i < 4; i++) {
			mulStack[depth][i] = childMul[i] * parentMul[i];
			addStack[depth][i] = childAdd[i] * parentMul[i] + parentAdd[i];
		}
		for (int i = 0; i < 3; i++) {
			hslStack[depth][i] = hslStack[depth - 1][i] + localHslStack[depth][i];
		}
	}

	private void sampleTransform(TransformTrack track, long tick, float[] result, int depth) {
		int index = findKey(track, track.times, tick);
		if (index == track.times.length - 1 || track.interpolation == CompiledScene.INTERPOLATION_STEP) {
			if (track.trs) {
				matrixFromTrs(result, track.translations[index], track.rotations[index],
						track.scales[index], track.pivots[index]);
			} else {
				copy(result, track.matrices[index], 16);
			}
			return;
		}
		float rate = rate(track.times, index, tick);
		if (!track.trs) {
			lerp(result, track.matrices[index], track.matrices[index + 1], rate, 16);
			return;
		}
		lerp(trsTranslation[depth], track.translations[index], track.translations[index + 1], rate, 3);
		lerp(trsScale[depth], track.scales[index], track.scales[index + 1], rate, 3);
		lerp(trsPivot[depth], track.pivots[index], track.pivots[index + 1], rate, 3);
		slerp(trsRotation[depth], track.rotations[index], track.rotations[index + 1], rate);
		matrixFromTrs(result, trsTranslation[depth], trsRotation[depth], trsScale[depth], trsPivot[depth]);
	}

	private void sampleColor(ColorTrack track, long tick, float[] mul, float[] add, float[] hsl) {
		int index = findKey(track, track.times, tick);
		if (index == track.times.length - 1 || track.interpolation == CompiledScene.INTERPOLATION_STEP) {
			copy(mul, track.mul[index], 4);
			copy(add, track.add[index], 4);
			copy(hsl, track.hsl[index], 3);
			return;
		}
		float rate = rate(track.times, index, tick);
		lerp(mul, track.mul[index], track.mul[index + 1], rate, 4);
		lerp(add, track.add[index], track.add[index + 1], rate, 4);
		lerp(hsl, track.hsl[index], track.hsl[index + 1], rate, 3);
	}

	private void sampleCamera(CameraTrack track, long tick, float[] result) {
		int index = findKey(track, track.times, tick);
		if (index == track.times.length - 1 || track.interpolation == CompiledScene.INTERPOLATION_STEP) {
			copy(result, track.centers[index], 3);
			result[3] = track.focalLengths[index];
			return;
		}
		float rate = rate(track.times, index, tick);
		lerp(result, track.centers[index], track.centers[index + 1], rate, 3);
		result[3] = track.focalLengths[index]
				+ (track.focalLengths[index + 1] - track.focalLengths[index]) * rate;
	}

	private void sampleRect(RectTrack track, long tick, float[] result) {
		int index = findKey(track, track.times, tick);
		if (index == track.times.length - 1 || track.interpolation == CompiledScene.INTERPOLATION_STEP) {
			copy(result, track.rects[index], 4);
			return;
		}
		lerp(result, track.rects[index], track.rects[index + 1],
				rate(track.times, index, tick), 4);
	}

	private int findKey(Object track, long[] times, long tick) {
		KeyCursor cursor = keyCursors.get(track);
		if (cursor != null && tick >= cursor.tick) {
			while (cursor.index + 1 < times.length && times[cursor.index + 1] <= tick) {
				cursor.index++;
			}
			cursor.tick = tick;
			return cursor.index;
		}
		int low = 0;
		int high = times.length - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			if (times[middle] <= tick) {
				low = middle + 1;
			} else {
				high = middle - 1;
			}
		}
		int result = Math.max(0, high);
		if (cursor != null) {
			cursor.index = result;
			cursor.tick = tick;
		}
		return result;
	}

	private void registerCursor(Object track) {
		if (track != null) {
			keyCursors.put(track, new KeyCursor());
		}
	}

	private static final class KeyCursor {
		private int index;
		private long tick = Long.MIN_VALUE;
	}

	private static float rate(long[] times, int index, long tick) {
		long span = times[index + 1] - times[index];
		if (span <= 0) return 0;
		return Math.max(0, Math.min(1, (float) (tick - times[index]) / span));
	}

	private void setRootTransform(float[] result, float x, float y, float width,
			float height, float angleDegrees) {
		setIdentity(result);
		float scaleX = width / scene.canvasWidth;
		float scaleY = height / scene.canvasHeight;
		result[0] = scaleX;
		result[5] = scene.topLeftOrigin ? -scaleY : scaleY;
		result[12] = x;
		result[13] = scene.topLeftOrigin ? y + height : y;
		if (angleDegrees == 0) {
			return;
		}
		float radians = (float) Math.toRadians(angleDegrees);
		float cosine = (float) Math.cos(radians);
		float sine = (float) Math.sin(radians);
		float centerX = x + width * 0.5f;
		float centerY = y + height * 0.5f;
		float[] rotation = localStack[0];
		setIdentity(rotation);
		rotation[0] = cosine; rotation[4] = -sine;
		rotation[1] = sine; rotation[5] = cosine;
		rotation[12] = centerX - cosine * centerX + sine * centerY;
		rotation[13] = centerY - sine * centerX - cosine * centerY;
		float[] copy = worldStack[0];
		System.arraycopy(result, 0, copy, 0, 16);
		multiply(result, rotation, copy);
		setIdentity(worldStack[0]);
	}

	static void matrixFromTrs(float[] result, float[] translation, float[] quaternion,
			float[] scale, float[] pivot) {
		float x = quaternion[0], y = quaternion[1], z = quaternion[2], w = quaternion[3];
		float xx = x * x, xy = x * y, xz = x * z, xw = x * w;
		float yy = y * y, yz = y * z, yw = y * w;
		float zz = z * z, zw = z * w;
		result[0] = (1 - 2 * (yy + zz)) * scale[0];
		result[1] = 2 * (xy + zw) * scale[0];
		result[2] = 2 * (xz - yw) * scale[0];
		result[3] = 0;
		result[4] = 2 * (xy - zw) * scale[1];
		result[5] = (1 - 2 * (xx + zz)) * scale[1];
		result[6] = 2 * (yz + xw) * scale[1];
		result[7] = 0;
		result[8] = 2 * (xz + yw) * scale[2];
		result[9] = 2 * (yz - xw) * scale[2];
		result[10] = (1 - 2 * (xx + yy)) * scale[2];
		result[11] = 0;
		result[12] = translation[0] + pivot[0]
				- (result[0] * pivot[0] + result[4] * pivot[1] + result[8] * pivot[2]);
		result[13] = translation[1] + pivot[1]
				- (result[1] * pivot[0] + result[5] * pivot[1] + result[9] * pivot[2]);
		result[14] = translation[2] + pivot[2]
				- (result[2] * pivot[0] + result[6] * pivot[1] + result[10] * pivot[2]);
		result[15] = 1;
	}

	private static void slerp(float[] result, float[] from, float[] to, float rate) {
		float dot = from[0] * to[0] + from[1] * to[1] + from[2] * to[2] + from[3] * to[3];
		float sign = dot < 0 ? -1 : 1;
		dot = Math.abs(dot);
		if (dot > 0.9995f) {
			for (int i = 0; i < 4; i++) {
				result[i] = from[i] + (to[i] * sign - from[i]) * rate;
			}
			normalize(result);
			return;
		}
		float theta = (float) Math.acos(Math.min(1, dot));
		float denominator = (float) Math.sin(theta);
		float a = (float) Math.sin((1 - rate) * theta) / denominator;
		float b = (float) Math.sin(rate * theta) / denominator * sign;
		for (int i = 0; i < 4; i++) {
			result[i] = from[i] * a + to[i] * b;
		}
	}

	private static void normalize(float[] value) {
		float length = (float) Math.sqrt(value[0] * value[0] + value[1] * value[1]
				+ value[2] * value[2] + value[3] * value[3]);
		if (length < EPSILON) {
			set(value, 0, 0, 0, 1);
		} else {
			for (int i = 0; i < 4; i++) value[i] /= length;
		}
	}

	static void multiply(float[] result, float[] left, float[] right) {
		for (int column = 0; column < 4; column++) {
			float r0 = right[column * 4];
			float r1 = right[column * 4 + 1];
			float r2 = right[column * 4 + 2];
			float r3 = right[column * 4 + 3];
			result[column * 4] = left[0] * r0 + left[4] * r1 + left[8] * r2 + left[12] * r3;
			result[column * 4 + 1] = left[1] * r0 + left[5] * r1 + left[9] * r2 + left[13] * r3;
			result[column * 4 + 2] = left[2] * r0 + left[6] * r1 + left[10] * r2 + left[14] * r3;
			result[column * 4 + 3] = left[3] * r0 + left[7] * r1 + left[11] * r2 + left[15] * r3;
		}
	}

	private static void lerp(float[] result, float[] from, float[] to, float rate, int length) {
		for (int i = 0; i < length; i++) {
			result[i] = from[i] + (to[i] - from[i]) * rate;
		}
	}

	private static void copy(float[] target, float[] source, int length) {
		System.arraycopy(source, 0, target, 0, length);
	}

	private static float[][] matrixStack(int count) {
		return vectorStack(count, 16);
	}

	private static float[][] vectorStack(int count, int length) {
		float[][] result = new float[count][length];
		return result;
	}

	private static void setIdentity(float[] value) {
		Arrays.fill(value, 0);
		value[0] = value[5] = value[10] = value[15] = 1;
	}

	private static void set(float[] value, float a, float b, float c) {
		value[0] = a; value[1] = b; value[2] = c;
	}

	private static void set(float[] value, float a, float b, float c, float d) {
		value[0] = a; value[1] = b; value[2] = c; value[3] = d;
	}

	public RenderCommand[] getCommands() {
		return commands;
	}

	public int getCommandCount() {
		return commandCount;
	}

	public float[] getRootTransform() {
		return rootTransform;
	}

	public long getEvaluationMicros() {
		return evaluatedTicks;
	}

	public int getEvaluatedLeaves() {
		return evaluatedLeaves;
	}

	public void reset() {
		commandCount = 0;
		evaluatedTicks = 0;
		evaluatedLeaves = 0;
		for (KeyCursor cursor : keyCursors.values()) {
			cursor.index = 0;
			cursor.tick = Long.MIN_VALUE;
		}
	}
}
