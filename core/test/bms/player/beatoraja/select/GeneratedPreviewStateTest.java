package bms.player.beatoraja.select;

import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedPreviewStateTest {

    @Test
    void onlyTheNewestSelectionMayPublishAResult() {
        GeneratedPreviewRequestTracker tracker = new GeneratedPreviewRequestTracker();
        SongData first = new SongData();
        SongData second = new SongData();

        long firstVersion = tracker.select(first);
        long secondVersion = tracker.select(second);

        assertFalse(tracker.isCurrent(first, firstVersion));
        assertTrue(tracker.isCurrent(second, secondVersion));
        tracker.clear();
        assertFalse(tracker.isCurrent(second, secondVersion));
    }

    @Test
    void cacheIsBoundedAndRecentlyReadEntriesStayResident() {
        GeneratedPreviewCache<String, String> cache = new GeneratedPreviewCache<>(2);
        cache.put("one", "1");
        cache.put("two", "2");
        cache.get("one");
        cache.put("three", "3");

        assertTrue(cache.contains("one"));
        assertFalse(cache.contains("two"));
        assertTrue(cache.contains("three"));
        assertTrue(cache.size() == 2);
    }
}
