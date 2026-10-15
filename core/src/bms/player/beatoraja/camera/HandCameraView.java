package bms.player.beatoraja.camera;

import bms.player.beatoraja.Config;

/**
 * Operator adjustment applied to every hand camera display (skin camera parts and the BGA frame), inside the
 * crop the skin declares. Read each frame from Config, so changes apply live without reopening the camera.
 *
 * @param offsetX display shift in % of the display width, positive to the right
 * @param offsetY display shift in % of the display height, positive downwards
 * @param zoom    magnification in %, 100 shows the whole (skin-cropped) image
 * @param focusX  horizontal centre of the zoomed area in % of the image as displayed (after mirroring)
 * @param focusY  vertical centre of the zoomed area in %, 0 is the top
 */
public record HandCameraView(int offsetX, int offsetY, int zoom, int focusX, int focusY) {
    public static final int MIN_OFFSET = -100, MAX_OFFSET = 100, MIN_ZOOM = 100, MAX_ZOOM = 400;
    public static final HandCameraView DEFAULT = new HandCameraView(0, 0, 100, 50, 50);

    public HandCameraView {
        offsetX = clamp(offsetX, MIN_OFFSET, MAX_OFFSET);
        offsetY = clamp(offsetY, MIN_OFFSET, MAX_OFFSET);
        zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        focusX = clamp(focusX, 0, 100);
        focusY = clamp(focusY, 0, 100);
    }

    public static HandCameraView from(Config config) {
        return new HandCameraView(config.getHandCameraOffsetX(), config.getHandCameraOffsetY(),
                config.getHandCameraZoom(), config.getHandCameraFocusX(), config.getHandCameraFocusY());
    }

    /** The zoomed sub-rectangle {x, y, width, height} (top-left origin) of {@code base}, which it never leaves. */
    public int[] source(int[] base, boolean mirror) {
        int width = Math.max(1, Math.round(base[2] * 100f / zoom));
        int height = Math.max(1, Math.round(base[3] * 100f / zoom));
        // Focus follows the displayed image, so a mirrored view moves the same way as the slider.
        int focus = mirror ? 100 - focusX : focusX;
        int x = base[0] + clamp(Math.round(base[2] * focus / 100f - width / 2f), 0, base[2] - width);
        int y = base[1] + clamp(Math.round(base[3] * focusY / 100f - height / 2f), 0, base[3] - height);
        return new int[] {x, y, width, height};
    }

    /** Display shift in skin units; y is up in the renderer, so a positive (downward) offset subtracts. */
    public float dx(float width) { return width * offsetX / 100f; }
    public float dy(float height) { return -height * offsetY / 100f; }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
