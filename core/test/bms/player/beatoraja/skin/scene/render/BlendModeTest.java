package bms.player.beatoraja.skin.scene.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlendModeTest {
	private GL20 original;
	private final List<Invocation> invocations = new ArrayList<>();

	@BeforeEach
	void installRecordingGl() {
		original = Gdx.gl;
		Gdx.gl = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(),
				new Class<?>[] {GL20.class}, (proxy, method, args) -> {
					invocations.add(new Invocation(method.getName(), args == null ? new Object[0] : args));
					Class<?> type = method.getReturnType();
					if (type == boolean.class) return false;
					if (type == int.class) return 0;
					if (type == long.class) return 0L;
					if (type == float.class) return 0f;
					return null;
				});
	}

	@AfterEach
	void restoreGl() {
		Gdx.gl = original;
	}

	@Test
	void normalAndPremultipliedNormalUseDifferentSourceFactors() {
		ProjectiveBatch.applyBlend(BlendMode.NORMAL, false);
		assertCalled("glBlendFuncSeparate", GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
				GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
		invocations.clear();
		ProjectiveBatch.applyBlend(BlendMode.NORMAL, true);
		assertCalled("glBlendFuncSeparate", GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA,
				GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
	}

	@Test
	void advancedModesInstallTheirNamedEquationsAndRestoreLegacyState() {
		ProjectiveBatch.applyBlend(BlendMode.SUBTRACT, false);
		assertCalled("glBlendEquationSeparate", GL20.GL_FUNC_REVERSE_SUBTRACT, GL20.GL_FUNC_ADD);

		invocations.clear();
		ProjectiveBatch.applyBlend(BlendMode.MULTIPLY, false);
		assertCalled("glBlendFuncSeparate", GL20.GL_DST_COLOR, GL20.GL_ONE_MINUS_SRC_ALPHA,
				GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);

		invocations.clear();
		ProjectiveBatch.applyBlend(BlendMode.SCREEN, false);
		assertCalled("glBlendFuncSeparate", GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_COLOR,
				GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);

		invocations.clear();
		ProjectiveBatch.applyBlend(BlendMode.INVERT, false);
		assertCalled("glBlendFuncSeparate", GL20.GL_ONE_MINUS_DST_COLOR,
				GL20.GL_ONE_MINUS_SRC_COLOR, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);

		invocations.clear();
		ProjectiveBatch.restoreBlend();
		assertCalled("glBlendEquationSeparate", GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
		assertCalled("glBlendFuncSeparate", GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
				GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
	}

	@Test
	void parsesEveryV1Name() {
		for (BlendMode mode : BlendMode.values()) {
			assertEquals(mode, BlendMode.parse(mode.name().toLowerCase()));
		}
	}

	private void assertCalled(String name, Object... arguments) {
		assertTrue(invocations.stream().anyMatch(invocation -> invocation.name.equals(name)
				&& Arrays.equals(invocation.arguments, arguments)),
				() -> "Expected " + name + Arrays.toString(arguments) + " in " + invocations);
	}

	private record Invocation(String name, Object[] arguments) {
	}
}
