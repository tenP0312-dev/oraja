package bms.player.beatoraja.skin.lua;

import bms.player.beatoraja.SkinConfig;
import bms.player.beatoraja.skin.SkinHeader;
import bms.player.beatoraja.skin.json.JSONSkinLoader;
import bms.player.beatoraja.skin.json.JsonSkin;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledDefaultSkinDefinitionTest {
	@Test
	void bundledSelectExposesBmsirControlsAndNineSortStates() throws IOException {
		InspectingJsonLoader loader = new InspectingJsonLoader();
		Path path = Path.of("../assets/skin/default/select.json");
		assertNotNull(loader.loadHeader(path));

		JsonSkin.Skin skin = loader.parsedSkin();
		assertEquals(9, imageSet(skin, "sortset").images.length);
		assertEquals(4, imageSet(skin, "bmsir-extra").images.length);
		assertTrue(hasDestination(skin, "bmsir-restore-off"));
		assertTrue(hasDestination(skin, "bmsir-restore-on"));
		assertTrue(hasValue(skin, "ir-rank"));
		assertTrue(hasValue(skin, "folder-fullcombo"));
		assertEquals(1, skin.radargraph.length);

		var placeholder = ImageIO.read(Path.of("../assets/skin/default/bmsir-controls-placeholder.png").toFile());
		assertNotNull(placeholder);
		assertEquals(224, placeholder.getWidth());
		assertEquals(32, placeholder.getHeight());
	}

	@Test
	void bundledSevenKeyPlayBuildsDefaultInfoAndRandomPlacement() {
		InspectingLuaLoader loader = new InspectingLuaLoader();
		Path path = Path.of("../assets/skin/default/play/play7.luaskin");
		SkinHeader header = loader.loadHeader(path);
		assertNotNull(header);
		assertTrue(Arrays.stream(header.getCustomOptions())
				.anyMatch(option -> "Lane Width".equals(option.name)));
		assertTrue(Arrays.stream(header.getCustomOptions())
				.anyMatch(option -> "Play Info".equals(option.name)));

		loader.loadConfiguredSkin(path, header);
		JsonSkin.Skin skin = loader.parsedSkin();
		assertTrue(hasValue(skin, "play-info-early"));
		assertTrue(hasValue(skin, "resolved-random-7"));
		assertTrue(hasValue(skin, "resolved-random-scratch"));
		assertTrue(hasDestination(skin, "play-info-title"));
		assertTrue(hasDestination(skin, "resolved-random-7"));
	}

	private static JsonSkin.ImageSet imageSet(JsonSkin.Skin skin, String id) {
		return Arrays.stream(skin.imageset)
				.filter(candidate -> id.equals(candidate.id))
				.findFirst()
				.orElseThrow();
	}

	private static boolean hasDestination(JsonSkin.Skin skin, String id) {
		return Arrays.stream(skin.destination).anyMatch(candidate -> id.equals(candidate.id));
	}

	private static boolean hasValue(JsonSkin.Skin skin, String id) {
		return Arrays.stream(skin.value).anyMatch(candidate -> id.equals(candidate.id));
	}

	private static final class InspectingJsonLoader extends JSONSkinLoader {
		JsonSkin.Skin parsedSkin() {
			return sk;
		}
	}

	private static final class InspectingLuaLoader extends LuaSkinLoader {
		void loadConfiguredSkin(Path path, SkinHeader header) {
			SkinConfig.Property property = new SkinConfig.Property();
			header.setSkinConfigProperty(property);
			lua.exportSkinProperty(header, property, value -> value);
			sk = fromLuaValue(JsonSkin.Skin.class, lua.execFile(path));
		}

		JsonSkin.Skin parsedSkin() {
			return sk;
		}
	}
}
