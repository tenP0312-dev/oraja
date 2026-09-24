package bms.player.beatoraja.ir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.SerializationException;

import bms.model.BMSDecoder;
import bms.player.beatoraja.MainController.IRStatus;
import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.system.RobustFile;

/** Disk-backed cache for successful IR rankings. */
public final class PersistentRankingDataStore {
    private static final Logger logger = LoggerFactory.getLogger(PersistentRankingDataStore.class);
    private static final int FORMAT_VERSION = 1;
    private static final String FILE_NAME = "ir-ranking-cache.json";
    private static final int MAX_ENTRIES = 20_000;

    private final Json json;

    public PersistentRankingDataStore() {
		json = new Json();
        json.setIgnoreUnknownFields(true);
        json.setOutputType(JsonWriter.OutputType.json);
        json.setUsePrototypes(false);
    }

    public synchronized IRScoreData[] load(
            String playerPath, String playerId, IRStatus ir, IRRankingContext context, Object target) {
        Path path = cachePath(playerPath, playerId);
        String key = cacheKey(ir, context, target);
        if (key == null || !Files.isRegularFile(path)) return null;
        try {
            if (Files.size(path) > 128L * 1024L * 1024L) {
                logger.warn("IR ranking cache exceeds the read limit; fetching the requested ranking again");
                return null;
            }
            CacheFile file = RobustFile.load(path, bytes -> {
                try {
                    CacheFile parsed = json.fromJson(CacheFile.class, new String(bytes, StandardCharsets.UTF_8));
                    if (parsed == null || parsed.version != FORMAT_VERSION || parsed.entries == null) {
                        throw new java.text.ParseException("unsupported or invalid ranking cache", 0);
                    }
                    return parsed;
                } catch (RuntimeException error) {
                    java.text.ParseException failure = new java.text.ParseException("invalid ranking cache: " + error.getMessage(), 0);
                    failure.initCause(error);
                    throw failure;
                }
            });
            for (Entry entry : file.entries) {
                if (entry != null && key.equals(entry.key) && valid(entry.scores)) {
                    return Arrays.stream(entry.scores).map(ScoreEntry::toScore).toArray(IRScoreData[]::new);
                }
            }
        } catch (IOException error) {
            logger.warn("IR ranking cache could not be loaded; fetching the requested ranking again: {}", error.getMessage());
        }
        return null;
    }

    public synchronized IRScoreData[] load(String playerPath, String playerId, IRStatus ir,
            IRRankingContext context, String targetIdentity) {
        return load(playerPath, playerId, ir, context, (Object) targetIdentity);
    }

    public synchronized boolean contains(String playerPath, String playerId, IRStatus ir,
            IRRankingContext context, String targetIdentity) {
        return load(playerPath, playerId, ir, context, targetIdentity) != null;
    }

    public synchronized void save(
            String playerPath, String playerId, IRStatus ir, IRRankingContext context,
            Object target, IRScoreData[] scores) {
        if (scores == null || scores.length > 100_000) return;
        for (IRScoreData score : scores) {
            if (score == null || score.clear == null || score.player == null
                    || score.clear.id < 0 || score.clear.id >= 11) return;
        }
        String key = cacheKey(ir, context, target);
        if (key == null) return;
        Path path = cachePath(playerPath, playerId);
        CacheFile file = readOrEmpty(path);
        if (file.entries == null) file.entries = new java.util.ArrayList<>();
        file.entries.removeIf(entry -> entry == null || key.equals(entry.key));
        Entry entry = new Entry();
        entry.key = key;
        entry.savedAt = System.currentTimeMillis();
        entry.scores = Arrays.stream(scores).map(ScoreEntry::new).toArray(ScoreEntry[]::new);
        file.entries.add(entry);
        if (file.entries.size() > MAX_ENTRIES) {
            file.entries.sort((left, right) -> Long.compare(right.savedAt, left.savedAt));
            file.entries.subList(MAX_ENTRIES, file.entries.size()).clear();
        }
        try {
            Files.createDirectories(path.getParent());
            RobustFile.write(path, json.toJson(file).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            logger.warn("IR ranking cache could not be saved: {}", error.getMessage());
        }
    }

    public synchronized void save(String playerPath, String playerId, IRStatus ir,
            IRRankingContext context, String targetIdentity, IRScoreData[] scores) {
        save(playerPath, playerId, ir, context, (Object) targetIdentity, scores);
    }

    private CacheFile readOrEmpty(Path path) {
        if (!Files.isRegularFile(path)) return new CacheFile();
        try {
            if (Files.size(path) > 128L * 1024L * 1024L) return new CacheFile();
            CacheFile file = RobustFile.load(path, bytes -> {
                try {
                    CacheFile parsed = json.fromJson(CacheFile.class, new String(bytes, StandardCharsets.UTF_8));
                    if (parsed == null || parsed.version != FORMAT_VERSION || parsed.entries == null) {
                        throw new java.text.ParseException("unsupported ranking cache", 0);
                    }
                    return parsed;
                } catch (RuntimeException error) {
                    java.text.ParseException failure = new java.text.ParseException(error.getMessage(), 0);
                    failure.initCause(error);
                    throw failure;
                }
            });
            if (file != null && file.version == FORMAT_VERSION && file.entries != null) return file;
        } catch (RuntimeException | IOException error) {
            logger.warn("Ignoring unreadable IR ranking cache while saving a successful response: {}", error.getMessage());
        }
        return new CacheFile();
    }

    private static boolean valid(ScoreEntry[] scores) {
        if (scores == null) return false;
        if (scores.length > 100_000) return false;
        for (ScoreEntry score : scores) {
            if (score == null || score.clear == null || score.player == null
                    || score.clear.id < 0 || score.clear.id >= 11) return false;
        }
        return true;
    }

    private static Path cachePath(String playerPath, String playerId) {
        return Paths.get(playerPath, playerId, FILE_NAME);
    }

    private static String cacheKey(IRStatus status, IRRankingContext context, Object target) {
        if (status == null || status.config == null || status.player == null || status.player.id == null || target == null) return null;
        IRConfig config = status.config;
		String targetKey = targetIdentity(target);
		if (targetKey.isEmpty()) return null;
		String identity = String.join("\n", nullToEmpty(config.getIrname()),
				nullToEmpty(status.player.id), Integer.toString(context.lnmode()),
				Boolean.toString(context.forceLn()), targetKey);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return BMSDecoder.convertHexString(digest);
        } catch (NoSuchAlgorithmException error) {
            return null;
        }
    }

    private static String targetIdentity(Object target) {
        if (target instanceof String identity) return identity;
        if (target instanceof SongData song) {
            String sha = song.getSha256();
            if (sha == null || sha.length() != 64) return "";
            return "song:" + sha.toLowerCase();
        }
        if (target instanceof CourseData course) {
            SongData[] songs = course.getSong();
            if (songs == null || songs.length == 0) return "";
            StringBuilder identity = new StringBuilder("course:");
            for (SongData song : songs) {
                if (song == null || song.getSha256() == null || song.getSha256().length() != 64) return "";
                identity.append(song.getSha256().toLowerCase()).append(':');
            }
            if (course.getConstraint() != null) {
                Arrays.stream(course.getConstraint()).forEach(constraint -> identity.append(constraint.name).append(':'));
            }
            return identity.append("name:").append(nullToEmpty(course.getName())).toString();
        }
        return "";
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    public static class CacheFile {
        public int version = FORMAT_VERSION;
        public java.util.ArrayList<Entry> entries = new java.util.ArrayList<>();
    }

    public static class Entry {
        public String key;
        public long savedAt;
        public ScoreEntry[] scores;
    }

    public static class ScoreEntry {
        public String sha256;
        public int lntype;
        public int bmsirLongNotePolicy;
        public String player;
        public bms.player.beatoraja.ClearType clear;
        public long date;
        public int epg, lpg, egr, lgr, egd, lgd, ebd, lbd, epr, lpr, ems, lms;
        public long avgjudge;
        public int maxcombo, notes, passnotes, minbp, option;
        public long seed;
        public int assist, gauge;
        public bms.player.beatoraja.input.BMSPlayerInputDevice.Type deviceType;
        public bms.player.beatoraja.play.JudgeAlgorithm judgeAlgorithm;
        public bms.player.beatoraja.play.BMSPlayerRule rule;
        public String skin;
        public IRGaugeHistory gaugeHistory;

        public ScoreEntry() { }

        ScoreEntry(IRScoreData score) {
            sha256 = score.sha256; lntype = score.lntype; bmsirLongNotePolicy = score.bmsirLongNotePolicy;
            player = score.player; clear = score.clear; date = score.date;
            epg = score.epg; lpg = score.lpg; egr = score.egr; lgr = score.lgr;
            egd = score.egd; lgd = score.lgd; ebd = score.ebd; lbd = score.lbd;
            epr = score.epr; lpr = score.lpr; ems = score.ems; lms = score.lms;
            avgjudge = score.avgjudge; maxcombo = score.maxcombo; notes = score.notes;
            passnotes = score.passnotes; minbp = score.minbp; option = score.option;
            seed = score.seed; assist = score.assist; gauge = score.gauge;
            deviceType = score.deviceType; judgeAlgorithm = score.judgeAlgorithm;
            rule = score.rule; skin = score.skin; gaugeHistory = score.gaugeHistory;
        }

        IRScoreData toScore() {
            IRScoreData score = new IRScoreData(sha256, lntype, bmsirLongNotePolicy, player, clear, date,
                    epg, lpg, egr, lgr, egd, lgd, ebd, lbd, epr, lpr, ems, lms, avgjudge,
                    maxcombo, notes, passnotes, minbp, option, seed, assist, gauge,
                    deviceType, judgeAlgorithm, rule, skin);
            score.gaugeHistory = gaugeHistory;
            return score;
        }
    }
}
