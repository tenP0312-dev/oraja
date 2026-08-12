package bms.player.beatoraja.controller;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Lwjgl3ControllerTest {

	@Test
	void mapsFirstHatCardinalAndDiagonalDirectionsToButtons29Through32() {
		assertTrue(Lwjgl3Controller.isFirstHatDirection(GLFW.GLFW_HAT_LEFT, 28));
		assertTrue(Lwjgl3Controller.isFirstHatDirection(GLFW.GLFW_HAT_UP, 29));
		assertTrue(Lwjgl3Controller.isFirstHatDirection(GLFW.GLFW_HAT_RIGHT_DOWN, 30));
		assertTrue(Lwjgl3Controller.isFirstHatDirection(GLFW.GLFW_HAT_RIGHT_DOWN, 31));
		assertFalse(Lwjgl3Controller.isFirstHatDirection(GLFW.GLFW_HAT_UP, 28));
		assertFalse(Lwjgl3Controller.isFirstHatDirection(GLFW.GLFW_HAT_LEFT, 32));
	}
}
