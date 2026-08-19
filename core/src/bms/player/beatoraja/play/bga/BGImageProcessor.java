package bms.player.beatoraja.play.bga;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.model.TimeLine;
import bms.player.beatoraja.PixmapResourcePool;
import bms.player.beatoraja.song.SongResource;

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
	private Texture[] bgacache;
	/**
	 * キャッシュされているBGイメージID
	 */
	private int[] bgacacheid;

	private final PixmapResourcePool cache;
	private IncrementalPreparation<Texture> preparation;

	public BGImageProcessor(int size, int maxgen) {
		bgacache = new Texture[size];
		bgacacheid = new int[size];
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
		Arrays.fill(bgacacheid, -1);
		for (Texture bga : bgacache) {
			if (bga != null) {
				disposals.addLast(bga);
			}
		}
		Arrays.fill(bgacache, null);

		boolean[] scheduledSlots = new boolean[bgacache.length];
		ArrayList<Integer> uploads = new ArrayList<>();
		for (TimeLine tl : timelines) {
			int bga = tl.getBGA();
			if (shouldPrepare(bga, scheduledSlots)) {
				uploads.add(bga);
			}

			bga = tl.getLayer();
			if (shouldPrepare(bga, scheduledSlots)) {
				uploads.add(bga);
			}
		}
		preparation = new IncrementalPreparation<>(
				disposals,
				uploads.stream().mapToInt(Integer::intValue).toArray()
		);
		logger.info("BGA incremental texture preparation queued - textures:{} disposals:{}",
				uploads.size(), disposals.size());
	}

	public boolean advancePreparation(int disposalBudget, int uploadBudget) {
		if (preparation == null) {
			return true;
		}
		preparation.advance(
				disposalBudget,
				uploadBudget,
				Texture::dispose,
				this::getTexture
		);
		if (preparation.isComplete()) {
			preparation = null;
			return true;
		}
		return false;
	}

	private boolean shouldPrepare(int id, boolean[] scheduledSlots) {
		if (id < 0 || id >= bgamap.length || bgamap[id] == null) {
			return false;
		}
		int slot = id % bgacache.length;
		if (scheduledSlots[slot]) {
			return false;
		}
		scheduledSlots[slot] = true;
		return true;
	}

	public Texture getTexture(int id) {
		final int cid = id % bgacache.length;
		// BGイメージキャッシュにTextureがある場合
		if (bgacacheid[cid] == id) {
			return bgacache[cid];
		}
		// BGイメージキャッシュにTextureがない場合
		if (id < bgamap.length && bgamap[id] != null){
			if(bgacache[cid] == null) {
				bgacache[cid] = new Texture(bgamap[id]);				
			} else if(bgacache[cid].getWidth() != bgamap[id].getWidth() || bgacache[cid].getHeight() != bgamap[id].getHeight()){
				bgacache[cid].dispose();
				bgacache[cid] = new Texture(bgamap[id]);				
			} else {
				bgacache[cid].draw(bgamap[id], 0, 0);
			}
			bgacacheid[cid] = id;
			return bgacache[cid];
		}
		return null;
	}

	/**
	 * リソースを開放する
	 */
	public void dispose() {
		if (preparation != null) {
			preparation.disposeRemaining(Texture::dispose);
			preparation = null;
		}
		for (Texture bga : bgacache) {
			if (bga != null) {
				bga.dispose();
			}
		}
		bgacache = new Texture[0];

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
