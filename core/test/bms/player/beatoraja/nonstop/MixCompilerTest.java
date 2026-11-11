package bms.player.beatoraja.nonstop;

import bms.model.BMSDecoder;
import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Note;
import bms.model.TimeLine;
import bms.player.beatoraja.song.SongResources;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MixCompilerTest {

    /** 16 bars of quarter notes on key 1 with a BGM hit per bar; one bar = 240000/bpm ms. */
    static BMSModel chart(int bpm, String extra) {
        StringBuilder text = new StringBuilder("#TITLE T" + bpm + "\n#BPM " + bpm + "\n#WAV01 key.wav\n#WAV02 bgm.wav\n#WAV03 ln.wav\n");
        for (int bar = 1; bar <= 16; bar++) {
            text.append(String.format("#%03d11:01010101\n", bar));
            text.append(String.format("#%03d01:02\n", bar));
        }
        text.append(extra);
        BMSModel model = new BMSDecoder().decode(text.toString().getBytes(Charset.forName("MS932")), false, null);
        assertNotNull(model);
        return model;
    }

    static MixCompiler.Source source(BMSModel model, String name) {
        return new MixCompiler.Source(model, SongResources.local(Path.of("/songs", name, name + ".bms")), 1.0);
    }

    static MixPlan plan(int tracks) {
        MixPlan plan = new MixPlan();
        plan.mode = bms.model.Mode.BEAT_5K;
        plan.name = "test";
        for (int i = 0; i < tracks; i++) {
            MixTrack track = new MixTrack();
            track.sha256 = String.format("%064d", i + 1);
            track.title = "song" + i;
            plan.addTrack(i, track);
        }
        return plan;
    }

    static int playable(BMSModel model, long fromMs, long toMs) {
        int count = 0;
        for (TimeLine tl : model.getAllTimeLines()) {
            if (tl.getMilliTime() < fromMs || tl.getMilliTime() >= toMs) {
                continue;
            }
            for (int lane = 0; lane < model.getMode().key; lane++) {
                if (tl.getNote(lane) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void blendHandsTheLanesOverOnceAndMovesTheRestToBackground() {
        BMSModel a = chart(120, "");
        BMSModel b = chart(120, "");
        MixPlan plan = plan(2);
        MixTransition join = plan.transitions.get(0);
        join.type = MixTransition.Type.BLEND;
        join.overlapMs = 8000;  // four bars at 120 BPM
        join.handoffMs = 4000;

        CompiledMix mix = MixCompiler.compile(plan, List.of(source(a, "a"), source(b, "b")));
        assertFalse(mix.hasErrors(), mix.issues().toString());

        long[] ends = mix.chartEnds();
        MixPlan.Layout layout = mix.layout();
        assertEquals(0, layout.start()[0]);
        assertEquals(ends[0] - 8000, layout.start()[1]);
        long handoff = layout.handoff()[0];
        assertEquals(layout.start()[1] + 4000, handoff);

        BMSModel merged = mix.model();
        int total = playable(merged, Long.MIN_VALUE, Long.MAX_VALUE);
        int ownA = playable(a, Long.MIN_VALUE, handoff);
        int ownB = playable(b, handoff - layout.offset()[1], Long.MAX_VALUE);
        assertEquals(ownA + ownB, total);
        assertTrue(total < playable(a, Long.MIN_VALUE, Long.MAX_VALUE) + playable(b, Long.MIN_VALUE, Long.MAX_VALUE));

        // every note of a song is audible exactly once: playable or background
        int sounds = 0;
        for (TimeLine tl : merged.getAllTimeLines()) {
            for (int lane = 0; lane < merged.getMode().key; lane++) {
                if (tl.getNote(lane) != null) {
                    sounds++;
                }
            }
            sounds += tl.getBackGroundNotes().length;
        }
        int originalSounds = soundCount(a) + soundCount(b);
        assertEquals(originalSounds, sounds);

        // the merged time line is ordered and its bar positions never go back
        TimeLine[] timelines = merged.getAllTimeLines();
        for (int i = 1; i < timelines.length; i++) {
            assertTrue(timelines[i].getMicroTime() > timelines[i - 1].getMicroTime());
            assertTrue(timelines[i].getSection() >= timelines[i - 1].getSection());
        }
        assertEquals(merged.getSHA256(), mix.identity());
        assertEquals(mix.identity(), merged.getValues().get(MixCompiler.MODEL_MARKER));
        assertTrue(merged.getWavList()[0].startsWith(MixSongResource.PREFIX + "0/"));
        assertTrue(merged.getTotal() > 0);
    }

    @Test
    void longNoteAcrossTheHandOffIsAnErrorNotASilentFix() {
        BMSModel a = chart(120, "");
        // a long note on key 2 spanning bars 15-16 of song B's start area is irrelevant;
        // put one on song A crossing the hand-off instead
        BMSModel aWithLn = chart(120, "#01452:03000000\n#01552:03000000\n");
        MixPlan plan = plan(2);
        MixTransition join = plan.transitions.get(0);
        join.overlapMs = 8000;
        // song A's last sound is at 33.5 s, so B starts at 25.5 s; the long note is 28 s .. 30 s
        join.handoffMs = 3000;  // hand-off at 28.5 s, inside the long note
        CompiledMix mix = MixCompiler.compile(plan, List.of(source(aWithLn, "a"), source(a, "b")));
        assertTrue(mix.hasErrors());
        assertTrue(mix.issues().stream().anyMatch(issue -> issue.english().contains("hand-off")));

        // the long note is not half-kept: no long note without its pair remains
        for (TimeLine tl : mix.model().getAllTimeLines()) {
            for (int lane = 0; lane < mix.model().getMode().key; lane++) {
                if (tl.getNote(lane) instanceof LongNote ln) {
                    assertNotNull(ln.getPair());
                }
            }
        }

        join.handoffMs = 1000; // hand-off at 26.5 s, before the long note: it stays in the background
        CompiledMix fixed = MixCompiler.compile(plan, List.of(source(aWithLn, "a"), source(a, "b")));
        assertFalse(fixed.hasErrors(), fixed.issues().toString());
    }

    @Test
    void cutHasNoOverlapAndTheOutgoingSongIsReleased() {
        BMSModel a = chart(150, "");
        BMSModel b = chart(120, "");
        MixPlan plan = plan(2);
        plan.tracks.get(0).outMs = 12800; // 8 bars at 150 BPM
        plan.tracks.get(1).inMs = 2000;
        MixTransition join = plan.transitions.get(0);
        join.type = MixTransition.Type.CUT;
        CompiledMix mix = MixCompiler.compile(plan, List.of(source(a, "a"), source(b, "b")));
        assertFalse(mix.hasErrors(), mix.issues().toString());
        MixPlan.Layout layout = mix.layout();
        assertEquals(layout.end()[0], layout.start()[1]);
        assertEquals(layout.start()[1], layout.handoff()[0]);
        assertEquals(1f, mix.gain(0, layout.end()[0] - 1), 1e-6);
        assertEquals(0f, mix.gain(0, layout.end()[0] + MixTransition.CUT_RELEASE_MS), 1e-6);
        assertEquals(1f, mix.gain(1, layout.start()[1]), 1e-6);
        // the tempo follows the song that owns the lanes
        TimeLine[] timelines = mix.model().getAllTimeLines();
        assertEquals(150.0, timelines[0].getBPM(), 1e-9);
        assertEquals(120.0, timelines[timelines.length - 1].getBPM(), 1e-9);
    }

    @Test
    void equalPowerBlendKeepsTheSumOfPowers() {
        BMSModel a = chart(120, "");
        MixPlan plan = plan(2);
        plan.transitions.get(0).overlapMs = 8000;
        CompiledMix mix = MixCompiler.compile(plan, List.of(source(a, "a"), source(a, "b")));
        long middle = mix.layout().start()[1] + 4000;
        double out = mix.gain(0, middle);
        double in = mix.gain(1, middle);
        assertEquals(1.0, out * out + in * in, 1e-6);
        assertEquals(0f, mix.gain(0, mix.layout().end()[0]), 1e-6);
    }

    @Test
    void notesKeepTheirTrackForVolumeShaping() {
        BMSModel a = chart(120, "");
        MixPlan plan = plan(2);
        CompiledMix mix = MixCompiler.compile(plan, List.of(source(a, "a"), source(a, "b")));
        int seen0 = 0;
        int seen1 = 0;
        for (TimeLine tl : mix.model().getAllTimeLines()) {
            for (Note note : tl.getBackGroundNotes()) {
                int track = mix.trackOf(note);
                assertTrue(track == 0 || track == 1);
                if (track == 0) seen0++; else seen1++;
            }
        }
        assertTrue(seen0 > 0 && seen1 > 0);
        assertTrue(mix.sounds(0).length > 0);
    }

    @Test
    void anOverlapLongerThanTheSongNeverReachesBeforeTheSet() {
        BMSModel a = chart(120, "");
        MixPlan plan = plan(2);
        plan.tracks.get(0).inMs = 20000;
        plan.tracks.get(0).outMs = 24000;   // a 4-second part
        plan.transitions.get(0).overlapMs = 30000;
        plan.transitions.get(0).handoffMs = 25000;
        CompiledMix mix = MixCompiler.compile(plan, List.of(source(a, "a"), source(a, "b")));
        MixPlan.Layout layout = mix.layout();
        assertTrue(layout.start()[1] >= layout.start()[0]);
        assertTrue(layout.handoff()[0] <= layout.end()[0]);
        for (TimeLine tl : mix.model().getAllTimeLines()) {
            assertTrue(tl.getMicroTime() >= 0);
        }
    }

    static int soundCount(BMSModel model) {
        int count = 0;
        for (TimeLine tl : model.getAllTimeLines()) {
            for (int lane = 0; lane < model.getMode().key; lane++) {
                if (tl.getNote(lane) != null && !(tl.getNote(lane) instanceof LongNote ln && ln.isEnd())) {
                    count++;
                }
            }
            count += tl.getBackGroundNotes().length;
        }
        return count;
    }
}
