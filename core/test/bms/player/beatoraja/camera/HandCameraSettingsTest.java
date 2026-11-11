package bms.player.beatoraja.camera;

import bms.player.beatoraja.Config;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandCameraSettingsTest {
    @Test void defaultsAreOptInAndRequireAnExplicitDevice() {
        var settings = HandCameraSettings.from(new Config());
        assertFalse(settings.enabled());
        assertFalse(settings.usable());
        assertEquals(640, settings.width());
        assertEquals(480, settings.height());
        assertEquals(30, settings.fps());
        assertFalse(settings.mirror());
        assertFalse(new HandCameraSettings(true, " ", 640, 480, 30, false).usable());
    }
    @Test void handEditedSettingsCannotAllocateUnboundedFrames() {
        var settings = new HandCameraSettings(true, " 0 ", Integer.MAX_VALUE, Integer.MAX_VALUE, 1000, true);
        assertEquals(1920, settings.width());
        assertEquals(1080, settings.height());
        assertEquals(60, settings.fps());
        assertEquals(6_220_800, settings.frameBytes());
        assertEquals("0", settings.device());
        var small = new HandCameraSettings(true, null, -1, -1, -1, false);
        assertEquals(160, small.width());
        assertEquals(120, small.height());
        assertEquals(1, small.fps());
    }
    @Test void settingsPersistAndOldConfigurationRemainsOff() {
        Json json = new Json();
        Config config = json.fromJson(Config.class,
                "{handCameraEnabled:true,handCameraDevice:0,handCameraWidth:1280,handCameraHeight:720,handCameraFps:60,handCameraMirror:true}");
        var expected = HandCameraSettings.from(config);
        assertEquals(expected, HandCameraSettings.from(json.fromJson(Config.class, json.toJson(config))));
        assertFalse(HandCameraSettings.from(json.fromJson(Config.class, "{}")).usable());
    }
    @Test void rememberedCameraIdentitySurvivesSaveAndOldConfigurationHasNone() {
        Json json = new Json();
        Config config = new Config();
        config.setHandCameraEnabled(true);
        config.setHandCameraDevice("1");
        config.setHandCameraDeviceName(" FaceTime HD Camera ");
        config.setHandCameraDeviceId("@device_pnp_\\\\?\\usb#vid_0001");
        config.validate();
        var reloaded = HandCameraSettings.from(json.fromJson(Config.class, json.toJson(config)));
        assertEquals("1", reloaded.device());
        assertEquals("FaceTime HD Camera", reloaded.deviceName());
        assertEquals("@device_pnp_\\\\?\\usb#vid_0001", reloaded.deviceId());
        assertTrue(reloaded.remembersIdentity());
        var legacy = HandCameraSettings.from(json.fromJson(Config.class, "{handCameraEnabled:true,handCameraDevice:0}"));
        assertTrue(legacy.usable());
        assertFalse(legacy.remembersIdentity());
        assertEquals("", legacy.deviceName());
    }
    @Test void normalizedTopLeftCropRejectsInvalidAreasAndKeepsPixelsBounded() {
        assertArrayEquals(new int[] {160, 120, 320, 240}, new CameraCrop(.25f, .25f, .5f, .5f).pixels(640, 480));
        assertArrayEquals(new int[] {639, 479, 1, 1}, new CameraCrop(.999f, .999f, .001f, .001f).pixels(640, 480));
        assertFalse(new CameraCrop(Float.NaN, 0, 1, 1).valid());
        assertFalse(new CameraCrop(0, 0, 0, 1).valid());
        assertFalse(new CameraCrop(.9f, 0, .2f, 1).valid());
        assertThrows(IllegalArgumentException.class, () -> new CameraCrop(0, -1, 1, 1).pixels(640, 480));
    }
}
