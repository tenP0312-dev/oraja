package bms.player.beatoraja.skin.scene;

import bms.player.beatoraja.skin.scene.render.BlendMode;

/** Validated, index-based runtime representation of a scene document. */
public final class CompiledScene {
	public static final int KIND_TEXTURE = 0;
	public static final int KIND_MESH = 1;
	public static final int KIND_CLIP = 2;
	public static final int PROJECTION_ORTHOGRAPHIC = 0;
	public static final int PROJECTION_FOCAL = 1;
	public static final int INTERPOLATION_STEP = 0;
	public static final int INTERPOLATION_LINEAR = 1;
	public static final int INTERPOLATION_TRS = 2;

	public final float canvasWidth;
	public final float canvasHeight;
	public final boolean topLeftOrigin;
	public final long ticksPerSecond;
	public final long duration;
	public final TextureResource[] textures;
	public final MeshResource[] meshes;
	public final Clip[] clips;
	public final int rootClip;
	public final int maxCommands;
	public final int maxDepth;
	public final int maxMaskDepth;

	CompiledScene(float canvasWidth, float canvasHeight, boolean topLeftOrigin,
			long ticksPerSecond, long duration, TextureResource[] textures,
			MeshResource[] meshes, Clip[] clips, int rootClip, int maxCommands,
			int maxDepth, int maxMaskDepth) {
		this.canvasWidth = canvasWidth;
		this.canvasHeight = canvasHeight;
		this.topLeftOrigin = topLeftOrigin;
		this.ticksPerSecond = ticksPerSecond;
		this.duration = duration;
		this.textures = textures;
		this.meshes = meshes;
		this.clips = clips;
		this.rootClip = rootClip;
		this.maxCommands = maxCommands;
		this.maxDepth = maxDepth;
		this.maxMaskDepth = maxMaskDepth;
	}

	public static final class TextureResource {
		public final String id;
		public final String path;
		public final boolean linear;
		public final boolean repeatX;
		public final boolean repeatY;
		public final boolean premultipliedAlpha;

		TextureResource(String id, String path, boolean linear, boolean repeatX,
				boolean repeatY, boolean premultipliedAlpha) {
			this.id = id;
			this.path = path;
			this.linear = linear;
			this.repeatX = repeatX;
			this.repeatY = repeatY;
			this.premultipliedAlpha = premultipliedAlpha;
		}
	}

	public static final class MeshResource {
		public final String id;
		public final float[] positions;
		public final float[] uv;
		public final short[] indices;

		MeshResource(String id, float[] positions, float[] uv, short[] indices) {
			this.id = id;
			this.positions = positions;
			this.uv = uv;
			this.indices = indices;
		}
	}

	public static final class Clip {
		public final String id;
		public final long duration;
		public final float framesPerSecond;
		public final Instance[] instances;

		Clip(String id, long duration, float framesPerSecond, Instance[] instances) {
			this.id = id;
			this.duration = duration;
			this.framesPerSecond = framesPerSecond;
			this.instances = instances;
		}
	}

	public static final class Instance {
		public final String id;
		public final int depth;
		public final int documentOrder;
		public final int kind;
		public final int reference;
		public final int mesh;
		public final long start;
		public final long end;
		public final BlendMode blend;
		public final float[] uvRect;
		public final TransformTrack transform;
		public final ColorTrack color;
		public final CameraTrack camera;
		public final RectTrack clipRect;
		public final long startOffset;
		public final double playbackRate;
		public final boolean repeat;
		public final FrameTrack frameTrack;

		Instance(String id, int depth, int documentOrder, int kind, int reference,
				int mesh, long start, long end, BlendMode blend, float[] uvRect,
				TransformTrack transform, ColorTrack color, CameraTrack camera,
				RectTrack clipRect, long startOffset, double playbackRate,
				boolean repeat, FrameTrack frameTrack) {
			this.id = id;
			this.depth = depth;
			this.documentOrder = documentOrder;
			this.kind = kind;
			this.reference = reference;
			this.mesh = mesh;
			this.start = start;
			this.end = end;
			this.blend = blend;
			this.uvRect = uvRect;
			this.transform = transform;
			this.color = color;
			this.camera = camera;
			this.clipRect = clipRect;
			this.startOffset = startOffset;
			this.playbackRate = playbackRate;
			this.repeat = repeat;
			this.frameTrack = frameTrack;
		}
	}

	public static final class TransformTrack {
		public final long[] times;
		public final int interpolation;
		public final boolean trs;
		public final float[][] matrices;
		public final float[][] translations;
		public final float[][] rotations;
		public final float[][] scales;
		public final float[][] pivots;

		TransformTrack(long[] times, int interpolation, boolean trs,
				float[][] matrices, float[][] translations, float[][] rotations,
				float[][] scales, float[][] pivots) {
			this.times = times;
			this.interpolation = interpolation;
			this.trs = trs;
			this.matrices = matrices;
			this.translations = translations;
			this.rotations = rotations;
			this.scales = scales;
			this.pivots = pivots;
		}
	}

	public static final class ColorTrack {
		public final long[] times;
		public final int interpolation;
		public final float[][] mul;
		public final float[][] add;
		public final float[][] hsl;

		ColorTrack(long[] times, int interpolation, float[][] mul, float[][] add,
				float[][] hsl) {
			this.times = times;
			this.interpolation = interpolation;
			this.mul = mul;
			this.add = add;
			this.hsl = hsl;
		}
	}

	public static final class CameraTrack {
		public final int projection;
		public final long[] times;
		public final int interpolation;
		public final float[][] centers;
		public final float[] focalLengths;

		CameraTrack(int projection, long[] times, int interpolation,
				float[][] centers, float[] focalLengths) {
			this.projection = projection;
			this.times = times;
			this.interpolation = interpolation;
			this.centers = centers;
			this.focalLengths = focalLengths;
		}
	}

	public static final class RectTrack {
		public final long[] times;
		public final int interpolation;
		public final float[][] rects;

		RectTrack(long[] times, int interpolation, float[][] rects) {
			this.times = times;
			this.interpolation = interpolation;
			this.rects = rects;
		}
	}

	public static final class FrameTrack {
		public final long[] times;
		public final long[] ends;
		public final int[] frames;

		FrameTrack(long[] times, long[] ends, int[] frames) {
			this.times = times;
			this.ends = ends;
			this.frames = frames;
		}
	}
}
