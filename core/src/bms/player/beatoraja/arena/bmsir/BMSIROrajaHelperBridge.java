package bms.player.beatoraja.arena.bmsir;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.player.beatoraja.ReplayData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends the final Arena lane placement to oraja_helper on Windows.
 *
 * The helper's named pipe is optional. Writes happen on a daemon thread so a
 * missing or stopped helper can never stall chart loading or gameplay.
 */
public final class BMSIROrajaHelperBridge {
    private static final String PIPE_PATH = "\\\\.\\pipe\\oraja_helper";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ExecutorService WRITER =
            Executors.newSingleThreadExecutor(new HelperThreadFactory());
    private static final AtomicLong LAST_FAILURE_LOG_NANOS = new AtomicLong();

    private BMSIROrajaHelperBridge() {
    }

    public static void publishArenaPlacement(
            BMSModel model,
            ReplayData replay
    ) {
        if (
                model == null
                        || replay == null
                        || !BMSIRArenaClient.isArenaPlayActive()
                        || !isWindows()
        ) {
            return;
        }
        ObjectNode message = placementMessage(model, replay);
        WRITER.execute(() -> writeMessage(message));
    }

    static ObjectNode placementMessage(BMSModel model, ReplayData replay) {
        Mode mode = model.getMode();
        ObjectNode message = JSON.createObjectNode();
        message.put("scene", "play");
        message.put("title", model.getTitle());
        message.put("artist", model.getArtist());
        message.put("md5", model.getMD5());
        message.put("sha256", model.getSHA256());
        message.put("playMode", mode.id);
        message.put("keyMode", playableKeyCount(mode));
        message.put("doublePlay", mode.player == 2);
        message.put("flip", mode.player == 2 && replay.doubleoption == 1);
        message.put("optionId", replay.randomoption);
        message.put(
                "option",
                BMSIRArenaClient.playOptionLabel(
                        replay.randomoption,
                        Mode.BEAT_7K.id
                )
        );
        message.put(
                "randomPlacement",
                sidePlacement(mode, replay, 0, replay.randomoption)
        );
        if (mode.player == 2) {
            message.put("option2PId", replay.randomoption2);
            message.put(
                    "option2P",
                    BMSIRArenaClient.playOptionLabel(
                            replay.randomoption2,
                            Mode.BEAT_7K.id
                    )
            );
            message.put(
                    "randomPlacement2P",
                    sidePlacement(mode, replay, 1, replay.randomoption2)
            );
        }
        return message;
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
        try (FileOutputStream pipe = new FileOutputStream(PIPE_PATH)) {
            pipe.write(
                    (JSON.writeValueAsString(message) + "\n")
                            .getBytes(StandardCharsets.UTF_8)
            );
            pipe.flush();
            BMSIRArenaLog.event(
                    "oraja_helper_placement_sent",
                    "play_mode", message.path("playMode").asInt(),
                    "key_mode", message.path("keyMode").asInt(),
                    "option", message.path("optionId").asInt(),
                    "option_2p", message.path("option2PId").isNumber()
                            ? message.path("option2PId").asInt()
                            : null,
                    "flip", message.path("flip").asBoolean()
            );
        } catch (Exception error) {
            long now = System.nanoTime();
            long previous = LAST_FAILURE_LOG_NANOS.get();
            if (
                    now - previous >= TimeUnit.MINUTES.toNanos(1)
                            && LAST_FAILURE_LOG_NANOS.compareAndSet(previous, now)
            ) {
                BMSIRArenaLog.event(
                        "oraja_helper_unavailable",
                        "error", error.getClass().getSimpleName()
                );
            }
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
