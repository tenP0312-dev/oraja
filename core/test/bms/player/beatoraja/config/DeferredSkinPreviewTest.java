package bms.player.beatoraja.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeferredSkinPreviewTest {
	@Test void loadingWaitsForACompletePresentedFrameAndRunsOnlyOnce() {
		DeferredSkinPreview load = new DeferredSkinPreview();
		load.request(10);
		assertFalse(load.take(10));
		assertFalse(load.take(11));
		assertTrue(load.take(12));
		assertFalse(load.take(13));
	}

	@Test void rapidEditsReplaceThePendingRequest() {
		DeferredSkinPreview load = new DeferredSkinPreview();
		load.request(10);
		load.request(11);
		assertFalse(load.take(12));
		assertTrue(load.take(13));
		assertFalse(load.take(14));
	}

	@Test void leavingTheScreenOrDisablingPreviewCancelsPendingWork() {
		DeferredSkinPreview load = new DeferredSkinPreview();
		load.request(10);
		load.cancel();
		assertFalse(load.take(12));
		load.request(20);
		assertTrue(load.take(22));
	}
}
