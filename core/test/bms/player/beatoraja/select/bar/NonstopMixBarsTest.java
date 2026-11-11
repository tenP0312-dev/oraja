package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.nonstop.MixIrClient;
import bms.player.beatoraja.nonstop.MixJson;
import bms.player.beatoraja.nonstop.MixPlan;
import bms.player.beatoraja.nonstop.MixResult;
import bms.player.beatoraja.nonstop.MixStore;
import bms.player.beatoraja.nonstop.MixTrack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NonstopMixBarsTest {
    @TempDir
    Path directory;

    static MixPlan plan(String name, int songs) {
        MixPlan plan = new MixPlan();
        plan.name = name;
        for (int i = 0; i < songs; i++) {
            MixTrack track = new MixTrack();
            track.sha256 = String.format("%064x", i + 10);
            track.title = "song " + i;
            track.outMs = 60_000L;
            plan.addTrack(i, track);
        }
        return plan;
    }

    private static List<String> keys(List<KeyedFunctionBar> rows) {
        return rows.stream().map(KeyedFunctionBar::getSelectionKey).toList();
    }

    @Test
    void folderListsSavedSetsWithTheirBestLampThenTheIrFolder() throws Exception {
        MixStore store = new MixStore(directory.resolve("nonstopmix"));
        MixPlan older = plan("older", 2);
        MixPlan newer = plan("newer", 3);
        // saving stamps the current time, so the second save must land on a later millisecond
        store.saveSet(older);
        long saved = older.updatedAt;
        while (System.currentTimeMillis() <= saved) {
            Thread.sleep(1);
        }
        store.saveSet(newer);
        MixResult record = new MixResult();
        record.identity = MixJson.playIdentity(older);
        record.clear = 5;
        store.addRecord(record);

        Bar[] children = new NonstopMixFolderBar(null, () -> store).getChildren();
        assertEquals(3, children.length);
        assertEquals("newer", children[0].getTitle(), "newest set first, like the editor's list");
        assertEquals("older", children[1].getTitle());
        assertEquals(0, children[0].getLamp(true));
        assertEquals(5, children[1].getLamp(true), "a set shows its best clear like a course lamp");
        assertEquals(0, children[1].getLamp(false));
        assertInstanceOf(NonstopMixIrFolderBar.class, children[2]);
    }

    @Test
    void emptyFolderStillHasAHintAndTheIrFolder() {
        MixStore store = new MixStore(directory.resolve("nonstopmix"));
        Bar[] children = new NonstopMixFolderBar(null, () -> store).getChildren();
        assertEquals(2, children.length);
        assertInstanceOf(FunctionBar.class, children[0]);
        assertInstanceOf(NonstopMixIrFolderBar.class, children[1]);
    }

    @Test
    void completeSetPlaysWithTheFirstPress() {
        MixPlan set = plan("Night drive", 2);
        List<KeyedFunctionBar> rows = NonstopMixSetBar.rows(set, List.of(), null, null);
        assertEquals(List.of("play", "autoplay", "summary", "best", "track0", "track1"), keys(rows));
        assertTrue(rows.get(0).getTitle().contains("Night drive"), "the play row names the set");
        assertEquals(FunctionBar.STYLE_COURSE, rows.get(0).getDisplayBarType());
        assertEquals(FunctionBar.STYLE_SONG, rows.get(4).getDisplayBarType());
        assertTrue(rows.get(5).getTitle().contains("song 1"));
    }

    @Test
    void missingSongsReplaceThePlayRows() {
        MixPlan set = plan("Night drive", 2);
        List<KeyedFunctionBar> rows = NonstopMixSetBar.rows(set, List.of(set.tracks.get(1)), null, null);
        assertEquals(List.of("play", "summary", "best", "track0", "track1"), keys(rows));
        assertEquals(FunctionBar.STYLE_MISSING, rows.get(0).getDisplayBarType());
        assertEquals(FunctionBar.STYLE_SONG, rows.get(3).getDisplayBarType());
        assertEquals(FunctionBar.STYLE_MISSING, rows.get(4).getDisplayBarType());

        List<KeyedFunctionBar> empty = NonstopMixSetBar.rows(plan("empty", 0), List.of(), null, null);
        assertEquals(List.of("play", "summary", "best"), keys(empty));
        assertEquals(FunctionBar.STYLE_MISSING, empty.get(0).getDisplayBarType());
    }

    @Test
    void bestRowShowsClearAndRate() {
        MixResult best = new MixResult();
        best.clear = 6;
        best.exScore = 900;
        best.maxExScore = 1000;
        String text = NonstopMixSetBar.bestText(best);
        assertTrue(text.contains("HARD"));
        assertTrue(text.contains("900/1000"));
        assertTrue(text.contains("90.0%"));
        assertEquals("FULL COMBO", MixResult.clearName(8));
        assertEquals("NORMAL", NonstopMixSetBar.judgeName(MixPlan.JudgeRule.NORMAL));
        assertEquals("VERY EASY", NonstopMixSetBar.judgeName(MixPlan.JudgeRule.VERY_EASY));
        assertTrue(NonstopMixSetBar.summary(plan("x", 2)).startsWith("2"));
    }

    @Test
    void randomBranchesDrawnWhileLoadingAreKept() {
        MixPlan stored = plan("x", 2);
        MixPlan prepared = stored.copy();
        prepared.tracks.get(1).random = new int[] {2};
        assertTrue(NonstopMixSetBar.copyRandoms(stored, prepared));
        assertArrayEquals(new int[] {2}, stored.tracks.get(1).random);
        assertNull(stored.tracks.get(0).random);
        assertFalse(NonstopMixSetBar.copyRandoms(stored, prepared), "already drawn branches are not replaced");
        assertFalse(NonstopMixSetBar.copyRandoms(stored, plan("y", 3)));
    }

    @Test
    void unsupportedIrShowsOnlyItsMessage() {
        List<Bar> rows = NonstopMixIrFolderBar.children(null, NonstopMixIrFolderBar.State.FAILED, List.of(),
                "This IR server does not support NONSTOP MIX yet");
        assertEquals(1, rows.size());
        assertEquals("This IR server does not support NONSTOP MIX yet", rows.get(0).getTitle());

        assertEquals(1, NonstopMixIrFolderBar.children(null, NonstopMixIrFolderBar.State.LOADING, List.of(), "").size());
        assertEquals(1, NonstopMixIrFolderBar.children(null, NonstopMixIrFolderBar.State.DONE, List.of(), "").size());

        MixIrClient.Listing listing = new MixIrClient.Listing();
        listing.id = "set-1";
        listing.name = "Shared";
        listing.author = "dj";
        List<Bar> sets = NonstopMixIrFolderBar.children(null, NonstopMixIrFolderBar.State.DONE, List.of(listing), "");
        assertEquals(1, sets.size());
        assertInstanceOf(NonstopMixIrSetBar.class, sets.get(0));
        assertEquals("Shared / dj", sets.get(0).getTitle());
    }

    @Test
    void downloadedIrSetIsReusedAtTheSameRevision() throws Exception {
        MixStore store = new MixStore(directory.resolve("nonstopmix"));
        MixPlan downloaded = plan("Shared", 1);
        downloaded.irId = "set-1";
        downloaded.irRevision = 2;
        store.saveSet(downloaded);
        MixIrClient.Listing listing = new MixIrClient.Listing();
        listing.id = "set-1";
        listing.revision = 2;
        assertEquals(downloaded.id, NonstopMixIrSetBar.saved(store, listing).id);
        listing.revision = 3;
        assertNull(NonstopMixIrSetBar.saved(store, listing), "a new revision is downloaded again");
    }
}
