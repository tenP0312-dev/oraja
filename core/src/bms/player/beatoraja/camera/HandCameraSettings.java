package bms.player.beatoraja.camera;

import bms.player.beatoraja.Config;

/**
 * Capture limits are enforced even when configuration was edited by hand.
 * {@code device} is the last FFmpeg input value. When {@code deviceName} or {@code deviceId} is set, the
 * camera is looked up by that identity on every open because the value may be an index that changes.
 */
public record HandCameraSettings(boolean enabled, String device, String deviceName, String deviceId,
        int width, int height, int fps, boolean mirror) {
    public HandCameraSettings {
        device = device == null ? "" : device.trim();
        deviceName = deviceName == null ? "" : deviceName.trim();
        deviceId = deviceId == null ? "" : deviceId.trim();
        width = Math.max(160, Math.min(1920, width));
        height = Math.max(120, Math.min(1080, height));
        fps = Math.max(1, Math.min(60, fps));
    }

    public HandCameraSettings(boolean enabled, String device, int width, int height, int fps, boolean mirror) {
        this(enabled, device, "", "", width, height, fps, mirror);
    }

    public static HandCameraSettings from(Config config) {
        return new HandCameraSettings(config.isHandCameraEnabled(), config.getHandCameraDevice(),
                config.getHandCameraDeviceName(), config.getHandCameraDeviceId(),
                config.getHandCameraWidth(), config.getHandCameraHeight(), config.getHandCameraFps(),
                config.isHandCameraMirror());
    }

    public boolean usable() { return enabled && !device.isEmpty(); }
    public boolean remembersIdentity() { return !deviceName.isEmpty() || !deviceId.isEmpty(); }
    public int frameBytes() { return width * height * 3; }

    HandCameraSettings withDevice(String value) {
        return new HandCameraSettings(enabled, value, deviceName, deviceId, width, height, fps, mirror);
    }
}
