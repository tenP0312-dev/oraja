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
    void combinesDirectSongsAndChildFolders() {
        SongData song = song("Direct song", "a".repeat(64));
        FolderData folder = folder("Archive", File.separator + "songs" + File.separator + "pack.rar"
                + File.separator);
        String rootpath = File.separator + "working";

        Bar[] children = FolderBar.createChildren(
                null,
                new SongData[]{song},
                new FolderData[]{folder},
                rootpath
        );

        assertEquals(2, children.length);
        assertTrue(Arrays.stream(children).anyMatch(
                child -> child instanceof SongBar && child.getTitle().equals("Direct song")));
        assertTrue(Arrays.stream(children).anyMatch(child -> {
            if (!(child instanceof FolderBar folderBar) || !child.getTitle().equals("Archive")) {
                return false;
            }
            String path = folder.getPath().substring(0, folder.getPath().length() - 1);
            return folderBar.getCRC().equals(SongUtils.crc32(path, new String[0], rootpath));
        }));
    }

    @Test
    void preservesSongOnlyAndFolderOnlyContents() {
        SongData song = song("Only song", "b".repeat(64));
        FolderData folder = folder("Only folder", File.separator + "songs" + File.separator + "child"
                + File.separator);

        Bar[] songOnly = FolderBar.createChildren(
                null, new SongData[]{song}, FolderData.EMPTY, File.separator + "working");
        Bar[] folderOnly = FolderBar.createChildren(
                null, SongData.EMPTY, new FolderData[]{folder}, File.separator + "working");

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
