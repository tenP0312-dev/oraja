package bms.player.beatoraja.skin.lua;

import bms.player.beatoraja.skin.json.JsonSkin;
import bms.player.beatoraja.SkinConfig;
import bms.player.beatoraja.Resolution;
import bms.player.beatoraja.config.SkinConfigurationSkin;
import bms.player.beatoraja.config.SkinPreview;
import bms.player.beatoraja.skin.SkinHeader;
import bms.player.beatoraja.skin.SkinObject;
import bms.player.beatoraja.skin.json.JSONSkinLoader;
import bms.player.beatoraja.skin.json.JsonSkinConfigurationSkinObjectLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaSkinPreviewDeclarationTest {
	@Test
	void bundledSkinSelectDeclaresAndDestinatesPreview() {
		InspectingLoader loader = new InspectingLoader();
		Path path = Path.of("../assets/skin/default/skinselect/skinselect.luaskin");
		SkinHeader header = loader.loadHeader(path);
		assertNotNull(header);
		loader.loadConfiguredSkin(path, header);

		JsonSkin.Skin skin = loader.parsedSkin();
		assertNotNull(skin.skinpreview);
		assertEquals("skin-preview", skin.skinpreview.id);
		JsonSkin.Destination destination = Arrays.stream(skin.destination)
				.filter(candidate -> "skin-preview".equals(candidate.id))
				.findFirst()
				.orElseThrow();

		header.setSourceResolution(Resolution.HD);
		header.setDestinationResolution(Resolution.HD);
		SkinConfigurationSkin configurationSkin = new SkinConfigurationSkin(header);
		SkinObject object = new JsonSkinConfigurationSkinObjectLoader(new JSONSkinLoader())
				.loadSkinObject(configurationSkin, skin, destination, path);
		assertTrue(object instanceof SkinPreview);
	}

	private static final class InspectingLoader extends LuaSkinLoader {
		void loadConfiguredSkin(Path path, SkinHeader header) {
			lua.exportSkinProperty(header, new SkinConfig.Property(), value -> value);
			sk = fromLuaValue(JsonSkin.Skin.class, lua.execFile(path));
		}

		JsonSkin.Skin parsedSkin() {
			return sk;
		}
	}
}
