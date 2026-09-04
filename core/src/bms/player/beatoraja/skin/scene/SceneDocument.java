package bms.player.beatoraja.skin.scene;

/**
 * JSON parse model for the neutral skin scene format.
 *
 * <p>The runtime never traverses this model. {@link SceneCompiler} validates
 * and compiles it into array/index based data before the first frame.</p>
 */
public final class SceneDocument {
	public int formatVersion;
	public Canvas canvas = new Canvas();
	public Timebase timebase = new Timebase();
	public long duration;
	public TextureDefinition[] textures = new TextureDefinition[0];
	public MeshDefinition[] meshes = new MeshDefinition[0];
	public ClipDefinition[] clips = new ClipDefinition[0];
	public String rootClip;

	public static final class Canvas {
		public float width;
		public float height;
		public String origin = "bottom-left";
	}

	public static final class Timebase {
		public long ticksPerSecond = 60000;
	}

	public static final class TextureDefinition {
		public String id;
		public String path;
		public String filter = "linear";
		public String wrapX = "clamp";
		public String wrapY = "clamp";
		public boolean premultipliedAlpha;
	}

	public static final class MeshDefinition {
		public String id;
		public float[] positions = new float[0];
		public float[] uv = new float[0];
		public int[] indices = new int[0];
	}

	public static final class ClipDefinition {
		public String id;
		public long duration;
		public float framesPerSecond = 60.0f;
		public InstanceDefinition[] instances = new InstanceDefinition[0];
	}

	public static final class InstanceDefinition {
		public String id;
		public int depth;
		public String kind = "texture";
		public String ref;
		public String mesh;
		public long start;
		public long end = Long.MIN_VALUE;
		public String blend = "normal";
		public float[] uvRect = new float[] {0, 0, 1, 1};
		public TransformTrackDefinition transformTrack;
		public ColorTrackDefinition colorTrack;
		public CameraTrackDefinition cameraTrack;
		public RectTrackDefinition clipRectTrack;
		public long startOffset;
		public double playbackRate = 1.0;
		public String loop = "none";
		public FrameKeyDefinition[] frameTrack = new FrameKeyDefinition[0];
	}

	public static final class TransformTrackDefinition {
		public String kind = "affine2";
		public String interpolation = "linear";
		public TransformKeyDefinition[] keys = new TransformKeyDefinition[0];
	}

	public static final class TransformKeyDefinition {
		public long t;
		/** affine2 (6 values) or row-major affine3 (12 values). */
		public float[] value;
		public float[] translation;
		/** Quaternion x, y, z, w. */
		public float[] rotation;
		public float[] scale;
		public float[] pivot;
	}

	public static final class ColorTrackDefinition {
		public String interpolation = "linear";
		public ColorKeyDefinition[] keys = new ColorKeyDefinition[0];
	}

	public static final class ColorKeyDefinition {
		public long t;
		public float[] mul;
		public float[] add;
		public float[] hsl;
	}

	public static final class CameraTrackDefinition {
		public String projection = "orthographic";
		public String interpolation = "linear";
		public CameraKeyDefinition[] keys = new CameraKeyDefinition[0];
	}

	public static final class CameraKeyDefinition {
		public long t;
		public float[] center;
		public float focalLength = Float.NaN;
	}

	public static final class RectTrackDefinition {
		public String interpolation = "linear";
		public RectKeyDefinition[] keys = new RectKeyDefinition[0];
	}

	public static final class RectKeyDefinition {
		public long t;
		public float[] rect;
	}

	public static final class FrameKeyDefinition {
		public long t;
		/** Optional exclusive end; omitted means the next key or instance end. */
		public long end = Long.MIN_VALUE;
		public int frame;
	}
}
