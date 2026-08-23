package bms.player.beatoraja.play.bga;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.model.TimeLine;
import bms.player.beatoraja.PixmapResourcePool;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.system.TimingDiagnostics;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/**
 * BGIリソース管理用クラス
 *
 * @author exch
 */
public class BGImageProcessor {
	private static final Logger logger = LoggerFactory.getLogger(BGImageProcessor.class);
	
	public static final String[] pic_extension = { "jpg", "jpeg", "gif", "bmp", "png", "tga" };
	/**
	 * BGイメージ
	 */
	private Pixmap[] bgamap = new Pixmap[1000];
	/**
	 * BGイメージのキャッシュ
	 */
	private final Map<Integer, Texture> bgacache;
	private final int bgacacheCapacity;

	private final PixmapResourcePool cache;
	private IncrementalPreparation<Texture> preparation;

	public BGImageProcessor(int size, int maxgen) {
		bgacacheCapacity = Math.max(size, 1);
		bgacache = new LinkedHashMap<>(bgacacheCapacity + 1, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Integer, Texture> eldest) {
				if (size() <= bgacacheCapacity) {
					return false;
				}
				Texture texture = eldest.getValue();
				if (texture != null) {
					texture.dispose();
				}
				return true;
			}
		};
		cache = new PixmapResourcePool(maxgen) {

			protected Pixmap convert(Pixmap pixmap) {
				int bgasize = Math.max(pixmap.getHeight(), pixmap.getWidth());
				if ( bgasize <=256 ){
					final int fixx = (256 - pixmap.getWidth()) / 2;
					Pixmap fixpixmap = new Pixmap(256, 256, pixmap.getFormat());
					fixpixmap.drawPixmap(pixmap, 0, 0, pixmap.getWidth(), pixmap.getHeight(),
							fixx, 0, pixmap.getWidth(), pixmap.getHeight());
					pixmap.dispose();
					return fixpixmap;
				}
				return pixmap;
			}
		};
	}

	public void put(int id, Path path) {
		Pixmap pixmap = cache.get(path.toString());
		put(id, pixmap);
	}

	public void put(int id, SongResource resource) {
		Pixmap pixmap = cache.get(resource);
		put(id, pixmap);
	}

	private void put(int id, Pixmap pixmap) {
		if(id >= bgamap.length) {
			bgamap = Arrays.copyOf(bgamap, id + 1);
		}
		bgamap[id] = pixmap;
	}
	
	public void clear() {
		Arrays.fill(bgamap,  null);
	}
	
	public void disposeOld() {
		cache.disposeOld();
	}

	/**
	 * BGAの初期データをあらかじめキャッシュする
	 */
	public void beginPrepare(TimeLine[] timelines) {
		ArrayDeque<Texture> disposals = new ArrayDeque<>();
		if (preparation != null) {
			preparation.drainDisposalsTo(disposals);
		}
		for (Texture bga : bgacache.values()) {
			if (bga != null) {
				disposals.addLast(bga);
			}
		}
		bgacache.clear();

		int[] imageIds = new int[timelines.length * 2];
		int imageIndex = 0;
		for (TimeLine tl : timelines) {
			imageIds[imageIndex++] = tl.getBGA();
			imageIds[imageIndex++] = tl.getLayer();
		}
		CachePlan cachePlan = calculateCachePlan(bgacacheCapacity, imageIds, this::hasImage);
		preparation = new IncrementalPreparation<>(
				disposals,
				cachePlan.uploads()
		);
		TimingDiagnostics.staticBgaCachePlan(
				cachePlan.uniqueImages(),
				bgacacheCapacity,
				cachePlan.uploads().length,
				cachePlan.deferredImages()
		);
		logger.info("BGA incremental texture preparation queued - textures:{} disposals:{}",
				cachePlan.uploads().length, disposals.size());
	}

	public boolean advancePreparation(int disposalBudget, int uploadBudget) {
		if (preparation == null) {
			return true;
		}
		preparation.advance(
				disposalBudget,
				uploadBudget,
				Texture::dispose,
				id -> getTexture(id, true)
		);
		if (preparation.isComplete()) {
			preparation = null;
			return true;
		}
		return false;
	}

	private boolean hasImage(int id) {
		return id >= 0 && id < bgamap.length && bgamap[id] != null;
	}

	public Texture getTexture(int id) {
		return getTexture(id, false);
	}

	private Texture getTexture(int id, boolean preparationUpload) {
		Texture cachedTexture = bgacache.get(id);
		if (cachedTexture != null) {
			return cachedTexture;
		}
		// BGイメージキャッシュにTextureがない場合
		if (id >= 0 && id < bgamap.length && bgamap[id] != null){
			long startedNanos = preparationUpload ? 0 : TimingDiagnostics.start();
			if (!preparationUpload) {
				TimingDiagnostics.increment(TimingDiagnostics.Counter.BGA_STATIC_CACHE_MISS);
			}
			Texture texture;
			try {
				if (!preparationUpload) {
					TimingDiagnostics.increment(TimingDiagnostics.Counter.BGA_STATIC_TEXTURE_CREATE);
				}
				// Each image owns an independent Texture. Repainting a direct-mapped
				// slot can leave an earlier draw using pixels from another BGA.
				texture = new Texture(bgamap[id]);
				bgacache.put(id, texture);
			} finally {
				if (!preparationUpload) {
					TimingDiagnostics.finish(TimingDiagnostics.Metric.BGA_STATIC_RUNTIME_UPLOAD, startedNanos);
				}
			}
			return texture;
		}
		return null;
	}

	static CachePlan calculateCachePlan(int cacheSize, int[] imageIds, IntPredicate hasImage) {
		if (cacheSize <= 0 || imageIds.length == 0) {
			return new CachePlan(new int[0], 0, 0);
		}
		BitSet referencedImages = new BitSet();
		int[] uploads = new int[Math.min(cacheSize, imageIds.length)];
		int uploadCount = 0;
		int uniqueImages = 0;
		for (int id : imageIds) {
			if (id < 0 || !hasImage.test(id) || referencedImages.get(id)) {
				continue;
			}
			referencedImages.set(id);
			uniqueImages++;
			if (uploadCount < cacheSize) {
				uploads[uploadCount++] = id;
			}
		}
		return new CachePlan(
				Arrays.copyOf(uploads, uploadCount),
				uniqueImages,
				uniqueImages - uploadCount
		);
	}

	record CachePlan(int[] uploads, int uniqueImages, int deferredImages) {
	}

	/**
	 * リソースを開放する
	 */
	public void dispose() {
		if (preparation != null) {
			preparation.disposeRemaining(Texture::dispose);
			preparation = null;
		}
		for (Texture bga : bgacache.values()) {
			if (bga != null) {
				bga.dispose();
			}
		}
		bgacache.clear();

		cache.dispose();
	}

	static final class IncrementalPreparation<T> {
		private final ArrayDeque<T> disposals;
		private final int[] uploads;
		private int uploadIndex;

		IncrementalPreparation(ArrayDeque<T> disposals, int[] uploads) {
			this.disposals = disposals;
			this.uploads = uploads;
		}

		void advance(
				int disposalBudget,
				int uploadBudget,
				Consumer<T> disposer,
				IntConsumer uploader) {
			for (int count = 0; count < Math.max(disposalBudget, 0) && !disposals.isEmpty(); count++) {
				disposer.accept(disposals.removeFirst());
			}
			for (int count = 0; count < Math.max(uploadBudget, 0) && uploadIndex < uploads.length; count++) {
				uploader.accept(uploads[uploadIndex++]);
			}
		}

		boolean isComplete() {
			return disposals.isEmpty() && uploadIndex >= uploads.length;
		}

		void drainDisposalsTo(ArrayDeque<T> target) {
			target.addAll(disposals);
			disposals.clear();
		}

		void disposeRemaining(Consumer<T> disposer) {
			while (!disposals.isEmpty()) {
				disposer.accept(disposals.removeFirst());
			}
		}
	}	
}
