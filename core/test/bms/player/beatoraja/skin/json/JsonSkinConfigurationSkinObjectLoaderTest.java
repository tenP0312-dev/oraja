package bms.player.beatoraja.skin.json;

import bms.player.beatoraja.skin.property.EventFactory;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static bms.player.beatoraja.skin.SkinProperty.BUTTON_CHANGE_SKIN;

class JsonSkinConfigurationSkinObjectLoaderTest {
	@Test
	void parsesAndMatchesAnExplicitSkinPreviewDeclaration() {
		JsonSkin.Skin skin = new Json().fromJson(JsonSkin.Skin.class,
				"{skinpreview:{id:preview}}");
		JsonSkin.Destination preview = new JsonSkin.Destination();
		preview.id = "preview";
		JsonSkin.Destination other = new JsonSkin.Destination();
		other.id = "other";

		assertNotNull(skin.skinpreview);
		assertTrue(JsonSkinConfigurationSkinObjectLoader.isSkinPreviewDestination(skin, preview));
		assertFalse(JsonSkinConfigurationSkinObjectLoader.isSkinPreviewDestination(skin, other));
	}

	@Test
	void legacyContainedBackgroundBecomesThePreviewInsteadOfCoveringIt() {
		JsonSkin.Skin skin = new JsonSkin.Skin();
		JsonSkin.Image changeArea = new JsonSkin.Image();
		changeArea.id = "skin-change-area";
		changeArea.act = EventFactory.getEvent(BUTTON_CHANGE_SKIN);
		JsonSkin.Image background = new JsonSkin.Image();
		background.id = "preview-background";
		skin.image = new JsonSkin.Image[]{changeArea, background};

		JsonSkin.Destination changeDestination = destination(changeArea.id, 100, 200, 680, 360);
		JsonSkin.Destination backgroundDestination =
				destination(background.id, 120, 200, 640, 360);
		skin.destination = new JsonSkin.Destination[]{changeDestination, backgroundDestination};

		var placement = JsonSkinConfigurationSkinObjectLoader.findPreviewPlacement(skin);

		assertNotNull(placement);
		assertEquals(backgroundDestination, placement.destination());
		assertFalse(placement.preserveOriginal());
	}

	@Test
	void legacySkinWithoutAContainedVisualKeepsItsChangeTarget() {
		JsonSkin.Skin skin = new JsonSkin.Skin();
		JsonSkin.Image changeArea = new JsonSkin.Image();
		changeArea.id = "skin-change-area";
		changeArea.act = EventFactory.getEvent(BUTTON_CHANGE_SKIN);
		skin.image = new JsonSkin.Image[]{changeArea};
		JsonSkin.Destination changeDestination = destination(changeArea.id, 111, 523, 687, 388);
		skin.destination = new JsonSkin.Destination[]{changeDestination};

		var placement = JsonSkinConfigurationSkinObjectLoader.findPreviewPlacement(skin);

		assertNotNull(placement);
		assertEquals(changeDestination, placement.destination());
		assertTrue(placement.preserveOriginal());
	}

	@Test
	void explicitPreviewDestinationWinsOverLegacyInference() {
		JsonSkin.Skin skin = new JsonSkin.Skin();
		JsonSkin.Image changeArea = new JsonSkin.Image();
		changeArea.id = "skin-change-area";
		changeArea.act = EventFactory.getEvent(BUTTON_CHANGE_SKIN);
		skin.image = new JsonSkin.Image[]{changeArea};

		JsonSkin.Destination changeDestination = destination(changeArea.id, 0, 0, 680, 360);
		JsonSkin.Destination explicit = destination("explicit-preview", 40, 20, 600, 320);
		skin.destination = new JsonSkin.Destination[]{changeDestination, explicit};

		skin.skinpreview = new JsonSkin.SkinPreview();
		skin.skinpreview.id = "explicit-preview";

		var placement = JsonSkinConfigurationSkinObjectLoader.findPreviewPlacement(skin);

		assertNotNull(placement);
		assertEquals(explicit, placement.destination());
		assertFalse(placement.preserveOriginal());
	}

	private static JsonSkin.Destination destination(
			String id, int x, int y, int width, int height) {
		JsonSkin.Destination destination = new JsonSkin.Destination();
		destination.id = id;
		JsonSkin.Animation animation = new JsonSkin.Animation();
		animation.x = x;
		animation.y = y;
		animation.w = width;
		animation.h = height;
		destination.dst = new JsonSkin.Animation[]{animation};
		return destination;
	}
}
