package bms.player.beatoraja.camera;

import bms.player.beatoraja.camera.FFmpegCameraProcess.Device;
import java.util.List;
import java.util.Optional;

/**
 * Matches the remembered camera against the devices enumerated now. Pure logic, no native code.
 * Never returns a different camera when the remembered one is missing or cannot be told apart.
 */
public final class HandCameraDeviceResolver {
    private HandCameraDeviceResolver() { }

    public static Optional<Device> resolve(HandCameraSettings saved, List<Device> devices) {
        return resolve(saved(saved), devices);
    }

    /** OS unique ID first, then the device name; the old value only breaks a tie between identical names. */
    static Optional<Device> resolve(Device saved, List<Device> devices) {
        if (!saved.id().isEmpty()) {
            Optional<Device> exact = devices.stream().filter(device -> device.id().equals(saved.id())).findFirst();
            if (exact.isPresent()) return exact;
            // Chosen among identically named cameras: only that unit's ID identifies it. Otherwise the ID may
            // change when the same camera moves to another USB port, so its unique name still identifies it.
            if (saved.value().equals(saved.id())) return Optional.empty();
        }
        String name = saved.displayName();
        List<Device> named = devices.stream().filter(device -> device.label().equals(name)).toList();
        if (named.size() == 1) return Optional.of(named.get(0));
        return named.stream().filter(device -> device.value().equals(saved.value())).findFirst();
    }

    /** Saves a selection from the launcher or the F5 menu with its identity. */
    public static void remember(bms.player.beatoraja.Config config, Device device) {
        config.setHandCameraDevice(device.value());
        config.setHandCameraDeviceName(device.label());
        config.setHandCameraDeviceId(device.id());
    }

    /** Launcher display of the saved configuration before the operator refreshes the list. */
    public static Device saved(HandCameraSettings settings) {
        return new Device(settings.device(), settings.deviceName(), settings.deviceId());
    }

    /** Launcher selection after Refresh: the same camera even if its index or list position changed. */
    public static Device refreshed(Device selected, List<Device> devices) {
        Optional<Device> match = selected.label().isEmpty() && selected.id().isEmpty()
                ? devices.stream().filter(device -> device.value().equals(selected.value())).findFirst()
                : resolve(selected, devices);
        return match.orElse(selected);
    }

    /**
     * Launcher commit of the combo box text. Candidates are in preference order (the selected entry first,
     * because identically named cameras display the same text). A candidate keeps its identity; text typed by
     * hand is an FFmpeg input value without an identity and is used as-is, as before.
     */
    public static Device select(String text, List<Device> candidates) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return new Device("", "", "");
        for (Device device : candidates) {
            if (device != null && (device.displayName().equals(value) || device.value().equals(value))) return device;
        }
        return new Device(value, "", "");
    }
}
