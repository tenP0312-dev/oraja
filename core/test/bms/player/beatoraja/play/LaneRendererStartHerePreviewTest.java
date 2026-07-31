package bms.player.beatoraja.play;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaneRendererStartHerePreviewTest {
    @Test
    void anchorsAtLaneTopWithoutLaneCover() {
        Rectangle lane = new Rectangle(10f, 20f, 30f, 100f);

        assertEquals(
                120f,
                LaneRenderer.startHerePreviewTop(lane, false, 0f, false, 0f)
        );
    }

    @Test
    void anchorsImmediatelyBelowLaneCoverWithLift() {
        Rectangle lane = new Rectangle(10f, 20f, 30f, 100f);

        assertEquals(
                80f,
                LaneRenderer.startHerePreviewTop(lane, true, 0.2f, true, 0.5f)
        );
    }

    @Test
    void clampsInvalidLiftAndLaneCoverRates() {
        Rectangle lane = new Rectangle(10f, 20f, 30f, 100f);

        assertEquals(
                120f,
                LaneRenderer.startHerePreviewTop(lane, true, -1f, true, -1f)
        );
        assertEquals(
                120f,
                LaneRenderer.startHerePreviewTop(lane, true, 2f, true, 2f)
        );
    }

    @Test
    void usesEachSkinsActualNoteThicknessAndOffsets() {
        Rectangle lane = new Rectangle(10f, 20f, 30f, 100f);
        Rectangle thin = new Rectangle();
        Rectangle thick = new Rectangle();

        assertTrue(LaneRenderer.setStartHerePreviewDestination(
                thin, lane, 4f, 1f, 0f, 2f, 0f,
                false, 0f, false, 0f
        ));
        assertTrue(LaneRenderer.setStartHerePreviewDestination(
                thick, lane, 12f, 1f, 0f, 2f, 0f,
                false, 0f, false, 0f
        ));

        assertEquals(11f, thin.x);
        assertEquals(32f, thin.width);
        assertEquals(4f, thin.height);
        assertEquals(12f, thick.height);
        assertEquals(120f, thin.y + thin.height);
        assertEquals(120f, thick.y + thick.height);
    }

    @Test
    void rejectsInvisibleSkinGeometry() {
        Rectangle lane = new Rectangle(10f, 20f, 30f, 100f);
        Rectangle destination = new Rectangle();

        assertFalse(LaneRenderer.setStartHerePreviewDestination(
                destination, lane, 4f, 0f, 0f, -30f, 0f,
                false, 0f, false, 0f
        ));
        assertFalse(LaneRenderer.setStartHerePreviewDestination(
                destination, lane, 4f, 0f, 0f, 0f, -4f,
                false, 0f, false, 0f
        ));
    }
}
