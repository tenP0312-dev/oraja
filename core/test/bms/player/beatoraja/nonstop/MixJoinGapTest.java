package bms.player.beatoraja.nonstop;

import bms.model.BMSDecoder;
import bms.model.BMSModel;
import bms.model.TimeLine;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Fade and cut joins keep the stretch without notes between songs short (Issue #553). */
class MixJoinGapTest {

    /** Background sound in bars 1..{@code lastBgmBar}, notes only in bars {@code firstNoteBar..lastNoteBar}. */
    static BMSModel chart(int bpm, int firstNoteBar, int lastNoteBar, int lastBgmBar, String extra) {
        StringBuilder text = new StringBuilder("#TITLE G" + bpm + "\n#BPM " + bpm + "\n#WAV01 key.wav\n#WAV02 bgm.wav\n#WAV03 ln.wav\n");
        for (int bar = 1; bar <= lastBgmBar; bar++) {
            text.append(String.format("#%03d01:02\n", bar));
        }
        for (int bar = firstNoteBar; bar <= lastNoteBar; bar++) {
            text.append(String.format("#%03d11:01010101\n", bar));
        }
        text.append(extra);
        BMSModel model = new BMSDecoder().decode(text.toString().getBytes(Charset.forName("MS932")), false, null);
        assertNotNull(model);
        return model;
    }

    static long barMs(int bpm) {
        return Math.round(240_000.0 / bpm);
    }

    /** Longest stretch between neighbouring playable notes of the compiled set, from the first note on. */
    static long longestNoteGap(BMSModel model) {
        List<Long> times = new ArrayList<>();
        for (TimeLine tl : model.getAllTimeLines()) {
            for (int lane = 0; lane < model.getMode().key; lane++) {
                if (tl.getNote(lane) != null) {
                    times.add(tl.getMilliTime());
                    break;
                }
            }
        }
        long longest = 0;
        for (int i = 1; i < times.size(); i++) {
            longest = Math.max(longest, times.get(i) - times.get(i - 1));
        }
        return longest;
    }

    private static CompiledMix planAndCompile(MixPlan plan, BMSModel a, BMSModel b) {
        List<MixTrackAnalysis> analyses = List.of(MixTrackAnalysis.of(a), MixTrackAnalysis.of(b));
        MixJoinPlanner.plan(plan, analyses, new MixJoinPlanner.Settings());
        CompiledMix mix = MixCompiler.compile(plan,
                List.of(MixCompilerTest.source(a, "a"), MixCompilerTest.source(b, "b")));
        assertFalse(mix.hasErrors(), mix.issues().toString());
        return mix;
    }

    @Test
    void cutDropsTheQuietOutroAndIntro() {
        // outgoing: notes end in bar 8, background runs to bar 20; incoming: notes start in bar 9
        BMSModel a = chart(120, 2, 8, 20, "");
        BMSModel b = chart(150, 9, 16, 16, "");
        MixPlan plan = MixCompilerTest.plan(2);
        CompiledMix mix = planAndCompile(plan, a, b);

        assertEquals(MixTransition.Type.CUT, plan.transitions.get(0).type);
        MixTrackAnalysis aa = MixTrackAnalysis.of(a);
        MixTrackAnalysis ab = MixTrackAnalysis.of(b);
        assertEquals(9 * barMs(120), plan.tracks.get(0).outMs, "stops at the bar after the last note");
        assertEquals(9 * barMs(150), plan.tracks.get(1).inMs, "starts at the bar of the first note");
        assertEquals(ab.firstNoteMs, plan.tracks.get(1).inMs);
        assertTrue(plan.tracks.get(0).outMs > aa.lastNoteMs);

        assertEquals(MixCompilerTest.playable(a, 0, Long.MAX_VALUE) + MixCompilerTest.playable(b, 0, Long.MAX_VALUE),
                MixCompilerTest.playable(mix.model(), 0, Long.MAX_VALUE), "no note is lost");
        assertTrue(longestNoteGap(mix.model()) <= 2 * barMs(120), "gap " + longestNoteGap(mix.model()));
    }

    @Test
    void fadeKeepsTheGapWithinTwoBars() {
        // tempo 120 -> 128 is too far to blend but close enough to fade
        BMSModel a = chart(120, 1, 6, 18, "");
        BMSModel b = chart(128, 7, 14, 14, "");
        MixPlan plan = MixCompilerTest.plan(2);
        CompiledMix mix = planAndCompile(plan, a, b);

        MixTransition join = plan.transitions.get(0);
        assertEquals(MixTransition.Type.FADE_OUT, join.type);
        assertEquals(0, join.gapMs);
        assertEquals(7 * barMs(120), plan.tracks.get(0).outMs);
        assertEquals(7 * barMs(128), plan.tracks.get(1).inMs);
        assertTrue(longestNoteGap(mix.model()) <= 2 * barMs(120), "gap " + longestNoteGap(mix.model()));
    }

    @Test
    void incomingKeepsALeadOfLessThanOneBarAndTheBarGrid() {
        // first note on the third beat of bar 5
        BMSModel a = chart(120, 1, 6, 6, "");
        BMSModel b = chart(150, 6, 10, 10, "#00511:00000100\n");
        MixPlan plan = MixCompilerTest.plan(2);
        planAndCompile(plan, a, b);

        MixTrackAnalysis ab = MixTrackAnalysis.of(b);
        long in = plan.tracks.get(1).inMs;
        assertEquals(5 * barMs(150), in, "starts on the bar line that holds the first note");
        assertTrue(ab.firstNoteMs - in >= 0 && ab.firstNoteMs - in < barMs(150));
    }

    @Test
    void outgoingWaitsForTheLastLongNoteTail() {
        // a long note starts in bar 6 and ends in bar 8; the last ordinary note is in bar 6
        BMSModel a = chart(120, 1, 6, 16, "#00652:00000300\n#00852:03000000\n");
        BMSModel b = chart(150, 1, 8, 8, "");
        MixPlan plan = MixCompilerTest.plan(2);
        CompiledMix mix = planAndCompile(plan, a, b);

        assertEquals(9 * barMs(120), plan.tracks.get(0).outMs, "the bar after the long-note end");
        assertEquals(MixCompilerTest.playable(a, 0, Long.MAX_VALUE) + MixCompilerTest.playable(b, 0, Long.MAX_VALUE),
                MixCompilerTest.playable(mix.model(), 0, Long.MAX_VALUE));
    }

    @Test
    void lockedRangesAndBlendsAreLeftAlone() {
        BMSModel a = chart(120, 2, 8, 20, "");
        BMSModel b = chart(150, 9, 16, 16, "");
        MixPlan plan = MixCompilerTest.plan(2);
        plan.tracks.get(0).lockRange = true;
        plan.tracks.get(0).outMs = 15 * barMs(120);
        plan.tracks.get(1).lockRange = true;
        plan.tracks.get(1).inMs = barMs(150);
        planAndCompile(plan, a, b);
        assertEquals(15 * barMs(120), plan.tracks.get(0).outMs);
        assertEquals(barMs(150), plan.tracks.get(1).inMs);

        // same tempo: blended, and the incoming song still starts at its first bar
        BMSModel c = chart(120, 2, 16, 16, "");
        BMSModel d = chart(120, 4, 16, 16, "");
        MixPlan blend = MixCompilerTest.plan(2);
        planAndCompile(blend, c, d);
        assertEquals(MixTransition.Type.BLEND, blend.transitions.get(0).type);
        assertEquals(MixTrackAnalysis.of(d).bars[0], blend.tracks.get(1).inMs);
    }
}
