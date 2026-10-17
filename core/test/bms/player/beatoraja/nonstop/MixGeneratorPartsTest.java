package bms.player.beatoraja.nonstop;

import bms.model.BMSModel;
import bms.model.Mode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MixGeneratorPartsTest {

    static MixSequencer.Candidate song(String title, String genre, double bpm, int level) {
        return new MixSequencer.Candidate(String.format("%064x", Math.abs(title.hashCode())), "", title,
                "artist " + title, genre, bpm, true, 120_000L, 800, level, level, "/songs/" + title + "/chart.bms");
    }

    static MixSequencer.Candidate chart(String title, String artist, String folder, double difficulty) {
        return new MixSequencer.Candidate(String.format("%064x", Math.abs((title + folder + difficulty).hashCode())), "",
                title, artist, "TECHNO", 140, true, 120_000L, 1500, 12, difficulty, folder + "/" + title + ".bms");
    }

    static MixSequencer.Candidate genreChart(String title, String genre, double bpm, double difficulty) {
        return new MixSequencer.Candidate(String.format("%064x", Math.abs(title.hashCode())), "", title,
                "artist " + title, genre, bpm, true, 120_000L, 1500, 12, difficulty, "/songs/" + title + "/c.bms");
    }

    @Test
    void differentSeedsGiveDifferentSetsWithABlankGenre() {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        String[] genres = {"TRANCE", "HOUSE", "TECHNO", "J-CORE", "ARTCORE"};
        for (int i = 0; i < 60; i++) {
            pool.add(genreChart("song " + i, genres[i % genres.length], 128 + (i * 7) % 50, 13 + i % 10));
        }
        java.util.Set<List<MixSequencer.Candidate>> sets = new java.util.HashSet<>();
        java.util.Set<MixSequencer.Candidate> openers = new java.util.HashSet<>();
        for (long seed = 1; seed <= 8; seed++) {
            MixSequencer.Settings settings = new MixSequencer.Settings();
            settings.targetMs = 12 * 60_000L;
            settings.seed = seed;
            List<MixSequencer.Candidate> set = MixSequencer.sequence(pool, List.of(), null, settings);
            assertFalse(set.isEmpty());
            // same seed, same set
            assertEquals(set, MixSequencer.sequence(pool, List.of(), null, settings));
            sets.add(set);
            openers.add(set.get(0));
        }
        assertTrue(sets.size() >= 6, "distinct sets: " + sets.size());
        assertTrue(openers.size() >= 4, "distinct openers: " + openers.size());
    }

    @Test
    void songsOfRecentSetsGiveWay() {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            pool.add(genreChart("song " + i, "HOUSE", 126, 20));
        }
        MixSequencer.Settings settings = new MixSequencer.Settings();
        settings.targetMs = 8 * 60_000L;
        settings.seed = 5;
        List<MixSequencer.Candidate> first = MixSequencer.sequence(pool, List.of(), null, settings);
        MixPlan previous = new MixPlan();
        for (MixSequencer.Candidate song : first) {
            MixTrack track = new MixTrack();
            track.title = song.title();
            track.artist = song.artist();
            previous.tracks.add(track);
        }
        settings.recentSongs = MixGenerator.recentSongs(List.of(previous), List.of());
        List<MixSequencer.Candidate> second = MixSequencer.sequence(pool, List.of(), null, settings);
        long repeated = second.stream().filter(first::contains).count();
        assertTrue(repeated <= 1, "repeated " + repeated + " of " + second.size());
        // a locked song stays even when it was in the recent set
        MixSequencer.Fixed locked = new MixSequencer.Fixed(0, first.get(0));
        assertEquals(first.get(0), MixSequencer.sequence(pool, List.of(locked), null, settings).get(0));
    }

    @Test
    void genreFamilyFiltersAndSteersTheSet() {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        String[] dnb = {"Drum'n'Bass", "DnB", "ドラムンベース", "Liquid Funk", "Jungle", "DRUM & BASS"};
        for (int i = 0; i < 18; i++) {
            pool.add(genreChart("dnb " + i, dnb[i % dnb.length], 172, 20));
            pool.add(genreChart("trance " + i, "TRANCE", 172, 20));
        }
        MixSequencer.Settings settings = new MixSequencer.Settings();
        settings.genreRule = MixSequencer.GenreRule.STRICT;
        settings.genreFamily = MixGenreFamily.DRUM_N_BASS;
        settings.targetMs = 10 * 60_000L;
        settings.seed = 9;
        List<MixSequencer.Candidate> set = MixSequencer.sequence(pool, List.of(), null, settings);
        assertTrue(set.size() >= 4);
        assertTrue(set.stream().allMatch(c -> c.title().startsWith("dnb")), set.toString());
        settings.genreRule = MixSequencer.GenreRule.SIMILAR;
        set = MixSequencer.sequence(pool, List.of(), null, settings);
        assertTrue(set.stream().filter(c -> c.title().startsWith("dnb")).count() * 2 > set.size(), set.toString());
    }

    @Test
    void sequencerUsesOneChartPerSong() {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        // four charts of one song in one folder, as the operator saw with AIR
        for (String chart : new String[] {"[NORMAL]", "[HYPER]", "[ANOTHER]", "[INSANE]"}) {
            pool.add(chart("AIR " + chart, "artist / obj:" + chart, "/songs/air", 20));
        }
        // the same song in another folder (package copy) is caught by title and artist
        pool.add(chart("ＡＩＲ -LEGGENDARIA-", "Artist", "/pack/air-copy", 20));
        for (int i = 0; i < 12; i++) {
            pool.add(chart("other " + i, "someone " + i, "/songs/other" + i, 20));
        }
        MixSequencer.Settings settings = new MixSequencer.Settings();
        settings.targetMs = 20 * 60_000L;
        settings.curve = MixSequencer.Curve.FLAT;
        for (long seed = 0; seed < 8; seed++) {
            settings.seed = seed;
            List<MixSequencer.Candidate> set = MixSequencer.sequence(pool, List.of(), null, settings);
            long air = set.stream().filter(c -> c.title().contains("AIR")).count();
            assertTrue(air <= 1, "seed " + seed + ": " + set);
        }
        // a locked chart of the song keeps its place and keeps the other charts out
        MixSequencer.Candidate locked = chart("AIR [SPECIAL]", "artist", "/elsewhere/air", 20);
        settings.seed = 3;
        List<MixSequencer.Candidate> set = MixSequencer.sequence(pool,
                List.of(new MixSequencer.Fixed(1, locked)), null, settings);
        assertEquals(locked, set.get(1));
        assertEquals(1, set.stream().filter(c -> c.title().contains("AIR")).count(), set.toString());
    }

    @Test
    void sameFolderIsTheSameSongEvenWithDifferentTitles() {
        MixSequencer.Candidate a = chart("Song A", "x", "/songs/pack", 15);
        MixSequencer.Candidate b = chart("Totally different", "y", "/songs/pack", 15);
        MixSequencer.Candidate c = chart("Song C", "z", "/songs/c", 15);
        MixSequencer.Settings settings = new MixSequencer.Settings();
        settings.targetMs = 60 * 60_000L;
        List<MixSequencer.Candidate> set = MixSequencer.sequence(List.of(a, b, c), List.of(), null, settings);
        assertEquals(2, set.size(), set.toString());
        assertTrue(set.contains(c));
        // reordering keeps every song, duplicates included
        settings.oneChartPerSong = false;
        settings.maxTracks = 3;
        assertEquals(3, MixSequencer.sequence(List.of(a, b, c), List.of(), null, settings).size());
    }

    @Test
    void songKeyIgnoresChartDecorations() {
        assertEquals(chart("AIR [ANOTHER]", "Composer / obj: A", "/a", 1).songKey(),
                chart("ＡＩＲ (7keys)", "composer obj.B", "/b", 1).songKey());
        assertNotEquals(chart("AIR", "x", "/a", 1).songKey(), chart("AIR", "y", "/a", 1).songKey());
        assertFalse(chart("[ ]", "x", "/a", 1).songKey().startsWith("|"));
    }

    @Test
    void sequencerFollowsTheDifficultyAxisAndRange() {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            // #PLAYLEVEL says 12 for all of them; only the axis tells them apart
            pool.add(chart("song " + i, "artist " + i, "/songs/" + i, 1 + i % 37));
        }
        MixSequencer.Settings settings = new MixSequencer.Settings();
        settings.targetMs = 16 * 60_000L;
        settings.curve = MixSequencer.Curve.RISING;
        settings.minDifficulty = MixDifficulty.Scale.INSANE.lower(5);
        settings.maxDifficulty = MixDifficulty.Scale.INSANE.upper(15);
        settings.seed = 7;
        List<MixSequencer.Candidate> set = MixSequencer.sequence(pool, List.of(), null, settings);
        assertTrue(set.size() >= 6, set.toString());
        assertTrue(set.stream().allMatch(c -> c.difficulty() >= 17 && c.difficulty() <= 27), set.toString());
        double firstHalf = set.subList(0, set.size() / 2).stream().mapToDouble(MixSequencer.Candidate::difficulty).average().orElse(0);
        double secondHalf = set.subList(set.size() / 2, set.size()).stream().mapToDouble(MixSequencer.Candidate::difficulty).average().orElse(0);
        assertTrue(secondHalf > firstHalf + 3, firstHalf + " -> " + secondHalf);
    }

    @Test
    void sequencerFillsTheTargetLengthWithoutRepeatingSongs() {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            pool.add(song("house " + i, "HOUSE", 124 + (i % 5), 1 + i % 12));
        }
        MixSequencer.Settings settings = new MixSequencer.Settings();
        settings.targetMs = 10 * 60_000L;
        settings.seed = 42;
        List<MixSequencer.Candidate> set = MixSequencer.sequence(pool, List.of(), null, settings);
        assertTrue(set.size() >= 5 && set.size() <= 7, "about ten minutes of two-minute songs: " + set.size());
        assertEquals(set.size(), set.stream().map(MixSequencer.Candidate::songKey).distinct().count());
        // same seed, same set
        assertEquals(set, MixSequencer.sequence(pool, List.of(), null, settings));
    }

    @Test
    void strictGenreNeverLeavesTheGenreAndCloseTempoIsPreferred() {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            pool.add(song("trance " + i, "Trance", 138 + i % 3, 8));
            pool.add(song("dnb " + i, "Drum'n'Bass", 174, 8));
        }
        MixSequencer.Settings settings = new MixSequencer.Settings();
        settings.genreRule = MixSequencer.GenreRule.STRICT;
        settings.genre = "TRANCE";
        settings.targetMs = 8 * 60_000L;
        settings.seed = 1;
        List<MixSequencer.Candidate> set = MixSequencer.sequence(pool, List.of(), null, settings);
        assertFalse(set.isEmpty());
        assertTrue(set.stream().allMatch(c -> c.genre().equals("Trance")));
    }

    @Test
    void fixedSongsStayWhereTheyWere() {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            pool.add(song("song " + i, "TECHNO", 130, 5));
        }
        MixSequencer.Candidate fixed = song("anthem", "TECHNO", 130, 5);
        MixSequencer.Settings settings = new MixSequencer.Settings();
        settings.targetMs = 6 * 60_000L;
        List<MixSequencer.Candidate> set = MixSequencer.sequence(pool,
                List.of(new MixSequencer.Fixed(2, fixed)), null, settings);
        assertEquals(fixed, set.get(2));
    }

    @Test
    void tempoCostTreatsHalfTimeAsCompatible() {
        MixSequencer.Settings settings = new MixSequencer.Settings();
        assertTrue(MixSequencer.tempoCost(87, 174, settings) < MixSequencer.tempoCost(150, 174, settings));
        assertTrue(MixSequencer.tempoCost(128, 129, settings) < 0.2);
        assertEquals(0.0, MixSequencer.genreCost("HOUSE", "house"));
        assertTrue(MixSequencer.genreCost("PROGRESSIVE HOUSE", "HOUSE") < MixSequencer.genreCost("HOUSE", "J-POP"));
    }

    @Test
    void plannerBlendsCloseTemposOnTheBarGridAndCutsFarOnes() {
        BMSModel a = MixCompilerTest.chart(128, "");
        BMSModel b = MixCompilerTest.chart(128, "");
        BMSModel c = MixCompilerTest.chart(170, "");
        MixPlan plan = MixCompilerTest.plan(3);
        plan.mode = Mode.BEAT_5K;
        List<MixTrackAnalysis> analyses = List.of(MixTrackAnalysis.of(a), MixTrackAnalysis.of(b), MixTrackAnalysis.of(c));
        MixJoinPlanner.Settings settings = new MixJoinPlanner.Settings();
        settings.blendBars = 4;
        MixJoinPlanner.plan(plan, analyses, settings);

        MixTransition first = plan.transitions.get(0);
        assertEquals(MixTransition.Type.BLEND, first.type);
        long bar = Math.round(240_000.0 / 128);
        assertEquals(0, first.overlapMs % bar, "the blend is a whole number of bars");
        assertTrue(first.handoffMs > 0 && first.handoffMs < first.overlapMs);
        assertNotEquals(MixTransition.Type.BLEND, plan.transitions.get(1).type);

        CompiledMix mix = MixCompiler.compile(plan, List.of(
                MixCompilerTest.source(a, "a"), MixCompilerTest.source(b, "b"), MixCompilerTest.source(c, "c")));
        assertFalse(mix.hasErrors(), mix.issues().toString());
        // the incoming song's first bar lands on a bar line of the outgoing song
        long startB = mix.layout().start()[1];
        long aBarPhase = (startB - mix.layout().offset()[0] - analyses.get(0).bars[0]) % bar;
        assertTrue(aBarPhase <= 2 || bar - aBarPhase <= 2, "phase " + aBarPhase);
    }

    @Test
    void handOffAvoidsLongNotes() {
        // long notes on lane 2 in bars 14 and 15 of the outgoing song
        BMSModel a = MixCompilerTest.chart(120, "#01452:03000300\n#01552:03000300\n");
        BMSModel b = MixCompilerTest.chart(120, "");
        MixPlan plan = MixCompilerTest.plan(2);
        List<MixTrackAnalysis> analyses = List.of(MixTrackAnalysis.of(a), MixTrackAnalysis.of(b));
        MixJoinPlanner.Settings settings = new MixJoinPlanner.Settings();
        MixJoinPlanner.plan(plan, analyses, settings);
        CompiledMix mix = MixCompiler.compile(plan, List.of(MixCompilerTest.source(a, "a"), MixCompilerTest.source(b, "b")));
        assertFalse(mix.hasErrors(), mix.issues().toString() + " join " + plan.transitions.get(0).type);
    }
}
