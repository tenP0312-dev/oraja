package bms.player.beatoraja.skin.scene.render;

import bms.player.beatoraja.skin.scene.RenderCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskStackTest {
	@Test
	void consecutiveUnmaskedCommandsReuseMaskState() throws Exception {
		MaskStack.MaskState state = new MaskStack.MaskState();
		assertTrue(state.matches(command(0)));
		assertTrue(state.matches(command(0)));
	}

	@Test
	void identicalMasksReuseStateUntilTheirGeometryChanges() throws Exception {
		MaskStack.MaskState state = new MaskStack.MaskState();
		RenderCommand first = command(1);
		first.maskWorld[0][0] = 1;
		first.maskRect[0][2] = 100;
		state.set(first);

		RenderCommand same = command(1);
		same.maskWorld[0][0] = 1;
		same.maskRect[0][2] = 100;
		assertTrue(state.matches(same));
		same.maskRect[0][2] = 101;
		assertFalse(state.matches(same));
	}

	private static RenderCommand command(int maskCount) throws Exception {
		Constructor<RenderCommand> constructor = RenderCommand.class.getDeclaredConstructor(int.class);
		constructor.setAccessible(true);
		RenderCommand command = constructor.newInstance(8);
		command.maskCount = maskCount;
		return command;
	}
}
