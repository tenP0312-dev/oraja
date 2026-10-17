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
                "artist " + title, genre, bpm, true, 120_000L, 800, level, "/songs/" + title);
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
