package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.song.*;
import bms.player.beatoraja.song.archive.SongArchives;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * ファイルシステムと連動したフォルダバー。
 *
 * @author exch
 */
public class FolderBar extends DirectoryBar {

    private final FolderData folder;
    private final String crc;

    public FolderBar(MusicSelector selector, FolderData folder, String crc) {
        super(selector);
        this.folder = folder;
        this.crc = crc;
    }

    public final FolderData getFolderData() {
        return folder;
    }

    public final String getCRC() {
        return crc;
    }

    @Override
    public final String getTitle() {
        return folder.getTitle();
    }

    @Override
    public Bar[] getChildren() {
        final SongDatabaseAccessor songdb = selector.getSongDatabase();
        final SongData[] songs = songdb.getSongDatas("parent", crc);
        final String rootpath = Paths.get(".").toAbsolutePath().toString();
        final FolderData[] folders = songdb.getFolderDatas("parent", crc);
        final String[] archiveParents = archiveParentCrcs(folders, rootpath);
        final SongData[] archiveSongs = songdb.getSongDatasByParents(archiveParents);
        return createChildren(selector, songs, archiveSongs, folders, rootpath);
    }

    static Bar[] createChildren(
            MusicSelector selector,
            SongData[] songs,
            SongData[] archiveSongs,
            FolderData[] folders,
            String rootpath
    ) {
        SongData[] visibleSongs = Stream.concat(Arrays.stream(songs), Arrays.stream(archiveSongs))
                .toArray(SongData[]::new);
        if (visibleSongs.length > 0) {
            return SongBar.toSongBarArray(visibleSongs);
        }

        return Stream.of(folders)
                .filter(folder -> !isArchiveFolder(folder))
                .map(folder -> new FolderBar(selector, folder, folderCrc(folder, rootpath)))
                .toArray(Bar[]::new);
    }

    static String[] archiveParentCrcs(FolderData[] folders, String rootpath) {
        return Stream.of(folders)
                .filter(FolderBar::isArchiveFolder)
                .map(folder -> folderCrc(folder, rootpath))
                .toArray(String[]::new);
    }

    private static boolean isArchiveFolder(FolderData folder) {
        return SongArchives.isVirtualPath(Paths.get(trimTrailingSeparator(folder.getPath())));
    }

    private static String folderCrc(FolderData folder, String rootpath) {
        return SongUtils.crc32(trimTrailingSeparator(folder.getPath()), new String[0], rootpath);
    }

    private static String trimTrailingSeparator(String path) {
        return path.endsWith(String.valueOf(File.separatorChar))
                ? path.substring(0, path.length() - 1)
                : path;
    }

    public void updateFolderStatus() {
        SongDatabaseAccessor songdb = selector.getSongDatabase();
        String path = folder.getPath();
        if (path.endsWith(String.valueOf(File.separatorChar))) {
            path = path.substring(0, path.length() - 1);
        }
        final String ccrc = SongUtils.crc32(path, new String[0], new File(".").getAbsolutePath());

        updateFolderStatus(songdb.getSongDatas("parent", ccrc));
    }
}
