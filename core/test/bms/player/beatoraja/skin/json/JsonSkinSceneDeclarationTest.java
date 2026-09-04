package bms.player.beatoraja.skin.json;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import bms.player.beatoraja.skin.lua.SkinLuaAccessor;

import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonSkinSceneDeclarationTest {
	@Test
	void parsesPluralSceneResourcesWithoutChangingNumericSceneDuration() {
		Json json = new Json();
		new JsonSkinSerializer(new SkinLuaAccessor(true), path -> Path.of(path).toFile())
				.setSerializers(json, new HashSet<>(), Path.of("."));
		JsonSkin.Skin skin = json.fromJson(JsonSkin.Skin.class,
				"{scene:2500,scenes:[{id:background,path:scene/background.scene.json,timer:0,cycle:2500,playbackRate:1.5,loop:true}]}");
		assertEquals(2500, skin.scene);
		assertEquals(1, skin.scenes.length);
		assertEquals("background", skin.scenes[0].id);
		assertEquals(1.5f, skin.scenes[0].playbackRate);
		assertEquals(0, skin.scenes[0].timer.getTimerId());
		assertNull(JSONSkinLoader.resolveSceneTimer(skin.scenes[0].timer));
	}

	@Test
	void omittedSceneTimerAlsoUsesStateStart() {
		Json json = new Json();
		new JsonSkinSerializer(new SkinLuaAccessor(true), path -> Path.of(path).toFile())
				.setSerializers(json, new HashSet<>(), Path.of("."));
		JsonSkin.Skin skin = json.fromJson(JsonSkin.Skin.class,
				"{scenes:[{id:background,path:scene/background.scene.json}]}");
		assertNull(JSONSkinLoader.resolveSceneTimer(skin.scenes[0].timer));
	}
}
