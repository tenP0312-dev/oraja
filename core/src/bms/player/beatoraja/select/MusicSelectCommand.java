package bms.player.beatoraja.select;

import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.select.bar.*;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.PlayerConfig;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Queue;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

import static bms.player.beatoraja.SystemSoundManager.SoundType.FOLDER_OPEN;
import static bms.player.beatoraja.SystemSoundManager.SoundType.OPTION_CHANGE;

public enum MusicSelectCommand {

	// TODO 最終的には全てEventFactoryへ移動

	RESET_REPLAY(selector -> {
		if (selector.getBarManager().getSelected() instanceof SelectableBar bar) {
			for (int i = 0; i < MusicSelector.REPLAY; i++) {
				if (bar.existsReplay(i)) {
					selector.setSelectedReplay(i);
					return;
				}
			}
		}
		selector.setSelectedReplay(-1);
	}),
	NEXT_REPLAY(selector -> {
		if (selector.getBarManager().getSelected() instanceof SelectableBar bar) {
			for (int i = 1; i < MusicSelector.REPLAY; i++) {
				final int selectedreplay = selector.getSelectedReplay();
				if (bar.existsReplay((i + selectedreplay) % MusicSelector.REPLAY)) {
					selector.setSelectedReplay((i + selectedreplay) % MusicSelector.REPLAY);
					selector.play(OPTION_CHANGE);
					break;
				}
			}
		}
	}),
	PREV_REPLAY(selector -> {
		if (selector.getBarManager().getSelected() instanceof SelectableBar bar) {
			for (int i = 1; i < MusicSelector.REPLAY; i++) {
				final int selectedreplay = selector.getSelectedReplay();
				if (bar.existsReplay((selectedreplay + MusicSelector.REPLAY - i) % MusicSelector.REPLAY)) {
					selector.setSelectedReplay((selectedreplay + MusicSelector.REPLAY - i) % MusicSelector.REPLAY);
					selector.play(OPTION_CHANGE);
					break;
				}
			}
		}
	}),
	/**
	 * 譜面のMD5ハッシュをクリップボードにコピーする
	 */
	COPY_MD5_HASH(selector -> {
		if (selector.getBarManager().getSelected() instanceof SongBar songbar) {
			final SongData song = songbar.getSongData();
			if (song != null) {
				String hash = song.getMd5();
				if (hash != null && hash.length() > 0) {
                    // NOTE: Previous clipboard management is using the java.awt library
                    // which is broken only on macos.
                    // COPY_SHA256_HASH has the same issue
					Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent clipboardContent = new ClipboardContent();
                    clipboardContent.putString(hash);
                    clipboard.setContent(clipboardContent);
					ImGuiNotify.info(String.format("MD5 hash copied: %s", hash));
				}
			}
		}
	}),
	/**
	 * 譜面のMD5ハッシュをクリップボードにコピーする
	 */
	COPY_SHA256_HASH(selector -> {
		if (selector.getBarManager().getSelected() instanceof SongBar songbar) {
			final SongData song = songbar.getSongData();
			if (song != null) {
				String hash = song.getSha256();
				if (hash != null && hash.length() > 0) {
					Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent clipboardContent = new ClipboardContent();
                    clipboardContent.putString(hash);
                    clipboard.setContent(clipboardContent);
					ImGuiNotify.info(String.format("SHA256 hash copied: %s", hash), 2000);
				}
			}
		}
	}),
	DOWNLOAD_IPFS(selector -> {
        Queue<DirectoryBar> dir = selector.getBarManager().getDirectory();
        boolean startdownload = false;
        for (DirectoryBar d : dir) {
            if (d instanceof TableBar) {
                String selecturl = ((TableBar) d).getUrl();
                if (selecturl == null)
                    break;

                Bar current = selector.getBarManager().getSelected();
                if (current instanceof SongBar) {
                    final SongData song = ((SongBar) current).getSongData();
                    if (song != null && song.getIpfs() != null) {
                        selector.main.getMusicDownloadProcessor().start(song);
                        startdownload = true;
                    }
                }

                if (!startdownload) {
					LoggerFactory.getLogger(MusicSelectCommand.class).info("ダウンロードは開始されませんでした。");
                }
                break;
            }
		}
	}),
	DOWNLOAD_HTTP(selector -> {
		Bar current = selector.getBarManager().getSelected();
		if (current instanceof SongBar) {
			final SongData song = ((SongBar) current).getSongData();
			if (song == null) {
				LoggerFactory.getLogger(MusicSelectCommand.class).info("Not a valid song bar? Skipped...");
				return ;
			}
			LoggerFactory.getLogger(MusicSelectCommand.class).info("Missing song md5: {}", song.getMd5());
			if (song.getMd5() != null && !song.getMd5().isEmpty()) {
				selector.main.getHttpDownloadProcessor().submitSongTask(song);
			}
		}
	}),
	DOWNLOAD_COURSE_HTTP(selector -> {
		Bar current = selector.getBarManager().getSelected();
		if (current instanceof GradeBar) {
			final SongData[] songs = ((GradeBar) current).getSongDatas();
            for (SongData song : songs) {
	            LoggerFactory.getLogger(MusicSelectCommand.class).info("Missing song md5: {}", song.getMd5());
                if (song.getMd5() != null && !song.getMd5().isEmpty()) {
                    selector.main.getHttpDownloadProcessor().submitSongTask(song);
                }
            }
		}
	}),
	/**
	 * Grouped song bars expose only their retained chart variants. Separate-row
	 * display keeps the legacy same-folder view. Courses keep their component
	 * chart expansion.
	 */
	SHOW_ALL_CHARTS(selector -> {
		final BarManager bar = selector.getBarManager();
		Bar current = bar.getSelected();
		if (current instanceof SongBar songBar
				&& (bar.getDirectory().size == 0
						|| (!(bar.getDirectory().last() instanceof AllChartsBar)
								&& !(bar.getDirectory().last() instanceof SameFolderBar)))) {
			if (songBar.getDifficultyVariantCount() > 1) {
				bar.updateBar(new AllChartsBar(songBar));
				selector.play(FOLDER_OPEN);
			} else if (songBar.existsSong()
					&& !PlayerConfig.BMSIR_SELECT_DIFFICULTY_DISPLAY_LR2.equals(
							selector.main.getPlayerConfig().getBmsirSelectDifficultyDisplay()
					)) {
				SongData song = songBar.getSongData();
				bar.updateBar(new SameFolderBar(
						selector,
						song.getFullTitle(),
						song.getFolder()
				));
				selector.play(FOLDER_OPEN);
			}
		} else if (current instanceof GradeBar) {
			List<Bar> songbars = Arrays.asList(((GradeBar) current).getSongDatas()).stream().distinct()
					.map(SongBar::new).collect(Collectors.toList());
			bar.updateBar(new ContainerBar(current.getTitle(), songbars.toArray(new Bar[songbars.size()])));
			selector.play(FOLDER_OPEN);
		}
	}),
	/**
	 * Open context menu for the currently selected bar
	 */
    SHOW_CONTEXT_MENU(selector -> {
		final BarManager bar = selector.getBarManager();
		Bar current = bar.getSelected();
        Bar previous = bar.getDirectory().isEmpty() ? null : bar.getDirectory().last();
        boolean alreadyInContextMenu = previous instanceof ContextMenuBar;
        if (current instanceof SongBar) {
            if (!alreadyInContextMenu) {
                bar.updateBar(new ContextMenuBar(selector, (SongBar) current));
                selector.play(FOLDER_OPEN);
            }
            else { selector.selectSong(BMSPlayerMode.PLAY); }
        }
        else if (current instanceof TableBar) {
            if (!alreadyInContextMenu) {
                bar.updateBar(new ContextMenuBar(selector, ((TableBar)current)));
                selector.play(FOLDER_OPEN);
            }
            else if (bar.updateBar(current)) { selector.play(FOLDER_OPEN); }
        }
        else if (current instanceof HashBar && previous instanceof TableBar) {
            // HashBars are also used in other places, but this will open
            // the context menu specific to difficulty table folders
            // Batch downloading is currently the only entry in this menu.
            if (!alreadyInContextMenu && (selector.main.getConfig().isEnableHttp()
                    || selector.main.getConfig().isEnableBmsirBodyDownload())) {
                bar.updateBar(
                    new ContextMenuBar(selector, ((TableBar)previous), ((HashBar)current)));
                selector.play(FOLDER_OPEN);
            }
            else if (bar.updateBar(current)) { selector.play(FOLDER_OPEN); }
        }
    });

    public final Consumer<MusicSelector> function;

	private MusicSelectCommand(Consumer<MusicSelector> function) {
		this.function = function;
	}
}
