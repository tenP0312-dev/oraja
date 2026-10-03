package bms.player.beatoraja.play.bga;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.model.Layer.Sequence;
import bms.model.*;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ResourcePool;
import bms.player.beatoraja.play.BMSPlayer;
import bms.player.beatoraja.play.SkinBGA;
import bms.player.beatoraja.skin.Skin.SkinObjectRenderer;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;
import bms.player.beatoraja.system.TimingDiagnostics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * BGAのリソース管理、描画用クラス
 *
 * @author exch
 */
public class BGAProcessor {
	private static final Logger logger = LoggerFactory.getLogger(BGAProcessor.class);
	private static final int PREPARE_DISPOSALS_PER_FRAME = 4;
	private static final int PREPARE_UPLOADS_PER_FRAME = 1;
	
	// TODO イベントレイヤー対応(現状はミスレイヤーのみ)

	private PlayerConfig player;
	private volatile float progress = 0;

	private MovieProcessor[] movies = new MovieProcessor[0]; 
	
	private final ResourcePool<String, MovieProcessor> mpgresource;
	private final java.util.concurrent.ConcurrentHashMap<String, SongResource> movieResources =
			new java.util.concurrent.ConcurrentHashMap<>();

	public static final String[] mov_extension = { "mp4", "wmv", "m4v", "webm", "mpg", "mpeg", "m1v", "m2v", "avi"};

	/**
	 * 再生中のBGAID
	 */
	private int playingbgaid = -1;
	/**
	 * 再生中のレイヤーID
	 */
	private int playinglayerid = -1;
	/**
	 * ミスレイヤー表示開始時間
	 */
	private long misslayertime;

	private long getMisslayerduration;
	/**
	 * 現在のミスレイヤーシーケンス
	 */
	private Layer misslayer = null;

	private long time;

	private BGImageProcessor cache;

	private Texture blanktex;

	private TimeLine[] timelines = {};
	private int pos;
	private TextureRegion image;
	private Rectangle tmpRect = new Rectangle();
	
	private boolean rbga;
	private boolean rlayer;
	private boolean preparationStarted;
	private long preparationStartedNanos;
    private final BgaPlaybackCoordinator async;
    private final boolean videoEnabled;
    private BgaTimelineSchedule schedule = new BgaTimelineSchedule(new TimeLine[0], new MovieProcessor[0]);
    private long mainStartMs, layerStartMs, moviePreparationStarted;
    private BgaTimelineSchedule.Event firstMovie, firstLayer;

	public BGAProcessor(Config config, PlayerConfig player) {
		this.player = player;
        videoEnabled = config.getBga() != Config.BGA_OFF && config.getBgaQualityMode() != BgaQualityProfile.Mode.OFF;
        async = config.isBgaAsyncPipeline() ? new BgaPlaybackCoordinator(config) : null;

		Pixmap blank = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		blank.setColor(Color.BLACK);
		blank.fill();
		blanktex = new Texture(blank);
		blank.dispose();

		mpgresource = new ResourcePool<String, MovieProcessor>(Math.max(config.getSongResourceGen(), 1)) {
			@Override
				protected MovieProcessor load(String key) {
					FFmpegProcessor mm = new FFmpegProcessor(config.getFrameskip());
					SongResource resource = movieResources.get(key);
					if (resource != null) {
						mm.create(resource);
					} else {
						mm.create(key);
					}
				return mm;
			}

			@Override
			protected void dispose(MovieProcessor resource) {
				resource.dispose();
			}
		};
		cache = new BGImageProcessor(256, Math.max(config.getSongResourceGen(), 1));
		image = new TextureRegion();
	}

	public synchronized void setModel(BMSModel model) {
		setModel(model, model != null ? SongResources.fromPath(Path.of(model.getPath())) : null);
	}

	public synchronized void setModel(BMSModel model, SongResource chartResource) {
		progress = 0;

        if (async != null) {
            async.reset();
            MovieProcessor[] old = movies;
            Gdx.app.postRunnable(() -> { for (MovieProcessor movie : old) if (movie != null) movie.dispose(); });
        }

		cache.clear();
		resetCurrentlyPlayingBGA();

		int id = 0;

		Array<TimeLine> tls = new Array<TimeLine>();

		if(model != null) {
			for(TimeLine tl : model.getAllTimeLines()) {
				if(tl.getBGA() != -1 || tl.getLayer() != -1 || tl.getEventlayer().length > 0) {
					tls.add(tl);
				}
			}

			// BMS格納ディレクトリ
			SongResource directory = chartResource.parent();

			movies = new MovieProcessor[model.getBgaList().length];
			for (String name : model.getBgaList()) {
				if (progress == 1) {
					break;
				}
				SongResource f = null;
				try {
					if (directory.resolve(name).exists()) {
						final int index = name.lastIndexOf('.');
						String fex = null;
						if (index != -1) {
							fex = name.substring(index + 1).toLowerCase();
						}
						if (fex != null) {
							if (Arrays.asList(mov_extension).contains(fex)){
								name = name.substring(0, index);
								for (String mov : mov_extension) {
									final SongResource mpgfile = directory.resolve(name + "." + mov);
									if (mpgfile.exists()) {
										f = mpgfile;
										break;
									}
								}
							}else if (Arrays.asList(BGImageProcessor.pic_extension).contains(fex)){
								name = name.substring(0, index);
								for (String pic : BGImageProcessor.pic_extension) {
									final SongResource picfile = directory.resolve(name + "." + pic);
									if (picfile.exists()) {
										f = picfile;
										break;
									}
								}
							}else{
								f = directory.resolve(name);
							}
						}
					}
					if (f == null) {
						final int index = name.lastIndexOf('.');
						if (index != -1) {
							name = name.substring(0, index);
						}
						for (String mov : mov_extension) {
							final SongResource mpgfile = directory.resolve(name + "." + mov);
							if (mpgfile.exists()) {
								f = mpgfile;
								break;
							}
						}
						if (f == null) {
							for (String mov : BGImageProcessor.pic_extension) {
								final SongResource picfile = directory.resolve(name + "." + mov);
								if (picfile.exists()) {
									f = picfile;
									break;
								}
							}
						}
					}
				} catch (IOException | IllegalArgumentException e) {
					logger.warn(e.getMessage());
				}

				if (f != null) {
					boolean isMovie = false;
					for (String mov : mov_extension) {
						if (f.name().toLowerCase().endsWith(mov)) {
                            isMovie = true;
                            if (!videoEnabled) break;
							try {
								movieResources.put(f.cacheKey(), f);
								MovieProcessor mm;
								try {
                                    mm = async != null ? async.movie(f) : mpgresource.get(f.cacheKey());
								} finally {
									movieResources.remove(f.cacheKey(), f);
								}
								movies[id] = mm;
								isMovie = true;
								break;
							} catch (Throwable e) {
								logger.warn("BGAファイル読み込み失敗。{}", e.getMessage());
								e.printStackTrace();
							}
						}
					}
					if(isMovie) {
					} else {
						cache.put(id, f);
					}
				}

				progress += 1f / model.getBgaList().length;
				id++;
			}
		} else {
            movies = new MovieProcessor[0];
        }
		timelines = tls.toArray(TimeLine.class);
        schedule = new BgaTimelineSchedule(timelines, movies);

		disposeOld();

		logger.info("BGAファイル読み込み完了。BGA数:{}", id);
		progress = 1;
	}

	/**
	 * Prepares only the timeline shape needed by an in-memory skin preview. The
	 * declared BGA intentionally resolves to the existing black fallback texture,
	 * so no file, decoder, or background loader is touched.
	 */
	public synchronized void setSkinPreviewModel(BMSModel model) {
		progress = 0;
        if (async != null) {
            async.reset();
            MovieProcessor[] old = movies;
            Gdx.app.postRunnable(() -> { for (MovieProcessor movie : old) if (movie != null) movie.dispose(); });
        }
		cache.clear();
		resetCurrentlyPlayingBGA();

		Array<TimeLine> tls = new Array<TimeLine>();
		if (model != null) {
			for (TimeLine tl : model.getAllTimeLines()) {
				if (tl.getBGA() != -1 || tl.getLayer() != -1 || tl.getEventlayer().length > 0) {
					tls.add(tl);
				}
			}
			movies = new MovieProcessor[model.getBgaList().length];
		} else {
			movies = new MovieProcessor[0];
		}
		timelines = tls.toArray(TimeLine.class);
		pos = 0;
		time = -1;
		disposeOld();
		progress = 1;
	}

	public void abort() {
		progress = 1;
	}

	public void disposeOld() {
		cache.disposeOld();
		Gdx.app.postRunnable(() -> mpgresource.disposeOld());
	}
	/**
	 * BGAの初期データをあらかじめキャッシュする
	 */
	public void beginPrepare(BMSPlayer player) {
        if (async != null) {
            async.reset();
            async.beginTick(-1, Gdx.graphics.getFrameId());
            for (var object : player.getSkin().getAllSkinObjects()) {
                if (object instanceof SkinBGA) {
                    Rectangle destination = object.getDestination(0, player);
                    if (destination != null) async.viewport(Math.max(1, (int) destination.width), Math.max(1, (int) destination.height));
                }
            }
            firstMovie = schedule.first(false);
            firstLayer = schedule.first(true);
            moviePreparationStarted = System.nanoTime();
        }
		pos = 0;
		if(cache != null) {
			cache.beginPrepare(timelines);
		}
		for (MovieProcessor mp : movies) {
			if(mp != null) {
				mp.stop();				
			}
		}
		resetCurrentlyPlayingBGA();
		time = -1;
		preparationStarted = true;
		preparationStartedNanos = TimingDiagnostics.start();
	}

	public boolean advancePreparation() {
		if (!preparationStarted) {
			return false;
		}
		long stepStarted = TimingDiagnostics.start();
		boolean complete;
		try {
			complete = cache == null || cache.advancePreparation(
					PREPARE_DISPOSALS_PER_FRAME,
					PREPARE_UPLOADS_PER_FRAME
			);
            if (async != null) {
                long metricsStart = async.metrics.start();
                async.beginTick(-1, Gdx.graphics.getFrameId());
                boolean movieReady = preloadFirst(firstMovie, 1);
                preloadFirst(firstLayer, 2);
                async.endTick();
                async.metrics.finish(BgaPerformanceMetrics.Metric.PREPARE, metricsStart);
                complete &= movieReady || System.nanoTime() - moviePreparationStarted >= 500_000_000L;
            }
		} finally {
			TimingDiagnostics.finish(TimingDiagnostics.Metric.BGA_PREPARE_STEP, stepStarted);
		}
		if (complete) {
			TimingDiagnostics.finish(
					TimingDiagnostics.Metric.BGA_PREPARE_TOTAL,
					preparationStartedNanos
			);
			preparationStartedNanos = 0;
			preparationStarted = false;
		}
		return complete;
	}

	private void resetCurrentlyPlayingBGA() {
		playingbgaid = -1;
		playinglayerid = -1;
		misslayertime = 0;
		misslayer = null;
	}

	private Texture getBGAData(long time, int id, boolean cont, int role) {
		if (progress != 1 || id < 0 || id >= movies.length) {
			return null;
		}

		if(movies[id] != null) {
            if (async != null && movies[id] instanceof BgaPlaybackCoordinator.Movie movie) {
                movie = movie.variant(role);
                long start = role == 2 ? misslayertime : role == 0 ? mainStartMs : layerStartMs;
                int priority = role == 1 ? 2 : 1;
                if (!async.request(movie, time, start, priority, false)) return null;
                return movie.getFrame(time);
            }
			if (!cont) {
				movies[id].play(time, false);
			}
			return movies[id].getFrame(time);
		}
		return cache != null ? cache.getTexture(id) : null;
	}
	
	public void prepareBGA(long time) {
		if (time < 0 || timelines == null) {
			this.time = -1;
			return;
		}
        if (time < this.time) {
            pos = 0;
            resetCurrentlyPlayingBGA();
            this.time = -1;
            if (async != null) async.reset();
        }
		for (int i = pos; i < timelines.length; i++) {
			final TimeLine tl = timelines[i];
			if (tl.getTime() > time) {
				break;
			}

			if (tl.getTime() > this.time) {
				final int bga = tl.getBGA();
				if (bga == -2) {
					playingbgaid = -1;
					rbga = false;
				} else if (bga >= 0) {
					playingbgaid = bga;
                    mainStartMs = tl.getTime();
					rbga = false;
				}
				
				final int layer = tl.getLayer();
				if (layer == -2) {
					playinglayerid = -1;
					rlayer = false;
				} else if (layer >= 0) {
					playinglayerid = layer;
                    layerStartMs = tl.getTime();
					rlayer = false;
				}

				final Layer[] eventlayer = tl.getEventlayer();
				
				for(Layer poor : eventlayer) {
					if (poor.event.type == Layer.EventType.MISS) {
						misslayer = poor;
					}					
				}
			} else {
				pos++;
			}
		}
		
		this.time = time;
	}


	public void drawBGA(SkinBGA dst, SkinObjectRenderer sprite, Rectangle r) {
        long started = async != null ? async.metrics.start() : 0;
        if (async != null && time >= 0) {
            async.viewport(Math.max(1, (int) r.width), Math.max(1, (int) r.height));
            async.beginTick(time, Gdx.graphics.getFrameId());
        }
        try {
		sprite.setColor(dst.getColor());
		sprite.setBlend(dst.getBlend());
		if (time < 0 || timelines == null) {
			sprite.draw(blanktex, r.x, r.y, r.width, r.height);
			return;
		}

		if (misslayer != null && misslayertime != 0 && time >= misslayertime && time < misslayertime + getMisslayerduration) {
			// draw miss layer
			final Sequence[] seq = misslayer.sequence[0];
			final int index = seq[(int) ((seq.length - 1) * (time - misslayertime) / getMisslayerduration)].id;
			if(index != Integer.MIN_VALUE) {
				Texture miss = getBGAData(time, index, true, 2);
				if (miss != null) {
					sprite.setType(SkinObjectRenderer.TYPE_LINEAR);
					drawBGAFixRatio(dst, sprite, r, miss);
				}				
			}
		} else {
			// draw BGA
			final Texture playingbgatex = getBGAData(time, playingbgaid, rbga, 0);
			rbga = true;
			if (playingbgatex != null) {
				if (movies[playingbgaid] != null) {
					sprite.setType(async != null ? SkinObjectRenderer.TYPE_LINEAR : SkinObjectRenderer.TYPE_FFMPEG);
					drawBGAFixRatio(dst, sprite, r, playingbgatex);
				} else {
					sprite.setType(SkinObjectRenderer.TYPE_LINEAR);
					drawBGAFixRatio(dst, sprite, r, playingbgatex);
				}
			} else {
				sprite.draw(blanktex, r.x, r.y, r.width, r.height);
			}
			// draw layer
			final Texture playinglayertex = getBGAData(time, playinglayerid, rlayer, 1);
			rlayer = true;
			if (playinglayertex != null) {
				if (movies[playinglayerid] != null) {
					sprite.setType(async != null ? SkinObjectRenderer.TYPE_LINEAR : SkinObjectRenderer.TYPE_FFMPEG);
					drawBGAFixRatio(dst, sprite, r, playinglayertex);
				} else {
					sprite.setType(SkinObjectRenderer.TYPE_LAYER);
					drawBGAFixRatio(dst, sprite, r, playinglayertex);
				}
			}
		}
        } finally {
            if (async != null && time >= 0) {
                if (time >= 0) preloadUpcoming();
                async.endTick();
                async.metrics.finish(BgaPerformanceMetrics.Metric.DRAW, started);
            }
        }
    }

    private boolean preloadFirst(BgaTimelineSchedule.Event event, int priority) {
        if (event == null || async.profile.fps() == 0) return true;
        var movie = ((BgaPlaybackCoordinator.Movie) movies[event.id()]).variant(event.layer() ? 1 : 0);
        if (!async.request(movie, event.timeMs(), event.timeMs(), priority, true)) return true;
        return movie.failed || movie.getFrame(event.timeMs()) != null;
    }

    private void preloadUpcoming() {
        int count = 0;
        for (int pass = 0; pass < 2; pass++) {
            boolean layer = pass == 1;
            if (count >= Math.min(2, async.profile.preloadCount())) break;
            var event = schedule.nextDistinctMovieAfter(time, layer ? playinglayerid : playingbgaid, layer);
            if (event != null && event.timeMs() - time <= async.profile.preloadWindowMs()) {
                var movie = ((BgaPlaybackCoordinator.Movie) movies[event.id()]).variant(layer ? 1 : 0);
                if (async.request(movie, event.timeMs(), event.timeMs(), layer ? 4 : 3, true)) count++;
            }
        }
        if (count < Math.min(2, async.profile.preloadCount()) && misslayer != null
                && misslayer.sequence.length > 0) {
            for (Sequence sequence : misslayer.sequence[0]) {
                if (sequence.id >= 0 && sequence.id < movies.length
                        && movies[sequence.id] instanceof BgaPlaybackCoordinator.Movie movie) {
                    async.request(movie.variant(2), 0, 0, 5, true);
                    break;
                }
            }
        }
    }
	
	/**
	 * Modify the aspect ratio and draw BGA
	 */
	private void drawBGAFixRatio(SkinBGA dst, SkinObjectRenderer sprite, Rectangle r, Texture bga){
		tmpRect.set(r);
		image.setTexture(bga);
		image.setRegion(0, 0, bga.getWidth(), bga.getHeight());
		dst.getStretch().stretchRect(tmpRect, image, image);
		sprite.draw(image, tmpRect.x, tmpRect.y, tmpRect.width, tmpRect.height);
	}

	/**
	 * ミスレイヤー開始時間を設定する
	 *
	 * @param time
	 *            ミスレイヤー開始時間(ms)
	 */
	public void setMisslayerTme(long time) {
		misslayertime = time;
		getMisslayerduration = player.getMisslayerDuration();
	}

	public void stop() {
        if (async != null) async.reset();
		for (MovieProcessor mpg : movies) {
			if (mpg != null) {
				mpg.stop();
			}
		}
	}

	/**
	 * リソースを開放する
	 */
	public void dispose() {
        if (async != null) {
            async.dispose();
            for (MovieProcessor movie : movies) if (movie != null) movie.dispose();
        }
		if (cache != null) {
			cache.dispose();
		}
		mpgresource.dispose();
	}

	public float getProgress() {
		return progress;
	}
}
