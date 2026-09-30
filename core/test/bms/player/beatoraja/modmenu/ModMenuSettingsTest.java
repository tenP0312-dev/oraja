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
            assertEquals(14, settings.itemEnabled.size());
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
