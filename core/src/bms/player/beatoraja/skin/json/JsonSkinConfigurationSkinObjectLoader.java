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
	private static final double MIN_COMPATIBILITY_AREA_RATIO = 0.5d;
	private static final double MIN_COMPATIBILITY_ASPECT_RATIO = 0.75d;
	private static final double MAX_COMPATIBILITY_ASPECT_RATIO = 4d / 3d;

	private boolean previewPlacementResolved;
	private PreviewPlacement previewPlacement;
	private boolean previewInjected;

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
		if (!previewPlacementResolved) {
			previewPlacement = findPreviewPlacement(sk);
			previewPlacementResolved = true;
		}
		if (!previewInjected && previewPlacement != null
				&& previewPlacement.destination() == dst) {
			previewInjected = true;
			if (previewPlacement.preserveOriginal()) {
				SkinObject object = super.loadSkinObject(skin, sk, dst, path);
				if (object != null) {
					// The legacy fallback can be the skin-change click target itself.
					// Retain that input object below the opaque preview surface.
					object.setName(dst.id);
					setDestination(skin, object, dst);
					skin.add(object);
				}
			}
			return new SkinPreview();
		}
		return super.loadSkinObject(skin, sk, dst, path);
	}

	static boolean isSkinPreviewDestination(JsonSkin.Skin skin, JsonSkin.Destination destination) {
		return skin.skinpreview != null && skin.skinpreview.id != null
				&& Objects.equals(destination.id, skin.skinpreview.id);
	}

	static PreviewPlacement findPreviewPlacement(JsonSkin.Skin skin) {
		JsonSkin.Destination explicit = findExplicitPreviewDestination(skin);
		if (explicit != null) {
			return new PreviewPlacement(explicit, isSkinChangeTarget(skin, explicit.id));
		}

		JsonSkin.Destination changeTarget = findSkinChangeTarget(skin);
		if (changeTarget == null) {
			return null;
		}
		PreviewBounds changeBounds = destinationBounds(changeTarget);
		if (changeBounds == null) {
			return null;
		}

		JsonSkin.Destination containedVisual = null;
		double containedArea = 0d;
		boolean afterChangeTarget = false;
		if (skin.destination != null) {
			for (JsonSkin.Destination destination : skin.destination) {
				if (destination == changeTarget) {
					afterChangeTarget = true;
					continue;
				}
				if (!afterChangeTarget || !hasVisualDefinition(skin, destination.id)) {
					continue;
				}
				PreviewBounds candidate = destinationBounds(destination);
				if (!isCompatibleContainedVisual(changeBounds, candidate)) {
					continue;
				}
				double area = candidate.area();
				if (area > containedArea) {
					containedArea = area;
					containedVisual = destination;
				}
			}
		}
		return containedVisual != null
				? new PreviewPlacement(containedVisual, false)
				: new PreviewPlacement(changeTarget, true);
	}

	private static JsonSkin.Destination findExplicitPreviewDestination(JsonSkin.Skin skin) {
		if (skin.skinpreview == null || skin.skinpreview.id == null || skin.destination == null) {
			return null;
		}
		return Arrays.stream(skin.destination)
				.filter(destination -> isSkinPreviewDestination(skin, destination))
				.findFirst()
				.orElse(null);
	}

	private static JsonSkin.Destination findSkinChangeTarget(JsonSkin.Skin skin) {
		if (skin.destination == null) {
			return null;
		}
		return Arrays.stream(skin.destination)
				.filter(destination -> destinationBounds(destination) != null)
				.filter(destination -> isSkinChangeTarget(skin, destination.id))
				.findFirst()
				.orElse(null);
	}

	private static boolean isSkinChangeTarget(JsonSkin.Skin skin, String id) {
		return skin.image != null && Arrays.stream(skin.image)
				.anyMatch(image -> Objects.equals(id, image.id)
						&& image.act != null && image.act.getEventId() == BUTTON_CHANGE_SKIN)
				|| skin.imageset != null && Arrays.stream(skin.imageset)
				.anyMatch(imageSet -> Objects.equals(id, imageSet.id)
						&& imageSet.act != null && imageSet.act.getEventId() == BUTTON_CHANGE_SKIN);
	}

	private static boolean hasVisualDefinition(JsonSkin.Skin skin, String id) {
		return skin.image != null && Arrays.stream(skin.image)
				.anyMatch(image -> Objects.equals(id, image.id))
				|| skin.imageset != null && Arrays.stream(skin.imageset)
				.anyMatch(imageSet -> Objects.equals(id, imageSet.id));
	}

	private static boolean isCompatibleContainedVisual(
			PreviewBounds changeTarget, PreviewBounds candidate) {
		if (candidate == null || !changeTarget.contains(candidate)
				|| candidate.area() < changeTarget.area() * MIN_COMPATIBILITY_AREA_RATIO) {
			return false;
		}
		double aspectRatio = candidate.aspectRatio() / changeTarget.aspectRatio();
		return aspectRatio >= MIN_COMPATIBILITY_ASPECT_RATIO
				&& aspectRatio <= MAX_COMPATIBILITY_ASPECT_RATIO;
	}

	private static PreviewBounds destinationBounds(JsonSkin.Destination destination) {
		if (destination == null || destination.dst == null) {
			return null;
		}
		int x = 0;
		int y = 0;
		int width = 0;
		int height = 0;
		for (JsonSkin.Animation animation : destination.dst) {
			if (animation.x != Integer.MIN_VALUE) {
				x = animation.x;
			}
			if (animation.y != Integer.MIN_VALUE) {
				y = animation.y;
			}
			if (animation.w != Integer.MIN_VALUE) {
				width = animation.w;
			}
			if (animation.h != Integer.MIN_VALUE) {
				height = animation.h;
			}
			if (Math.abs(width) >= 160 && Math.abs(height) >= 90) {
				return PreviewBounds.of(x, y, width, height);
			}
		}
		return null;
	}

	record PreviewPlacement(JsonSkin.Destination destination, boolean preserveOriginal) {}

	private record PreviewBounds(double left, double bottom, double right, double top) {
		private static PreviewBounds of(int x, int y, int width, int height) {
			return new PreviewBounds(
					Math.min(x, x + width), Math.min(y, y + height),
					Math.max(x, x + width), Math.max(y, y + height));
		}

		private double area() {
			return (right - left) * (top - bottom);
		}

		private double aspectRatio() {
			return (right - left) / (top - bottom);
		}

		private boolean contains(PreviewBounds other) {
			return other.left >= left && other.right <= right
					&& other.bottom >= bottom && other.top <= top;
		}
	}
}
