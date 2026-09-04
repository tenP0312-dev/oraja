package bms.player.beatoraja.skin.scene;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneEvaluatorTest {
	private static final float TOLERANCE = 0.0001f;

	@Test
	void composesParentTransformAndAffineColorInDepthOrder() throws Exception {
		SceneDocument document = SceneCompilerTest.fixture();
		SceneDocument.ClipDefinition child = SceneCompilerTest.clip("child");
		SceneDocument.InstanceDefinition leaf = document.clips[0].instances[0];
		leaf.id = "child-leaf";
		leaf.transformTrack = affineTrack(0, 5);
		leaf.colorTrack = colorTrack(0.5f, 0.2f);
		child.instances = new SceneDocument.InstanceDefinition[] {leaf};

		SceneDocument.ClipDefinition root = SceneCompilerTest.clip("root");
		SceneDocument.InstanceDefinition parent = SceneCompilerTest.clipInstance("parent", "child");
		parent.transformTrack = affineTrack(10, 0);
		parent.colorTrack = colorTrack(0.5f, 0.1f);
		root.instances = new SceneDocument.InstanceDefinition[] {parent};
		document.clips = new SceneDocument.ClipDefinition[] {root, child};

		CompiledScene scene = new SceneCompiler().compile(document, Path.of("/tmp/scene.json"));
		SceneEvaluator evaluator = new SceneEvaluator(scene);
		assertEquals(1, evaluator.evaluate(0, 0, 0, 100, 100, 0, 1, 1, 1, 1));
		RenderCommand command = evaluator.getCommands()[0];

		assertEquals(10, command.world[12], TOLERANCE);
		assertEquals(5, command.world[13], TOLERANCE);
		assertEquals(0.25f, command.colorMul[0], TOLERANCE);
		assertEquals(0.2f, command.colorAdd[0], TOLERANCE);
	}

	@Test
	void frameOverrideIsStepBasedAndNormalPlaybackRemainsContinuous() throws Exception {
		SceneDocument document = SceneCompilerTest.fixture();
		SceneDocument.ClipDefinition child = SceneCompilerTest.clip("child");
		SceneDocument.InstanceDefinition leaf = document.clips[0].instances[0];
		leaf.id = "animated-leaf";
		leaf.transformTrack = animatedAffineTrack();
		child.instances = new SceneDocument.InstanceDefinition[] {leaf};

		SceneDocument.ClipDefinition root = SceneCompilerTest.clip("root");
		SceneDocument.InstanceDefinition parent = SceneCompilerTest.clipInstance("parent", "child");
		SceneDocument.FrameKeyDefinition frame = new SceneDocument.FrameKeyDefinition();
		frame.t = 0;
		frame.end = 500;
		frame.frame = 1;
		parent.frameTrack = new SceneDocument.FrameKeyDefinition[] {frame};
		root.instances = new SceneDocument.InstanceDefinition[] {parent};
		document.clips = new SceneDocument.ClipDefinition[] {root, child};

		SceneEvaluator evaluator = new SceneEvaluator(
				new SceneCompiler().compile(document, Path.of("/tmp/scene.json")));
		evaluator.evaluate(100, 0, 0, 100, 100, 0, 1, 1, 1, 1);
		assertEquals(10, evaluator.getCommands()[0].world[12], TOLERANCE);

		evaluator.evaluate(1500, 0, 0, 100, 100, 0, 1, 1, 1, 1);
		assertEquals(15, evaluator.getCommands()[0].world[12], TOLERANCE);
	}

	@Test
	void copiesNestedProjectiveMasksAndCamera() throws Exception {
		SceneDocument document = SceneCompilerTest.fixture();
		SceneDocument.InstanceDefinition leaf = document.clips[0].instances[0];
		leaf.clipRectTrack = rectTrack(1, 2, 30, 40);
		leaf.cameraTrack = focalCamera();

		SceneEvaluator evaluator = new SceneEvaluator(
				new SceneCompiler().compile(document, Path.of("/tmp/scene.json")));
		evaluator.evaluate(0, 0, 0, 100, 100, 0, 1, 1, 1, 1);
		RenderCommand command = evaluator.getCommands()[0];
		assertEquals(1, command.maskCount);
		assertEquals(CompiledScene.PROJECTION_FOCAL, command.projection);
		assertEquals(50, command.camera[0], TOLERANCE);
		assertEquals(30, command.maskRect[0][2], TOLERANCE);
	}

	@Test
	void usesStableDepthOrderAndHalfOpenActiveIntervals() throws Exception {
		SceneDocument document = SceneCompilerTest.fixture();
		document.textures = new SceneDocument.TextureDefinition[] {
				texture("zero"), texture("one"), texture("two")
		};
		SceneDocument.InstanceDefinition depthTwo = SceneCompilerTest.textureInstance("depth-two", "two");
		depthTwo.depth = 2;
		SceneDocument.InstanceDefinition firstDepthOne = SceneCompilerTest.textureInstance("first-one", "zero");
		firstDepthOne.depth = 1;
		SceneDocument.InstanceDefinition secondDepthOne = SceneCompilerTest.textureInstance("second-one", "one");
		secondDepthOne.depth = 1;
		secondDepthOne.start = 10;
		secondDepthOne.end = 20;
		document.clips[0].instances = new SceneDocument.InstanceDefinition[] {
				depthTwo, firstDepthOne, secondDepthOne
		};

		SceneEvaluator evaluator = evaluator(document);
		assertEquals(3, evaluator.evaluate(10, 0, 0, 100, 100, 0, 1, 1, 1, 1));
		assertEquals(0, evaluator.getCommands()[0].textureIndex);
		assertEquals(1, evaluator.getCommands()[1].textureIndex);
		assertEquals(2, evaluator.getCommands()[2].textureIndex);
		assertEquals(2, evaluator.evaluate(20, 0, 0, 100, 100, 0, 1, 1, 1, 1));
	}

	@Test
	void interpolatesTrsQuaternionAndRecoversAfterSeek() throws Exception {
		SceneDocument document = SceneCompilerTest.fixture();
		SceneDocument.TransformTrackDefinition track = new SceneDocument.TransformTrackDefinition();
		track.kind = "trs";
		track.interpolation = "trs";
		SceneDocument.TransformKeyDefinition start = trsKey(0, new float[] {0, 0, 0, 1});
		SceneDocument.TransformKeyDefinition end = trsKey(1000, new float[] {0, 0, 1, 0});
		track.keys = new SceneDocument.TransformKeyDefinition[] {start, end};
		document.clips[0].instances[0].transformTrack = track;

		SceneEvaluator evaluator = evaluator(document);
		evaluator.evaluate(500, 0, 0, 100, 100, 0, 1, 1, 1, 1);
		assertEquals(0, evaluator.getCommands()[0].world[0], TOLERANCE);
		assertEquals(1, evaluator.getCommands()[0].world[1], TOLERANCE);
		evaluator.evaluate(900, 0, 0, 100, 100, 0, 1, 1, 1, 1);
		evaluator.evaluate(100, 0, 0, 100, 100, 0, 1, 1, 1, 1);
		assertEquals((float) Math.cos(Math.toRadians(18)),
				evaluator.getCommands()[0].world[0], TOLERANCE);
	}

	@Test
	void appliesChildPlaybackRateAndRepeat() throws Exception {
		SceneDocument document = SceneCompilerTest.fixture();
		SceneDocument.ClipDefinition child = SceneCompilerTest.clip("child");
		SceneDocument.InstanceDefinition leaf = document.clips[0].instances[0];
		leaf.id = "child-leaf";
		leaf.transformTrack = animatedAffineTrack();
		child.instances = new SceneDocument.InstanceDefinition[] {leaf};
		SceneDocument.ClipDefinition root = SceneCompilerTest.clip("root");
		SceneDocument.InstanceDefinition parent = SceneCompilerTest.clipInstance("parent", "child");
		parent.playbackRate = 2;
		parent.loop = "repeat";
		root.instances = new SceneDocument.InstanceDefinition[] {parent};
		document.clips = new SceneDocument.ClipDefinition[] {root, child};

		SceneEvaluator evaluator = evaluator(document);
		evaluator.evaluate(31_000, 0, 0, 100, 100, 0, 1, 1, 1, 1);
		assertEquals(20, evaluator.getCommands()[0].world[12], TOLERANCE);
	}

	@Test
	void holdsTheFirstKeyBeforeItsTimestampInsteadOfExtrapolating() throws Exception {
		SceneDocument document = SceneCompilerTest.fixture();
		SceneDocument.TransformTrackDefinition track = new SceneDocument.TransformTrackDefinition();
		track.keys = new SceneDocument.TransformKeyDefinition[] {
				SceneCompilerTest.affineKey(100, new float[] {1, 0, 0, 1, 10, 0}),
				SceneCompilerTest.affineKey(200, new float[] {1, 0, 0, 1, 20, 0})
		};
		document.clips[0].instances[0].transformTrack = track;
		SceneEvaluator evaluator = evaluator(document);
		evaluator.evaluate(0, 0, 0, 100, 100, 0, 1, 1, 1, 1);
		assertEquals(10, evaluator.getCommands()[0].world[12], TOLERANCE);
	}

	private static SceneEvaluator evaluator(SceneDocument document) throws Exception {
		return new SceneEvaluator(new SceneCompiler().compile(document, Path.of("/tmp/scene.json")));
	}

	private static SceneDocument.TextureDefinition texture(String id) {
		SceneDocument.TextureDefinition texture = new SceneDocument.TextureDefinition();
		texture.id = id;
		texture.path = id + ".png";
		return texture;
	}

	private static SceneDocument.TransformKeyDefinition trsKey(long time, float[] rotation) {
		SceneDocument.TransformKeyDefinition key = new SceneDocument.TransformKeyDefinition();
		key.t = time;
		key.translation = new float[] {0, 0, 0};
		key.rotation = rotation;
		key.scale = new float[] {1, 1, 1};
		key.pivot = new float[] {0, 0, 0};
		return key;
	}

	private static SceneDocument.TransformTrackDefinition affineTrack(float x, float y) {
		SceneDocument.TransformTrackDefinition track = new SceneDocument.TransformTrackDefinition();
		track.keys = new SceneDocument.TransformKeyDefinition[] {
				SceneCompilerTest.affineKey(0, new float[] {1, 0, 0, 1, x, y})
		};
		return track;
	}

	private static SceneDocument.TransformTrackDefinition animatedAffineTrack() {
		SceneDocument.TransformTrackDefinition track = new SceneDocument.TransformTrackDefinition();
		track.keys = new SceneDocument.TransformKeyDefinition[] {
				SceneCompilerTest.affineKey(0, new float[] {1, 0, 0, 1, 0, 0}),
				SceneCompilerTest.affineKey(2000, new float[] {1, 0, 0, 1, 20, 0})
		};
		return track;
	}

	private static SceneDocument.ColorTrackDefinition colorTrack(float multiply, float add) {
		SceneDocument.ColorTrackDefinition track = new SceneDocument.ColorTrackDefinition();
		SceneDocument.ColorKeyDefinition key = new SceneDocument.ColorKeyDefinition();
		key.mul = new float[] {multiply, multiply, multiply, multiply};
		key.add = new float[] {add, add, add, add};
		track.keys = new SceneDocument.ColorKeyDefinition[] {key};
		return track;
	}

	private static SceneDocument.RectTrackDefinition rectTrack(float x, float y, float width, float height) {
		SceneDocument.RectTrackDefinition track = new SceneDocument.RectTrackDefinition();
		SceneDocument.RectKeyDefinition key = new SceneDocument.RectKeyDefinition();
		key.rect = new float[] {x, y, width, height};
		track.keys = new SceneDocument.RectKeyDefinition[] {key};
		return track;
	}

	private static SceneDocument.CameraTrackDefinition focalCamera() {
		SceneDocument.CameraTrackDefinition track = new SceneDocument.CameraTrackDefinition();
		track.projection = "focal";
		SceneDocument.CameraKeyDefinition key = new SceneDocument.CameraKeyDefinition();
		key.center = new float[] {50, 50, -100};
		key.focalLength = 100;
		track.keys = new SceneDocument.CameraKeyDefinition[] {key};
		return track;
	}
}
