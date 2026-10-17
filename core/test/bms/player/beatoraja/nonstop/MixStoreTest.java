package bms.player.beatoraja.nonstop;

import bms.model.BMSModel;
import bms.model.Note;
import bms.model.TimeLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MixStoreTest {
    @TempDir
    Path directory;

    @Test
    void setsAndRecordsAreKeptApartFromChartScores() throws Exception {
        MixStore store = new MixStore(directory.resolve("nonstopmix"));
        MixPlan plan = MixJsonTest.sample();
        store.saveSet(plan);
        List<MixPlan> sets = store.listSets();
        assertEquals(1, sets.size());
        assertEquals(plan.id, sets.get(0).id);
        assertEquals("/home/me/bms/song0/chart.bms", sets.get(0).tracks.get(0).localPath, "local sets keep their paths");

        MixResult first = new MixResult();
        first.identity = MixJson.playIdentity(plan);
        first.exScore = 100;
        first.clear = 4;
        first.playedAt = 1;
        MixResult second = new MixResult();
        second.identity = first.identity;
        second.exScore = 90;
        second.clear = 5;
        second.playedAt = 2;
        store.addRecord(first);
        store.addRecord(second);
        Files.writeString(directory.resolve("nonstopmix").resolve("records.jsonl"), "not json\n",
                java.nio.file.StandardOpenOption.APPEND);
        assertEquals(2, store.records(first.identity).size());
        assertEquals(5, store.best(first.identity).clear, "a better lamp wins over a higher score");
        assertTrue(store.records("other").isEmpty());

        store.saveDraft(plan);
        assertEquals(plan.name, store.loadDraft().name);
        store.deleteSet(plan);
        assertTrue(store.listSets().isEmpty());
    }

    @Test
    void perTrackResultsFollowTheNotesOfEachSong() {
        BMSModel a = MixCompilerTest.chart(120, "");
        MixPlan plan = MixCompilerTest.plan(2);
        CompiledMix mix = MixCompiler.compile(plan, List.of(MixCompilerTest.source(a, "a"), MixCompilerTest.source(a, "b")));
        BMSModel merged = mix.model();
        int judged = 0;
        for (TimeLine tl : merged.getAllTimeLines()) {
            for (int lane = 0; lane < merged.getMode().key; lane++) {
                Note note = tl.getNote(lane);
                if (note != null && mix.trackOf(note) == 0) {
                    note.setState(1); // PGREAT
                    judged++;
                }
            }
        }
        List<MixResult.TrackResult> rows = MixResult.perTrack(mix, merged);
        assertEquals(judged, rows.get(0).judges[0]);
        assertEquals(0, rows.get(0).unreached);
        assertEquals(rows.get(1).notes, rows.get(1).unreached);
        assertEquals(judged * 2, rows.get(0).exScore());
    }

    @Test
    void runtimeGainFollowsTheActiveSet() {
        BMSModel a = MixCompilerTest.chart(120, "");
        MixPlan plan = MixCompilerTest.plan(2);
        List<MixCompiler.Source> sources = List.of(MixCompilerTest.source(a, "a"), MixCompilerTest.source(a, "b"));
        CompiledMix mix = MixCompiler.compile(plan, sources);
        Note late = null;
        for (TimeLine tl : mix.model().getAllTimeLines()) {
            for (Note note : tl.getBackGroundNotes()) {
                if (mix.trackOf(note) == 0 && tl.getMilliTime() > mix.layout().start()[1] + 1000) {
                    late = note;
                }
            }
        }
        assertNotNull(late);
        assertEquals(1f, MixRuntime.noteGain(late));
        MixRuntime.activate(new MixRuntime.Session(mix, sources, plan, MixRuntime.Kind.PREVIEW, "t"));
        try {
            assertTrue(MixRuntime.noteGain(late) < 1f, "a fading song plays quieter");
            assertTrue(MixRuntime.isMix(mix.model()));
            BMSModel retry = MixRuntime.recompile();
            assertNotSame(mix.model(), retry);
            assertEquals(mix.identity(), retry.getSHA256());
        } finally {
            MixRuntime.deactivate();
        }
        assertFalse(MixRuntime.isActive());
    }
}
