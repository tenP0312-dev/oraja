package bms.player.beatoraja;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;

/**
 * Pixmapリソースプール
 * 
 * @author exch
 */
public class PixmapResourcePool extends ResourcePool<String, Pixmap> {
	private static Logger logger = LoggerFactory.getLogger(PixmapResourcePool.class);
	private final ConcurrentHashMap<String, SongResource> songResources = new ConcurrentHashMap<>();

	public PixmapResourcePool() {
		super(1);
	}
	
	public PixmapResourcePool(int maxgen) {
		super(maxgen);
	}
	
	@Override
	protected Pixmap load(String path) {
		SongResource resource = songResources.get(path);
		final Pixmap pixmap = resource != null ? loadPicture(resource) : loadPicture(path);
		return pixmap != null ? convert(pixmap) : null;
	}

	public Pixmap get(SongResource resource) {
		String key = resource.cacheKey();
		songResources.put(key, resource);
		try {
			return get(key);
		} finally {
			songResources.remove(key, resource);
		}
	}

	/**
	 * Pixmapをload時に変換する。
	 *
	 * @param pixmap
	 * @return
	 */
	protected Pixmap convert(Pixmap pixmap) {
		return pixmap;
	}

	@Override
	protected void dispose(Pixmap resource) {
		resource.dispose();
	}

	/**
	 * 指定のパスで表現されるファイルを読み込む
	 * @param path イメージファイルのパス
	 * @return イメージ。読めなかった場合またはpathがファイルでない場合はnullを返す
	 */
	public static Pixmap loadPicture(String path) {
		Pixmap tex = null;
		File f = new File(path);
		if(!f.isFile()) {
			return tex;
		}

		final boolean jpeg = isJpeg(path);
		// Some valid JPEGs can trigger a native jpgd assertion before Java can catch it.
		// Route JPEG through ImageIO so a bad image cannot terminate the whole client.
		if (!jpeg) {
			try {
				if(path.endsWith(".cim")) {
					tex = PixmapIO.readCIM(Gdx.files.internal(path));
				} else {
					tex = new Pixmap(Gdx.files.internal(path));
				}
			} catch (Throwable e) {
				logger.warn("BGAファイル読み込み失敗。{}", e.getMessage());
			}
		}
		if (tex == null) {
			if (!jpeg) {
				logger.warn("BGAファイル読み込み再試行:{}", path);
			}
			try {
				// TODO 一部のbmsはImageIO.readで失敗する(e.g. past glow)。別の画像デコーダーが必要
				BufferedImage bi = ImageIO.read(f);
				if (bi == null) {
					return null;
				}
//						System.out.println("width : " + bi.getWidth() + " height : " + bi.getHeight() + " type : " + bi.getType());
				tex = new Pixmap(bi.getWidth(), bi.getHeight(), Pixmap.Format.RGBA8888);
				for(int x = 0;x < bi.getWidth();x++) {
					for(int y = 0;y < bi.getHeight();y++) {
						tex.drawPixel(x, y, (bi.getRGB(x, y) << 8 | 0x000000ff));
					}
				}
			} catch (Throwable e) {
				logger.warn("BGAファイル読み込み失敗。{}", e.getMessage());
				e.printStackTrace();
			}
		}

		return tex;
	}

	public static Pixmap loadPicture(SongResource resource) {
		if (resource.localPath().isPresent()) {
			return loadPicture(resource.localPath().get().toString());
		}
		try {
			if (!resource.exists() || resource.isDirectory()) {
				return null;
			}
			String lowerName = resource.name().toLowerCase(Locale.ROOT);
			if (lowerName.endsWith(".cim") || lowerName.endsWith(".tga")) {
				return loadPicture(resource.materialize().toString());
			}
			try (InputStream input = resource.openStream()) {
				BufferedImage image = ImageIO.read(input);
				if (image == null) {
					return null;
				}
				Pixmap pixmap = new Pixmap(image.getWidth(), image.getHeight(), Pixmap.Format.RGBA8888);
				for (int x = 0; x < image.getWidth(); x++) {
					for (int y = 0; y < image.getHeight(); y++) {
						pixmap.drawPixel(x, y, image.getRGB(x, y) << 8 | 0x000000ff);
					}
				}
				return pixmap;
			}
		} catch (Throwable e) {
			logger.warn("BGAファイル読み込み失敗。{} ({})", resource.displayPath(), e.getMessage());
			return null;
		}
	}

	static boolean isJpeg(String path) {
		final String lower = path.toLowerCase(java.util.Locale.ROOT);
		return lower.endsWith(".jpg") || lower.endsWith(".jpeg");
	}
}
