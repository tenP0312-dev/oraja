package bms.player.beatoraja;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamCompatibilityFixesTest {

	@Test
	void newAudioConfigsDefaultTo256SimultaneousSources() {
		assertEquals(256, new AudioConfig().getDeviceSimultaneousSources());
	}

	@Test
	void jpegDetectionIsCaseInsensitiveAndDoesNotMatchOtherImages() {
		assertTrue(PixmapResourcePool.isJpeg("bga/example.jpg"));
		assertTrue(PixmapResourcePool.isJpeg("bga/example.JPEG"));
		assertFalse(PixmapResourcePool.isJpeg("bga/example.png"));
	}

	@Test
	void targetProgressCalculationDoesNotOverflowIntMultiplication() {
		ScoreDataProperty property = new ScoreDataProperty();
		property.setTargetScore(3_000_000, 2_500_000, 2_000_000);

		ScoreData current = new ScoreData();
		current.setNotes(2_000_000);
		property.update(current, 1_500_000);

		assertEquals(2_250_000, property.getNowBestScore());
		assertEquals(1_875_000, property.getNowRivalScore());

		property.refreshTargetScoreProgress(1_500_000);
		assertEquals(2_250_000, property.getNowBestScore());
		assertEquals(1_875_000, property.getNowRivalScore());
	}
}
