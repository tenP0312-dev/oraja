package bms.player.beatoraja.pattern;

import bms.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BMSIRSevenToNineModifierTest {
    @Test void everySourceChordHasSafePlacementAndOnlyNecessaryReduction() {
        for (int sourceMask = 1; sourceMask < 256; sourceMask++) {
            BMSModel model = model(1);
            TimeLine tl = model.getAllTimeLines()[0];
            for (int lane = 0; lane < 8; lane++) {
                if ((sourceMask & (1 << lane)) != 0) tl.setNote(lane, new NormalNote(lane + 1));
            }
            BMSIRSevenToNineModifier.apply(model);
            int mask = visibleMask(tl);
            assertTrue(BMSIRSevenToNineModifier.safeChord(mask), "source=" + sourceMask);
            assertEquals(Math.min(6, Integer.bitCount(sourceMask)), Integer.bitCount(mask));
            assertEquals(Integer.bitCount(sourceMask), model.getTotalNotes() + tl.getBackGroundNotes().length);
            assertEquals(Mode.POPN_9K, model.getMode());
            assertEquals(9, tl.getLaneCount());
        }
    }

    @Test void staircaseAndScratchTrillAreDeterministic() {
        BMSModel first = staircase();
        BMSModel second = staircase();
        BMSIRSevenToNineModifier.apply(first);
        BMSIRSevenToNineModifier.apply(second);
        assertEquals(BMSIRManiacModifier.placementHash(first), BMSIRManiacModifier.placementHash(second));
        int last = -1;
        for (int i = 0; i < 7; i++) {
            int lane = laneOf(first.getAllTimeLines()[i], i + 1);
            assertTrue(lane > last);
            last = lane;
        }
        BMSModel scratch = model(4);
        for (TimeLine tl : scratch.getAllTimeLines()) tl.setNote(7, new NormalNote(100));
        BMSIRSevenToNineModifier.apply(scratch);
        assertEquals(0, laneOf(scratch.getAllTimeLines()[0], 100));
        assertEquals(8, laneOf(scratch.getAllTimeLines()[1], 100));
        assertEquals(0, laneOf(scratch.getAllTimeLines()[2], 100));
        assertEquals(8, laneOf(scratch.getAllTimeLines()[3], 100));
    }

    @Test void heldLongNotesReserveLanesAndSafeChordsThroughTheirEnds() {
        BMSModel model = model(8);
        TimeLine[] tls = model.getAllTimeLines();
        for (int lane = 0; lane < 8; lane++) {
            LongNote start = new LongNote(10 + lane);
            LongNote end = new LongNote(20 + lane);
            tls[0].setNote(lane, start);
            tls[7].setNote(lane, end);
            start.setPair(end);
            start.setType(LongNote.TYPE_HELLCHARGENOTE);
        }
        // Dense intervening starts must be reduced, never overwrite LN bodies/ends.
        for (int i = 1; i < 7; i++) tls[i].setNote(0, new NormalNote(50 + i));
        BMSIRSevenToNineModifier.apply(model);
        int held = visibleMask(tls[0]);
        assertEquals(6, Integer.bitCount(held));
        assertEquals(held, visibleMask(tls[7]));
        for (int lane = 0; lane < 9; lane++) {
            if (tls[0].getNote(lane) instanceof LongNote ln) {
                assertSame(ln.getPair(), tls[7].getNote(lane));
                assertEquals(LongNote.TYPE_HELLCHARGENOTE, ln.getType());
            }
        }
        for (int i = 1; i < 7; i++) {
            assertEquals(0, visibleMask(tls[i]));
            assertEquals(1, tls[i].getBackGroundNotes().length);
        }
        assertEquals(2, tls[0].getBackGroundNotes().length);
        assertEquals(2, tls[7].getBackGroundNotes().length);
    }

    @Test void reductionRetainsKeysoundsOffsetsLayersAndSourceHash() {
        BMSModel model = model(1);
        TimeLine tl = model.getAllTimeLines()[0];
        Note[] source = new Note[8];
        for (int lane = 0; lane < 8; lane++) {
            source[lane] = new NormalNote(lane, 1234 + lane, 8765);
            source[lane].addLayeredNote(new NormalNote(90 + lane));
            tl.setNote(lane, source[lane]);
        }
        BMSIRSevenToNineModifier.apply(model);
        Set<Note> output = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int lane = 0; lane < 9; lane++) if (tl.getNote(lane) != null) output.add(tl.getNote(lane));
        output.addAll(Arrays.asList(tl.getBackGroundNotes()));
        assertEquals(8, output.size());
        for (int lane = 0; lane < 8; lane++) {
            assertTrue(output.contains(source[lane]));
            assertEquals(1234 + lane, source[lane].getMicroStarttime());
            assertEquals(8765, source[lane].getMicroDuration());
            assertEquals(90 + lane, source[lane].getLayeredNotes()[0].getWav());
        }
        assertEquals("a".repeat(64), model.getSHA256());
        assertFalse(BMSIRSevenToNineModifier.apply(model));
    }

    @Test void unsupportedModesStayUntouched() {
        BMSModel model = model(1);
        model.setMode(Mode.BEAT_14K);
        assertFalse(BMSIRSevenToNineModifier.apply(model));
        assertFalse(BMSIRSevenToNineModifier.isApplied(model));
        assertEquals(Mode.BEAT_14K, model.getMode());
    }

    private static BMSModel staircase() {
        BMSModel model = model(7);
        for (int i = 0; i < 7; i++) model.getAllTimeLines()[i].setNote(i, new NormalNote(i + 1));
        return model;
    }

    private static BMSModel model(int count) {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setSHA256("a".repeat(64));
        model.setBpm(120);
        TimeLine[] lines = new TimeLine[count];
        for (int i = 0; i < count; i++) {
            lines[i] = new TimeLine(i / 4.0, 1_000_000L + i * 125_000L, 8);
            lines[i].setBPM(120);
        }
        model.setAllTimeLine(lines);
        return model;
    }

    private static int visibleMask(TimeLine tl) {
        int mask = 0;
        for (int i = 0; i < 9; i++) if (tl.getNote(i) != null) mask |= 1 << i;
        return mask;
    }

    private static int laneOf(TimeLine tl, int wav) {
        for (int i = 0; i < 9; i++) if (tl.getNote(i) != null && tl.getNote(i).getWav() == wav) return i;
        return -1;
    }
}
