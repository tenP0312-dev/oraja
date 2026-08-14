package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.song.FolderData;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.song.SongUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderBarTest {
    @Test
    void flattensArchiveSongsBesideDirectSongsAndHidesContainerFolders() {
        SongData directSong = song("Direct song", "a".repeat(64));
        SongData archiveSong = song("Archive song", "b".repeat(64));
        FolderData archive = folder("Archive", File.separator + "songs" + File.separator
                + "pack.rar!-Pack" + File.separator);
        FolderData ordinary = folder("Ordinary song folder", File.separator + "songs" + File.separator
                + "ordinary" + File.separator);
        String rootpath = File.separator + "working";

        String[] archiveParents = FolderBar.archiveParentCrcs(
                new FolderData[]{archive, ordinary}, rootpath);
        Bar[] children = FolderBar.createChildren(
                null,
                new SongData[]{directSong},
                new SongData[]{archiveSong},
                new FolderData[]{archive, ordinary},
                rootpath
        );

        assertEquals(1, archiveParents.length);
        String archivePath = archive.getPath().substring(0, archive.getPath().length() - 1);
        assertEquals(SongUtils.crc32(archivePath, new String[0], rootpath), archiveParents[0]);
        assertEquals(2, children.length);
        assertTrue(Arrays.stream(children).allMatch(SongBar.class::isInstance));
        assertTrue(Arrays.stream(children).anyMatch(child -> child.getTitle().equals("Direct song")));
        assertTrue(Arrays.stream(children).anyMatch(child -> child.getTitle().equals("Archive song")));
    }

    @Test
    void flattensArchiveSongsWhenTheParentHasNoDirectSongs() {
        SongData archiveSong = song("Archive only song", "c".repeat(64));
        FolderData archive = folder("Archive", File.separator + "songs" + File.separator
                + "pack.zip!" + File.separator);

        Bar[] children = FolderBar.createChildren(
                null,
                SongData.EMPTY,
                new SongData[]{archiveSong},
                new FolderData[]{archive},
                File.separator + "working"
        );

        assertEquals(1, children.length);
        assertTrue(children[0] instanceof SongBar);
        assertEquals("Archive only song", children[0].getTitle());
    }

    @Test
    void preservesEstablishedSongOnlyAndFolderOnlyContents() {
        SongData song = song("Only song", "d".repeat(64));
        FolderData folder = folder("Only folder", File.separator + "songs" + File.separator + "child"
                + File.separator);

        Bar[] songOnly = FolderBar.createChildren(
                null, new SongData[]{song}, SongData.EMPTY, new FolderData[]{folder}, File.separator + "working");
        Bar[] folderOnly = FolderBar.createChildren(
                null, SongData.EMPTY, SongData.EMPTY, new FolderData[]{folder}, File.separator + "working");

        assertEquals(1, songOnly.length);
        assertTrue(songOnly[0] instanceof SongBar);
        assertEquals(1, folderOnly.length);
        assertTrue(folderOnly[0] instanceof FolderBar);
    }

    private static SongData song(String title, String sha256) {
        SongData song = new SongData();
        song.setTitle(title);
        song.setSha256(sha256);
        return song;
    }

    private static FolderData folder(String title, String path) {
        FolderData folder = new FolderData();
        folder.setTitle(title);
        folder.setPath(path);
        return folder;
    }
}
