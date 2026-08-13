package bms.player.beatoraja.select;

import static bms.player.beatoraja.SystemSoundManager.SoundType.FOLDER_CLOSE;

import java.io.BufferedInputStream;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import bms.player.beatoraja.arena.client.ArenaBar;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.arena.bmsir.BMSIRDanCourseCache;
import bms.player.beatoraja.arena.bmsir.BMSIRPrimaryIrTableCache;
import bms.player.beatoraja.arena.bmsir.BMSIRSelectKeyMode;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.modmenu.SongManagerMenu;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.Queue;
import com.badlogic.gdx.utils.Sort;
import com.badlogic.gdx.utils.StringBuilder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import bms.model.Mode;
import bms.player.beatoraja.*;
import bms.player.beatoraja.CourseData.CourseDataConstraint;
import bms.player.beatoraja.CourseData.TrophyData;
import bms.player.beatoraja.external.BMSSearchAccessor;
import bms.player.beatoraja.ir.IRResponse;
import bms.player.beatoraja.ir.IRChartData;
import bms.player.beatoraja.ir.IRCourseData;
import bms.player.beatoraja.ir.IRTableData;
import bms.player.beatoraja.select.bar.*;
import bms.player.beatoraja.skin.property.EventFactory.EventType;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;
import bms.player.beatoraja.song.SongInformationAccessor;

/**
 * 楽曲バー管理用クラス
 *
 * @author exch
 */
public class BarManager {
	private static final Logger logger = LoggerFactory.getLogger(BarManager.class);
	
	private final MusicSelector select;
	/**
	 * 難易度表バー一覧
	 */
	private volatile TableBar[] tables = new TableBar[0];
	private final Set<TableBar> bmsirPrimaryIrTables =
			Collections.newSetFromMap(new IdentityHashMap<>());
	private final PrimaryIrTableReloader primaryIrTableReloader;
	private volatile PendingPrimaryIrTables pendingPrimaryIrTables;
	private static final String BMSIR_MY_DIFFICULTY_TABLE_URL =
			"bmsir://my-difficulty-table";

	private Bar[] commands;
	
	private TableBar courses;

	private HashBar[] favorites = new HashBar[0];

	/**
	 * 現在のフォルダ階層
	 */
	private final Queue<DirectoryBar> dir = new Queue<>();
	private String dirString = "";
	/**
	 * 現在表示中のバー一覧
	 */
	Bar[] currentsongs;
	/**
	 * 選択中のバーのインデックス
	 */
	int selectedindex;

	/**
	 * 各階層のフォルダを開く元となったバー
	 */
	private final Queue<Bar> sourcebars = new Queue<>();

	// jsonで定義したrandom bar (folder)
	private List<RandomFolder> randomFolderList;

	// システム側で挿入されるルートフォルダ
	private final HashMap<String, Bar> appendFolders = new HashMap<String, Bar>();
	/**
	 * 検索結果バー一覧
	 */
	private final Array<SearchWordBar> search = new Array<SearchWordBar>();
	/**
	 * Arena lobby selection
	 */
	private final Array<ArenaBar> arenaBars = new Array<>();
	/**
	 * ランダムコース結果バー一覧
	 */
	private final Array<RandomCourseResult> randomCourseResult = new Array<>();

	BarContentsLoaderThread loader;
	private TableDataAccessor startupTableDataAccessor;
	private BMSSearchAccessor startupSearchAccessor;
	private Array<TableBar> startupTables;

	public BarManager(MusicSelector select) {
		this(select, task -> {
			Thread thread = new Thread(task, "BMS-IR Primary IR table reload");
			thread.setDaemon(true);
			thread.start();
		});
	}

	BarManager(MusicSelector select, Executor primaryIrReloadExecutor) {
		this.select = select;
		this.primaryIrTableReloader =
				new PrimaryIrTableReloader(primaryIrReloadExecutor);
	}
	
	void init() {
		initLocalTables();
		initIrTables();
		initCourses();
		initFavoritesAndCommands();
	}

	void initLocalTables() {
		startupTableDataAccessor = new TableDataAccessor(
				select.resource.getConfig().getTablepath()
		);

        TableData[] unsortedtables;
        try (var perf = PerformanceMetrics.get().Event("Saved table load")) {
			unsortedtables = startupTableDataAccessor.readAll();
        }

		final List<TableData> sortedtables = new ArrayList<TableData>(unsortedtables.length);
		
		for(String url : select.resource.getConfig().getTableURL()) {
			for(int i = 0;i < unsortedtables.length;i++) {
				final TableData td = unsortedtables[i];
				if(td != null && url.equals(td.getUrl())) {
					sortedtables.add(td);
					unsortedtables[i] = null;
					break;
				}
			}
		}

		Arrays.stream(unsortedtables).filter(Objects::nonNull).forEach(td -> sortedtables.add(td));

		startupSearchAccessor = new BMSSearchAccessor(
				select.resource.getConfig().getTablepath()
		);

		startupTables = new Array<TableBar>();

		sortedtables.stream().map(td -> {
			if (td.getName().equals("BMS Search")) {
				return new TableBar(select, td, startupSearchAccessor);
			} else {
				return new TableBar(select, td,
						new TableDataAccessor.DifficultyTableAccessor(select.resource.getConfig().getTablepath(), td.getUrl()));
			}			
		}).forEach(startupTables::add);
	}

	int initIrTables() {
		if (startupTables == null) {
			initLocalTables();
		}
		bmsirPrimaryIrTables.clear();
		int loadedTables = 0;
		if(select.main.getIRStatus().length > 0) {
			MainController.IRStatus primaryIr = select.main.getIRStatus()[0];
			boolean isBmsirPrimary = BMSIRDanCourseCache.isBmsirPrimaryName(
					primaryIr.config.getIrname()
			);
			if (isBmsirPrimary) {
				TableData[] cachedTables = BMSIRPrimaryIrTableCache.read(
						select.resource.getConfig().getPlayerpath(),
						select.resource.getPlayerConfig().getId()
				);
				for (TableData cached : cachedTables) {
					TableBar table = createIrTableBar(cached);
					if (table != null) {
						startupTables.add(table);
						bmsirPrimaryIrTables.add(table);
						loadedTables++;
					}
				}
			} else {
				IRResponse<IRTableData[]> response = primaryIr.connection.getTableDatas();
				if(response != null && response.isSucceeded() && response.getData() != null) {
					for(IRTableData irtd : response.getData()) {
						TableBar table = createIrTableBar(irtd);
						if (table != null) {
							startupTables.add(table);
							loadedTables++;
						}
					}
				} else {
					logger.warn(
							"IRからのテーブル取得失敗 : {}",
							response == null ? "No response" : response.getMessage()
					);
				}
			}
		}

		BMSSearchAccessor searchAccessor = startupSearchAccessor;
		TableDataAccessor tableDataAccessor = startupTableDataAccessor;
		new Thread(() -> {
			TableData td = searchAccessor.read();
			if (td != null) {
				tableDataAccessor.write(td);
			}
		}).start();

		this.tables = startupTables.toArray(TableBar.class);
		return loadedTables;
	}

	void initCourses() {
		TableDataAccessor.TableAccessor courseReader = new TableDataAccessor.TableAccessor("course") {
			@Override
			public TableData read() {
				TableData td = new TableData();
				td.setName("COURSE");
				CourseData[] personalCourses = new CourseDataAccessor("course").readAll();
				if (select.resource.getPlayerConfig().isBmsirDanLocalSyncEnabled()) {
					CourseData[] bmsirCourses = BMSIRDanCourseCache.read(
							select.resource.getConfig().getPlayerpath(),
							select.resource.getPlayerConfig().getId()
					);
					td.setCourse(Stream.concat(
							Stream.of(personalCourses),
							Stream.of(bmsirCourses)
					).toArray(CourseData[]::new));
				} else {
					td.setCourse(personalCourses);
				}
				return td;
			}

			@Override
			public void write(TableData td) {
			}
		};
		courses = new TableBar(select, courseReader.read(), courseReader);
	}

	void initFavoritesAndCommands() {
		CourseData[] cds = new CourseDataAccessor("favorite").readAll();
//		if(cds.length == 0) {
//			cds = new CourseData[1];
//			cds[0] = new CourseData();
//			cds[0].setName("FAVORITE");
//		}
		
		favorites = Stream.of(cds).map(cd -> new HashBar(select, cd.getName(), cd.getSong())).toArray(HashBar[]::new);

		Array<Bar> l = new Array<Bar>();

		Array<Bar> lampupdate = new Array<Bar>();
		Array<Bar> scoreupdate = new Array<Bar>();
		for(int i = 0;i < 30;i++) {
			String s = i == 0 ? "TODAY" : i + "DAYS AGO";
			long t = ((System.currentTimeMillis() / 86400000) - i) * 86400;
			lampupdate.add(new CommandBar(select,  s, "scorelog.clear > scorelog.oldclear AND scorelog.date >= "  + t + " AND scorelog.date < " + (t + 86400)));
			scoreupdate.add(new CommandBar(select,  s,  "scorelog.score > scorelog.oldscore AND scorelog.date >= "  + t + " AND scorelog.date < " + (t + 86400)));
		}
		l.add(new ContainerBar("LAMP UPDATE", lampupdate.toArray(Bar.class)));
		l.add(new ContainerBar("SCORE UPDATE", scoreupdate.toArray(Bar.class)));
		try {
			Json json = new Json();
			CommandFolder[] cf = json.fromJson(CommandFolder[].class,
					new BufferedInputStream(Files.newInputStream(Paths.get("folder/default.json"))));
			Stream.of(cf).forEach(folder -> l.add(createCommandBar(select, folder)));
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			randomFolderList = objectMapper.readValue(
					new BufferedInputStream(Files.newInputStream(Paths.get("random/default.json"))),
					new TypeReference<List<RandomFolder>>() {
					});
		} catch (Throwable e) {
			randomFolderList = new ArrayList<RandomFolder>();
			RandomFolder randomFolder = new RandomFolder();
			randomFolder.setName("RANDOM SELECT");
			randomFolderList.add(randomFolder);
			e.printStackTrace();
		}

		commands = l.toArray(Bar.class);
		startupTableDataAccessor = null;
		startupSearchAccessor = null;
		startupTables = null;
	}
	
	public boolean updateBar() {
		if (dir.size > 0) {
			return updateBar(dir.last());
		}
		return updateBar(null);
	}

	void invalidatePlayerScoreDisplay() {
		if (loader != null) {
			loader.stopRunning();
			loader = null;
		}
		clearPlayerScores(currentsongs);
	}

	SongData[] getVisibleSongDatas() {
		if (currentsongs == null) {
			return new SongData[0];
		}
		return Stream.of(currentsongs)
				.filter(bar -> bar instanceof SongBar && ((SongBar) bar).existsSong())
				.flatMap(bar -> Stream.of(((SongBar) bar).getDifficultyVariants()))
				.filter(song -> song.getPath() != null)
				.toArray(SongData[]::new);
	}

	static void clearPlayerScores(Bar[] bars) {
		if (bars == null) {
			return;
		}
		for (Bar bar : bars) {
			bar.setScore(null);
			if (bar instanceof DirectoryBar directory) {
				directory.clear();
			}
		}
	}

	public boolean updateBar(Bar bar) {
		Bar prevbar = currentsongs != null ? currentsongs[selectedindex] : null;
		int prevdirsize = dir.size;
		Bar sourcebar = null;
		Array<Bar> l = new Array<Bar>();
		boolean showInvisibleCharts = false;
		boolean isSortable = true;
		boolean removedExistingDirectory = false;

		if (MainLoader.getIllegalSongCount() > 0) {
			l.addAll(SongBar.toSongBarArray(select.getSongDatabase().getSongDatas(MainLoader.getIllegalSongs())));
		} else if (bar == null) {
			// root bar
			if (dir.size > 0) {
				prevbar = dir.first();
			}
            if (prevbar instanceof ContextMenuBar) {
                prevbar = ((ContextMenuBar)prevbar).getPrevious();
            }
			dir.clear();
			sourcebars.clear();
			l.addAll(new FolderBar(select, null, "e2977170").getChildren());
			l.add(courses);
			l.addAll(favorites);
			appendFolders.keySet().forEach((key) -> {
			    l.add(appendFolders.get(key));
			});
			l.addAll(tables);
			l.addAll(commands);
			l.addAll(search);
			l.addAll(arenaBars);
		} else if (bar instanceof DirectoryBar) {
			showInvisibleCharts = ((DirectoryBar)bar).isShowInvisibleChart();
			if(dir.indexOf((DirectoryBar) bar, true) != -1) {
				while(dir.last() != bar) {
					prevbar = dir.removeLast();
					sourcebar = sourcebars.removeLast();
				}
				dir.removeLast();
				removedExistingDirectory = true;
			}
			l.addAll(((DirectoryBar) bar).getChildren());
			isSortable = ((DirectoryBar) bar).isSortable();

			if (bar instanceof ContainerBar && randomCourseResult.size > 0) {
				StringBuilder str = new StringBuilder();
				for (Bar b : dir) {
					str.append(b.getTitle()).append(" > ");
				}
				str.append(bar.getTitle()).append(" > ");
				final String ds = str.toString();
				for (RandomCourseResult r : randomCourseResult) {
					if (r.dirString.equals(ds)) {
						l.add(r.course);
					}
				}
			}
		}

		if(!select.resource.getConfig().isShowNoSongExistingBar()) {
			Array<Bar> remove = new Array<Bar>();
			for (Bar b : l) {
				if ((b instanceof SongBar && !((SongBar) b).existsSong())
					|| b instanceof GradeBar && !((GradeBar) b).existsAllSongs()) {
					remove.add(b);
				}
			}
			l.removeAll(remove, true);
		}

		if (l.size > 0) {
			final PlayerConfig config = select.resource.getPlayerConfig();
			final String[] visibleModes = config.getBmsirSelectKeyModes();
			Mode mode = config.getMode();
			if (!BMSIRSelectKeyMode.isModeVisible(visibleModes, mode)) {
				mode = null;
				config.setMode(null);
			}
			Array<Bar> remove = new Array<>();
			for (Bar b : l) {
				if (b instanceof SongBar songBar && songBar.getSongData() != null) {
					final SongData song = songBar.getSongData();
					if ((!showInvisibleCharts && (song.getFavorite()
							& (SongData.INVISIBLE_SONG | SongData.INVISIBLE_CHART)) != 0)
							|| !BMSIRSelectKeyMode.isSongModeVisible(
									visibleModes,
									song.getMode()
							)
							|| (mode != null && song.getMode() != 0
									&& song.getMode() != mode.id)) {
						remove.add(b);
					}
				}
			}
			l.removeAll(remove, true);
			if (l.size == 0) {
				if (removedExistingDirectory) {
					dir.addLast((DirectoryBar) bar);
				}
				logger.warn("表示対象の楽曲がありません");
				return false;
			}

			if (bar != null) {
				dir.addLast((DirectoryBar) bar);
				if (dir.size > prevdirsize) {
					sourcebars.addLast(prevbar);
				}
			}

			Bar[] newcurrentsongs = l.toArray(Bar.class);
			if (PlayerConfig.BMSIR_SELECT_DIFFICULTY_DISPLAY_LR2.equals(
					config.getBmsirSelectDifficultyDisplay()
			)) {
				String preferredSha256 = prevbar instanceof SongBar
						? ((SongBar) prevbar).getSongData().getSha256()
						: null;
				newcurrentsongs = groupDifficultyBars(
						newcurrentsongs,
						preferredSha256,
						config.getBmsirSelectDifficultyStage()
				);
			}
			for (Bar b : newcurrentsongs) {
				if (b instanceof SongBar) {
					SongData sd = ((SongBar) b).getSongData();
					if (sd != null && select.getScoreDataCache().existsScoreDataCache(sd, config.getLnmode())) {
						b.setScore(select.getScoreDataCache().readScoreData(sd, config.getLnmode()));
					}
				}
			}

			if(isSortable) {
				final BarSorter sorter = BarSorter.valueOf(select.main.getPlayerConfig().getSortid());
			    Sort.instance().sort(newcurrentsongs, sorter != null ? sorter.sorter : BarSorter.TITLE.sorter);
                if (SongManagerMenu.isLastPlayedSortEnabled()) {
                    Sort.instance().sort(newcurrentsongs, BarSorter.LASTUPDATE.sorter);
                }
			}

			Array<Bar> bars = new Array<Bar>();
			if (select.main.getPlayerConfig().isRandomSelect()
				&& !(bar instanceof ContextMenuBar)) {
				try {
					for (RandomFolder randomFolder : randomFolderList) {
						SongData[] randomTargets = Stream.of(newcurrentsongs)
								.filter(songBar -> songBar instanceof SongBar)
								.flatMap(songBar -> Stream.of(
										((SongBar) songBar).getDifficultyVariants()
								))
								.filter(song -> song.getPath() != null)
								.toArray(SongData[]::new);
						if (randomFolder.getFilter() != null) {
							Set<String> filterKey = randomFolder.getFilter().keySet();
							randomTargets = Stream.of(randomTargets).filter(r -> {
								ScoreData scoreData = select.getScoreDataCache().readScoreData(r, config.getLnmode());
                                return randomFolder.filterSong(scoreData);
							}).toArray(SongData[]::new);
						}
						if ((randomFolder.getFilter() != null && randomTargets.length >= 1)
								|| (randomFolder.getFilter() == null && randomTargets.length >= 2)) {
							Bar randomBar = new ExecutableBar(randomTargets, select.main.getCurrentState(),
									randomFolder.getName());
							bars.add(randomBar);
						}
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}

			bars.addAll(newcurrentsongs);

			currentsongs = bars.toArray(Bar.class);
			
			select.getBarRender().updateBarText();

			selectedindex = 0;

			// 変更前と同じバーがあればカーソル位置を保持する
			if (sourcebar != null) {
				prevbar = sourcebar;
			}
			if (prevbar != null) {
				if (prevbar instanceof SongBar && ((SongBar) prevbar).existsSong()) {
					final SongBar prevsong = (SongBar) prevbar;
					for (int i = 0; i < currentsongs.length; i++) {
						if (currentsongs[i] instanceof SongBar && ((SongBar) currentsongs[i]).existsSong() &&
								((SongBar) currentsongs[i]).getSongData().getSha256()
								.equals(prevsong.getSongData().getSha256())) {
							selectedindex = i;
							break;
						}
					}
				} else {
					for (int i = 0; i < currentsongs.length; i++) {
						if (currentsongs[i].getClass() == prevbar.getClass() && currentsongs[i].getTitle().equals(prevbar.getTitle())) {
							selectedindex = i;
							break;
						}
					}

				}
			}

			if (loader != null) {
				loader.stopRunning();
			}
			loader = new BarContentsLoaderThread(select, currentsongs);
			loader.start();
			select.getScoreDataProperty().update(currentsongs[selectedindex].getScore(),
					currentsongs[selectedindex].getRivalScore());

			StringBuilder str = new StringBuilder();
			for (Bar b : dir) {
				str.append(b.getTitle()).append(" > ");
			}
			dirString = str.toString();

			select.selectedBarMoved();

			return true;
		}

		if (dir.size > 0) {
			updateBar(dir.last());
		} else {
			updateBar(null);
		}
		logger.warn("楽曲がありません");
		return false;
	}

	public void close() {
		if(dir.size == 0) {
            SongManagerMenu.forceDisableLastPlayedSort();
			select.executeEvent(EventType.sort);
			return;
		}

		final DirectoryBar current = dir.removeLast();
		final DirectoryBar parent = dir.size > 0 ? dir.last() : null;
		dir.addLast(current);
		updateBar(parent);
		select.play(FOLDER_CLOSE);
	}

	public Queue<DirectoryBar> getDirectory() {
		return dir;
	}

	public String getDirectoryString() {
		return dirString;
	}

	public Bar getSelected() {
		return currentsongs != null ? currentsongs[selectedindex] : null;
	}

	/** Installs deterministic in-memory bars for an off-screen skin preview. */
	void installSkinPreviewBars(Bar[] bars, int selectedIndex) {
		currentsongs = bars != null && bars.length > 0 ? bars : new Bar[0];
		selectedindex = currentsongs.length == 0
				? 0
				: Math.max(0, Math.min(selectedIndex, currentsongs.length - 1));
		dir.clear();
		sourcebars.clear();
		dirString = "SKIN PREVIEW / VIRTUAL FOLDER";
		select.getBarRender().updateBarText();
	}

	public void setSelected(Bar bar) {
		for (int i = 0; i < currentsongs.length; i++) {
			if (currentsongs[i].getTitle().equals(bar.getTitle())) {
				selectedindex = i;
				select.getScoreDataProperty().update(currentsongs[selectedindex].getScore(),
						currentsongs[selectedindex].getRivalScore());
				break;
			}
		}
	}

	public float getSelectedPosition() {
		return ((float) selectedindex) / currentsongs.length;
	}

    public TableBar[] getTables() { return tables.clone(); }

	/** Returns true when this root bar came from the configured BMS-IR Primary IR. */
	public boolean isBmsirPrimaryIrTable(TableBar table) {
		return table != null && bmsirPrimaryIrTables.contains(table);
	}

	/** Starts a non-blocking refresh for a selected BMS-IR Primary IR root table. */
	public boolean reloadBmsirPrimaryIrTable(TableBar table) {
		if (!isBmsirPrimaryIrTable(table)) {
			return false;
		}
		MainController.IRStatus[] statuses = select.main.getIRStatus();
		if (statuses.length == 0 || !BMSIRDanCourseCache.isBmsirPrimaryName(
				statuses[0].config.getIrname()
		)) {
			return false;
		}
		MainController.IRStatus primaryIr = statuses[0];
		return startBmsirPrimaryIrTableReload(primaryIr, true);
	}

	/** Starts the automatic Primary IR refresh after startup has completed. */
	public boolean refreshBmsirPrimaryIrTablesAfterStartup() {
		MainController.IRStatus[] statuses = select.main.getIRStatus();
		if (statuses.length == 0 || !BMSIRDanCourseCache.isBmsirPrimaryName(
				statuses[0].config.getIrname()
		)) {
			return false;
		}
		return startBmsirPrimaryIrTableReload(statuses[0], false);
	}

	private boolean startBmsirPrimaryIrTableReload(
			MainController.IRStatus primaryIr,
			boolean notifyUser
	) {
		boolean hadPrimaryIrTables = !bmsirPrimaryIrTables.isEmpty();
		boolean started = primaryIrTableReloader.start(
				primaryIr.connection::getTableDatas,
				new PrimaryIrTableReloader.Listener() {
					@Override
					public void succeeded(IRTableData[] refreshedTables) {
						TableData[] cachedTables = convertValidIrTables(
								refreshedTables,
								hadPrimaryIrTables
						);
						BMSIRPrimaryIrTableCache.sync(
								select.resource.getConfig().getPlayerpath(),
								select.resource.getPlayerConfig().getId(),
								cachedTables
						);
						boolean coursesChanged =
								select.resource.getPlayerConfig().isBmsirDanLocalSyncEnabled()
								&& BMSIRDanCourseCache.sync(
										select.resource.getConfig().getPlayerpath(),
										select.resource.getPlayerConfig().getId(),
										refreshedTables
								);
						pendingPrimaryIrTables = new PendingPrimaryIrTables(
								cachedTables,
								coursesChanged,
								notifyUser
						);
						postToRenderThread(BarManager.this::applyPendingPrimaryIrTables);
					}

					@Override
					public void failed(String message) {
						logger.warn("IRからのテーブル再取得失敗 : {}", message);
						if (notifyUser) {
							postToRenderThread(() -> ImGuiNotify.error(BMSIRArenaI18n.text(
									"Primary IR選曲テーブルの更新に失敗しました: " + message,
									"Failed to refresh Primary IR selection tables: " + message
							)));
						}
					}
				}
		);
		if (started && notifyUser) {
			ImGuiNotify.info(BMSIRArenaI18n.text(
					"Primary IR選曲テーブルを更新しています",
					"Refreshing Primary IR selection tables"
			));
		} else if (notifyUser) {
			ImGuiNotify.warning(BMSIRArenaI18n.text(
					"Primary IR選曲テーブルは更新中です",
					"Primary IR selection tables are already refreshing"
			));
		}
		return true;
	}

	/** Applies a completed fetch only while Music Select is active. */
	void applyPendingPrimaryIrTables() {
		PendingPrimaryIrTables pending = pendingPrimaryIrTables;
		if (pending == null
				|| select.main.getCurrentState() != select
				|| !canApplyPrimaryIrTables(pending.notifyUser(), dir.size)) {
			return;
		}
		pendingPrimaryIrTables = null;
		try {
			List<TableBar> replacements = new ArrayList<>(pending.tables().length);
			for (TableData source : pending.tables()) {
				TableBar replacement = createIrTableBar(source);
				if (replacement == null) {
					throw new IllegalArgumentException("invalid Primary IR table response");
				}
				replacements.add(replacement);
			}
			replaceBmsirPrimaryIrTables(replacements, pending.coursesChanged());
			if (pending.notifyUser()) {
				ImGuiNotify.success(BMSIRArenaI18n.text(
						"Primary IR選曲テーブルを更新しました",
						"Refreshed Primary IR selection tables"
				));
			}
		} catch (RuntimeException error) {
			logger.error("BMS-IR table reload could not be applied", error);
			ImGuiNotify.error(BMSIRArenaI18n.text(
					"BMS-IR表の再読み込み結果を適用できませんでした",
					"Could not apply the reloaded BMS-IR tables"
			));
		} finally {
			primaryIrTableReloader.complete();
		}
	}

	private synchronized void replaceBmsirPrimaryIrTables(
			List<TableBar> replacements,
			boolean coursesChanged
	) {
		TableBar[] previousTables = tables;
		Set<TableBar> previousManaged = Collections.newSetFromMap(new IdentityHashMap<>());
		previousManaged.addAll(bmsirPrimaryIrTables);
		TableBar previousCourses = courses;
		List<TableBar> next = replaceManagedTables(
				Arrays.asList(tables),
				bmsirPrimaryIrTables,
				replacements
		);
		// Directory stacks retain child instances from the old TableBars. Return
		// to root before replacing their owners so no stale child can survive.
		updateBar(null);
		try {
			tables = next.toArray(TableBar[]::new);
			bmsirPrimaryIrTables.clear();
			bmsirPrimaryIrTables.addAll(replacements);
			if (coursesChanged) {
				initCourses();
			}
			updateBar(null);
		} catch (RuntimeException error) {
			tables = previousTables;
			bmsirPrimaryIrTables.clear();
			bmsirPrimaryIrTables.addAll(previousManaged);
			courses = previousCourses;
			try {
				updateBar(null);
			} catch (RuntimeException rollbackError) {
				error.addSuppressed(rollbackError);
			}
			throw error;
		}
	}

	static <T> List<T> replaceManagedTables(
			List<T> current,
			Set<T> managed,
			List<T> replacements
	) {
		List<T> next = new ArrayList<>(
				Math.max(0, current.size() - managed.size()) + replacements.size()
		);
		int insertionIndex = -1;
		for (T item : current) {
			if (managed.contains(item)) {
				if (insertionIndex < 0) {
					insertionIndex = next.size();
				}
			} else {
				next.add(item);
			}
		}
		next.addAll(insertionIndex < 0 ? next.size() : insertionIndex, replacements);
		return next;
	}

	static boolean canApplyPrimaryIrTables(boolean manualRefresh, int directoryDepth) {
		return manualRefresh || directoryDepth <= 0;
	}

	static void validateIrTableResponse(IRTableData[] sources, boolean hadTables) {
		convertValidIrTables(sources, hadTables);
	}

	static TableData[] convertValidIrTables(IRTableData[] sources, boolean hadTables) {
		if (sources == null || (sources.length == 0 && hadTables)) {
			throw new IllegalArgumentException("empty Primary IR table response");
		}
		TableData[] converted = new TableData[sources.length];
		for (int index = 0; index < sources.length; index++) {
			TableData table = convertIrTable(sources[index]);
			if (table == null || !table.validate()) {
				throw new IllegalArgumentException("invalid Primary IR table response");
			}
			converted[index] = table;
		}
		return converted;
	}

	static TableData convertIrTable(IRTableData source) {
		if (source == null) {
			return null;
		}
		TableData table = new TableData();
		table.setName(source.name);
		table.setFolder(Arrays.stream(
				source.folders == null ? new IRTableData.IRTableFolder[0] : source.folders
		).filter(Objects::nonNull).map(folder -> {
			TableData.TableFolder converted = new TableData.TableFolder();
			converted.setName(folder.name);
			converted.setSong(Arrays.stream(
					folder.charts == null ? new IRChartData[0] : folder.charts
			).filter(Objects::nonNull).map(BarManager::convertIrChart).toArray(SongData[]::new));
			return converted;
		}).toArray(TableData.TableFolder[]::new));
		table.setCourse(Arrays.stream(
				source.courses == null ? new IRCourseData[0] : source.courses
		).filter(Objects::nonNull).map(course -> {
			CourseData converted = new CourseData();
			converted.setName(course.name);
			converted.setSong(Arrays.stream(
					course.charts == null ? new IRChartData[0] : course.charts
			).filter(Objects::nonNull).map(BarManager::convertIrChart).toArray(SongData[]::new));
			converted.setConstraint(
					course.constraint == null
							? CourseDataConstraint.EMPTY
							: Arrays.stream(course.constraint)
									.filter(Objects::nonNull)
									.toArray(CourseDataConstraint[]::new)
			);
			converted.setTrophy(Arrays.stream(
					course.trophy == null
							? new IRCourseData.IRTrophyData[0]
							: course.trophy
			).filter(Objects::nonNull).map(trophy -> {
				TrophyData convertedTrophy = new TrophyData();
				convertedTrophy.setName(trophy.name);
				convertedTrophy.setMissrate(trophy.smissrate);
				convertedTrophy.setScorerate(trophy.scorerate);
				return convertedTrophy;
			}).toArray(TrophyData[]::new));
			converted.setRelease(true);
			return converted;
		}).toArray(CourseData[]::new));
		return table;
	}

	private static SongData convertIrChart(IRChartData chart) {
		SongData song = new SongData();
		song.setSha256(chart.sha256);
		song.setMd5(chart.md5);
		song.setTitle(chart.title);
		song.setSubtitle(chart.subtitle);
		song.setArtist(chart.artist);
		song.setSubartist(chart.subartist);
		song.setGenre(chart.genre);
		song.setUrl(chart.url);
		song.setAppendurl(chart.appendurl);
		if (chart.mode != null) {
			song.setMode(chart.mode.id);
		}
		return song;
	}

	private TableBar createIrTableBar(IRTableData source) {
		TableData table = convertIrTable(source);
		return createIrTableBar(table);
	}

	private TableBar createIrTableBar(TableData table) {
		if (table == null || !table.validate()) {
			return null;
		}
		return new TableBar(
				select,
				table,
				new TableDataAccessor.DifficultyTableAccessor(
						select.resource.getConfig().getTablepath(),
						table.getUrl()
				)
		);
	}

	private static void postToRenderThread(Runnable task) {
		if (Gdx.app != null) {
			Gdx.app.postRunnable(task);
		}
	}

	private record PendingPrimaryIrTables(
			TableData[] tables,
			boolean coursesChanged,
			boolean notifyUser
	) {
	}

	/** Replaces the server-backed owner table after returning to a safe root. */
	public synchronized void replaceBmsirMyDifficultyTable(TableData data) {
		// A directory keeps child bar instances from the TableBar that opened it.
		// Clear that stack before replacing the root object so no stale children
		// survive an in-game save/reload.
		updateBar(null);
		List<TableBar> next = new ArrayList<>();
		int insertionIndex = -1;
		for (TableBar table : tables) {
			if (BMSIR_MY_DIFFICULTY_TABLE_URL.equals(table.getUrl())) {
				if (insertionIndex < 0) {
					insertionIndex = next.size();
				}
				continue;
			}
			next.add(table);
		}
		if (data != null && data.getFolder() != null && data.getFolder().length > 0) {
			TableDataAccessor.TableAccessor accessor =
					new TableDataAccessor.TableAccessor(BMSIR_MY_DIFFICULTY_TABLE_URL) {
						@Override
						public TableData read() {
							return data;
						}

						@Override
						public void write(TableData ignored) {
						}
					};
			TableBar replacement = new TableBar(select, data, accessor);
			next.add(insertionIndex < 0 ? next.size() : insertionIndex, replacement);
		}
		tables = next.toArray(TableBar[]::new);
		updateBar(null);
	}

	public void setSelectedPosition(float value) {
		if (value >= 0 && value < 1) {
			selectedindex = (int) (currentsongs.length * value);
		}
		select.getScoreDataProperty().update(currentsongs[selectedindex].getScore(),
				currentsongs[selectedindex].getRivalScore());
	}

	public void move(boolean inclease) {
		if (inclease) {
			selectedindex++;
		} else {
			selectedindex += currentsongs.length - 1;
		}
		selectedindex = selectedindex % currentsongs.length;
		select.getScoreDataProperty().update(currentsongs[selectedindex].getScore(),
				currentsongs[selectedindex].getRivalScore());
	}

	/** Changes to the next same-folder/mode difficulty using the configured display. */
	public boolean cycleSelectedDifficulty() {
		PlayerConfig config = select.resource.getPlayerConfig();
		if (PlayerConfig.BMSIR_SELECT_DIFFICULTY_DISPLAY_SEPARATE.equals(
				config.getBmsirSelectDifficultyDisplay()
		)) {
			int nextIndex = nextDifficultyBarIndex(currentsongs, selectedindex);
			if (nextIndex < 0) {
				return false;
			}
			selectedindex = nextIndex;
			select.getScoreDataProperty().update(
					currentsongs[selectedindex].getScore(),
					currentsongs[selectedindex].getRivalScore()
			);
			select.getBarRender().updateBarText();
			select.selectedSongVariantChanged();
			return true;
		}

		Bar current = getSelected();
		if (!(current instanceof SongBar songBar) || !songBar.cycleDifficulty()) {
			return false;
		}
		SongData song = songBar.getSongData();
		if (song.getDifficulty() >= 1 && song.getDifficulty() <= 5) {
			config.setBmsirSelectDifficultyStage(song.getDifficulty());
		}
		updateBar();
		select.selectedSongVariantChanged();
		return true;
	}

	static int nextDifficultyBarIndex(Bar[] bars, int currentIndex) {
		if (bars == null
				|| currentIndex < 0
				|| currentIndex >= bars.length
				|| !(bars[currentIndex] instanceof SongBar selected)) {
			return -1;
		}
		String groupKey = difficultyGroupKey(selected.getSongData());
		List<Integer> candidates = IntStream.range(0, bars.length)
				.filter(index -> bars[index] instanceof SongBar songBar
						&& groupKey.equals(difficultyGroupKey(songBar.getSongData())))
				.boxed()
				.sorted((left, right) -> {
					int compared = SongBar.compareDifficulty(
							((SongBar) bars[left]).getSongData(),
							((SongBar) bars[right]).getSongData()
					);
					return compared != 0 ? compared : Integer.compare(left, right);
				})
				.toList();
		if (candidates.size() < 2) {
			return -1;
		}
		int position = candidates.indexOf(currentIndex);
		return candidates.get((position + 1) % candidates.size());
	}

	/** Cycles configured key modes independently of the current bar/list shape. */
	public boolean cycleBmsirSelectMode(int direction) {
		PlayerConfig config = select.resource.getPlayerConfig();
		Mode original = config.getMode();
		Mode cursor = original;
		int candidateCount = BMSIRSelectKeyMode.selected(
				config.getBmsirSelectKeyModes()
		).size();
		for (int attempt = 0; attempt < candidateCount; attempt++) {
			BMSIRSelectKeyMode candidate = BMSIRSelectKeyMode.nextConfigured(
					config.getBmsirSelectKeyModes(),
					cursor,
					direction
			);
			if (candidate == null || candidate.mode() == original) {
				break;
			}
			config.setMode(candidate.mode());
			if (updateBar()) {
				return true;
			}
			cursor = candidate.mode();
		}
		config.setMode(original);
		updateBar();
		return false;
	}

	static Bar[] groupDifficultyBars(
			Bar[] bars,
			String preferredSha256,
			int difficultyStage
	) {
		Map<String, List<SongData>> groups = new LinkedHashMap<>();
		for (Bar bar : bars) {
			if (bar instanceof SongBar songBar) {
				for (SongData song : songBar.getDifficultyVariants()) {
					String key = difficultyGroupKey(song);
					groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(song);
				}
			}
		}

		Set<String> emitted = new HashSet<>();
		List<Bar> grouped = new ArrayList<>(bars.length);
		for (Bar bar : bars) {
			if (!(bar instanceof SongBar songBar)) {
				grouped.add(bar);
				continue;
			}
			String key = difficultyGroupKey(songBar.getSongData());
			if (!emitted.add(key)) {
				continue;
			}
			List<SongData> variants = groups.get(key);
			if (variants.size() == 1) {
				grouped.add(bar);
			} else {
				grouped.add(new SongBar(
						variants.toArray(SongData[]::new),
						preferredSha256,
						difficultyStage
				));
			}
		}
		return grouped.toArray(Bar[]::new);
	}

	private static String difficultyGroupKey(SongData song) {
		String folder = song.getFolder();
		if (folder == null || folder.isBlank()) {
			String sha256 = song.getSha256();
			return sha256 == null || sha256.isBlank()
					? "chart-object\u0000" + System.identityHashCode(song)
					: "chart\u0000" + sha256;
		}
		return "folder\u0000" + song.getMode() + "\u0000" + folder;
	}

	private Bar createCommandBar(MusicSelector select, CommandFolder folder) {
		return (folder.getFolder() != null && folder.getFolder().length > 0 || folder.getRandomCourse() != null && folder.getRandomCourse().length > 0) ?
			new ContainerBar(folder.getName(), Stream.concat(
					Stream.of(folder.getFolder()).map(child -> createCommandBar(select, child))
					,Stream.of(folder.getRandomCourse()).map(RandomCourseBar::new)).toArray(Bar[]::new)) : 
			new CommandBar(select, folder.getName(), folder.getSql(), folder.isShowall());
	}

	public void addSearch(SearchWordBar bar) {
		for (SearchWordBar s : search) {
			if (s.getTitle().equals(bar.getTitle())) {
				search.removeValue(s, true);
				break;
			}
		}
		if (search.size >= select.resource.getConfig().getMaxSearchBarCount()) {
			search.removeIndex(0);
		}
		search.add(bar);
	}

	public void replaceArenaSelection(ArenaBar bar) {
		if (!arenaBars.isEmpty()) {
			arenaBars.clear();
		}
		arenaBars.add(bar);
	}

	public void addRandomCourse(GradeBar bar, String dirString) {
		if (randomCourseResult.size >= 100) {
			randomCourseResult.removeIndex(0);
		}
		randomCourseResult.add(new RandomCourseResult(bar, dirString));
	}

	synchronized public void setAppendDirectoryBar(String key, Bar bar) {
	    this.appendFolders.put(key, bar);
	}

	public static class CommandFolder {

		private String name;
		private CommandFolder[] folder = new CommandFolder[0];
		private String sql;
		private RandomCourseData[] rcourse = RandomCourseData.EMPTY;
		private boolean showall = false;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public CommandFolder[] getFolder() {
			return folder;
		}

		public void setFolder(CommandFolder[] songs) {
			this.folder = songs;
		}

		public String getSql() {
			return sql;
		}

		public void setSql(String sql) {
			this.sql = sql;
		}

		public RandomCourseData[] getRandomCourse() { return rcourse; }

		public void setRandomCourse(RandomCourseData[] course) { this.rcourse = course; }

		public boolean isShowall() {
			return showall;
		}

		public void setShowall(boolean showall) {
			this.showall = showall;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class RandomFolder {
		private String name;
		private Map<String, Object> filter;
		public String getName() {
			return "[RANDOM] " + name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Map<String, Object> getFilter() {
			return filter;
		}

		public void setFilter(Map<String, Object> filter) {
			this.filter = filter;
		}

        public Boolean filterSong(ScoreData scoreData) {
            Set<String> filterKey = this.getFilter().keySet();
            for (String key : filterKey) {
                String getterMethodName = "get" + key.substring(0, 1).toUpperCase()
                        + key.substring(1);
                try {
                    if (this.getFilter().get(key) instanceof Integer) { // Fork for integer values of the filter key.
                        Integer value = (Integer)this.getFilter().get(key);
                        if (scoreData == null) {
                            if (0 != value) {
                                return false;
                            }
                        } else {
                            Method getterMethod = ScoreData.class.getMethod(getterMethodName);
                            Object propertyValue = getterMethod.invoke(scoreData);
                            if (!propertyValue.equals(value)) {
                                return false;
                            }
                        }
                        return true;
                    }
                    Object valueArr[] = ((String)this.getFilter().get(key)).split("&&");
                    for (Object value : valueArr) // Fork for string values of the filter key.
                    {
                        value = ((String)value).replaceAll("\\s",""); // Clean from whitespaces.
                        if (scoreData == null) {
                            String stringValue = (String) value;
                            if (!stringValue.isEmpty() && !stringValue.substring(0, 1).equals("<")) {
                                return false; // Because lack of value would be less than anything.
                            }
                        } else {
                            Method getterMethod = ScoreData.class.getMethod(getterMethodName);
                            Object propertyValue = getterMethod.invoke(scoreData);
                            if (propertyValue instanceof Integer) {
                                String valueString = (String)value;
                                Integer propertyValueInt = (Integer)propertyValue;
                                Integer filterValueInt;
                                // Checking the position for the integer bit of the key value.
                                if (valueString.substring(1,2).equals("=")){ // If the operation is either >= or <=.
                                    filterValueInt = Integer.parseInt(valueString.substring(2));
                                }
                                // If the operation is > or <.
                                else filterValueInt = Integer.parseInt(valueString.substring(1));

                                if (valueString.substring(0,1).equals(">")) // Fork for > and >= operations.
                                {
                                    if (valueString.substring(1,2).equals("="))
                                    {
                                        if (!(propertyValueInt >= filterValueInt))
                                        {
                                            return false;
                                        }
                                    }
                                    else if (!(propertyValueInt > filterValueInt))
                                    {
                                        return false;
                                    }
                                }
                                if (valueString.substring(0,1).equals("<")) // Fork for < and <= operations.
                                {
                                    if (valueString.substring(1,2).equals("="))
                                    {
                                        if (!(propertyValueInt <= filterValueInt))
                                        {
                                            return false;
                                        }
                                    }
                                    else if (!(propertyValueInt < filterValueInt))
                                    {
                                        return false;
                                    }
                                }
                            }
                            else if (!propertyValue.equals(value)) {
                                return false;
                            }
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    return false;
                }
            }
            return true;
        }

    }

	static class RandomCourseResult {
		public GradeBar course;
		public String dirString;

		public RandomCourseResult(GradeBar course, String dirString) {
			this.course = course;
			this.dirString = dirString;
		}
	}

	/**
	 * 選曲バー内のスコアデータ等を読み込むためのスレッド
	 */
	static class BarContentsLoaderThread extends Thread {

		private final MusicSelector select;
		/**
		 * データ読み込み対象の選曲バー
		 */
		private Bar[] bars;
		/**
		 * 読み込み終了フラグ
		 */
		private volatile boolean stop = false;

		public BarContentsLoaderThread(MusicSelector select, Bar[] bar) {
			this.select = select;
			this.bars = bar;
		}

		@Override
		public void run() {
			final MainController main = select.main;
			final PlayerConfig config = select.resource.getPlayerConfig();
			final ScoreDataCache rival = select.getRivalScoreDataCache();
			final String rivalName = rival != null ? select.getRival().getName() : null;

			final SongData[] songs = Stream.of(bars).filter(bar -> bar instanceof SongBar && ((SongBar) bar).existsSong())
					.map(bar -> ((SongBar) bar).getSongData()).toArray(SongData[]::new);
			// loading score
			// TODO collectorを使用してスコアをまとめて取得
			for (Bar bar : bars) {
				if (isStale()) {
					break;
				}
				if (bar instanceof SongBar && ((SongBar) bar).existsSong()) {
					SongData sd = ((SongBar) bar).getSongData();
					if (bar.getScore() == null) {
						ScoreData score = select.getScoreDataCache().readScoreData(sd, config.getLnmode());
						if (isStale()) {
							break;
						}
						bar.setScore(score);
					}
					if (rival != null && bar.getRivalScore() == null) {
						final ScoreData rivalScore = rival.readScoreData(sd, config.getLnmode());
						if(rivalScore != null) {
							rivalScore.setPlayer(rivalName);							
						}
						bar.setRivalScore(rivalScore);
					}
					for(int i = 0;i < MusicSelector.REPLAY;i++) {
						((SongBar) bar).setExistsReplay(i, main.getPlayDataAccessor().existsReplayData(sd.getSha256(), sd.hasUndefinedLongNote(),config.getLnmode(), i));						
					}
				} else if (bar instanceof GradeBar && ((GradeBar)bar).existsAllSongs()) {
					final GradeBar gb = (GradeBar) bar;
					String[] hash = new String[gb.getSongDatas().length];
					boolean ln = false;
					for (int j = 0; j < gb.getSongDatas().length; j++) {
						hash[j] = gb.getSongDatas()[j].getSha256();
						ln |= gb.getSongDatas()[j].hasUndefinedLongNote();
					}
					CourseDataConstraint[] constraint = gb.getCourseData().getConstraint();
					gb.setScore(main.getPlayDataAccessor().readScoreData(hash, ln, config.getLnmode(), 0, constraint));
					gb.setMirrorScore(main.getPlayDataAccessor().readScoreData(hash, ln, config.getLnmode(), 1, constraint));
					gb.setRandomScore(main.getPlayDataAccessor().readScoreData(hash, ln, config.getLnmode(), 2, constraint));
					for(int i = 0;i < MusicSelector.REPLAY;i++) {
						gb.setExistsReplay(i, main.getPlayDataAccessor().existsReplayData(hash, ln ,config.getLnmode(), i, constraint));						
					}
				}

				if (select.resource.getConfig().isFolderlamp()) {
					if (bar instanceof DirectoryBar) {
						((DirectoryBar) bar).updateFolderStatus();
					}
				}
				if (stop) {
					break;
				}
			}
			// loading song information
			final SongInformationAccessor info = main.getInfoDatabase();
			if(info != null) {
				info.getInformation(songs);
			}
			// loading banner
			// loading stagefile
			for (Bar bar : bars) {
				if (bar instanceof SongBar && ((SongBar) bar).existsSong()) {
					final SongBar songbar = (SongBar) bar;
					SongData song = songbar.getSongData();
					try {
						SongResource bannerfile = SongResources.fromPath(Paths.get(song.getPath()))
								.parent().resolve(song.getBanner());
						if (song.getBanner().length() > 0 && bannerfile.exists()) {
							songbar.setBanner(select.getBannerResource().get(bannerfile));
						}
					} catch (Exception e) {
						logger.warn("banner読み込み失敗 : {}", song.getBanner());
					}
					try {
						SongResource stagefilefile = SongResources.fromPath(Paths.get(song.getPath()))
								.parent().resolve(song.getStagefile());
						if (song.getStagefile().length() > 0 && stagefilefile.exists()) {
							songbar.setStagefile(select.getStagefileResource().get(stagefilefile));
						}
					} catch (Exception e) {
						logger.warn("stagefile読み込み失敗 : {}", song.getStagefile());
					}
				}
				if (stop) {
					break;
				}
			}
		}

		/**
		 * データ読み込みを中断する
		 */
		public void stopRunning() {
			stop = true;
		}

		private boolean isStale() {
			return stop || select.getBarManager().loader != this;
		}
	}
}
