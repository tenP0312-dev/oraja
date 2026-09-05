package bms.player.beatoraja.skin;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinTextGradientTest {
	@Test
	void requiresBothColorsAndLeavesLegacyTextDisabledByDefault() {
		TestSkinText text = new TestSkinText();
		assertFalse(text.gradientEnabled());

		text.setGradientTopColor(Color.WHITE);
		assertFalse(text.gradientEnabled());

		text.setGradientBottomColor(Color.BLACK);
		assertTrue(text.gradientEnabled());
	}

	@Test
	void interpolatesAndClampsColorsByVertexHeight() {
		float[] vertices = new float[20];
		vertices[1] = -5f;
		vertices[6] = 2.5f;
		vertices[11] = 10f;
		vertices[16] = 15f;

		Skin.SkinObjectRenderer.applyVerticalGradient(vertices, vertices.length,
				new Color(0f, 0f, 1f, 0.5f), new Color(1f, 0f, 0f, 1f), 0f, 10f);

		assertPackedColor(vertices[2], 0f, 0f, 1f, 0.5f);
		assertPackedColor(vertices[7], 0.25f, 0f, 0.75f, 0.625f);
		assertPackedColor(vertices[12], 1f, 0f, 0f, 1f);
		assertPackedColor(vertices[17], 1f, 0f, 0f, 1f);
	}

	private static void assertPackedColor(float packed, float r, float g, float b, float a) {
		Color actual = new Color();
		Color.abgr8888ToColor(actual, packed);
		assertEquals(r, actual.r, 2f / 255f);
		assertEquals(g, actual.g, 2f / 255f);
		assertEquals(b, actual.b, 2f / 255f);
		assertEquals(a, actual.a, 2f / 255f);
	}

	private static final class TestSkinText extends SkinText {
		private TestSkinText() {
			super(-1);
		}

		private boolean gradientEnabled() {
			return hasGradient();
		}

		@Override
		public void prepareFont(String text) {
		}

		@Override
		protected void prepareText(String text) {
		}

		@Override
		public void draw(Skin.SkinObjectRenderer sprite, float offsetX, float offsetY) {
		}

		@Override
		public void draw(Skin.SkinObjectRenderer sprite) {
		}

		@Override
		public void dispose() {
		}
	}
}
