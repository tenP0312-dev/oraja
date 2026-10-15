package bms.player.beatoraja.skin;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinObjectClipTest {
    private static final Rectangle FRAME = new Rectangle(100, 0, 400, 200);

    @Test void anImageInsideTheFrameIsUntouched() {
        Rectangle rect = new Rectangle(100, 0, 400, 200);
        float[] uv = {0, 0, 1, 1};
        assertTrue(SkinObject.clipToFrame(rect, uv, FRAME));
        assertEquals(new Rectangle(100, 0, 400, 200), rect);
        assertArrayEquals(new float[] {0, 0, 1, 1}, uv, 1e-6f);
    }

    @Test void anImageShiftedUpIsCutAtTheFrameTopWithItsSourcePixels() {
        // Offset Y -73% moved the IIDX33 strip up over the BGA; only the part still in the frame is drawn.
        Rectangle rect = new Rectangle(100, 146, 400, 200);
        float[] uv = {0, 0, 1, 1};
        assertTrue(SkinObject.clipToFrame(rect, uv, FRAME));
        assertEquals(new Rectangle(100, 146, 400, 54), rect);
        // y is up and v starts at the image top: the visible bottom 27% of the frame shows the image's bottom 27%.
        assertArrayEquals(new float[] {0, .73f, 1, 1}, uv, 1e-6f);
    }

    @Test void mirroredImagesAreCutOnTheDisplayedSide() {
        Rectangle rect = new Rectangle(300, 0, 400, 200);
        float[] uv = {1, 0, 0, 1}; // flipped horizontally
        assertTrue(SkinObject.clipToFrame(rect, uv, FRAME));
        assertEquals(new Rectangle(300, 0, 200, 200), rect);
        // The displayed left half stays; it shows source u from 1 down to .5.
        assertArrayEquals(new float[] {1, 0, .5f, 1}, uv, 1e-6f);
    }

    @Test void anImageEntirelyOutsideTheFrameDrawsNothing() {
        assertFalse(SkinObject.clipToFrame(new Rectangle(100, 200, 400, 200), new float[] {0, 0, 1, 1}, FRAME));
        assertFalse(SkinObject.clipToFrame(new Rectangle(-400, 0, 400, 200), new float[] {0, 0, 1, 1}, FRAME));
        assertFalse(SkinObject.clipToFrame(new Rectangle(100, 0, 0, 200), new float[] {0, 0, 1, 1}, FRAME));
    }
}
