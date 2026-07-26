package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.MainState.MainStateType;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.Version;
import bms.player.beatoraja.arena.client.Client;
import bms.player.beatoraja.modmenu.ArenaMenu;
import bms.player.beatoraja.modmenu.FreqTrainerMenu;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.modmenu.JudgeTrainer;
import bms.player.beatoraja.modmenu.RandomTrainer;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final String CLIENT_VERSION = "0.1.8-dev";
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_OFFICIAL_ARENA_LEVEL = 25;
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
    private static volatile boolean forfeitRequested;
    private static volatile boolean normalResultReady = true;
    private static volatile int currentPlayerId;
    private static volatile String currentMatchId = "";
    private static volatile long serverStartMillis;
    private static volatile long serverClockOffsetMillis;
    private static volatile ObjectNode pendingFinal;
    private static volatile int currentPlayOption;
    private static volatile int currentPlayMode;
    private static volatile int currentChartTotalNotes;
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
    private static volatile JsonNode nominationView = JSON.createObjectNode();
    private static volatile boolean nominationOpen;
    private static volatile long nominationDeadlineMillis;
    private static volatile int nominationTargetBand;
    private static volatile long fillDeadlineMillis;
    private static volatile int fillPlayerCount;
    private static volatile int fillMaxPlayers = 8;

    private BMSIRArenaClient() {
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
            this.title = "★" + level + " (" + songs.length + "譜面)";
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
        currentPlayOption = 0;
        currentPlayMode = 0;
        currentChartTotalNotes = 0;
        serverClockOffsetMillis = 0L;
        arenaRating = 1000.0;
        arenaMatchesPlayed = 0;
        queueStatus = "idle";
        arenaUiMessage = "";
        rankingView = JSON.createObjectNode();
        liveView = JSON.createObjectNode();
        resultView = JSON.createObjectNode();
        clearNominationState();
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

    static boolean shouldShowOverlay() {
        return initialized
                && main != null
                && main.getPlayerConfig().isBmsirArenaEnabled();
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

    static String arenaUiMessage() {
        return arenaUiMessage;
    }

    static JsonNode rankingView() {
        return rankingView;
    }

    static JsonNode currentMatchView() {
        return resultView.isObject() && resultView.size() > 0
                ? resultView
                : liveView;
    }

    static JsonNode nominationView() {
        return nominationView;
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
                Math.min(MAX_OFFICIAL_ARENA_LEVEL, targetBand)
        );
        Map<Integer, Map<String, SongData>> candidates = new LinkedHashMap<>();
        if (tables == null) {
            return new LinkedHashMap<>();
        }
        for (TableData table : tables) {
            if (
                    table == null
                            || !isOfficialArenaTable(
                                    table.getName(),
                                    table.getUrl()
                            )
            ) {
                continue;
            }
            for (TableData.TableFolder folder : table.getFolder()) {
                int level = officialArenaLevel(folder.getName());
                if (level < 1 || level > ceiling) {
                    continue;
                }
                for (SongData song : folder.getSong()) {
                    String md5 = song == null ? "" : song.getMd5();
                    String key = md5 == null
                            ? ""
                            : md5.toLowerCase(Locale.ROOT);
                    if (!key.isBlank()) {
                        candidates.computeIfAbsent(
                                level,
                                ignored -> new LinkedHashMap<>()
                        ).putIfAbsent(key, song);
                    }
                }
            }
            break;
        }
        Map<Integer, SongData[]> result = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (int level = 1; level <= ceiling; level++) {
            Map<String, SongData> levelSongs = candidates.get(level);
            if (levelSongs == null || levelSongs.isEmpty()) {
                continue;
            }
            SongData[] uniqueSongs = levelSongs.entrySet().stream()
                    .filter(entry -> seen.add(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toArray(SongData[]::new);
            if (uniqueSongs.length > 0) {
                result.put(level, uniqueSongs);
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
                        "発狂BMS難易度表が見つかりません。現在の一覧から選曲してください";
                ImGuiNotify.warning(
                        "Arena選曲: 発狂BMS難易度表の候補を読み込めませんでした"
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
                        "Arena選曲: ★1～★"
                                + targetBand
                                + "の所持譜面がありません"
                );
                return;
            }
            ArenaNominationRootBar arenaFolder = new ArenaNominationRootBar(
                    selector,
                    ownedByLevel
            );
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
        ObjectNode message = JSON.createObjectNode();
        message.put("type", "queue_entry");
        arenaUiMessage = "エントリーを送信しています";
        send(message);
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
        message.put("play_option", currentPlayOption);
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
        message.put("state", hardFail ? "hard_fail" : "complete");
        message.put("clear_type", score.getClear());
        message.put("play_option", currentPlayOption);
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
                    arenaUiMessage = "Arenaサーバーへ接続しました";
                    updateServerClock(message);
                    ImGuiNotify.info("BMS-IR Arenaへ接続しました");
                    String activeMatchId = message.path("active_match_id").asText();
                    if (reserved && activeMatchId.isBlank()) {
                        reserved = false;
                        arenaPlayPending = false;
                        arenaPlayActive = false;
                        currentMatchId = "";
                        pendingFinal = null;
                        currentPlayOption = 0;
                        currentPlayMode = 0;
                        currentChartTotalNotes = 0;
                        forfeitRequested = false;
                        clearNominationState();
                        restoreOptionsWhenSafe();
                        ImGuiNotify.info("Arenaの試合状態を同期しました", 8000);
                    }
                    if (pendingFinal != null && !activeMatchId.isBlank()) {
                        send(pendingFinal);
                    }
                    sendState(normalizeCurrentState(), readyForArena(normalizeCurrentState()));
                }
                case "arena_status" -> receiveArenaStatus(message);
                case "pong" -> updateServerClock(message);
                case "fill_started" -> receiveFillStarted(message);
                case "players_updated" -> receivePlayersUpdated(message);
                case "match_reserved" -> {
                    String incomingMatchId = message.path("match_id").asText();
                    boolean sameMatch = reserved && currentMatchId.equals(incomingMatchId);
                    currentMatchId = incomingMatchId;
                    reserved = true;
                    queueStatus = "reserved";
                    arenaUiMessage = "マッチングしました";
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
                        forfeitRequested = false;
                        clearNominationState();
                        ImGuiNotify.info("マッチングしました！ 現在のプレイ終了後にArenaへ移動します", 8000);
                    }
                    sendState(normalizeCurrentState(), readyForArena(normalizeCurrentState()));
                }
                case "nomination_started", "nomination_status" ->
                        receiveNominationStatus(message);
                case "nomination_accepted" -> {
                    if (currentMatchId.equals(message.path("match_id").asText())) {
                        arenaUiMessage = "選曲を受け付けました";
                        ImGuiNotify.info("Arena選曲を受け付けました", 3000);
                    }
                }
                case "nominations_revealed" -> receiveNominationsRevealed(message);
                case "chart" -> receiveChart(message);
                case "start" -> scheduleArenaStart(message);
                case "live" -> receiveLiveView(message);
                case "match_resume" -> {
                    String incomingMatchId = message.path("match_id").asText();
                    currentMatchId = incomingMatchId;
                    reserved = true;
                    clearFillState();
                    if ("countdown".equals(message.path("state").asText()) && arenaPlayPending) {
                        scheduleArenaStart(message);
                    } else if (!arenaPlayActive) {
                        ImGuiNotify.warning("BMS-IR Arena: 試合へ再接続しましたが、プレイ状態を復元できません");
                    }
                }
                case "result" -> {
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
                            arenaMatchesPlayed++;
                            break;
                        }
                    }
                    queueStatus = autoReentered ? "queued" : "cancelled";
                    arenaUiMessage = autoReentered
                            ? "対戦終了。次の対戦を待機しています"
                            : "対戦終了。自動エントリーを終了しました";
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    currentPlayOption = 0;
                    currentPlayMode = 0;
                    currentChartTotalNotes = 0;
                    clearNominationState();
                    restoreOptionsWhenSafe();
                    ImGuiNotify.info(
                            autoReentered
                                    ? "Arena終了。次の対戦を待機しています"
                                    : "Arena終了。自動エントリーを終了しました",
                            8000
                    );
                    sendState("result", false);
                    requestArenaStatus();
                }
                case "forfeit_accepted" -> {
                    queueStatus = "cancelled";
                    arenaUiMessage = "対戦を棄権しました";
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    currentPlayOption = 0;
                    currentPlayMode = 0;
                    currentChartTotalNotes = 0;
                    normalResultReady = true;
                    clearNominationState();
                    restoreOptionsWhenSafe();
                    ImGuiNotify.warning("Arenaの対戦を棄権しました。自動エントリーを終了します");
                    sendState(normalizeCurrentState(), false);
                    requestArenaStatus();
                }
                case "match_cancelled" -> {
                    arenaUiMessage = "試合がキャンセルされました";
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    currentPlayOption = 0;
                    currentPlayMode = 0;
                    currentChartTotalNotes = 0;
                    clearNominationState();
                    restoreOptionsWhenSafe();
                    ImGuiNotify.warning("Arenaの試合がキャンセルされました");
                    requestArenaStatus();
                }
                case "match_released" -> {
                    boolean queueRetained = message.path("queue_retained").asBoolean();
                    queueStatus = queueRetained ? "queued" : "cancelled";
                    arenaUiMessage = queueRetained
                            ? "今回の組み合わせから外れました。待機は継続しています"
                            : "今回の組み合わせから外れました";
                    reserved = false;
                    arenaPlayPending = false;
                    arenaPlayActive = false;
                    forfeitRequested = false;
                    currentMatchId = "";
                    pendingFinal = null;
                    currentPlayOption = 0;
                    currentPlayMode = 0;
                    currentChartTotalNotes = 0;
                    clearNominationState();
                    restoreOptionsWhenSafe();
                    ImGuiNotify.warning(
                            queueRetained
                                    ? "Arenaの今回の組み合わせから外れました。待機は継続しています"
                                    : "Arenaの今回の組み合わせから外れました"
                    );
                }
                case "replaced" -> ImGuiNotify.warning("BMS-IR Arena: 同じアカウントの別本体へ接続を移しました");
                case "error" -> {
                    String code = message.path("code").asText();
                    arenaUiMessage = switch (code) {
                        case "nomination_rejected" ->
                                "選曲できません。発狂難易度表と★上限を確認してください";
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
        }
    }

    static void receiveArenaStatus(JsonNode message) {
        JsonNode player = message.path("player");
        arenaRating = player.path("rating_exact").asDouble(
                player.path("rating").asDouble(1000.0)
        );
        arenaMatchesPlayed = player.path("matches_played").asInt(0);
        queueStatus = player.path("queue").path("status").asText("idle");
        rankingView = message.path("ranking");
        arenaUiMessage = switch (queueStatus) {
            case "queued" -> "対戦相手を待っています";
            case "reserved", "matched" -> "対戦準備中です";
            case "withdraw_requested" -> "棄権処理中です";
            default -> "エントリーできます";
        };
    }

    static void receiveNominationStatus(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        clearFillState();
        int targetBand = message.path("target_band").asInt(1);
        boolean shouldOpenCandidates =
                !nominationOpen || nominationTargetBand != targetBand;
        nominationView = message;
        nominationOpen = true;
        nominationTargetBand = targetBand;
        nominationDeadlineMillis = Math.round(
                message.path("deadline").asDouble() * 1000.0
        );
        queueStatus = "matched";
        arenaUiMessage = "次のArena課題曲を選んでください";
        if (shouldOpenCandidates) {
            openNominationCandidateFolder(targetBand);
        }
    }

    static void receiveNominationsRevealed(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        clearFillState();
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
        liveView = message;
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
        JsonNode chart = message.path("chart");
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
                Client.state.getSelectedSongRemote().setMd5(md5);
                Client.state.getSelectedSongRemote().setTitle(message.path("chart").path("title").asText());
                Client.state.getSelectedSongRemote().setArtist(message.path("chart").path("artist").asText());
                Client.state.setLobbySongData(song);
                arenaPlayPending = true;
                currentPlayMode = supportedPlayMode(song.getMode()) ? song.getMode() : 0;
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

    private static void scheduleArenaStart(JsonNode message) {
        if (!currentMatchId.equals(message.path("match_id").asText())) {
            return;
        }
        serverStartMillis = Math.round(message.path("start_at").asDouble() * 1000.0);
        resultView = JSON.createObjectNode();
        long serverNowMillis = System.currentTimeMillis() + serverClockOffsetMillis;
        long delay = Math.max(0L, serverStartMillis - serverNowMillis);
        SCHEDULER.schedule(() -> Gdx.app.postRunnable(() -> {
            if (!reserved || !arenaPlayPending || main == null) {
                return;
            }
            applyFixedOptions(main.getPlayerConfig());
            currentPlayOption = encodePlayOption(
                    main.getPlayerConfig(),
                    currentPlayMode
            );
            arenaPlayActive = true;
            arenaPlayPending = false;
            clearNominationState();
            normalResultReady = false;
            lastLiveNanos = 0L;
            ArenaMenu.startCurrentLobbySong();
        }), delay, TimeUnit.MILLISECONDS);
    }

    private static void applyFixedOptions(PlayerConfig config) {
        if (savedOptions == null) {
            savedOptions = new OptionSnapshot(config);
        }
        if (config.getRandom() < 0 || config.getRandom() > 5) {
            config.setRandom(0);
        }
        if (config.getRandom2() < 0 || config.getRandom2() > 5) {
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
        FreqTrainerMenu.FREQ_TRAINER_ENABLED.set(false);
        JudgeTrainer.setActive(false);
        RandomTrainer.setActive(false);
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
            arenaUiMessage = "Arenaサーバーから切断されました。再接続します";
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
            arenaUiMessage = "Arenaサーバーへ接続できません";
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
