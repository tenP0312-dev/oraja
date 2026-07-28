package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.MainState.MainStateType;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ReplayData;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.TableDataAccessor;
import bms.player.beatoraja.Version;
import bms.player.beatoraja.arena.client.Client;
import bms.player.beatoraja.modmenu.ArenaMenu;
import bms.player.beatoraja.modmenu.FreqTrainerMenu;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.modmenu.JudgeTrainer;
import bms.player.beatoraja.modmenu.RandomTrainer;
import bms.player.beatoraja.pattern.LR2RandomPattern;
import bms.player.beatoraja.play.BMSPlayer;
import bms.player.beatoraja.play.BMSPlayerRule;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.bar.Bar;
import bms.player.beatoraja.select.bar.DirectoryBar;
import bms.player.beatoraja.select.bar.SongBar;
import bms.player.beatoraja.select.bar.TableBar;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
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
    private static final String CLIENT_VERSION = Version.getArenaClientVersion();
    private static final String CLIENT_FLAVOR = "arena-oraja";
    private static final int PROTOCOL_VERSION = 5;
    private static final int MAX_NORMAL_ARENA_LEVEL = 12;
    private static final int MAX_OFFICIAL_ARENA_LEVEL = 25;
    private static final int MAX_ARENA_RATING_BAND =
            MAX_NORMAL_ARENA_LEVEL + MAX_OFFICIAL_ARENA_LEVEL;
    private static final String NORMAL_ARENA_TABLE_NAME = "GENOCIDE 通常難易度表";
    private static final String NORMAL_ARENA_TABLE_URL =
            "https://darksabun.club/table/archive/normal1/";
    private static final String OFFICIAL_ARENA_TABLE_NAME = "発狂BMS難易度表";
    private static final String OFFICIAL_ARENA_TABLE_URL =
            "https://darksabun.club/table/archive/insane1/";
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

    private static volatile MainController main;
    private static volatile ArenaSocket socket;
    private static volatile boolean initialized;
    private static volatile boolean connected;
    private static volatile boolean reserved;
    private static volatile boolean arenaPlayPending;
    private static volatile boolean arenaPlayActive;
    private static volatile boolean playReadySent;
    private static volatile boolean forfeitRequested;
    private static volatile boolean normalResultReady = true;
    private static volatile int currentPlayerId;
    private static volatile String currentMatchId = "";
    private static volatile long serverStartMillis;
    private static volatile long loadDeadlineMillis;
    private static volatile long serverClockOffsetMillis;
    private static volatile ScheduledFuture<?> clockSyncTask;
    private static volatile ScheduledFuture<?> optionReadyTask;
    private static volatile ObjectNode pendingFinal;
    private static volatile int currentPlayOption;
    private static volatile int currentPlayMode;
    private static volatile int lastKnownPlayMode;
    private static volatile int currentChartTotalNotes;
    private static volatile long currentRandomSeed = -1L;
    private static volatile boolean optionSelectionOpen;
    private static volatile boolean optionReadySent;
    private static volatile long optionDeadlineMillis;
    private static final AtomicLong sequence = new AtomicLong();
    private static volatile long lastLiveNanos;
    private static volatile OptionSnapshot savedOptions;
    private static volatile double arenaRating = 1000.0;
    private static volatile int arenaMatchesPlayed;
    private static volatile String queueStatus = "idle";
    private static volatile String arenaUiMessage = "";
    private static volatile JsonNode rankingView = JSON.createObjectNode();
    private static volatile JsonNode liveView = JSON.createObjectNode();
    private static volatile JsonNode resultView = JSON.createObjectNode();
    private static volatile JsonNode manualView = BMSIRArenaManual.load(JSON);
    private static volatile JsonNode nominationView = JSON.createObjectNode();
    private static volatile JsonNode rulesView = JSON.createObjectNode();
    private static volatile JsonNode queueView = JSON.createObjectNode();
    private static volatile JsonNode roomView = JSON.createObjectNode();
    private static volatile JsonNode publicRoomsView = JSON.createArrayNode();
    private static volatile boolean roomReady;
    private static volatile boolean nominationOpen;
    private static volatile long nominationDeadlineMillis;
    private static volatile int nominationTargetBand;
    private static volatile long fillDeadlineMillis;
    private static volatile int fillPlayerCount;
    private static volatile int fillMaxPlayers = 8;
    private static final Object CHAT_LOCK = new Object();
    private static final List<JsonNode> chatMessages = new ArrayList<>();
    private static final List<JsonNode> lobbyChatMessages = new ArrayList<>();

    private BMSIRArenaClient() {
    }

    static String clientVersion() {
        return CLIENT_VERSION;
    }

    static final class ArenaNominationLevelBar extends DirectoryBar {
        private final String title;
        private final SongData[] songs;

        ArenaNominationLevelBar(
                MusicSelector selector,
                int level,
                SongData[] songs
        ) {
            super(selector);
            this.title = arenaBandLabel(level) + " (" + songs.length + "譜面)";
            this.songs = songs;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public Bar[] getChildren() {
            return Arrays.stream(songs)
                    .map(SongBar::new)
                    .toArray(Bar[]::new);
        }

        @Override
        public boolean usesTableFolderStyle() {
            return true;
        }

        @Override
        public void updateFolderStatus() {
            updateFolderStatus(songs);
        }
    }

    static final class ArenaNominationRootBar extends DirectoryBar {
        private final ArenaNominationLevelBar[] levels;
        private final SongData[] songs;

        ArenaNominationRootBar(
                MusicSelector selector,
                Map<Integer, SongData[]> songsByLevel
        ) {
            super(selector);
            levels = songsByLevel.entrySet().stream()
                    .filter(entry -> entry.getValue().length > 0)
                    .map(entry -> new ArenaNominationLevelBar(
                            selector,
                            entry.getKey(),
                            entry.getValue()
                    ))
                    .toArray(ArenaNominationLevelBar[]::new);
            songs = songsByLevel.values().stream()
                    .flatMap(Arrays::stream)
                    .toArray(SongData[]::new);
        }

        @Override
        public String getTitle() {
            return "BMS-IR Arena 選曲候補";
        }

        @Override
        public Bar[] getChildren() {
            return Arrays.copyOf(levels, levels.length, Bar[].class);
        }

        @Override
        public void updateFolderStatus() {
            updateFolderStatus(songs);
        }
    }

    public static synchronized void initialize(MainController controller) {
        shutdown();
        main = controller;
        initialized = true;
        BMSIRArenaLog.event(
                "initialize",
                "client_version", CLIENT_VERSION,
                "body_version", Version.getVersion(),
                "client_flavor", CLIENT_FLAVOR,
                "ruleset_profile", BMSPlayerRule.getConfiguredRuleProfileId()
        );
        if (!controller.getPlayerConfig().isBmsirArenaEnabled()) {
            logger.info("BMS-IR Arena is disabled");
            BMSIRArenaLog.event("disabled");
            return;
        }
        IRConfig config = findBmsirConfig(controller.getPlayerConfig());
        if (config == null) {
            BMSIRArenaLog.event("configuration_missing");
            ImGuiNotify.warning("BMS-IR Arena: BMS-IRのIR設定が見つかりません");
            return;
        }
        int playerId;
        try {
            playerId = Integer.parseInt(config.getUserid().trim());
        } catch (NumberFormatException e) {
            BMSIRArenaLog.event("configuration_invalid_player_id");
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
            BMSIRArenaLog.event("connect_requested", "player_id", playerId);
            next.connect();
        } catch (Exception e) {
            logger.warn("BMS-IR Arena connection setup failed", e);
            BMSIRArenaLog.event(
                    "connect_setup_failed",
                    "error", e.getClass().getSimpleName(),
                    "message", e.getMessage()
            );
            ImGuiNotify.error("BMS-IR Arena: 接続先が不正です");
        }
    }

    public static synchronized void shutdown() {
        BMSIRArenaLog.event(
                "shutdown",
                "match_id", currentMatchId,
                "reserved", reserved,
                "play_active", arenaPlayActive
        );
        sendForfeit("client_shutdown");
        initialized = false;
        connected = false;
        reserved = false;
        arenaPlayPending = false;
        arenaPlayActive = false;
        playReadySent = false;
        forfeitRequested = false;
        currentPlayerId = 0;
        currentMatchId = "";
        pendingFinal = null;
        currentPlayOption = 0;
        currentPlayMode = 0;
        lastKnownPlayMode = 0;
        loadDeadlineMillis = 0L;
        serverStartMillis = 0L;
        currentChartTotalNotes = 0;
        currentRandomSeed = -1L;
        serverClockOffsetMillis = 0L;
        ScheduledFuture<?> oldClockSyncTask = clockSyncTask;
        clockSyncTask = null;
        if (oldClockSyncTask != null) {
            oldClockSyncTask.cancel(false);
        }
        arenaRating = 1000.0;
        arenaMatchesPlayed = 0;
        queueStatus = "idle";
        arenaUiMessage = "";
        rankingView = JSON.createObjectNode();
        rulesView = JSON.createObjectNode();
        queueView = JSON.createObjectNode();
        roomView = JSON.createObjectNode();
        publicRoomsView = JSON.createArrayNode();
        roomReady = false;
        liveView = JSON.createObjectNode();
        resultView = JSON.createObjectNode();
        clearNominationState();
        clearChatMessages();
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

    public static boolean ignoresArenaPreloadInputDelay() {
        return arenaPlayActive;
    }

    public static boolean isArenaStartReleased() {
        return arenaStartReleased(
                arenaPlayActive,
                playReadySent,
                serverStartMillis,
                System.currentTimeMillis() + serverClockOffsetMillis
        );
    }

    static boolean arenaStartReleased(
            boolean playActive,
            boolean readySent,
            long startMillis,
            long serverNowMillis
    ) {
        return !playActive
                || (readySent && startMillis > 0L && serverNowMillis >= startMillis);
    }

    public static void onArenaPlayReady() {
        if (
                !arenaPlayActive
                        || playReadySent
                        || currentMatchId.isBlank()
        ) {
            return;
        }
        String chartHash = Client.state.getSelectedSongRemote().getMd5();
        if (chartHash == null || chartHash.isBlank()) {
            return;
        }
        playReadySent = true;
        sendPlayReady(chartHash);
        arenaUiMessage = "他の参加者のロード完了を待っています";
    }

    private static void sendPlayReady(String chartHash) {
        ObjectNode message = baseMatchMessage("play_ready");
        message.put("chart_hash", chartHash);
        send(message);
    }

    static boolean shouldShowOverlay() {
        return initialized
                && main != null
                && main.getPlayerConfig().isBmsirArenaEnabled();
    }

    static PlayerConfig playerConfig() {
        return main == null ? null : main.getPlayerConfig();
    }

    static boolean isCurrentPlayDouble() {
        return isDoublePlayMode(currentPlayModeForLayout());
    }

    static int currentPlayModeForLayout() {
        if (currentPlayMode > 0) {
            return currentPlayMode;
        }
        if (
                main != null
                        && main.getPlayerResource().getBMSModel() != null
        ) {
            return main.getPlayerResource().getBMSModel().getMode().id;
        }
        return lastKnownPlayMode;
    }

    static String currentPlayModeLabel() {
        return playModeLabel(currentPlayModeForLayout());
    }

    static String currentMatchPlayModeLabel() {
        if (reserved && !currentMatchId.isBlank()) {
            return currentPlayMode > 0
                    ? playModeLabel(currentPlayMode)
                    : "";
        }
        JsonNode chart = resultView.path("chart");
        if (
                !isShowingCompletedResult()
                        || !chart.isObject()
                        || chart.size() == 0
        ) {
            return "";
        }
        return playModeLabel(lastKnownPlayMode);
    }

    static boolean isConnected() {
        return connected;
    }

    static boolean isReserved() {
        return reserved;
    }

    public static boolean isNominationOpen() {
        return nominationOpen
                && reserved
                && !currentMatchId.isBlank();
    }

    static int currentPlayerId() {
        return currentPlayerId;
    }

    static double arenaRating() {
        return arenaRating;
    }

    static int arenaMatchesPlayed() {
        return arenaMatchesPlayed;
    }

    static String queueStatus() {
        return queueStatus;
    }

    static boolean currentQueueAllowsCpu() {
        JsonNode value = queueView.get("allow_cpu");
        if (value != null && value.isBoolean()) {
            return value.asBoolean();
        }
        PlayerConfig config = playerConfig();
        return config == null || config.isBmsirArenaAllowCpu();
    }

    static String arenaUiMessage() {
        return arenaUiMessage;
    }

    static JsonNode rankingView() {
        return rankingView;
    }

    static JsonNode manualView() {
        return manualView;
    }

    static JsonNode currentMatchView() {
        return resultView.isObject() && resultView.size() > 0
                ? resultView
                : liveView.isObject() && liveView.size() > 0
                        ? liveView
                        : roomView;
    }

    static JsonNode nominationView() {
        return nominationView;
    }

    static JsonNode rulesView() {
        return rulesView;
    }

    static JsonNode queueView() {
        return queueView;
    }

    static JsonNode roomView() {
        return roomView;
    }

    static JsonNode publicRoomsView() {
        return publicRoomsView;
    }

    static void dismissResult() {
        JsonNode dismissed = resultView;
        resultView = JSON.createObjectNode();
        if (
                currentMatchId.isBlank()
                        && (
                                liveView == dismissed
                                        || "result".equals(
                                                liveView.path("type").asText("")
                                        )
                        )
        ) {
            liveView = JSON.createObjectNode();
        }
    }

    static boolean isRoomReady() {
        return roomReady;
    }

    private static JsonNode activeRulesOrQueue() {
        return reserved && rulesView.isObject() && rulesView.size() > 0
                ? rulesView
                : queueView;
    }

    static String currentMatchMode() {
        return activeRulesOrQueue().path("match_mode").asText("ranked");
    }

    static String currentScoreRule() {
        return activeRulesOrQueue().path("score_rule").asText("exscore");
    }

    static String currentForcedGauge() {
        return activeRulesOrQueue().path("forced_gauge").asText("free");
    }

    static String currentRulesetProfile() {
        String fallback = "ranked".equals(currentMatchMode())
                ? BMSPlayerRule.PROFILE_LR2
                : BMSPlayerRule.getConfiguredRuleProfileId();
        return BMSPlayerRule.normalizeRuleProfile(
                activeRulesOrQueue().path("ruleset_profile").asText(fallback)
        );
    }

    static void setConfiguredRulesetProfile(String profile) {
        String normalized = BMSPlayerRule.normalizeRuleProfile(profile);
        PlayerConfig config = playerConfig();
        if (config != null) {
            config.setBmsirRulesetProfile(normalized);
        }
        BMSPlayerRule.setConfiguredRuleProfile(normalized);
    }

    static String currentChartScope() {
        return activeRulesOrQueue().path("chart_scope").asText("official");
    }

    static String currentRoomCode() {
        return activeRulesOrQueue().path("room_code").asText("");
    }

    static String currentRoomName() {
        return activeRulesOrQueue().path("room_name").asText("");
    }

    static boolean isCurrentRoomLocked() {
        return roomView.path("locked").asBoolean(
                queueView.path("room_locked").asBoolean(false)
        );
    }

    static int currentRoomHostId() {
        return activeRulesOrQueue().path("room_host_id").asInt(0);
    }

    static boolean isCurrentRoomHost() {
        return currentPlayerId > 0 && currentPlayerId == currentRoomHostId();
    }

    static boolean isRoomParticipating() {
        return queueView.path("room_participating").asBoolean(true);
    }

    static boolean isRoomParticipationPending() {
        return !queueView.path("participation_after_series_id").asText("").isBlank();
    }

    static boolean isRoomPaused() {
        return roomView.path("paused").asBoolean(false);
    }

    static boolean isSpectatorPublic() {
        return activeRulesOrQueue().path("spectator_public").asBoolean(false);
    }

    static boolean isForceHostOption() {
        return activeRulesOrQueue().path("force_host_option").asBoolean(false);
    }

    static String currentNominationPolicy() {
        return activeRulesOrQueue().path("nomination_policy").asText("all");
    }

    static int currentNominationSeconds() {
        return activeRulesOrQueue().path("nomination_seconds").asInt(60);
    }

    static int currentOptionSeconds() {
        return activeRulesOrQueue().path("option_seconds").asInt(10);
    }

    static int currentIntermissionSeconds() {
        return activeRulesOrQueue().path("intermission_seconds").asInt(0);
    }

    static String currentSeriesFormat() {
        return activeRulesOrQueue().path("series_format").asText("single");
    }

    static int currentFirstToWins() {
        return activeRulesOrQueue().path("first_to_wins").asInt(2);
    }

    static int currentSeriesRound() {
        return activeRulesOrQueue().path("series_round").asInt(1);
    }

    static int currentSelectorPlayerId() {
        return activeRulesOrQueue().path("selector_player_id").asInt(0);
    }

    static String currentRatingPolicy() {
        return rulesView.path("rating_policy").asText(
                "ranked".equals(currentMatchMode()) ? "elo" : "none"
        );
    }

    static boolean isFillWaiting() {
        return reserved
                && !currentMatchId.isBlank()
                && fillDeadlineMillis > 0L
                && !nominationOpen;
    }

    static long fillSecondsRemaining() {
        return fillCountdownSeconds(
                fillDeadlineMillis,
                System.currentTimeMillis() + serverClockOffsetMillis
        );
    }

    static long fillCountdownSeconds(
            long deadlineMillis,
            long serverNowMillis
    ) {
        if (deadlineMillis <= 0L) {
            return 0L;
        }
        return Math.max(
                0L,
                (deadlineMillis - serverNowMillis + 999L) / 1000L
        );
    }

    static int fillPlayerCount() {
        return fillPlayerCount;
    }

    static int fillMaxPlayers() {
        return fillMaxPlayers;
    }

    static long nominationSecondsRemaining() {
        return nominationSecondsRemaining(
                System.currentTimeMillis() + serverClockOffsetMillis
        );
    }

    static long nominationSecondsRemaining(long serverNowMillis) {
        if (!nominationOpen || nominationDeadlineMillis <= 0L) {
            return 0L;
        }
        return fillCountdownSeconds(
                nominationDeadlineMillis,
                serverNowMillis
        );
    }

    static long nominationCountdownSeconds(
            long deadlineMillis,
            long serverNowMillis
    ) {
        return fillCountdownSeconds(deadlineMillis, serverNowMillis);
    }

    static long loadSecondsRemaining() {
        return fillCountdownSeconds(
                loadDeadlineMillis,
                System.currentTimeMillis() + serverClockOffsetMillis
        );
    }

    static long startSecondsRemaining() {
        return fillCountdownSeconds(
                serverStartMillis,
                System.currentTimeMillis() + serverClockOffsetMillis
        );
    }

    static String currentPhaseAction() {
        if (isRoomPaused()) {
            return "休憩中（全員観戦）";
        }
        if (
                isNominationOpen()
                        && "cpu_chart_request".equals(
                                nominationView.path("type").asText("")
                        )
        ) {
            return "CPUが課題曲を選んでいます";
        }
        return phaseAction(
                isOptionSelectionOpen(),
                optionReadySent,
                isNominationOpen(),
                nominationView.path("can_nominate").asBoolean(true),
                isFillWaiting(),
                currentMatchView().path("state").asText(""),
                playReadySent,
                isShowingCompletedResult(),
                queueStatus
        );
    }

    static String phaseAction(
            boolean optionOpen,
            boolean optionReady,
            boolean nominationOpen,
            boolean canNominate,
            boolean fillWaiting,
            String matchState,
            boolean loadReady,
            boolean completedResult,
            String currentQueueStatus
    ) {
        if (optionOpen) {
            return optionReady
                    ? "ほかの参加者のOP確定を待っています"
                    : "OPを選んでください";
        }
        if (nominationOpen) {
            return canNominate
                    ? "曲を選んでください"
                    : "部屋主の選曲を待っています";
        }
        if (fillWaiting) {
            return "追加の参加者を待っています";
        }
        if ("loading".equals(matchState)) {
            return loadReady
                    ? "ほかの参加者の読込を待っています"
                    : "譜面を読み込んでいます";
        }
        if ("countdown".equals(matchState)) {
            return "対戦開始を待っています";
        }
        if ("playing".equals(matchState)) {
            return "プレイ中";
        }
        if (completedResult) {
            return "対戦結果を確認してください";
        }
        return switch (currentQueueStatus) {
            case "queued" -> "対戦相手を待っています";
            case "reserved", "matched" -> "対戦準備中です";
            case "withdraw_requested" -> "退出処理を待っています";
            default -> "エントリーしてください";
        };
    }

    static boolean currentPhaseHasCountdown() {
        if (isOptionSelectionOpen() || isNominationOpen() || isFillWaiting()) {
            return true;
        }
        String state = currentMatchView().path("state").asText("");
        return ("loading".equals(state) && loadDeadlineMillis > 0L)
                || ("countdown".equals(state) && serverStartMillis > 0L);
    }

    static long currentPhaseSecondsRemaining() {
        if (isOptionSelectionOpen()) {
            return optionSecondsRemaining();
        }
        if (isNominationOpen()) {
            return nominationSecondsRemaining();
        }
        if (isFillWaiting()) {
            return fillSecondsRemaining();
        }
        String state = currentMatchView().path("state").asText("");
        if ("loading".equals(state)) {
            return loadSecondsRemaining();
        }
        if ("countdown".equals(state)) {
            return startSecondsRemaining();
        }
        return 0L;
    }

    static SongData currentNominationSong() {
        if (
                main == null
                        || !(main.getCurrentState() instanceof MusicSelector selector)
                        || !(selector.getSelectedBar() instanceof SongBar songBar)
                        || !songBar.existsSong()
        ) {
            return null;
        }
        SongData song = songBar.getSongData();
        return song != null && !song.getMd5().isBlank() ? song : null;
    }

    static boolean isOfficialArenaTable(String name, String url) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedUrl = url == null
                ? ""
                : url.trim().toLowerCase(Locale.ROOT);
        return OFFICIAL_ARENA_TABLE_NAME.equals(normalizedName)
                || OFFICIAL_ARENA_TABLE_URL.equals(normalizedUrl);
    }

    static boolean isNormalArenaTable(String name, String url) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedUrl = url == null
                ? ""
                : url.trim().toLowerCase(Locale.ROOT);
        return NORMAL_ARENA_TABLE_NAME.equals(normalizedName)
                || "通常難易度表".equals(normalizedName)
                || NORMAL_ARENA_TABLE_URL.equals(normalizedUrl);
    }

    static int normalArenaLevel(String label) {
        if (label == null || !label.startsWith("☆")) {
            return -1;
        }
        try {
            int level = Integer.parseInt(label.substring(1));
            return level >= 1 && level <= MAX_NORMAL_ARENA_LEVEL
                    ? level
                    : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static String arenaBandLabel(int band) {
        int value = Math.max(1, Math.min(MAX_ARENA_RATING_BAND, band));
        return value <= MAX_NORMAL_ARENA_LEVEL
                ? "☆" + value
                : "★" + (value - MAX_NORMAL_ARENA_LEVEL);
    }

    static int officialArenaLevel(String label) {
        if (label == null || !label.startsWith("★")) {
            return -1;
        }
        try {
            int level = Integer.parseInt(label.substring(1));
            return level >= 1 && level <= MAX_OFFICIAL_ARENA_LEVEL
                    ? level
                    : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static SongData[] nominationCandidateElements(
            TableBar[] tables,
            int targetBand
    ) {
        return nominationCandidateElementsByLevel(tables, targetBand)
                .values()
                .stream()
                .flatMap(Arrays::stream)
                .toArray(SongData[]::new);
    }

    static Map<Integer, SongData[]> nominationCandidateElementsByLevel(
            TableBar[] tables,
            int targetBand
    ) {
        return nominationCandidateElementsByLevel(
                tables == null
                        ? null
                        : Arrays.stream(tables)
                                .filter(Objects::nonNull)
                                .map(TableBar::getTableData)
                                .toArray(TableData[]::new),
                targetBand
        );
    }

    static SongData[] nominationCandidateElements(
            TableData[] tables,
            int targetBand
    ) {
        return nominationCandidateElementsByLevel(tables, targetBand)
                .values()
                .stream()
                .flatMap(Arrays::stream)
                .toArray(SongData[]::new);
    }

    static Map<Integer, SongData[]> nominationCandidateElementsByLevel(
            TableData[] tables,
            int targetBand
    ) {
        int ceiling = Math.max(
                1,
                Math.min(MAX_ARENA_RATING_BAND, targetBand)
        );
        Map<Integer, Map<String, SongData>> candidates = new LinkedHashMap<>();
        if (tables == null) {
            return new LinkedHashMap<>();
        }
        for (TableData table : tables) {
            if (table == null) {
                continue;
            }
            boolean normalTable = isNormalArenaTable(table.getName(), table.getUrl());
            boolean officialTable = isOfficialArenaTable(table.getName(), table.getUrl());
            if (!normalTable && !officialTable) {
                continue;
            }
            for (TableData.TableFolder folder : table.getFolder()) {
                int tableLevel = normalTable
                        ? normalArenaLevel(folder.getName())
                        : officialArenaLevel(folder.getName());
                int band = normalTable
                        ? tableLevel
                        : tableLevel < 1
                                ? -1
                                : MAX_NORMAL_ARENA_LEVEL + tableLevel;
                if (band < 1 || band > ceiling) {
                    continue;
                }
                for (SongData song : folder.getSong()) {
                    String md5 = song == null ? "" : song.getMd5();
                    String key = md5 == null
                            ? ""
                            : md5.toLowerCase(Locale.ROOT);
                    if (!key.isBlank()) {
                        candidates.computeIfAbsent(
                                band,
                                ignored -> new LinkedHashMap<>()
                        ).putIfAbsent(key, song);
                    }
                }
            }
        }
        Map<Integer, SongData[]> result = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (int band = 1; band <= ceiling; band++) {
            Map<String, SongData> levelSongs = candidates.get(band);
            if (levelSongs == null || levelSongs.isEmpty()) {
                continue;
            }
            SongData[] uniqueSongs = levelSongs.entrySet().stream()
                    .filter(entry -> seen.add(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toArray(SongData[]::new);
            if (uniqueSongs.length > 0) {
                result.put(band, uniqueSongs);
            }
        }
        return result;
    }

    private static void openNominationCandidateFolder(int targetBand) {
        MainController controller = main;
        String matchId = currentMatchId;
        if (controller == null || Gdx.app == null) {
            return;
        }
        Gdx.app.postRunnable(() -> {
            if (
                    controller != main
                            || !isNominationOpen()
                            || !matchId.equals(currentMatchId)
                            || !(controller.getCurrentState()
                                    instanceof MusicSelector selector)
            ) {
                return;
            }
            Map<Integer, SongData[]> candidatesByLevel =
                    nominationCandidateElementsByLevel(
                            selector.getBarManager().getTables(),
                            targetBand
                    );
            SongData[] candidates = candidatesByLevel.values().stream()
                    .flatMap(Arrays::stream)
                    .toArray(SongData[]::new);
            if (candidates.length == 0) {
                arenaUiMessage =
                        "通常／発狂BMS難易度表が見つかりません。現在の一覧から選曲してください";
                ImGuiNotify.warning(
                        "Arena選曲: 通常／発狂BMS難易度表の候補を読み込めませんでした"
                );
                return;
            }
            SongData[] localSongs = playableOwnedSongs(
                    selector.getSongDatabase().getSongDatas(
                            Arrays.stream(candidates)
                                    .map(SongData::getMd5)
                                    .toArray(String[]::new)
                    )
            );
            Map<Integer, SongData[]> ownedByLevel =
                    playableOwnedSongsByLevel(candidatesByLevel, localSongs);
            int ownedCount = ownedByLevel.values().stream()
                    .mapToInt(songs -> songs.length)
                    .sum();
            if (ownedCount == 0) {
                arenaUiMessage =
                        "選曲可能な所持譜面がありません。ランダム選曲を利用できます";
                ImGuiNotify.warning(
                        "Arena選曲: ☆1～"
                                + arenaBandLabel(targetBand)
                                + "の所持譜面がありません"
                );
                return;
            }
            ArenaNominationRootBar arenaFolder = new ArenaNominationRootBar(
                    selector,
                    ownedByLevel
            );
            // A completed Arena round can leave the previous temporary folder
            // in the selector stack. Reopen from root so the directory label
            // does not grow by another Arena folder on every auto-requeue.
            selector.getBarManager().updateBar(null);
            if (selector.getBarManager().updateBar(arenaFolder)) {
                ImGuiNotify.info(
                        "Arena選曲候補を表示しました（所持"
                                + ownedCount
                                + "譜面）",
                        5000
                );
            }
        });
    }

    private static void openFreeNominationRoot() {
        MainController controller = main;
        String matchId = currentMatchId;
        if (controller == null || Gdx.app == null) {
            return;
        }
        Gdx.app.postRunnable(() -> {
            if (
                    controller != main
                            || !isNominationOpen()
                            || !matchId.equals(currentMatchId)
                            || !(controller.getCurrentState()
                                    instanceof MusicSelector selector)
            ) {
                return;
            }
            selector.getBarManager().updateBar(null);
            arenaUiMessage = "所持譜面から自由に選曲してください";
            ImGuiNotify.info(
                    "Arena自由選曲: 所持譜面から1曲選んでください",
                    5000
            );
        });
    }

    static SongData[] playableOwnedSongs(SongData[] songs) {
        Map<String, SongData> playable = new LinkedHashMap<>();
        if (songs == null) {
            return SongData.EMPTY;
        }
        for (SongData song : songs) {
            if (song == null || song.getPath() == null || song.getPath().isBlank()) {
                continue;
            }
            String sha256 = song.getSha256();
            String md5 = song.getMd5();
            String key = sha256 != null && !sha256.isBlank()
                    ? sha256.toLowerCase(Locale.ROOT)
                    : md5 == null ? "" : md5.toLowerCase(Locale.ROOT);
            if (!key.isBlank()) {
                playable.putIfAbsent(key, song);
            }
        }
        return playable.values().toArray(SongData[]::new);
    }

    static Map<Integer, SongData[]> playableOwnedSongsByLevel(
            Map<Integer, SongData[]> candidatesByLevel,
            SongData[] ownedSongs
    ) {
        Map<String, SongData> ownedByMd5 = new LinkedHashMap<>();
        for (SongData song : playableOwnedSongs(ownedSongs)) {
            String md5 = song.getMd5();
            if (md5 != null && !md5.isBlank()) {
                ownedByMd5.putIfAbsent(
                        md5.toLowerCase(Locale.ROOT),
                        song
                );
            }
        }
        Map<Integer, SongData[]> result = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        if (candidatesByLevel == null) {
            return result;
        }
        for (Map.Entry<Integer, SongData[]> entry : candidatesByLevel.entrySet()) {
            Map<String, SongData> levelSongs = new LinkedHashMap<>();
            for (SongData candidate : entry.getValue()) {
                String md5 = candidate == null ? "" : candidate.getMd5();
                String key = md5 == null
                        ? ""
                        : md5.toLowerCase(Locale.ROOT);
                SongData owned = ownedByMd5.get(key);
                if (!key.isBlank() && owned != null && seen.add(key)) {
                    levelSongs.put(key, owned);
                }
            }
            if (!levelSongs.isEmpty()) {
                result.put(
                        entry.getKey(),
                        levelSongs.values().toArray(SongData[]::new)
                );
            }
        }
        return result;
    }

    static SongData highestOwnedCpuChart(
            Map<Integer, SongData[]> ownedByLevel
    ) {
        if (ownedByLevel == null || ownedByLevel.isEmpty()) {
            return null;
        }
        int highest = ownedByLevel.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-1);
        SongData[] songs = ownedByLevel.get(highest);
        if (songs == null || songs.length == 0) {
            return null;
        }
        return songs[ThreadLocalRandom.current().nextInt(songs.length)];
    }

    private static void respondToCpuChartRequest(JsonNode message) {
        if (!isCurrentMatchMessage(message)) {
            return;
        }
        receiveRules(message);
        nominationView = message;
        nominationOpen = true;
        nominationTargetBand = Math.max(
                1,
                message.path("target_band").asInt(1)
        );
        nominationDeadlineMillis = Math.round(
                message.path("deadline").asDouble() * 1000.0
        );
        arenaUiMessage = "CPUの課題曲を所持譜面から選んでいます";
        MainController controller = main;
        if (controller == null || Gdx.app == null) {
            sendCpuChartCandidate("");
            return;
        }
        String matchId = currentMatchId;
        Gdx.app.postRunnable(() -> {
            if (
                    controller != main
                            || !matchId.equals(currentMatchId)
                            || !isNominationOpen()
            ) {
                return;
            }
            try {
                TableData[] tables = new TableDataAccessor(
                        controller.getConfig().getTablepath()
                ).readAll();
                Map<Integer, SongData[]> candidates =
                        nominationCandidateElementsByLevel(
                                tables,
                                nominationTargetBand
                        );
                String[] md5s = candidates.values().stream()
                        .flatMap(Arrays::stream)
                        .map(SongData::getMd5)
                        .filter(value -> value != null && !value.isBlank())
                        .toArray(String[]::new);
                SongData[] localSongs = playableOwnedSongs(
                        controller.getSongDatabase().getSongDatas(md5s)
                );
                SongData selected = highestOwnedCpuChart(
                        playableOwnedSongsByLevel(candidates, localSongs)
                );
                sendCpuChartCandidate(
                        selected == null ? "" : selected.getMd5()
                );
            } catch (RuntimeException exception) {
                logger.warn(
                        "BMS-IR Arena CPU chart lookup failed: {}",
                        exception.getMessage()
                );
                sendCpuChartCandidate("");
            }
        });
    }

    private static void sendCpuChartCandidate(String chartHash) {
        ObjectNode reply = baseMatchMessage("cpu_chart_candidate");
        reply.put("chart_hash", chartHash == null ? "" : chartHash);
        send(reply);
    }

    public static void requestCurrentChartNomination() {
        if (!isNominationOpen()) {
            return;
        }
        SongData song = currentNominationSong();
        if (song == null) {
            ImGuiNotify.warning("Arena選曲: 通常の楽曲譜面を選んでください");
            return;
        }
        ObjectNode message = baseMatchMessage("chart_nominate");
        message.put("chart_hash", song.getMd5());
        arenaUiMessage = "選曲を確認しています";
        send(message);
    }

    static void requestRandomNomination() {
        if (!isNominationOpen()) {
            return;
        }
        arenaUiMessage = "ランダム選曲を登録しています";
        send(baseMatchMessage("chart_nomination_skip"));
    }

    static boolean isShowingCompletedResult() {
        return resultView.isObject() && resultView.size() > 0;
    }

    static boolean isGameplayState() {
        return "play".equals(normalizeCurrentState());
    }

    static void requestQueueEntry() {
        ObjectNode message = queueEntryMessage(playerConfig());
        arenaUiMessage = "エントリーを送信しています";
        send(message);
    }

    static ObjectNode queueEntryMessage(PlayerConfig config) {
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "queue_entry");
        message.put("ruleset_profile", BMSPlayerRule.PROFILE_LR2);
        message.put(
                "unrestricted_rating",
                config != null && config.isBmsirArenaUnrestrictedRating()
        );
        message.put(
                "allow_cpu",
                config == null || config.isBmsirArenaAllowCpu()
        );
        return message;
    }

    static void requestRoomEntry(
            String matchMode,
            String scoreRule,
            String forcedGauge,
            String chartScope,
            String roomCode
    ) {
        requestRoomEntry(
                matchMode,
                scoreRule,
                forcedGauge,
                chartScope,
                roomCode,
                "",
                ""
        );
    }

    static void requestRoomEntry(
            String matchMode,
            String scoreRule,
            String forcedGauge,
            String chartScope,
            String roomCode,
            String roomName,
            String roomPassword
    ) {
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "room_entry");
        message.put("match_mode", matchMode);
        message.put("score_rule", scoreRule);
        message.put("forced_gauge", forcedGauge);
        message.put("chart_scope", chartScope);
        message.put("room_code", normalizeRoomCode(roomCode));
        message.put("room_name", roomName == null ? "" : roomName);
        message.put("room_password", roomPassword == null ? "" : roomPassword);
        PlayerConfig config = playerConfig();
        message.put(
                "ruleset_profile",
                config == null
                        ? BMSPlayerRule.PROFILE_LR2
                        : config.getBmsirRulesetProfile()
        );
        message.put(
                "nomination_policy",
                config == null ? "all" : config.getBmsirArenaNominationPolicy()
        );
        message.put(
                "nomination_seconds",
                config == null ? 60 : config.getBmsirArenaNominationSeconds()
        );
        message.put(
                "option_seconds",
                config == null ? 10 : config.getBmsirArenaOptionSeconds()
        );
        message.put(
                "intermission_seconds",
                config == null ? 0 : config.getBmsirArenaIntermissionSeconds()
        );
        message.put(
                "series_format",
                config == null ? "single" : config.getBmsirArenaSeriesFormat()
        );
        message.put(
                "first_to_wins",
                config == null ? 2 : config.getBmsirArenaFirstToWins()
        );
        message.put(
                "stay_in_room",
                config != null && config.isBmsirArenaStayInRoom()
        );
        message.put(
                "room_participating",
                config == null || config.isBmsirArenaRoomParticipating()
        );
        message.put(
                "spectator_public",
                config != null && config.isBmsirArenaSpectatorPublic()
        );
        message.put(
                "force_host_option",
                config != null && config.isBmsirArenaForceHostOption()
        );
        arenaUiMessage = "部屋へ参加しています";
        roomReady = false;
        send(message);
    }

    static String normalizeRoomCode(String roomCode) {
        return roomCode == null
                ? ""
                : roomCode.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    static void requestRoomSettings(
            String scoreRule,
            String forcedGauge,
            String chartScope,
            String roomName,
            String roomPassword,
            boolean updatePassword
    ) {
        PlayerConfig config = playerConfig();
        if (config == null || !isCurrentRoomHost()) {
            return;
        }
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "room_settings");
        message.put("score_rule", scoreRule);
        message.put("forced_gauge", forcedGauge);
        message.put("chart_scope", chartScope);
        message.put("room_name", roomName == null ? "" : roomName);
        message.put("room_password", roomPassword == null ? "" : roomPassword);
        message.put("update_password", updatePassword);
        message.put("nomination_policy", config.getBmsirArenaNominationPolicy());
        message.put("nomination_seconds", config.getBmsirArenaNominationSeconds());
        message.put("option_seconds", config.getBmsirArenaOptionSeconds());
        message.put("intermission_seconds", config.getBmsirArenaIntermissionSeconds());
        message.put("series_format", config.getBmsirArenaSeriesFormat());
        message.put("first_to_wins", config.getBmsirArenaFirstToWins());
        message.put("ruleset_profile", config.getBmsirRulesetProfile());
        message.put("spectator_public", config.isBmsirArenaSpectatorPublic());
        message.put("force_host_option", config.isBmsirArenaForceHostOption());
        arenaUiMessage = "部屋設定を更新しています";
        send(message);
    }

    static void requestRoomSettings() {
        requestRoomSettings(
                currentScoreRule(),
                currentForcedGauge(),
                currentChartScope(),
                activeRulesOrQueue().path("room_name").asText(""),
                "",
                false
        );
    }

    static void requestRoomReady(boolean ready) {
        roomReady = ready;
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "room_ready");
        message.put("ready", ready);
        send(message);
        sendState(normalizeCurrentState(), readyForArena(normalizeCurrentState()));
    }

    static void requestRoomParticipation(boolean participating) {
        PlayerConfig config = playerConfig();
        if (config != null) {
            config.setBmsirArenaRoomParticipating(participating);
        }
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "room_participation");
        message.put("participating", participating);
        send(message);
    }

    static void requestRoomDisband() {
        if (!isCurrentRoomHost()) {
            return;
        }
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "room_disband");
        send(message);
    }

    static void requestRoomKick(int playerId) {
        if (!isCurrentRoomHost() || playerId <= 0 || playerId == currentPlayerId) {
            return;
        }
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "room_kick");
        message.put("player_id", playerId);
        send(message);
    }

    static void requestRoomTransferHost(int playerId) {
        if (!isCurrentRoomHost() || playerId <= 0 || playerId == currentPlayerId) {
            return;
        }
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "room_transfer_host");
        message.put("player_id", playerId);
        send(message);
    }

    static void requestRoomSetSelector(int playerId) {
        if (!isCurrentRoomHost() || playerId <= 0) {
            return;
        }
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "room_set_selector");
        message.put("player_id", playerId);
        send(message);
    }

    static void requestRoomStay(boolean stayInRoom) {
        if ("ranked".equals(currentMatchMode())) {
            return;
        }
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "room_stay");
        message.put("stay_in_room", stayInRoom);
        send(message);
    }

    static void requestChat(String text) {
        String roomCode = currentRoomCode();
        if ((!reserved || currentMatchId.isBlank()) && roomCode.isBlank()) {
            return;
        }
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "chat_send");
        if (!currentMatchId.isBlank()) {
            message.put("match_id", currentMatchId);
        }
        if (!roomCode.isBlank()) {
            message.put("room_code", roomCode);
        }
        message.put("text", text == null ? "" : text);
        send(message);
    }

    static void requestLobbyChat(String text) {
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "lobby_chat_send");
        message.put("text", text == null ? "" : text);
        send(message);
    }

    static List<JsonNode> chatMessages() {
        synchronized (CHAT_LOCK) {
            return List.copyOf(chatMessages);
        }
    }

    static List<JsonNode> lobbyChatMessages() {
        synchronized (CHAT_LOCK) {
            return List.copyOf(lobbyChatMessages);
        }
    }

    static void requestForceEndVote() {
        if (!arenaPlayActive || currentMatchId.isBlank()) {
            return;
        }
        send(baseMatchMessage("force_end_vote"));
    }

    private static void clearChatMessages() {
        synchronized (CHAT_LOCK) {
            chatMessages.clear();
        }
    }

    private static void receiveChat(JsonNode message) {
        String incomingMatchId = message.path("match_id").asText("");
        String incomingRoomCode = message.path("room_code").asText("");
        boolean matchScoped = !currentMatchId.isBlank()
                && currentMatchId.equals(incomingMatchId);
        boolean roomScoped = !currentRoomCode().isBlank()
                && currentRoomCode().equals(incomingRoomCode);
        if (!matchScoped && !roomScoped) {
            return;
        }
        synchronized (CHAT_LOCK) {
            if ("chat_history".equals(message.path("type").asText())) {
                chatMessages.clear();
                message.path("messages").forEach(chatMessages::add);
            } else {
                chatMessages.add(message);
                while (chatMessages.size() > 50) {
                    chatMessages.remove(0);
                }
            }
        }
    }

    private static void receiveLobbyChat(JsonNode message) {
        synchronized (CHAT_LOCK) {
            if ("lobby_chat_history".equals(message.path("type").asText())) {
                lobbyChatMessages.clear();
                message.path("messages").forEach(lobbyChatMessages::add);
            } else {
                lobbyChatMessages.add(message);
                while (lobbyChatMessages.size() > 50) {
                    lobbyChatMessages.remove(0);
                }
            }
        }
    }

    public static void applySynchronizedRandomSeed(ReplayData playinfo) {
        if (
                !arenaPlayActive
                        || playinfo == null
                        || currentRandomSeed < 0L
                        || main == null
        ) {
            return;
        }
        long seed = synchronizedRandomSeed(
                currentRandomSeed,
                main.getPlayerConfig().isBmsirArenaRandomMirror()
        );
        if (usesSynchronizedRandomSeed(playinfo.randomoption)) {
            playinfo.randomoptionseed = seed;
        }
        if (usesSynchronizedRandomSeed(playinfo.randomoption2)) {
            playinfo.randomoption2seed = seed;
        }
    }

    static boolean usesSynchronizedRandomSeed(int option) {
        return option == 2;
    }

    static long synchronizedRandomSeed(long seed, boolean mirror) {
        if (!mirror) {
            return seed;
        }
        new RandomTrainer();
        if (RandomTrainer.getRandomSeedMap() == null) {
            return seed;
        }
        String reversed = new StringBuilder(
                LR2RandomPattern.getRajaLaneOrder(seed, false)
        ).reverse().toString();
        Long mirrored = RandomTrainer.getRandomSeedMap().get(
                Integer.parseInt(reversed)
        );
        return mirrored == null ? seed : mirrored;
    }

    static void requestQueueCancel() {
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "queue_cancel");
        arenaUiMessage = isFillWaiting()
                ? "マッチから退出しています"
                : reserved
                        ? "棄権を送信しています"
                        : "待機解除を送信しています";
        send(message);
    }

    static void requestArenaStatus() {
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "arena_status");
        send(message);
    }

    static void requestArenaManual() {
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "arena_manual");
        send(message);
    }

    public static boolean isAbortInputBlocked() {
        return arenaPlayActive
                && main != null
                && main.getPlayerConfig().isBmsirArenaEnabled();
    }

    public static void enforceArenaOptions() {
        if (arenaPlayActive && main != null) {
            applyFixedOptions(
                    main.getPlayerConfig(),
                    currentForcedGauge()
            );
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
                        && "select".equals(value)
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
        if ("private".equals(currentMatchMode())) {
            return roomReady
                    && ("select".equals(state)
                            || ("result".equals(state) && normalResultReady));
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
        message.put("minbp", arenaMinBp(score));
        message.put("max_combo", Math.max(0, score.getCombo()));
        message.put("play_option", currentPlayOption);
        message.put("ln_mode", "LN");
        if (currentPlayMode > 0) {
            message.put("play_mode", currentPlayMode);
        }
        send(message);
    }

    public static void onMusicResultPrepared(ScoreData score, boolean hardFail) {
        if (!arenaPlayActive || score == null || currentMatchId.isBlank()) {
            return;
        }
        ObjectNode message = baseMatchMessage("final");
        message.put("seq", sequence.incrementAndGet());
        message.put("exscore", score.getExscore());
        message.put(
                "processed_notes",
                finalProcessedNotes(score, hardFail, currentChartTotalNotes)
        );
        message.put("minbp", arenaMinBp(score));
        message.put("max_combo", Math.max(0, score.getCombo()));
        message.put("state", hardFail ? "hard_fail" : "complete");
        message.put("clear_type", score.getClear());
        message.put("play_option", currentPlayOption);
        message.put("ln_mode", "LN");
        if (currentPlayMode > 0) {
            message.put("play_mode", currentPlayMode);
        }
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

    static int arenaMinBp(ScoreData score) {
        if (score == null) {
            return 0;
        }
        int stored = score.getMinbp();
        if (stored >= 0 && stored <= 1_000_000) {
            return stored;
        }
        int current = 0;
        for (int judge = 3; judge <= 5; judge++) {
            current += Math.max(0, score.getJudgeCount(judge));
        }
        return current;
    }

    static int finalProcessedNotes(
            ScoreData score,
            boolean hardFail,
            int expectedChartNotes
    ) {
        if (!hardFail && expectedChartNotes > 0) {
            return expectedChartNotes;
        }
        return processedNotes(score);
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
            BMSIRArenaLog.event(
                    "send_skipped",
                    "type", message.path("type").asText(""),
                    "match_id", message.path("match_id").asText(""),
                    "reason", "socket_not_open"
            );
            return;
        }
        try {
            BMSIRArenaLog.message("outbound", message);
            current.send(JSON.writeValueAsString(message));
        } catch (Exception e) {
            logger.warn("BMS-IR Arena send failed: {}", e.getMessage());
            BMSIRArenaLog.event(
                    "send_failed",
                    "type", message.path("type").asText(""),
                    "match_id", message.path("match_id").asText(""),
                    "error", e.getClass().getSimpleName(),
                    "message", e.getMessage()
            );
        }
    }

    private static void handleMessage(String raw) {
        try {
            JsonNode message = JSON.readTree(raw);
            BMSIRArenaLog.message("inbound", message);
            switch (message.path("type").asText()) {
                case "hello_ok" -> {
                    connected = true;
                    arenaUiMessage = "Arenaサーバーへ接続しました";
                    updateServerClock(message);
                    ImGuiNotify.info("BMS-IR Arenaへ接続しました");
                    String activeMatchId = message.path("active_match_id").asText();
                    if (reserved && activeMatchId.isBlank()) {
                        reserved = false;
                        arenaPlayPending = false;
                        arenaPlayActive = false;
                        playReadySent = false;
                        serverStartMillis = 0L;
                        currentMatchId = "";
                        pendingFinal = null;
                        currentPlayOption = 0;
                        currentPlayMode = 0;
                        currentChartTotalNotes = 0;
                        currentRandomSeed = -1L;
                        forfeitRequested = false;
                        clearNominationState();
                        restoreOptionsWhenSafe();
                        ImGuiNotify.info("Arenaの試合状態を同期しました", 8000);
                    }
                    if (pendingFinal != null && !activeMatchId.isBlank()) {
                        send(pendingFinal);
                    }
                    if (
                            arenaPlayActive
                                    && playReadySent
                                    && !activeMatchId.isBlank()
                    ) {
                        sendPlayReady(
                                Client.state.getSelectedSongRemote().getMd5()
                        );
                    }
                    sendState(normalizeCurrentState(), readyForArena(normalizeCurrentState()));
                }
                case "arena_status" -> receiveArenaStatus(message);
                case "arena_manual" -> {
                    JsonNode accepted = BMSIRArenaManual.accept(message, JSON);
                    if (accepted.isObject() && accepted.size() > 0) {
                        manualView = accepted;
                    }
                }
                case "pong" -> updateServerClock(message);
                case "fill_started" -> receiveFillStarted(message);
                case "players_updated" -> receivePlayersUpdated(message);
                case "room_status" -> {
                    roomView = message;
                    if (!arenaPlayActive && message.path("rules").isObject()) {
                        rulesView = message.path("rules");
                    }
                    for (JsonNode player : message.path("players")) {
                        if (player.path("player_id").asInt() == currentPlayerId) {
                            roomReady = player.path("ready").asBoolean(false);
                            break;
                        }
                    }
                }
                case "chat", "chat_history" -> receiveChat(message);
                case "lobby_chat", "lobby_chat_history" ->
                        receiveLobbyChat(message);
                case "match_reserved" -> {
                    String incomingMatchId = message.path("match_id").asText();
                    boolean sameMatch = reserved && currentMatchId.equals(incomingMatchId);
                    currentMatchId = incomingMatchId;
                    reserved = true;
                    queueStatus = "reserved";
                    arenaUiMessage = "マッチングしました";
                    receiveRules(message);
                    if (!sameMatch) {
                        String currentState = normalizeCurrentState();
                        if ("select".equals(currentState)) {
                            normalResultReady = true;
                        } else if ("decide".equals(currentState) || "play".equals(currentState)) {
                            normalResultReady = false;
                        }
                        sequence.set(0);
                        currentPlayOption = 0;
                        currentPlayMode = 0;
                        currentChartTotalNotes = 0;
                        currentRandomSeed = -1L;
                        playReadySent = false;
                        serverStartMillis = 0L;
                        forfeitRequested = false;
                        clearNominationState();
                        clearChatMessages();
                        ImGuiNotify.info("マッチングしました！ 現在のプレイ終了後にArenaへ移動します", 8000);
                    }
                    sendState(normalizeCurrentState(), readyForArena(normalizeCurrentState()));
                }
                case "nomination_started", "nomination_status" ->
                        receiveNominationStatus(message);
                case "cpu_chart_request" ->
                        respondToCpuChartRequest(message);
                case "nomination_accepted" -> {
                    if (currentMatchId.equals(message.path("match_id").asText())) {
                        arenaUiMessage = "選曲を受け付けました";
                        ImGuiNotify.info("Arena選曲を受け付けました", 3000);
                    }
                }
                case "chart_candidate_rejected" -> {
                    if (isCurrentMatchMessage(message)) {
                        int missingCount = message.path("missing_player_ids").size();
                        arenaUiMessage = missingCount > 0
                                ? "未所持者がいるため別の候補を確認しています"
                                : "この候補は使用できないため再抽選しています";
                        ImGuiNotify.warning(
                                "Arena: "
                                        + arenaUiMessage
                                        + (missingCount > 0
                                                ? "（" + missingCount + "人）"
                                                : ""),
                                5000
                        );
                    }
                }
                case "nominations_revealed" -> receiveNominationsRevealed(message);
                case "chart" -> receiveChart(message);
                case "option_select" -> receiveOptionSelection(message);
                case "prepare" -> prepareArenaPlay(message);
                case "start" -> receiveArenaStart(message);
                case "live" -> receiveLiveView(message);
                case "match_resume" -> {
                    if (!isCurrentMatchMessage(message)) {
                        BMSIRArenaLog.event(
                                "message_ignored",
                                "type", "match_resume",
                                "match_id", message.path("match_id").asText(""),
                                "active_match_id", currentMatchId
                        );
                        logger.debug(
                                "Ignoring Arena match_resume for another match: {}",
                                message.path("match_id").asText()
                        );
                        return;
                    }
                    String incomingMatchId = message.path("match_id").asText();
                    currentMatchId = incomingMatchId;
                    reserved = true;
                    currentRandomSeed = message.path("random_seed").asLong(-1L);
                    clearFillState();
                    if ("countdown".equals(message.path("state").asText()) && arenaPlayActive) {
                        receiveArenaStart(message);
                    } else if (!arenaPlayActive) {
                        ImGuiNotify.warning("BMS-IR Arena: 試合へ再接続しましたが、プレイ状態を復元できません");
                    }
                }
                case "result" -> {
                    if (!isCurrentMatchMessage(message)) {
                        BMSIRArenaLog.event(
                                "message_ignored",
                                "type", "result",
                                "match_id", message.path("match_id").asText(""),
                                "active_match_id", currentMatchId
                        );
                        logger.debug(
                                "Ignoring Arena result for another match: {}",
                                message.path("match_id").asText()
                        );
                        return;
                    }
                    receiveRules(message);
                    resultView = message;
                    liveView = message;
                    boolean autoReentered = false;
                    for (JsonNode playerId : message.path("auto_reentry_player_ids")) {
                        if (playerId.asInt() == currentPlayerId) {
                            autoReentered = true;
                            break;
                        }
                    }
                    for (JsonNode player : message.path("players")) {
                        if (player.path("player_id").asInt() == currentPlayerId) {
                            arenaRating = player.path("after").asDouble(arenaRating);
                            if (message.path("rated").asBoolean(false)) {
                                arenaMatchesPlayed++;
                            }
                            break;
                        }
                    }
                    queueStatus = autoReentered ? "queued" : "cancelled";
                    roomReady = false;
                    boolean roomMatch =
                            !"ranked".equals(currentMatchMode());
                    arenaUiMessage = autoReentered
                            ? (roomMatch
                                    ? "対戦終了。この部屋で次の対戦を待っています"
                                    : "対戦終了。次の対戦を待機しています")
                            : "対戦終了。自動エントリーを終了しました";
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    playReadySent = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    currentPlayOption = 0;
                    currentPlayMode = 0;
                    currentChartTotalNotes = 0;
                    currentRandomSeed = -1L;
                    serverStartMillis = 0L;
                    clearNominationState();
                    restoreOptionsWhenSafe();
                    ImGuiNotify.info(
                            autoReentered
                                    ? (roomMatch
                                            ? "Arena終了。この部屋に残りました"
                                            : "Arena終了。次の対戦を待機しています")
                                    : "Arena終了。自動エントリーを終了しました",
                            8000
                    );
                    sendState("result", false);
                    requestArenaStatus();
                }
                case "forfeit_accepted" -> {
                    if (!isCurrentMatchMessage(message)) {
                        BMSIRArenaLog.event(
                                "message_ignored",
                                "type", "forfeit_accepted",
                                "match_id", message.path("match_id").asText(""),
                                "active_match_id", currentMatchId
                        );
                        logger.debug(
                                "Ignoring Arena forfeit_accepted for another match: {}",
                                message.path("match_id").asText()
                        );
                        return;
                    }
                    queueStatus = "cancelled";
                    arenaUiMessage = "対戦を棄権しました";
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    playReadySent = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    currentPlayOption = 0;
                    currentPlayMode = 0;
                    currentChartTotalNotes = 0;
                    currentRandomSeed = -1L;
                    serverStartMillis = 0L;
                    normalResultReady = true;
                    roomReady = false;
                    clearNominationState();
                    clearChatMessages();
                    restoreOptionsWhenSafe();
                    ImGuiNotify.warning("Arenaの対戦を棄権しました。自動エントリーを終了します");
                    sendState(normalizeCurrentState(), false);
                    requestArenaStatus();
                }
                case "match_cancelled" -> {
                    if (!isCurrentMatchMessage(message)) {
                        BMSIRArenaLog.event(
                                "message_ignored",
                                "type", "match_cancelled",
                                "match_id", message.path("match_id").asText(""),
                                "active_match_id", currentMatchId
                        );
                        logger.debug(
                                "Ignoring Arena match_cancelled for another match: {}",
                                message.path("match_id").asText()
                        );
                        return;
                    }
                    arenaUiMessage = "試合がキャンセルされました";
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    playReadySent = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    currentPlayOption = 0;
                    currentPlayMode = 0;
                    currentChartTotalNotes = 0;
                    currentRandomSeed = -1L;
                    serverStartMillis = 0L;
                    clearNominationState();
                    roomReady = false;
                    clearChatMessages();
                    restoreOptionsWhenSafe();
                    ImGuiNotify.warning("Arenaの試合がキャンセルされました");
                    requestArenaStatus();
                }
                case "room_disbanded" -> {
                    if (
                            !message.path("match_id").asText("").isBlank()
                                    && !isCurrentMatchMessage(message)
                    ) {
                        return;
                    }
                    queueStatus = "cancelled";
                    arenaUiMessage = "プライベートルームが解体されました";
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    playReadySent = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    currentPlayOption = 0;
                    currentPlayMode = 0;
                    currentChartTotalNotes = 0;
                    currentRandomSeed = -1L;
                    serverStartMillis = 0L;
                    clearNominationState();
                    clearFillState();
                    clearChatMessages();
                    roomView = JSON.createObjectNode();
                    roomReady = false;
                    restoreOptionsWhenSafe();
                    ImGuiNotify.info("Arenaプライベートルームを解体しました", 5000);
                    requestArenaStatus();
                }
                case "room_kicked" -> {
                    queueStatus = "cancelled";
                    arenaUiMessage = "プライベートルームから退出しました";
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    playReadySent = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    clearNominationState();
                    clearFillState();
                    clearChatMessages();
                    roomView = JSON.createObjectNode();
                    roomReady = false;
                    restoreOptionsWhenSafe();
                    ImGuiNotify.warning("部屋主によって退出されました", 6000);
                    requestArenaStatus();
                }
                case "match_released" -> {
                    if (!isCurrentMatchMessage(message)) {
                        BMSIRArenaLog.event(
                                "message_ignored",
                                "type", "match_released",
                                "match_id", message.path("match_id").asText(""),
                                "active_match_id", currentMatchId
                        );
                        logger.debug(
                                "Ignoring Arena match_released for another match: {}",
                                message.path("match_id").asText()
                        );
                        return;
                    }
                    boolean queueRetained = message.path("queue_retained").asBoolean();
                    queueStatus = queueRetained ? "queued" : "cancelled";
                    arenaUiMessage = queueRetained
                            ? "今回の組み合わせから外れました。待機は継続しています"
                            : "今回の組み合わせから外れました";
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    playReadySent = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    currentPlayOption = 0;
                    currentPlayMode = 0;
                    currentChartTotalNotes = 0;
                    currentRandomSeed = -1L;
                    serverStartMillis = 0L;
                    clearNominationState();
                    roomReady = false;
                    clearChatMessages();
                    restoreOptionsWhenSafe();
                    ImGuiNotify.warning(
                            queueRetained
                                    ? "Arenaの今回の組み合わせから外れました。待機は継続しています"
                                    : "Arenaの今回の組み合わせから外れました"
                    );
                }
                case "force_end_approved" -> {
                    if (!isCurrentMatchMessage(message)) {
                        return;
                    }
                    arenaUiMessage = "全員同意でこの曲を終了します";
                    if (main != null && Gdx.app != null) {
                        Gdx.app.postRunnable(
                                () -> {
                                    if (main.getCurrentState() instanceof BMSPlayer player) {
                                        player.stopPlay();
                                    }
                                }
                        );
                    }
                }
                case "replaced" -> ImGuiNotify.warning("BMS-IR Arena: 同じアカウントの別本体へ接続を移しました");
                case "error" -> {
                    String code = message.path("code").asText();
                    arenaUiMessage = switch (code) {
                        case "nomination_rejected" ->
                                "選曲できません。部屋の選曲範囲と所持譜面を確認してください";
                        case "room_entry_failed" ->
                                "部屋へ参加できません: "
                                        + message.path("message").asText(code);
                        case "room_settings_failed" ->
                                "部屋設定を変更できません: "
                                        + message.path("message").asText(code);
                        case "room_disband_failed" ->
                                "部屋を解体できません: "
                                        + message.path("message").asText(code);
                        case "room_kick_failed" ->
                                "参加者をキックできません: "
                                        + message.path("message").asText(code);
                        case "room_transfer_host_failed" ->
                                "HOSTを移譲できません: "
                                        + message.path("message").asText(code);
                        case "room_set_selector_failed" ->
                                "選曲担当を変更できません: "
                                        + message.path("message").asText(code);
                        case "nomination_closed" -> "選曲受付は終了しました";
                        default -> message.path("message").asText(code);
                    };
                    ImGuiNotify.error("BMS-IR Arena: " + arenaUiMessage);
                }
                default -> {
                }
            }
        } catch (Exception e) {
            logger.warn("BMS-IR Arena message parse failed", e);
            BMSIRArenaLog.event(
                    "message_parse_failed",
                    "error", e.getClass().getSimpleName(),
                    "message", e.getMessage(),
                    "raw_length", raw == null ? 0 : raw.length()
            );
        }
    }

    static boolean isCurrentMatchMessage(JsonNode message) {
        return matchMessageMatches(currentMatchId, message);
    }

    static boolean matchMessageMatches(String activeMatchId, JsonNode message) {
        return activeMatchId != null
                && !activeMatchId.isBlank()
                && message != null
                && activeMatchId.equals(message.path("match_id").asText());
    }

    static void receiveArenaStatus(JsonNode message) {
        JsonNode player = message.path("player");
        arenaRating = player.path("rating_exact").asDouble(
                player.path("rating").asDouble(1000.0)
        );
        arenaMatchesPlayed = player.path("matches_played").asInt(0);
        queueView = player.path("queue");
        queueStatus = queueView.path("status").asText("idle");
        rankingView = message.path("ranking");
        publicRoomsView = message.path("public_rooms");
        if (message.path("lobby_chat").isArray()) {
            synchronized (CHAT_LOCK) {
                lobbyChatMessages.clear();
                message.path("lobby_chat").forEach(lobbyChatMessages::add);
            }
        }
        PlayerConfig config = playerConfig();
        if (
                config != null
                        && currentPlayerId > 0
                        && queueView.path("room_host_id").asInt(0)
                                == currentPlayerId
        ) {
            config.setBmsirArenaNominationPolicy(
                    queueView.path("nomination_policy").asText("all")
            );
            config.setBmsirArenaNominationSeconds(
                    queueView.path("nomination_seconds").asInt(60)
            );
            config.setBmsirArenaOptionSeconds(
                    queueView.path("option_seconds").asInt(10)
            );
            config.setBmsirArenaIntermissionSeconds(
                    queueView.path("intermission_seconds").asInt(0)
            );
            config.setBmsirArenaSeriesFormat(
                    queueView.path("series_format").asText("single")
            );
            config.setBmsirArenaFirstToWins(
                    queueView.path("first_to_wins").asInt(2)
            );
            config.setBmsirArenaSpectatorPublic(
                    queueView.path("spectator_public").asBoolean(false)
            );
            config.setBmsirArenaForceHostOption(
                    queueView.path("force_host_option").asBoolean(false)
            );
        }
        if (config != null && "private".equals(queueView.path("match_mode").asText())) {
            config.setBmsirArenaRoomParticipating(
                    queueView.path("room_participating").asBoolean(true)
            );
        }
        if (!"private".equals(queueView.path("match_mode").asText())) {
            roomReady = false;
        }
        arenaUiMessage = switch (queueStatus) {
            case "queued" -> "対戦相手を待っています";
            case "reserved", "matched" -> "対戦準備中です";
            case "withdraw_requested" -> "棄権処理中です";
            default -> "エントリーできます";
        };
    }

    private static void receiveRules(JsonNode message) {
        JsonNode incoming = message.path("rules");
        if (incoming.isObject() && incoming.size() > 0) {
            rulesView = incoming;
        }
    }

    static void receiveNominationStatus(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        clearFillState();
        receiveRules(message);
        int targetBand = message.path("target_band").asInt(1);
        String chartScope = message.path("chart_scope").asText(
                currentChartScope()
        );
        boolean canNominate = message.path("can_nominate").asBoolean(true);
        boolean shouldOpenCandidates =
                !nominationOpen
                        || nominationTargetBand != targetBand
                        || !chartScope.equals(currentChartScope());
        nominationView = message;
        nominationOpen = true;
        nominationTargetBand = targetBand;
        nominationDeadlineMillis = Math.round(
                message.path("deadline").asDouble() * 1000.0
        );
        queueStatus = "matched";
        String retryReason = message.path("retry_reason").asText();
        arenaUiMessage = !canNominate
                ? "選曲担当の選曲を待っています"
                : retryReason.isBlank()
                        ? (
                                message.path("required_count").asInt(1) > 1
                                        ? "Arena候補曲を必要数選んでください"
                                        : "次のArena課題曲を選んでください"
                        )
                        : "共通して所持する候補がないため、選曲し直してください";
        if (shouldOpenCandidates && canNominate) {
            if ("free".equals(chartScope)) {
                openFreeNominationRoot();
            } else {
                openNominationCandidateFolder(targetBand);
            }
        }
    }

    static void receiveNominationsRevealed(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        clearFillState();
        receiveRules(message);
        nominationView = message;
        nominationOpen = false;
        nominationDeadlineMillis = 0L;
        JsonNode chart = message.path("chart");
        String title = chart.path("title").asText();
        arenaUiMessage = title.isBlank()
                ? "抽選結果を確認しています"
                : "採用曲: " + title;
    }

    private static void clearNominationState() {
        nominationView = JSON.createObjectNode();
        nominationOpen = false;
        nominationDeadlineMillis = 0L;
        nominationTargetBand = 0;
        clearFillState();
        clearOptionSelection();
    }

    private static void receiveOptionSelection(JsonNode message) {
        if (
                !currentMatchId.equals(message.path("match_id").asText())
                        || main == null
        ) {
            return;
        }
        receiveRules(message);
        int incomingPlayMode = message.path("play_mode").asInt(0);
        if (supportedPlayMode(incomingPlayMode)) {
            currentPlayMode = incomingPlayMode;
            lastKnownPlayMode = incomingPlayMode;
        }
        boolean wasOpen = optionSelectionOpen;
        optionSelectionOpen = true;
        optionReadySent = message.path("ready").asBoolean(optionReadySent);
        optionDeadlineMillis = Math.round(
                message.path("deadline").asDouble() * 1000.0
        );
        queueStatus = "matched";
        arenaUiMessage = optionReadySent
                ? "OP確定済み。他の参加者を待っています"
                : "OPを選択してください";
        if (!wasOpen) {
            Gdx.app.postRunnable(() -> {
                if (!optionSelectionOpen || main == null) {
                    return;
                }
                applyFixedOptions(main.getPlayerConfig(), currentForcedGauge());
            });
        }
        scheduleOptionReady();
    }

    static boolean isOptionSelectionOpen() {
        return optionSelectionOpen
                && reserved
                && !currentMatchId.isBlank();
    }

    static boolean isOptionReadySent() {
        return optionReadySent;
    }

    static long optionSecondsRemaining() {
        return fillCountdownSeconds(
                optionDeadlineMillis,
                System.currentTimeMillis() + serverClockOffsetMillis
        );
    }

    static int optionReadyCount() {
        return currentMatchView()
                .path("option_selection")
                .path("ready_count")
                .asInt(0);
    }

    static int optionPlayerCount() {
        return currentMatchView()
                .path("option_selection")
                .path("player_count")
                .asInt(0);
    }

    static String currentOptionLabel() {
        PlayerConfig config = playerConfig();
        if (config == null) {
            return "-";
        }
        return playOptionLabel(encodePlayOption(config, currentPlayMode), currentPlayMode);
    }

    static void requestOptionReady() {
        if (
                !isOptionSelectionOpen()
                        || optionReadySent
                        || main == null
                        || currentPlayMode <= 0
        ) {
            return;
        }
        PlayerConfig config = main.getPlayerConfig();
        applyFixedOptions(config, currentForcedGauge());
        currentPlayOption = encodePlayOption(config, currentPlayMode);
        optionReadySent = true;
        ObjectNode message = baseMatchMessage("option_ready");
        message.put("play_option", currentPlayOption);
        message.put("play_mode", currentPlayMode);
        message.put("ln_mode", "LN");
        send(message);
        arenaUiMessage = "OP確定済み。他の参加者を待っています";
        BMSIRArenaLog.event(
                "option_ready",
                "match_id", currentMatchId,
                "play_mode", currentPlayMode,
                "play_option", currentPlayOption
        );
        cancelOptionReadyTask();
    }

    private static synchronized void scheduleOptionReady() {
        cancelOptionReadyTask();
        if (!isOptionSelectionOpen() || optionReadySent || optionDeadlineMillis <= 0L) {
            return;
        }
        long serverNow = System.currentTimeMillis() + serverClockOffsetMillis;
        long delayMillis = Math.max(0L, optionDeadlineMillis - serverNow - 250L);
        optionReadyTask = SCHEDULER.schedule(() -> {
            if (Gdx.app != null) {
                Gdx.app.postRunnable(BMSIRArenaClient::requestOptionReady);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static synchronized void cancelOptionReadyTask() {
        ScheduledFuture<?> task = optionReadyTask;
        optionReadyTask = null;
        if (task != null) {
            task.cancel(false);
        }
    }

    private static void clearOptionSelection() {
        optionSelectionOpen = false;
        optionReadySent = false;
        optionDeadlineMillis = 0L;
        cancelOptionReadyTask();
    }

    static void receiveFillStarted(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        fillDeadlineMillis = Math.round(
                message.path("deadline").asDouble() * 1000.0
        );
        fillPlayerCount = message.path("player_count").asInt(fillPlayerCount);
        fillMaxPlayers = Math.max(
                1,
                message.path("max_players").asInt(fillMaxPlayers)
        );
        queueStatus = "reserved";
        arenaUiMessage = "追加の参加者を待っています";
    }

    static void receivePlayersUpdated(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        if (message.path("player_ids").isArray()) {
            fillPlayerCount = message.path("player_ids").size();
        }
    }

    private static void clearFillState() {
        fillDeadlineMillis = 0L;
        fillPlayerCount = 0;
        fillMaxPlayers = 8;
    }

    private static void receiveLiveView(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        receiveRules(message);
        liveView = message;
        int incomingPlayMode = message.path("play_mode").asInt(0);
        if (supportedPlayMode(incomingPlayMode)) {
            currentPlayMode = incomingPlayMode;
            lastKnownPlayMode = incomingPlayMode;
        }
        String state = message.path("state").asText();
        if ("filling".equals(state)) {
            double deadline = message.path("fill_deadline").asDouble();
            if (deadline > 0.0) {
                fillDeadlineMillis = Math.round(deadline * 1000.0);
            }
            fillPlayerCount = message.path("players").size();
            fillMaxPlayers = Math.max(
                    1,
                    message.path("max_players").asInt(fillMaxPlayers)
            );
        } else {
            clearFillState();
        }
        if ("countdown".equals(state) || "playing".equals(state)) {
            resultView = JSON.createObjectNode();
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
            long receivedMillis = System.currentTimeMillis();
            if (message.has("client_time")) {
                long sentMillis = Math.round(
                        message.path("client_time").asDouble() * 1000.0
                );
                if (sentMillis > 0L && receivedMillis >= sentMillis) {
                    serverClockOffsetMillis = clockOffsetMillis(
                            message.path("server_time").asDouble(),
                            sentMillis,
                            receivedMillis
                    );
                    return;
                }
            }
            serverClockOffsetMillis = Math.round(
                    message.path("server_time").asDouble() * 1000.0
                            - receivedMillis
            );
        }
    }

    static long clockOffsetMillis(
            double serverTimeSeconds,
            long sentMillis,
            long receivedMillis
    ) {
        long midpointMillis = sentMillis + (receivedMillis - sentMillis) / 2L;
        return Math.round(serverTimeSeconds * 1000.0) - midpointMillis;
    }

    private static void receiveChart(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText()) || main == null) {
            return;
        }
        receiveRules(message);
        JsonNode chart = message.path("chart");
        currentRandomSeed = message.path("random_seed").asLong(-1L);
        String md5 = chart.path("md5").asText();
        int expectedTotalNotes = Math.max(0, chart.path("totalnotes").asInt());
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
                boolean sameActiveChart = arenaPlayActive && md5.equalsIgnoreCase(
                        Client.state.getSelectedSongRemote().getMd5()
                );
                Client.state.getSelectedSongRemote().setMd5(md5);
                Client.state.getSelectedSongRemote().setTitle(message.path("chart").path("title").asText());
                Client.state.getSelectedSongRemote().setArtist(message.path("chart").path("artist").asText());
                Client.state.setLobbySongData(song);
                if (!sameActiveChart) {
                    arenaPlayPending = true;
                    playReadySent = false;
                    serverStartMillis = 0L;
                }
                currentPlayMode = supportedPlayMode(song.getMode()) ? song.getMode() : 0;
                if (currentPlayMode > 0) {
                    lastKnownPlayMode = currentPlayMode;
                }
                currentChartTotalNotes = expectedTotalNotes;
            }
            ObjectNode reply = baseMatchMessage("chart_check");
            reply.put("chart_hash", md5);
            reply.put("available", available);
            reply.put("totalnotes", chartCheckTotalNotes(song, expectedTotalNotes));
            if (available && supportedPlayMode(song.getMode())) {
                reply.put("play_mode", song.getMode());
            }
            send(reply);
            if (!available) {
                ImGuiNotify.warning("Arena課題譜面を所持していません: " + md5);
            }
        });
    }

    private static void prepareArenaPlay(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        if (arenaPlayActive || !arenaPlayPending || main == null) {
            return;
        }
        receiveRules(message);
        currentRandomSeed = message.path("random_seed").asLong(currentRandomSeed);
        int lockedPlayMode = message.path("play_mode").asInt(currentPlayMode);
        int lockedPlayOption = message.path("play_option").asInt(0);
        loadDeadlineMillis = Math.round(
                message.path("load_deadline").asDouble() * 1000.0
        );
        if (supportedPlayMode(lockedPlayMode)) {
            currentPlayMode = lockedPlayMode;
            lastKnownPlayMode = lockedPlayMode;
        }
        clearOptionSelection();
        resultView = JSON.createObjectNode();
        Gdx.app.postRunnable(() -> {
            if (!reserved || !arenaPlayPending || main == null) {
                return;
            }
            applyFixedOptions(
                    main.getPlayerConfig(),
                    currentForcedGauge()
            );
            applyLockedPlayOption(
                    main.getPlayerConfig(),
                    currentPlayMode,
                    lockedPlayOption
            );
            currentPlayOption = lockedPlayOption;
            arenaPlayActive = true;
            arenaPlayPending = false;
            playReadySent = false;
            serverStartMillis = 0L;
            clearNominationState();
            normalResultReady = false;
            lastLiveNanos = 0L;
            arenaUiMessage = "Arena課題譜面を読み込んでいます";
            ArenaMenu.startCurrentLobbySong();
        });
    }

    private static void receiveArenaStart(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        serverStartMillis = Math.round(
                message.path("start_at").asDouble() * 1000.0
        );
        loadDeadlineMillis = 0L;
        arenaUiMessage = "全員準備完了。まもなく開始します";
    }

    private static void applyFixedOptions(
            PlayerConfig config,
            String forcedGauge
    ) {
        BMSPlayerRule.setArenaRuleProfileOverride(currentRulesetProfile());
        if (savedOptions == null) {
            savedOptions = new OptionSnapshot(config);
        }
        if (!isAllowedArenaRandom(config.getRandom())) {
            config.setRandom(0);
        }
        if (!isAllowedArenaRandom(config.getRandom2())) {
            config.setRandom2(0);
        }
        if (config.getDoubleoption() < 0 || config.getDoubleoption() > 1) {
            config.setDoubleoption(0);
        }
        config.setLnmode(0);
        config.setMineMode(0);
        config.setScrollMode(0);
        config.setLongnoteMode(0);
        config.setSevenToNinePattern(0);
        config.setSevenToNineType(0);
        config.setExtranoteDepth(0);
        config.setBpmguide(false);
        config.setCustomJudge(false);
        int gauge = forcedGaugeOption(forcedGauge);
        if (gauge >= 0) {
            config.setGauge(gauge);
            config.setGaugeAutoShift(PlayerConfig.GAUGEAUTOSHIFT_NONE);
        }
        FreqTrainerMenu.FREQ_TRAINER_ENABLED.set(false);
        JudgeTrainer.setActive(false);
        RandomTrainer.setActive(false);
    }

    static boolean isAllowedArenaRandom(int option) {
        return option == 0
                || option == 1
                || option == 2
                || option == 3
                || option == 4
                || option == 5;
    }

    static int forcedGaugeOption(String forcedGauge) {
        return switch (forcedGauge == null ? "" : forcedGauge) {
            case "normal" -> 2;
            case "hard" -> 3;
            case "exhard" -> 4;
            case "hazard" -> 5;
            default -> -1;
        };
    }

    static int chartCheckTotalNotes(SongData song, int expectedTotalNotes) {
        if (
                song == null
                        || expectedTotalNotes <= 0
                        || song.getNotes() != expectedTotalNotes
        ) {
            return 0;
        }
        return expectedTotalNotes;
    }

    static int encodePlayOption(PlayerConfig config, int playMode) {
        int option = config.getRandom();
        for (Mode mode : Mode.values()) {
            if (mode.id == playMode && mode.player == 2) {
                return option
                        + config.getRandom2() * 10
                        + config.getDoubleoption() * 100;
            }
        }
        return option;
    }

    static void applyLockedPlayOption(
            PlayerConfig config,
            int playMode,
            int playOption
    ) {
        config.setRandom(playOption % 10);
        if (isDoublePlayMode(playMode)) {
            config.setRandom2((playOption / 10) % 10);
            config.setDoubleoption((playOption / 100) % 10);
        } else {
            config.setRandom2(0);
            config.setDoubleoption(0);
        }
    }

    static String playOptionLabel(int playOption, int playMode) {
        String first = randomOptionLabel(playOption % 10);
        if (!isDoublePlayMode(playMode)) {
            return first;
        }
        String second = randomOptionLabel((playOption / 10) % 10);
        return "1P: " + first
                + " / 2P: " + second
                + (((playOption / 100) % 10) == 1 ? " / FLIP" : "");
    }

    static String playModeLabel(int playMode) {
        // POPN_5K and POPN_9K share the legacy protocol id 9. Arena exposes
        // that id as the supported 9KEY/PMS mode.
        if (playMode == Mode.POPN_9K.id) {
            return "9KEY / PMS";
        }
        for (Mode mode : Mode.values()) {
            if (mode.id != playMode) {
                continue;
            }
            int keys = mode.key - mode.scratchKey.length;
            if (mode.player == 2) {
                return keys + "KEY / DOUBLE PLAY";
            }
            if (mode == Mode.POPN_9K || keys == 9) {
                return "9KEY / PMS";
            }
            return keys + "KEY / SINGLE PLAY";
        }
        return "";
    }

    static String playModeLayoutKey(int playMode) {
        if (playMode == Mode.POPN_9K.id) {
            return "9";
        }
        for (Mode mode : Mode.values()) {
            if (mode.id == playMode) {
                int keys = mode.key - mode.scratchKey.length;
                return Integer.toString(keys);
            }
        }
        return "unknown";
    }

    private static boolean isDoublePlayMode(int playMode) {
        for (Mode mode : Mode.values()) {
            if (mode.id == playMode) {
                return mode.player == 2;
            }
        }
        return false;
    }

    private static String randomOptionLabel(int option) {
        return switch (option) {
            case 1 -> "MIRROR";
            case 2 -> "RANDOM";
            case 3 -> "R-RANDOM";
            case 4 -> "S-RANDOM";
            case 5 -> "SPIRAL";
            default -> "NORMAL";
        };
    }

    private static boolean supportedPlayMode(int playMode) {
        for (Mode mode : Mode.values()) {
            if (mode.id == playMode) {
                return true;
            }
        }
        return false;
    }

    private static void restoreOptions() {
        if (savedOptions != null && main != null) {
            savedOptions.restore(main.getPlayerConfig());
        }
        savedOptions = null;
        BMSPlayerRule.clearArenaRuleProfileOverride();
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
        private final int gauge;
        private final int gaugeAutoShift;
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
            gauge = config.getGauge();
            gaugeAutoShift = config.getGaugeAutoShift();
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
            config.setGauge(gauge);
            config.setGaugeAutoShift(gaugeAutoShift);
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
            BMSIRArenaLog.event(
                    "socket_open",
                    "player_id", playerId,
                    "client_version", CLIENT_VERSION,
                    "body_version", Version.getVersion(),
                    "client_flavor", CLIENT_FLAVOR,
                    "ruleset_profile", BMSPlayerRule.getConfiguredRuleProfileId()
            );
            ObjectNode hello = JSON.createObjectNode();
            hello.put("type", "hello");
            hello.put("protocol", PROTOCOL_VERSION);
            hello.put("player_id", playerId);
            hello.put("passmd5", passmd5);
            hello.put("client_version", CLIENT_VERSION);
            hello.put("body_version", Version.getVersion());
            hello.put("build_hash", Version.getGitCommitHash());
            hello.put("client_flavor", CLIENT_FLAVOR);
            hello.put("ruleset_profile", BMSPlayerRule.getConfiguredRuleProfileId());
            hello.put("arena_enabled", main != null && main.getPlayerConfig().isBmsirArenaEnabled());
            hello.put("server_cpu_v1", true);
            try {
                send(JSON.writeValueAsString(hello));
                startClockSync(this);
            } catch (Exception e) {
                BMSIRArenaLog.event(
                        "hello_send_failed",
                        "error", e.getClass().getSimpleName(),
                        "message", e.getMessage()
                );
                close();
            }
        }

        @Override
        public void onMessage(String message) {
            handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            stopClockSync();
            connected = false;
            arenaUiMessage = "Arenaサーバーから切断されました。再接続します";
            BMSIRArenaLog.event(
                    "socket_close",
                    "code", code,
                    "reason", reason,
                    "remote", remote,
                    "match_id", currentMatchId,
                    "queue_status", queueStatus
            );
            if (initialized && main != null && main.getPlayerConfig().isBmsirArenaEnabled()) {
                SCHEDULER.schedule(() -> {
                    synchronized (BMSIRArenaClient.class) {
                        if (initialized && socket == this) {
                            try {
                                reconnect();
                            } catch (Exception e) {
                                logger.debug("Arena reconnect failed: {}", e.getMessage());
                                BMSIRArenaLog.event(
                                        "reconnect_failed",
                                        "error", e.getClass().getSimpleName(),
                                        "message", e.getMessage()
                                );
                            }
                        }
                    }
                }, 5, TimeUnit.SECONDS);
            }
        }

        @Override
        public void onError(Exception e) {
            connected = false;
            arenaUiMessage = "Arenaサーバーへ接続できません";
            logger.warn("BMS-IR Arena socket error: {}", e.getMessage());
            BMSIRArenaLog.event(
                    "socket_error",
                    "error", e == null ? "" : e.getClass().getSimpleName(),
                    "message", e == null ? "" : e.getMessage(),
                    "match_id", currentMatchId
            );
        }
    }

    private static synchronized void startClockSync(ArenaSocket activeSocket) {
        stopClockSync();
        clockSyncTask = SCHEDULER.scheduleAtFixedRate(() -> {
            if (socket != activeSocket || !activeSocket.isOpen()) {
                return;
            }
            ObjectNode ping = JSON.createObjectNode();
            ping.put("type", "ping");
            ping.put("client_time", System.currentTimeMillis() / 1000.0);
            send(ping);
        }, 0L, 5L, TimeUnit.SECONDS);
    }

    private static synchronized void stopClockSync() {
        ScheduledFuture<?> task = clockSyncTask;
        clockSyncTask = null;
        if (task != null) {
            task.cancel(false);
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
