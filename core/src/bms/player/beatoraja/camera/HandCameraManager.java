package bms.player.beatoraja.camera;

import bms.player.beatoraja.Config;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/** Render-thread owner. Skin demand is collected every frame, independent of skin preparation FPS. */
public final class HandCameraManager implements AutoCloseable {
    private final CameraCapture capture;
    private final Config config;
    private boolean requested, polled;
    private Pixmap pixmap;
    private Texture texture;
    private long uploadedAt;
    private HandCameraSettings settings;
    private HandCameraSettings failedSettings;

    public HandCameraManager(Config config) { this(config, new CameraCapture()); }
    HandCameraManager(Config config, CameraCapture capture) { this.config = config; this.capture = capture; }
    public void beginFrame() { requested = false; polled = false; }

    /** For the play skin's BGA frame: null (show the BGA) unless that option is on and a frame is available. */
    public Texture requestBgaFrameTexture() {
        return config.isHandCameraBgaFrame() ? requestTexture() : null;
    }

    public Texture requestTexture() {
        HandCameraSettings next = HandCameraSettings.from(config);
        if (!next.usable()) return null;
        requested = true;
        if (next.equals(failedSettings)) return null;
        if (!next.equals(settings)) {
            settings = next;
            uploadedAt = 0;
        }
        capture.request(next);
        try {
            if (!polled) {
                polled = true;
                try (CameraCapture.Frame frame = capture.poll()) {
                    if (frame != null) {
                        if (pixmap != null && (pixmap.getWidth() != frame.width() || pixmap.getHeight() != frame.height())) {
                            disposeTexture();
                        }
                        if (pixmap == null) pixmap = new Pixmap(frame.width(), frame.height(), Pixmap.Format.RGB888);
                        pixmap.getPixels().clear();
                        pixmap.getPixels().put(frame.pixels()).flip();
                        if (texture == null) {
                            texture = new Texture(pixmap);
                            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                        } else texture.draw(pixmap, 0, 0);
                        uploadedAt = System.nanoTime();
                    }
                }
            }
        } catch (RuntimeException | LinkageError error) {
            capture.stop();
            failedSettings = next;
            org.slf4j.LoggerFactory.getLogger(HandCameraManager.class).warn(
                    "Hand camera rendering unavailable ({})", error.getClass().getSimpleName());
            return null;
        }
        // A disconnected or stalled camera must not leave a frozen hand image indefinitely.
        return uploadedAt != 0 && System.nanoTime() - uploadedAt < 1_000_000_000L ? texture : null;
    }

    /** In-game menu status: a frame was shown within the last second. */
    public boolean isReceiving() { return uploadedAt != 0 && System.nanoTime() - uploadedAt < 1_000_000_000L; }

    public void endFrame() {
        if (!requested) {
            capture.stop();
            settings = null;
            failedSettings = null;
            disposeTexture();
        }
    }

    public void suspend() { requested = false; endFrame(); }

    private void disposeTexture() {
        if (texture != null) { texture.dispose(); texture = null; }
        if (pixmap != null) { pixmap.dispose(); pixmap = null; }
        uploadedAt = 0;
    }

    @Override public void close() { capture.close(); disposeTexture(); }
}
