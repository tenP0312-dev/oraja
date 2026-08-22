package bms.player.beatoraja.play;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaneRendererStartHerePreviewTest {
	@Test
	void previewExitRewindsTheTimelineCursorToTheChartStart() {
		assertEquals(0, LaneRenderer.chartStartTimelinePosition());
	}

	@Test
	void noteThicknessGrowsFromTheChartAnchorInsteadOfShiftingItsCenter() {
		assertEquals(105f, LaneRenderer.noteDestinationY(100f, 5f));
	}

    @Test
    void previewFadesOutAndBackInOverExactlyOneSecond() {
        assertEquals(0.65f, LaneRenderer.startHerePreviewAlpha(0L), 0.0001f);
        assertEquals(1.0f, LaneRenderer.startHerePreviewAlpha(500L), 0.0001f);
        assertEquals(0.65f, LaneRenderer.startHerePreviewAlpha(1000L), 0.0001f);
        assertEquals(0.825f, LaneRenderer.startHerePreviewAlpha(1250L), 0.0001f);
    }
    @Test
    void showsFromPreloadThroughReadyAndStopsAtPlay() {
        assertTrue(LaneRenderer.showsStartHerePreview(
                BMSPlayer.STATE_PRELOAD,
                true
        ));
        assertTrue(LaneRenderer.showsStartHerePreview(
                BMSPlayer.STATE_READY,
                true
        ));
        assertFalse(LaneRenderer.showsStartHerePreview(
                BMSPlayer.STATE_PLAY,
                true
        ));
        assertFalse(LaneRenderer.showsStartHerePreview(
                BMSPlayer.STATE_PRELOAD,
                false
        ));
    }

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
                88f,
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

    @Test
    void computesGreenNumberBeforeStaticPreviewReturns() {
        assertEquals(800, LaneRenderer.startHerePreviewDuration(150.0, 1.0, 2.0f, false, 0.0f, false, 0.0f));
        assertEquals(400, LaneRenderer.startHerePreviewDuration(150.0, 1.0, 2.0f, true, 0.5f, false, 0.0f));
        assertEquals(480, LaneRenderer.startHerePreviewDuration(150.0, 1.0, 2.0f, true, 0.5f, true, 0.2f));
        assertEquals(400, LaneRenderer.startHerePreviewDuration(150.0, 2.0, 2.0f, false, 0.0f, false, 0.0f));
        assertEquals(1, LaneRenderer.startHerePreviewDuration(0.0, 1.0, 2.0f, false, 0.0f, false, 0.0f));
    }

    @Test
    void derivesPersistentFixedDurationFromTheLiveHispeedAndCover() {
        assertEquals(800, LaneRenderer.fixedDurationForHispeed(
                150.0, 2.0f, false, 0.0f
        ));
        assertEquals(400, LaneRenderer.fixedDurationForHispeed(
                150.0, 2.0f, true, 0.5f
        ));
        assertEquals(1, LaneRenderer.fixedDurationForHispeed(
                0.0, 2.0f, true, 0.5f
        ));
        assertEquals(1, LaneRenderer.fixedDurationForHispeed(
                150.0, 2.0f, true, 1.0f
        ));
	}

}
