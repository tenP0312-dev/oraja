package bms.player.beatoraja.skin.json;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JSONSkinLoaderSceneIdTest {
	@Test
	void collisionsClaimDestinationButNeverFallBackOrReenable() {
		JsonSkin.Skin skin = new JsonSkin.Skin();
		JsonSkin.Image image = new JsonSkin.Image();
		image.id = "ordinary";
		skin.image = new JsonSkin.Image[] {image};
		skin.scenes = new JsonSkin.SceneResource[] {
				scene("ordinary", "first.scene.json"),
				scene("duplicate", "first.scene.json"),
				scene("duplicate", "second.scene.json"),
				scene("duplicate", "third.scene.json"),
				scene("valid", "valid.scene.json")
		};

		JSONSkinLoader loader = new JSONSkinLoader();
		loader.prepareSceneResources(skin);
		assertTrue(loader.claimsSceneId("ordinary"));
		assertTrue(loader.claimsSceneId("duplicate"));
		assertFalse(loader.hasEnabledSceneId("ordinary"));
		assertFalse(loader.hasEnabledSceneId("duplicate"));
		assertTrue(loader.hasEnabledSceneId("valid"));

		JsonSkin.Destination destination = new JsonSkin.Destination();
		destination.id = "ordinary";
		assertNull(new JsonSelectSkinObjectLoader(loader)
				.loadSkinObject(null, skin, destination, Path.of("skin.json")));
		assertNull(new JsonPlaySkinObjectLoader(loader)
				.loadSkinObject(null, skin, destination, Path.of("skin.json")));
		assertNull(new JsonSkinConfigurationSkinObjectLoader(loader)
				.loadSkinObject(null, skin, destination, Path.of("skin.json")));
	}

	private static JsonSkin.SceneResource scene(String id, String path) {
		JsonSkin.SceneResource scene = new JsonSkin.SceneResource();
		scene.id = id;
		scene.path = path;
		return scene;
	}
}
