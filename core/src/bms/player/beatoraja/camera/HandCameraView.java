package bms.player.beatoraja.camera;

import bms.player.beatoraja.Config;

/**
 * Operator adjustment applied to every hand camera display (skin camera parts and the BGA frame), inside the
 * crop the skin declares. Read each frame from Config, so changes apply live without reopening the camera.
 *
 * @param offsetX display shift in % of the display width, positive to the right; clipped to the frame
 * @param offsetY display shift in % of the display height, positive downwards; clipped to the frame
 * @param zoom    magnification in %, 100 shows the skin's crop window at its size
 * @param focusX  window position: 50 keeps the skin's crop, every 1 moves it 1% of the whole camera image
 *                to the right as displayed (after mirroring)
 * @param focusY  window position: 50 keeps the skin's crop, larger moves it down by % of the whole image as
 *                displayed (after a vertical flip)
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

    /**
     * The source window {x, y, width, height} (top-left origin) in a {@code fullWidth} x {@code fullHeight}
     * camera image. It starts as the skin's crop {@code base}, shrinks around its centre by the zoom and moves
     * by the window position across the whole image (adding to the skin's own crop offset), stopping at the
     * image edges. Even at 100% a skin crop smaller than the image (e.g. IIDX33's 40% strip) can be moved.
     */
    public int[] source(int[] base, int fullWidth, int fullHeight, boolean mirror) {
        return source(base, fullWidth, fullHeight, mirror, false);
    }

    public int[] source(int[] base, int fullWidth, int fullHeight, boolean mirror, boolean flipVertical) {
        int width = Math.min(fullWidth, Math.max(1, Math.round(base[2] * 100f / zoom)));
        int height = Math.min(fullHeight, Math.max(1, Math.round(base[3] * 100f / zoom)));
        // The position follows the displayed image, so a flipped view moves the same way as the slider.
        int shiftX = mirror ? 50 - focusX : focusX - 50;
        int shiftY = flipVertical ? 50 - focusY : focusY - 50;
        float centreX = base[0] + base[2] / 2f + fullWidth * shiftX / 100f;
        float centreY = base[1] + base[3] / 2f + fullHeight * shiftY / 100f;
        int x = clamp(Math.round(centreX - width / 2f), 0, fullWidth - width);
        int y = clamp(Math.round(centreY - height / 2f), 0, fullHeight - height);
        return new int[] {x, y, width, height};
    }

    /** Display shift in skin units; y is up in the renderer, so a positive (downward) offset subtracts. */
    public float dx(float width) { return width * offsetX / 100f; }
    public float dy(float height) { return -height * offsetY / 100f; }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
