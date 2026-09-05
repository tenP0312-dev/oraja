package bms.player.beatoraja.skin.scene.render;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import de.damios.guacamole.gdx.graphics.ShaderCompatibilityHelper;

/** Core-profile compatible shader for focal projective scene vertices. */
final class SceneShader {
	static final String VERTEX = """
			// Skin Scene projective vertex shader.
			attribute vec3 a_position;
			attribute vec4 a_color;
			attribute vec2 a_texCoord0;
			attribute vec4 a_colorAdd;
			attribute vec3 a_hsl;
			uniform mat4 u_combinedTransform;
			uniform vec4 u_camera;
			uniform float u_focalProjection;
			varying vec4 v_color;
			varying vec2 v_texCoords;
			varying vec4 v_colorAdd;
			varying vec3 v_hsl;
			void main() {
			    float clipW = 1.0;
			    vec2 numerator = a_position.xy;
			    if (u_focalProjection > 0.5) {
			        clipW = (a_position.z - u_camera.z) / u_camera.w;
			        numerator = vec2(
			            u_camera.x * clipW + a_position.x - u_camera.x,
			            u_camera.y * clipW + a_position.y - u_camera.y);
			    }
			    gl_Position = u_combinedTransform * vec4(numerator, 0.0, clipW);
			    v_color = a_color;
			    v_texCoords = a_texCoord0;
			    v_colorAdd = a_colorAdd;
			    v_hsl = a_hsl;
			}
			""";

	static final String FRAGMENT = """
			#ifdef GL_ES
			precision mediump float;
			#endif
			varying vec4 v_color;
			varying vec2 v_texCoords;
			varying vec4 v_colorAdd;
			varying vec3 v_hsl;
			uniform sampler2D u_texture;
			uniform float u_inputPremultipliedAlpha;
			uniform float u_outputPremultipliedAlpha;
			uniform float u_invert;

			vec3 rgbToHsl(vec3 color) {
			    float maximum = max(max(color.r, color.g), color.b);
			    float minimum = min(min(color.r, color.g), color.b);
			    float lightness = (maximum + minimum) * 0.5;
			    float delta = maximum - minimum;
			    if (delta < 0.000001) return vec3(0.0, 0.0, lightness);
			    float saturation = delta / (1.0 - abs(2.0 * lightness - 1.0));
			    float hue;
			    if (maximum == color.r) hue = mod((color.g - color.b) / delta, 6.0);
			    else if (maximum == color.g) hue = (color.b - color.r) / delta + 2.0;
			    else hue = (color.r - color.g) / delta + 4.0;
			    return vec3(fract(hue / 6.0), clamp(saturation, 0.0, 1.0), lightness);
			}

			float hueToRgb(float p, float q, float t) {
			    t = fract(t);
			    if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
			    if (t < 1.0 / 2.0) return q;
			    if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
			    return p;
			}

			vec3 hslToRgb(vec3 hsl) {
			    if (hsl.y < 0.000001) return vec3(hsl.z);
			    float q = hsl.z < 0.5 ? hsl.z * (1.0 + hsl.y)
			                              : hsl.z + hsl.y - hsl.z * hsl.y;
			    float p = 2.0 * hsl.z - q;
			    return vec3(hueToRgb(p, q, hsl.x + 1.0 / 3.0),
			                hueToRgb(p, q, hsl.x),
			                hueToRgb(p, q, hsl.x - 1.0 / 3.0));
			}

			void main() {
			    vec4 texel = texture2D(u_texture, v_texCoords);
			    vec3 sourceRgb = u_inputPremultipliedAlpha > 0.5 && texel.a > 0.000001
			                     ? texel.rgb / texel.a : texel.rgb;
			    vec3 hsl = rgbToHsl(sourceRgb);
			    hsl = vec3(fract(hsl.x + v_hsl.x),
			               clamp(hsl.y + v_hsl.y, 0.0, 1.0),
			               clamp(hsl.z + v_hsl.z, 0.0, 1.0));
			    vec4 color = clamp(vec4(hslToRgb(hsl), texel.a) * v_color + v_colorAdd,
			                       0.0, 1.0);
			    if (u_invert > 0.5) color.rgb = vec3(color.a);
			    else if (u_outputPremultipliedAlpha > 0.5) color.rgb *= color.a;
			    gl_FragColor = color;
			}
			""";

	private final ShaderProgram program;

	SceneShader() {
		program = ShaderCompatibilityHelper.fromString(VERTEX, FRAGMENT);
		if (!program.isCompiled()) {
			String log = program.getLog();
			program.dispose();
			throw new IllegalStateException("Skin scene shader compilation failed: " + log);
		}
	}

	ShaderProgram program() {
		return program;
	}

	void dispose() {
		program.dispose();
	}
}
