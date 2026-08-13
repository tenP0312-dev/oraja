package bms.player.beatoraja.skin.json;

import bms.player.beatoraja.config.SkinConfigurationSkin;
import bms.player.beatoraja.config.SkinPreview;
import bms.player.beatoraja.skin.SkinHeader;
import bms.player.beatoraja.skin.SkinObject;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import static bms.player.beatoraja.skin.SkinProperty.BUTTON_CHANGE_SKIN;

public class JsonSkinConfigurationSkinObjectLoader extends JsonSkinObjectLoader<SkinConfigurationSkin> {
	private boolean compatibilityPreviewInjected;

	public JsonSkinConfigurationSkinObjectLoader(JSONSkinLoader loader) {
		super(loader);
	}

	@Override
	public SkinConfigurationSkin getSkin(SkinHeader header) {
		return new SkinConfigurationSkin(header);
	}

	@Override
	public SkinObject loadSkinObject(SkinConfigurationSkin skin, JsonSkin.Skin sk,
			JsonSkin.Destination dst, Path path) {
		SkinObject object = super.loadSkinObject(skin, sk, dst, path);
		if (object != null) {
			if (!compatibilityPreviewInjected && shouldInjectPreviewBehindSkinChangeTarget(sk, dst)) {
				compatibilityPreviewInjected = true;
				// Keep the skin's original visual/click target, then let the caller add
				// the preview at the same destination. Later destinations such as
				// arrows remain above both objects.
				object.setName(dst.id);
				setDestination(skin, object, dst);
				skin.add(object);
				return new SkinPreview();
			}
			return object;
		}
		return isSkinPreviewDestination(sk, dst) ? new SkinPreview() : null;
	}

	static boolean isSkinPreviewDestination(JsonSkin.Skin skin, JsonSkin.Destination destination) {
		return skin.skinpreview != null && skin.skinpreview.id != null
				&& Objects.equals(destination.id, skin.skinpreview.id);
	}

	static boolean shouldInjectPreviewBehindSkinChangeTarget(JsonSkin.Skin skin,
			JsonSkin.Destination destination) {
		if (hasExplicitPreviewDestination(skin) || !hasLargeDestination(destination)) {
			return false;
		}
		return skin.image != null && Arrays.stream(skin.image)
				.anyMatch(image -> Objects.equals(destination.id, image.id)
						&& image.act != null && image.act.getEventId() == BUTTON_CHANGE_SKIN)
				|| skin.imageset != null && Arrays.stream(skin.imageset)
				.anyMatch(imageSet -> Objects.equals(destination.id, imageSet.id)
						&& imageSet.act != null && imageSet.act.getEventId() == BUTTON_CHANGE_SKIN);
	}

	private static boolean hasExplicitPreviewDestination(JsonSkin.Skin skin) {
		return skin.skinpreview != null && skin.skinpreview.id != null
				&& skin.destination != null
				&& Arrays.stream(skin.destination)
				.anyMatch(destination -> Objects.equals(destination.id, skin.skinpreview.id));
	}

	private static boolean hasLargeDestination(JsonSkin.Destination destination) {
		return destination.dst != null && Arrays.stream(destination.dst)
				.anyMatch(animation -> animation.w != Integer.MIN_VALUE
						&& animation.h != Integer.MIN_VALUE
						&& Math.abs(animation.w) >= 160 && Math.abs(animation.h) >= 90);
	}

}
