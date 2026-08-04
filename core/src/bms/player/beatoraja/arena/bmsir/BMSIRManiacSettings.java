package bms.player.beatoraja.arena.bmsir;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * Persisted LR2 MANIAC OPTIONS and Double Battle settings.
 *
 * The object is deliberately self-contained so a play can snapshot it before
 * Arena/course guards temporarily disable the feature.
 */
public final class BMSIRManiacSettings {
    public static final int ALGORITHM_VERSION = 1;

    public static final String RANDOM_LINK_OFF = "off";
    public static final String RANDOM_LINK_SYNC = "sync";
    public static final String RANDOM_LINK_SYMMETRY = "symmetry";

    public enum RankingClass {
        NORMAL,
        MANIAC_STANDARD,
        EXTRA,
        ADD_NOTES,
        ADD_LONGNOTES,
        DOUBLE_BATTLE,
        LOCAL_ONLY
    }

    private int hiddenSudden1P;
    private int hiddenSudden2P;
    private int extraMode;
    private int addNotes;
    private int addLongNotes;
    private int addMines;
    private int acceleration;
    private int softLanding;
    private int earthquake;
    private int tornado;
    private int superLoop;
    private int gambol;
    private int character;
    private int heartbeat;
    private int loudness;
    private int nabeatsu;
    private int sinCurve;
    private int wave;
    private int spiral;
    private int sideJump;
    private boolean doubleBattle;
    private boolean autoScratch;
    private String randomLink = RANDOM_LINK_OFF;
    private boolean warnDoubleBattleOnDp = true;
    private Long generationSeedOverride;

    public BMSIRManiacSettings() {
    }

    public BMSIRManiacSettings(BMSIRManiacSettings source) {
        if (source == null) {
            return;
        }
        hiddenSudden1P = source.hiddenSudden1P;
        hiddenSudden2P = source.hiddenSudden2P;
        extraMode = source.extraMode;
        addNotes = source.addNotes;
        addLongNotes = source.addLongNotes;
        addMines = source.addMines;
        acceleration = source.acceleration;
        softLanding = source.softLanding;
        earthquake = source.earthquake;
        tornado = source.tornado;
        superLoop = source.superLoop;
        gambol = source.gambol;
        character = source.character;
        heartbeat = source.heartbeat;
        loudness = source.loudness;
        nabeatsu = source.nabeatsu;
        sinCurve = source.sinCurve;
        wave = source.wave;
        spiral = source.spiral;
        sideJump = source.sideJump;
        doubleBattle = source.doubleBattle;
        autoScratch = source.autoScratch;
        randomLink = source.randomLink;
        warnDoubleBattleOnDp = source.warnDoubleBattleOnDp;
        generationSeedOverride = source.generationSeedOverride;
        validate();
    }

    public BMSIRManiacSettings validate() {
        hiddenSudden1P = clamp(hiddenSudden1P, 0, 3);
        hiddenSudden2P = clamp(hiddenSudden2P, 0, 3);
        extraMode = clamp(extraMode, 0, 3);
        addNotes = percent(addNotes);
        addLongNotes = percent(addLongNotes);
        addMines = percent(addMines);
        acceleration = clamp(acceleration, 0, 3);
        softLanding = clamp(softLanding, 0, 2);
        earthquake = percent(earthquake);
        tornado = percent(tornado);
        superLoop = percent(superLoop);
        gambol = clamp(gambol, 0, 2);
        character = percent(character);
        heartbeat = percent(heartbeat);
        loudness = percent(loudness);
        nabeatsu = percent(nabeatsu);
        sinCurve = percent(sinCurve);
        wave = percent(wave);
        spiral = percent(spiral);
        sideJump = percent(sideJump);
        if (!doubleBattle) {
            autoScratch = false;
        }
        setRandomLink(randomLink);
        return this;
    }

    public static BMSIRManiacSettings fromCanonicalOptions(String canonical) {
        if (canonical == null || canonical.isBlank()) return null;
        Map<String, String> values = new HashMap<>();
        for (String entry : canonical.split(",")) {
            int separator = entry.indexOf('=');
            if (separator > 0) {
                values.put(entry.substring(0, separator), entry.substring(separator + 1));
            }
        }
        if (integer(values, "algorithm") != ALGORITHM_VERSION) return null;
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setHiddenSudden1P(integer(values, "hs1"));
        settings.setHiddenSudden2P(integer(values, "hs2"));
        settings.setExtraMode(integer(values, "extra"));
        settings.setAddNotes(integer(values, "notes"));
        settings.setAddLongNotes(integer(values, "long"));
        settings.setAddMines(integer(values, "mines"));
        settings.setAcceleration(integer(values, "accel"));
        settings.setSoftLanding(integer(values, "soft"));
        settings.setEarthquake(integer(values, "earthquake"));
        settings.setTornado(integer(values, "tornado"));
        settings.setSuperLoop(integer(values, "superloop"));
        settings.setGambol(integer(values, "gambol"));
        settings.setCharacter(integer(values, "char"));
        settings.setHeartbeat(integer(values, "heartbeat"));
        settings.setLoudness(integer(values, "loudness"));
        settings.setNabeatsu(integer(values, "nabeatsu"));
        settings.setSinCurve(integer(values, "sin"));
        settings.setWave(integer(values, "wave"));
        settings.setSpiral(integer(values, "spiral"));
        settings.setSideJump(integer(values, "sidejump"));
        settings.setDoubleBattle(Boolean.parseBoolean(values.getOrDefault("db", "false")));
        settings.setAutoScratch(Boolean.parseBoolean(values.getOrDefault("autoscratch", "false")));
        settings.setRandomLink(values.get("link"));
        String seed = values.getOrDefault("seed", "fixed");
        if (!"fixed".equals(seed)) {
            try {
                settings.setGenerationSeedOverride(Long.parseUnsignedLong(seed));
            } catch (NumberFormatException error) {
                return null;
            }
        }
        settings.validate();
        return settings.isActive() ? settings : null;
    }

    private static int integer(Map<String, String> values, String key) {
        try {
            return Integer.parseInt(values.getOrDefault(key, "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public boolean isActive() {
        return chartTransformCount() > 0
                || hasStandardEffect()
                || doubleBattle;
    }

    public boolean hasStandardEffect() {
        return hiddenSudden1P > 0 || hiddenSudden2P > 0
                || addMines > 0 || acceleration > 0 || softLanding > 0
                || earthquake > 0 || tornado > 0 || superLoop > 0
                || gambol > 0 || character > 0 || heartbeat > 0
                || loudness > 0 || nabeatsu > 0 || sinCurve > 0
                || wave > 0 || spiral > 0 || sideJump > 0;
    }

    public int chartTransformCount() {
        int count = 0;
        if (extraMode > 0) count++;
        if (addNotes > 0) count++;
        if (addLongNotes > 0) count++;
        return count;
    }

    public RankingClass rankingClass() {
        if (!isActive()) {
            return RankingClass.NORMAL;
        }
        if (generationSeedOverride != null || loudness > 0) {
            return RankingClass.LOCAL_ONLY;
        }
        if (doubleBattle && chartTransformCount() > 0) {
            return RankingClass.LOCAL_ONLY;
        }
        if (chartTransformCount() > 1) {
            return RankingClass.LOCAL_ONLY;
        }
        if (doubleBattle) return RankingClass.DOUBLE_BATTLE;
        if (extraMode > 0) return RankingClass.EXTRA;
        if (addNotes > 0) return RankingClass.ADD_NOTES;
        if (addLongNotes > 0) return RankingClass.ADD_LONGNOTES;
        return RankingClass.MANIAC_STANDARD;
    }

    public String virtualChartId(String baseSha256) {
        if (rankingClass() == RankingClass.NORMAL
                || rankingClass() == RankingClass.MANIAC_STANDARD
                || rankingClass() == RankingClass.LOCAL_ONLY) {
            return null;
        }
        String source = normalizedBaseHash(baseSha256) + ':' + rankingKey()
                + ":v" + ALGORITHM_VERSION;
        return "bmsir-maniac-v" + ALGORITHM_VERSION + '-' + sha256(source);
    }

    /** Local score key. It distinguishes every structured option set. */
    public String storageChartId(String baseSha256) {
        if (!isActive()) return normalizedBaseHash(baseSha256);
        return "bmsir-maniac-score-v" + ALGORITHM_VERSION + '-'
                + sha256(normalizedBaseHash(baseSha256) + ':' + canonicalOptions());
    }

    public String canonicalOptions() {
        validate();
        String base = String.join(",",
                "hs1=" + hiddenSudden1P,
                "hs2=" + hiddenSudden2P,
                "extra=" + extraMode,
                "notes=" + addNotes,
                "long=" + addLongNotes,
                "mines=" + addMines,
                "accel=" + acceleration,
                "soft=" + softLanding,
                "earthquake=" + earthquake,
                "tornado=" + tornado,
                "superloop=" + superLoop,
                "gambol=" + gambol,
                "char=" + character,
                "heartbeat=" + heartbeat,
                "loudness=" + loudness,
                "nabeatsu=" + nabeatsu,
                "sin=" + sinCurve,
                "wave=" + wave,
                "spiral=" + spiral,
                "sidejump=" + sideJump,
                "db=" + doubleBattle,
                "link=" + randomLink
        );
        if (autoScratch) {
            base += ",autoscratch=true";
        }
        return base + "," + String.join(",",
                "seed=" + (generationSeedOverride == null ? "fixed" : generationSeedOverride),
                "algorithm=" + ALGORITHM_VERSION
        );
    }

    public long generationSeed(String baseSha256) {
        if (generationSeedOverride != null) {
            return generationSeedOverride;
        }
        String digest = sha256(normalizedBaseHash(baseSha256) + ':' + rankingKey()
                + ":v" + ALGORITHM_VERSION);
        return Long.parseUnsignedLong(digest.substring(0, 16), 16);
    }

    public String rankingKey() {
        return switch (rankingClass()) {
            case NORMAL -> "normal";
            case MANIAC_STANDARD -> "maniac-standard";
            case EXTRA -> "extra-" + extraMode;
            case ADD_NOTES -> "add-notes-" + addNotes;
            case ADD_LONGNOTES -> "add-longnotes-" + addLongNotes;
            case DOUBLE_BATTLE -> autoScratch
                    ? "double-battle-autoscratch"
                    : "double-battle";
            case LOCAL_ONLY -> "local-only";
        };
    }

    public String compactOptionText() {
        List<String> values = new ArrayList<>();
        if (doubleBattle) values.add("DB" + randomLinkSuffix()
                + (autoScratch ? " AUTO SCRATCH" : ""));
        if (extraMode > 0) values.add("EXTRA Lv" + extraMode);
        if (addNotes > 0) values.add("ADD " + addNotes + "%");
        if (addLongNotes > 0) values.add("LN " + addLongNotes + "%");
        if (addMines > 0) values.add("MINE " + addMines + "%");
        if (gambol > 0) values.add("GAMBOL Lv" + gambol);
        if (softLanding > 0) values.add("SOFT Lv" + softLanding);
        appendPercent(values, "TORNADO", tornado);
        appendPercent(values, "EARTHQUAKE", earthquake);
        appendPercent(values, "SUPERLOOP", superLoop);
        appendPercent(values, "CHAR", character);
        appendPercent(values, "HEARTBEAT", heartbeat);
        appendPercent(values, "LOUDNESS", loudness);
        appendPercent(values, "NABEATSU", nabeatsu);
        appendPercent(values, "SIN", sinCurve);
        appendPercent(values, "WAVE", wave);
        appendPercent(values, "SPIRAL", spiral);
        appendPercent(values, "SIDEJUMP", sideJump);
        if (acceleration > 0) values.add("ACCEL " + acceleration);
        if (hiddenSudden1P > 0 || hiddenSudden2P > 0) values.add("HID/SUD");
        if (values.size() > 3) {
            return "MANIAC x" + values.size();
        }
        return String.join(" / ", values);
    }

    public String detailedOptionText() {
        BMSIRManiacSettings copy = new BMSIRManiacSettings(this);
        List<String> values = new ArrayList<>();
        String compact = copy.compactOptionText();
        if (!compact.startsWith("MANIAC x")) {
            return compact;
        }
        // Temporarily collect without the compact threshold.
        if (doubleBattle) values.add("DB" + randomLinkSuffix()
                + (autoScratch ? " AUTO SCRATCH" : ""));
        if (extraMode > 0) values.add("EXTRA Lv" + extraMode);
        if (addNotes > 0) values.add("ADD NOTES " + addNotes + "%");
        if (addLongNotes > 0) values.add("ADD LONGNOTES " + addLongNotes + "%");
        if (addMines > 0) values.add("MINE " + addMines + "%");
        if (gambol > 0) values.add("GAMBOL Lv" + gambol);
        if (softLanding > 0) values.add("SOFTLANDING Lv" + softLanding);
        appendPercent(values, "TORNADO", tornado);
        appendPercent(values, "EARTHQUAKE", earthquake);
        appendPercent(values, "SUPERLOOP", superLoop);
        appendPercent(values, "CHAR", character);
        appendPercent(values, "HEARTBEAT", heartbeat);
        appendPercent(values, "LOUDNESS", loudness);
        appendPercent(values, "NABEATSU", nabeatsu);
        appendPercent(values, "SIN CURVE", sinCurve);
        appendPercent(values, "WAVE", wave);
        appendPercent(values, "SPIRAL", spiral);
        appendPercent(values, "SIDEJUMP", sideJump);
        if (acceleration > 0) values.add("ACCELERATION " + acceleration);
        if (hiddenSudden1P > 0) values.add("HIDDEN/SUDDEN 1P=" + hiddenSudden1P);
        if (hiddenSudden2P > 0) values.add("HIDDEN/SUDDEN 2P=" + hiddenSudden2P);
        return String.join(" / ", values);
    }

    private String randomLinkSuffix() {
        return switch (randomLink) {
            case RANDOM_LINK_SYNC -> " SYNC";
            case RANDOM_LINK_SYMMETRY -> " SYMMETRY";
            default -> "";
        };
    }

    private static void appendPercent(List<String> target, String name, int value) {
        if (value > 0) target.add(name + ' ' + value + "%");
    }

    private static int percent(int value) {
        if (value <= 0) return 0;
        return clamp(((value + 5) / 10) * 10, 10, 100);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String normalizedBaseHash(String hash) {
        return hash == null ? "" : hash.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte item : digest) output.append(String.format("%02x", item));
            return output.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public int getHiddenSudden1P() { return hiddenSudden1P; }
    public void setHiddenSudden1P(int value) { hiddenSudden1P = value; validate(); }
    public int getHiddenSudden2P() { return hiddenSudden2P; }
    public void setHiddenSudden2P(int value) { hiddenSudden2P = value; validate(); }
    public int getExtraMode() { return extraMode; }
    public void setExtraMode(int value) { extraMode = value; validate(); }
    public int getAddNotes() { return addNotes; }
    public void setAddNotes(int value) { addNotes = value; validate(); }
    public int getAddLongNotes() { return addLongNotes; }
    public void setAddLongNotes(int value) { addLongNotes = value; validate(); }
    public int getAddMines() { return addMines; }
    public void setAddMines(int value) { addMines = value; validate(); }
    public int getAcceleration() { return acceleration; }
    public void setAcceleration(int value) { acceleration = value; validate(); }
    public int getSoftLanding() { return softLanding; }
    public void setSoftLanding(int value) { softLanding = value; validate(); }
    public int getEarthquake() { return earthquake; }
    public void setEarthquake(int value) { earthquake = value; validate(); }
    public int getTornado() { return tornado; }
    public void setTornado(int value) { tornado = value; validate(); }
    public int getSuperLoop() { return superLoop; }
    public void setSuperLoop(int value) { superLoop = value; validate(); }
    public int getGambol() { return gambol; }
    public void setGambol(int value) { gambol = value; validate(); }
    public int getCharacter() { return character; }
    public void setCharacter(int value) { character = value; validate(); }
    public int getHeartbeat() { return heartbeat; }
    public void setHeartbeat(int value) { heartbeat = value; validate(); }
    public int getLoudness() { return loudness; }
    public void setLoudness(int value) { loudness = value; validate(); }
    public int getNabeatsu() { return nabeatsu; }
    public void setNabeatsu(int value) { nabeatsu = value; validate(); }
    public int getSinCurve() { return sinCurve; }
    public void setSinCurve(int value) { sinCurve = value; validate(); }
    public int getWave() { return wave; }
    public void setWave(int value) { wave = value; validate(); }
    public int getSpiral() { return spiral; }
    public void setSpiral(int value) { spiral = value; validate(); }
    public int getSideJump() { return sideJump; }
    public void setSideJump(int value) { sideJump = value; validate(); }
    public boolean isDoubleBattle() { return doubleBattle; }
    public void setDoubleBattle(boolean value) {
        doubleBattle = value;
        if (!value) autoScratch = false;
    }
    public boolean isAutoScratch() { return autoScratch; }
    public void setAutoScratch(boolean value) { autoScratch = doubleBattle && value; }
    public String getRandomLink() { validate(); return randomLink; }
    public void setRandomLink(String value) {
        String normalized = value == null ? RANDOM_LINK_OFF : value.toLowerCase(Locale.ROOT);
        randomLink = RANDOM_LINK_SYNC.equals(normalized) || RANDOM_LINK_SYMMETRY.equals(normalized)
                ? normalized : RANDOM_LINK_OFF;
    }
    public boolean isWarnDoubleBattleOnDp() { return warnDoubleBattleOnDp; }
    public void setWarnDoubleBattleOnDp(boolean value) { warnDoubleBattleOnDp = value; }
    public Long getGenerationSeedOverride() { return generationSeedOverride; }
    public void setGenerationSeedOverride(Long value) { generationSeedOverride = value; }
}
