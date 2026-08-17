package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.TableDataAccessor;
import bms.player.beatoraja.select.*;
import bms.player.beatoraja.song.SongData;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 難易度表バー
 *
 * @author exch
 */
public class TableBar extends DirectoryBar {
	/**
	 * 難易度表データ
	 */
	private TableData td;
	/**
	 * 難易度表レベルバー
	 */
    private HashBar[] levels;
    /**
     * 難易度表コースバー
     */
    private GradeBar[] grades;
    /**
     * レベルバー+コースバー
     */
    private Bar[] children;
    private final TableDataAccessor.TableAccessor tr;
    private final boolean difficultyLevelFolders;

    public TableBar(MusicSelector selector, TableData td, TableDataAccessor.TableAccessor tr) {
		this(selector, td, tr, false);
	}

    public TableBar(
            MusicSelector selector,
            TableData td,
            TableDataAccessor.TableAccessor tr,
            boolean difficultyLevelFolders
    ) {
    	super(selector);
        this.tr = tr;
        this.difficultyLevelFolders = difficultyLevelFolders;
        setTableData(td);
    }

    @Override
    public final String getTitle() {
        return td.getName();
    }

	public String getUrl() {
		return td.getUrl();
	}

    public TableDataAccessor.TableAccessor getAccessor() {
    	return tr;
    }

    public void setTableData(TableData td) {
    	this.td = td;
		levels = Stream.of(td.getFolder()).map(folder -> new HashBar(
				selector,
				folder.getName(),
				folder.getSong(),
				difficultyLevelFolders ? legacyFolderTableLevel(td, folder) : null
		)).toArray(HashBar[]::new);
		final CourseData[] courses = td.getCourse();
		Set<String> hashset = Stream.of(courses).flatMap(course -> Stream.of(course.getSong()))
				.map(song -> song.getSha256().length() > 0 ? song.getSha256() : song.getMd5()).collect(Collectors.toSet());
		final SongData[] songs = selector.getSongDatabase().getSongDatas(hashset.toArray(new String[hashset.size()]));

		grades = Stream.of(courses).map(course -> {
			SongData[] songlist = course.getSong();
			for (int j = 0;j < songlist.length;j++) {
				final SongData hash = songlist[j];
				for (SongData sd : songs) {
					if ((hash.getMd5().length() > 0 && hash.getMd5().equals(sd.getMd5())) || (hash.getSha256().length() > 0 && hash.getSha256().equals(sd.getSha256()))) {
						sd.merge(hash);
						songlist[j] = sd;
						break;
					}
				}
			}
			return new GradeBar(course);
		}).toArray(GradeBar[]::new);

		children = Stream.concat(Stream.of(levels), Stream.of(grades)).toArray(Bar[]::new);
    }

	private static Integer legacyFolderTableLevel(TableData table, TableData.TableFolder folder) {
		String level = folder.getName();
		String tag = table.getTag();
		if (tag != null && !tag.isEmpty() && level.startsWith(tag)) {
			level = level.substring(tag.length());
		}
		return TableData.parseDisplayLevel(level);
	}

    public HashBar[] getLevels() {
        return levels;
    }

    public GradeBar[] getGrades() {
        return grades;
    }

    @Override
    public Bar[] getChildren() {
    	return children;
    }

    public TableData getTableData() {
		return td;
	}
}
