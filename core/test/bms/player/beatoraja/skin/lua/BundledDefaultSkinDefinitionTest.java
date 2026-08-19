package bms.player.beatoraja.skin.lua;

import bms.player.beatoraja.SkinConfig;
import bms.player.beatoraja.skin.SkinHeader;
import bms.player.beatoraja.skin.json.JSONSkinLoader;
import bms.player.beatoraja.skin.json.JsonSkin;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledDefaultSkinDefinitionTest {
	@Test
	void bundledSelectExposesBmsirControlsAndNineSortStates() throws IOException {
		InspectingJsonLoader loader = new InspectingJsonLoader();
		Path path = Path.of("../assets/skin/default/select.json");
		assertNotNull(loader.loadHeader(path));

		JsonSkin.Skin skin = loader.parsedSkin();
		assertEquals(1920, skin.w);
		assertEquals(1080, skin.h);
		assertEquals(9, imageSet(skin, "sortset").images.length);
		assertEquals(4, imageSet(skin, "bmsir-extra").images.length);
		assertTrue(hasDestination(skin, "bmsir-restore-off"));
		assertTrue(hasDestination(skin, "bmsir-restore-on"));
		assertTrue(hasValue(skin, "ir-rank"));
		assertTrue(hasValue(skin, "folder-fullcombo"));
		assertTrue(hasDestination(skin, "comment-panel"));
		assertTrue(hasDestination(skin, "popbelt-ui-base"));
		assertTrue(hasDestination(skin, "popbelt-ui-top"));
		assertFalse(hasDestination(skin, "notes-graph"));
		assertFalse(hasDestination(skin, "bpmgraph"));

		JsonSkin.Text tableComment = text(skin, "tablecomment");
		assertTrue(tableComment.wrapping);
		assertEquals(18, tableComment.size);
		JsonSkin.Destination tableCommentDestination = destination(skin, "tablecomment");
		assertEquals(120, tableCommentDestination.dst[0].x);
		assertEquals(286, tableCommentDestination.dst[0].y);
		assertEquals(1000, tableCommentDestination.dst[0].w);
		assertEquals(28, tableCommentDestination.dst[0].h);
		assertEquals(1, skin.radargraph.length);
		assertEquals(652, destination(skin, "radar1").dst[0].y);
		assertEquals(630, destination(skin, "playlevel").dst[0].y);
		assertEquals(505, destination(skin, "score").dst[0].y);

		var placeholder = ImageIO.read(Path.of("../assets/skin/default/bmsir-controls-placeholder.png").toFile());
		assertNotNull(placeholder);
		assertEquals(224, placeholder.getWidth());
		assertEquals(32, placeholder.getHeight());

		for (String name : new String[] {"select.png", "select2.png", "select3.png"}) {
			var background = ImageIO.read(Path.of("../assets/skin/default/select/background", name).toFile());
			assertNotNull(background);
			assertEquals(1920, background.getWidth());
			assertEquals(1080, background.getHeight());
		}

		var baseUi = ImageIO.read(Path.of("../assets/skin/default/select/popbelt-ui-base.png").toFile());
		assertNotNull(baseUi);
		assertEquals(1920, baseUi.getWidth());
		assertEquals(1080, baseUi.getHeight());

		var topUi = ImageIO.read(Path.of("../assets/skin/default/select/popbelt-ui-top.png").toFile());
		assertNotNull(topUi);
		assertEquals(1920, topUi.getWidth());
		assertEquals(1080, topUi.getHeight());

		var songBar = ImageIO.read(Path.of("../assets/skin/default/songbar.png").toFile());
		assertNotNull(songBar);
		assertEquals(750, songBar.getWidth());
		assertEquals(432, songBar.getHeight());

		assertTrue(Files.isRegularFile(Path.of("../assets/skin/default/select/background/select.svg")));
		assertTrue(Files.isRegularFile(Path.of("../assets/skin/default/select/background/select2.svg")));
		assertTrue(Files.isRegularFile(Path.of("../assets/skin/default/select/background/select3.svg")));
		assertTrue(Files.isRegularFile(Path.of("../assets/skin/default/select/popbelt-ui-base.svg")));
		assertTrue(Files.isRegularFile(Path.of("../assets/skin/default/select/popbelt-ui-top.svg")));
		assertTrue(Files.isRegularFile(Path.of("../assets/skin/default/songbar.svg")));
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

	private static JsonSkin.Destination destination(JsonSkin.Skin skin, String id) {
		return Arrays.stream(skin.destination)
				.filter(candidate -> id.equals(candidate.id))
				.findFirst()
				.orElseThrow();
	}

	private static JsonSkin.Text text(JsonSkin.Skin skin, String id) {
		return Arrays.stream(skin.text)
				.filter(candidate -> id.equals(candidate.id))
				.findFirst()
				.orElseThrow();
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
