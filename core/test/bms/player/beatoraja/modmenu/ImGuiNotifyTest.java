package bms.player.beatoraja.modmenu;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImGuiNotifyTest {

    @Test
    void disablingInfoKeepsWarningsAndErrorsVisible() throws Exception {
        Field field = ImGuiNotify.class.getDeclaredField("notifications");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ImGuiNotify.Toast> notifications =
                (List<ImGuiNotify.Toast>) field.get(null);
        notifications.clear();
        try {
            ImGuiNotify.setInfoEnabled(false);
            ImGuiNotify.info("hidden");
            ImGuiNotify.warning("visible warning");
            ImGuiNotify.error("visible error");

            assertEquals(2, notifications.size());
            assertEquals(ImGuiNotify.ToastType.Warning, notifications.get(0).getType());
            assertEquals(ImGuiNotify.ToastType.Error, notifications.get(1).getType());
        }
        finally {
            notifications.clear();
            ImGuiNotify.setInfoEnabled(true);
        }
    }
}
