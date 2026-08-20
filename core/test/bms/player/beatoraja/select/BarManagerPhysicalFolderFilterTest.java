package bms.player.beatoraja.select;

import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.select.bar.Bar;
import bms.player.beatoraja.select.bar.ContainerBar;
import bms.player.beatoraja.select.bar.FolderBar;
import bms.player.beatoraja.song.FolderData;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BarManagerPhysicalFolderFilterTest {
    @Test
    void defaultOffPreservesEveryRootBar() {
        PlayerConfig config = new PlayerConfig();
        Bar[] roots = roots();

        Bar[] filtered = BarManager.filterRootPhysicalFolders(roots, config);

        assertSame(roots, filtered);
    }

    @Test
    void enabledEmptySelectionHidesEveryPhysicalRootOnly() {
        PlayerConfig config = new PlayerConfig();
        config.setBmsirPhysicalFolderFilterEnabled(true);

        Bar[] filtered = BarManager.filterRootPhysicalFolders(roots(), config);

        assertEquals(1, filtered.length);
        assertEquals("Difficulty tables", filtered[0].getTitle());
    }

    @Test
    void enabledSelectionKeepsOnlyMatchingPhysicalRootsAndVirtualRoots() {
        PlayerConfig config = new PlayerConfig();
        config.setBmsirPhysicalFolderFilterEnabled(true);
        config.setBmsirVisiblePhysicalFolderPaths(new String[]{
                "." + File.separator + "songs-b" + File.separator
        });

        Bar[] filtered = BarManager.filterRootPhysicalFolders(roots(), config);

        assertArrayEquals(
                new String[]{"Songs B", "Difficulty tables"},
                Arrays.stream(filtered).map(Bar::getTitle).toArray(String[]::new)
        );
    }

    private static Bar[] roots() {
        return new Bar[]{
                folder("Songs A", "songs-a" + File.separator),
                folder("Songs B", "songs-b" + File.separator),
                new ContainerBar("Difficulty tables", new Bar[0])
        };
    }

    private static FolderBar folder(String title, String path) {
        FolderData folder = new FolderData();
        folder.setTitle(title);
        folder.setPath(path);
        return new FolderBar(null, folder, title);
    }
}
