package bms.player.beatoraja.play;

import static bms.player.beatoraja.CourseData.CourseDataConstraint.*;
import static bms.player.beatoraja.skin.SkinProperty.*;
import static bms.player.beatoraja.SystemSoundManager.SoundType.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.player.beatoraja.arena.client.Client;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacPlayContext;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;
import bms.player.beatoraja.arena.bmsir.BMSIROrajaHelperBridge;
import bms.player.beatoraja.pattern.LaneShuffleModifier.OneBassLaneRandomShuffleModifier;
import bms.player.beatoraja.pattern.OneBassPattern;
import bms.player.beatoraja.bmsir.BMSIRLongNotePolicy;
import bms.player.beatoraja.bmsir.BMSIRTestPlayFolder;
import io.github.catizard.jlr2arenaex.enums.ClientToServer;
import io.github.catizard.jlr2arenaex.network.SelectedBMSMessage;
import bms.player.beatoraja.audio.BMSLoudnessAnalyzer;
import bms.player.beatoraja.modmenu.FreqTrainerMenu;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.modmenu.JudgeTrainer;
import bms.player.beatoraja.modmenu.RandomTrainer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;

import bms.model.*;
import bms.player.beatoraja.*;
import bms.player.beatoraja.AudioConfig.FrequencyType;
import bms.player.beatoraja.input.*;
import bms.player.beatoraja.pattern.*;
import bms.player.beatoraja.pattern.LaneShuffleModifier.*;
import bms.player.beatoraja.play.PracticeConfiguration.PracticeProperty;
import bms.player.beatoraja.play.bga.BGAProcessor;
import bms.player.beatoraja.skin.SkinType;
import bms.player.beatoraja.system.TimingDiagnostics;

/**
 * BMSプレイヤー本体
 *
 * @author exch
 */
public class BMSPlayer extends MainState {
	private static final Logger logger = LoggerFactory.getLogger(BMSPlayer.class);

	static int normalDoubleOption(int option) {
		return option == 1 ? 1 : 0;
	}

	private BMSModel model;

	private LaneRenderer lanerender;
	private LaneProperty laneProperty;
	private JudgeManager judge;

	private BGAProcessor bga;

	private GrooveGauge gauge;

	private int playtime;

	/**
	 * キー入力用スレッド
	 */
	private KeyInputProccessor keyinput;
	private ControlInputProcessor control;

	private KeySoundProcessor keysound;

	private int assist = 0;

	private ReplayData playinfo = new ReplayData();
	/**
	 * リプレイデータ
	 */
	private ReplayData replay = null;

	private FloatArray[] gaugelog;

	private int playspeed = 100;

	/**
	 * リプレイHS保存用 STATE READY時に保存
	 */
	private PlayConfig replayConfig;

	static final int TIME_MARGIN = 5000;
	private static final long LOUDNESS_ANALYSIS_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(15);

	private int state = STATE_PRELOAD;

	public static final int STATE_PRELOAD = 0;
	public static final int STATE_PRACTICE = 1;
	public static final int STATE_PRACTICE_FINISHED = 2;
	public static final int STATE_READY = 3;
	public static final int STATE_PLAY = 4;
	public static final int STATE_FAILED = 5;
	public static final int STATE_FINISHED = 6;
	public static final int STATE_ABORTED = 7;
	public static final int STATE_WAIT = 8;

	private long prevtime;

	private PracticeConfiguration practice = new PracticeConfiguration();
	private long starttimeoffset;

	private RhythmTimerProcessor rhythm;
	private long startpressedtime;
	private boolean firedWaitingReady = false;
	private boolean allReady = false;

	private float adjustedVolume = -1.f;
	private boolean analysisChecked = false;
	private Future<BMSLoudnessAnalyzer.AnalysisResult> analysisTask;
	private long analysisWaitStartedNanos;
	private long analysisDiagnosticStartedNanos;
	private boolean loudnessWaitStageReported;
	private boolean bgaPreparationStarted;
	private boolean bgaPreparationComplete;
	private boolean countdownStageReported;
	private long diagnosticPlaySessionId;

	public BMSPlayer(MainController main, PlayerResource resource) {
		super(main);
		initialize(resource);
	}

	/** Constructor for an isolated, non-playing skin-preview state. */
	protected BMSPlayer(MainController main, PlayerResource resource, boolean skinPreview) {
		super(main, resource);
		if (!skinPreview) {
			throw new IllegalArgumentException("preview constructor requires skinPreview=true");
		}
		this.model = resource.getBMSModel();
		this.gaugelog = new FloatArray[0];
	}

	private void initialize(PlayerResource resource) {
		BMSIRArenaClient.enforceArenaOptions();
		this.model = resource.getBMSModel();
		diagnosticPlaySessionId = TimingDiagnostics.playSessionStarted(
				model != null ? model.getSHA256() : null
		);
		BMSIRArenaClient.tracePlayPhase("constructor_begin", this);
		BMSPlayerMode autoplay = resource.getPlayMode();
		PlayerConfig config = resource.getPlayerConfig();
		BMSIRManiacPlayContext maniacContext = null;

		playinfo.randomoption = config.getRandom();
		playinfo.randomoption2 = config.getRandom2();
		playinfo.doubleoption = normalDoubleOption(config.getDoubleoption());

		RandomTrainer randomtrainer = new RandomTrainer();
        Optional<GhostBattlePlay.Settings> ghostBattle = GhostBattlePlay.consume();
		final ReplayData borrowedChartOption = resource.getChartOption();
		boolean borrowedRandomPlacement1P = false;
		boolean borrowedRandomPlacement2P = false;

		ReplayData HSReplay = null;

        if(ghostBattle.isPresent()) {
			playinfo.randomoption = ghostBattle.get().random().ordinal();
            if (config.getRandom() == bms.player.beatoraja.pattern.Random.MIRROR.ordinal()) {
                ImGuiNotify.info(String.format("Ghost Battle: Mirroring pattern."));
                switch (ghostBattle.get().random()) {
                case IDENTITY:
                    playinfo.randomoption = bms.player.beatoraja.pattern.Random.MIRROR.ordinal();
                    break;
                case MIRROR:
                    playinfo.randomoption = bms.player.beatoraja.pattern.Random.IDENTITY.ordinal();
                    break;
                case RANDOM:
                    StringBuilder pattern =  new StringBuilder();
                    pattern.append(ghostBattle.get().lanes());
                    Integer reversed = Integer.parseInt(pattern.reverse().toString());
                    ghostBattle = Optional.of(
                        new GhostBattlePlay.Settings(ghostBattle.get().random(), reversed));
                    break;
                }
            }
			if (playinfo.randomoption
					== bms.player.beatoraja.pattern.Random.RANDOM.ordinal()) {
				long ghostSeed = OneBassPattern.borrowedSeedForLaneOrder(
						RandomTrainer.getRandomSeedMap(),
						ghostBattle.get().lanes()
				);
				if (ghostSeed >= 0) {
					playinfo.randomoptionseed = ghostSeed;
					borrowedRandomPlacement1P = true;
				}
			}
        }
		else if(borrowedChartOption != null) {
			playinfo.randomoption = borrowedChartOption.randomoption;
			playinfo.randomoptionseed = borrowedChartOption.randomoptionseed;
			playinfo.randomoption2 = borrowedChartOption.randomoption2;
			playinfo.randomoption2seed = borrowedChartOption.randomoption2seed;
			playinfo.doubleoption = normalDoubleOption(borrowedChartOption.doubleoption);
			playinfo.rand = borrowedChartOption.rand;
			borrowedRandomPlacement1P = OneBassPattern.isStandardRandomSeed(
					playinfo.randomoptionseed
			);
			borrowedRandomPlacement2P = OneBassPattern.isStandardRandomSeed(
					playinfo.randomoption2seed
			);
		}
		if (autoplay.mode == BMSPlayerMode.Mode.REPLAY) {
			if (resource.getCourseBMSModels() != null) {
				// コースモードのリプレイ読み込み
				if (resource.getCourseReplay().length == 0) {
					// コースモード1曲目の処理
					ReplayData[] replays = main.getPlayDataAccessor().readReplayData(resource.getCourseBMSModels(),
							config.getLnmode(), autoplay.id, resource.getConstraint());
					if (replays != null) {
						for (ReplayData rd : replays) {
							resource.addCourseReplay(rd);
						}
						replay = replays[0];
					} else {
						logger.info("リプレイデータを読み込めなかったため、通常プレイモードに移行");
						autoplay = BMSPlayerMode.PLAY;
						resource.setPlayMode(autoplay);
					}
				} else {
					// 2曲目以降の処理
					for (int i = 0; i < resource.getCourseBMSModels().length; i++) {
						if (resource.getCourseBMSModels()[i].getMD5().equals(resource.getBMSModel().getMD5())) {
							replay = resource.getCourseReplay()[i];
						}
					}
				}
			} else {
				// 1曲モードのリプレイ読み込み
				replay = main.getPlayDataAccessor().readReplayData(model, config.getLnmode(), autoplay.id);
				if (replay != null) {
					boolean isReplayPatternPlay = false;
					if(main.getInputProcessor().getKeyState(1)) {
						//保存された譜面オプション/Random Seedから譜面再現
						logger.info("リプレイ再現モード : 譜面");
						playinfo.randomoption = replay.randomoption;
						playinfo.randomoptionseed = replay.randomoptionseed;
						playinfo.randomoption2 = replay.randomoption2;
						playinfo.randomoption2seed = replay.randomoption2seed;
						playinfo.doubleoption = normalDoubleOption(replay.doubleoption);
						playinfo.rand = replay.rand;
						isReplayPatternPlay = true;
					} else if(main.getInputProcessor().getKeyState(2)) {
						//保存された譜面オプションログから譜面オプション再現
						logger.info("リプレイ再現モード : オプション");
						playinfo.randomoption = replay.randomoption;
						playinfo.randomoption2 = replay.randomoption2;
						playinfo.doubleoption = normalDoubleOption(replay.doubleoption);
						isReplayPatternPlay = true;
					}
					if(main.getInputProcessor().getKeyState(4)) {
						//保存されたHSオプションログからHSオプション再現
						logger.info("リプレイ再現モード : ハイスピード");
						HSReplay = replay;
						isReplayPatternPlay = true;
					}
					if(isReplayPatternPlay) {
						replay = null;
						autoplay = BMSPlayerMode.PLAY;
						resource.setPlayMode(autoplay);
					}
				} else {
					logger.info("リプレイデータを読み込めなかったため、通常プレイモードに移行");
					autoplay = BMSPlayerMode.PLAY;
					resource.setPlayMode(autoplay);
				}
			}
		}

		boolean score = true;
		boolean forceNoIRSend = false;

		// Allow osu score submission
		if (model.isFromOSU()) {
			forceNoIRSend = false;
		}

		// RANDOM構文処理
		if (model.getRandom() != null && model.getRandom().length > 0) {
			if (autoplay.mode == BMSPlayerMode.Mode.REPLAY) {
				playinfo.rand = replay.rand;
			} else if (resource.getReplayData().randomoptionseed != -1) {
				// この処理はMusicResult、QuickRetry時にのみ通る
				playinfo.rand = resource.getReplayData().rand;
			}

			if(playinfo.rand != null && playinfo.rand.length > 0) {
				model = resource.loadBMSModel(playinfo.rand);
				// 暫定処置
				BMSModelUtils.setStartNoteTime(model, 1000);
				BMSPlayerRule.validate(model);
			}
			playinfo.rand = model.getRandom();
			logger.info("譜面分岐 : {}", Arrays.toString(playinfo.rand));
		}
		// 通常プレイの場合は最後のノーツ、オートプレイの場合はBG/BGAを含めた最後のノーツ
		playtime = (autoplay.mode == BMSPlayerMode.Mode.AUTOPLAY ? model.getLastTime() : model.getLastNoteTime()) + TIME_MARGIN;

		resource.setFreqOn(false);
		resource.setFreqString("");
		if(FreqTrainerMenu.isFreqTrainerEnabled() && autoplay.mode == BMSPlayerMode.Mode.PLAY && resource.getCourseBMSModels() == null) {
			int freq = FreqTrainerMenu.getFreq();

			playtime = (model.getLastNoteTime() + 1000) * 100 / freq + TIME_MARGIN;

			// Chart render scale, note judge is handled by create()::judge.init() later
			BMSModelUtils.changeFrequency(model, freq / 100f);

			// Audio
			if (main.getConfig().getAudioConfig().getFreqOption() == FrequencyType.FREQUENCY) {
				main.getAudioProcessor().setGlobalPitch(freq / 100f);
			}

			// Whenever using freq mode, score is forced to not send to IR service
			forceNoIRSend = true;

			// "Persist" some states in resource
			resource.setFreqOn(true);
			resource.setFreqString(FreqTrainerMenu.getFreqString());
		}
		if (autoplay.mode == BMSPlayerMode.Mode.PLAY || autoplay.mode == BMSPlayerMode.Mode.AUTOPLAY) {
			if (config.isBpmguide() && (model.getMinBPM() < model.getMaxBPM())) {
				// BPM変化がなければBPMガイドなし
				assist = Math.max(assist, 1);
				score = false;
			}

			if (config.isCustomJudge() &&
					(config.getKeyJudgeWindowRatePerfectGreat() > 100 || config.getKeyJudgeWindowRateGreat() > 100 || config.getKeyJudgeWindowRateGood() > 100
					|| config.getScratchJudgeWindowRatePerfectGreat() > 100 || config.getScratchJudgeWindowRateGreat() > 100 || config.getScratchJudgeWindowRateGood() > 100)) {
				assist = Math.max(assist, 2);
				score = false;
			}

			// Override judge rank
			if (JudgeTrainer.isActive()) {
				// This could work since beatoraja would firstly convert the judge rank that is not defined as
				// the window rate to it and directly mark the model as BMSON type (see BMSPlayerRule::validate)
				int overridingJudgeWindowRate = JudgeTrainer.getJudgeWindowRate(model.getMode());
				int originalJudgeWindowRate = model.getJudgerank();
				logger.info("Overriding original judge window from {} to {}", originalJudgeWindowRate, overridingJudgeWindowRate);
				if (originalJudgeWindowRate < overridingJudgeWindowRate) {
					// Like expand judge treatment above if the original judge window is stricter than customized one
					assist = Math.max(assist, 2);
					score = false;
				}
				model.setJudgerank(overridingJudgeWindowRate);
			}

			// Constant considered as assist in Endless Dream
			// This is a community discussion result, see https://github.com/seraxis/lr2oraja-endlessdream/issues/42
			if (config.getPlayConfig(model.getMode()).getPlayconfig().isEnableConstant()) {
				assist = Math.max(assist, 2);
			}

			Array<PatternModifier> mods = new Array<PatternModifier>();

			if(config.getScrollMode() > 0) {
				mods.add(new ScrollSpeedModifier(config.getScrollMode() - 1, config.getScrollSection(), config.getScrollRate()));
			}
			if(config.getLongnoteMode() > 0) {
				mods.add(new LongNoteModifier(config.getLongnoteMode() - 1, config.getLongnoteRate()));
			}
			if(config.getMineMode() > 0) {
				mods.add(new MineNoteModifier(config.getMineMode() - 1));
			}
            // maybe we skip all that for gbattle
            if (ghostBattle.isPresent()){
                mods = new Array<PatternModifier>();
            }

			for(PatternModifier mod : mods) {
				mod.modify(model);
				if(mod.getAssistLevel() != PatternModifier.AssistLevel.NONE) {
					assist = Math.max(assist, mod.getAssistLevel() == PatternModifier.AssistLevel.ASSIST ? 2 : 1);
					score = false;
				}
			}

			ReplayData maniacReplay = replay != null
					? replay
					: resource.getChartOption();
			BMSIRManiacSettings requestedManiac = maniacReplay != null
					&& maniacReplay.bmsirManiacSettings != null
							? maniacReplay.bmsirManiacSettings
							: config.getBmsirManiacSettings();
			boolean arenaBlocksManiac = BMSIRArenaClient.blocksLocalOneBass()
					&& !BMSIRManiacPlayContext.allowsDuringArena(
							requestedManiac,
							model.getMode()
					);
			maniacContext = BMSIRManiacPlayContext.prepare(
					requestedManiac,
					model,
					resource.getCourseBMSModels() != null
							|| arenaBlocksManiac
			);
			resource.setManiacPlayContext(maniacContext);
			if (maniacContext != null) {
				// Dedicated submission is performed separately after the result.
				// Never let a transformed play enter the ordinary chart endpoint.
				forceNoIRSend = true;
				playtime = model.getLastNoteTime() + TIME_MARGIN;
			}

		}

		logger.info("譜面オプション設定");
		if (replay != null && replay.pattern != null) {
			// リプレイ譜面再現(PatternModifyLog使用。旧verとの互換性維持用)
			if(replay.sevenToNinePattern > 0 && model.getMode() == Mode.BEAT_7K) {
				model.setMode(Mode.POPN_9K);
			}
			PatternModifier.modify(model, Arrays.asList(replay.pattern));
			logger.info("リプレイデータから譜面再現 : PatternModifyLog");
		} else if (autoplay.mode != BMSPlayerMode.Mode.PRACTICE) {

			// リプレイデータからのoption/seed再現
			ReplayData rd = null;
			if(replay != null) {
				rd = replay;
				logger.info("リプレイデータから譜面再現 : option/seed");
			} else if(resource.getReplayData().randomoptionseed != -1) {
				rd = resource.getReplayData();
				logger.info("前回プレイ時の譜面再現");
			}
			if (rd != null) {
				if(rd.sevenToNinePattern > 0 && model.getMode() == Mode.BEAT_7K) {
					model.setMode(Mode.POPN_9K);
				}
				playinfo.randomoption = rd.randomoption;
				playinfo.randomoptionseed = rd.randomoptionseed;
				playinfo.randomoption2 = rd.randomoption2;
				playinfo.randomoption2seed = rd.randomoption2seed;
				playinfo.doubleoption = normalDoubleOption(rd.doubleoption);
				playinfo.oneBassTarget = rd.oneBassTarget;
				playinfo.oneBassTarget2 = rd.oneBassTarget2;
			}
			BMSIRArenaClient.applySynchronizedRandomSeed(playinfo);
			boolean doubleBattleLinked = maniacContext != null
					&& maniacContext.isDoubleBattleApplied()
					&& !BMSIRManiacSettings.RANDOM_LINK_OFF.equals(maniacContext.randomLink());

			boolean oneBassAllowed =
					rd == null
							&& autoplay.mode == BMSPlayerMode.Mode.PLAY
							&& playinfo.doubleoption != 1
							&& config.isBmsirOneBassEnabled()
							&& !BMSIRArenaClient.blocksLocalOneBass()
							&& !Client.connected.get();
			if (rd == null && oneBassAllowed) {
				if (
						playinfo.randomoption
								== bms.player.beatoraja.pattern.Random.RANDOM.ordinal()
				) {
					playinfo.oneBassTarget = OneBassPattern.captureTarget(
							model.getMode(),
							0,
							main.getInputProcessor().startPressed(),
							main.getInputProcessor()::getKeyState
					);
				}
				if (
						model.getMode().player == 2
								&& playinfo.randomoption2
								== bms.player.beatoraja.pattern.Random.RANDOM.ordinal()
				) {
					playinfo.oneBassTarget2 = OneBassPattern.captureTarget(
							model.getMode(),
							1,
							main.getInputProcessor().startPressed(),
							main.getInputProcessor()::getKeyState
					);
				}
			} else if (rd == null) {
				playinfo.oneBassTarget = -1;
				playinfo.oneBassTarget2 = -1;
			}
			if (rd == null && playinfo.oneBassTarget >= 0) {
				long selectedSeed = OneBassPattern.selectReplayableSeed(
						model.getMode(),
						0,
						playinfo.oneBassTarget,
						playinfo.randomoptionseed,
						borrowedRandomPlacement1P
				);
				if (selectedSeed >= 0) {
					playinfo.randomoptionseed = selectedSeed;
					logger.info(
							"LR2ワンバス(1P) : Target Lane {}, Seed : {}",
							playinfo.oneBassTarget,
							playinfo.randomoptionseed
					);
				} else {
					logger.warn("LR2ワンバス(1P) : 再生可能な通常RANDOM seedを選択できないため無効化");
					playinfo.oneBassTarget = -1;
				}
			}
			if (rd == null && playinfo.oneBassTarget2 >= 0) {
				long selectedSeed = OneBassPattern.selectReplayableSeed(
						model.getMode(),
						1,
						playinfo.oneBassTarget2,
						playinfo.randomoption2seed,
						borrowedRandomPlacement2P
				);
				if (selectedSeed >= 0) {
					playinfo.randomoption2seed = selectedSeed;
					logger.info(
							"LR2ワンバス(2P) : Target Lane {}, Seed : {}",
							playinfo.oneBassTarget2,
							playinfo.randomoption2seed
					);
				} else {
					logger.warn("LR2ワンバス(2P) : 再生可能な通常RANDOM seedを選択できないため無効化");
					playinfo.oneBassTarget2 = -1;
				}
			}

			Array<PatternModifier> mods = new Array<PatternModifier>();
			// DP譜面オプション
			if(model.getMode().player == 2 && !doubleBattleLinked) {
				if (playinfo.doubleoption == 1) {
					mods.add(new PlayerFlipModifier());
				}
				logger.info("譜面オプション(DP) :  {}", playinfo.doubleoption);

				PatternModifier pm = playinfo.oneBassTarget2 >= 0
						? new OneBassLaneRandomShuffleModifier(
								1,
								playinfo.oneBassTarget2
						)
						: PatternModifier.create(
								playinfo.randomoption2,
								1,
								model.getMode(),
								config
						);
				if (playinfo.randomoption2seed == -1
						&& RandomTrainer.isActive()
						&& model.getMode() == Mode.BEAT_14K
						&& playinfo.randomoption2
						== bms.player.beatoraja.pattern.Random.RANDOM.ordinal()
						&& RandomTrainer.getRandomSeedMap() != null) {
					Long trainerSeed = RandomTrainer.getRandomSeedMap().get(
							Integer.parseInt(RandomTrainer.getLaneOrder2P())
					);
					if (trainerSeed != null) pm.setSeed(trainerSeed);
				}
				if(playinfo.randomoption2seed != -1) {
					pm.setSeed(playinfo.randomoption2seed);
				} else {
					playinfo.randomoption2seed = pm.getSeed();
				}
				mods.add(pm);
				logger.info("譜面オプション(2P) :  {}, Seed : {}", playinfo.randomoption2, playinfo.randomoption2seed);
			}

			// SP譜面オプション
			PatternModifier pm = playinfo.oneBassTarget >= 0
					? new OneBassLaneRandomShuffleModifier(
							0,
							playinfo.oneBassTarget
					)
					: PatternModifier.create(
							playinfo.randomoption,
							0,
							model.getMode(),
							config
					);
			if(playinfo.randomoptionseed != -1) {
				pm.setSeed(playinfo.randomoptionseed);
			} else {
				if (Client.connected.get() && !Client.state.getHost().equals(Client.state.getRemoteId())) {
					if (RandomTrainer.isActive()) {
						logger.info("RandomTrainer: Disabled during arena session");
					}
                    int lr2Seed = Client.state.getRandomSeed();
                    long rajaSeed = 0;
                    if (Client.state.getRandomFlip()) {
                        HashMap<Integer, Long> seedmap = RandomTrainer.getRandomSeedMap();
                        String lanePattern = new StringBuilder().append(
                            LR2RandomPattern.getLR2LaneOrder(lr2Seed, false)
                        ).reverse().toString();
                        rajaSeed = seedmap.get(Integer.parseInt(lanePattern));
                        logger.info("Arena: Applying flipped random seed from host, converting from flipped pattern {} to {}", lanePattern, rajaSeed);
                    } else {
                        rajaSeed = LR2RandomPattern.fromLR2SeedToRaja(lr2Seed);
                        logger.info("Arena: Applying random seed from host, converting from {} to {}", lr2Seed, rajaSeed);
                    }
					pm.setSeed(rajaSeed);
				} else if (ghostBattle.isPresent()) {
					Integer pattern = ghostBattle.get().lanes();
					logger.info("Ghost battle - fixing lane pattern to {}", pattern);
					long ghostSeed = OneBassPattern.borrowedSeedForLaneOrder(
							RandomTrainer.getRandomSeedMap(),
							pattern
					);
					if (ghostSeed >= 0) {
						pm.setSeed(ghostSeed);
					} else {
						logger.warn("Ghost battle - replayable lane seed is unavailable for {}", pattern);
					}
				} else {
					if (RandomTrainer.isActive()
							&& (model.getMode() == Mode.BEAT_7K
							|| maniacContext != null && maniacContext.isDoubleBattleApplied()
							&& model.getMode() == Mode.BEAT_14K)
							&& RandomTrainer.getRandomSeedMap() != null) {
						HashMap<Integer, Long> seedmap = RandomTrainer.getRandomSeedMap();
						logger.info("RandomTrainer: Enabled, modifying random seed");
						pm.setSeed(seedmap.get(Integer.parseInt(RandomTrainer.getLaneOrder())));
					}
				}
				playinfo.randomoptionseed = pm.getSeed();
			}
			mods.add(pm);
			logger.info("譜面オプション(1P) :  {}, Seed : {}", playinfo.randomoption, playinfo.randomoptionseed);
			if (doubleBattleLinked) {
				boolean symmetry = BMSIRManiacSettings.RANDOM_LINK_SYMMETRY.equals(
						maniacContext.randomLink()
				);
				mods.add(new DoubleBattleLinkModifier(symmetry));
				playinfo.randomoption2 = playinfo.randomoption;
				playinfo.randomoption2seed = playinfo.randomoptionseed;
				logger.info("DOUBLE BATTLE RANDOM LINK : {}", maniacContext.randomLink());
			}

			if (config.getSevenToNinePattern() >= 1 && model.getMode() == Mode.BEAT_7K) {
				//7to9
				ModeModifier mod = new ModeModifier(Mode.BEAT_7K, Mode.POPN_9K, config);
				mods.add(mod);
			}

			int[][] patternArray = new int[model.getMode().player][];

			for(PatternModifier mod : mods) {
				mod.modify(model);
				if(mod.getAssistLevel() != PatternModifier.AssistLevel.NONE) {
					logger.info("アシスト譜面オプションが選択されました");
					assist = Math.max(assist, mod.getAssistLevel() == PatternModifier.AssistLevel.ASSIST ? 2 : 1);
					score = false;
				}

				if (mod instanceof LaneShuffleModifier lmod){
					if(lmod.isToDisplay()){
						patternArray[lmod.player] = lmod.getRandomPattern(model.getMode());
					}
				}
			}
			if (doubleBattleLinked && patternArray.length == 2 && patternArray[0] != null) {
				int sideWidth = model.getMode().key / 2;
				int[] linkedPattern = new int[patternArray[0].length];
				boolean symmetry = BMSIRManiacSettings.RANDOM_LINK_SYMMETRY.equals(
						maniacContext.randomLink()
				);
				int[] playableLanes = IntStream.range(0, sideWidth)
						.filter(lane -> !model.getMode().isScratchKey(lane))
						.toArray();
				for (int local = 0; local < sideWidth; local++) {
					int rightLane = sideWidth + local;
					if (model.getMode().isScratchKey(rightLane)) {
						linkedPattern[local] = rightLane;
						continue;
					}
					int position = 0;
					while (position < playableLanes.length && playableLanes[position] != local) {
						position++;
					}
					int sourceLocal = playableLanes[
							symmetry ? playableLanes.length - 1 - position : position
					];
					linkedPattern[local] = sideWidth + patternArray[0][sourceLocal];
				}
				patternArray[1] = linkedPattern;
			}
//			playinfo.pattern = pattern.toArray(new PatternModifyLog[pattern.size()]);
			playinfo.laneShufflePattern = patternArray;
				BMSIROrajaHelperBridge.publishPlacement(model, playinfo);
				if (resource.getManiacPlayContext() != null) {
					resource.getManiacPlayContext().updatePlacement(model);
				}
				if (resource.getChartOption() != null
						&& resource.getChartOption().bmsirManiacPlacementHash != null
						&& !resource.getChartOption().bmsirManiacPlacementHash.isBlank()
						&& resource.getManiacPlayContext() != null
						&& !resource.getChartOption().bmsirManiacPlacementHash.equals(
						resource.getManiacPlayContext().placementHash())) {
					resource.setRivalScoreData(null);
					ImGuiNotify.error("MANIAC ghost placement did not match this client build.");
				}

		}

		// Pattern/replay modifiers can create CN/HCN after the initial decode.
		// Normalize once more immediately before gameplay is initialized.
		BMSIRLongNotePolicy.normalizeModel(model);
		BMSIRArenaClient.tracePlayPhase("pattern_ready", this);

		if(HSReplay != null && HSReplay.config != null) {
			//保存されたHSオプションログからHSオプション再現
			config.getPlayConfig(model.getMode()).setPlayconfig(HSReplay.config);
		}

		logger.info("ゲージ設定");
		if(replay != null) {
			for(int count = (main.getInputProcessor().getKeyState(5) ? 1 : 0) + (main.getInputProcessor().getKeyState(3) ? 2 : 0);count > 0; count--) {
				if (replay.gauge != GrooveGauge.HAZARD || replay.gauge != GrooveGauge.EXHARDCLASS) {
					replay.gauge++;
				}
			}
		}
		if(replay != null && main.getInputProcessor().getKeyState(5)) {
		}
		// プレイゲージ、初期値設定
		gauge = GrooveGauge.create(model, replay != null ? replay.gauge : config.getGauge(), resource);
		// ゲージログ初期化
		gaugelog = new FloatArray[gauge.getGaugeTypeLength()];
		for(int i = 0; i < gaugelog.length; i++) {
			gaugelog[i] = new FloatArray(playtime / 500 + 2);
		}

		final boolean testPlay = autoplay.mode == BMSPlayerMode.Mode.PLAY
				&& BMSIRTestPlayFolder.contains(model, main.getConfig().getWorkDirectory());
		if (testPlay) {
			score = false;
			forceNoIRSend = true;
		}

		if (assist != 0) {
			ImGuiNotify.warning("Assist options enabled. Next play will be saved as an assist clear");
		}
		if (!score) {
			ImGuiNotify.warning(testPlay
					? bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n.text(
							"作業フォルダ: スコア保存とIR送信は無効です",
							"Work folder: score saving and IR submission are disabled")
					: "Score nullifying options enabled. Next play will not be saved");
		}
		// No on-screen notice here: forceNoIRSend already follows directly from
		// options the player themselves turned on (freq trainer, MANIAC), so
		// the outcome is not a surprise. IR submission behavior is unchanged;
		// only this popup is removed.
		logger.info("アシストレベル : {} - スコア保存 : {} - no IR submit : {}", assist, score, forceNoIRSend);

		resource.setUpdateScore(score);
		resource.setUpdateCourseScore(resource.isUpdateCourseScore() && score);
		resource.setForceNoIRSend(forceNoIRSend);
		final int difficulty = resource.getSongdata() != null ? resource.getSongdata().getDifficulty() : 0;
		resource.getSongdata().setBMSModel(model);
		resource.getSongdata().setDifficulty(difficulty);
		if (resource.getOriginalMode() != null) {
			// setBMSModel() above just copied model.getMode() into the
			// SongData, which is normally correct, but MANIAC Double
			// Battle intentionally rewrites the in-memory model's mode
			// (e.g. BEAT_7K -> BEAT_14K) for the play session only. Left
			// uncorrected, the select-screen SongData for this song keeps
			// reporting the doubled mode until it is independently
			// reloaded, which made BMSIRManiacApiClient.effectiveSettings()
			// see a native-DP mode immediately after returning from play
			// and incorrectly disable Double Battle (its mode.player == 2
			// guard), collapsing MANIAC settings to inactive and showing
			// the ordinary, non-MANIAC lamp until the song was reselected.
			resource.getSongdata().setMode(resource.getOriginalMode().id);
		}
	}

	public SkinType getSkinType() {
		for(SkinType type : SkinType.values()) {
			if(type.getMode() == model.getMode()) {
				return type;
			}
		}
		return null;
	}

	public void create() {
		BMSIRArenaClient.tracePlayPhase("create_begin", this);
		final BMSPlayerMode autoplay = resource.getPlayMode();
		laneProperty = new LaneProperty(model.getMode());
		keysound = new KeySoundProcessor(this);
		judge = new JudgeManager(this);
		control = new ControlInputProcessor(this, autoplay);
		keyinput = new KeyInputProccessor(this, laneProperty);
		PlayerConfig config = resource.getPlayerConfig();

		loadSkin(getSkinType());
		BMSIRArenaClient.tracePlayPhase("skin_loaded", this);

		final SystemSoundManager.SoundType[] guideses = {GUIDESE_PG,GUIDESE_GR,GUIDESE_GD,GUIDESE_BD,GUIDESE_PR,GUIDESE_MS};
		for(int i = 0;i < 6;i++) {
			if(config.isGuideSE()) {
				Path[] paths = main.getSoundManager().getSoundPaths(guideses[i]);
				if(paths.length > 0) {
					main.getAudioProcessor().setAdditionalKeySound(i, true, paths[0].toString());
					main.getAudioProcessor().setAdditionalKeySound(i, false, paths[0].toString());
				}
			} else {
				main.getAudioProcessor().setAdditionalKeySound(i, true, null);
				main.getAudioProcessor().setAdditionalKeySound(i, false, null);
			}
		}

		final BMSPlayerInputProcessor input = main.getInputProcessor();
		if(autoplay.mode == BMSPlayerMode.Mode.PLAY || autoplay.mode == BMSPlayerMode.Mode.PRACTICE) {
			input.setPlayConfig(config.getPlayConfig(model.getMode()));
		} else if (autoplay.mode == BMSPlayerMode.Mode.AUTOPLAY || autoplay.mode == BMSPlayerMode.Mode.REPLAY) {
			input.setEnable(false);
		}
		lanerender = new LaneRenderer(this, model);
		BMSIRArenaClient.tracePlayPhase("lane_renderer_ready", this);
		for (CourseData.CourseDataConstraint i : resource.getConstraint()) {
			if (i == NO_SPEED) {
				control.setEnableControl(false);
				break;
			}
		}

		judge.init(model, resource);
		BMSIRArenaClient.tracePlayPhase("judge_ready", this);

		rhythm = new RhythmTimerProcessor(model,
				(getSkin() instanceof PlaySkin) ? ((PlaySkin) getSkin()).getNoteExpansionRate()[0] != 100 || ((PlaySkin) getSkin()).getNoteExpansionRate()[1] != 100 : false);

		bga = resource.getBGAManager();

		ScoreData score = main.getPlayDataAccessor().readScoreData(model, config.getLnmode());
		logger.info("スコアデータベースからスコア取得");
		if (score == null) {
			score = new ScoreData();
		}

		if (autoplay.mode == BMSPlayerMode.Mode.PRACTICE) {
			getScoreDataProperty().setTargetScore(0, null, 0, null, model.getTotalNotes());
			practice.create(model, main.getConfig());
			state = STATE_PRACTICE;
		} else {
			
			if(resource.getRivalScoreData() == null || resource.getCourseBMSModels() != null) {
				ScoreData targetScore = TargetProperty.getTargetProperty(config.getTargetid()).getTarget(main);
				resource.setTargetScoreData(targetScore);
			} else {
				resource.setTargetScoreData(resource.getRivalScoreData());
			}
            ScoreData target = resource.getTargetScoreData();
            getScoreDataProperty().setTargetScore(
                score.getExscore(), score.decodeGhost(),
                target != null ? target.getExscore() : 0,
                target != null ? target.decodeGhost() : null,
                model.getTotalNotes());
            BMSIRArenaClient.applyArenaInitialTargetScore(
                    this,
                    score,
                    model.getTotalNotes()
            );
        }
		TimingDiagnostics.playStageChanged("LOADING_AUDIO");
	}

	@Override
	public void prepare() {
		TimingDiagnostics.playStageChanged(
				state == STATE_PRACTICE ? "PRACTICE_LOADING" : "LOADING_AUDIO"
		);
	}

	@Override
	public void render() {
		final PlaySkin skin = (PlaySkin) getSkin();
		if(skin == null) {
			main.changeState(MainStateType.MUSICSELECT);
			return;
		}
		final BMSPlayerMode autoplay = resource.getPlayMode();
		final BMSPlayerInputProcessor input = main.getInputProcessor();
		final PlayerConfig config = resource.getPlayerConfig();

		final long micronow = timer.getNowMicroTime();

		if(micronow > skin.getInput() * 1000){
			timer.switchTimer(TIMER_STARTINPUT, true);
		}
		if(!BMSIRArenaClient.ignoresArenaPreloadInputDelay()
				&& (input.startPressed() || input.isSelectPressed())){
			startpressedtime = micronow;
		}
		

		switch (state) {
		// 楽曲ロード
			case STATE_PRELOAD -> {
				boolean mediaReady = resource.mediaLoadFinished();
				boolean bgaReady = mediaReady && advanceBgaPreparation();
				if(config.isChartPreview()) {
					if(timer.isTimerOn(141) && micronow > startpressedtime) {
						timer.setTimerOff(141);
						lanerender.resetTimelinePosition();
					} else if(!timer.isTimerOn(141) && micronow == startpressedtime){
						timer.setMicroTimer(141, micronow - starttimeoffset * 1000);
					}
				}

				if (mediaReady && bgaReady && micronow > (skin.getLoadstart() + skin.getLoadend()) * 1000
						&& micronow - startpressedtime > 1000000) {
					if(config.isChartPreview()) {
						timer.setTimerOff(141);
						lanerender.resetTimelinePosition();
					}

					if (!pollLoudnessAnalysis()) {
						break;
					}

					if (Client.connected.get()) {
						state = STATE_WAIT;
						TimingDiagnostics.playStageChanged("READY");
					} else {
						state = STATE_READY;
						TimingDiagnostics.playStageChanged("READY");
						timer.setTimerOn(TIMER_READY);
						play(PLAY_READY);
						BMSIRArenaClient.onArenaPlayReady();
						logger.info("STATE_READYに移行");
					}
				}
				if(!timer.isTimerOn(TIMER_PM_CHARA_1P_NEUTRAL) || !timer.isTimerOn(TIMER_PM_CHARA_2P_NEUTRAL)){
					timer.setTimerOn(TIMER_PM_CHARA_1P_NEUTRAL);
					timer.setTimerOn(TIMER_PM_CHARA_2P_NEUTRAL);
				}
			}
			case STATE_WAIT -> {
				if (!firedWaitingReady) {
					firedWaitingReady = true;
					Client.send(ClientToServer.CTS_SELECTED_BMS, createSelectedBMSMessage(model, playinfo.randomoptionseed, playinfo.randomoption).pack());
					Client.send(ClientToServer.CTS_LOADING_COMPLETE, "".getBytes());
					Client.acceptNextAllReady((allReady) -> this.allReady = allReady);
				}
				if (this.allReady) {
					state = STATE_READY;
					timer.setTimerOn(TIMER_READY);
					play(PLAY_READY);
					logger.info("STATE_READYに移行");
				}
			}
			// practice mode
			case STATE_PRACTICE -> {
				boolean mediaReady = resource.mediaLoadFinished();
				boolean bgaReady = mediaReady && advanceBgaPreparation();
				if (timer.isTimerOn(TIMER_PLAY)) {
					resource.reloadBMSFile();
					resetBgaPreparation();
					mediaReady = false;
					bgaReady = false;
					model = resource.getBMSModel();
					resource.getSongdata().setBMSModel(model);
					if (resource.getOriginalMode() != null) {
						// See the comment where the play-start reattachment
						// does the same restoration: setBMSModel() above
						// would otherwise leave a MANIAC Double Battle
						// mode change (e.g. BEAT_7K -> BEAT_14K) on the
						// select-screen SongData past this practice reload.
						resource.getSongdata().setMode(resource.getOriginalMode().id);
					}
					lanerender.init(model);
					keyinput.setKeyBeamStop(false);
					timer.setTimerOff(TIMER_PLAY);
					timer.setTimerOff(TIMER_RHYTHM);
					timer.setTimerOff(TIMER_FAILED);
					timer.setTimerOff(TIMER_FADEOUT);
					timer.setTimerOff(TIMER_ENDOFNOTE_1P);

					for(int i = TIMER_PM_CHARA_1P_NEUTRAL; i <= TIMER_PM_CHARA_DANCE; i++) timer.setTimerOff(i);
				}
				if(!timer.isTimerOn(TIMER_PM_CHARA_1P_NEUTRAL) || !timer.isTimerOn(TIMER_PM_CHARA_2P_NEUTRAL)){
					timer.setTimerOn(TIMER_PM_CHARA_1P_NEUTRAL);
					timer.setTimerOn(TIMER_PM_CHARA_2P_NEUTRAL);
				}
				control.setEnableControl(false);
				control.setEnableCursor(false);
				practice.processInput(input);

				if (input.getKeyState(0) && mediaReady && bgaReady && micronow > (skin.getLoadstart() + skin.getLoadend()) * 1000
						&& micronow - startpressedtime > 1000000) {
					PracticeProperty property = practice.getPracticeProperty();
					control.setEnableControl(true);
					control.setEnableCursor(true);
					if (property.freq != 100) {
						BMSModelUtils.changeFrequency(model, property.freq / 100f);
						if (main.getConfig().getAudioConfig().getFreqOption() == FrequencyType.FREQUENCY) {
							main.getAudioProcessor().setGlobalPitch(property.freq / 100f);
						}
					}
					model.setTotal(property.total);
					PracticeModifier pm = new PracticeModifier(property.starttime * 100 / property.freq,
							property.endtime * 100 / property.freq, property.gaugetype);
					pm.modify(model);
					if (model.getMode().player == 2) {
						if (property.doubleop == 1) {
							new PlayerFlipModifier().modify(model);
						}
						PatternModifier.create(property.random2, 1, model.getMode(), config).modify(model);
					}
					PatternModifier.create(property.random, 0, model.getMode(), config).modify(model);
                    if (RandomTrainer.isActive() && model.getMode() == Mode.BEAT_7K && RandomTrainer.getRandomSeedMap() != null) {
                        HashMap<Integer, Long> seedmap = RandomTrainer.getRandomSeedMap();
                        logger.info("RandomTrainer: Enabled, modifying random seed");
                        pm.setSeed(seedmap.get(Integer.parseInt(RandomTrainer.getLaneOrder())));
                    }
                    pm.modify(model);

					gauge = practice.getGauge(model);
					model.setJudgerank(property.judgerank);
					lanerender.init(model);
					judge.init(model, resource);
					skin.pomyu.init();
					starttimeoffset = (property.starttime > 1000 ? property.starttime - 1000 : 0) * 100 / property.freq;
					playtime = (property.endtime + 1000) * 100 / property.freq + TIME_MARGIN;
					state = STATE_READY;
					TimingDiagnostics.playStageChanged("READY");
					timer.setTimerOn(TIMER_READY);
					play(PLAY_READY);
					logger.info("STATE_READYに移行");
				}
			}
			// practice終了
			case STATE_PRACTICE_FINISHED -> {
				if (timer.getNowTime(TIMER_FADEOUT) > skin.getFadeout()) {
					input.setEnable(true);
					input.setStartTime(0);
					main.changeState(MainStateType.MUSICSELECT);
				}
			}
			// GET READY
			case STATE_READY -> {
				if (!countdownStageReported) {
					countdownStageReported = true;
					TimingDiagnostics.playStageChanged("COUNTDOWN");
				}
				if (timer.getNowTime(TIMER_READY) > skin.getPlaystart()
						&& BMSIRArenaClient.isArenaStartReleased()) {
					replayConfig = lanerender.getPlayConfig().clone();
					saveConfig();
					state = STATE_PLAY;
					TimingDiagnostics.playStageChanged("ACTIVE_PLAY");
					timer.setMicroTimer(TIMER_PLAY, micronow - starttimeoffset * 1000);
					timer.setMicroTimer(TIMER_RHYTHM, micronow - starttimeoffset * 1000);

					input.setStartTime(micronow + timer.getStartMicroTime() - starttimeoffset * 1000);
					input.setKeyLogMarginTime(resource.getMarginTime());
					keyinput.startJudge(model, replay != null ? replay.keylog : null, resource.getMarginTime());
					keysound.startBGPlay(model, starttimeoffset * 1000);
					logger.info("STATE_PLAYに移行");
				}
			}
			// プレイ
			case STATE_PLAY -> {
				final long deltatime = micronow - prevtime;
				final long deltaplay = deltatime * (100 - playspeed) / 100;
				PracticeProperty property = practice.getPracticeProperty();
				timer.setMicroTimer(TIMER_PLAY, timer.getMicroTimer(TIMER_PLAY) + deltaplay);

				rhythm.update(this, deltatime, lanerender.getNowBPM(), property.freq);

				final long ptime = timer.getNowTime(TIMER_PLAY);
				float g = gauge.getValue();
				for(int i = 0; i < gaugelog.length; i++) {
					if (gaugelog[i].size <= ptime / 500) {
						gaugelog[i].add(gauge.getValue(i));
					}
				}
				timer.switchTimer(TIMER_GAUGE_MAX_1P, gauge.getGauge().isMax());

				skin.pomyu.updateTimer(this);

				// System.out.println("playing time : " + time);
				if (playtime < ptime) {
					state = STATE_FINISHED;
					timer.setTimerOn(TIMER_MUSIC_END);
					for(int i = TIMER_PM_CHARA_1P_NEUTRAL; i <= TIMER_PM_CHARA_2P_BAD; i++) {
						timer.setTimerOff(i);
					}
					timer.setTimerOff(TIMER_PM_CHARA_DANCE);

					logger.info("STATE_FINISHEDに移行");
				} else if(playtime - TIME_MARGIN < ptime) {
					timer.switchTimer(TIMER_ENDOFNOTE_1P, true);
				}
				// stage failed判定
				if (config.getGaugeAutoShift() == PlayerConfig.GAUGEAUTOSHIFT_BESTCLEAR || config.getGaugeAutoShift() == PlayerConfig.GAUGEAUTOSHIFT_SELECT_TO_UNDER) {
					final int len = config.getGaugeAutoShift() == PlayerConfig.GAUGEAUTOSHIFT_BESTCLEAR
							? (gauge.getType() >= GrooveGauge.CLASS ? GrooveGauge.EXHARDCLASS + 1 : GrooveGauge.HAZARD + 1)
							: (gauge.isCourseGauge() ? Math.min(Math.max(config.getGauge(), GrooveGauge.NORMAL) + GrooveGauge.CLASS - GrooveGauge.NORMAL, GrooveGauge.EXHARDCLASS) + 1 : config.getGauge() + 1);
					int type = gauge.isCourseGauge() ? GrooveGauge.CLASS
							: gauge.getType() < config.getBottomShiftableGauge() ? gauge.getType() : config.getBottomShiftableGauge();
					for (int i = type; i < len; i++) {
						if (gauge.getGauge(i).getValue() > 0f && gauge.getGauge(i).isQualified()) {
							type = i;
						}
					}
					gauge.setType(type);
				} else if (g == 0) {
					switch(config.getGaugeAutoShift()) {
					case PlayerConfig.GAUGEAUTOSHIFT_NONE:
						// FAILED移行
						state = STATE_FAILED;
						timer.setTimerOn(TIMER_FAILED);
						if (resource.mediaLoadFinished()) {
							main.getAudioProcessor().stop((Note) null);
						}
						play(PLAY_STOP);
						logger.info("STATE_FAILEDに移行");
						break;
					case PlayerConfig.GAUGEAUTOSHIFT_CONTINUE:
						break;
					case PlayerConfig.GAUGEAUTOSHIFT_SURVIVAL_TO_GROOVE:
						if(!gauge.isCourseGauge()) {
							// GAS処理
							gauge.setType(GrooveGauge.NORMAL);
						}
						break;
					}
				}
			}
			// 閉店処理
			case STATE_FAILED -> {
                control.setEnableControl(false);
                control.setEnableCursor(false);
				keyinput.stopJudge();
				keysound.stopBGPlay();
				if ((input.startPressed() ^ input.isSelectPressed()) && resource.getCourseBMSModels() == null
						&& autoplay.mode == BMSPlayerMode.Mode.PLAY) {
                    main.getAudioProcessor().setGlobalPitch(1f);
					if (!resource.isUpdateScore()) {
						resource.getReplayData().randomoptionseed = -1;
						logger.info("アシストモード時は同じ譜面でリプレイできません");
					} else if (input.startPressed()) {
						resource.getReplayData().randomoptionseed = -1;
						logger.info("オプションを変更せずリプレイ");
					} else {
						resource.setScoreData(createScoreData());
						logger.info("同じ譜面でリプレイ");
					}
					saveConfig();
					resource.reloadBMSFile();
					main.changeState(MainStateType.PLAY);
				} else if (timer.getNowTime(TIMER_FAILED) > skin.getClose()) {
					main.getAudioProcessor().setGlobalPitch(1f);
					if (resource.mediaLoadFinished()) {
						resource.getBGAManager().stop();
					}
					if (autoplay.mode == BMSPlayerMode.Mode.PLAY || autoplay.mode == BMSPlayerMode.Mode.REPLAY) {
						resource.setScoreData(createScoreData());
					}
					resource.setCombo(judge.getCourseCombo());
					resource.setMaxcombo(judge.getCourseMaxcombo());
					saveConfig();
					if (timer.isTimerOn(TIMER_PLAY)) {
						for (long l = timer.getTimer(TIMER_FAILED) - timer.getTimer(TIMER_PLAY); l < playtime + 500; l += 500) {
							for(int i = 0; i < gaugelog.length; i++) {
								gaugelog[i].add(0f);
							}
						}
					}
					resource.setGauge(gaugelog);
					resource.setGrooveGauge(gauge);
					resource.setAssist(assist);
					input.setEnable(true);
					input.setStartTime(0);
					if (autoplay.mode == BMSPlayerMode.Mode.PRACTICE) {
						state = STATE_PRACTICE;
					} else if (resource.getScoreData() != null) {
						main.changeState(MainStateType.RESULT);
					} else {
						main.changeState(MainStateType.MUSICSELECT);
					}
				}
			}
			// 完奏処理
			case STATE_FINISHED -> {
                control.setEnableControl(false);
                control.setEnableCursor(false);
                keyinput.stopJudge();
				keysound.stopBGPlay();
				if (timer.getNowTime(TIMER_MUSIC_END) > skin.getFinishMargin()) {
					timer.switchTimer(TIMER_FADEOUT, true);
				}
				if (timer.getNowTime(TIMER_FADEOUT) > skin.getFadeout()) {
					main.getAudioProcessor().setGlobalPitch(1f);
					resource.getBGAManager().stop();

					if (autoplay.mode == BMSPlayerMode.Mode.PLAY || autoplay.mode == BMSPlayerMode.Mode.REPLAY) {
						resource.setScoreData(createScoreData());
					}
					resource.setCombo(judge.getCourseCombo());
					resource.setMaxcombo(judge.getCourseMaxcombo());
					saveConfig();
					resource.setGauge(gaugelog);
					resource.setGrooveGauge(gauge);
					resource.setAssist(assist);
					input.setEnable(true);
					input.setStartTime(0);
					if (autoplay.mode == BMSPlayerMode.Mode.PRACTICE) {
						state = STATE_PRACTICE;
					} else if (resource.getScoreData() != null) {
						logger.info("\"score\": {}", resource.getScoreData());
						main.changeState(MainStateType.RESULT);
					} else {
						if (resource.mediaLoadFinished()) {
							main.getAudioProcessor().stop((Note) null);
						}
						if (resource.getCourseBMSModels() != null && resource.nextCourse()) {
							main.changeState(MainStateType.PLAY);
						} else if(resource.nextSong()){
							main.changeState(MainStateType.DECIDE);
						} else {
							main.changeState(MainStateType.MUSICSELECT);
						}
					}
				}
			}
			case STATE_ABORTED -> {
				if ((resource.getPlayMode().mode == BMSPlayerMode.Mode.PLAY
						&& input.startPressed() ^ input.isSelectPressed()) && resource.getCourseBMSModels() == null) {
					main.getAudioProcessor().setGlobalPitch(1f);
					if (!resource.isUpdateScore()) {
						resource.getReplayData().randomoptionseed = -1;
						logger.info("アシストモード時は同じ譜面でリプレイできません");
					} else if (input.startPressed()) {
						resource.getReplayData().randomoptionseed = -1;
						logger.info("オプションを変更せずリプレイ");
					} else {
						resource.setScoreData(createScoreData());
						logger.info("同じ譜面でリプレイ");
					}
					saveConfig();
					resource.reloadBMSFile();
					main.changeState(MainStateType.PLAY);
				}
				if (timer.getNowTime(TIMER_FADEOUT) > skin.getFadeout()) {
					input.setEnable(true);
					input.setStartTime(0);
					main.changeState(MainStateType.MUSICSELECT);
				}
			}
		}

		prevtime = micronow;
	}

	public void setPlaySpeed(int playspeed) {
		this.playspeed = playspeed;
		if (main.getConfig().getAudioConfig().getFastForward() == FrequencyType.FREQUENCY) {
			main.getAudioProcessor().setGlobalPitch(playspeed / 100f);
		}
	}

	public int getPlaySpeed() {
		return playspeed;
	}

	public void input() {
		control.input();
		keyinput.input();
	}

	public KeyInputProccessor getKeyinput() {
		return keyinput;
	}

	public int getState() {
		return state;
	}

	public float getAdjustedVolume() {
		return adjustedVolume;
	}

	private boolean pollLoudnessAnalysis() {
		if (analysisChecked) {
			return true;
		}
		if (analysisTask == null) {
			adjustedVolume = -1.f;
			analysisTask = resource.getAnalysisTask();
			if (analysisTask == null) {
				analysisChecked = true;
				return true;
			}
			analysisWaitStartedNanos = System.nanoTime();
			analysisDiagnosticStartedNanos = TimingDiagnostics.start();
		}

		if (!analysisTask.isDone()) {
			if (!loudnessWaitStageReported) {
				loudnessWaitStageReported = true;
				TimingDiagnostics.playStageChanged("WAITING_LOUDNESS");
			}
			if (System.nanoTime() - analysisWaitStartedNanos < LOUDNESS_ANALYSIS_TIMEOUT_NANOS) {
				return false;
			}
			analysisTask.cancel(true);
			ImGuiNotify.warning("Chart volume analysis timed out");
			logger.warn("Loudness analysis timed out after 15 seconds");
			finishLoudnessAnalysisWait();
			return true;
		}

		try {
			BMSLoudnessAnalyzer.AnalysisResult result = analysisTask.get();
			if (result.success) {
				float configVolume = main.getConfig().getAudioConfig().getKeyvolume();
				adjustedVolume = result.calculateAdjustedVolume(configVolume);
				logger.info("Volume set to {} ({} LUFS)", adjustedVolume, result.loudnessLUFS);
			} else {
				logger.warn("Analysis failed: {}", result.errorMessage);
				ImGuiNotify.warning("Loudness analysis failed");
			}
		} catch (CancellationException | ExecutionException e) {
			ImGuiNotify.warning("Failed to analyze chart volume");
			logger.warn("Loudness analysis error: {}", e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			ImGuiNotify.warning("Failed to analyze chart volume");
			logger.warn("Loudness analysis interrupted");
		}
		finishLoudnessAnalysisWait();
		return true;
	}

	private void finishLoudnessAnalysisWait() {
		analysisChecked = true;
		TimingDiagnostics.finish(
				TimingDiagnostics.Metric.LOUDNESS_ANALYSIS_WAIT,
				analysisDiagnosticStartedNanos
		);
		analysisDiagnosticStartedNanos = 0;
	}

	private boolean advanceBgaPreparation() {
		if (bgaPreparationComplete) {
			return true;
		}
		if (!bgaPreparationStarted) {
			bga.beginPrepare(this);
			bgaPreparationStarted = true;
			TimingDiagnostics.playStageChanged("LOADING_BGA");
		}
		bgaPreparationComplete = bga.advancePreparation();
		return bgaPreparationComplete;
	}

	private void resetBgaPreparation() {
		bgaPreparationStarted = false;
		bgaPreparationComplete = false;
		countdownStageReported = false;
	}

	public LaneRenderer getLanerender() {
		return lanerender;
	}

	public LaneProperty getLaneProperty() {
		return laneProperty;
	}

	private void saveConfig() {
		for (CourseData.CourseDataConstraint c : resource.getConstraint()) {
			if (c == NO_SPEED) {
				return;
			}
		}
		PlayConfig pc = resource.getPlayerConfig().getPlayConfig(model.getMode()).getPlayconfig();
		copyLiveLaneSettings(pc, lanerender.getPlayConfig());
	}

	static void copyLiveLaneSettings(PlayConfig target, PlayConfig live) {
		if (target.getFixhispeed() != PlayConfig.FIX_HISPEED_OFF) {
			target.setDuration(live.getDuration());
		} else {
			target.setHispeed(live.getHispeed());
		}
		target.setLanecover(live.getLanecover());
		target.setEnablelanecover(live.isEnablelanecover());
		target.setLift(live.getLift());
		target.setEnablelift(live.isEnablelift());
		target.setHidden(live.getHidden());
		target.setEnablehidden(live.isEnablehidden());
		target.setStartHerePreviewEnabled(
				live.isStartHerePreviewEnabled()
		);
		target.setStartHerePreviewMeasures(
				live.getStartHerePreviewMeasures()
		);
		target.setStartHerePreviewMaxNotes(
				live.getStartHerePreviewMaxNotes()
		);
	}

	public ScoreData createScoreData() {
		final PlayerConfig config = resource.getPlayerConfig();
		ScoreData score = judge.getScoreData();
		if (resource.getCourseBMSModels() == null
				&& state != STATE_ABORTED
				&& (score.getEpg() + score.getLpg() + score.getEgr() + score.getLgr() + score.getEgd() + score.getLgd() + score.getEbd() + score.getLbd() == 0)) {
			return null;
		}

		ClearType clear = ClearType.Failed;
		if (state != STATE_FAILED && gauge.isQualified()) {
			if (assist > 0) {
				if(resource.getCourseBMSModels() == null) clear = assist == 1 ? ClearType.LightAssistEasy : ClearType.AssistEasy;
			} else {
				if (judge.getPastNotes() == judge.getCombo()) {
					if (judge.getJudgeCount(2) == 0) {
						if (judge.getJudgeCount(1) == 0) {
							clear = ClearType.Max;
						} else {
							clear = ClearType.Perfect;
						}
					} else {
						clear = ClearType.FullCombo;
					}
				} else if (resource.getCourseBMSModels() == null) {
					clear = gauge.getClearType();
				}
			}
		}
		score.setClear(clear.id);
		score.setGauge(gauge.isTypeChanged() ? -1 : gauge.getType());
		score.setGaugelog(gaugelog);
		score.setOption(playinfo.randomoption + (model.getMode().player == 2
				? (playinfo.randomoption2 * 10 + playinfo.doubleoption * 100) : 0));
		score.setSeed((model.getMode().player == 2 ? playinfo.randomoption2seed * 65536 * 256 : 0) + playinfo.randomoptionseed);
		score.encodeGhost(judge.getGhost());
		// リプレイデータ保存。スコア保存されない場合はリプレイ保存しない
		final ReplayData replay = resource.getReplayData();
		replay.player = main.getPlayerConfig().getName();
		replay.sha256 = model.getSHA256();
		replay.mode = config.getLnmode();
		replay.date = Calendar.getInstance().getTimeInMillis() / 1000;
		replay.keylog = main.getInputProcessor().getKeyInputLog();
//		replay.pattern = playinfo.pattern;
		replay.laneShufflePattern = playinfo.laneShufflePattern;
		replay.rand = playinfo.rand;
		replay.gauge = config.getGauge();
		replay.sevenToNinePattern = config.getSevenToNinePattern();
		replay.randomoption = playinfo.randomoption;
		replay.randomoptionseed = playinfo.randomoptionseed;
		replay.randomoption2 = playinfo.randomoption2;
		replay.randomoption2seed = playinfo.randomoption2seed;
		replay.doubleoption = playinfo.doubleoption;
		replay.oneBassTarget = playinfo.oneBassTarget;
		replay.oneBassTarget2 = playinfo.oneBassTarget2;
		BMSIRManiacPlayContext maniacContext = resource.getManiacPlayContext();
		if (maniacContext != null) {
			replay.bmsirManiacSettings = maniacContext.settings();
			replay.bmsirManiacVirtualChartId = maniacContext.virtualHash();
			replay.bmsirManiacGenerationSeed = maniacContext.generationSeed();
			replay.bmsirManiacAlgorithmVersion =
					bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings.ALGORITHM_VERSION;
			replay.bmsirManiacPlacementHash = maniacContext.placementHash();
		}
		replay.config = replayConfig;

		score.setPassnotes(judge.getPastNotes());
		score.setMinbp(score.getEbd() + score.getLbd() + score.getEpr() + score.getLpr() + score.getEms() + score.getLms() + resource.getSongdata().getNotes() - judge.getPastNotes());

		long avgduration = 0;
		long average = 0;
		long stddev = 0;
		ArrayList<Long> playTimes = new ArrayList<Long>();
		final int lanes = model.getMode().key;
		for (TimeLine tl : model.getAllTimeLines()) {
			for (int i = 0; i < lanes; i++) {
				Note n = tl.getNote(i);
				if (n != null && (n instanceof NormalNote || (n instanceof LongNote ln &&
						!(((model.getLntype() == BMSModel.LNTYPE_LONGNOTE && ln.getType() == LongNote.TYPE_UNDEFINED)
								|| ln.getType() == LongNote.TYPE_LONGNOTE)
								&& ((LongNote) n).isEnd())))) {
					int state = n.getState();
					long time = n.getMicroPlayTime();
					if (state >= 1 && state <= 4) {
						playTimes.add(time);
						avgduration += Math.abs(time);
						average += time;
					}
				}
			}
		}
		score.setTotalDuration(avgduration);
		score.setTotalAvg(average);
		if (!playTimes.isEmpty()) {
			score.setAvgjudge(avgduration / playTimes.size());
			score.setAvg(average / playTimes.size());
		}

		for (long time : playTimes) {
			long meanOffset = time - score.getAvg();
			stddev += meanOffset * meanOffset;
		}
		if (!playTimes.isEmpty()) {
			stddev = (long)Math.sqrt((double)(stddev / playTimes.size()));
		}
		score.setStddev(stddev);

		score.setDeviceType(main.getInputProcessor().getDeviceType());
		score.setSkin(getSkin().header.getName());
		return score;
	}

	public void stopPlay() {
		if (main.hasObsListener()) {
			main.getObsListener().triggerPlayEnded();
		}
		if (state == STATE_PRACTICE) {
			practice.saveProperty();
			timer.setTimerOn(TIMER_FADEOUT);
			state = STATE_PRACTICE_FINISHED;
			return;
		}
		if (state == STATE_WAIT) {
			Client.send(ClientToServer.CTS_CHART_CANCELLED, "".getBytes());
			main.getAudioProcessor().setGlobalPitch(1f);
			timer.setTimerOn(TIMER_FADEOUT);
			if (resource.getPlayMode().mode == BMSPlayerMode.Mode.PLAY) {
				state = STATE_ABORTED;
			} else {
				state = STATE_PRACTICE_FINISHED;
			}
			return;
		}
		if (state == STATE_PRELOAD || state == STATE_READY) {
			main.getAudioProcessor().setGlobalPitch(1f);
			timer.setTimerOn(TIMER_FADEOUT);
			if (resource.getPlayMode().mode == BMSPlayerMode.Mode.PLAY) {
				state = STATE_ABORTED;
			} else {
				state = STATE_PRACTICE_FINISHED;
			}
			return;
		}
		if (timer.isTimerOn(TIMER_FAILED) || timer.isTimerOn(TIMER_FADEOUT)) {
			return;
		}
		if (state != STATE_FINISHED && resource.getCourseBMSModels() == null &&
				judge.getJudgeCount(0) + judge.getJudgeCount(1) + judge.getJudgeCount(2) + judge.getJudgeCount(3) == 0) {
			keyinput.stopJudge();
			keysound.stopBGPlay();
			if (resource.mediaLoadFinished()) {
				main.getAudioProcessor().stop((Note) null);
			}
			state = STATE_ABORTED;
			timer.setTimerOn(TIMER_FADEOUT);
			return;
		}
		if (state != STATE_FINISHED && 
				(judge.getPastNotes() == resource.getSongdata().getNotes()
				|| resource.getPlayMode().mode == BMSPlayerMode.Mode.AUTOPLAY)) {
			state = STATE_FINISHED;
			timer.setTimerOn(TIMER_FADEOUT);
			logger.info("STATE_FINISHEDに移行");
		} else if(state == STATE_FINISHED && !timer.isTimerOn(TIMER_FADEOUT)) {
			timer.setTimerOn(TIMER_FADEOUT);
		} else if(state != STATE_FINISHED) {
			main.getAudioProcessor().setGlobalPitch(1f);
			state = STATE_FAILED;
			timer.setTimerOn(TIMER_FAILED);
			if (resource.mediaLoadFinished()) {
				main.getAudioProcessor().stop((Note) null);
			}
			play(PLAY_STOP);
			logger.info("STATE_FAILEDに移行");
		}
	}

	@Override
	public void shutdown() {
		TimingDiagnostics.playSessionFinished(
				diagnosticPlaySessionId,
				state == STATE_FINISHED || state == STATE_FAILED ? "RESULT" : null
		);
	}

	@Override
	public void dispose() {
		super.dispose();
		lanerender.dispose();
		practice.dispose();
		logger.info("システム描画のリソース解放");
	}

	public PracticeConfiguration getPracticeConfiguration() {
		return practice;
	}

	public int getJudgeCount(int judge, boolean fast) {
		return this.judge.getJudgeCount(judge, fast);
	}

	public JudgeManager getJudgeManager() {
		return judge;
	}
	
	public ReplayData getOptionInformation() {
		return playinfo;
	}

	public void update(int judge, long time) {
		if (this.judge.getCombo() == 0) {
			bga.setMisslayerTme(time);
		}
		gauge.update(judge);
		// System.out.println("Now count : " + notes + " - " + totalnotes);

		//フルコン判定
		timer.switchTimer(TIMER_FULLCOMBO_1P, this.judge.getPastNotes() == resource.getSongdata().getNotes()
				&& this.judge.getPastNotes() == this.judge.getCombo());

		getScoreDataProperty().update(this.judge.getScoreData(), this.judge.getPastNotes());
		BMSIRArenaClient.updateArenaLiveTargetScore(
				this,
				model.getTotalNotes(),
				this.judge.getPastNotes()
		);

		timer.switchTimer(TIMER_SCORE_A, getScoreDataProperty().qualifyRank(18));
		timer.switchTimer(TIMER_SCORE_AA, getScoreDataProperty().qualifyRank(21));
		timer.switchTimer(TIMER_SCORE_AAA, getScoreDataProperty().qualifyRank(24));
		timer.switchTimer(TIMER_SCORE_BEST, this.judge.getScoreData().getExscore() >= getScoreDataProperty().getBestScore());
		timer.switchTimer(TIMER_SCORE_TARGET, this.judge.getScoreData().getExscore() >= getScoreDataProperty().getRivalScore());

		((PlaySkin)getSkin()).pomyu.PMcharaJudge = judge + 1;
	}

	public GrooveGauge getGauge() {
		return gauge;
	}

	public boolean isNoteEnd() {
		return judge.getPastNotes() == resource.getSongdata().getNotes();
	}

	public int getPastNotes() {
		return judge.getPastNotes();
	}

	public int getPlaytime() {
		return playtime;
	}

	public Mode getMode() {
		return model.getMode();
	}

	public long getNowQuarterNoteTime() {
		return rhythm != null ? rhythm.getNowQuarterNoteTime() : 0;
	}

	private SelectedBMSMessage createSelectedBMSMessage(BMSModel model, long randomSeed, int randomOption) {
		// TODO: items are not supported.
		// NOTE: We need to convert a Raja seed to LR2 seed
		// NOTE: Gauge isn't synced everytime, considering 99% raja users are using auto-shift, there's no reason
		// to sync an initial gauge value. Also LR2 has a different gauge system definition, it's tedious to handle
		// the assist clear & ex-hard etc
		return new SelectedBMSMessage(LR2RandomPattern.fromRajaToLR2Seed(randomSeed), model.getMD5(), model.getTitle(), model.getArtist(), randomOption, 0, false);
	}
}
