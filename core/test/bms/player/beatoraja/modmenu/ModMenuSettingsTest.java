package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.PlayerConfig;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ModMenuSettingsTest {
    @TempDir Path directory;

    @Test
    void oldMissingNullAndSparseSettingsRetainLegacyItems() {
        for (String json : new String[] {
                "{}", "{\"modMenuSettings\":null}",
                "{\"modMenuSettings\":{\"itemEnabled\":null}}",
                "{\"modMenuSettings\":{\"itemEnabled\":{\"song_manager\":false,\"rate_modifier\":null}}}"
        }) {
            PlayerConfig player = new Json().fromJson(PlayerConfig.class, json);
            player.validate();
            var settings = player.getModMenuSettings();
            assertEquals(15, settings.itemEnabled.size());
            for (ModMenuItem item : ModMenuItem.values()) {
                assertEquals(item != ModMenuItem.SONG_MANAGER || !json.contains("false"), settings.isEnabled(item));
            }
        }
    }

    @Test
    void actualPlayerFileRoundTripPreservesEveryChoiceAndUnknownIds() throws Exception {
        PlayerConfig player = PlayerConfig.validatePlayerConfig("player1", new PlayerConfig());
        Files.createDirectories(directory.resolve("player1"));
        var settings = player.getModMenuSettings();
        settings.itemEnabled = new LinkedHashMap<>();
        for (ModMenuItem item : ModMenuItem.values()) settings.itemEnabled.put(item.id, false);
        settings.itemEnabled.put("future_item", false);
        settings.filterEnabled = false;
        settings.songManagerSortLastPlayed = true;
        assertTrue(PlayerConfig.writeChecked(directory.toString(), player, PlayerConfig.getConfigJson(player)));

        PlayerConfig restored = PlayerConfig.readPlayerConfig(directory.toString(), "player1");
        var loaded = restored.getModMenuSettings();
        assertFalse(loaded.filterEnabled);
        assertTrue(loaded.songManagerSortLastPlayed);
        assertEquals(Boolean.FALSE, loaded.itemEnabled.get("future_item"));
        for (ModMenuItem item : ModMenuItem.values()) {
            assertFalse(loaded.isSelected(item));
            assertTrue(loaded.isEnabled(item));
        }
        loaded.filterEnabled = true;
        for (ModMenuItem item : ModMenuItem.values()) assertFalse(loaded.isEnabled(item));
    }

    @Test
    void splitSettingsWindowsInheritTheFormerMiscSettingsChoice() {
        for (boolean miscEnabled : new boolean[] {true, false}) {
            PlayerConfig player = new Json().fromJson(PlayerConfig.class,
                    "{\"modMenuSettings\":{\"itemEnabled\":{\"misc_settings\":" + miscEnabled
                            + ",\"legacy_arena_menu\":false}}}");
            player.validate();
            var settings = player.getModMenuSettings();
            for (ModMenuItem item : new ModMenuItem[] {
                    ModMenuItem.PLAY_SETTINGS, ModMenuItem.SOUND_SETTINGS, ModMenuItem.DISPLAY_SETTINGS}) {
                assertEquals(miscEnabled, settings.isEnabled(item), item.id);
            }
            assertTrue(settings.isEnabled(ModMenuItem.SONG_MANAGER));
            // Retired ids stay in the file so an older client reading it keeps the user's choice.
            assertEquals(Boolean.valueOf(miscEnabled), settings.itemEnabled.get("misc_settings"));
            assertEquals(Boolean.FALSE, settings.itemEnabled.get("legacy_arena_menu"));
        }
        PlayerConfig explicit = new Json().fromJson(PlayerConfig.class,
                "{\"modMenuSettings\":{\"itemEnabled\":{\"misc_settings\":false,\"sound_settings\":true}}}");
        explicit.validate();
        assertTrue(explicit.getModMenuSettings().isEnabled(ModMenuItem.SOUND_SETTINGS));
        assertFalse(explicit.getModMenuSettings().isEnabled(ModMenuItem.PLAY_SETTINGS));
    }

    @Test
    void filterStopsManiacWithoutLosingSavedChoices() {
        PlayerConfig player = new PlayerConfig();
        player.getBmsirManiacSettings().setAddNotes(50);
        player.getBmsirManiacSettings().setCourseGauge(true);
        player.getModMenuSettings().itemEnabled.put(ModMenuItem.MANIAC_OPTIONS.id, false);
        assertFalse(player.getEffectiveBmsirManiacSettings().isActive());
        assertFalse(player.getEffectiveBmsirManiacSettings().isCourseGauge());
        assertEquals(50, player.getBmsirManiacSettings().getAddNotes());
        player.getModMenuSettings().filterEnabled = false;
        assertEquals(50, player.getEffectiveBmsirManiacSettings().getAddNotes());
        player.getModMenuSettings().filterEnabled = true;
        assertFalse(player.getEffectiveBmsirManiacSettings().isActive());
    }

    @Test
    void songSortRestoresBeforeFirstRenderAndSwitchesPlayers() {
        PlayerConfig first = new PlayerConfig();
        first.getModMenuSettings().songManagerSortLastPlayed = true;
        SongManagerMenu.restoreLastPlayedSort(first);
        assertTrue(SongManagerMenu.isLastPlayedSortEnabled());
        first.getModMenuSettings().itemEnabled.put(ModMenuItem.SONG_MANAGER.id, false);
        assertFalse(SongManagerMenu.isLastPlayedSortEnabled());
        assertTrue(first.getModMenuSettings().songManagerSortLastPlayed);
        first.getModMenuSettings().filterEnabled = false;
        assertTrue(SongManagerMenu.isLastPlayedSortEnabled());
        SongManagerMenu.restoreLastPlayedSort(new PlayerConfig());
        assertFalse(SongManagerMenu.isLastPlayedSortEnabled());
    }
}
