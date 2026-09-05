package bms.player.beatoraja.skin.lua;

import bms.player.beatoraja.skin.json.JsonSkin;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuaSkinTextGradientDeclarationTest {
	@Test
	void convertsOptionalGradientColorsFromLuaTextDefinition() {
		LuaValue value = JsePlatform.standardGlobals().load("""
				return {
				  gradientTopColor = "fff4cfff",
				  gradientBottomColor = "ff6f3cff"
				}
				""").call();

		JsonSkin.Text text = new LuaSkinLoader().fromLuaValue(JsonSkin.Text.class, value);

		assertEquals("fff4cfff", text.gradientTopColor);
		assertEquals("ff6f3cff", text.gradientBottomColor);
	}
}
