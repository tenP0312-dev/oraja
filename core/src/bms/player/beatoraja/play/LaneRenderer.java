package bms.player.beatoraja.play;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.player.beatoraja.*;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacPlayContext;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;
import bms.player.beatoraja.play.SkinNote.SkinLane;

import bms.model.*;
import bms.player.beatoraja.skin.Skin.SkinObjectRenderer;
import bms.player.beatoraja.skin.SkinObject.SkinOffset;
import bms.player.beatoraja.skin.SkinImage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.GdxRuntimeException;

import static bms.player.beatoraja.CourseData.CourseDataConstraint.*;
import static bms.player.beatoraja.skin.SkinProperty.*;

/**
 * レーン描画用クラス
 * 
 * @author exch
 */
public class LaneRenderer {
	private static final Logger logger = LoggerFactory.getLogger(LaneRenderer.class);
	
	private float basehispeed;

	private float hispeedmargin = 0.25f;

	private BMSModel model;
	private TimeLine[] timelines;

	private int pos;

	private final BMSPlayer main;

	private BitmapFont font;
	private final PlaySkin skin;

	private final PlayerConfig config;
	private PlayConfig playconfig;
	private StartHerePreviewData startHerePreview;
	private final Rectangle startHerePreviewDestination = new Rectangle();
	private final BMSIRManiacVisualEffects.Transform maniacTransform =
			new BMSIRManiacVisualEffects.Transform();
	private final BMSIRManiacVisualEffects.Transform maniacLongEndTransform =
			new BMSIRManiacVisualEffects.Transform();
	private BMSIRManiacSettings maniacSettings;

	private int currentduration;
	private double currentscroll = 1.0;
	private final boolean bmsirLr2HispeedFixEnabled;
	private boolean pseudoFhsLatched;
	private int lockedGreen;

	private double basebpm;
	private double nowbpm;
	private double mainbpm;
	private double minbpm;
	private double maxbpm;
	
	//PMSのリズムに合わせたノートの拡大用
	//4分から最大拡大までの時間
	private final float noteExpansionTime = 9;
	//最大拡大から通常サイズに戻るまでの時間
	private final float noteContractionTime = 150;

	public LaneRenderer(BMSPlayer main, BMSModel model) {

		this.main = main;
		Pixmap hp = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		hp.drawPixel(0, 0, Color.toIntBits(255, 255, 255, 255));
		hp.dispose();

		try {
			FreeTypeFontGenerator generator = new FreeTypeFontGenerator(
					Gdx.files.internal(main.main.getConfig().getSystemfontpath()));
			FreeTypeFontParameter parameter = new FreeTypeFontParameter();
			parameter.size = 18;
			font = generator.generateFont(parameter);
			generator.dispose();
		} catch (GdxRuntimeException e) {
			logger.error("Practice Font読み込み失敗");
		}

		this.skin = (PlaySkin) main.getSkin();
		this.config = main.resource.getPlayerConfig();
		BMSIRManiacPlayContext maniacContext = main.resource.getManiacPlayContext();
		this.maniacSettings = maniacContext == null ? null : maniacContext.settings();
		this.playconfig = config.getPlayConfig(model.getMode()).getPlayconfig().clone();
		boolean noSpeedConstraint = false;
		for (CourseData.CourseDataConstraint constraint : main.resource.getConstraint()) {
			if (constraint == NO_SPEED) {
				noSpeedConstraint = true;
				break;
			}
		}
		BMSPlayerMode playMode = main.resource.getPlayMode();
		Boolean replaySetting = playconfig.getBmsirLr2HispeedFixReplayEnabled();
		boolean replayUsesSnapshot = playMode != null
				&& playMode.mode == BMSPlayerMode.Mode.REPLAY
				&& replaySetting != null;
		this.bmsirLr2HispeedFixEnabled = !noSpeedConstraint
				&& (replayUsesSnapshot
						? replaySetting
						: config.isBmsirLr2HispeedFixEnabled());
		playconfig.setBmsirLr2HispeedFixReplayEnabled(
				this.bmsirLr2HispeedFixEnabled
		);

		init(model);

		for (CourseData.CourseDataConstraint i : main.resource.getConstraint()) {
			if (i == NO_SPEED) {
				playconfig.setHispeed(1.0f);
				playconfig.setLanecover(0);
				playconfig.setLift(0);
				playconfig.setHidden(0);
				playconfig.setEnableConstant(false);
			}
		}
	}

	public void init(BMSModel model) {
		pos = chartStartTimelinePosition();
		this.model = model;
		BMSIRManiacPlayContext maniacContext = main.resource.getManiacPlayContext();
		this.maniacSettings = maniacContext == null ? null : maniacContext.settings();
		List<TimeLine> tls = new ArrayList<TimeLine>();
		double cbpm = model.getBpm();
		double cscr = 1.0;
		for (TimeLine tl : model.getAllTimeLines()) {
			if (cbpm != tl.getBPM() || tl.getStop() > 0 || cscr != tl.getScroll() || tl.getSectionLine()) {
				tls.add(tl);
			} else if (tl.existNote() || tl.existHiddenNote()) {
				tls.add(tl);
			}
			cbpm = tl.getBPM();
			cscr = tl.getScroll();
		}
		this.timelines = tls.toArray(new TimeLine[tls.size()]);
		// logger.info("省略したTimeLine数:" +
		// (model.getAllTimeLines().length - timelines.length) + " / " +
		// model.getAllTimeLines().length);

		minbpm = model.getMinBPM();
		maxbpm = model.getMaxBPM();
		Map<Double, Integer> m = new HashMap<Double, Integer>();
		for (TimeLine tl : model.getAllTimeLines()) {
			Integer count = m.get(tl.getBPM());
			if (count == null) {
				count = 0;
			}
			m.put(tl.getBPM(), count + tl.getTotalNotes());
		}
		int maxcount = 0;
		for (double bpm : m.keySet()) {
			if (m.get(bpm) > maxcount) {
				maxcount = m.get(bpm);
				mainbpm = bpm;
			}
		}
		basebpm = switch (playconfig.getFixhispeed()) {
			case PlayConfig.FIX_HISPEED_OFF -> basebpm;
			case PlayConfig.FIX_HISPEED_STARTBPM -> model.getBpm();
			case PlayConfig.FIX_HISPEED_MINBPM -> minbpm;
			case PlayConfig.FIX_HISPEED_MAXBPM -> maxbpm;
			case PlayConfig.FIX_HISPEED_MAINBPM -> mainbpm;
			default -> basebpm;
		};

		this.setLanecover(playconfig.getLanecover());
		basehispeed = playconfig.getHispeed();
		this.hispeedmargin = playconfig.getHispeedMargin();
		this.startHerePreview = StartHerePreviewData.build(model);
		updateStartHerePreviewMetrics();
	}

	/** Restarts the monotonic timeline scan without restoring saved play settings. */
	public void resetTimelinePosition() {
		pos = chartStartTimelinePosition();
	}

	static int chartStartTimelinePosition() {
		return 0;
	}

	public float getHispeed() {
		return playconfig.getHispeed();
	}

	public float getAppliedHispeed() {
		return appliedHispeed(nowbpm, currentscroll);
	}

	public int getDuration() {
		if (bmsirLr2HispeedFixEnabled) {
			return pseudoFhsLatched
					? lockedGreen
					: BMSIRHispeed.equivalentGreen(playconfig);
		}
		return playconfig.getDuration();
	}

	public void setDuration(int gvalue) {
		playconfig.setDuration(Math.max(PlayConfig.DURATION_MIN, gvalue));
		if (bmsirLr2HispeedFixEnabled) {
			if (pseudoFhsLatched) {
				lockedGreen = playconfig.getDuration();
			} else {
				playconfig.setBmsirBaseScrollSpeed(
						BMSIRHispeed.baseScrollSpeedForGreen(
								playconfig,
								playconfig.getDuration()
						)
				);
				playconfig.setDuration(BMSIRHispeed.equivalentGreen(playconfig));
			}
			updateStartHerePreviewMetrics();
			return;
		}
		setLanecover(playconfig.getLanecover());
	}

	public int getCurrentDuration() {
		return currentduration;
	}

	public float getHispeedmargin() {
		return hispeedmargin;
	}

	public void setHispeedmargin(float hispeedmargin) {
		this.hispeedmargin = hispeedmargin;
	}

	public void rebuildStartHerePreview() {
		this.startHerePreview = StartHerePreviewData.build(model);
		updateStartHerePreviewMetrics();
	}

	public boolean isEnableLift() {
		return playconfig.isEnablelift();
	}

	public float getLiftRegion() {
		return playconfig.getLift();
	}

	public void setLiftRegion(float liftRegion) {
		playconfig.setLift(liftRegion < 0 ? 0 : (liftRegion > 1 ? 1 : liftRegion));
		updateStartHerePreviewMetrics();
	}

	public float getLanecover() {
		return playconfig.getLanecover();
	}

	public void resetHispeed(double targetbpm) {
		if (bmsirLr2HispeedFixEnabled) {
			updateStartHerePreviewMetrics();
			return;
		}
		if (playconfig.getFixhispeed() != PlayConfig.FIX_HISPEED_OFF) {
			playconfig.setHispeed(BMSIRHispeed.hispeedForDuration(
					playconfig.getDuration(),
					targetbpm,
					1.0,
					playconfig.isEnablelanecover(),
					playconfig.getLanecover(),
					false,
					0f
			));
		}
	}
	
	public void setLanecover(float lanecover) {
		setLanecoverValue(lanecover);
		if (!bmsirLr2HispeedFixEnabled) {
			resetHispeed(basebpm);
		}
		updateStartHerePreviewMetrics();
	}

	void changeLanecover(
			float lanecover,
			boolean recalculateHispeed,
			double targetBpm
	) {
		setLanecoverValue(lanecover);
		if (bmsirLr2HispeedFixEnabled) {
			// LR2 mode keeps the applied speed independent from cover. The
			// pseudo-FHS path derives speed from lockedGreen when rendered.
		} else if (recalculateHispeed && Double.isFinite(targetBpm) && targetBpm > 0.0) {
			resetHispeed(targetBpm);
		} else {
			syncFixedDurationToHispeed();
		}
		updateStartHerePreviewMetrics();
	}

	private void setLanecoverValue(float lanecover) {
		playconfig.setLanecover(lanecover < 0 ? 0 : (lanecover > 1 ? 1 : lanecover));
	}

	private void syncFixedDurationToHispeed() {
		if (playconfig.getFixhispeed() == PlayConfig.FIX_HISPEED_OFF
				|| !Double.isFinite(basebpm)
				|| basebpm <= 0.0
				|| !Float.isFinite(playconfig.getHispeed())
				|| playconfig.getHispeed() <= 0f) {
			return;
		}
		playconfig.setDuration(fixedDurationForHispeed(
				basebpm,
				playconfig.getHispeed(),
				playconfig.isEnablelanecover(),
				playconfig.getLanecover()
		));
	}

	static int fixedDurationForHispeed(
			double bpm,
			float hispeed,
			boolean laneCoverEnabled,
			float laneCover
	) {
		return Math.min(PlayConfig.DURATION_MAX, BMSIRHispeed.currentDuration(
				hispeed,
				bpm,
				1.0,
				laneCoverEnabled,
				laneCover,
				false,
				0f
		));
	}

	public void setEnableLanecover(boolean b) {
		playconfig.setEnablelanecover(b);
		if (!bmsirLr2HispeedFixEnabled) {
			syncFixedDurationToHispeed();
		}
		updateStartHerePreviewMetrics();
	}

	public boolean isEnableLanecover() {
		return playconfig.isEnablelanecover();
	}

	public float getHiddenCover() {
		return playconfig.getHidden();
	}

	public void setHiddenCover(float hiddenCover) {
		playconfig.setHidden(hiddenCover < 0 ? 0 : (hiddenCover > 1 ? 1 : hiddenCover));
	}

	public void setEnableHidden(boolean b) {
		playconfig.setEnablehidden(b);
	}

	public boolean isEnableHidden() {
		return playconfig.isEnablehidden();
	}

	public void changeHispeed(boolean b) {
		float f;
		if (playconfig.getFixhispeed() != PlayConfig.FIX_HISPEED_OFF) {
			f = basehispeed * hispeedmargin * (b ? 1 : -1);
		} else {
			f = hispeedmargin * (b ? 1 : -1);
		}
		if (playconfig.getHispeed() + f > 0 && playconfig.getHispeed() + f < 20) {
			boolean relock = bmsirLr2HispeedFixEnabled && pseudoFhsLatched;
			if (relock) {
				pseudoFhsLatched = false;
			}
			playconfig.setHispeed(playconfig.getHispeed() + f);
			if (relock) {
				lockedGreen = BMSIRHispeed.currentDuration(
						appliedHispeed(nowbpm, currentscroll),
						nowbpm,
						currentscroll,
						playconfig.isEnablelanecover(),
						playconfig.getLanecover(),
						playconfig.isEnablelift(),
						playconfig.getLift()
				);
				pseudoFhsLatched = true;
			} else if (!bmsirLr2HispeedFixEnabled) {
				syncFixedDurationToHispeed();
			}
			updateStartHerePreviewMetrics();
		}
	}

	public PlayConfig getPlayConfig() {
		if (!bmsirLr2HispeedFixEnabled) {
			syncFixedDurationToHispeed();
		}
		return playconfig;
	}

	public boolean isBmsirLr2HispeedFixEnabled() {
		return bmsirLr2HispeedFixEnabled;
	}

	public boolean isPseudoFhsLatched() {
		return pseudoFhsLatched;
	}

	public boolean togglePseudoFhsLatch() {
		if (!bmsirLr2HispeedFixEnabled) {
			return false;
		}
		if (pseudoFhsLatched) {
			pseudoFhsLatched = false;
		} else {
			lockedGreen = Math.max(PlayConfig.DURATION_MIN, currentduration);
			playconfig.setDuration(lockedGreen);
			pseudoFhsLatched = true;
		}
		updateStartHerePreviewMetrics();
		return pseudoFhsLatched;
	}

	public void setBmsirBaseScrollSpeed(int value) {
		playconfig.setBmsirBaseScrollSpeed(
				BMSIRHispeed.clampBaseScrollSpeed(value)
		);
		updateStartHerePreviewMetrics();
	}

	private float appliedHispeed(double bpm, double scroll) {
		if (!bmsirLr2HispeedFixEnabled) {
			return playconfig.getHispeed();
		}
		if (pseudoFhsLatched) {
			return BMSIRHispeed.hispeedForDuration(
					lockedGreen,
					bpm,
					scroll,
					playconfig.isEnablelanecover(),
					playconfig.getLanecover(),
					playconfig.isEnablelift(),
					playconfig.getLift()
			);
		}
		return BMSIRHispeed.appliedHispeed(
				playconfig.getHispeed(),
				playconfig.getBmsirBaseScrollSpeed(),
				playconfig.getFixhispeed() != PlayConfig.FIX_HISPEED_OFF,
				basebpm
		);
	}

	public void drawLane(SkinObjectRenderer sprite, long time, SkinLane[] lanes, SkinOffset[] offsets) {
		float offsetX = 0;
		float offsetY = 0;
		float offsetW = 0;
		float offsetH= 0;
		for(SkinOffset offset : offsets) {
			offsetX += offset.x;
			offsetY += offset.y;
			offsetW += offset.w;
			offsetH += offset.h;
		}
		if (showsStartHerePreview(
				main.getState(),
				playconfig.isStartHerePreviewEnabled()
		)) {
			updateStartHerePreviewMetrics();
			updatePlayCoverOffsets(lanes);
			if (drawStartHerePreview(
					sprite,
					lanes,
					time,
					offsetX,
					offsetY,
					offsetW,
					offsetH
			)) {
				return;
			}
		}
		
		time = (main.timer.isTimerOn(TIMER_PLAY) ? time - main.timer.getTimer(TIMER_PLAY) : 
			(main.timer.isTimerOn(141) ? time - main.timer.getTimer(141) : 0)) + config.getJudgetiming();
		if (main.getState() == BMSPlayer.STATE_PRACTICE) {
			time = main.getPracticeConfiguration().getPracticeProperty().starttime;
			pos = chartStartTimelinePosition();
		}
		final long microtime = time * 1000;
		final boolean showTimeline = (main.getState() == BMSPlayer.STATE_PRACTICE);

		final Rectangle[] playerr = skin.getLaneGroupRegion();
		double bpm = model.getBpm();
		double nbpm = bpm;
		double nscroll = 1.0;
		for (int i = (pos > 5 ? pos - 5 : 0); i < timelines.length && timelines[i].getMicroTime() <= microtime; i++) {
			nbpm = timelines[i].getBPM();
			nscroll = timelines[i].getScroll();
		}
		nowbpm = nbpm;
		currentscroll = nscroll;
		final float hispeed = main.getState() != BMSPlayer.STATE_PRACTICE
				? appliedHispeed(nbpm, nscroll)
				: 1.0f;
		final double region = nscroll > 0 ? (240000 / nbpm / hispeed) / nscroll : 0;
		// double sect = (bpm / 60) * 4 * 1000;
		// TODO hu,hlをレーン毎に変更
		final double hu = lanes[0].region.y + lanes[0].region.height;
		final double hl = playconfig.isEnablelift() ? lanes[0].region.y + lanes[0].region.height * playconfig.getLift() : lanes[0].region.y;
		final double rxhs = (hu - hl) * hispeed;
		double y = hl;

		final float lanecover = playconfig.isEnablelanecover() ? playconfig.getLanecover() : 0;
		currentduration = bmsirLr2HispeedFixEnabled
				? BMSIRHispeed.currentDuration(
						hispeed,
						nbpm,
						nscroll,
						playconfig.isEnablelanecover(),
						playconfig.getLanecover(),
						playconfig.isEnablelift(),
						playconfig.getLift()
				)
				: (int) Math.round(region * (1 - lanecover));
		
		main.main.getOffset(OFFSET_LIFT).y = (float) (hl - lanes[0].region.y);
		main.main.getOffset(OFFSET_LANECOVER).y = (float) ((hl - hu) * lanecover);
		// TODO HIDDENとLIFT混在の必要性とHIDDENの必要性
		final SkinOffset hidden = main.main.getOffset(OFFSET_HIDDEN_COVER);
		if (playconfig.isEnablehidden()) {
			hidden.a = 0;
			if (playconfig.isEnablelift()) {
				hidden.y =  (1 - playconfig.getLift()) * playconfig.getHidden()
						* skin.getLaneRegion()[0].height;
			} else {
				hidden.y = playconfig.getHidden() * skin.getLaneRegion()[0].height;
			}
		} else {
			hidden.a = -255;
		}

		// 判定エリア表示
		if (config.isShowjudgearea()) {
			final Color[] color = { Color.valueOf("0000ff20"), Color.valueOf("00ff0020"), Color.valueOf("ffff0020"),
					Color.valueOf("ff800020"), Color.valueOf("ff000020") };
			for (int lane = 0; lane < lanes.length; lane++) {
				final long[][] judgetime = main.getJudgeManager().getJudgeTimeRegion(lane);
				for (int i = pos; i < timelines.length; i++) {
					final TimeLine tl = timelines[i];
					if (tl.getMicroTime() >= microtime) {
						double rate = (tl.getSection() - (i > 0 ? timelines[i - 1].getSection() : 0)) * (i > 0 ? timelines[i - 1].getScroll() : 1.0) * rxhs
								/ (tl.getMicroTime() - (i > 0
										? timelines[i - 1].getMicroTime() + timelines[i - 1].getMicroStop() : 0));
						for (int j = color.length - 1; j >= 0; j--) {
							sprite.setColor(color[j]);
							long nj = j > 0 ? judgetime[j - 1][1] : 0;
							sprite.draw(main.getImage(IMAGE_WHITE), lanes[lane].region.x, (float) (hl + nj * rate), lanes[lane].region.width,
									(float) ((judgetime[j][1] - nj) * rate));
						}
						break;
					}
				}
			}
		}

		// draw section line
		final double orgy = y;
		final boolean enableConstant = playconfig.isEnableConstant() && (main.getState() != BMSPlayer.STATE_PRACTICE);
		final int baseduration = playconfig.getDuration();
		final float alphaLimit =  playconfig.getConstantFadeinTime() * 1000;
		for (int i = pos; i < timelines.length && y <= hu; i++) {
			final TimeLine tl = timelines[i];
			if (tl.getMicroTime() >= microtime) {
				if (enableConstant) {
					final long targetTime = microtime + (baseduration * 1000);
					final long timeDifference = tl.getMicroTime() - targetTime;
					if(alphaLimit >= 0) {
						if (tl.getMicroTime() >= targetTime) {
						    if (timeDifference < alphaLimit) {
						    	// フェードイン処理
						        sprite.setColor(1f, 1f, 1f, (alphaLimit - timeDifference) / alphaLimit);
						    } else {
						    	// ノーツ非表示
						        continue;
						    }
						} else {
						    sprite.setColor(Color.WHITE);
						}
					} else {
						if (tl.getMicroTime() >= targetTime) {
					    	// ノーツ非表示
							continue;
						} else {
						    if (timeDifference > alphaLimit) {
						    	// フェードイン処理
						        sprite.setColor(1f, 1f, 1f, 1f - (alphaLimit - timeDifference) / alphaLimit);
						    } else {
							    sprite.setColor(Color.WHITE);
						    }
						}						
					}
				}

				if (i > 0) {
					final TimeLine prevtl = timelines[i - 1];
					if (prevtl.getMicroTime() + prevtl.getMicroStop() > microtime) {
						y += (tl.getSection() - prevtl.getSection()) * prevtl.getScroll() * rxhs;
					} else {
						y += (tl.getSection() - prevtl.getSection()) * prevtl.getScroll() * (tl.getMicroTime() - microtime)
								/ (tl.getMicroTime() - prevtl.getMicroTime() - prevtl.getMicroStop()) * rxhs;
					}
				} else {
					y += tl.getSection() * (tl.getMicroTime() - microtime) / tl.getMicroTime() * rxhs;
				}
				if (showTimeline && (i > 0 && (tl.getTime() / 1000) > (timelines[i - 1].getTime() / 1000))) {
					for (SkinImage line : skin.getTimeLine()) {
						line.draw(sprite, time, main, 0, (int) (y - hl));
					}
					for (Rectangle r : playerr) {
						// TODO 数値もスキンベースへ移行
						if(font != null) {
							sprite.draw(font, String.format("%2d:%02d.%1d", tl.getTime() / 60000,
									(tl.getTime() / 1000) % 60, (tl.getTime() / 100) % 10), r.x + 4, (float) (y + 20), Color.valueOf("40c0c0"));							
						}
					}
				}

				if (config.isBpmguide() || showTimeline) {
					if (tl.getBPM() != nbpm) {
						for (SkinImage line : skin.getBPMLine()) {
							line.draw(sprite, time, main, 0, (int) (y - hl));
						}
						for (Rectangle r : playerr) {
							// TODO 数値もスキンベースへ移行
							if(font != null) {
								sprite.draw(font, "BPM" + ((int) tl.getBPM()), r.x + r.width / 2, (float) (y + 20), Color.valueOf("00c000"));
							}
							
						}

					}
					if (tl.getStop() > 0) {
						for (SkinImage line : skin.getStopLine()) {
							line.draw(sprite, time, main, 0, (int) (y - hl));
						}
						for (Rectangle r : playerr) {
							// TODO 数値もスキンベースへ移行
							if(font != null) {
								sprite.draw(font, "STOP " + ((int) tl.getStop()) + "ms", r.x + r.width / 2, (float) (y + 20), Color.valueOf("c0c000"));								
							}
						}
					}

				}
				// 小節線描画
				if (tl.getSectionLine()) {
					for (SkinImage line : skin.getLine()) {
						line.draw(sprite, time, main, 0, (int) (y - hl));
					}
				}
				nbpm = tl.getBPM();
			} else if (pos == i - 1) {
				boolean b = true;
				for (int lane = 0; lane < lanes.length; lane++) {
					final Note note = tl.getNote(lane);
					if (note != null && ((note instanceof LongNote ln && (ln.isEnd() ? ln : ln.getPair()).getMicroTime() >= microtime)
							|| (config.isShowpastnote() && note instanceof NormalNote && note.getState() == 0))) {
						b = false;
						break;
					}
				}
				if (b) {
					pos = i;
				}
			}
		}

		sprite.setColor(Color.WHITE);
		sprite.setBlend(0);
		sprite.setType(SkinObjectRenderer.TYPE_NORMAL);
		y = orgy;
		final long now = main.timer.getNowTime();
		
		for (int i = pos; i < timelines.length && y <= hu; i++) {
			final TimeLine tl = timelines[i];
			if (enableConstant) {
				final long targetTime = microtime + (baseduration * 1000);
				final long timeDifference = tl.getMicroTime() - targetTime;
				if(alphaLimit >= 0) {
					if (tl.getMicroTime() >= targetTime) {
					    if (timeDifference < alphaLimit) {
					    	// フェードイン処理
					        sprite.setColor(1f, 1f, 1f, (alphaLimit - timeDifference) / alphaLimit);
					    } else {
					    	// ノーツ非表示
					        continue;
					    }
					} else {
					    sprite.setColor(Color.WHITE);
					}
				} else {
					if (tl.getMicroTime() >= targetTime) {
				    	// ノーツ非表示
						continue;
					} else {
					    if (timeDifference > alphaLimit) {
					    	// フェードイン処理
					        sprite.setColor(1f, 1f, 1f, 1f - (alphaLimit - timeDifference) / alphaLimit);
					    } else {
						    sprite.setColor(Color.WHITE);
					    }
					}						
				}
			}

			if (tl.getMicroTime() >= microtime) {
				if (i > 0) {
					final TimeLine prevtl = timelines[i - 1];
					if (prevtl.getMicroTime() + prevtl.getMicroStop() > microtime) {
						y += (tl.getSection() - prevtl.getSection()) * prevtl.getScroll() * rxhs;
					} else {
						y += (tl.getSection() - prevtl.getSection()) * prevtl.getScroll() * (tl.getMicroTime() - microtime)
								/ (tl.getMicroTime() - prevtl.getMicroTime() - prevtl.getMicroStop()) * rxhs;
					}
				} else {
					y += tl.getSection() * (tl.getMicroTime() - microtime) / tl.getMicroTime() * rxhs;
				}
			}
			// ノート描画
			for (int lane = 0; lane < lanes.length; lane++) {
				final float scale = lanes[lane].scale;
				final Note note = tl.getNote(lane);
				if (note != null) {
					//4分のタイミングでノートを拡大する
					float dstx = lanes[lane].region.x + offsetX;
					float dsty = noteDestinationY((float) y, offsetY);
					float dstw = lanes[lane].region.width + offsetW;
					float dsth = scale + offsetH;
					if(skin.getNoteExpansionRate()[0] != 100 || skin.getNoteExpansionRate()[1] != 100) {
						if((now - main.getNowQuarterNoteTime()) < noteExpansionTime) {
							dstw *= 1 + (skin.getNoteExpansionRate()[0]/100.0f - 1) * (now - main.getNowQuarterNoteTime()) / noteExpansionTime;
							dsth *= 1 + (skin.getNoteExpansionRate()[1]/100.0f - 1) * (now - main.getNowQuarterNoteTime()) / noteExpansionTime;
							dstx -= (dstw - lanes[lane].region.width) / 2;
							dsty -= (dsth - scale) / 2;
						} else if((now - main.getNowQuarterNoteTime()) >= noteExpansionTime && (now - main.getNowQuarterNoteTime()) <= (noteExpansionTime + noteContractionTime)) {
							dstw *= 1 + (skin.getNoteExpansionRate()[0]/100.0f - 1) * (noteContractionTime - (now - main.getNowQuarterNoteTime() - noteExpansionTime)) / noteContractionTime;
							dsth *= 1 + (skin.getNoteExpansionRate()[1]/100.0f - 1) * (noteContractionTime - (now - main.getNowQuarterNoteTime() - noteExpansionTime)) / noteContractionTime;
							dstx -= (dstw - lanes[lane].region.width) / 2;
							dsty -= (dsth - scale) / 2;
						}
					}
					BMSIRManiacVisualEffects.apply(
							maniacTransform,
							maniacSettings,
							lane,
							lanes.length,
							model.getMode().player,
							i,
							now,
							dstx,
							dsty,
							dstw,
							dsth,
							(float) hl,
							(float) hu
					);
					if (!maniacTransform.visible) continue;
					dstx = maniacTransform.x;
					dsty = maniacTransform.y;
					dstw = maniacTransform.width;
					dsth = maniacTransform.height;
					if (note instanceof NormalNote) {
						// draw normal note
						if (lanes[lane].dstnote2 != Integer.MIN_VALUE) {
							if (tl.getMicroTime() >= microtime && (note.getState() == 0 || note.getState() >= 4)) {
								final TextureRegion s = config.isMarkprocessednote() && note.getState() != 0
								? lanes[lane].processedImage : lanes[lane].noteImage;
								sprite.draw(s, dstx, dsty, dstw, dsth);
							}
						} else if (tl.getMicroTime() >= microtime || (config.isShowpastnote() && note.getState() == 0)) {
							final TextureRegion s = config.isMarkprocessednote() && note.getState() != 0
									? lanes[lane].processedImage : lanes[lane].noteImage;
							sprite.draw(s, dstx, dsty, dstw, dsth);
						}
					} else if (note instanceof LongNote ln) {
						if (!ln.isEnd() && ln.getPair().getMicroTime() >= microtime) {
							// if (((LongNote) note).getEnd() == null) {
							// logger.warn(
							// "LN終端がなく、モデルが正常に表示されません。LN開始時間:"
							// + ((LongNote) note)
							// .getStart()
							// .getTime());
							// } else {
							double dy = 0;
							TimeLine prevtl = tl;
							for (int j = i + 1; j < timelines.length
									&& prevtl.getSection() != ln.getPair().getSection(); j++) {
								final TimeLine nowtl = timelines[j];
								if (nowtl.getMicroTime() >= microtime) {
									if (prevtl.getMicroTime() + prevtl.getMicroStop() > microtime) {
										dy += (nowtl.getSection() - prevtl.getSection()) * prevtl.getScroll() * rxhs;
									} else {
										dy += (nowtl.getSection() - prevtl.getSection()) * prevtl.getScroll()
												* (nowtl.getMicroTime() - microtime)
												/ (nowtl.getMicroTime() - prevtl.getMicroTime() - prevtl.getMicroStop())
												* rxhs;
									}
								}
								prevtl = nowtl;
							}
							if (dy > 0) {
								BMSIRManiacVisualEffects.apply(
										maniacLongEndTransform,
										maniacSettings,
										lane,
										lanes.length,
										model.getMode().player,
										i,
										now,
										dstx,
										(float) (dsty + dy),
										dstw,
										dsth,
										(float) hl,
										(float) hu
								);
								if (!maniacLongEndTransform.visible) continue;
								float transformedDy = maniacLongEndTransform.y - dsty;
								if (transformedDy <= 0f) continue;
								final float dscale = dsth > scale ? (dsth - scale) / 2 : 0;
								this.drawLongNote(sprite, lanes[lane].longImage, dstx, maniacLongEndTransform.y, dstw,
										(float) (dsty < (lanes[lane].region.y - dscale) ? dsty - (lanes[lane].region.y -dscale) : transformedDy), dsth, lane,
										ln);
							}
							// System.out.println(dy);
						}
					} else if (note instanceof MineNote) {
						// draw mine note
						if (tl.getMicroTime() >= microtime) {
							sprite.draw(lanes[lane].mineImage, dstx, dsty, dstw, dsth);
						}
					}
				}
				// hidden note
				if (config.isShowhiddennote() && tl.getMicroTime() >= microtime) {
					final Note hnote = tl.getHiddenNote(lane);
					if (hnote != null) {
						sprite.draw(lanes[lane].hiddenImage, lanes[lane].region.x, (float) y, lanes[lane].region.width, scale);
					}
				}
			}
		}
		// System.out.println("time :" + ltime + " y :" + yy + " real time : "
		// + (ltime * (hu - hl) / yy));
		
		//PMS見逃しPOOR描画
		// TODO dstnote2をレーン毎に変更
		if (lanes[0].dstnote2 != Integer.MIN_VALUE) {
			//遅BADからノースピの速度で落下
			final long badTime = Math.abs( main.getJudgeManager().getJudgeTable(false)[2][0] );
			double stopTime;
			double orgy2 = lanes[0].dstnote2;
			if(orgy2 < -lanes[0].region.height) orgy2 = -lanes[0].region.height;
			if(orgy2 > orgy) orgy2 = orgy;
			final double rxhs2 = (hu - hl);
			int nowPos = timelines.length - 1;
			for (int i = pos; i < timelines.length; i++) {
				final TimeLine tl = timelines[i];
				if (tl.getMicroTime() >= microtime) {
					nowPos = i;
					break;
				}
			}
			for (int i = nowPos; i >= 0 && y >= orgy2; i--) {
				final TimeLine tl = timelines[i];
				y = orgy;
				if (i + 1 < timelines.length) {
					int j;
					for (j = i; j + 1 < timelines.length && timelines[j + 1].getMicroTime() < microtime; j++) {
						if(timelines[j + 1].getMicroTime() > tl.getMicroTime() + tl.getMicroStop() + badTime) {
							stopTime = Math.max(tl.getMicroTime() + tl.getMicroStop() + badTime - timelines[j].getMicroTime() - timelines[j].getMicroStop(), 0);
							y -= (timelines[j + 1].getMicroTime() - timelines[j].getMicroTime() - timelines[j].getMicroStop() - stopTime) * rxhs2 * timelines[j].getBPM() / 240000000;
							//4分の画面上での長さ rxhs2 / 4 [pixel] 4分の時間 60 / BPM [second] 落下速度 rxhs2 * BPM / 240 [pixel/second]
						}
					}
					if(timelines[j].getMicroTime() + timelines[j].getMicroStop() < microtime) {
						if(microtime > tl.getMicroTime() + tl.getMicroStop() + badTime) {
							stopTime = Math.max(tl.getMicroTime() + tl.getMicroStop() + badTime - timelines[j].getMicroTime() - timelines[j].getMicroStop(), 0);
							y -= (microtime - timelines[j].getMicroTime() - timelines[j].getMicroStop() - stopTime) * rxhs2 * timelines[j].getBPM() / 240000000;
						}
					}
				} else {
					if(tl.getMicroTime() + tl.getMicroStop() < microtime) {
						if(microtime > tl.getMicroTime() + tl.getMicroStop() + badTime) {
							stopTime = Math.max(tl.getMicroTime() + tl.getMicroStop() + badTime - tl.getMicroTime() - tl.getMicroStop(), 0);
							y -= (microtime - tl.getMicroTime() - tl.getMicroStop() - stopTime) * rxhs2 * tl.getBPM() / 240000000;
						}
					}
				}
				// ノート描画
				for (int lane = 0; lane < lanes.length; lane++) {
					final float scale = lanes[lane].scale;
					final Note note = tl.getNote(lane);
					if (note != null) {
						if (note instanceof NormalNote) {
							// draw normal note
							//4分のタイミングでノートを拡大する
							float dstx = lanes[lane].region.x;
							float dsty = (float) y;
							float dstw = lanes[lane].region.width;
							float dsth = scale;
							if(skin.getNoteExpansionRate()[0] != 100 || skin.getNoteExpansionRate()[1] != 100) {
								if((now - main.getNowQuarterNoteTime()) < noteExpansionTime) {
									dstw *= 1 + (skin.getNoteExpansionRate()[0]/100.0f - 1) * (now - main.getNowQuarterNoteTime()) / noteExpansionTime;
									dsth *= 1 + (skin.getNoteExpansionRate()[1]/100.0f - 1) * (now - main.getNowQuarterNoteTime()) / noteExpansionTime;
									dstx -= (dstw - lanes[lane].region.width) / 2;
									dsty -= (dsth - scale) / 2;
								} else if((now - main.getNowQuarterNoteTime()) >= noteExpansionTime && (now - main.getNowQuarterNoteTime()) <= (noteExpansionTime + noteContractionTime)) {
									dstw *= 1 + (skin.getNoteExpansionRate()[0]/100.0f - 1) * (noteContractionTime - (now - main.getNowQuarterNoteTime() - noteExpansionTime)) / noteContractionTime;
									dsth *= 1 + (skin.getNoteExpansionRate()[1]/100.0f - 1) * (noteContractionTime - (now - main.getNowQuarterNoteTime() - noteExpansionTime)) / noteContractionTime;
									dstx -= (dstw - lanes[lane].region.width) / 2;
									dsty -= (dsth - scale) / 2;
								}
							}
							if ( ((note.getState() == 0 || note.getState() >= 4) && tl.getMicroTime() <= microtime) && y >= orgy2) {
								final TextureRegion s = lanes[lane].noteImage;
								if(y > orgy) sprite.draw(s, dstx, (float) (orgy - (dsth - scale) / 2), dstw, dsth);
								else sprite.draw(s, dstx, dsty, dstw, dsth);
							}
						}
					}
				}
			}
		}
	}

	private void updateStartHerePreviewMetrics() {
		if (startHerePreview == null || !startHerePreview.isValid()) {
			return;
		}
		nowbpm = startHerePreview.anchorBpm();
		currentduration = startHerePreviewDuration(
				nowbpm,
				startHerePreview.anchorScroll(),
				appliedHispeed(nowbpm, startHerePreview.anchorScroll()),
				playconfig.isEnablelanecover(),
				playconfig.getLanecover(),
				playconfig.isEnablelift(),
				playconfig.getLift()
		);
	}

	static int startHerePreviewDuration(
			double bpm,
			double scroll,
			float hispeed,
			boolean laneCoverEnabled,
			float laneCover,
			boolean liftEnabled,
			float lift
	) {
		return BMSIRHispeed.currentDuration(
				hispeed,
				bpm,
				scroll,
				laneCoverEnabled,
				laneCover,
				liftEnabled,
				lift
		);
	}

	static double effectiveLaneCover(
			boolean laneCoverEnabled,
			float laneCover,
			boolean liftEnabled,
			float lift
	) {
		return BMSIRHispeed.effectiveLaneCover(
				laneCoverEnabled,
				laneCover,
				liftEnabled,
				lift
		);
	}

	private boolean drawStartHerePreview(
			SkinObjectRenderer sprite,
			SkinLane[] lanes,
			long time,
			float offsetX,
			float offsetY,
			float offsetW,
			float offsetH
	) {
		if (
				startHerePreview == null
						|| !startHerePreview.isValid()
						|| lanes == null
						|| lanes.length != startHerePreview.laneCount()
		) {
			return false;
		}

		for (StartHerePreviewData.PreviewNote previewNote : startHerePreview.notes()) {
			SkinLane lane = lanes[previewNote.lane()];
			if (
					lane.noteImage == null
							|| !setStartHerePreviewDestination(
									startHerePreviewDestination,
									lane.region,
									lane.scale,
									offsetX,
									offsetY,
									offsetW,
									offsetH,
									playconfig.isEnablelift(),
									playconfig.getLift(),
									playconfig.isEnablelanecover(),
									playconfig.getLanecover()
							)
			) {
				return false;
			}
		}

		sprite.setBlend(0);
		sprite.setType(SkinObjectRenderer.TYPE_NORMAL);
		for (StartHerePreviewData.PreviewNote previewNote : startHerePreview.notes()) {
			SkinLane lane = lanes[previewNote.lane()];
			setStartHerePreviewDestination(
					startHerePreviewDestination,
					lane.region,
					lane.scale,
					offsetX,
					offsetY,
					offsetW,
					offsetH,
					playconfig.isEnablelift(),
					playconfig.getLift(),
					playconfig.isEnablelanecover(),
					playconfig.getLanecover()
			);
			sprite.setColor(1f, 1f, 1f, startHerePreviewAlpha(time));
			sprite.draw(
					lane.noteImage,
					startHerePreviewDestination.x,
					startHerePreviewDestination.y,
					startHerePreviewDestination.width,
					startHerePreviewDestination.height
			);
		}
		sprite.setColor(Color.WHITE);
		return true;
	}

	static float startHerePreviewAlpha(long time) {
		long cycleMillis = Math.floorMod(time, 1000L);
		float triangle = cycleMillis <= 500L
				? cycleMillis / 500f
				: (1000L - cycleMillis) / 500f;
		return 0.65f + 0.35f * triangle;
	}

	static boolean showsStartHerePreview(int state, boolean enabled) {
		return enabled
				&& (state == BMSPlayer.STATE_PRELOAD || state == BMSPlayer.STATE_READY);
	}

	/**
	 * Loading/READY marker's unmodified top edge. The note itself still receives the
	 * same note offsets as ordinary gameplay drawing.
	 */
	static float startHerePreviewTop(
			Rectangle laneRegion,
			boolean liftEnabled,
			float lift,
			boolean laneCoverEnabled,
			float laneCover
	) {
		float upper = laneRegion.y + laneRegion.height;
		float lower = liftEnabled
				? laneRegion.y + laneRegion.height * Math.max(0f, Math.min(1f, lift))
				: laneRegion.y;
		double effectiveLaneCover = effectiveLaneCover(
				laneCoverEnabled,
				laneCover,
				liftEnabled,
				lift
		);
		return laneCoverEnabled
				? (float) (upper + (lower - upper) * effectiveLaneCover)
				: upper;
	}

	static boolean setStartHerePreviewDestination(
			Rectangle destination,
			Rectangle laneRegion,
			float noteScale,
			float offsetX,
			float offsetY,
			float offsetW,
			float offsetH,
			boolean liftEnabled,
			float lift,
			boolean laneCoverEnabled,
			float laneCover
	) {
		if (destination == null || laneRegion == null) {
			return false;
		}
		float width = laneRegion.width + offsetW;
		float height = noteScale + offsetH;
		if (width <= 0f || height <= 0f) {
			return false;
		}
		float baseY = startHerePreviewTop(
				laneRegion,
				liftEnabled,
				lift,
				laneCoverEnabled,
				laneCover
		) - noteScale;
		destination.set(
				laneRegion.x + offsetX,
				baseY + offsetY - offsetH / 2f,
				width,
				height
		);
		return true;
	}

	static float noteDestinationY(float chartY, float offsetY) {
		return chartY + offsetY;
	}

	private void updatePlayCoverOffsets(SkinLane[] lanes) {
		if (lanes == null || lanes.length == 0 || lanes[0].region == null) {
			return;
		}
		float upper = lanes[0].region.y + lanes[0].region.height;
		float lower = playconfig.isEnablelift()
				? lanes[0].region.y + lanes[0].region.height * playconfig.getLift()
				: lanes[0].region.y;
		float laneCover = playconfig.isEnablelanecover() ? playconfig.getLanecover() : 0f;
		main.main.getOffset(OFFSET_LIFT).y = lower - lanes[0].region.y;
		main.main.getOffset(OFFSET_LANECOVER).y = (lower - upper) * laneCover;
	}

	public double getNowBPM() {
		return nowbpm;
	}

	public double getMinBPM() {
		return minbpm;
	}

	public double getMaxBPM() {
		return maxbpm;
	}

	public double getMainBPM() {
		return mainbpm;
	}

	final private void drawLongNote(SkinObjectRenderer sprite, TextureRegion[] longImage, float x, float y, float width, float height, float scale,
			int lane, LongNote ln) {
		if ((model.getLntype() == BMSModel.LNTYPE_HELLCHARGENOTE && ln.getType() == LongNote.TYPE_UNDEFINED)
				|| ln.getType() == LongNote.TYPE_HELLCHARGENOTE) {
			// HCN
			final JudgeManager judge = main.getJudgeManager();
			sprite.draw(
					longImage[judge.getProcessingLongNote(lane) == ln.getPair() ? 6
							: (judge.getPassingLongNote(lane) == ln && ln.getState() != 0
									? (judge.getHellChargeJudge(lane) ? 8 : 9) : 7)],
					x, y - height + scale, width, height - scale);
			sprite.draw(longImage[4], x, y, width, scale);
			sprite.draw(longImage[5], x, y - height, width, scale);
		} else if ((model.getLntype() == BMSModel.LNTYPE_CHARGENOTE && ln.getType() == LongNote.TYPE_UNDEFINED)
				|| ln.getType() == LongNote.TYPE_CHARGENOTE) {
			// CN
			sprite.draw(longImage[main.getJudgeManager().getProcessingLongNote(lane) == ln.getPair() ? 2 : 3], x,
					y - height + scale, width, height - scale);
			sprite.draw(longImage[0], x, y, width, scale);
			sprite.draw(longImage[1], x, y - height, width, scale);
		} else if ((model.getLntype() == BMSModel.LNTYPE_LONGNOTE && ln.getType() == LongNote.TYPE_UNDEFINED)
				|| ln.getType() == LongNote.TYPE_LONGNOTE) {
			// LN
			sprite.draw(longImage[main.getJudgeManager().getProcessingLongNote(lane) == ln.getPair() ? 2 : 3], x,
					y - height + scale, width, height - scale);
            if (config.isForcedCNEndings()) { sprite.draw(longImage[0], x, y, width, scale); }
			sprite.draw(longImage[1], x, y - height, width, scale);
		}
	}

	public void dispose() {
		if (font != null) {
			font.dispose();
			font = null;
		}
	}
}
