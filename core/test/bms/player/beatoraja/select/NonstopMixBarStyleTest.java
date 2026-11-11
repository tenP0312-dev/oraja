package bms.player.beatoraja.select;

import bms.player.beatoraja.nonstop.MixIrClient;
import bms.player.beatoraja.nonstop.MixPlan;
import bms.player.beatoraja.select.bar.NonstopMixFolderBar;
import bms.player.beatoraja.select.bar.NonstopMixIrFolderBar;
import bms.player.beatoraja.select.bar.NonstopMixIrSetBar;
import bms.player.beatoraja.select.bar.NonstopMixSetBar;
import bms.player.beatoraja.select.bar.SongBar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NonstopMixBarStyleTest {
    @Test
    void foldersUseTheFolderBarAndSetsTheCourseBar() {
        assertEquals(1, BarRenderer.nonstopMixBarStyle(new NonstopMixFolderBar(null)));
        assertEquals(1, BarRenderer.nonstopMixBarStyle(new NonstopMixIrFolderBar(null)));
        assertEquals(3, BarRenderer.nonstopMixBarStyle(new NonstopMixSetBar(null, new MixPlan(), null)));
        assertEquals(3, BarRenderer.nonstopMixBarStyle(new NonstopMixIrSetBar(null, new MixIrClient.Listing())));
        assertEquals(-1, BarRenderer.nonstopMixBarStyle(new SongBar(new bms.player.beatoraja.song.SongData())));
    }
}
