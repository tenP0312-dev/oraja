package bms.player.beatoraja;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.player.beatoraja.exceptions.PlayerConfigException;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaOverlay;
import bms.player.beatoraja.arena.bmsir.BMSIRNumpadAction;
import bms.player.beatoraja.modmenu.*;
import bms.tool.mdprocessor.HttpDownloadProcessor;
import bms.tool.mdprocessor.HttpDownloadSource;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.StringBuilder;

import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.MainState.MainStateType;
import bms.player.beatoraja.audio.*;
import bms.player.beatoraja.config.KeyConfiguration;
import bms.player.beatoraja.config.SkinConfiguration;
import bms.player.beatoraja.decide.MusicDecide;
import bms.player.beatoraja.external.*;
import bms.player.beatoraja.obs.*;
import bms.player.beatoraja.input.BMSPlayerInputProcessor;
import bms.player.beatoraja.input.KeyCommand;
import bms.player.beatoraja.ir.*;
import bms.player.beatoraja.play.BMSPlayer;
import bms.player.beatoraja.play.BMSPlayerRule;
import bms.player.beatoraja.play.TargetProperty;
import bms.player.beatoraja.result.CourseResult;
import bms.player.beatoraja.result.MusicResult;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.bar.TableBar;
import bms.player.beatoraja.skin.SkinLoader;
import bms.player.beatoraja.skin.SkinObject.SkinOffset;
import bms.player.beatoraja.skin.SkinProperty;
import bms.player.beatoraja.song.*;
import bms.player.beatoraja.stream.StreamController;
import bms.tool.mdprocessor.MusicDownloadProcessor;

import static bms.player.beatoraja.modmenu.ImGuiRenderer.getShowModMenu;

/**
 * アプリケーションのルートクラス
 *
 * @author exch
 */
public class MainController {
	private static final Logger logger = LoggerFactory.getLogger(MainController.class);

	private static final String VERSION = Version.getArenaDisplayName();

	public static final boolean debug = false;
	public static final int debugTextXpos = 10;

	/**
	 * 起動時間
	 */
	private final long boottime = System.currentTimeMillis();
	private final Calendar cl = Calendar.getInstance();
	private long mouseMovedTime;

	private MusicDecide decide;
	private MusicSelector selector;
	private MusicResult result;
	private CourseResult gresult;
	private KeyConfiguration keyconfig;
	private SkinConfiguration skinconfig;

	private AudioDriver audio;

	private BMSLoudnessAnalyzer loudnessAnalyzer;

	private PlayerResource resource;

	private BitmapFont systemfont;
	private boolean imGuiInitialized;
	private boolean skinLoaderInitialized;

	private MainState current;
	
	private TimerManager timer;

	private Config config;
	private PlayerConfig player;
	private BMSPlayerMode auto;
	private boolean songUpdated;

	private SongInformationAccessor infodb;

	private IRStatus[] ir;
	private Array<IRStatus> startupIrStatuses;

	private RivalDataAccessor rivals = new RivalDataAccessor();

	private RankingDataCache ircache = new RankingDataCache();

	private SpriteBatch sprite;
	/**
	 * 1曲プレイで指定したBMSファイル
	 */
	private Path bmsfile;

	private BMSPlayerInputProcessor input;
	/**
	 * FPSを描画するかどうか
	 */
	private boolean showfps;
	/**
	 * プレイデータアクセサ
	 */
	private PlayDataAccessor playdata;

	private SystemSoundManager sound;

	private Thread screenshot;

	private MusicDownloadProcessor download;
	private HttpDownloadProcessor httpDownloadProcessor;

	private StreamController streamController;

	private ObsListener obsListener;
	private ObsWsClient obsClient;

	public static final int offsetCount = SkinProperty.OFFSET_MAX + 1;
	private final SkinOffset[] offset = new SkinOffset[offsetCount];

	protected TextureRegion black;
	protected TextureRegion white;

	private final Array<MainStateListener> stateListener = new Array<MainStateListener>();

	public ImGuiRenderer imGui;

	public List<IRSendStatus> irSendStatus = new ArrayList<IRSendStatus>();

	private final static List<Consumer<MainController>> beforeRenderTasks = new ArrayList<>();
	private final static List<Consumer<MainController>> afterRenderTasks = new ArrayList<>();
	private final static List<Consumer<MainController>> oneShotBeforeRenderTasks = new ArrayList<>();
	private final static List<Consumer<MainController>> oneShotAfterRenderTasks = new ArrayList<>();

	public MainController(Path f, Config config, PlayerConfig player, BMSPlayerMode auto, boolean songUpdated) {
		this.auto = auto;
		this.config = config;
		this.songUpdated = songUpdated;

		for(int i = 0;i < offset.length;i++) {
			offset[i] = new SkinOffset();
		}

		if(player == null) {
            try {
                player = PlayerConfig.readPlayerConfig(config.getPlayerpath(), config.getPlayername());
            } catch (PlayerConfigException e) {
                logger.error(e.getLocalizedMessage());
            }
        }
		this.player = player;
		BMSPlayerRule.setConfiguredRuleProfile(player.getBmsirRulesetProfile());

		this.bmsfile = f;
	}

	private void initializeIRConfig() {
		startupIrStatuses = new Array<>();
		for (IRConfig irconfig : player.getIrconfig()) {
			loginIr(irconfig);
		}
		finishIrInitialization();
		rivals.update(this);
	}

	private StartupTask.Result loginIr(IRConfig irconfig) {
		IRConnection connection = IRConnectionManager.getIRConnection(
				irconfig.getIrname()
		);
		if (connection == null) {
			return StartupTask.Result.skip("接続プラグインなし");
		}
		if (irconfig.getUserid() == null
				|| irconfig.getUserid().isBlank()
				|| irconfig.getPassword() == null
				|| irconfig.getPassword().isBlank()) {
			return StartupTask.Result.skip("認証情報なし");
		}

		IRResponse<IRPlayerData> response;
		try {
			response = connection.login(new IRAccount(
					irconfig.getUserid(),
					irconfig.getPassword(),
					""
			));
		} catch (IllegalArgumentException error) {
			logger.info("trying pre-0.8.5 IR login method");
			response = connection.login(
					irconfig.getUserid(),
					irconfig.getPassword()
			);
		}
		if (!response.isSucceeded()) {
			logger.warn("IRへのログイン失敗 : {}", response.getMessage());
			return StartupTask.Result.skip(response.getMessage());
		}
		startupIrStatuses.add(new IRStatus(
				irconfig,
				connection,
				response.getData()
		));
		return StartupTask.Result.ok(response.getData().name);
	}

	private StartupTask.Result finishIrInitialization() {
		ir = startupIrStatuses.toArray(IRStatus.class);
		startupIrStatuses = null;
		return StartupTask.Result.ok(ir.length + "件");
	}

	List<StartupTask> createStartupTasks() {
		List<StartupTask> tasks = new ArrayList<>();
		tasks.add(StartupTask.required("BMSデータベース・禁止譜面確認", () -> {
			SongDatabaseAccessor database = MainLoader.getScoreDatabaseAccessor();
			if (database == null) {
				throw new IllegalStateException("楽曲データベースを開けません");
			}
			for (SongData song : database.getSongDatas(SongUtils.illegalsongs)) {
				MainLoader.putIllegalSong(song.getSha256());
			}
			int illegalCount = MainLoader.getIllegalSongCount();
			if (illegalCount > 0) {
				throw new IllegalStateException(
						"禁止譜面を" + illegalCount + "件検出しました"
				);
			}
			return StartupTask.Result.ok("禁止譜面 0件");
		}));
		tasks.add(StartupTask.required("BMS保存先", () -> {
			initializeDownloadRoots();
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.optional("楽曲情報データベース", () -> {
			Class.forName("org.sqlite.JDBC");
			if (!config.isUseSongInfo()) {
				return StartupTask.Result.skip("無効");
			}
			infodb = new SongInformationAccessor(config.getSonginfopath());
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.required("プレイデータベース", () -> {
			playdata = new PlayDataAccessor(config);
			return StartupTask.Result.ok();
		}));

		startupIrStatuses = new Array<>();
		IRConfig[] irConfigs = player.getIrconfig();
		for (int index = 0; index < irConfigs.length; index++) {
			IRConfig irConfig = irConfigs[index];
			String name = irConfig.getIrname() == null
					? "IR"
					: irConfig.getIrname();
			String label = "IRログイン " + name
					+ " (" + (index + 1) + "/" + irConfigs.length + ")";
			tasks.add(StartupTask.optional(label, () -> loginIr(irConfig)));
		}
		tasks.add(StartupTask.required(
				"IRセッション",
				this::finishIrInitialization
		));
		tasks.add(StartupTask.optional("ライバル情報", () -> {
			if (ir.length == 0) {
				return StartupTask.Result.skip("IR未接続");
			}
			rivals.update(this);
			return StartupTask.Result.ok(rivals.getRivalCount() + "人");
		}));

		tasks.add(StartupTask.optional("PortAudio初期化", () -> {
			if (config.getAudioConfig().getDriver() != DriverType.PortAudio) {
				return StartupTask.Result.skip("OpenALを使用");
			}
			try {
				audio = new PortAudioDriver(config);
				return StartupTask.Result.ok();
			} catch (Throwable error) {
				config.getAudioConfig().setDriver(DriverType.OpenAL);
				return StartupTask.Result.skip("OpenALへ切替");
			}
		}));
		tasks.add(StartupTask.required("タイマー", () -> {
			timer = new TimerManager();
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.required("システムサウンド検索", () -> {
			sound = new SystemSoundManager(this);
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.optional("Discord・OBS連携", () -> {
			boolean enabled = initializeExternalListeners();
			return enabled
					? StartupTask.Result.ok()
					: StartupTask.Result.skip("無効");
		}));

		tasks.add(StartupTask.required("描画システム", () -> {
			sprite = SpriteBatchHelper.createSpriteBatch();
			SkinLoader.initPixmapResourcePool(config.getSkinPixmapGen());
			skinLoaderInitialized = true;
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.required("Modメニュー", () -> {
			try (var perf = PerformanceMetrics.get().Event("ImGui init")) {
				ImGuiRenderer.init();
			}
			imGuiInitialized = true;
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.optional("システムフォント", () -> {
			try (var perf = PerformanceMetrics.get().Event("System font load")) {
				FreeTypeFontGenerator generator = new FreeTypeFontGenerator(
						Gdx.files.internal(config.getSystemfontpath())
				);
				FreeTypeFontParameter parameter = new FreeTypeFontParameter();
				parameter.size = 24;
				systemfont = generator.generateFont(parameter);
				generator.dispose();
			}
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.required("入力デバイス", () -> {
			try (var perf = PerformanceMetrics.get().Event("Input Processor constructor")) {
				input = new BMSPlayerInputProcessor(config, player);
			}
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.required("オーディオ", () -> {
			if (config.getAudioConfig().getDriver() == DriverType.OpenAL) {
				audio = new GdxSoundDriver(config);
			}
			loudnessAnalyzer = new BMSLoudnessAnalyzer(config);
			return StartupTask.Result.ok(config.getAudioConfig().getDriver().name());
		}));
		tasks.add(StartupTask.required("プレイヤーリソース", () -> {
			resource = new PlayerResource(audio, config, player, loudnessAnalyzer);
			selector = new MusicSelector(this, songUpdated);
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.required("ローカル難易度表", () -> {
			selector.initializeLocalTables();
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.required("BMS-IR難易度表・段位", () -> {
			selector.initializeIrTables();
			return ir.length > 0
					? StartupTask.Result.ok()
					: StartupTask.Result.skip("IR未接続");
		}));
		tasks.add(StartupTask.required("コース", () -> {
			selector.initializeCourses();
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.required("お気に入り・選曲コマンド", () -> {
			selector.initializeFavoritesAndCommands();
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.required("ゲーム画面", () -> {
			initializeRemainingStates();
			initializeStateReferences(false);
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.optional("Arena接続", () -> {
			BMSIRArenaClient.initialize(this);
			return player.isBmsirArenaEnabled()
					? StartupTask.Result.ok()
					: StartupTask.Result.skip("無効");
		}));
		tasks.add(StartupTask.required("選曲スキン・初期画面", () -> {
			activateInitialState();
			return StartupTask.Result.ok();
		}));
		tasks.add(StartupTask.required("起動後サービス", () -> {
			finishStartupServices();
			return StartupTask.Result.ok();
		}));
		return tasks;
	}

	public boolean hasObsListener() {
		return obsListener != null;
	}

	public ObsListener getObsListener() {
		return obsListener;
	}

	public void saveLastRecording(String reason) {
		if (config.isUseObsWs() && obsClient != null) {
			obsClient.saveLastRecording(reason);
		}
	}

	/**
	 * Register a task that'll be executed each time before render
	 */
	public static void registerBeforeRenderTask(Consumer<MainController> task) {
		beforeRenderTasks.add(task);
	}

	/**
	 * Register a task that'll be executed each time after render
	 */
	public static void registerAfterRenderTask(Consumer<MainController> task) {
		afterRenderTasks.add(task);
	}

	/**
	 * Push a task that'll be executed exactly once after render
	 *
	 * @apiNote This function should be called inside render function, otherwise, the caller must ensure the race
	 * condition won't happen
	 */
	public static void pushOneShotBeforeRenderTask(Consumer<MainController> task) {
		oneShotBeforeRenderTasks.add(task);
	}

	/**
	 * Push a task that'll be executed exactly once after render
	 *
	 * @apiNote This function should be called inside render function, otherwise, the caller must ensure the race
	 * condition won't happen
	 */
	public static void pushOneShotAfterRenderTask(Consumer<MainController> task) {
		oneShotAfterRenderTasks.add(task);
	}

	public SkinOffset getOffset(int index) {
		return offset[index];
	}

	public SongDatabaseAccessor getSongDatabase() {
		return MainLoader.getScoreDatabaseAccessor();
	}

	public SongInformationAccessor getInfoDatabase() {
		return infodb;
	}

	public PlayDataAccessor getPlayDataAccessor() {
		return playdata;
	}
	
	public RivalDataAccessor getRivalDataAccessor() {
		return rivals;
	}
	
	public RankingDataCache getRankingDataCache() {
		return ircache;
	}

	public SpriteBatch getSpriteBatch() {
		return sprite;
	}

	public PlayerResource getPlayerResource() {
		return resource;
	}

	public Config getConfig() {
		return config;
	}

	public PlayerConfig getPlayerConfig() {
		return player;
	}

	public void changeState(MainStateType state) {
		MainState newState = null;
		switch (state) {
		case MUSICSELECT:
			if (this.bmsfile != null) {
				exit();
			} else {
				newState = selector;
			}
			break;
		case DECIDE:
			newState = config.isSkipDecideScreen() ? createBMSPlayerState() : decide;
			break;
		case PLAY:
			newState = createBMSPlayerState();
			break;
		case RESULT:
			newState = result;
			break;
		case COURSERESULT:
			newState = gresult;
			break;
		case CONFIG:
			newState = keyconfig;
			break;
		case SKINCONFIG:
			newState = skinconfig;
			break;
		}

		if (newState != null && current != newState) {
			changeState(newState);
		}
		if (current.getStage() != null) {
			Gdx.input.setInputProcessor(new InputMultiplexer(current.getStage(), input.getKeyBoardInputProcesseor()));
		} else {
			Gdx.input.setInputProcessor(input.getKeyBoardInputProcesseor());
		}
		BMSIRArenaClient.onStateChange(state);
	}

	private void changeState(MainState newState) {
		newState.create();
		if(newState.getSkin() != null) {
			newState.getSkin().prepare(newState);
		}
		if(current != null) {
			current.shutdown();
			current.setSkin(null);
		}
		current = newState;
		timer.setMainState(newState);
		current.prepare();
		updateMainStateListener(0);
	}

	public void loadNewProfile(PlayerConfig pc) {
		config.setPlayername(pc.getId());
		player = pc;
		BMSPlayerRule.setConfiguredRuleProfile(pc.getBmsirRulesetProfile());

		playdata = new PlayDataAccessor(config);

		initializeIRConfig();
		// Dispose MusicSelector to unallocate loaded skin
		selector.dispose();
		initializeStates();
		updateStateReferences();
		triggerLnWarning();
		setTargetList();

		changeState(selector);
		if (current.getStage() != null) {
			Gdx.input.setInputProcessor(new InputMultiplexer(current.getStage(), input.getKeyBoardInputProcesseor()));
		} else {
			Gdx.input.setInputProcessor(input.getKeyBoardInputProcesseor());
		}

		lastConfigSave = System.nanoTime();
	}

	private MainState createBMSPlayerState() {
		return new BMSPlayer(this, resource);
	}

	public MainState getCurrentState() {
		return current;
	}

	private void processBmsirNumpadShortcuts() {
		String[] configured = player.getBmsirNumpadActions();
		for (int number = 0; number < configured.length; number++) {
			if (input.isNumpadPressed(number)) {
				executeBmsirNumpadAction(BMSIRNumpadAction.fromId(configured[number]));
			}
		}
	}

	private void executeBmsirNumpadAction(BMSIRNumpadAction action) {
		switch (action) {
		case JUDGE_AUTO:
			if (current instanceof BMSPlayer) {
				player.setNotesDisplayTimingAutoAdjust(
						!player.isNotesDisplayTimingAutoAdjust()
				);
				ImGuiNotify.info(
						"JUDGE TIMING AUTO: "
								+ (player.isNotesDisplayTimingAutoAdjust() ? "ON" : "OFF"),
						2000
				);
			}
			break;
		case JUDGE_PLUS:
			changeJudgeTiming(player.getBmsirNumpadJudgeTimingStep());
			break;
		case JUDGE_MINUS:
			changeJudgeTiming(-player.getBmsirNumpadJudgeTimingStep());
			break;
		case KEY_CONFIG:
			if (current instanceof MusicSelector) {
				changeState(MainStateType.CONFIG);
			}
			break;
		case SKIN_CONFIG:
			if (current instanceof MusicSelector) {
				changeState(MainStateType.SKINCONFIG);
			}
			break;
		case BMS_SEARCH:
		case MODE_FILTER:
		case SORT:
		case REPLAY:
		case RIVAL:
		case SAME_FOLDER:
		case OPEN_DOCUMENT:
		case OPEN_IR:
		case FAVORITE_SONG:
		case FAVORITE_CHART:
		case UPDATE_FOLDER:
		case OPEN_FOLDER:
		case PRACTICE:
		case AUTOPLAY:
			if (current instanceof MusicSelector) {
				selector.executeNumpadAction(action);
			}
			break;
		case ARENA_OVERLAY:
			BMSIRArenaOverlay.toggleVisibility();
			break;
		case MOD_MENU:
			imGui.toggleMenu();
			break;
		case FPS:
			showfps = !showfps;
			break;
		case FULLSCREEN:
			toggleScreenMode();
			break;
		case SCREENSHOT:
			saveScreenshot();
			break;
		default:
			break;
		}
	}

	private void changeJudgeTiming(int delta) {
		if (!(current instanceof BMSPlayer)) {
			return;
		}
		player.setJudgetiming(Math.max(
				PlayerConfig.JUDGETIMING_MIN,
				Math.min(PlayerConfig.JUDGETIMING_MAX, player.getJudgetiming() + delta)
		));
		ImGuiNotify.info("JUDGE TIMING: " + player.getJudgetiming() + " ms", 2000);
	}

	private void toggleScreenMode() {
		boolean fullscreen = Gdx.graphics.isFullscreen();
		if (fullscreen) {
			Lwjgl3Graphics graphics = (Lwjgl3Graphics) Gdx.graphics;
			Gdx.graphics.setUndecorated(false);
			Gdx.graphics.setWindowedMode(config.getWindowWidth(), config.getWindowHeight());

			Graphics.DisplayMode maxResOrCurrent = Arrays.stream(Gdx.graphics.getDisplayModes())
					.max(Comparator.comparingInt((Graphics.DisplayMode mode) -> mode.width)
							.thenComparingInt(mode -> mode.height)
							.thenComparingInt(mode -> mode.refreshRate))
					.orElse(Gdx.graphics.getDisplayMode());
			int windowX = (maxResOrCurrent.width / 2) - (config.getWindowWidth() / 2);
			int windowY = (maxResOrCurrent.height / 2) - (config.getWindowHeight() / 2);
			if (windowY == 0) {
				windowY += 32;
			}
			graphics.getWindow().setPosition(windowX, windowY);
		} else {
			Graphics.DisplayMode windowResOrCurrent = Arrays.stream(Gdx.graphics.getDisplayModes())
					.filter(mode -> mode.width == config.getWindowWidth()
							&& mode.height == config.getWindowHeight())
					.max(Comparator.comparingInt(mode -> mode.refreshRate))
					.orElse(Gdx.graphics.getDisplayMode());
			Gdx.graphics.setFullscreenMode(windowResOrCurrent);
		}
		config.setDisplaymode(fullscreen
				? Config.DisplayMode.WINDOW
				: Config.DisplayMode.FULLSCREEN);
	}

	private void saveScreenshot() {
		if (screenshot != null && screenshot.isAlive()) {
			return;
		}
		final byte[] pixels = ScreenUtils.getFrameBufferPixels(
				0,
				0,
				Gdx.graphics.getBackBufferWidth(),
				Gdx.graphics.getBackBufferHeight(),
				true
		);
		screenshot = new Thread(() -> {
			for (int index = 3; index < pixels.length; index += 4) {
				pixels[index] = (byte) 0xff;
			}
			new ScreenShotFileExporter().send(current, pixels);
		});
		screenshot.start();
		saveLastRecording("ON_SCREENSHOT");
	}

	public static MainStateType getStateType(MainState state) {
		if (state instanceof KeyConfiguration) {
			return MainStateType.CONFIG;
		} else if (state instanceof BMSPlayer) {
			return MainStateType.PLAY;
		} else if (state instanceof MusicSelector) {
			return MainStateType.MUSICSELECT;
		} else if (state instanceof SkinConfiguration) {
			return MainStateType.SKINCONFIG;
		} else if (state instanceof CourseResult) {
			return MainStateType.COURSERESULT;
		} else if (state instanceof MusicDecide) {
			return MainStateType.DECIDE;
		} else if (state instanceof MusicResult) {
			return MainStateType.RESULT;
		}
		return null;
	}

	public void setPlayMode(BMSPlayerMode auto) {
		this.auto = auto;

	}

	public void create() {
		final long started = System.currentTimeMillis();
		for (StartupTask task : createStartupTasks()) {
			try {
				task.operation.run();
			} catch (Throwable error) {
				if (task.fatal) {
					throw new GdxRuntimeException(
							"Startup task failed: " + task.label,
							error
					);
				}
				logger.warn("Optional startup task failed: {}", task.label, error);
			}
		}
		logger.info("初期化時間(ms) : {}", System.currentTimeMillis() - started);
	}

	private void initializeDownloadRoots() {
		if (config.isEnableIpfs()) {
			addDownloadRoot(Paths.get("ipfs").toAbsolutePath());
		}
		if (config.isEnableHttp()) {
			addDownloadRoot(Paths.get(config.getDownloadDirectory()).toAbsolutePath());
		}
	}

	private void addDownloadRoot(Path path) {
		if (!path.toFile().exists()) {
			path.toFile().mkdirs();
		}
		List<String> roots = new ArrayList<>(Arrays.asList(config.getBmsroot()));
		if (path.toFile().exists() && !roots.contains(path.toString())) {
			roots.add(path.toString());
			config.setBmsroot(roots.toArray(String[]::new));
		}
	}

	private boolean initializeExternalListeners() {
		boolean enabled = false;
		if (config.isUseDiscordRPC()) {
			stateListener.add(new DiscordListener());
			enabled = true;
		}
		if (config.isUseObsWs()) {
			obsListener = new ObsListener(config);
			obsClient = obsListener.getObsClient();
			stateListener.add(obsListener);
			enabled = true;
		}
		return enabled;
	}

	private void activateInitialState() {
		if (bmsfile != null) {
			if (resource.setBMSFile(bmsfile, auto)) {
				changeState(MainStateType.PLAY);
			} else {
				changeState(MainStateType.CONFIG);
				exit();
			}
		} else {
			changeState(MainStateType.MUSICSELECT);
		}
	}

	private void finishStartupServices() {
		startInputPolling();
		triggerLnWarning();
		setTargetList();
		initializePlainTextures();
		Gdx.gl.glClearColor(0, 0, 0, 1);
		initializeDownloadServices();
		startIrResendProcess();
		lastConfigSave = System.nanoTime();
	}

	private void startInputPolling() {
		Thread polling = new Thread(() -> {
			long time = 0;
			for (;;) {
				final long now = System.nanoTime() / 1000000;
				if (time != now) {
					time = now;
					input.poll();
				} else {
					try {
						Thread.sleep(0, 500000);
					} catch (InterruptedException error) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		}, "BMS input polling");
		polling.start();
	}

	private void initializePlainTextures() {
		Pixmap plainPixmap = new Pixmap(2, 1, Pixmap.Format.RGBA8888);
		plainPixmap.drawPixel(0, 0, Color.toIntBits(255, 0, 0, 0));
		plainPixmap.drawPixel(1, 0, Color.toIntBits(255, 255, 255, 255));
		Texture plainTexture = new Texture(plainPixmap);
		black = new TextureRegion(plainTexture, 0, 0, 1, 1);
		white = new TextureRegion(plainTexture, 1, 0, 1, 1);
		plainPixmap.dispose();
	}

	private void initializeDownloadServices() {
		if (config.isEnableIpfs()) {
			download = new MusicDownloadProcessor(config.getIpfsUrl(), md5 -> {
				SongData[] songs = getSongDatabase().getSongDatas(md5);
				String[] result = new String[songs.length];
				for (int index = 0; index < result.length; index++) {
					result[index] = songs[index].getPath();
				}
				return result;
			});
			download.start(null);
		}
		if (config.isEnableHttp()) {
			HttpDownloadSource source = HttpDownloadProcessor.DOWNLOAD_SOURCES
					.get(config.getDownloadSource())
					.build(config);
			httpDownloadProcessor = new HttpDownloadProcessor(
					this,
					source,
					config.getDownloadDirectory()
			);
			DownloadTaskState.initialize(httpDownloadProcessor);
			DownloadTaskMenu.setProcessor(httpDownloadProcessor);
		}
	}

	private void startIrResendProcess() {
		if (ir.length == 0) {
			return;
		}
		ImGuiNotify.info(String.format("%d IR Connection Succeed", ir.length));
		Thread irResendProcess = new Thread(() -> {
			for (;;) {
				final long now = System.currentTimeMillis();
				try {
					List<IRSendStatus> completed = new ArrayList<>();
					for (IRSendStatus score : irSendStatus) {
						long retryDelay = (long) (Math.pow(4, score.retry) * 1000);
						if (score.retry != 0 && now - score.lastTry >= retryDelay) {
							score.send();
						}
						if (score.isSent) {
							completed.add(score);
						}
						if (score.retry > config.getIrSendCount()) {
							completed.add(score);
							ImGuiNotify.error(String.format(
									"Failed to send a score for %s %s",
									score.song.getTitle(),
									score.song.getSubtitle()
							));
						}
					}
					irSendStatus.removeAll(completed);
					Thread.sleep(3000);
				} catch (InterruptedException error) {
					Thread.currentThread().interrupt();
					return;
				} catch (Exception error) {
					logger.error(error.getMessage());
				}
			}
		}, "IR resend process");
		irResendProcess.start();
	}

	private void initializeStates() {
		resource = new PlayerResource(audio, config, player, loudnessAnalyzer);

		try (var perf = PerformanceMetrics.get().Event("MusicSelector constructor")) {
			selector = new MusicSelector(this, songUpdated);
		}
		selector.initializeAllBars();
		initializeRemainingStates();
	}

	private void initializeRemainingStates() {
		if(player.getRequestEnable()) {
			streamController = new StreamController(selector);
			streamController.run();
		}

		decide = new MusicDecide(this);
		result = new MusicResult(this);
		gresult = new CourseResult(this);
		keyconfig = new KeyConfiguration(this);
		skinconfig = new SkinConfiguration(this, player);
	}

	private void updateStateReferences() {
		initializeStateReferences(true);
	}

	private void initializeStateReferences(boolean initializeArena) {
		SkinMenu.init(this, player);
		SongManagerMenu.injectMusicSelector(selector);
		ArenaMenu.init(resource.getPlayerConfig().getName(), selector);
		MiscSettingMenu.setMain(this);
		if (initializeArena) {
			BMSIRArenaClient.initialize(this);
		}
	}

	private void triggerLnWarning() {
		String lnModeName = switch (player.getLnmode()) {
			case 1 -> "CN";
			case 2 -> "HCN";
			default -> "LN";
		};
		if (!lnModeName.equals("LN")) {
			// give them a really insistent warning
			String lnWarning = "Long Note mode is " + lnModeName + ".\n"
				+ "This is not recommended.\n"
				+ "Your scores may be incompatible with IR.\n"
				+ "You may change this in play options.";
			ImGuiNotify.warning(lnWarning, 8000);
		}
	}

	private void setTargetList() {
		Array<String> targetlist = new Array<String>(player.getTargetlist());
		for(int i = 0;i < rivals.getRivalCount();i++) {
			targetlist.add("RIVAL_" + (i + 1));
		}
		TargetProperty.setTargets(targetlist.toArray(String.class), this);
	}

	private long prevtime;

	private final StringBuilder message = new StringBuilder();

	public void render() {
//		input.poll();
		timer.update();

		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		current.render();
		sprite.begin();
		if (current.getSkin() != null) {
			current.getSkin().updateCustomObjects(current);
			current.getSkin().drawAllObjects(sprite, current);
		}
		sprite.end();

		final Stage stage = current.getStage();
		if (stage != null) {
			stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
			stage.draw();
		}

		// show fps
		if (showfps && systemfont != null) {
			sprite.begin();
			systemfont.setColor(Color.CYAN);
			message.setLength(0);
			systemfont.draw(sprite, message.append("FPS ").append(Gdx.graphics.getFramesPerSecond()), debugTextXpos,
					config.getResolution().height - 2);
					if(debug) {
				message.setLength(0);
				systemfont.draw(sprite, message.append("Skin Pixmap Images ").append(SkinLoader.getResource().size()), debugTextXpos,
						config.getResolution().height - 26);
				message.setLength(0);
				systemfont.draw(sprite, message.append("Total Memory Used(MB) ").append(Runtime.getRuntime().totalMemory() / (1024 * 1024)), debugTextXpos,
						config.getResolution().height - 50);
				message.setLength(0);
				systemfont.draw(sprite, message.append("Total Free Memory(MB) ").append(Runtime.getRuntime().freeMemory() / (1024 * 1024)), debugTextXpos,
						config.getResolution().height - 74);
				message.setLength(0);
				systemfont.draw(sprite, message.append("Max Sprite In Batch ").append(sprite.maxSpritesInBatch), debugTextXpos,
						config.getResolution().height - 98);
				message.setLength(0);
				systemfont.draw(sprite, message.append("Skin Pixmap Resource Size ").append(SkinLoader.getResource().size()), debugTextXpos,
						config.getResolution().height - 122);
				message.setLength(0);
				systemfont.draw(sprite, message.append("Stagefile Pixmap Resource Size ").append(selector.getStagefileResource().size()), debugTextXpos,
						config.getResolution().height - 146);
				message.setLength(0);
				systemfont.draw(sprite, message.append("Banner Pixmap Resource Size ").append(selector.getBannerResource().size()), debugTextXpos,
						config.getResolution().height - 170);
						if (current.getSkin() != null) {
					message.setLength(0);
					systemfont.draw(sprite, message.append("Skin Prepare Time ").append(current.getSkin().pcntPrepare), debugTextXpos,
							config.getResolution().height - 194);
					message.setLength(0);
					systemfont.draw(sprite, message.append("Skin Draw Time ").append(current.getSkin().pcntDraw), debugTextXpos,
							config.getResolution().height - 218);
					var i = 0;
					var l = current.getSkin().pcntmap.keySet().stream().mapToInt(c->c.getSimpleName().length()).max().orElse(1);
					var f = "%" + l + "s";
					message.setLength(0);
					message.append(String.format(f,"SkinObject")).append(" num // prepare cur/avg/max // draw cur/avg/max");
					systemfont.draw(sprite, message, debugTextXpos, config.getResolution().height - 242);
					var entrys = current.getSkin().pcntmap.entrySet().stream()
						.sorted((e1,e2) -> e1.getKey().getSimpleName().compareTo(e2.getKey().getSimpleName()))
						.toList();
					for (Map.Entry<Class, long[]> e : entrys) {
						message.setLength(0);
						message.append(String.format(f,e.getKey().getSimpleName())).append(" ")
						.append(e.getValue()[0]).append(" // ")
						.append(e.getValue()[1]/100).append(" / ")
						.append(e.getValue()[2]/100000).append(" / ")
						.append(e.getValue()[3]/100).append(" // ")
						.append(e.getValue()[4]/100).append(" / ")
						.append(e.getValue()[5]/100000).append(" / ")
						.append(e.getValue()[6]/100);
						systemfont.draw(sprite, message, debugTextXpos, config.getResolution().height - (266 + i * 24));
						i++;
					}
				}
			}

			sprite.end();
		}

        periodicConfigSave();

        if (config.isEnableHttp()) { DownloadTaskState.update(); }
        PerformanceMetrics.get().commit();

		imGui.start();
		imGui.render();
		imGui.end();

		// TODO renderループに入れるのではなく、MusicDownloadProcessorのListenerとして実装したほうがいいのでは
		if(download != null && download.isDownload()){
			downloadIpfsMessageRenderer(download.getMessage());
		}

		final long time = System.currentTimeMillis();
		if(time > prevtime) {
		    prevtime = time;
			processBmsirNumpadShortcuts();
            current.input();
            // event - move pressed
            if (input.isMousePressed()) {
                input.setMousePressed();
                current.getSkin().mousePressed(current, input.getMouseButton(), input.getMouseX(), input.getMouseY());
            }
            // event - move dragged
            if (input.isMouseDragged()) {
                input.setMouseDragged();
                current.getSkin().mouseDragged(current, input.getMouseButton(), input.getMouseX(), input.getMouseY());
            }

            // マウスカーソル表示判定
            if(input.isMouseMoved()) {
            	input.setMouseMoved(false);
            	mouseMovedTime = time;
			}
            if (
                    !getShowModMenu()
                            && current instanceof BMSPlayer
                            && (
                                    !player.isBmsirArenaEnabled()
                                            || !player.isBmsirArenaShowCursor()
                            )
            ) {
                long hideDelay = player.isBmsirArenaEnabled() ? 250L : 2000L;
                Gdx.input.setCursorCatched(time > mouseMovedTime + hideDelay);
            } else {
                Gdx.input.setCursorCatched(false);
            }
			// The configurable Arena shortcut must get first chance at function
			// keys so combinations such as Ctrl+F6 are not consumed as F6.
			if (input.isActivated(KeyCommand.TOGGLE_BMSIR_ARENA_OVERLAY)) {
				BMSIRArenaOverlay.toggleVisibility();
			}

			// FPS表示切替
            if (input.isActivated(KeyCommand.SHOW_FPS)) {
                showfps = !showfps;
            }
            // fullscreen - windowed
            if (!input.getKeyState(Input.Keys.ALT_LEFT) && !input.getKeyState(Input.Keys.ALT_RIGHT) && input.isActivated(KeyCommand.SWITCH_SCREEN_MODE)) {
				toggleScreenMode();
            }

            // if (input.getFunctionstate()[4] && input.getFunctiontime()[4] != 0) {
            // int resolution = config.getResolution();
            // resolution = (resolution + 1) % RESOLUTION.length;
            // if (config.isFullscreen()) {
            // Gdx.graphics.setWindowedMode((int) RESOLUTION[resolution].width,
            // (int) RESOLUTION[resolution].height);
            // Graphics.DisplayMode currentMode = Gdx.graphics.getDisplayMode();
            // Gdx.graphics.setFullscreenMode(currentMode);
            // }
            // else {
            // Gdx.graphics.setWindowedMode((int) RESOLUTION[resolution].width,
            // (int) RESOLUTION[resolution].height);
            // }
            // config.setResolution(resolution);
            // input.getFunctiontime()[4] = 0;
            // }

            // screen shot
            if (input.isActivated(KeyCommand.SAVE_SCREENSHOT)) {
				saveScreenshot();
            }

            if (input.isActivated(KeyCommand.POST_TWITTER)) {
                if (screenshot == null || !screenshot.isAlive()) {
            		final byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight(), false);
                    screenshot = new Thread(() -> {
                		// 全ピクセルのアルファ値を255にする(=透明色を無くす)
                		for(int i = 3;i < pixels.length;i+=4) {
                			pixels[i] = (byte) 0xff;
                		}
                    	new ScreenShotTwitterExporter(player).send(current, pixels);
                    });
                    screenshot.start();
                }
            }

			if (input.isActivated(KeyCommand.TOGGLE_MOD_MENU)) {
				imGui.toggleMenu();
			}

			if (download != null && download.getDownloadpath() != null) {
            	this.updateSong(download.getDownloadpath());
            	download.setDownloadpath(null);
            }
			if (updateSong != null && !updateSong.isAlive()) {
				selector.getBarManager().updateBar();
				updateSong = null;
			}
        }
	}

	public void dispose() {
		BMSIRArenaClient.shutdown();
		saveConfig();

		if (selector != null) {
			selector.dispose();
		}
		if (streamController != null) {
		    streamController.dispose();
        }
		if (decide != null) {
			decide.dispose();
		}
		if (result != null) {
			result.dispose();
		}
		if (gresult != null) {
			gresult.dispose();
		}
		if (keyconfig != null) {
			keyconfig.dispose();
		}
		if (skinconfig != null) {
			skinconfig.dispose();
		}
		if (imGuiInitialized) {
			ImGuiRenderer.dispose();
		}
		if (resource != null) {
			resource.dispose();
		}
//		input.dispose();
		if (skinLoaderInitialized) {
			SkinLoader.getResource().dispose();
		}
		ShaderManager.dispose();
		if (download != null) {
			download.dispose();
		}
		if (loudnessAnalyzer != null) {
			loudnessAnalyzer.shutdown();
		}

		logger.info("全リソース破棄完了");
	}

	public void pause() {
		current.pause();
	}

	public void resize(int width, int height) {
		current.resize(width, height);
	}

	public void resume() {
		current.resume();
	}

	public void beforeRender() {
		beforeRenderTasks.forEach(task -> task.accept(this));
		for (Consumer<MainController> task : oneShotBeforeRenderTasks) {
			task.accept(this);
		}
		oneShotBeforeRenderTasks.clear();
	}

	public void afterRender() {
		afterRenderTasks.forEach(task -> task.accept(this));
		for (Consumer<MainController> task : oneShotAfterRenderTasks) {
			task.accept(this);
		}
		oneShotAfterRenderTasks.clear();
	}

	public void saveConfig(){
		Config.write(config);
		PlayerConfig.write(config.getPlayerpath(), player);
		logger.info("設定情報を保存");
	}

    private long lastConfigSave = 0;
    private Thread configWrite;

    private void periodicConfigSave() {
        // let's not start anything heavy during play
        if (current instanceof BMSPlayer) { return; }

        // save once every 5 minutes
        long now = System.nanoTime();
        if ((now - lastConfigSave) < 2 * 60 * 1000000000L) { return; }

        if (configWrite != null && configWrite.isAlive()) {
            logger.error("Couldn't write config files - save process is stuck.");
            return;
        }

        lastConfigSave = now;

        // the write are quite slow but we can do them on a separate thread;
        // we still serialize the configs into json on the
        // main thread to avoid multithreading issues
        final String configJson = Config.getConfigJson(config);
        final String playerConfigJson = PlayerConfig.getConfigJson(player);
        configWrite = new Thread(() -> {
            Config.write(config, configJson);
            PlayerConfig.write(config.getPlayerpath(), player, playerConfigJson);
        });
        configWrite.start();
    }

	public void exit() {
		Gdx.app.exit();
	}

	public BMSPlayerInputProcessor getInputProcessor() {
		return input;
	}

	public AudioDriver getAudioProcessor() {
		return audio;
	}

	public IRStatus[] getIRStatus() {
		return ir;
	}

	public SystemSoundManager getSoundManager() {
		return sound;
	}

	public MusicDownloadProcessor getMusicDownloadProcessor(){
		return download;
	}

	public ImGuiRenderer getImGui() {
		return imGui;
	}

	public void setImGui(ImGuiRenderer imGui) {
		this.imGui = imGui;
	}

	public void updateMainStateListener(int status) {
		for(MainStateListener listener : stateListener) {
			listener.update(current, status);
		}
	}

	public long getPlayTime() {
		return System.currentTimeMillis() - boottime;
	}

	public Calendar getCurrnetTime() {
		cl.setTimeInMillis(System.currentTimeMillis());
		return cl;
	}

	public TimerManager getTimer() {
		return timer;
	}

	public long getStartTime() {
		return timer.getStartTime();
	}

	public long getStartMicroTime() {
		return timer.getStartMicroTime();
	}

	public long getNowTime() {
		return timer.getNowTime();
	}

	public long getNowTime(int id) {
		return timer.getNowTime(id);
	}

	public long getNowMicroTime() {
		return timer.getNowMicroTime();
	}

	public long getNowMicroTime(int id) {
		return timer.getNowMicroTime(id);
	}

	public long getTimer(int id) {
		return getMicroTimer(id) / 1000;
	}

	public long getMicroTimer(int id) {
		return timer.getMicroTimer(id);
	}

	public boolean isTimerOn(int id) {
		return getMicroTimer(id) != Long.MIN_VALUE;
	}

	public void setTimerOn(int id) {
		timer.setTimerOn(id);
	}

	public void setTimerOff(int id) {
		setMicroTimer(id, Long.MIN_VALUE);
	}

	public void setMicroTimer(int id, long microtime) {
		timer.setMicroTimer(id, microtime);
	}

	public HttpDownloadProcessor getHttpDownloadProcessor() {
		return httpDownloadProcessor;
	}

	public void setHttpDownloadProcessor(HttpDownloadProcessor httpDownloadProcessor) {
		this.httpDownloadProcessor = httpDownloadProcessor;
	}

	public void switchTimer(int id, boolean on) {
		timer.switchTimer(id, on);
	}

	private UpdateThread updateSong;

	public void updateSong(String path) {
		updateSong(path, false);
	}

	public void updateSong(String path, boolean updateParentWhenMissing) {
		if (updateSong == null || !updateSong.isAlive()) {
			updateSong = new SongUpdateThread(path, updateParentWhenMissing);
			updateSong.start();
		} else {
			logger.warn("楽曲更新中のため、更新要求は取り消されました");
		}
	}

	public void updateTable(TableBar reader) {
		if (updateSong == null || !updateSong.isAlive()) {
			updateSong = new TableUpdateThread(reader);
			updateSong.start();
		} else {
			logger.warn("楽曲更新中のため、更新要求は取り消されました");
		}
	}

	private UpdateThread downloadIpfs;

	public void downloadIpfsMessageRenderer(String message) {
		if (downloadIpfs == null || !downloadIpfs.isAlive()) {
			downloadIpfs = new DownloadMessageThread(message);
			downloadIpfs.start();
		}
	}

	public static String getVersion() {
		return VERSION;
	}

	abstract class UpdateThread extends Thread {

		protected String message;

		public UpdateThread(String message) {
			this.message = message;
		}
	}

	/**
	 * 楽曲データベース更新用スレッド
	 *
	 * @author exch
	 */
	class SongUpdateThread extends UpdateThread {

		private final String path;
		private final boolean updateParentWhenMissing;

		public SongUpdateThread(String path, boolean updateParentWhenMissing) {
			super("updating folder : " + (path == null ? "ALL" : path) + ", update parent when missing :" + (updateParentWhenMissing ? "yes" : "no"));
			this.path = path;
			this.updateParentWhenMissing = updateParentWhenMissing;
		}

		public void run() {
			ImGuiNotify.info(this.message);
			getSongDatabase().updateSongDatas(path, config.getBmsroot(), false, updateParentWhenMissing, getInfoDatabase());
		}
	}

	/**
	 * 難易度表更新用スレッド
	 *
	 * @author exch
	 */
	class TableUpdateThread extends UpdateThread {

		private final TableBar accessor;

		public TableUpdateThread(TableBar bar) {
			super("updating table : " + bar.getAccessor().name);
			accessor = bar;
		}

		public void run() {
			ImGuiNotify.info(this.message);
			TableData td = accessor.getAccessor().read();
			if (td != null) {
				accessor.getAccessor().write(td);
				accessor.setTableData(td);
			}
		}
	}

	class DownloadMessageThread extends UpdateThread {
		public DownloadMessageThread(String message) {
			super(message);
		}

		public void run() {
			while (download != null && download.isDownload() && download.getMessage() != null) {
				ImGuiNotify.info(download.getMessage());
				try {
					sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static class IRStatus {

		public final IRConfig config;
		public final IRConnection connection;
		public final IRPlayerData player;

		public IRStatus(IRConfig config, IRConnection connection, IRPlayerData player) {
			this.config = config;
			this.connection = connection;
			this.player = player;
		}
	}

	public static class IRSendStatus {
		public final IRConnection ir;
		public final SongData song;
		public final ScoreData score;
		public int retry = 0;
		public long lastTry = 0;
		public boolean isSent = false;
		public IRSendStatus(IRConnection ir, SongData song, ScoreData score) {
			this.ir = ir;
			this.song = song;
			this.score = score;
		}

		public boolean send() {
			logger.info("IRへスコア送信中 : {}", song.getTitle());
			lastTry = System.currentTimeMillis();
			IRResponse<Object> send1 = ir.sendPlayData(new IRChartData(song), new bms.player.beatoraja.ir.IRScoreData(score));
			retry++;
			if(send1.isSucceeded()) {
				logger.info("IRスコア送信完了 : {}", song.getTitle());
				isSent = true;
				return true;
			} else {
				logger.warn("IRスコア送信失敗 : {}", send1.getMessage());
				return false;
			}

		}
	}
}
