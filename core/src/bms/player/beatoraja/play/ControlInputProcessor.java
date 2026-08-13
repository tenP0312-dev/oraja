package bms.player.beatoraja.play;

import static bms.player.beatoraja.skin.SkinProperty.*;

import java.util.Arrays;

import bms.player.beatoraja.PlayConfig;
import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.input.BMSPlayerInputProcessor;
import bms.player.beatoraja.input.KeyBoardInputProcesseor.ControlKeys;

/**
 * BMSPlayerの制御系の入力処理を行うクラス
 * 
 * @author exch
 */
public final class ControlInputProcessor {

	private final BMSPlayer player;

	private boolean[] hschanged;
	private long startpressedtime;
	private long selectpressedtime;
	private boolean startpressed = false;
	private boolean selectpressed = false;
	private boolean startAndSelectPressed = false;
	private boolean cursorpressed;
	private long lanecovertiming;
	private long laneCoverStartTiming = Long.MIN_VALUE;
	private long exitpressedtime;

	private final long exitPressDuration;

	private boolean enableControl = true;
	private boolean enableCursor = true;

	private final BMSPlayerMode autoplay;

	private Runnable processStart;
	private Runnable processSelect;

	private boolean isChangeLift = true;

	private float coverChangeMarginLow = 0.001f;
	private float coverChangeMarginHigh = 0.01f;
	private long coverSpeedSwitchDuration = 500;

	private boolean hispeedAutoAdjust;

	public ControlInputProcessor(BMSPlayer player, BMSPlayerMode autoplay) {
		this.player = player;
		this.autoplay = autoplay;
		hschanged = new boolean[BMSPlayerInputProcessor.KEYSTATE_SIZE];
		Arrays.fill(hschanged, true);

		final PlayConfig playConfig = player.resource.getPlayerConfig().getPlayConfig(player.getMode()).getPlayconfig();
		coverChangeMarginLow = playConfig.getLanecovermarginlow();
		coverChangeMarginHigh = playConfig.getLanecovermarginhigh();
		coverSpeedSwitchDuration = playConfig.getLanecoverswitchduration();
		hispeedAutoAdjust = playConfig.isEnableHispeedAutoAdjust();

		exitPressDuration = player.main.getPlayerConfig().getExitPressDuration();

		final int[] keybinds = switch (this.player.getMode()) {
			case BEAT_5K, BEAT_10K -> new int[]{-1, 1, -1, 1, -1, 2, -2, -1, 1, -1, 1, -1, 2, -2};
			case POPN_5K, POPN_9K -> new int[]{-1, 1, -1, 1, -1, 1, -1, 2, -2};
			case BEAT_7K, BEAT_14K -> new int[]{-1, 1, -1, 1, -1, 1, -1, 2, -2, -1, 1, -1, 1, -1, 1, -1, 2, -2};
			case KEYBOARD_24K, KEYBOARD_24K_DOUBLE ->
					new int[]{-1, 1, -1, 1, -1, -1, 1, -1, 1, -1, 1, -1,
							-1, 1, -1, 1, -1, -1, 1, -1, 1, -1, 1, -1, -2, 2,
							-1, 1, -1, 1, -1, -1, 1, -1, 1, -1, 1, -1,
							-1, 1, -1, 1, -1, -1, 1, -1, 1, -1, 1, -1, -2, 2};
		};

		processStart = () -> {
			final LaneRenderer lanerender = player.getLanerender();
			final BMSPlayerInputProcessor input = player.main.getInputProcessor();
			// change hi speed by START + Keys
			for(int i = 0; i < keybinds.length; i++) {
				final boolean keystate = input.getKeyState(i);
				if (usesSixSevenCoverControl(lanerender, i)) {
					if (keystate && !hschanged[i]) {
						changeSixSevenCoverValue(isSevenKey(player.getMode(), i));
					}
					hschanged[i] = keystate;
					continue;
				}
				switch(keybinds[i]) {
					case -1 -> {
						if(keystate && !hschanged[i]) {
							lanerender.changeHispeed(false);
						}
					}
					case 1 -> {
						if(keystate && !hschanged[i]) {
							lanerender.changeHispeed(true);
						}
					}
					case 2 -> changeCoverValue(i, true);
					case -2 -> changeCoverValue(i, false);
				}
				hschanged[i] = keystate;
			}
		};
		processSelect = () -> {
			final LaneRenderer lanerender = player.getLanerender();
			final BMSPlayerInputProcessor input = player.main.getInputProcessor();
			// change duration by SELECT + Keys
			for(int i = 0; i < keybinds.length; i++) {
				final boolean keystate = input.getKeyState(i);
				switch(keybinds[i]) {
					case -1 -> {
						if(keystate && !hschanged[i]) {
							lanerender.setDuration(lanerender.getDuration() -1);
						}
					}
					case 1 -> {
						if(keystate && !hschanged[i]) {
							lanerender.setDuration(lanerender.getDuration() +1);
						}
					}
					case 2 -> changeDuration(i, true);
					case -2 -> changeDuration(i, false);
				}
				hschanged[i] = keystate;
			}
		};
	}

	private boolean usesSixSevenCoverControl(LaneRenderer laneRenderer, int key) {
		if (!isSixSevenKey(player.getMode(), key)) {
			return false;
		}
		return usesSixSevenCoverControl(
				player.resource.getPlayerConfig().getBmsirCoverControlMode(),
				laneRenderer.isEnableLanecover(),
				laneRenderer.isEnableHidden(),
				laneRenderer.isEnableLift()
		);
	}

	static boolean usesSixSevenCoverControl(
			String mode,
			boolean laneCover,
			boolean hidden,
			boolean lift
	) {
		if (PlayerConfig.BMSIR_COVER_CONTROL_LR2.equals(mode)) {
			return laneCover;
		}
		return PlayerConfig.BMSIR_COVER_CONTROL_EXTENDED.equals(mode)
				&& (laneCover || hidden || lift);
	}

	static boolean isSixSevenKey(bms.model.Mode mode, int key) {
		return switch (mode) {
			case BEAT_7K -> key == 5 || key == 6;
			case BEAT_14K -> key == 5 || key == 6 || key == 14 || key == 15;
			default -> false;
		};
	}

	static boolean isSevenKey(bms.model.Mode mode, int key) {
		return switch (mode) {
			case BEAT_7K -> key == 6;
			case BEAT_14K -> key == 6 || key == 15;
			default -> false;
		};
	}

	private void changeSixSevenCoverValue(boolean increase) {
		LaneRenderer laneRenderer = player.getLanerender();
		PlayerConfig config = player.resource.getPlayerConfig();
		float delta = config.getBmsirCoverChangeStep() / 1000f;
		if (!increase) {
			delta = -delta;
		}

		if (PlayerConfig.BMSIR_COVER_CONTROL_LR2.equals(
				config.getBmsirCoverControlMode()
		)) {
			setSixSevenLaneCoverValue(
					laneRenderer,
					laneRenderer.getLanecover() + delta
			);
		} else if (laneRenderer.isEnableLanecover()) {
			setSixSevenLaneCoverValue(
					laneRenderer,
					laneRenderer.getLanecover() + delta
			);
		} else if (laneRenderer.isEnableHidden() && laneRenderer.isEnableLift()) {
			if (isChangeLift) {
				laneRenderer.setLiftRegion(laneRenderer.getLiftRegion() - delta);
			} else {
				laneRenderer.setHiddenCover(laneRenderer.getHiddenCover() - delta);
			}
		} else if (laneRenderer.isEnableHidden()) {
			laneRenderer.setHiddenCover(laneRenderer.getHiddenCover() - delta);
		} else if (laneRenderer.isEnableLift()) {
			laneRenderer.setLiftRegion(laneRenderer.getLiftRegion() - delta);
		}

		if (shouldRecalculateSixSevenHispeed(
				config.isBmsirCoverHispeedAutoAdjustEnabled(),
				hispeedAutoAdjust,
				laneRenderer.getNowBPM()
		)) {
			laneRenderer.resetHispeed(laneRenderer.getNowBPM());
		}
	}

	private static void setSixSevenLaneCoverValue(
			LaneRenderer laneRenderer,
			float laneCover
	) {
		if (keepsIidxFhsLaneCoverReset(
				laneRenderer.getPlayConfig().getFixhispeed()
		)) {
			laneRenderer.setLanecover(laneCover);
		} else {
			laneRenderer.setLanecoverWithoutHispeedReset(laneCover);
		}
	}

	static boolean keepsIidxFhsLaneCoverReset(int fixHispeed) {
		return fixHispeed == PlayConfig.FIX_HISPEED_IIDX_FHS;
	}

	static boolean shouldRecalculateSixSevenHispeed(
			boolean bmsirAutoAdjust,
			boolean playAutoAdjust,
			double nowBpm
	) {
		return bmsirAutoAdjust && playAutoAdjust && nowBpm > 0;
	}

	public void setEnableControl(boolean b) {
		enableControl = b;
	}

	public void setEnableCursor(boolean b) {
		enableCursor = b;
	}

	public void input() {
		final LaneRenderer lanerender = player.getLanerender();
		final BMSPlayerInputProcessor input = player.main.getInputProcessor();
		// 各種コントロール入力判定
		if (enableControl) {
			if (enableCursor) {
				if (input.getControlKeyState(ControlKeys.UP)) {
					if (!cursorpressed) {
						setCoverValue(-0.01f);
						cursorpressed = true;
					}
				} else if (input.getControlKeyState(ControlKeys.DOWN)) {
					if (!cursorpressed) {
						setCoverValue(0.01f);
						cursorpressed = true;
					}
				} else {
					cursorpressed = false;
				}
			}
			// move lane cover by mouse wheel
			if (input.getScroll() != 0) {
				setCoverValue(- input.getScroll() * 0.005f);
				input.resetScroll();
			}
			if ((input.startPressed())
					|| (player.resource.getPlayerConfig().isWindowHold() && player.timer.isTimerOn(TIMER_PLAY) && !player.isNoteEnd())) {
				if ((autoplay.mode == BMSPlayerMode.Mode.PLAY || autoplay.mode == BMSPlayerMode.Mode.PRACTICE) && startpressed) {
					processStart.run();
				} else if ((autoplay.mode == BMSPlayerMode.Mode.PLAY || autoplay.mode == BMSPlayerMode.Mode.PRACTICE) && !startpressed) {
					Arrays.fill(hschanged, true);
				}
				// show-hide lane cover by double-press START
				if (!startpressed) {
					long stime = System.currentTimeMillis();
					if (stime < startpressedtime + 500) {
						lanerender.setEnableLanecover(!lanerender.isEnableLanecover());
						startpressedtime = 0;
					} else {
						startpressedtime = stime;
					}
				}
				startpressed = true;
			} else {
				startpressed = false;
			}
			if(input.isSelectPressed()){
				if ((autoplay.mode == BMSPlayerMode.Mode.PLAY || autoplay.mode == BMSPlayerMode.Mode.PRACTICE) && selectpressed) {
					processSelect.run();
				} else if ((autoplay.mode == BMSPlayerMode.Mode.PLAY || autoplay.mode == BMSPlayerMode.Mode.PRACTICE) && !selectpressed) {
					Arrays.fill(hschanged, true);
				}
				if (!selectpressed) {
					long stime = System.currentTimeMillis();
					if (stime < selectpressedtime + 500) {
						lanerender.setEnableHidden(!lanerender.isEnableHidden());
						selectpressedtime = 0;
					} else {
						selectpressedtime = stime;
					}
				}
				selectpressed = true;
			} else {
				selectpressed = false;
			}
			if ((input.startPressed() && input.isSelectPressed())) {
				if(!startAndSelectPressed) {
					isChangeLift = !isChangeLift;
				}
				startAndSelectPressed = true;
			} else {
				startAndSelectPressed = false;
			}
		}
		long now = System.currentTimeMillis();
		boolean abortBlocked = BMSIRArenaClient.isAbortInputBlocked();
		if(!abortBlocked && ((input.startPressed() && input.isSelectPressed() && now - exitpressedtime > exitPressDuration )||
				(player.isNoteEnd() && (input.startPressed() || input.isSelectPressed())))){
			input.startChanged(false);
			input.setSelectPressed(false);
			player.stopPlay();
		}else if(!(input.startPressed() && input.isSelectPressed())){
			exitpressedtime = now;
		}
		// stop playing
		if (!abortBlocked && input.isControlKeyPressed(ControlKeys.ESCAPE)) {
			player.stopPlay();
		}
		// play speed change (autoplay or replay only)
		if (autoplay.mode == BMSPlayerMode.Mode.AUTOPLAY || autoplay.mode == BMSPlayerMode.Mode.REPLAY) {
			if (input.getControlKeyState(ControlKeys.NUM1)) {
				player.setPlaySpeed(25);
			} else if (input.getControlKeyState(ControlKeys.NUM2)) {
				player.setPlaySpeed(50);
			} else if (input.getControlKeyState(ControlKeys.NUM3)) {
				player.setPlaySpeed(200);
			} else if (input.getControlKeyState(ControlKeys.NUM4)) {
				player.setPlaySpeed(300);
			} else {
				player.setPlaySpeed(100);
			}
		}
	}

	/*
	 * 状況に応じてレーンカバー/リフト/HIDDENの表示量を変える
	 * レーンカバー: 「レーンカバーがオン」もしくは「リフトとHIDDENが共にオフ」
	 * リフト: 「レーンカバーがオフ」
	 * HIDDEN: 「レーンカバーがオフ」かつ「リフトがオフ」
	 * 「レーンカバーがオフ」で「リフトとHIDDENが共にオン」の時は「START+SELECT短押し」で切り替え
	 */
	private void setCoverValue(float value) {
		final LaneRenderer lanerender = player.getLanerender();
		if(lanerender.isEnableLanecover() || (!lanerender.isEnableLift() && !lanerender.isEnableHidden())) {
			lanerender.setLanecover(lanerender.getLanecover() + value);

		} else if(lanerender.isEnableHidden()) {
			lanerender.setHiddenCover(lanerender.getHiddenCover() - value);
		}
		else if(lanerender.isEnableLift() && isChangeLift) {
			lanerender.setLiftRegion(lanerender.getLiftRegion() - value);
		}

		if (hispeedAutoAdjust && lanerender.getNowBPM() > 0) {
			lanerender.resetHispeed(lanerender.getNowBPM());
		}
	}

	private void changeCoverValue(int key, boolean up) {
		final BMSPlayerInputProcessor input = player.main.getInputProcessor();

		// move lane cover by START + Scratch
		if(input.isAnalogInput(key)) {
			// analog
			int dTicks = input.getAnalogDiffAndReset(key, 200) * (up ? 1 : -1);
			if (dTicks != 0) {
				setCoverValue(dTicks*coverChangeMarginLow);
			}
		} else {
			// non-analog
			if (input.getKeyState(key)) {
				long l = System.currentTimeMillis();
				if(laneCoverStartTiming == Long.MIN_VALUE) laneCoverStartTiming = l;
				if (l - lanecovertiming > 50) {
					setCoverValue((up ? 1 : -1) * (l - laneCoverStartTiming > coverSpeedSwitchDuration ? coverChangeMarginHigh : coverChangeMarginLow));
					lanecovertiming = l;
				}
			} else if(laneCoverStartTiming != Long.MIN_VALUE) {
				laneCoverStartTiming = Long.MIN_VALUE;
			}
		}
	}

	private void changeDuration(int key, boolean up) {
		final LaneRenderer lanerender = player.getLanerender();
		final BMSPlayerInputProcessor input = player.main.getInputProcessor();

		// change duration by SELECT + Scratch
		if(input.isAnalogInput(key)) {
			// analog
			int dTicks = input.getAnalogDiffAndReset(key, 200) * (up ? 1 : -1);
			if (dTicks != 0) {
				lanerender.setDuration(lanerender.getDuration() + dTicks);
			}
		} else {
			// non-analog
			if (input.getKeyState(key)) {
				long l = System.currentTimeMillis();
				if (l - lanecovertiming > 50) {
					lanerender.setDuration(lanerender.getDuration() + (up ? 1 : -1));
					lanecovertiming = l;
				}
			}
		}
	}
}
