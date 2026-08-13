package bms.player.beatoraja.skin.json;

import bms.player.beatoraja.skin.property.EventFactory;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

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
	void largeSkinChangeTargetProvidesCompatibilityPreviewPlacement() {
		JsonSkin.Skin skin = new JsonSkin.Skin();
		JsonSkin.Image changeArea = new JsonSkin.Image();
		changeArea.id = "skin-change-area";
		changeArea.act = EventFactory.getEvent(BUTTON_CHANGE_SKIN);
		skin.image = new JsonSkin.Image[]{changeArea};

		JsonSkin.Destination destination = new JsonSkin.Destination();
		destination.id = changeArea.id;
		JsonSkin.Animation animation = new JsonSkin.Animation();
		animation.w = 687;
		animation.h = 388;
		destination.dst = new JsonSkin.Animation[]{animation};
		skin.destination = new JsonSkin.Destination[]{destination};

		assertTrue(JsonSkinConfigurationSkinObjectLoader
				.shouldInjectPreviewBehindSkinChangeTarget(skin, destination));

		animation.h = 40;
		assertFalse(JsonSkinConfigurationSkinObjectLoader
				.shouldInjectPreviewBehindSkinChangeTarget(skin, destination));

		animation.h = 388;
		skin.skinpreview = new JsonSkin.SkinPreview();
		skin.skinpreview.id = "explicit-preview";
		JsonSkin.Destination explicit = new JsonSkin.Destination();
		explicit.id = "explicit-preview";
		skin.destination = new JsonSkin.Destination[]{destination, explicit};
		assertFalse(JsonSkinConfigurationSkinObjectLoader
				.shouldInjectPreviewBehindSkinChangeTarget(skin, destination));
	}
}
