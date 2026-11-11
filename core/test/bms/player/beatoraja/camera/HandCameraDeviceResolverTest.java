package bms.player.beatoraja.camera;

import bms.player.beatoraja.camera.FFmpegCameraProcess.Device;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandCameraDeviceResolverTest {
    private static HandCameraSettings saved(String value, String name, String id) {
        return new HandCameraSettings(true, value, name, id, 640, 480, 30, false);
    }

    @Test void macCameraIsFoundByNameAfterItsIndexChanged() {
        // Selected as [0]; on the next launch Continuity Camera enumerates first.
        var today = FFmpegCameraProcess.parseDevices("macos", "[AVFoundation indev @ x] AVFoundation video devices:\n"
                + "[AVFoundation indev @ x] [0] iPhone Camera\n[AVFoundation indev @ x] [1] USB Hand Camera\n"
                + "[AVFoundation indev @ x] [2] Capture screen 0\n"
                + "[AVFoundation indev @ x] AVFoundation audio devices:\n[AVFoundation indev @ x] [0] USB Hand Camera");
        assertEquals(Optional.of(new Device("1", "USB Hand Camera")),
                HandCameraDeviceResolver.resolve(saved("0", "USB Hand Camera", ""), today));
    }

    @Test void missingCameraNeverFallsBackToAnotherDevice() {
        var others = List.of(new Device("0", "FaceTime HD Camera"), new Device("1", "OBS Virtual Camera"));
        assertEquals(Optional.empty(), HandCameraDeviceResolver.resolve(saved("0", "USB Hand Camera", ""), others));
        assertEquals(Optional.empty(), HandCameraDeviceResolver.resolve(saved("0", "USB Hand Camera", ""), List.of()));
        // A Windows camera chosen by ID among identical names is not replaced by its twin.
        var twin = List.of(new Device("USB Camera", "USB Camera", "@device_pnp_b"));
        assertEquals(Optional.empty(),
                HandCameraDeviceResolver.resolve(saved("@device_pnp_a", "USB Camera", "@device_pnp_a"), twin));
    }

    @Test void windowsCameraIsFoundByUniqueIdThenByUniqueNameRegardlessOfOrder() {
        String output = "[dshow @ x] \"USB Camera\" (video)\n[dshow @ x]   Alternative name \"@device_pnp_b\"\n"
                + "[dshow @ x] \"Microphone (USB Camera)\" (audio)\n[dshow @ x]   Alternative name \"@device_cm_mic\"\n"
                + "[dshow @ x] \"USB Camera\" (video)\n[dshow @ x]   Alternative name \"@device_pnp_a\"\n"
                + "[dshow @ x] \"Integrated Camera\" (video)\n[dshow @ x]   Alternative name \"@device_pnp_c\"\n";
        var devices = FFmpegCameraProcess.parseDevices("windows", output);
        // Identical names are addressed by ID because DirectShow would otherwise open the first one.
        assertEquals(List.of(new Device("@device_pnp_b", "USB Camera", "@device_pnp_b"),
                new Device("@device_pnp_a", "USB Camera", "@device_pnp_a"),
                new Device("Integrated Camera", "Integrated Camera", "@device_pnp_c")), devices);
        assertEquals("@device_pnp_a", HandCameraDeviceResolver.resolve(
                saved("@device_pnp_a", "USB Camera", "@device_pnp_a"), devices).orElseThrow().value());
        // The same unique camera moved to another USB port gets a new ID but keeps its name.
        assertEquals("Integrated Camera", HandCameraDeviceResolver.resolve(
                saved("Integrated Camera", "Integrated Camera", "@device_pnp_old"), devices).orElseThrow().value());
        // Identical names without a matching ID or value cannot be told apart.
        assertEquals(Optional.empty(),
                HandCameraDeviceResolver.resolve(saved("USB Camera", "USB Camera", ""), devices));
    }

    @Test void dshowDevicesThatAreBothAudioAndVideoAreListed() {
        assertEquals(List.of(new Device("Capture Card", "Capture Card", "@device_sw_x")),
                FFmpegCameraProcess.parseDevices("windows",
                        "[dshow @ x] \"Capture Card\" (audio, video)\n[dshow @ x]   Alternative name \"@device_sw_x\"\n"
                                + "[dshow @ x] \"Line In\" (audio)\n[dshow @ x]   Alternative name \"@device_cm_y\"\n"));
    }

    @Test void identicalMacNamesFallBackToThePreviousIndexOnly() {
        var twins = List.of(new Device("0", "USB Camera"), new Device("1", "USB Camera"));
        assertEquals("1", HandCameraDeviceResolver.resolve(saved("1", "USB Camera", ""), twins).orElseThrow().value());
        assertEquals(Optional.empty(), HandCameraDeviceResolver.resolve(saved("2", "USB Camera", ""), twins));
    }

    @Test void launcherKeepsTheRememberedCameraUntilTheOperatorChoosesAnother() {
        var current = HandCameraDeviceResolver.saved(saved("0", "USB Hand Camera", ""));
        assertEquals("USB Hand Camera", current.displayName());
        // Saving without Refresh keeps the identity instead of saving the displayed name as an index.
        assertSame(current, HandCameraDeviceResolver.select(" USB Hand Camera ", Arrays.asList(null, current)));
        // After Refresh, the list entry with today's index is selected and saved.
        var today = List.of(new Device("0", "iPhone Camera"), new Device("1", "USB Hand Camera"));
        Device refreshed = HandCameraDeviceResolver.refreshed(current, today);
        assertEquals(new Device("1", "USB Hand Camera"), refreshed);
        assertEquals(refreshed, HandCameraDeviceResolver.select("USB Hand Camera", List.of(today.get(0), today.get(1), current)));
        assertEquals(new Device("0", "iPhone Camera"), HandCameraDeviceResolver.select("iPhone Camera", List.of(refreshed, today.get(0), today.get(1), current)));
        // A camera that is not connected stays selected as remembered; nothing else is picked.
        assertSame(current, HandCameraDeviceResolver.refreshed(current, List.of(new Device("0", "iPhone Camera"))));
        // Text typed by hand is an FFmpeg input value without identity, as before; empty clears the selection.
        assertEquals(new Device("2", "", ""), HandCameraDeviceResolver.select("2", List.of(refreshed, current)));
        assertEquals(new Device("", "", ""), HandCameraDeviceResolver.select("  ", List.of(current)));
    }

    @Test void launcherSavesTheSelectedOneOfIdenticallyNamedCameras() {
        var first = new Device("@device_pnp_a", "USB Camera", "@device_pnp_a");
        var second = new Device("@device_pnp_b", "USB Camera", "@device_pnp_b");
        assertSame(second, HandCameraDeviceResolver.select("USB Camera", List.of(second, first, second)));
    }

    @Test void legacyConfigurationWithoutIdentityKeepsItsValue() {
        var legacy = HandCameraDeviceResolver.saved(new HandCameraSettings(true, "0", 640, 480, 30, false));
        assertEquals("0", legacy.displayName());
        assertEquals(new Device("0", "FaceTime HD Camera"),
                HandCameraDeviceResolver.refreshed(legacy, List.of(new Device("0", "FaceTime HD Camera"))));
        assertSame(legacy, HandCameraDeviceResolver.refreshed(legacy, List.of(new Device("1", "Other"))));
    }
}
