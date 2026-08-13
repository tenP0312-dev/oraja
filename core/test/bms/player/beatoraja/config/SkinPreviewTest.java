package bms.player.beatoraja.config;

import bms.player.beatoraja.skin.SkinType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinPreviewTest {
	@Test
	void recursiveAndResultScreensAreExcludedFromPreview() {
		EnumSet<SkinType> excluded = EnumSet.of(
				SkinType.SKIN_SELECT,
				SkinType.RESULT,
				SkinType.COURSE_RESULT);

		for (SkinType type : SkinType.values()) {
			assertEquals(!excluded.contains(type), SkinConfiguration.supportsPreview(type), type.name());
		}
		assertFalse(SkinConfiguration.supportsPreview(null));
		assertTrue(SkinConfiguration.supportsPreview(SkinType.PLAY_7KEYS));
	}

	@Test
	void previewBufferUsesDestinationSizeAndStaysBounded() {
		assertEquals(640, SkinPreview.bufferDimension(1920, 640));
		assertEquals(360, SkinPreview.bufferDimension(1080, 360));
		assertEquals(1280, SkinPreview.bufferDimension(1280, 4096));
		assertEquals(2048, SkinPreview.bufferDimension(8192, 4096));
		assertEquals(1, SkinPreview.bufferDimension(0, 0));
	}
}
