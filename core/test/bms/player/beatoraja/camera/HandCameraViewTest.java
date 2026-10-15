package bms.player.beatoraja.camera;

import bms.player.beatoraja.Config;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandCameraViewTest {
    private static final int[] FULL = {0, 0, 640, 480};

    @Test void defaultsShowTheWholeImageInPlace() {
        HandCameraView view = HandCameraView.from(new Config());
        assertEquals(HandCameraView.DEFAULT, view);
        assertArrayEquals(FULL, view.source(FULL, 640, 480, false));
        assertArrayEquals(FULL, view.source(FULL, 640, 480, true));
        assertEquals(0f, view.dx(320));
        assertEquals(0f, view.dy(180), 0f);
    }

    @Test void zoomCropsAroundTheFocusAndNeverLeavesTheImage() {
        assertArrayEquals(new int[] {160, 120, 320, 240}, new HandCameraView(0, 0, 200, 50, 50).source(FULL, 640, 480, false));
        // Focus at the bottom edge: the window stops at the image edge instead of reading outside it.
        assertArrayEquals(new int[] {160, 240, 320, 240}, new HandCameraView(0, 0, 200, 50, 100).source(FULL, 640, 480, false));
        assertArrayEquals(new int[] {0, 0, 160, 120}, new HandCameraView(0, 0, 400, 0, 0).source(FULL, 640, 480, false));
        // The skin's own crop (e.g. IIDX33's 40% strip) is the base; zoom works inside it.
        int[] strip = {0, 144, 640, 192};
        assertArrayEquals(new int[] {160, 192, 320, 96}, new HandCameraView(0, 0, 200, 50, 50).source(strip, 640, 480, false));
    }

    @Test void theSkinsCropWindowMovesAcrossTheWholeImageEvenAt100Percent() {
        // IIDX33 strip: the centre 40% of the height (cropY .3, height .4 of a 480-line image).
        int[] strip = {0, 144, 640, 192};
        assertArrayEquals(strip, HandCameraView.DEFAULT.source(strip, 640, 480, false));
        // 30: 20% of the whole image (96 lines) upwards; it adds to the skin's own crop offset.
        assertArrayEquals(new int[] {0, 48, 640, 192}, new HandCameraView(0, 0, 100, 50, 30).source(strip, 640, 480, false));
        // The window stops at the image edges instead of reading outside the camera image.
        assertArrayEquals(new int[] {0, 0, 640, 192}, new HandCameraView(0, 0, 100, 50, 0).source(strip, 640, 480, false));
        assertArrayEquals(new int[] {0, 288, 640, 192}, new HandCameraView(0, 0, 100, 50, 100).source(strip, 640, 480, false));
        // A narrow skin crop moves sideways too, and zoom shrinks the window around its moved centre.
        int[] narrow = {160, 0, 320, 480};
        assertArrayEquals(new int[] {320, 0, 320, 480}, new HandCameraView(0, 0, 100, 100, 50).source(narrow, 640, 480, false));
        assertArrayEquals(new int[] {400, 120, 160, 240}, new HandCameraView(0, 0, 200, 75, 50).source(narrow, 640, 480, false));
        // A skin showing the whole image has nothing to move until zoomed in.
        assertArrayEquals(FULL, new HandCameraView(0, 0, 100, 0, 100).source(FULL, 640, 480, false));
    }

    @Test void mirroredFocusMovesTheSameWayAsTheDisplayedImage() {
        HandCameraView left = new HandCameraView(0, 0, 200, 0, 50);
        assertEquals(0, left.source(FULL, 640, 480, false)[0]);
        // With mirroring the displayed left edge is the source's right edge.
        assertEquals(320, left.source(FULL, 640, 480, true)[0]);
    }

    @Test void offsetsArePercentOfTheDisplayAndPositiveYMovesDown() {
        HandCameraView view = new HandCameraView(25, 50, 100, 50, 50);
        assertEquals(80f, view.dx(320));
        // The renderer's y axis points up.
        assertEquals(-90f, view.dy(180));
    }

    @Test void handEditedValuesAreClampedAndTheAdjustmentPersists() {
        HandCameraView wild = new HandCameraView(-500, 500, 1, -3, 300);
        assertEquals(new HandCameraView(-100, 100, 100, 0, 100), wild);
        Config config = new Config();
        config.setHandCameraView(new HandCameraView(10, -20, 250, 30, 70));
        Json json = new Json();
        assertEquals(new HandCameraView(10, -20, 250, 30, 70),
                HandCameraView.from(json.fromJson(Config.class, json.toJson(config))));
        Config edited = json.fromJson(Config.class, "{handCameraZoom:9999,handCameraOffsetX:-999}");
        edited.validate();
        assertEquals(400, edited.getHandCameraZoom());
        assertEquals(-100, edited.getHandCameraOffsetX());
        // Older configurations without the fields keep the unadjusted view.
        assertEquals(HandCameraView.DEFAULT, HandCameraView.from(json.fromJson(Config.class, "{}")));
    }
}
