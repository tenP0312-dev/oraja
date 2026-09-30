package bms.player.beatoraja.modmenu;

import imgui.type.ImBoolean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModMenuStateTest {
    @Test
    void disableClosesAllWindowsAndReenableDoesNotReopen() throws Exception {
        Map<ModMenuItem, String> windows = Map.ofEntries(
                Map.entry(ModMenuItem.RATE_MODIFIER, "SHOW_FREQ_PLUS"),
                Map.entry(ModMenuItem.RANDOM_TRAINER, "SHOW_RANDOM_TRAINER"),
                Map.entry(ModMenuItem.JUDGE_TRAINER, "SHOW_JUDGE_TRAINER"),
                Map.entry(ModMenuItem.SKIN_CONFIGURATION, "SHOW_SKIN_MENU"),
                Map.entry(ModMenuItem.SKIN_WIDGET_MANAGER, "SHOW_SKIN_WIDGET_MANAGER"),
                Map.entry(ModMenuItem.SONG_MANAGER, "SHOW_SONG_MANAGER"),
                Map.entry(ModMenuItem.DOWNLOAD_TASKS, "SHOW_DOWNLOAD_MENU"),
                Map.entry(ModMenuItem.PERFORMANCE_MONITOR, "SHOW_PERFORMANCE_MONITOR"),
                Map.entry(ModMenuItem.MISC_SETTINGS, "SHOW_MISC_SETTING"),
                Map.entry(ModMenuItem.LEGACY_ARENA_MENU, "SHOW_ARENA_MENU"),
                Map.entry(ModMenuItem.LEGACY_ARENA_GRAPH, "SHOW_GRAPH_MENU"),
                Map.entry(ModMenuItem.MANIAC_OPTIONS, "SHOW_MANIAC_OPTIONS"));
        for (var entry : windows.entrySet()) {
            Field field = ImGuiRenderer.class.getDeclaredField(entry.getValue());
            field.setAccessible(true);
            ImBoolean flag = (ImBoolean) field.get(null);
            flag.set(true);
            ImGuiRenderer.applyModMenuItemState(entry.getKey(), false);
            assertFalse(flag.get(), entry.getKey().id);
            ImGuiRenderer.applyModMenuItemState(entry.getKey(), true);
            assertFalse(flag.get(), entry.getKey().id);
        }
    }

    @Test
    void disableStopsTrainerStateIncludingTheirEditors() throws Exception {
        FreqTrainerMenu.FREQ_TRAINER_ENABLED.set(true);
        RandomTrainer.setActive(true);
        JudgeTrainer.setActive(true);
        setEditorFlag(RandomTrainerMenu.class, "RANDOM_TRAINER_ENABLED", true);
        setEditorFlag(JudgeTrainerMenu.class, "OVERRIDE_CHART_JUDGE", true);
        ImGuiRenderer.applyModMenuItemState(ModMenuItem.RATE_MODIFIER, false);
        ImGuiRenderer.applyModMenuItemState(ModMenuItem.RANDOM_TRAINER, false);
        ImGuiRenderer.applyModMenuItemState(ModMenuItem.JUDGE_TRAINER, false);
        assertFalse(FreqTrainerMenu.isFreqTrainerEnabled());
        assertFalse(RandomTrainer.isActive());
        assertFalse(JudgeTrainer.isActive());
        assertFalse(editorFlag(RandomTrainerMenu.class, "RANDOM_TRAINER_ENABLED").get());
        assertFalse(editorFlag(JudgeTrainerMenu.class, "OVERRIDE_CHART_JUDGE").get());
    }

    private static void setEditorFlag(Class<?> type, String name, boolean value) throws Exception {
        editorFlag(type, name).set(value);
    }

    private static ImBoolean editorFlag(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (ImBoolean) field.get(null);
    }
}
