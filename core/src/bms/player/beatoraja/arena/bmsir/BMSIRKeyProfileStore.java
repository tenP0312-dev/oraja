package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.PlayModeConfig;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.system.RobustFile;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.SerializationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Named snapshots of the per-mode {@link PlayModeConfig} values (key assignment
 * and play settings), shared by every player.
 *
 * The profiles live in their own file in the player root so that
 * config_player.json rewrites by this or any other body never touch them. A
 * damaged file is reported, never replaced with defaults.
 */
public final class BMSIRKeyProfileStore {
    static final String FILE_NAME = "bmsir_keyprofiles.json";
    static final int MAX_NAME_LENGTH = 40;
    /** Mode ids as used by {@link PlayerConfig#getPlayConfig(int)}. */
    static final int[] MODE_IDS = {5, 7, 10, 14, 9, 25, 50};

    private static final Logger logger =
            LoggerFactory.getLogger(BMSIRKeyProfileStore.class);

    private BMSIRKeyProfileStore() {
    }

    public enum Scope {
        /** Key assignment and play settings of every mode in the profile. */
        ALL,
        /** Key assignment and play settings of one mode. */
        CURRENT_MODE,
        /** Keyboard, controller and MIDI assignment of every mode. */
        KEYS_ONLY
    }

    public static final class Profile {
        private String name = "";
        private ModeEntry[] modes = new ModeEntry[0];

        public String getName() {
            return name;
        }
    }

    static final class ModeEntry {
        private int mode;
        private PlayModeConfig config;
    }

    static final class Data {
        private int schemaVersion = 1;
        private Profile[] profiles = new Profile[0];
    }

    /** Returns the stored profiles; a missing file is an empty list. */
    public static List<Profile> load(String playerPath) throws IOException {
        Path path = path(playerPath);
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        Data data = RobustFile.load(path, bytes -> parse(path, bytes));
        List<Profile> profiles = new ArrayList<>();
        if (data.profiles != null) {
            for (Profile profile : data.profiles) {
                if (profile != null && profile.name != null && !profile.name.isBlank()) {
                    if (profile.modes == null) {
                        profile.modes = new ModeEntry[0];
                    }
                    profiles.add(profile);
                }
            }
        }
        return profiles;
    }

    public static boolean save(String playerPath, List<Profile> profiles) {
        Path path = path(playerPath);
        try {
            Data data = new Data();
            data.profiles = profiles.toArray(new Profile[0]);
            String serialized = configuredJson().prettyPrint(data);
            RobustFile.write(path, serialized.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | SerializationException | NullPointerException e) {
            logger.error(
                    "BMS-IR key profiles could not be saved to {}: {}",
                    path,
                    e.getLocalizedMessage()
            );
            return false;
        }
    }

    /** Captures every mode of the player; the result shares nothing with it. */
    public static Profile snapshot(String name, PlayerConfig player) {
        Profile profile = new Profile();
        profile.name = normalizeName(name);
        List<ModeEntry> entries = new ArrayList<>();
        for (int modeId : MODE_IDS) {
            ModeEntry entry = new ModeEntry();
            entry.mode = modeId;
            entry.config = copy(player.getPlayConfig(modeId));
            entries.add(entry);
        }
        profile.modes = entries.toArray(new ModeEntry[0]);
        return profile;
    }

    /**
     * Applies the profile to the player and returns the number of modes
     * changed. Modes missing from the profile keep their current values.
     *
     * @param currentModeId mode id used by {@link Scope#CURRENT_MODE}
     */
    public static int apply(Profile profile, PlayerConfig player, Scope scope, int currentModeId) {
        int applied = 0;
        for (ModeEntry entry : profile.modes) {
            if (entry == null || entry.config == null || !isKnownMode(entry.mode)) {
                continue;
            }
            if (scope == Scope.CURRENT_MODE && entry.mode != currentModeId) {
                continue;
            }
            PlayModeConfig incoming = copy(entry.config);
            if (scope == Scope.KEYS_ONLY) {
                PlayModeConfig target = copy(player.getPlayConfig(entry.mode));
                target.setKeyboardConfig(incoming.getKeyboardConfig());
                target.setController(incoming.getController());
                target.setMidiConfig(incoming.getMidiConfig());
                incoming = target;
            }
            incoming.validate(keyCount(entry.mode));
            setMode(player, entry.mode, incoming);
            applied++;
        }
        return applied;
    }

    /** Trims and bounds a user-entered name; blank stays blank. */
    public static String normalizeName(String name) {
        String trimmed = name == null ? "" : name.strip();
        return trimmed.length() > MAX_NAME_LENGTH
                ? trimmed.substring(0, MAX_NAME_LENGTH)
                : trimmed;
    }

    public static boolean nameTaken(List<Profile> profiles, String name, Profile except) {
        for (Profile profile : profiles) {
            if (profile != except && profile.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Independent copy under a new name. */
    public static Profile duplicate(Profile source, String name) {
        Profile profile = new Profile();
        profile.name = normalizeName(name);
        profile.modes = new ModeEntry[source.modes.length];
        for (int index = 0; index < source.modes.length; index++) {
            ModeEntry from = source.modes[index];
            ModeEntry entry = new ModeEntry();
            entry.mode = from.mode;
            entry.config = copy(from.config);
            profile.modes[index] = entry;
        }
        return profile;
    }

    public static void rename(Profile profile, String name) {
        profile.name = normalizeName(name);
    }

    static Path path(String playerPath) {
        return Paths.get(playerPath, FILE_NAME);
    }

    private static boolean isKnownMode(int modeId) {
        for (int known : MODE_IDS) {
            if (known == modeId) {
                return true;
            }
        }
        return false;
    }

    private static int keyCount(int modeId) {
        return switch (modeId) {
            case 5 -> 7;
            case 10 -> 14;
            case 14 -> 18;
            case 25 -> 26;
            case 50 -> 52;
            default -> 9;
        };
    }

    private static void setMode(PlayerConfig player, int modeId, PlayModeConfig config) {
        switch (modeId) {
            case 5 -> player.setMode5(config);
            case 7 -> player.setMode7(config);
            case 10 -> player.setMode10(config);
            case 14 -> player.setMode14(config);
            case 9 -> player.setMode9(config);
            case 25 -> player.setMode24(config);
            case 50 -> player.setMode24double(config);
            default -> throw new IllegalArgumentException("mode " + modeId);
        }
    }

    private static PlayModeConfig copy(PlayModeConfig source) {
        Json json = configuredJson();
        return json.fromJson(PlayModeConfig.class, json.toJson(source));
    }

    private static Data parse(Path path, byte[] bytes) throws ParseException {
        try {
            Data data = configuredJson().fromJson(
                    Data.class,
                    new String(bytes, StandardCharsets.UTF_8)
            );
            if (data == null) {
                throw new SerializationException("empty key profiles");
            }
            return data;
        } catch (SerializationException e) {
            throw new ParseException(
                    "BMS-IR key profiles parse failed - Path: "
                            + path
                            + ", Log: "
                            + e.getLocalizedMessage(),
                    0
            );
        }
    }

    private static Json configuredJson() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        json.setOutputType(JsonWriter.OutputType.json);
        json.setUsePrototypes(false);
        return json;
    }
}
