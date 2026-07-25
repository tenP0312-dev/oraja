package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.MainState.MainStateType;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.Version;
import bms.player.beatoraja.arena.client.Client;
import bms.player.beatoraja.modmenu.ArenaMenu;
import bms.player.beatoraja.modmenu.FreqTrainerMenu;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.modmenu.JudgeTrainer;
import bms.player.beatoraja.modmenu.RandomTrainer;
import bms.player.beatoraja.song.SongData;

import com.badlogic.gdx.Gdx;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central BMS-IR Arena connection.
 *
 * This is intentionally separate from ArenaEX's host/client lobby protocol.
 * BMS-IR owns matchmaking, chart choice, start time, live fan-out and results.
 */
public final class BMSIRArenaClient {
    private static final Logger logger = LoggerFactory.getLogger(BMSIRArenaClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CLIENT_VERSION = "0.1.0-dev";
    private static final int PROTOCOL_VERSION = 1;
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

    private static volatile MainController main;
    private static volatile ArenaSocket socket;
    private static volatile boolean initialized;
    private static volatile boolean connected;
    private static volatile boolean reserved;
    private static volatile boolean arenaPlayPending;
    private static volatile boolean arenaPlayActive;
    private static volatile boolean forfeitRequested;
    private static volatile boolean normalResultReady = true;
    private static volatile int currentPlayerId;
    private static volatile String currentMatchId = "";
    private static volatile long serverStartMillis;
    private static volatile long serverClockOffsetMillis;
    private static volatile ObjectNode pendingFinal;
    private static final AtomicLong sequence = new AtomicLong();
    private static volatile long lastLiveNanos;
    private static volatile OptionSnapshot savedOptions;

    private BMSIRArenaClient() {
    }

    public static synchronized void initialize(MainController controller) {
        shutdown();
        main = controller;
        initialized = true;
        if (!controller.getPlayerConfig().isBmsirArenaEnabled()) {
            logger.info("BMS-IR Arena is disabled");
            return;
        }
        IRConfig config = findBmsirConfig(controller.getPlayerConfig());
        if (config == null) {
            ImGuiNotify.warning("BMS-IR Arena: BMS-IRのIR設定が見つかりません");
            return;
        }
        int playerId;
        try {
            playerId = Integer.parseInt(config.getUserid().trim());
        } catch (NumberFormatException e) {
            ImGuiNotify.error("BMS-IR Arena: User IDが不正です");
            return;
        }
        currentPlayerId = playerId;
        String passmd5 = md5(config.getPassword());
        try {
            URI uri = new URI(controller.getPlayerConfig().getBmsirArenaServer());
            ArenaSocket next = new ArenaSocket(uri, playerId, passmd5);
            next.setConnectionLostTimeout(15);
            socket = next;
            next.connect();
        } catch (Exception e) {
            logger.warn("BMS-IR Arena connection setup failed", e);
            ImGuiNotify.error("BMS-IR Arena: 接続先が不正です");
        }
    }

    public static synchronized void shutdown() {
        sendForfeit("client_shutdown");
        initialized = false;
        connected = false;
        reserved = false;
        arenaPlayPending = false;
        arenaPlayActive = false;
        forfeitRequested = false;
        currentPlayerId = 0;
        currentMatchId = "";
        pendingFinal = null;
        serverClockOffsetMillis = 0L;
        restoreOptions();
        ArenaSocket old = socket;
        socket = null;
        if (old != null) {
            try {
                old.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static IRConfig findBmsirConfig(PlayerConfig player) {
        IRConfig[] configs = player.getIrconfig();
        if (configs == null || configs.length == 0) {
            return null;
        }
        for (IRConfig config : configs) {
            String name = config == null ? "" : config.getIrname();
            if (name != null && name.toLowerCase(Locale.ROOT).contains("bms")) {
                return config;
            }
        }
        return null;
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isSelectionBlocked() {
        return !arenaPlayActive && (reserved || isAwaitingNormalResult());
    }

    public static boolean isAwaitingNormalResult() {
        return savedOptions != null && !arenaPlayActive && !normalResultReady;
    }

    public static boolean isArenaPlayActive() {
        return arenaPlayActive;
    }

    public static boolean isAbortInputBlocked() {
        return arenaPlayActive
                && main != null
                && main.getPlayerConfig().isBmsirArenaEnabled();
    }

    public static void enforceArenaOptions() {
        if (arenaPlayActive && main != null) {
            applyFixedOptions(main.getPlayerConfig());
            if (main.getPlayerResource().getBMSModel() != null && savedOptions != null) {
                savedOptions.disableConstant(
                        main.getPlayerConfig(),
                        main.getPlayerResource().getBMSModel().getMode()
                );
            }
        }
    }

    public static void onStateChange(MainStateType state) {
        if (!initialized || main == null) {
            return;
        }
        String value = normalizeState(state);
        if (
                arenaPlayActive
                        && pendingFinal == null
                        && ("select".equals(value) || "result".equals(value))
        ) {
            sendForfeit("play_aborted");
        }
        if ("play".equals(value) && !arenaPlayActive) {
            normalResultReady = false;
        }
        sendState(value, readyForArena(value));
    }

    private static String normalizeState(MainStateType state) {
        if (state == MainStateType.MUSICSELECT) {
            return "select";
        }
        if (state == MainStateType.DECIDE) {
            return "decide";
        }
        if (state == MainStateType.RESULT) {
            return "result";
        }
        if (state == MainStateType.COURSERESULT) {
            return "course";
        }
        if (state != MainStateType.PLAY || main == null) {
            return "unknown";
        }
        if (main.getPlayerResource().getCourseBMSModels() != null) {
            return "course";
        }
        BMSPlayerMode mode = main.getPlayerResource().getPlayMode();
        if (mode == null || mode.mode == BMSPlayerMode.Mode.PLAY) {
            return "play";
        }
        if (mode.mode == BMSPlayerMode.Mode.PRACTICE) {
            return "practice";
        }
        if (mode.mode == BMSPlayerMode.Mode.AUTOPLAY) {
            return "autoplay";
        }
        return "replay";
    }

    private static boolean readyForArena(String state) {
        if (!reserved || arenaPlayActive) {
            return false;
        }
        if ("select".equals(state)) {
            return true;
        }
        return "result".equals(state) && normalResultReady;
    }

    public static void onNormalResultReady() {
        normalResultReady = true;
        if (main != null) {
            sendState("result", readyForArena("result"));
        }
        restoreOptionsWhenSafe();
    }

    public static void onJudge(ScoreData score) {
        if (!arenaPlayActive || score == null || currentMatchId.isBlank()) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastLiveNanos < 1_000_000_000L) {
            return;
        }
        lastLiveNanos = now;
        ObjectNode message = baseMatchMessage("live");
        message.put("seq", sequence.incrementAndGet());
        message.put("exscore", score.getExscore());
        message.put("processed_notes", processedNotes(score));
        send(message);
    }

    public static void onMusicResultPrepared(ScoreData score, boolean hardFail) {
        if (!arenaPlayActive || score == null || currentMatchId.isBlank()) {
            return;
        }
        ObjectNode message = baseMatchMessage("final");
        message.put("seq", sequence.incrementAndGet());
        message.put("exscore", score.getExscore());
        message.put("processed_notes", processedNotes(score));
        message.put("state", hardFail ? "hard_fail" : "complete");
        pendingFinal = message;
        send(message);
        arenaPlayActive = false;
    }

    private static int processedNotes(ScoreData score) {
        if (score == null) {
            return 0;
        }
        int judged = 0;
        for (int judge = 0; judge <= 5; judge++) {
            judged += Math.max(0, score.getJudgeCount(judge));
        }
        int processed = Math.max(Math.max(0, score.getPassnotes()), judged);
        int total = score.getNotes();
        return total > 0 ? Math.min(processed, total) : processed;
    }

    private static ObjectNode baseMatchMessage(String type) {
        ObjectNode message = JSON.createObjectNode();
        message.put("type", type);
        message.put("match_id", currentMatchId);
        return message;
    }

    private static void sendForfeit(String reason) {
        if (
                forfeitRequested
                        || !reserved
                        || currentMatchId.isBlank()
                        || pendingFinal != null
        ) {
            return;
        }
        ObjectNode message = baseMatchMessage("forfeit");
        message.put("reason", reason);
        forfeitRequested = true;
        send(message);
    }

    private static void sendState(String state, boolean ready) {
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "state");
        message.put("state", state);
        message.put("arena_enabled", main != null && main.getPlayerConfig().isBmsirArenaEnabled());
        message.put("ready", ready);
        send(message);
    }

    private static void send(ObjectNode message) {
        ArenaSocket current = socket;
        if (current == null || !current.isOpen()) {
            return;
        }
        try {
            current.send(JSON.writeValueAsString(message));
        } catch (Exception e) {
            logger.warn("BMS-IR Arena send failed: {}", e.getMessage());
        }
    }

    private static void handleMessage(String raw) {
        try {
            JsonNode message = JSON.readTree(raw);
            switch (message.path("type").asText()) {
                case "hello_ok" -> {
                    connected = true;
                    updateServerClock(message);
                    ImGuiNotify.info("BMS-IR Arenaへ接続しました");
                    String activeMatchId = message.path("active_match_id").asText();
                    if (reserved && activeMatchId.isBlank()) {
                        reserved = false;
                        arenaPlayPending = false;
                        arenaPlayActive = false;
                        currentMatchId = "";
                        pendingFinal = null;
                        forfeitRequested = false;
                        restoreOptionsWhenSafe();
                        ImGuiNotify.info("Arenaの試合状態を同期しました", 8000);
                    }
                    sendState(normalizeCurrentState(), readyForArena(normalizeCurrentState()));
                    if (pendingFinal != null && !activeMatchId.isBlank()) {
                        send(pendingFinal);
                    }
                }
                case "pong" -> updateServerClock(message);
                case "match_reserved" -> {
                    String incomingMatchId = message.path("match_id").asText();
                    boolean sameMatch = reserved && currentMatchId.equals(incomingMatchId);
                    currentMatchId = incomingMatchId;
                    reserved = true;
                    if (!sameMatch) {
                        String currentState = normalizeCurrentState();
                        if ("select".equals(currentState)) {
                            normalResultReady = true;
                        } else if ("decide".equals(currentState) || "play".equals(currentState)) {
                            normalResultReady = false;
                        }
                        sequence.set(0);
                        forfeitRequested = false;
                        ImGuiNotify.info("マッチングしました！ 現在のプレイ終了後にArenaへ移動します", 8000);
                    }
                    sendState(normalizeCurrentState(), readyForArena(normalizeCurrentState()));
                }
                case "chart" -> receiveChart(message);
                case "start" -> scheduleArenaStart(message);
                case "match_resume" -> {
                    String incomingMatchId = message.path("match_id").asText();
                    currentMatchId = incomingMatchId;
                    reserved = true;
                    if ("countdown".equals(message.path("state").asText()) && arenaPlayPending) {
                        scheduleArenaStart(message);
                    } else if (!arenaPlayActive) {
                        ImGuiNotify.warning("BMS-IR Arena: 試合へ再接続しましたが、プレイ状態を復元できません");
                    }
                }
                case "result" -> {
                    boolean autoReentered = false;
                    for (JsonNode playerId : message.path("auto_reentry_player_ids")) {
                        if (playerId.asInt() == currentPlayerId) {
                            autoReentered = true;
                            break;
                        }
                    }
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    restoreOptionsWhenSafe();
                    ImGuiNotify.info(
                            autoReentered
                                    ? "Arena終了。次の対戦を待機しています"
                                    : "Arena終了。自動エントリーを終了しました",
                            8000
                    );
                    sendState("result", false);
                }
                case "forfeit_accepted" -> {
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    normalResultReady = true;
                    restoreOptionsWhenSafe();
                    ImGuiNotify.warning("Arenaの対戦を棄権しました。自動エントリーを終了します");
                    sendState(normalizeCurrentState(), false);
                }
                case "match_cancelled" -> {
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    restoreOptionsWhenSafe();
                    ImGuiNotify.warning("Arenaの試合がキャンセルされました");
                }
                case "match_released" -> {
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    restoreOptionsWhenSafe();
                    ImGuiNotify.warning(
                            message.path("queue_retained").asBoolean()
                                    ? "Arenaの今回の組み合わせから外れました。待機は継続しています"
                                    : "Arenaの今回の組み合わせから外れました"
                    );
                }
                case "replaced" -> ImGuiNotify.warning("BMS-IR Arena: 同じアカウントの別本体へ接続を移しました");
                case "error" -> ImGuiNotify.error(
                        "BMS-IR Arena: " + message.path("message").asText(message.path("code").asText()));
                default -> {
                }
            }
        } catch (Exception e) {
            logger.warn("BMS-IR Arena message parse failed", e);
        }
    }

    private static String normalizeCurrentState() {
        if (main == null || main.getCurrentState() == null) {
            return "unknown";
        }
        String simple = main.getCurrentState().getClass().getSimpleName();
        if ("MusicSelector".equals(simple)) return "select";
        if ("MusicDecide".equals(simple)) return "decide";
        if ("MusicResult".equals(simple)) return "result";
        if ("CourseResult".equals(simple)) return "course";
        if ("BMSPlayer".equals(simple)) {
            return normalizeState(MainStateType.PLAY);
        }
        return "unknown";
    }

    private static void updateServerClock(JsonNode message) {
        if (message.has("server_time")) {
            serverClockOffsetMillis = Math.round(
                    message.path("server_time").asDouble() * 1000.0
                            - System.currentTimeMillis()
            );
        }
    }

    private static void receiveChart(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText()) || main == null) {
            return;
        }
        String md5 = message.path("chart").path("md5").asText();
        Gdx.app.postRunnable(() -> {
            SongData[] songs = main.getSongDatabase().getSongDatas(new String[]{md5});
            SongData song = songs != null && songs.length > 0 ? songs[0] : null;
            /*
             * Existing songdata.db files may contain a CN/HCN-scale note
             * count from before this dedicated client normalized its catalog.
             * The MD5 identifies the exact source chart; its model is
             * normalized again when gameplay loads it, so a stale cached count
             * must not incorrectly report that the chart is missing.
             */
            boolean available = song != null;
            if (available) {
                Client.state.getSelectedSongRemote().setMd5(md5);
                Client.state.getSelectedSongRemote().setTitle(message.path("chart").path("title").asText());
                Client.state.getSelectedSongRemote().setArtist(message.path("chart").path("artist").asText());
                Client.state.setLobbySongData(song);
                arenaPlayPending = true;
            }
            ObjectNode reply = baseMatchMessage("chart_check");
            reply.put("chart_hash", md5);
            reply.put("available", available);
            reply.put("totalnotes", 0);
            send(reply);
            if (!available) {
                ImGuiNotify.warning("Arena課題譜面を所持していません: " + md5);
            }
        });
    }

    private static void scheduleArenaStart(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        serverStartMillis = Math.round(message.path("start_at").asDouble() * 1000.0);
        long serverNowMillis = System.currentTimeMillis() + serverClockOffsetMillis;
        long delay = Math.max(0L, serverStartMillis - serverNowMillis);
        SCHEDULER.schedule(() -> Gdx.app.postRunnable(() -> {
            if (!reserved || !arenaPlayPending || main == null) {
                return;
            }
            applyFixedOptions(main.getPlayerConfig());
            arenaPlayActive = true;
            arenaPlayPending = false;
            normalResultReady = false;
            lastLiveNanos = 0L;
            ArenaMenu.startCurrentLobbySong();
        }), delay, TimeUnit.MILLISECONDS);
    }

    private static void applyFixedOptions(PlayerConfig config) {
        if (savedOptions == null) {
            savedOptions = new OptionSnapshot(config);
        }
        config.setRandom(0);
        config.setRandom2(0);
        config.setDoubleoption(0);
        config.setLnmode(0);
        config.setMineMode(0);
        config.setScrollMode(0);
        config.setLongnoteMode(0);
        config.setSevenToNinePattern(0);
        config.setSevenToNineType(0);
        config.setExtranoteDepth(0);
        config.setBpmguide(false);
        config.setCustomJudge(false);
        FreqTrainerMenu.FREQ_TRAINER_ENABLED.set(false);
        JudgeTrainer.setActive(false);
        RandomTrainer.setActive(false);
    }

    private static void restoreOptions() {
        if (savedOptions != null && main != null) {
            savedOptions.restore(main.getPlayerConfig());
        }
        savedOptions = null;
    }

    private static void restoreOptionsWhenSafe() {
        if (!arenaPlayActive && normalResultReady) {
            restoreOptions();
        }
    }

    private static final class OptionSnapshot {
        private final int random;
        private final int random2;
        private final int doubleOption;
        private final int lnMode;
        private final int mineMode;
        private final int scrollMode;
        private final int longNoteMode;
        private final int sevenToNinePattern;
        private final int sevenToNineType;
        private final int extraNoteDepth;
        private final boolean bpmGuide;
        private final boolean customJudge;
        private final boolean frequencyTrainer;
        private final boolean judgeTrainer;
        private final boolean randomTrainer;
        private bms.model.Mode constantMode;
        private boolean constantEnabled;

        private OptionSnapshot(PlayerConfig config) {
            random = config.getRandom();
            random2 = config.getRandom2();
            doubleOption = config.getDoubleoption();
            lnMode = config.getLnmode();
            mineMode = config.getMineMode();
            scrollMode = config.getScrollMode();
            longNoteMode = config.getLongnoteMode();
            sevenToNinePattern = config.getSevenToNinePattern();
            sevenToNineType = config.getSevenToNineType();
            extraNoteDepth = config.getExtranoteDepth();
            bpmGuide = config.isBpmguide();
            customJudge = config.isCustomJudge();
            frequencyTrainer = FreqTrainerMenu.isFreqTrainerEnabled();
            judgeTrainer = JudgeTrainer.isActive();
            randomTrainer = RandomTrainer.isActive();
        }

        private void disableConstant(PlayerConfig config, bms.model.Mode mode) {
            if (constantMode == null) {
                constantMode = mode;
                constantEnabled = config.getPlayConfig(mode).getPlayconfig().isEnableConstant();
            }
            config.getPlayConfig(mode).getPlayconfig().setEnableConstant(false);
        }

        private void restore(PlayerConfig config) {
            config.setRandom(random);
            config.setRandom2(random2);
            config.setDoubleoption(doubleOption);
            config.setLnmode(lnMode);
            config.setMineMode(mineMode);
            config.setScrollMode(scrollMode);
            config.setLongnoteMode(longNoteMode);
            config.setSevenToNinePattern(sevenToNinePattern);
            config.setSevenToNineType(sevenToNineType);
            config.setExtranoteDepth(extraNoteDepth);
            config.setBpmguide(bpmGuide);
            config.setCustomJudge(customJudge);
            FreqTrainerMenu.FREQ_TRAINER_ENABLED.set(frequencyTrainer);
            JudgeTrainer.setActive(judgeTrainer);
            RandomTrainer.setActive(randomTrainer);
            if (constantMode != null) {
                config.getPlayConfig(constantMode).getPlayconfig().setEnableConstant(constantEnabled);
            }
        }
    }

    private static final class ArenaSocket extends WebSocketClient {
        private final int playerId;
        private final String passmd5;

        private ArenaSocket(URI uri, int playerId, String passmd5) {
            super(uri);
            this.playerId = playerId;
            this.passmd5 = passmd5;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            ObjectNode hello = JSON.createObjectNode();
            hello.put("type", "hello");
            hello.put("protocol", PROTOCOL_VERSION);
            hello.put("player_id", playerId);
            hello.put("passmd5", passmd5);
            hello.put("client_version", CLIENT_VERSION);
            hello.put("body_version", Version.getVersion());
            hello.put("build_hash", Version.getGitCommitHash());
            hello.put("arena_enabled", main != null && main.getPlayerConfig().isBmsirArenaEnabled());
            try {
                send(JSON.writeValueAsString(hello));
            } catch (Exception e) {
                close();
            }
        }

        @Override
        public void onMessage(String message) {
            handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            connected = false;
            if (initialized && main != null && main.getPlayerConfig().isBmsirArenaEnabled()) {
                SCHEDULER.schedule(() -> {
                    synchronized (BMSIRArenaClient.class) {
                        if (initialized && socket == this) {
                            try {
                                reconnect();
                            } catch (Exception e) {
                                logger.debug("Arena reconnect failed: {}", e.getMessage());
                            }
                        }
                    }
                }, 5, TimeUnit.SECONDS);
            }
        }

        @Override
        public void onError(Exception e) {
            connected = false;
            logger.warn("BMS-IR Arena socket error: {}", e.getMessage());
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "BMS-IR-Arena");
            thread.setDaemon(true);
            return thread;
        }
    }
}
