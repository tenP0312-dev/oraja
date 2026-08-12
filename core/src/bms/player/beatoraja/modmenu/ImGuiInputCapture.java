package bms.player.beatoraja.modmenu;

/**
 * Shares ImGui input ownership with the high-frequency BMS input poller.
 *
 * <p>libGDX dispatches GLFW callbacks before the render loop, while gameplay
 * keyboard state is also polled from a separate thread. Keeping the current
 * capture state here lets both paths discard input that belongs to ImGui.</p>
 */
public final class ImGuiInputCapture {
    private static volatile boolean keyboardCaptured;
    private static volatile boolean mouseCaptured;
    private static volatile boolean externalEditorOpen;

    private ImGuiInputCapture() {
    }

    public static void updateFromImGui(
            boolean wantCaptureKeyboard,
            boolean wantTextInput,
            boolean wantCaptureMouse,
            boolean anyItemFocused,
            boolean anyItemActive
    ) {
        keyboardCaptured = wantCaptureKeyboard
                || wantTextInput
                || anyItemFocused
                || anyItemActive;
        mouseCaptured = wantCaptureMouse || anyItemActive;
    }

    public static boolean isKeyboardCaptured() {
        return keyboardCaptured || externalEditorOpen;
    }

    public static boolean isMouseCaptured() {
        return mouseCaptured || externalEditorOpen;
    }

    public static void setExternalEditorOpen(boolean open) {
        externalEditorOpen = open;
    }

    static void resetForTest() {
        keyboardCaptured = false;
        mouseCaptured = false;
        externalEditorOpen = false;
    }
}
