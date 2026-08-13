package bms.player.beatoraja.audio;

import bms.model.BMSModel;
import bms.model.Note;
import bms.player.beatoraja.song.SongResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.Disposable;

/**
 * 各種音源再生インターフェイス
 * 
 * @author exch
 */
public interface AudioDriver extends Disposable {

	/**
	 * 指定したパスの音源を鳴らす
	 * 
	 * @param path
	 *            音源のファイルパス
	 * @param loop
	 *            ループ再生するかどうか
	 */
	public void play(String path, float volume, boolean loop);

	default void play(SongResource resource, float volume, boolean loop) {
		try {
			play(resource.materialize().toString(), volume, loop);
		} catch (java.io.IOException ignored) {
		}
	}
	
	/**
	 * 指定したパスの音源のボリュームを設定する
	 * @param path
	 *            音源のファイルパス
	 * @param volume
	 *            ボリューム
	 */
	public void setVolume(String path, float volume);

	default void setVolume(SongResource resource, float volume) {
		setVolume(resource.cacheKey(), volume);
	}
	
	/**
	 * 指定したパスの音源がなっている場合はtrueを返す
	 * 
	 * @param path
	 *            音源のファイルパス
	 */
	public boolean isPlaying(String path);

	default boolean isPlaying(SongResource resource) {
		return isPlaying(resource.cacheKey());
	}
	
	/**
	 * 指定したパスの音源がなっている場合は止める
	 * 
	 * @param path
	 *            音源のファイルパス
	 */
	public void stop(String path);

	default void stop(SongResource resource) {
		stop(resource.cacheKey());
	}

	/**
	 * 指定したパスの音源を開放する
	 * 
	 * @param path
	 *            音源のファイルパス
	 */
	public void dispose(String path);

	default void dispose(SongResource resource) {
		dispose(resource.cacheKey());
	}

	/**
	 * BMSの音源データを読み込む
	 * 
	 * @param model
	 *            BMSモデル
	 */
	public void setModel(BMSModel model);

	default void setModel(BMSModel model, SongResource resource) {
		setModel(model);
	}
	
	/**
	 * 判定に対応した追加キー音を定義する
	 * @param judge 判定
	 * @param fast EARLYの場合はtrue
	 * @param path 音源パス。nullの場合は定義しない
	 */
	public void setAdditionalKeySound(int judge, boolean fast, String path);

	/**
	 * BMSの音源データ読み込みを中止する
	 */
	public void abort();

	/**
	 * 音源の読み込み状況を返す
	 * 
	 * @return 音源の読み込み状況(0.0 - 1.0)
	 */
	public float getProgress();

	/**
	 * 指定したNoteの音を鳴らす
	 * 
	 * @param n
	 *            Note
	 * @param volume
	 *            ボリューム(0.0 - 1.0)
	 * @param pitch
	 *            ピッチ変化(-12 - 12)
	 */
	public void play(Note n, float volume, int pitch);

	public void play(int judge, boolean fast);
	/**
	 * 指定したNoteの音を止める。nullの場合は再生されている音を全て止める
	 * 
	 * @param n
	 *            Note
	 */
	public void stop(Note n);
	
	/**
	 * 指定したパスの音源のボリュームを設定する
	 * @param n
	 *            Note
	 * @param volume
	 *            ボリューム
	 */
	public void setVolume(Note n, float volume);

	/**
	 * 全体のピッチを変更する。可能な場合は再生中の音のピッチも変更する
	 *
	 * @param pitch ピッチ(0.5 - 2.0)
	 */
	public void setGlobalPitch(float pitch);

	/**
	 * 全体のピッチを取得する
	 * @return ピッチ(0.5 - 2.0)
	 */
	public float getGlobalPitch();
	
	/**
	 * 古い音源リソースを開放する
	 */
	public void disposeOld();
	
	/**
	 * 指定されたパスから対応している音源ファイルのパスを全て取得する
	 * @param path 指定されたパス
	 * @return 音源ファイルのパス
	 */
	public static Path[] getPaths(String path) {
		final String[] exts = { ".wav", ".flac", ".ogg", ".mp3"};

		List<Path> result = new ArrayList<Path>();
		final int index = path.lastIndexOf('.');
		final String name = path.substring(0, index < 0 ? path.length() : index);
		final String ext = index < 0 ? "" : path.substring(index, path.length());
		
		Path p = Paths.get(path);
		if (Files.exists(p)) {
			result.add(p);
		}

		for (String _ext : exts) {
			if (!_ext.equals(ext)) {
				final Path p2 = p.resolve(name + _ext);
				if(Files.exists(p2)) {
					result.add(p2);					
				}
			}
		}
		
		return result.toArray(new Path[result.size()]);
	}

	public static SongResource[] getResources(SongResource resource) {
		final String[] extensions = { ".wav", ".flac", ".ogg", ".mp3" };
		List<SongResource> result = new ArrayList<>();
		try {
			if (resource.exists()) {
				result.add(resource);
			}
			String filename = resource.name();
			int index = filename.lastIndexOf('.');
			String name = index < 0 ? filename : filename.substring(0, index);
			String extension = index < 0 ? "" : filename.substring(index);
			for (String candidateExtension : extensions) {
				if (candidateExtension.equalsIgnoreCase(extension)) {
					continue;
				}
				SongResource candidate = resource.parent().resolve(name + candidateExtension);
				if (candidate.exists()) {
					result.add(candidate);
				}
			}
		} catch (Exception ignored) {
		}
		return result.isEmpty() ? new SongResource[] { resource } : result.toArray(new SongResource[0]);
	}
}
