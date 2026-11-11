package bms.player.beatoraja.camera;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.camera.FFmpegCameraProcess.Device;
import com.badlogic.gdx.utils.Json;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandCameraBgaFrameTest {
    private static Config enabled(boolean bgaFrame) {
        Config config = new Config();
        config.setHandCameraEnabled(true);
        config.setHandCameraDevice("camera");
        config.setHandCameraBgaFrame(bgaFrame);
        return config;
    }

    @Test void theBgaFrameOpensTheCameraOnlyWhenThatOptionIsOn() throws Exception {
        for (boolean bgaFrame : new boolean[] {false, true}) {
            AtomicInteger opens = new AtomicInteger();
            CameraCapture capture = new CameraCapture(s -> { opens.incrementAndGet(); return new CameraCaptureTest.Pipe(); });
            try (HandCameraManager manager = new HandCameraManager(enabled(bgaFrame), capture)) {
                manager.beginFrame();
                // No frame yet: the BGA keeps drawing until the camera delivers one.
                assertNull(manager.requestBgaFrameTexture());
                manager.endFrame();
                if (bgaFrame) CameraCaptureTest.until(() -> opens.get() == 1);
                else {
                    Thread.sleep(50);
                    assertEquals(0, opens.get());
                }
                assertFalse(manager.isReceiving());
            }
        }
    }

    @Test void bgaFrameOptionPersistsAndOldConfigurationKeepsTheBga() {
        Json json = new Json();
        Config config = enabled(true);
        assertTrue(json.fromJson(Config.class, json.toJson(config)).isHandCameraBgaFrame());
        assertFalse(json.fromJson(Config.class, "{handCameraEnabled:true,handCameraDevice:0}").isHandCameraBgaFrame());
    }

    @Test void aMenuSelectionIsRememberedWithItsIdentity() {
        Config config = new Config();
        HandCameraDeviceResolver.remember(config, new Device("1", "USB Hand Camera"));
        var settings = HandCameraSettings.from(config);
        assertEquals("1", settings.device());
        assertEquals("USB Hand Camera", settings.deviceName());
        assertTrue(settings.remembersIdentity());
        HandCameraDeviceResolver.remember(config, new Device("@device_pnp_a", "USB Camera", "@device_pnp_a"));
        assertEquals("@device_pnp_a", HandCameraSettings.from(config).deviceId());
    }

    @Test void menuPresetsAlwaysKeepTheCurrentValue() {
        assertEquals(HandCameraPresets.SIZES, HandCameraPresets.sizes(640, 480));
        var custom = HandCameraPresets.sizes(1024, 576);
        assertTrue(custom.contains(new HandCameraPresets.Size(1024, 576)));
        assertEquals(HandCameraPresets.SIZES.size() + 1, custom.size());
        assertEquals(new HandCameraPresets.Size(320, 240), custom.get(0));
        assertEquals(List.of(15, 24, 30, 60), HandCameraPresets.fps(30));
        assertEquals(List.of(15, 24, 25, 30, 60), HandCameraPresets.fps(25));
    }
}
