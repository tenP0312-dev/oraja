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
    void idleImguiFrameDoesNotCaptureInput() {
        ImGuiInputCapture.updateFromImGui(false, false, false, false, false);

        assertFalse(ImGuiInputCapture.isKeyboardCaptured());
        assertFalse(ImGuiInputCapture.isMouseCaptured());
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

    @Test
    void completedButtonOrTextEditReleasesKeyboardDespiteRetainedFocus() {
        ImGuiInputCapture.updateFromImGui(true, false, true, true, true);
        assertTrue(ImGuiInputCapture.isKeyboardCaptured());
        ImGuiInputCapture.updateFromImGui(true, false, true, true, false);
        assertFalse(ImGuiInputCapture.isKeyboardCaptured());
        assertTrue(ImGuiInputCapture.isMouseCaptured());
        ImGuiInputCapture.updateFromImGui(true, true, false, true, true);
        assertTrue(ImGuiInputCapture.isKeyboardCaptured());
        ImGuiInputCapture.updateFromImGui(true, false, false, true, false);
        assertFalse(ImGuiInputCapture.isKeyboardCaptured());
    }

    @Test
    void externalEditorRemainsCapturedUntilItCloses() {
        ImGuiInputCapture.setExternalEditorOpen(true);
        ImGuiInputCapture.updateFromImGui(true, false, false, true, false);
        assertTrue(ImGuiInputCapture.isKeyboardCaptured());
        ImGuiInputCapture.setExternalEditorOpen(false);
        assertFalse(ImGuiInputCapture.isKeyboardCaptured());
    }

    @Test
    void existingMenusKeepTheirKeyboardNavigationCapture() {
        ImGuiInputCapture.updateFromImGui(true, false, false, false, false, true);
        assertTrue(ImGuiInputCapture.isKeyboardCaptured());
        ImGuiInputCapture.updateFromImGui(false, false, false, true, false, true);
        assertTrue(ImGuiInputCapture.isKeyboardCaptured());
        ImGuiInputCapture.updateFromImGui(false, false, false, false, false, true);
        assertFalse(ImGuiInputCapture.isKeyboardCaptured());
    }
}
