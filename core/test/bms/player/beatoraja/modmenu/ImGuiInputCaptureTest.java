package bms.player.beatoraja.modmenu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ImGuiInputCaptureTest {
    @AfterEach
    void resetCapture() {
        ImGuiInputCapture.resetForTest();
    }

    @Test
    void focusedTextInputCapturesKeyboard() {
        ImGuiInputCapture.updateFromImGui(false, true, false, false, false);

        assertTrue(ImGuiInputCapture.isKeyboardCaptured());
        assertFalse(ImGuiInputCapture.isMouseCaptured());
    }

    @Test
    void activeItemCapturesKeyboardAndMouseInTheSameFrame() {
        ImGuiInputCapture.updateFromImGui(false, false, false, false, true);

        assertTrue(ImGuiInputCapture.isKeyboardCaptured());
        assertTrue(ImGuiInputCapture.isMouseCaptured());
    }

    @Test
    void externalImeEditorCapturesBothInputPaths() {
        ImGuiInputCapture.setExternalEditorOpen(true);

        assertTrue(ImGuiInputCapture.isKeyboardCaptured());
        assertTrue(ImGuiInputCapture.isMouseCaptured());
    }
}
