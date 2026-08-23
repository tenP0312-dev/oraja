package bms.player.beatoraja.arena.bmsir;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.player.beatoraja.ReplayData;
import bms.player.beatoraja.ClearType;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.song.SongData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes the final lane placement for the bundled OBS/browser view.
 *
 * The legacy helper named pipe remains optional on Windows. All writes happen
 * on a daemon thread so a missing view can never stall chart loading/gameplay.
 */
public final class BMSIROrajaHelperBridge {
    private static final String PIPE_PATH = "\\\\.\\pipe\\oraja_helper";
    private static final Path SNAPSHOT_DIRECTORY = Path.of("bmsir-helper");
    private static final Path SNAPSHOT_PATH =
            SNAPSHOT_DIRECTORY.resolve("current.json");
    private static final Path VIEW_PATH =
            SNAPSHOT_DIRECTORY.resolve("random_pattern_dp.html");
    private static final String VIEW_RESOURCE =
            "/resources/bmsir-helper/random_pattern_dp.html";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ExecutorService WRITER =
            Executors.newSingleThreadExecutor(new HelperThreadFactory());
    private static final AtomicLong LAST_FAILURE_LOG_NANOS = new AtomicLong();
    private static volatile ObjectNode lastPlacement;

    private BMSIROrajaHelperBridge() {
    }

    public static void publishArenaPlacement(
            BMSModel model,
            ReplayData replay
    ) {
        publishPlacement(model, replay);
    }

    public static void publishPlacement(BMSModel model, ReplayData replay) {
        if (model == null || replay == null) {
            return;
        }
        ObjectNode message = placementMessage(model, replay);
        lastPlacement = message.deepCopy();
        WRITER.execute(() -> writeMessage(message));
    }

    public static void publishSelectedSong(SongData song) {
        if (song != null) {
            publishMessage(selectMessage(song));
        }
    }

    public static void publishResult(
            SongData song,
            ReplayData replay,
            Mode mode,
            ScoreData score
    ) {
        if (score != null) {
            publishMessage(resultMessage(song, replay, mode, score));
        }
    }

    public static void publishPlayEnd(
            SongData song,
            ReplayData replay,
            Mode mode,
            ScoreData score,
            int playedNotes,
            int totalNotes,
            long elapsedSeconds,
            boolean quickRetry
    ) {
        publishMessage(playEndMessage(
                song,
                replay,
                mode,
                score,
                playedNotes,
                totalNotes,
                elapsedSeconds,
                quickRetry
        ));
    }

    private static void publishMessage(ObjectNode message) {
        lastPlacement = message.deepCopy();
        WRITER.execute(() -> writeMessage(message));
    }

    public static void publishScene(String scene) {
        String normalized = switch (scene == null ? "" : scene) {
            case "select", "play", "result" -> scene;
            default -> "select";
        };
        WRITER.execute(() -> {
            try {
                ObjectNode message = lastPlacement == null
                        ? loadSnapshot()
                        : lastPlacement.deepCopy();
                if (message == null) {
                    return;
                }
                message.put("scene", normalized);
                message.put("updatedAt", System.currentTimeMillis());
                lastPlacement = message.deepCopy();
                writeMessage(message);
            } catch (Exception error) {
                logUnavailable("scene", error);
            }
        });
    }

    static ObjectNode placementMessage(BMSModel model, ReplayData replay) {
        Mode mode = model.getMode();
        ObjectNode message = JSON.createObjectNode();
        message.put("schemaVersion", 1);
        message.put("updatedAt", System.currentTimeMillis());
        message.put("scene", "play");
        message.put("event", "song_play");
        message.put("title", model.getTitle());
        message.put("artist", model.getArtist());
        message.put("md5", model.getMD5());
        message.put("sha256", model.getSHA256());
        message.put("playMode", mode.id);
        message.put("keyMode", playableKeyCount(mode));
        message.put("doublePlay", mode.player == 2);
        message.put("flip", mode.player == 2 && replay.doubleoption == 1);
        message.put("optionId", replay.randomoption);
        message.put("randomSeed", replay.randomoptionseed);
        message.put(
                "option",
                BMSIRArenaClient.playOptionLabel(
                        replay.randomoption,
                        mode.id
                )
        );
        message.put(
                "randomPlacement",
                sidePlacement(mode, replay, 0, replay.randomoption)
        );
        if (mode.player == 2) {
            message.put("option2PId", replay.randomoption2);
            message.put("randomSeed2P", replay.randomoption2seed);
            message.put("doubleOption", replay.doubleoption);
            message.put(
                    "option2P",
                    BMSIRArenaClient.playOptionLabel(
                            replay.randomoption2,
                            mode.id
                    )
            );
            message.put(
                    "randomPlacement2P",
                    sidePlacement(mode, replay, 1, replay.randomoption2)
            );
        }
        return message;
    }

    static ObjectNode selectMessage(SongData song) {
        return baseMessage("song_select", "select", song);
    }

    static ObjectNode resultMessage(
            SongData song,
            ReplayData replay,
            Mode mode,
            ScoreData score
    ) {
        ObjectNode message = baseMessage("song_result", "result", song);
        addOptions(message, replay, mode);
        message.put("score", score.getExscore());
        int notes = song != null ? song.getNotes() : score.getNotes();
        message.put("scoreRate", notes > 0
                ? score.getExscore() * 100.0f / (notes * 2.0f)
                : 0.0f);
        ClearType clear = ClearType.getClearTypeByID(score.getClear());
        message.put("clearLamp", clear != null ? clear.name() : "NoPlay");
        message.put("clearLampId", score.getClear());
        message.put("missCount", score.getMinbp());
        message.set("judges", judgeMessage(score));
        return message;
    }

    static ObjectNode playEndMessage(
            SongData song,
            ReplayData replay,
            Mode mode,
            ScoreData score,
            int playedNotes,
            int totalNotes,
            long elapsedSeconds,
            boolean quickRetry
    ) {
        ObjectNode message = baseMessage("song_play_end", "play", song);
        addOptions(message, replay, mode);
        message.put("playEndMetrics", true);
        message.put("playedNotes", Math.max(0, playedNotes));
        message.put("totalNotes", Math.max(0, totalNotes));
        message.put("elapsedSeconds", Math.max(0, elapsedSeconds));
        message.put("quickRetry", quickRetry);
        if (score != null) {
            message.set("judges", judgeMessage(score));
        }
        return message;
    }

    private static ObjectNode baseMessage(String event, String scene, SongData song) {
        ObjectNode message = JSON.createObjectNode();
        message.put("schemaVersion", 1);
        message.put("updatedAt", System.currentTimeMillis());
        message.put("event", event);
        message.put("scene", scene);
        message.put("title", song != null ? song.getFullTitle() : "");
        message.put("artist", song != null ? song.getFullArtist() : "");
        message.put("sha256", song != null ? song.getSha256() : "");
        message.put("md5", song != null ? song.getMd5() : "");
        return message;
    }

    private static void addOptions(ObjectNode message, ReplayData replay, Mode mode) {
        if (replay == null || mode == null) {
            return;
        }
        message.put("playMode", mode.id);
        message.put("keyMode", playableKeyCount(mode));
        message.put("doublePlay", mode.player == 2);
        message.put("flip", mode.player == 2 && replay.doubleoption == 1);
        message.put("optionId", replay.randomoption);
        message.put("randomSeed", replay.randomoptionseed);
        message.put("option", BMSIRArenaClient.playOptionLabel(replay.randomoption, mode.id));
        message.put("randomPlacement", sidePlacement(mode, replay, 0, replay.randomoption));
        if (mode.player == 2) {
            message.put("option2PId", replay.randomoption2);
            message.put("randomSeed2P", replay.randomoption2seed);
            message.put("doubleOption", replay.doubleoption);
            message.put("option2P", BMSIRArenaClient.playOptionLabel(replay.randomoption2, mode.id));
            message.put("randomPlacement2P", sidePlacement(mode, replay, 1, replay.randomoption2));
        }
    }

    private static ObjectNode judgeMessage(ScoreData score) {
        ObjectNode judges = JSON.createObjectNode();
        judges.put("epg", score.getEpg());
        judges.put("lpg", score.getLpg());
        judges.put("egr", score.getEgr());
        judges.put("lgr", score.getLgr());
        judges.put("egd", score.getEgd());
        judges.put("lgd", score.getLgd());
        judges.put("ebd", score.getEbd());
        judges.put("lbd", score.getLbd());
        judges.put("epr", score.getEpr());
        judges.put("lpr", score.getLpr());
        judges.put("ems", score.getEms());
        judges.put("lms", score.getLms());
        return judges;
    }

    static int playableKeyCount(Mode mode) {
        return mode.key - mode.scratchKey.length;
    }

    static String sidePlacement(
            Mode mode,
            ReplayData replay,
            int player,
            int randomOption
    ) {
        int lanesPerPlayer = mode.key / mode.player;
        int playablePerPlayer = playableKeyCount(mode) / mode.player;
        int[] actual = replay.laneShufflePattern != null
                && player >= 0
                && player < replay.laneShufflePattern.length
                ? replay.laneShufflePattern[player]
                : null;
        StringBuilder placement = new StringBuilder(playablePerPlayer);
        for (int lane = 0; lane < playablePerPlayer; lane++) {
            int localSource;
            if (actual != null && lane < actual.length) {
                localSource = actual[lane] - lanesPerPlayer * player + 1;
                if (localSource < 1 || localSource > playablePerPlayer) {
                    localSource = lane + 1;
                }
            } else if (randomOption == 1) {
                localSource = playablePerPlayer - lane;
            } else {
                localSource = lane + 1;
            }
            placement.append(localSource);
        }
        return placement.toString();
    }

    private static void writeMessage(ObjectNode message) {
        byte[] payload;
        try {
            payload = (JSON.writeValueAsString(message) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            writeSnapshot(payload);
            BMSIRArenaLog.event(
                    "pattern_view_snapshot_written",
                    "play_mode", message.path("playMode").asInt(),
                    "key_mode", message.path("keyMode").asInt(),
                    "option", message.path("optionId").asInt(),
                    "option_2p", message.path("option2PId").isNumber()
                            ? message.path("option2PId").asInt()
                            : null,
                    "flip", message.path("flip").asBoolean()
            );
        } catch (Exception error) {
            logUnavailable("snapshot", error);
            return;
        }

        if (!isWindows()) {
            return;
        }
        try (FileOutputStream pipe = new FileOutputStream(PIPE_PATH)) {
            pipe.write(payload);
            pipe.flush();
        } catch (Exception error) {
            logUnavailable("legacy_pipe", error);
        }
    }

    private static void writeSnapshot(byte[] payload) throws Exception {
        Files.createDirectories(SNAPSHOT_DIRECTORY);
        ensureViewFile();
        Path temporary = SNAPSHOT_DIRECTORY.resolve("current.json.tmp");
        Files.write(temporary, payload);
        try {
            Files.move(
                    temporary,
                    SNAPSHOT_PATH,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(
                    temporary,
                    SNAPSHOT_PATH,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static ObjectNode loadSnapshot() {
        if (!Files.isRegularFile(SNAPSHOT_PATH)) {
            return null;
        }
        try {
            var parsed = JSON.readTree(SNAPSHOT_PATH.toFile());
            return parsed != null
                    && parsed.isObject()
                    && parsed.path("schemaVersion").asInt() == 1
                    ? (ObjectNode) parsed
                    : null;
        } catch (Exception error) {
            logUnavailable("snapshot_read", error);
            return null;
        }
    }

    private static void ensureViewFile() throws Exception {
        if (Files.isRegularFile(VIEW_PATH)) {
            return;
        }
        try (InputStream resource =
                     BMSIROrajaHelperBridge.class.getResourceAsStream(
                             VIEW_RESOURCE
                     )) {
            if (resource == null) {
                throw new IllegalStateException("pattern view resource missing");
            }
            Path temporary = SNAPSHOT_DIRECTORY.resolve(
                    "random_pattern_dp.html.tmp"
            );
            Files.copy(
                    resource,
                    temporary,
                    StandardCopyOption.REPLACE_EXISTING
            );
            try {
                Files.move(
                        temporary,
                        VIEW_PATH,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary,
                        VIEW_PATH,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
    }

    private static void logUnavailable(String target, Exception error) {
        long now = System.nanoTime();
        long previous = LAST_FAILURE_LOG_NANOS.get();
        if (
                now - previous >= TimeUnit.MINUTES.toNanos(1)
                        && LAST_FAILURE_LOG_NANOS.compareAndSet(previous, now)
        ) {
            BMSIRArenaLog.event(
                    "pattern_view_unavailable",
                    "target", target,
                    "error", error.getClass().getSimpleName()
            );
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase()
                .contains("win");
    }

    private static final class HelperThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "bmsir-oraja-helper");
            thread.setDaemon(true);
            return thread;
        }
    }
}
