package bms.player.beatoraja.song;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 楽曲データベースへのアクセスインターフェイス
 * 
 * @author exch
 */
public interface SongDatabaseAccessor {

	/**
	 * 楽曲を取得する
	 * 
	 * @param key
	 *            属性
	 * @param value
	 *            属性値
	 * @return 検索結果
	 */
	public SongData[] getSongDatas(String key, String value);

	/**
	 * Returns songs whose selection-screen parent matches any supplied folder CRC.
	 *
	 * @param parents selection-screen parent folder CRCs
	 * @return matching songs
	 */
	public default SongData[] getSongDatasByParents(String[] parents) {
		if (parents == null || parents.length == 0) {
			return SongData.EMPTY;
		}
		List<SongData> songs = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String parent : parents) {
			if (parent != null && !parent.isBlank() && seen.add(parent)) {
				for (SongData song : getSongDatas("parent", parent)) {
					songs.add(song);
				}
			}
		}
		return songs.toArray(SongData[]::new);
	}

	/**
	 * MD5/SHA256で指定した楽曲をまとめて取得する
	 * 
	 * @param hashes
	 *            楽曲のハッシュ
	 * @return
	 */
	public SongData[] getSongDatas(String[] hashes);

	/**
	 * スコアデータベース、スコアログデータベース、譜面情報データベースを跨いでSQLで問い合わせを行う
	 * 
	 * @param sql
	 *            SQL
	 * @param score
	 *            スコアデータベースのパス
	 * @param scorelog
	 *            スコアログデータベースのパス
	 * @param info
	 *            譜面情報データベースのパス
	 * @return
	 */
	public SongData[] getSongDatas(String sql, String score, String scorelog, String info);

	public void setSongDatas(SongData[] songs);

	public SongData[] getSongDatasByText(String text);

	/**
	 * 楽曲を取得する
	 * 
	 * @param key
	 *            属性
	 * @param value
	 *            属性値
	 * @return 検索結果
	 */
	public FolderData[] getFolderDatas(String key, String value);

	/**
	 * データベースを更新する
	 * 
	 * @param updatepath
	 *            更新するフォルダのパス。全更新する場合はnull
	 * @param updateAll
	 *            更新の必要がないものも更新するかどうか
	 */
	public void updateSongDatas(String updatepath, String[] bmsroot, boolean updateAll, boolean updateParentWhenMissing, SongInformationAccessor info);

	void updateSongDatas(String updatePath, String[] bmsroot, boolean updateAll, boolean updateParentWhenMissing, SongInformationAccessor info, SongDatabaseUpdateListener listener);
}
