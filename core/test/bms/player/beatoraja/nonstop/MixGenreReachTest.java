package bms.player.beatoraja.nonstop;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static bms.player.beatoraja.nonstop.MixGeneratorPartsTest.genreChart;
import static org.junit.jupiter.api.Assertions.*;

/** "Same genre only" widens step by step instead of making a one-song set (#565). */
class MixGenreReachTest {

    private static MixSequencer.Settings strict(String genre) {
        MixSequencer.Settings settings = new MixSequencer.Settings();
        settings.genreRule = MixSequencer.GenreRule.STRICT;
        settings.genre = genre;
        settings.targetMs = 8 * 60_000L;
        settings.seed = 7;
        return settings;
    }

    private static List<MixSequencer.Candidate> pool() {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        pool.add(genreChart("rare", "Brutal Hardcore", 180, 10));
        for (int i = 0; i < 6; i++) {
            pool.add(genreChart("hc" + i, "HARDCORE", 180, 10));
        }
        for (int i = 0; i < 6; i++) {
            pool.add(genreChart("gabber" + i, "GABBER", 180, 10));
        }
        for (int i = 0; i < 6; i++) {
            pool.add(genreChart("pop" + i, "POP", 128, 10));
        }
        return pool;
    }

    @Test
    void enoughSongsKeepTheExactGenre() {
        MixSequencer.Selection selection = MixSequencer.select(pool(), List.of(), null, strict("HARDCORE"));
        assertEquals(MixSequencer.GenreReach.EXACT, selection.reach());
        assertFalse(selection.widened());
        assertTrue(selection.songs().size() > 1);
        assertTrue(selection.songs().stream().allMatch(c -> c.genre().equals("HARDCORE")));
    }

    @Test
    void aRareGenreWidensToGenresSharingAWord() {
        MixSequencer.Selection selection = MixSequencer.select(pool(), List.of(), null, strict("Brutal Hardcore"));
        assertEquals(1, selection.exactMatches());
        assertEquals(MixSequencer.GenreReach.WORD, selection.reach());
        assertTrue(selection.widened());
        assertTrue(selection.songs().size() > 1, "no more one-song sets");
        assertTrue(selection.songs().stream().noneMatch(c -> c.genre().equals("POP")));
    }

    @Test
    void aGenreWithoutCloseNamesWidensToItsFamilyThenEverything() {
        List<MixSequencer.Candidate> pool = pool();
        pool.add(genreChart("speed", "Speedcore", 250, 10));
        MixSequencer.Selection family = MixSequencer.select(pool, List.of(), null, strict("Speedcore"));
        assertEquals(MixSequencer.GenreReach.FAMILY, family.reach(), "speedcore is in the hardcore family");
        assertTrue(family.songs().size() > 1);

        List<MixSequencer.Candidate> lonely = pool();
        lonely.add(genreChart("odd", "Zzyzx", 150, 10));
        MixSequencer.Selection all = MixSequencer.select(lonely, List.of(), null, strict("Zzyzx"));
        assertEquals(MixSequencer.GenreReach.ALL, all.reach());
        assertTrue(all.songs().size() > 1);
    }

    @Test
    void otherRulesNeverReportWidening() {
        MixSequencer.Settings similar = strict("Brutal Hardcore");
        similar.genreRule = MixSequencer.GenreRule.SIMILAR;
        assertFalse(MixSequencer.select(pool(), List.of(), null, similar).widened());
        assertFalse(MixSequencer.select(pool(), List.of(), null, strict("")).widened());
    }

    @Test
    void neededSongsFollowTargetAndRoom() {
        MixSequencer.Settings settings = strict("x");
        settings.targetMs = 10 * 60_000L;
        settings.segmentMs = 0;
        settings.joinMs = 0;
        assertEquals(5, MixSequencer.neededSongs(settings, 0));
        settings.maxTracks = 3;
        assertEquals(2, MixSequencer.neededSongs(settings, 1), "a range replacement needs only its room");
        settings.maxTracks = 2;
        assertEquals(1, MixSequencer.neededSongs(settings, 1));
    }

    @Test
    void countsAreDistinctSongsPerStep() {
        int[] counts = MixGenerator.genreCounts(pool(), "Brutal Hardcore", null);
        assertEquals(1, counts[0]);
        assertEquals(7, counts[1], "Brutal Hardcore plus the six HARDCORE songs");
        assertTrue(counts[2] >= 13, "the hardcore family adds gabber");
        int[] family = MixGenerator.genreCounts(pool(), "", MixGenreFamily.HARDCORE);
        assertEquals(family[0], family[2]);
    }

    @Test
    void widenedNoteNamesTheGenreAndCount() {
        MixSequencer.Selection selection = MixSequencer.select(pool(), List.of(), null, strict("Brutal Hardcore"));
        String note = MixGenerator.widenedNote(selection, null);
        assertTrue(note.contains("Brutal Hardcore"));
        assertTrue(note.contains("1"));
    }
}
