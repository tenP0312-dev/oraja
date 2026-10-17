package bms.player.beatoraja.nonstop;

import bms.model.Mode;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MixChartSwapTest {

    static SongData chart(String folder, String name, int difficulty, int level, int notes) {
        SongData song = new SongData();
        song.setSha256(String.format("%064x", Math.abs((folder + name).hashCode())));
        song.setMd5(String.format("%032x", Math.abs((folder + name).hashCode())));
        song.setTitle("Song " + folder);
        song.setSubtitle("[" + name + "]");
        song.setFolder(folder);
        song.setPath("/songs/" + folder + "/" + name + ".bms");
        song.setMode(Mode.BEAT_7K.id);
        song.setDifficulty(difficulty);
        song.setLevel(level);
        song.setNotes(notes);
        song.setLength(120_000);
        return song;
    }

    /** A library made of song folders. */
    static final class Library implements MixChartSwap.Charts {
        final Map<String, List<SongData>> folders = new HashMap<>();

        Library add(SongData... charts) {
            for (SongData chart : charts) {
                folders.computeIfAbsent(chart.getFolder(), k -> new ArrayList<>()).add(chart);
            }
            return this;
        }

        @Override
        public SongData current(MixTrack track) {
            for (List<SongData> charts : folders.values()) {
                for (SongData chart : charts) {
                    if (chart.getSha256().equals(track.sha256)) {
                        return chart;
                    }
                }
            }
            return null;
        }

        @Override
        public SongData[] sameFolder(SongData chart) {
            return folders.getOrDefault(chart.getFolder(), List.of()).toArray(SongData[]::new);
        }
    }

    @Test
    void alternativesAreOtherChartsOfTheSameModeEasierFirst() {
        SongData normal = chart("a", "N", 2, 5, 600);
        SongData hyper = chart("a", "H", 3, 9, 1100);
        SongData another = chart("a", "A", 4, 11, 1500);
        SongData nineKey = chart("a", "9K", 4, 11, 1500);
        nineKey.setMode(Mode.POPN_9K.id);
        SongData duplicate = chart("a", "N", 2, 5, 600);
        SongData unplayable = chart("a", "X", 5, 12, 2000);
        unplayable.setPath(null);
        List<SongData> result = MixChartSwap.alternatives(hyper,
                new SongData[] {another, hyper, nineKey, normal, duplicate, unplayable}, Mode.BEAT_7K.id);
        assertEquals(List.of(normal, another), result);
    }

    @Test
    void stepPicksTheNearestEasierOrHarderChart() {
        SongData normal = chart("a", "N", 2, 5, 600);
        SongData hyper = chart("a", "H", 3, 9, 1100);
        SongData another = chart("a", "A", 4, 11, 1500);
        SongData insane = chart("a", "I", 5, 12, 2000);
        List<SongData> others = MixChartSwap.alternatives(hyper, new SongData[] {normal, hyper, another, insane},
                Mode.BEAT_7K.id);
        assertSame(normal, MixChartSwap.step(hyper, others, -1));
        assertSame(another, MixChartSwap.step(hyper, others, 1));
        List<SongData> fromInsane = MixChartSwap.alternatives(insane, new SongData[] {normal, hyper, another, insane},
                Mode.BEAT_7K.id);
        assertNull(MixChartSwap.step(insane, fromInsane, 1));
        // the same level is ordered by notes
        SongData sameLevel = chart("a", "H2", 3, 9, 1300);
        assertSame(sameLevel, MixChartSwap.step(hyper, List.of(normal, sameLevel, another), 1));
    }

    @Test
    void replaceChartKeepsThePartAndTheTrackSettings() {
        SongData hyper = chart("a", "H", 3, 9, 1100);
        SongData another = chart("a", "A", 4, 11, 1500);
        another.setLength(90_000);
        MixTrack track = MixLibrary.trackFrom(hyper);
        track.inMs = 30_000;
        track.outMs = 100_000;
        track.gainDb = -2;
        track.lockRange = true;
        track.random = new int[] {2};
        MixChartSwap.replaceChart(track, another);
        assertEquals(another.getSha256(), track.sha256);
        assertEquals(another.getMd5(), track.md5);
        assertEquals("[A]", track.subtitle);
        assertEquals(11, track.level);
        assertEquals(another.getPath(), track.localPath);
        assertNull(track.random);
        assertEquals(30_000, track.inMs);
        assertEquals(-1, track.outMs, "an end past the new chart becomes 'to the end'");
        assertEquals(-2, track.gainDb);
        assertTrue(track.lockRange);

        MixTrack late = MixLibrary.trackFrom(hyper);
        late.inMs = 95_000;
        MixChartSwap.replaceChart(late, another);
        assertEquals(89_000, late.inMs, "a start past the new chart moves inside it");
    }

    @Test
    void shiftSetMakesANewCopyAndListsSongsWithoutSuchAChart() {
        SongData aNormal = chart("a", "N", 2, 5, 600);
        SongData aHyper = chart("a", "H", 3, 9, 1100);
        SongData bOnly = chart("b", "H", 3, 8, 900);
        SongData cHyper = chart("c", "H", 3, 7, 800);
        SongData cNormal = chart("c", "N", 2, 4, 400);
        Library library = new Library().add(aNormal, aHyper, bOnly, cHyper, cNormal);
        MixPlan plan = new MixPlan();
        plan.name = "Night";
        plan.irId = "ir-1";
        plan.irRevision = 3;
        plan.addTrack(0, MixLibrary.trackFrom(aHyper));
        plan.addTrack(1, MixLibrary.trackFrom(bOnly));
        plan.addTrack(2, MixLibrary.trackFrom(cHyper));
        plan.tracks.get(0).inMs = 10_000;
        plan.transitions.get(0).overlapMs = 8000;
        plan.transitions.get(0).locked = true;
        String identity = MixJson.playIdentity(plan);

        MixChartSwap.Shifted easier = MixChartSwap.shiftSet(plan, -1, library);
        MixPlan copy = easier.plan();
        assertNotEquals(plan.id, copy.id);
        assertEquals("Night(易)", copy.name);
        assertEquals("", copy.irId);
        assertEquals(0, copy.irRevision);
        assertEquals(aNormal.getSha256(), copy.tracks.get(0).sha256);
        assertEquals(bOnly.getSha256(), copy.tracks.get(1).sha256);
        assertEquals(cNormal.getSha256(), copy.tracks.get(2).sha256);
        assertEquals(List.of(copy.tracks.get(1)), easier.unchanged());
        assertEquals(10_000, copy.tracks.get(0).inMs);
        assertEquals(8000, copy.transitions.get(0).overlapMs);
        assertTrue(copy.transitions.get(0).locked);
        assertNotEquals(identity, MixJson.playIdentity(copy), "records of the copy stay separate");
        // the source set is untouched
        assertEquals(aHyper.getSha256(), plan.tracks.get(0).sha256);
        assertEquals(identity, MixJson.playIdentity(plan));

        MixChartSwap.Shifted harder = MixChartSwap.shiftSet(plan, 1, library);
        assertEquals("Night(難)", harder.plan().name);
        assertEquals(3, harder.unchanged().size(), "no song has a harder chart");
    }

    @Test
    void songsNotInTheLibraryStayAsTheyAre() {
        MixPlan plan = new MixPlan();
        MixTrack missing = new MixTrack();
        missing.sha256 = "f".repeat(64);
        missing.title = "Missing";
        plan.addTrack(0, missing);
        MixChartSwap.Shifted shifted = MixChartSwap.shiftSet(plan, 1, new Library());
        assertEquals(1, shifted.unchanged().size());
        assertEquals(missing.sha256, shifted.plan().tracks.get(0).sha256);
    }
}
