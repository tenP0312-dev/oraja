package bms.player.beatoraja.skin.scene.render;

import bms.player.beatoraja.skin.scene.CompiledScene;
import bms.player.beatoraja.skin.scene.RenderCommand;
import bms.player.beatoraja.skin.scene.SceneProjection;
import bms.player.beatoraja.skin.scene.SkinSceneObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

/** Shared dynamic mesh batch that preserves command order. */
final class ProjectiveBatch {
	private static final int MAX_VERTICES = 65532;
	private static final int MAX_INDICES = 196596;
	private static final int FLOATS_PER_VERTEX = 16;
	private static final float[] UNIT_POSITIONS = {
			0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0
	};
	private static final float[] TOP_LEFT_UNIT_UV = {0, 0, 1, 0, 1, 1, 0, 1};
	private static final float[] BOTTOM_LEFT_UNIT_UV = {0, 1, 1, 1, 1, 0, 0, 0};
	private static final short[] UNIT_INDICES = {0, 1, 2, 2, 3, 0};
	private static final float[] WHITE = {1, 1, 1, 1};
	private static final float[] CLEAR = {0, 0, 0, 0};
	private static final float[] NO_HSL = {0, 0, 0};
	private static final float[] FULL_UV = {0, 0, 1, 1};

	private final Mesh mesh;
	private final SceneShader sceneShader;
	private final float[] vertices = new float[MAX_VERTICES * FLOATS_PER_VERTEX];
	private final short[] indices = new short[MAX_INDICES];
	private final Matrix4 combinedTransform = new Matrix4();
	private int vertexCount;
	private int indexCount;
	private Texture texture;
	private BlendMode blend;
	private boolean premultiplied;
	private int projection;
	private final float[] camera = new float[4];
	private final float[] maskPositions = new float[12];
	private boolean stateSet;
	private int drawCalls;
	private int transformedVertices;

	ProjectiveBatch() {
		Mesh createdMesh = new Mesh(false, MAX_VERTICES, MAX_INDICES,
				new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
				new VertexAttribute(Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
				new VertexAttribute(Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
				new VertexAttribute(Usage.Generic, 4, "a_colorAdd"),
				new VertexAttribute(Usage.Generic, 3, "a_hsl"));
		try {
			sceneShader = new SceneShader();
		} catch (Throwable error) {
			createdMesh.dispose();
			throw error;
		}
		mesh = createdMesh;
	}

	void setCombinedTransform(Matrix4 transform) {
		flush();
		combinedTransform.set(transform);
	}

	void beginMetrics() {
		drawCalls = 0;
		transformedVertices = 0;
	}

	void draw(SkinSceneObject object, RenderCommand command) {
		CompiledScene scene = object.getScene();
		float[] positions = command.meshIndex < 0 ? UNIT_POSITIONS
				: scene.meshes[command.meshIndex].positions;
		float[] uv = command.meshIndex < 0 ? defaultQuadUv(scene.topLeftOrigin)
				: scene.meshes[command.meshIndex].uv;
		short[] sourceIndices = command.meshIndex < 0 ? UNIT_INDICES
				: scene.meshes[command.meshIndex].indices;
		if (!inFrontOfCamera(positions, command.world, command.projection, command.camera)) {
			return;
		}
		Texture nextTexture = object.getTexture(command.textureIndex);
		if (!sameState(nextTexture, command) || vertexCount + positions.length / 3 > MAX_VERTICES
				|| indexCount + sourceIndices.length > MAX_INDICES) {
			flush();
			setState(nextTexture, command);
		}
		append(positions, uv, sourceIndices, command.world, command.colorMul,
				command.colorAdd, command.hsl, command.uvRect);
	}

	void drawMask(Texture maskTexture, float[] world, float[] rect, int projection,
			float[] cameraValues) {
		flush();
		texture = maskTexture;
		blend = BlendMode.NORMAL;
		premultiplied = false;
		this.projection = projection;
		System.arraycopy(cameraValues, 0, camera, 0, 4);
		stateSet = true;
		float x = rect[0], y = rect[1], width = rect[2], height = rect[3];
		maskPositions[0] = x; maskPositions[1] = y; maskPositions[2] = 0;
		maskPositions[3] = x + width; maskPositions[4] = y; maskPositions[5] = 0;
		maskPositions[6] = x + width; maskPositions[7] = y + height; maskPositions[8] = 0;
		maskPositions[9] = x; maskPositions[10] = y + height; maskPositions[11] = 0;
		append(maskPositions, TOP_LEFT_UNIT_UV, UNIT_INDICES, world, WHITE, CLEAR, NO_HSL, FULL_UV);
		flush();
	}

	static float[] defaultQuadUv(boolean topLeftOrigin) {
		return topLeftOrigin ? TOP_LEFT_UNIT_UV : BOTTOM_LEFT_UNIT_UV;
	}

	private void append(float[] positions, float[] uv, short[] sourceIndices,
			float[] world, float[] colorMul, float[] colorAdd, float[] hsl, float[] uvRect) {
		transformedVertices += positions.length / 3;
		int baseVertex = vertexCount;
		float u0 = uvRect[0], v0 = uvRect[1];
		float du = uvRect[2] - u0, dv = uvRect[3] - v0;
		for (int i = 0; i < positions.length / 3; i++) {
			float x = positions[i * 3], y = positions[i * 3 + 1], z = positions[i * 3 + 2];
			int offset = vertexCount++ * FLOATS_PER_VERTEX;
			vertices[offset] = world[0] * x + world[4] * y + world[8] * z + world[12];
			vertices[offset + 1] = world[1] * x + world[5] * y + world[9] * z + world[13];
			vertices[offset + 2] = world[2] * x + world[6] * y + world[10] * z + world[14];
			vertices[offset + 3] = colorMul[0];
			vertices[offset + 4] = colorMul[1];
			vertices[offset + 5] = colorMul[2];
			vertices[offset + 6] = colorMul[3];
			vertices[offset + 7] = u0 + uv[i * 2] * du;
			vertices[offset + 8] = v0 + uv[i * 2 + 1] * dv;
			vertices[offset + 9] = colorAdd[0];
			vertices[offset + 10] = colorAdd[1];
			vertices[offset + 11] = colorAdd[2];
			vertices[offset + 12] = colorAdd[3];
			vertices[offset + 13] = hsl[0];
			vertices[offset + 14] = hsl[1];
			vertices[offset + 15] = hsl[2];
		}
		for (short sourceIndex : sourceIndices) {
			indices[indexCount++] = (short) (baseVertex + (sourceIndex & 0xffff));
		}
	}

	private boolean sameState(Texture nextTexture, RenderCommand command) {
		return stateSet && texture == nextTexture && blend == command.blend
				&& premultiplied == command.premultipliedAlpha
				&& projection == command.projection && equal(camera, command.camera);
	}

	private void setState(Texture nextTexture, RenderCommand command) {
		texture = nextTexture;
		blend = command.blend;
		premultiplied = command.premultipliedAlpha;
		projection = command.projection;
		System.arraycopy(command.camera, 0, camera, 0, 4);
		stateSet = true;
	}

	void flush() {
		if (indexCount == 0) {
			return;
		}
		if (projection == CompiledScene.PROJECTION_FOCAL && camera[3] <= 0) {
			clear();
			return;
		}
		mesh.setVertices(vertices, 0, vertexCount * FLOATS_PER_VERTEX);
		mesh.setIndices(indices, 0, indexCount);
		texture.bind(0);
		applyBlend(blend, premultiplied);
		ShaderProgram shader = sceneShader.program();
		shader.bind();
		shader.setUniformi("u_texture", 0);
		boolean outputPremultiplied = premultiplied || blend == BlendMode.MULTIPLY
				|| blend == BlendMode.SCREEN;
		shader.setUniformf("u_inputPremultipliedAlpha", premultiplied ? 1f : 0f);
		shader.setUniformf("u_outputPremultipliedAlpha", outputPremultiplied ? 1f : 0f);
		shader.setUniformf("u_invert", blend == BlendMode.INVERT ? 1f : 0f);
		shader.setUniformMatrix("u_combinedTransform", combinedTransform);
		shader.setUniformf("u_camera", camera[0], camera[1], camera[2], camera[3]);
		shader.setUniformf("u_focalProjection",
				projection == CompiledScene.PROJECTION_FOCAL ? 1f : 0f);
		mesh.render(shader, GL20.GL_TRIANGLES, 0, indexCount);
		drawCalls++;
		clear();
	}

	private void clear() {
		vertexCount = 0;
		indexCount = 0;
	}

	void discard() {
		clear();
	}

	static void applyBlend(BlendMode mode, boolean premultiplied) {
		Gdx.gl.glEnable(GL20.GL_BLEND);
		boolean outputPremultiplied = premultiplied || mode == BlendMode.MULTIPLY
				|| mode == BlendMode.SCREEN;
		int sourceAlpha = outputPremultiplied ? GL20.GL_ONE : GL20.GL_SRC_ALPHA;
		switch (mode) {
			case NORMAL -> {
				Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
				Gdx.gl.glBlendFuncSeparate(sourceAlpha, GL20.GL_ONE_MINUS_SRC_ALPHA,
						GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
			}
			case ADD -> {
				Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
				Gdx.gl.glBlendFuncSeparate(sourceAlpha, GL20.GL_ONE,
						GL20.GL_ONE, GL20.GL_ONE);
			}
			case SUBTRACT -> {
				Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_REVERSE_SUBTRACT, GL20.GL_FUNC_ADD);
				Gdx.gl.glBlendFuncSeparate(sourceAlpha, GL20.GL_ONE,
						GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
			}
			case MULTIPLY -> {
				Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
				Gdx.gl.glBlendFuncSeparate(GL20.GL_DST_COLOR, GL20.GL_ONE_MINUS_SRC_ALPHA,
						GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
			}
			case SCREEN -> {
				Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
				Gdx.gl.glBlendFuncSeparate(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_COLOR,
						GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
			}
			case ERASE -> {
				Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
				Gdx.gl.glBlendFuncSeparate(GL20.GL_ZERO, GL20.GL_ONE_MINUS_SRC_ALPHA,
						GL20.GL_ZERO, GL20.GL_ONE_MINUS_SRC_ALPHA);
			}
			case INVERT -> {
				Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
				Gdx.gl.glBlendFuncSeparate(GL20.GL_ONE_MINUS_DST_COLOR, GL20.GL_ONE_MINUS_SRC_COLOR,
						GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
			}
		}
	}

	static void restoreBlend() {
		Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
		Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
				GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
	}

	private static boolean equal(float[] left, float[] right) {
		for (int i = 0; i < 4; i++) {
			if (Float.floatToIntBits(left[i]) != Float.floatToIntBits(right[i])) return false;
		}
		return true;
	}

	private static boolean inFrontOfCamera(float[] positions, float[] world, int projection,
			float[] camera) {
		if (projection != CompiledScene.PROJECTION_FOCAL) return true;
		for (int i = 0; i < positions.length / 3; i++) {
			float x = positions[i * 3], y = positions[i * 3 + 1], z = positions[i * 3 + 2];
			float worldZ = world[2] * x + world[6] * y + world[10] * z + world[14];
			if (worldZ - camera[2] <= SceneProjection.CAMERA_EPSILON) return false;
		}
		return true;
	}

	void dispose() {
		mesh.dispose();
		sceneShader.dispose();
	}

	int getDrawCalls() {
		return drawCalls;
	}

	int getTransformedVertices() {
		return transformedVertices;
	}

}
