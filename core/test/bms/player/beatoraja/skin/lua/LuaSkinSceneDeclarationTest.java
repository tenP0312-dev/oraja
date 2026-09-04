package bms.player.beatoraja.skin.lua;

import bms.player.beatoraja.skin.json.JsonSkin;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuaSkinSceneDeclarationTest {
	@Test
	void mapsSceneResourceThroughTheSharedReflectionSchema() {
		LuaTable resource = new LuaTable();
		resource.set("id", LuaValue.valueOf("background"));
		resource.set("path", LuaValue.valueOf("scene/background.scene.json"));
		resource.set("cycle", LuaValue.valueOf(2500));
		resource.set("playbackRate", LuaValue.valueOf(1.25));
		resource.set("loop", LuaValue.TRUE);
		resource.set("timer", LuaValue.ZERO);
		LuaTable resources = new LuaTable();
		resources.set(1, resource);
		LuaTable root = new LuaTable();
		root.set("scene", LuaValue.valueOf(2500));
		root.set("scenes", resources);

		JsonSkin.Skin skin = new LuaSkinLoader().fromLuaValue(JsonSkin.Skin.class, root);
		assertEquals(2500, skin.scene);
		assertEquals(1, skin.scenes.length);
		assertEquals("background", skin.scenes[0].id);
		assertEquals(1.25f, skin.scenes[0].playbackRate);
		assertEquals(0, skin.scenes[0].timer.getTimerId());
	}
}
