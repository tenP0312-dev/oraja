package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.arena.bmsir.BMSIRKeyProfileStore.Profile;
import bms.player.beatoraja.arena.bmsir.BMSIRKeyProfileStore.Scope;

import com.badlogic.gdx.Input.Keys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRKeyProfileStoreTest {
    @TempDir
    Path directory;

    @Test
    void missingFileIsEmptyAndRoundTripKeepsEveryMode() throws Exception {
        String root = directory.toString();
        assertTrue(BMSIRKeyProfileStore.load(root).isEmpty());

        PlayerConfig player = new PlayerConfig();
        player.getMode7().getKeyboardConfig().getKeyAssign()[0] = Keys.A;
        player.getMode14().getPlayconfig().setHispeed(3.5f);
        List<Profile> profiles = new ArrayList<>();
        profiles.add(BMSIRKeyProfileStore.snapshot("  Home  ", player));
        assertTrue(BMSIRKeyProfileStore.save(root, profiles));

        List<Profile> loaded = BMSIRKeyProfileStore.load(root);
        assertEquals(1, loaded.size());
        assertEquals("Home", loaded.get(0).getName());

        PlayerConfig other = new PlayerConfig();
        int applied = BMSIRKeyProfileStore.apply(loaded.get(0), other, Scope.ALL, 7);
        assertEquals(BMSIRKeyProfileStore.MODE_IDS.length, applied);
        assertEquals(Keys.A, other.getMode7().getKeyboardConfig().getKeyAssign()[0]);
        assertEquals(3.5f, other.getMode14().getPlayconfig().getHispeed());
    }

    @Test
    void snapshotIsIndependentFromLaterEdits() {
        PlayerConfig player = new PlayerConfig();
        Profile profile = BMSIRKeyProfileStore.snapshot("p", player);
        player.getMode7().getKeyboardConfig().getKeyAssign()[0] = Keys.A;

        PlayerConfig other = new PlayerConfig();
        BMSIRKeyProfileStore.apply(profile, other, Scope.ALL, 7);
        assertEquals(Keys.Z, other.getMode7().getKeyboardConfig().getKeyAssign()[0]);
    }

    @Test
    void currentModeScopeChangesOnlyThatMode() {
        PlayerConfig source = new PlayerConfig();
        source.getMode7().getKeyboardConfig().getKeyAssign()[0] = Keys.A;
        source.getMode14().getKeyboardConfig().getKeyAssign()[0] = Keys.A;
        Profile profile = BMSIRKeyProfileStore.snapshot("p", source);

        PlayerConfig target = new PlayerConfig();
        assertEquals(1, BMSIRKeyProfileStore.apply(profile, target, Scope.CURRENT_MODE, 7));
        assertEquals(Keys.A, target.getMode7().getKeyboardConfig().getKeyAssign()[0]);
        assertEquals(Keys.Z, target.getMode14().getKeyboardConfig().getKeyAssign()[0]);
    }

    @Test
    void keysOnlyScopeLeavesPlaySettingsUntouched() {
        PlayerConfig source = new PlayerConfig();
        source.getMode7().getKeyboardConfig().getKeyAssign()[0] = Keys.A;
        source.getMode7().getPlayconfig().setHispeed(4.0f);
        Profile profile = BMSIRKeyProfileStore.snapshot("p", source);

        PlayerConfig target = new PlayerConfig();
        float before = target.getMode7().getPlayconfig().getHispeed();
        BMSIRKeyProfileStore.apply(profile, target, Scope.KEYS_ONLY, 7);
        assertEquals(Keys.A, target.getMode7().getKeyboardConfig().getKeyAssign()[0]);
        assertEquals(before, target.getMode7().getPlayconfig().getHispeed());
    }

    @Test
    void duplicateAndRenameAreIndependent() {
        PlayerConfig player = new PlayerConfig();
        Profile original = BMSIRKeyProfileStore.snapshot("a", player);
        Profile copy = BMSIRKeyProfileStore.duplicate(original, "b");
        BMSIRKeyProfileStore.rename(original, "c");
        assertEquals("b", copy.getName());
        assertEquals("c", original.getName());

        List<Profile> profiles = List.of(original, copy);
        assertTrue(BMSIRKeyProfileStore.nameTaken(profiles, "B", original));
        assertFalse(BMSIRKeyProfileStore.nameTaken(profiles, "b", copy));
        assertEquals(
                BMSIRKeyProfileStore.MAX_NAME_LENGTH,
                BMSIRKeyProfileStore.normalizeName("x".repeat(100)).length()
        );
    }

    @Test
    void damagedFileIsReportedAndLeftUntouched() throws Exception {
        Path file = BMSIRKeyProfileStore.path(directory.toString());
        byte[] damaged = "{ not json".getBytes(StandardCharsets.UTF_8);
        Files.write(file, damaged);

        assertThrows(IOException.class, () -> BMSIRKeyProfileStore.load(directory.toString()));
        assertArrayEquals(damaged, Files.readAllBytes(file));
    }

    @Test
    void unknownModesAndMissingEntriesAreSkipped() throws Exception {
        Path file = BMSIRKeyProfileStore.path(directory.toString());
        Files.writeString(file, """
                {"schemaVersion":1,"profiles":[
                  {"name":"sparse","modes":[{"mode":99},{"mode":7}]},
                  {"name":""}
                ]}
                """);
        List<Profile> loaded = BMSIRKeyProfileStore.load(directory.toString());
        assertEquals(1, loaded.size());

        PlayerConfig target = new PlayerConfig();
        assertEquals(0, BMSIRKeyProfileStore.apply(loaded.get(0), target, Scope.ALL, 7));
    }
}
