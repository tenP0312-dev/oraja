package bms.player.beatoraja.skin.scene.render;

import de.damios.guacamole.gdx.graphics.ShaderCompatibilityHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneShaderTest {
	@Test
	void macOsCoreConversionRewritesAllLegacyQualifiers() {
		String vertex = ShaderCompatibilityHelper.toVert150(SceneShader.VERTEX);
		String fragment = ShaderCompatibilityHelper.toFrag150(SceneShader.FRAGMENT);

		assertFalse(vertex.contains("attribute "));
		assertFalse(vertex.contains("varying "));
		assertTrue(vertex.contains("in vec3 a_position;"));
		assertTrue(vertex.contains("out vec4 v_color;"));

		assertFalse(fragment.contains("varying "));
		assertFalse(fragment.contains("gl_FragColor"));
		assertTrue(fragment.contains("in vec4 v_color;"));
		assertTrue(fragment.contains("out vec4 fragColor;"));
	}
}
