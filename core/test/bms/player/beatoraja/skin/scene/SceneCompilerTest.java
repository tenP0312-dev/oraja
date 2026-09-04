package bms.player.beatoraja.skin.scene;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneCompilerTest {
	@Test
	void compilesRowMajorAffineAndUsesLastDuplicateKey() throws Exception {
		SceneDocument document = fixture();
		SceneDocument.TransformTrackDefinition track = new SceneDocument.TransformTrackDefinition();
		track.kind = "affine2";
		track.keys = new SceneDocument.TransformKeyDefinition[] {
				affineKey(100, new float[] {1, 0, 0, 1, 2, 3}),
				affineKey(0, new float[] {1, 0, 0, 1, 0, 0}),
				affineKey(100, new float[] {1, 0, 0, 1, 4, 5})
		};
		document.clips[0].instances[0].transformTrack = track;

		CompiledScene compiled = new SceneCompiler().compile(document, Path.of("/tmp/scene.json"));
		CompiledScene.TransformTrack result = compiled.clips[0].instances[0].transform;

		assertArrayEquals(new long[] {0, 100}, result.times);
		assertEquals(4, result.matrices[1][12]);
		assertEquals(5, result.matrices[1][13]);
	}

	@Test
	void rejectsCyclesAndNonFiniteValues() {
		SceneDocument cyclic = fixture();
		cyclic.clips = new SceneDocument.ClipDefinition[] {clip("root"), clip("child")};
		cyclic.rootClip = "root";
		cyclic.clips[0].instances = new SceneDocument.InstanceDefinition[] {clipInstance("a", "child")};
		cyclic.clips[1].instances = new SceneDocument.InstanceDefinition[] {clipInstance("b", "root")};
		assertThrows(SceneValidationException.class,
				() -> new SceneCompiler().compile(cyclic, Path.of("/tmp/scene.json")));

		SceneDocument nonFinite = fixture();
		SceneDocument.TransformTrackDefinition track = new SceneDocument.TransformTrackDefinition();
		track.keys = new SceneDocument.TransformKeyDefinition[] {
				affineKey(0, new float[] {Float.NaN, 0, 0, 1, 0, 0})
		};
		nonFinite.clips[0].instances[0].transformTrack = track;
		assertThrows(SceneValidationException.class,
				() -> new SceneCompiler().compile(nonFinite, Path.of("/tmp/scene.json")));

		SceneDocument unreachableCycle = fixture();
		SceneDocument.ClipDefinition root = unreachableCycle.clips[0];
		SceneDocument.ClipDefinition first = clip("first");
		SceneDocument.ClipDefinition second = clip("second");
		first.instances = new SceneDocument.InstanceDefinition[] {clipInstance("first-child", "second")};
		second.instances = new SceneDocument.InstanceDefinition[] {clipInstance("second-child", "first")};
		unreachableCycle.clips = new SceneDocument.ClipDefinition[] {root, first, second};
		assertThrows(SceneValidationException.class,
				() -> new SceneCompiler().compile(unreachableCycle, Path.of("/tmp/scene.json")));
	}

	@Test
	void rejectsTextureTraversalAndOutOfRangeMeshIndex() {
		SceneDocument traversal = fixture();
		traversal.textures[0].path = "../outside.png";
		assertThrows(SceneValidationException.class,
				() -> new SceneCompiler().compile(traversal, Path.of("/tmp/scene.json")));

		SceneDocument invalidMesh = fixture();
		SceneDocument.MeshDefinition mesh = new SceneDocument.MeshDefinition();
		mesh.id = "quad";
		mesh.positions = new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0};
		mesh.uv = new float[] {0, 0, 1, 0, 0, 1};
		mesh.indices = new int[] {0, 1, 3};
		invalidMesh.meshes = new SceneDocument.MeshDefinition[] {mesh};
		assertThrows(SceneValidationException.class,
				() -> new SceneCompiler().compile(invalidMesh, Path.of("/tmp/scene.json")));
	}

	@Test
	void compilesAffine3PivotAndInheritedTrackValues() throws Exception {
		SceneDocument document = fixture();
		SceneDocument.TransformTrackDefinition transform = new SceneDocument.TransformTrackDefinition();
		transform.kind = "affine3";
		SceneDocument.TransformKeyDefinition first = new SceneDocument.TransformKeyDefinition();
		first.t = 0;
		first.value = new float[] {0, -1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 7};
		first.pivot = new float[] {10, 0, 0};
		SceneDocument.TransformKeyDefinition inherited = new SceneDocument.TransformKeyDefinition();
		inherited.t = 100;
		transform.keys = new SceneDocument.TransformKeyDefinition[] {first, inherited};
		document.clips[0].instances[0].transformTrack = transform;

		SceneDocument.ColorTrackDefinition color = new SceneDocument.ColorTrackDefinition();
		SceneDocument.ColorKeyDefinition colorFirst = new SceneDocument.ColorKeyDefinition();
		colorFirst.mul = new float[] {0.5f, 0.6f, 0.7f, 0.8f};
		SceneDocument.ColorKeyDefinition colorInherited = new SceneDocument.ColorKeyDefinition();
		colorInherited.t = 100;
		color.keys = new SceneDocument.ColorKeyDefinition[] {colorFirst, colorInherited};
		document.clips[0].instances[0].colorTrack = color;

		CompiledScene.Instance instance = new SceneCompiler()
				.compile(document, Path.of("/tmp/scene.json")).clips[0].instances[0];
		assertEquals(10, instance.transform.matrices[0][12], 0.0001f);
		assertEquals(-10, instance.transform.matrices[0][13], 0.0001f);
		assertEquals(7, instance.transform.matrices[0][14], 0.0001f);
		assertArrayEquals(instance.transform.matrices[0], instance.transform.matrices[1]);
		assertArrayEquals(instance.color.mul[0], instance.color.mul[1]);
	}

	@Test
	void rejectsKeysAndFrameIntervalsBeyondTheirClip() {
		SceneDocument lateKey = fixture();
		lateKey.clips[0].duration = 100;
		lateKey.clips[0].instances[0].end = 100;
		lateKey.clips[0].instances[0].transformTrack = new SceneDocument.TransformTrackDefinition();
		lateKey.clips[0].instances[0].transformTrack.keys = new SceneDocument.TransformKeyDefinition[] {
				affineKey(101, new float[] {1, 0, 0, 1, 0, 0})
		};
		assertThrows(SceneValidationException.class,
				() -> new SceneCompiler().compile(lateKey, Path.of("/tmp/scene.json")));

		SceneDocument lateFrame = fixture();
		lateFrame.clips = new SceneDocument.ClipDefinition[] {clip("root"), clip("child")};
		lateFrame.rootClip = "root";
		SceneDocument.InstanceDefinition child = clipInstance("child-instance", "child");
		child.end = 100;
		SceneDocument.FrameKeyDefinition frame = new SceneDocument.FrameKeyDefinition();
		frame.t = 0;
		frame.end = 101;
		child.frameTrack = new SceneDocument.FrameKeyDefinition[] {frame};
		lateFrame.clips[0].instances = new SceneDocument.InstanceDefinition[] {child};
		lateFrame.clips[1].instances = new SceneDocument.InstanceDefinition[] {
				textureInstance("child-leaf", "texture")
		};
		assertThrows(SceneValidationException.class,
				() -> new SceneCompiler().compile(lateFrame, Path.of("/tmp/scene.json")));
	}

	static SceneDocument fixture() {
		SceneDocument document = new SceneDocument();
		document.formatVersion = 1;
		document.canvas.width = 100;
		document.canvas.height = 100;
		document.timebase.ticksPerSecond = 60000;
		document.duration = 60000;
		SceneDocument.TextureDefinition texture = new SceneDocument.TextureDefinition();
		texture.id = "texture";
		texture.path = "texture.png";
		document.textures = new SceneDocument.TextureDefinition[] {texture};
		SceneDocument.ClipDefinition root = clip("root");
		SceneDocument.InstanceDefinition leaf = new SceneDocument.InstanceDefinition();
		leaf.id = "leaf";
		leaf.ref = "texture";
		root.instances = new SceneDocument.InstanceDefinition[] {leaf};
		document.clips = new SceneDocument.ClipDefinition[] {root};
		document.rootClip = "root";
		return document;
	}

	static SceneDocument.ClipDefinition clip(String id) {
		SceneDocument.ClipDefinition clip = new SceneDocument.ClipDefinition();
		clip.id = id;
		clip.duration = 60000;
		return clip;
	}

	static SceneDocument.InstanceDefinition clipInstance(String id, String reference) {
		SceneDocument.InstanceDefinition instance = new SceneDocument.InstanceDefinition();
		instance.id = id;
		instance.kind = "clip";
		instance.ref = reference;
		return instance;
	}

	static SceneDocument.InstanceDefinition textureInstance(String id, String reference) {
		SceneDocument.InstanceDefinition instance = new SceneDocument.InstanceDefinition();
		instance.id = id;
		instance.ref = reference;
		return instance;
	}

	static SceneDocument.TransformKeyDefinition affineKey(long time, float[] value) {
		SceneDocument.TransformKeyDefinition key = new SceneDocument.TransformKeyDefinition();
		key.t = time;
		key.value = value;
		return key;
	}
}
