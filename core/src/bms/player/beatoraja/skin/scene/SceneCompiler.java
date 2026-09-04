package bms.player.beatoraja.skin.scene;

import bms.player.beatoraja.skin.scene.CompiledScene.CameraTrack;
import bms.player.beatoraja.skin.scene.CompiledScene.Clip;
import bms.player.beatoraja.skin.scene.CompiledScene.ColorTrack;
import bms.player.beatoraja.skin.scene.CompiledScene.FrameTrack;
import bms.player.beatoraja.skin.scene.CompiledScene.Instance;
import bms.player.beatoraja.skin.scene.CompiledScene.MeshResource;
import bms.player.beatoraja.skin.scene.CompiledScene.RectTrack;
import bms.player.beatoraja.skin.scene.CompiledScene.TextureResource;
import bms.player.beatoraja.skin.scene.CompiledScene.TransformTrack;
import bms.player.beatoraja.skin.scene.SceneDocument.CameraKeyDefinition;
import bms.player.beatoraja.skin.scene.SceneDocument.ColorKeyDefinition;
import bms.player.beatoraja.skin.scene.SceneDocument.FrameKeyDefinition;
import bms.player.beatoraja.skin.scene.SceneDocument.InstanceDefinition;
import bms.player.beatoraja.skin.scene.SceneDocument.RectKeyDefinition;
import bms.player.beatoraja.skin.scene.SceneDocument.TransformKeyDefinition;
import bms.player.beatoraja.skin.scene.render.BlendMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Validates and compiles the neutral scene parse model. */
public final class SceneCompiler {
	private static final Logger logger = LoggerFactory.getLogger(SceneCompiler.class);
	public static final int MAX_TEXTURES = 512;
	public static final int MAX_MESHES = 2048;
	public static final int MAX_CLIPS = 1024;
	public static final int MAX_INSTANCES = 16384;
	public static final int MAX_KEYS = 1_000_000;
	public static final int MAX_VERTICES = 1_000_000;
	public static final int MAX_MESH_VERTICES = 65532;
	public static final int MAX_MESH_INDICES = 196596;
	public static final int MAX_RECURSION = 64;
	public static final int MAX_MASK_DEPTH = 8;

	public CompiledScene compile(SceneDocument document, Path sceneFile)
			throws SceneValidationException {
		if (document == null) {
			throw failure("document", "is missing");
		}
		if (document.formatVersion != 1) {
			throw failure("formatVersion", "unsupported version " + document.formatVersion);
		}
		if (document.canvas == null || !positiveFinite(document.canvas.width)
				|| !positiveFinite(document.canvas.height)) {
			throw failure("canvas", "width and height must be finite and positive");
		}
		String origin = document.canvas.origin == null ? "bottom-left" : document.canvas.origin;
		if (!origin.equals("bottom-left") && !origin.equals("top-left")) {
			throw failure("canvas.origin", "must be bottom-left or top-left");
		}
		if (document.timebase == null || document.timebase.ticksPerSecond <= 0) {
			throw failure("timebase.ticksPerSecond", "must be positive");
		}
		if (document.duration <= 0) {
			throw failure("duration", "must be positive");
		}

		SceneDocument.TextureDefinition[] textureDefinitions = nonNull(document.textures,
				SceneDocument.TextureDefinition[]::new);
		SceneDocument.MeshDefinition[] meshDefinitions = nonNull(document.meshes,
				SceneDocument.MeshDefinition[]::new);
		SceneDocument.ClipDefinition[] clipDefinitions = nonNull(document.clips,
				SceneDocument.ClipDefinition[]::new);
		if (textureDefinitions.length > MAX_TEXTURES || meshDefinitions.length > MAX_MESHES
				|| clipDefinitions.length > MAX_CLIPS) {
			throw failure("resources", "resource count exceeds the scene limit");
		}

		Path root = sceneFile.toAbsolutePath().normalize().getParent();
		Map<String, Integer> textureIds = new HashMap<>();
		TextureResource[] textures = new TextureResource[textureDefinitions.length];
		for (int i = 0; i < textureDefinitions.length; i++) {
			var source = textureDefinitions[i];
			String field = "textures[" + i + "]";
			requireId(source == null ? null : source.id, textureIds, i, field);
			if (source.path == null || source.path.isBlank()) {
				throw failure(field + ".path", "is required");
			}
			Path texturePath = root.resolve(source.path).normalize();
			if (!texturePath.startsWith(root)) {
				throw failure(field + ".path", "escapes the scene directory");
			}
			boolean linear = parseFilter(source.filter, field + ".filter");
			boolean repeatX = parseWrap(source.wrapX, field + ".wrapX");
			boolean repeatY = parseWrap(source.wrapY, field + ".wrapY");
			textures[i] = new TextureResource(source.id, texturePath.toString(), linear,
					repeatX, repeatY, source.premultipliedAlpha);
		}

		Map<String, Integer> meshIds = new HashMap<>();
		MeshResource[] meshes = new MeshResource[meshDefinitions.length];
		long vertexTotal = 0;
		for (int i = 0; i < meshDefinitions.length; i++) {
			var source = meshDefinitions[i];
			String field = "meshes[" + i + "]";
			requireId(source == null ? null : source.id, meshIds, i, field);
			float[] positions = source.positions == null ? new float[0] : source.positions.clone();
			float[] uv = source.uv == null ? new float[0] : source.uv.clone();
			int[] sourceIndices = source.indices == null ? new int[0] : source.indices.clone();
			if (positions.length == 0 || positions.length % 3 != 0) {
				throw failure(field + ".positions", "must contain xyz triples");
			}
			int vertices = positions.length / 3;
			vertexTotal += vertices;
			if (vertices > MAX_MESH_VERTICES || vertexTotal > MAX_VERTICES
					|| uv.length != vertices * 2) {
				throw failure(field, "vertex limit exceeded or UV count does not match");
			}
			if (sourceIndices.length == 0 || sourceIndices.length % 3 != 0
					|| sourceIndices.length > MAX_MESH_INDICES) {
				throw failure(field + ".indices", "must contain triangles");
			}
			for (int j = 0; j < positions.length; j++) {
				requireFinite(positions[j], field + ".positions[" + j + "]");
			}
			for (int j = 0; j < uv.length; j++) {
				requireFinite(uv[j], field + ".uv[" + j + "]");
			}
			short[] indices = new short[sourceIndices.length];
			for (int j = 0; j < sourceIndices.length; j++) {
				int index = sourceIndices[j];
				if (index < 0 || index >= vertices) {
					throw failure(field + ".indices[" + j + "]", "is outside the vertex range");
				}
				indices[j] = (short) index;
			}
			meshes[i] = new MeshResource(source.id, positions, uv, indices);
		}

		Map<String, Integer> clipIds = new HashMap<>();
		for (int i = 0; i < clipDefinitions.length; i++) {
			requireId(clipDefinitions[i] == null ? null : clipDefinitions[i].id,
					clipIds, i, "clips[" + i + "]");
		}
		Integer rootClip = clipIds.get(document.rootClip);
		if (rootClip == null) {
			throw failure("rootClip", "does not reference a clip");
		}

		Clip[] clips = new Clip[clipDefinitions.length];
		Set<String> instanceIds = new HashSet<>();
		int instanceTotal = 0;
		long keyTotal = 0;
		for (int clipIndex = 0; clipIndex < clipDefinitions.length; clipIndex++) {
			var sourceClip = clipDefinitions[clipIndex];
			if (sourceClip.duration < 0) {
				throw failure("clips[" + clipIndex + "].duration", "cannot be negative");
			}
			long clipDuration = sourceClip.duration > 0 ? sourceClip.duration : document.duration;
			if (clipDuration <= 0 || !Float.isFinite(sourceClip.framesPerSecond)
					|| sourceClip.framesPerSecond <= 0) {
				throw failure("clips[" + clipIndex + "]", "has invalid duration or frame rate");
			}
			InstanceDefinition[] definitions = nonNull(sourceClip.instances,
					InstanceDefinition[]::new);
			instanceTotal += definitions.length;
			if (instanceTotal > MAX_INSTANCES) {
				throw failure("clips", "instance count exceeds " + MAX_INSTANCES);
			}
			Instance[] instances = new Instance[definitions.length];
			for (int i = 0; i < definitions.length; i++) {
				instances[i] = compileInstance(definitions[i], i, clipIndex, clipDuration,
						textureIds, meshIds, clipIds, instanceIds);
				keyTotal += keyCount(instances[i]);
				if (keyTotal > MAX_KEYS) {
					throw failure("clips", "key count exceeds " + MAX_KEYS);
				}
			}
			Arrays.sort(instances, Comparator.comparingInt((Instance value) -> value.depth)
					.thenComparingInt(value -> value.documentOrder));
			clips[clipIndex] = new Clip(sourceClip.id, clipDuration,
					sourceClip.framesPerSecond, instances);
		}

		int[] state = new int[clips.length];
		for (int i = 0; i < clips.length; i++) {
			if (state[i] == 0) validateAcyclic(i, clips, state, 0);
		}
		int maxCommands = countLeafCommands(rootClip, clips, 0);
		int maxDepth = maximumDepth(rootClip, clips, 1);
		int maxMasks = maximumMaskDepth(rootClip, clips, 0, 0);
		if (maxCommands <= 0) {
			throw failure("rootClip", "does not contain a drawable leaf");
		}
		if (maxCommands > MAX_INSTANCES || maxDepth > MAX_RECURSION
				|| maxMasks > MAX_MASK_DEPTH) {
			throw failure("rootClip", "expanded scene exceeds a runtime limit");
		}
		return new CompiledScene(document.canvas.width, document.canvas.height,
				origin.equals("top-left"), document.timebase.ticksPerSecond,
				document.duration, textures, meshes, clips, rootClip, maxCommands,
				maxDepth, maxMasks);
	}

	private Instance compileInstance(InstanceDefinition source, int documentOrder,
			int clipIndex, long clipDuration,
			Map<String, Integer> textureIds, Map<String, Integer> meshIds,
			Map<String, Integer> clipIds, Set<String> instanceIds)
			throws SceneValidationException {
		String field = "clips[" + clipIndex + "].instances[" + documentOrder + "]";
		if (source == null || source.id == null || source.id.isBlank()
				|| !instanceIds.add(source.id)) {
			throw failure(field + ".id", "must be globally unique and non-empty");
		}
		int kind;
		int reference;
		if ("clip".equals(source.kind)) {
			kind = CompiledScene.KIND_CLIP;
			reference = requiredReference(source.ref, clipIds, field + ".ref");
		} else if ("texture".equals(source.kind) || "mesh".equals(source.kind)) {
			kind = "mesh".equals(source.kind) ? CompiledScene.KIND_MESH
					: CompiledScene.KIND_TEXTURE;
			reference = requiredReference(source.ref, textureIds, field + ".ref");
		} else {
			throw failure(field + ".kind", "must be texture, mesh, or clip");
		}
		int mesh = source.mesh == null ? -1 : requiredReference(source.mesh, meshIds,
				field + ".mesh");
		if (kind == CompiledScene.KIND_MESH && mesh < 0) {
			throw failure(field + ".mesh", "is required for a mesh instance");
		}
		long end = source.end == Long.MIN_VALUE ? clipDuration : source.end;
		if (source.start < 0 || end <= source.start || end > clipDuration) {
			throw failure(field, "active interval must satisfy 0 <= start < end <= clip duration");
		}
		if (!Double.isFinite(source.playbackRate) || source.playbackRate <= 0) {
			throw failure(field + ".playbackRate", "must be finite and positive");
		}
		boolean repeat;
		if (source.loop == null || source.loop.equals("none")) {
			repeat = false;
		} else if (source.loop.equals("repeat")) {
			repeat = true;
		} else {
			throw failure(field + ".loop", "must be none or repeat");
		}
		float[] uvRect = requireVector(source.uvRect, 4, field + ".uvRect",
				new float[] {0, 0, 1, 1});
		BlendMode blend;
		try {
			blend = BlendMode.parse(source.blend);
		} catch (IllegalArgumentException error) {
			throw failure(field + ".blend", error.getMessage());
		}
		TransformTrack transform = compileTransform(source.transformTrack,
				field + ".transformTrack");
		ColorTrack color = compileColor(source.colorTrack, field + ".colorTrack");
		CameraTrack camera = compileCamera(source.cameraTrack, field + ".cameraTrack");
		RectTrack clipRect = compileRect(source.clipRectTrack, field + ".clipRectTrack");
		validateTimes(transform.times, clipDuration, field + ".transformTrack");
		validateTimes(color.times, clipDuration, field + ".colorTrack");
		if (camera != null) validateTimes(camera.times, clipDuration, field + ".cameraTrack");
		if (clipRect != null) validateTimes(clipRect.times, clipDuration, field + ".clipRectTrack");
		FrameTrack frames = compileFrames(source.frameTrack, source.start, end,
				field + ".frameTrack");
		return new Instance(source.id, source.depth, documentOrder, kind, reference,
				mesh, source.start, end, blend, uvRect,
				transform, color, camera, clipRect,
				source.startOffset, source.playbackRate, repeat,
				frames);
	}

	private TransformTrack compileTransform(SceneDocument.TransformTrackDefinition source,
			String field) throws SceneValidationException {
		if (source == null || source.keys == null || source.keys.length == 0) {
			return new TransformTrack(new long[] {0}, CompiledScene.INTERPOLATION_STEP,
					false, new float[][] {identity()}, null, null, null, null);
		}
		TransformKeyDefinition[] keys = sortedUnique(source.keys, key -> key.t, field);
		boolean trs = "trs".equals(source.kind);
		if (!trs && !"affine2".equals(source.kind) && !"affine3".equals(source.kind)) {
			throw failure(field + ".kind", "must be affine2, affine3, or trs");
		}
		int interpolation = parseInterpolation(source.interpolation, trs, field);
		long[] times = new long[keys.length];
		float[][] matrices = trs ? null : new float[keys.length][];
		float[][] translations = trs ? new float[keys.length][] : null;
		float[][] rotations = trs ? new float[keys.length][] : null;
		float[][] scales = trs ? new float[keys.length][] : null;
		float[][] pivots = trs ? new float[keys.length][] : null;
		float[] previousValue = null;
		float[] previousTranslation = new float[] {0, 0, 0};
		float[] previousRotation = new float[] {0, 0, 0, 1};
		float[] previousScale = new float[] {1, 1, 1};
		float[] previousPivot = new float[] {0, 0, 0};
		for (int i = 0; i < keys.length; i++) {
			var key = keys[i];
			times[i] = key.t;
			if (trs) {
				translations[i] = requireVector(key.translation, 3, field + ".keys.translation",
						previousTranslation);
				rotations[i] = requireVector(key.rotation, 4, field + ".keys.rotation",
						previousRotation);
				normalizeQuaternion(rotations[i], field + ".keys.rotation");
				scales[i] = requireVector(key.scale, 3, field + ".keys.scale",
						previousScale);
				pivots[i] = requireVector(key.pivot, 3, field + ".keys.pivot",
						previousPivot);
				previousTranslation = translations[i];
				previousRotation = rotations[i];
				previousScale = scales[i];
				previousPivot = pivots[i];
			} else {
				int length = "affine2".equals(source.kind) ? 6 : 12;
				if (previousValue == null) {
					previousValue = length == 6 ? new float[] {1, 0, 0, 1, 0, 0}
							: new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
				}
				float[] value = requireVector(key.value, length, field + ".keys.value", previousValue);
				float[] pivot = requireVector(key.pivot, 3, field + ".keys.pivot",
						previousPivot);
				matrices[i] = withPivot(toMatrix(value, length), pivot);
				previousValue = value;
				previousPivot = pivot;
			}
		}
		return new TransformTrack(times, interpolation, trs, matrices, translations,
				rotations, scales, pivots);
	}

	private ColorTrack compileColor(SceneDocument.ColorTrackDefinition source, String field)
			throws SceneValidationException {
		if (source == null || source.keys == null || source.keys.length == 0) {
			return new ColorTrack(new long[] {0}, CompiledScene.INTERPOLATION_STEP,
					new float[][] {{1, 1, 1, 1}}, new float[][] {{0, 0, 0, 0}},
					new float[][] {{0, 0, 0}});
		}
		ColorKeyDefinition[] keys = sortedUnique(source.keys, key -> key.t, field);
		int interpolation = parseInterpolation(source.interpolation, false, field);
		long[] times = new long[keys.length];
		float[][] mul = new float[keys.length][];
		float[][] add = new float[keys.length][];
		float[][] hsl = new float[keys.length][];
		float[] previousMul = new float[] {1, 1, 1, 1};
		float[] previousAdd = new float[] {0, 0, 0, 0};
		float[] previousHsl = new float[] {0, 0, 0};
		for (int i = 0; i < keys.length; i++) {
			times[i] = keys[i].t;
			mul[i] = requireVector(keys[i].mul, 4, field + ".keys.mul",
					previousMul);
			add[i] = requireVector(keys[i].add, 4, field + ".keys.add",
					previousAdd);
			hsl[i] = requireVector(keys[i].hsl, 3, field + ".keys.hsl",
					previousHsl);
			previousMul = mul[i]; previousAdd = add[i]; previousHsl = hsl[i];
		}
		return new ColorTrack(times, interpolation, mul, add, hsl);
	}

	private CameraTrack compileCamera(SceneDocument.CameraTrackDefinition source, String field)
			throws SceneValidationException {
		if (source == null || source.keys == null || source.keys.length == 0) {
			return null;
		}
		int projection;
		if ("orthographic".equals(source.projection)) {
			projection = CompiledScene.PROJECTION_ORTHOGRAPHIC;
		} else if ("focal".equals(source.projection)) {
			projection = CompiledScene.PROJECTION_FOCAL;
		} else {
			throw failure(field + ".projection", "must be orthographic or focal");
		}
		CameraKeyDefinition[] keys = sortedUnique(source.keys, key -> key.t, field);
		int interpolation = parseInterpolation(source.interpolation, false, field);
		long[] times = new long[keys.length];
		float[][] centers = new float[keys.length][];
		float[] focal = new float[keys.length];
		float[] previousCenter = new float[] {0, 0, -1};
		float previousFocal = 1;
		for (int i = 0; i < keys.length; i++) {
			times[i] = keys[i].t;
			centers[i] = requireVector(keys[i].center, 3, field + ".keys.center", previousCenter);
			float focalValue = Float.isNaN(keys[i].focalLength) ? previousFocal : keys[i].focalLength;
			requireFinite(focalValue, field + ".keys.focalLength");
			if (projection == CompiledScene.PROJECTION_FOCAL && focalValue <= 0) {
				throw failure(field + ".keys.focalLength", "must be positive for focal projection");
			}
			focal[i] = focalValue;
			previousCenter = centers[i]; previousFocal = focalValue;
		}
		return new CameraTrack(projection, times, interpolation, centers, focal);
	}

	private RectTrack compileRect(SceneDocument.RectTrackDefinition source, String field)
			throws SceneValidationException {
		if (source == null || source.keys == null || source.keys.length == 0) {
			return null;
		}
		RectKeyDefinition[] keys = sortedUnique(source.keys, key -> key.t, field);
		int interpolation = parseInterpolation(source.interpolation, false, field);
		long[] times = new long[keys.length];
		float[][] rects = new float[keys.length][];
		float[] previousRect = null;
		for (int i = 0; i < keys.length; i++) {
			times[i] = keys[i].t;
			rects[i] = requireVector(keys[i].rect, 4, field + ".keys.rect", previousRect);
			if (rects[i][2] < 0 || rects[i][3] < 0) {
				throw failure(field + ".keys.rect", "width and height cannot be negative");
			}
			previousRect = rects[i];
		}
		return new RectTrack(times, interpolation, rects);
	}

	private FrameTrack compileFrames(FrameKeyDefinition[] source, long instanceStart,
			long instanceEnd, String field)
			throws SceneValidationException {
		if (source == null || source.length == 0) {
			return null;
		}
		FrameKeyDefinition[] keys = sortedUnique(source, key -> key.t, field);
		long[] times = new long[keys.length];
		long[] ends = new long[keys.length];
		int[] frames = new int[keys.length];
		for (int i = 0; i < keys.length; i++) {
			times[i] = keys[i].t;
			ends[i] = keys[i].end != Long.MIN_VALUE ? keys[i].end
					: (i + 1 < keys.length ? keys[i + 1].t : instanceEnd);
			frames[i] = keys[i].frame;
			if (times[i] < instanceStart || ends[i] <= times[i]
					|| ends[i] > instanceEnd || frames[i] < 0) {
				throw failure(field, "contains an invalid active interval or frame");
			}
		}
		return new FrameTrack(times, ends, frames);
	}

	private static int parseInterpolation(String value, boolean trs, String field)
			throws SceneValidationException {
		String resolved = value == null ? "linear" : value;
		return switch (resolved) {
			case "step" -> CompiledScene.INTERPOLATION_STEP;
			case "linear" -> CompiledScene.INTERPOLATION_LINEAR;
			case "trs" -> {
				if (!trs) {
					throw failure(field + ".interpolation", "trs is valid only for a trs track");
				}
				yield CompiledScene.INTERPOLATION_TRS;
			}
			default -> throw failure(field + ".interpolation", "is unknown: " + resolved);
		};
	}

	private static boolean parseFilter(String value, String field) throws SceneValidationException {
		if (value == null || value.equals("linear")) {
			return true;
		}
		if (value.equals("nearest")) {
			return false;
		}
		throw failure(field, "must be nearest or linear");
	}

	private static boolean parseWrap(String value, String field) throws SceneValidationException {
		if (value == null || value.equals("clamp")) {
			return false;
		}
		if (value.equals("repeat")) {
			return true;
		}
		throw failure(field, "must be clamp or repeat");
	}

	private static <T> void requireId(String id, Map<String, Integer> ids, int index,
			String field) throws SceneValidationException {
		if (id == null || id.isBlank() || ids.putIfAbsent(id, index) != null) {
			throw failure(field + ".id", "must be unique and non-empty");
		}
	}

	private static int requiredReference(String id, Map<String, Integer> ids, String field)
			throws SceneValidationException {
		Integer value = ids.get(id);
		if (value == null) {
			throw failure(field, "references an unknown id: " + id);
		}
		return value;
	}

	private static float[] requireVector(float[] value, int length, String field,
			float[] defaultValue) throws SceneValidationException {
		float[] result = value;
		if (result == null) {
			if (defaultValue == null) {
				throw failure(field, "is required");
			}
			result = defaultValue;
		}
		if (result.length != length) {
			throw failure(field, "must contain " + length + " values");
		}
		result = result.clone();
		for (int i = 0; i < result.length; i++) {
			requireFinite(result[i], field + "[" + i + "]");
		}
		return result;
	}

	private static void requireFinite(float value, String field) throws SceneValidationException {
		if (!Float.isFinite(value)) {
			throw failure(field, "must be finite");
		}
	}

	private static boolean positiveFinite(float value) {
		return Float.isFinite(value) && value > 0;
	}

	private static void normalizeQuaternion(float[] value, String field)
			throws SceneValidationException {
		float length = (float) Math.sqrt(value[0] * value[0] + value[1] * value[1]
				+ value[2] * value[2] + value[3] * value[3]);
		if (length < 0.000001f) {
			throw failure(field, "cannot be a zero quaternion");
		}
		for (int i = 0; i < 4; i++) {
			value[i] /= length;
		}
	}

	private static float[] toMatrix(float[] value, int length) {
		float[] result = identity();
		if (length == 6) {
			result[0] = value[0];
			result[1] = value[1];
			result[4] = value[2];
			result[5] = value[3];
			result[12] = value[4];
			result[13] = value[5];
		} else {
			result[0] = value[0]; result[4] = value[1]; result[8] = value[2]; result[12] = value[3];
			result[1] = value[4]; result[5] = value[5]; result[9] = value[6]; result[13] = value[7];
			result[2] = value[8]; result[6] = value[9]; result[10] = value[10]; result[14] = value[11];
		}
		return result;
	}

	private static float[] withPivot(float[] matrix, float[] pivot) {
		if (pivot[0] == 0 && pivot[1] == 0 && pivot[2] == 0) {
			return matrix;
		}
		float[] left = identity();
		left[12] = pivot[0]; left[13] = pivot[1]; left[14] = pivot[2];
		float[] right = identity();
		right[12] = -pivot[0]; right[13] = -pivot[1]; right[14] = -pivot[2];
		return multiply(multiply(left, matrix), right);
	}

	static float[] identity() {
		float[] result = new float[16];
		result[0] = result[5] = result[10] = result[15] = 1;
		return result;
	}

	static float[] multiply(float[] left, float[] right) {
		float[] result = new float[16];
		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				result[column * 4 + row] = left[row] * right[column * 4]
						+ left[4 + row] * right[column * 4 + 1]
						+ left[8 + row] * right[column * 4 + 2]
						+ left[12 + row] * right[column * 4 + 3];
			}
		}
		return result;
	}

	private static void validateAcyclic(int clip, Clip[] clips, int[] state, int depth)
			throws SceneValidationException {
		if (depth > MAX_RECURSION || state[clip] == 1) {
			throw failure("clips", "contains a reference cycle or excessive nesting");
		}
		if (state[clip] == 2) {
			return;
		}
		state[clip] = 1;
		for (Instance instance : clips[clip].instances) {
			if (instance.kind == CompiledScene.KIND_CLIP) {
				validateAcyclic(instance.reference, clips, state, depth + 1);
			}
		}
		state[clip] = 2;
	}

	private static int countLeafCommands(int clip, Clip[] clips, int depth)
			throws SceneValidationException {
		if (depth > MAX_RECURSION) {
			throw failure("clips", "is nested too deeply");
		}
		long result = 0;
		for (Instance instance : clips[clip].instances) {
			result += instance.kind == CompiledScene.KIND_CLIP
					? countLeafCommands(instance.reference, clips, depth + 1) : 1;
			if (result > MAX_INSTANCES) {
				throw failure("clips", "expands to too many draw commands");
			}
		}
		return (int) result;
	}

	private static int maximumDepth(int clip, Clip[] clips, int depth) {
		int result = depth;
		for (Instance instance : clips[clip].instances) {
			if (instance.kind == CompiledScene.KIND_CLIP) {
				result = Math.max(result, maximumDepth(instance.reference, clips, depth + 1));
			}
		}
		return result;
	}

	private static int maximumMaskDepth(int clip, Clip[] clips, int masks, int depth) {
		int result = masks;
		for (Instance instance : clips[clip].instances) {
			int next = masks + (instance.clipRect == null ? 0 : 1);
			result = Math.max(result, next);
			if (instance.kind == CompiledScene.KIND_CLIP && depth < MAX_RECURSION) {
				result = Math.max(result, maximumMaskDepth(instance.reference, clips, next, depth + 1));
			}
		}
		return result;
	}

	private static int keyCount(Instance instance) {
		return instance.transform.times.length + instance.color.times.length
				+ (instance.camera == null ? 0 : instance.camera.times.length)
				+ (instance.clipRect == null ? 0 : instance.clipRect.times.length)
				+ (instance.frameTrack == null ? 0 : instance.frameTrack.times.length);
	}

	private static void validateTimes(long[] times, long duration, String field)
			throws SceneValidationException {
		for (long time : times) {
			if (time > duration) {
				throw failure(field, "contains a key beyond the clip duration");
			}
		}
	}

	private interface TimeReader<T> {
		long read(T value);
	}

	private static <T> T[] sortedUnique(T[] source, TimeReader<T> reader, String field)
			throws SceneValidationException {
		if (source.length > MAX_KEYS) {
			throw failure(field, "contains too many keys");
		}
		T[] sorted = source.clone();
		Arrays.sort(sorted, Comparator.comparingLong(reader::read));
		int count = 0;
		for (T value : sorted) {
			if (value == null || reader.read(value) < 0) {
				throw failure(field, "contains a null key or negative time");
			}
			if (count > 0 && reader.read(sorted[count - 1]) == reader.read(value)) {
				logger.warn("{} contains duplicate key time {}; the last key wins",
						field, reader.read(value));
				sorted[count - 1] = value;
			} else {
				sorted[count++] = value;
			}
		}
		return Arrays.copyOf(sorted, count);
	}

	private interface ArrayFactory<T> {
		T create(int length);
	}

	private static <T> T nonNull(T value, ArrayFactory<T> factory) {
		return value == null ? factory.create(0) : value;
	}

	private static SceneValidationException failure(String field, String message) {
		return new SceneValidationException(field + ": " + message);
	}
}
