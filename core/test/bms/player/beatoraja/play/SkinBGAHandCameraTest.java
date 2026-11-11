package bms.player.beatoraja.play;

import bms.player.beatoraja.camera.CameraCrop;
import bms.player.beatoraja.skin.SkinCamera;
import bms.player.beatoraja.skin.SkinObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinBGAHandCameraTest {
    @Test void aSkinWithItsOwnCameraPartKeepsTheBgaInTheBgaFrame() {
        SkinBGA bga = new SkinBGA(0);
        // Skins without a camera part (most skins) may show the camera in the BGA frame.
        assertFalse(SkinBGA.hasCameraPart(new SkinObject[] {bga}));
        assertFalse(SkinBGA.hasCameraPart(new SkinObject[0]));
        assertFalse(SkinBGA.hasCameraPart(null));
        // e.g. IIDX33 "BGA + Camera": the skin's camera strip is the only camera display.
        assertTrue(SkinBGA.hasCameraPart(new SkinObject[] {bga, new SkinCamera(new CameraCrop(0, .3f, 1, .4f))}));
    }
}
