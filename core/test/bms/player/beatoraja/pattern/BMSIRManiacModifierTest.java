package bms.player.beatoraja.pattern;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.MineNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRManiacModifierTest {
    @Test
    void sameChartAndSettingsProduceSamePlacement() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAddNotes(50);
        BMSIRManiacModifier first = new BMSIRManiacModifier(settings);
        BMSIRManiacModifier second = new BMSIRManiacModifier(settings);
        first.modify(model());
        second.modify(model());
        assertEquals(first.getPlacementHash(), second.getPlacementHash());
    }

    @Test
    void addMinesUsesDeterministicMidpoints() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAddMines(100);
        BMSModel model = model();
        new BMSIRManiacModifier(settings).modify(model);
        assertTrue(model.containsMineNote());
        assertTrue(java.util.Arrays.stream(model.getAllTimeLines())
                .anyMatch(line -> line.getNote(0) instanceof MineNote));
    }

    @Test
    void extraModeUsesLr2LaneNumbersAndAvoidsNotesInsideTheMinimumGap() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setExtraMode(1);
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        model.setSHA256("lr2-extra-golden");
        model.setWavList(new String[]{"", "kick.ogg", "finish.ogg"});
        TimeLine first = line(0, 0);
        first.setNote(0, new NormalNote(1));
        TimeLine background = line(0.2, 400_000);
        background.addBackGroundNote(new NormalNote(1));
        TimeLine last = line(0.5, 1_000_000);
        last.setNote(6, new NormalNote(2));
        model.setAllTimeLine(new TimeLine[]{first, background, last});

        new BMSIRManiacModifier(settings).modify(model);

        // OpenLR2 lane 1 is occupied within 500 ms, so odd WAV 1 shifts to lane 2.
        assertEquals(0, background.getBackGroundNotes().length);
        assertEquals(1, background.getNote(1).getWav());
    }

    @Test
    void extraModeCollapsesExistingLongNotesLikeLr2() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setExtraMode(1);
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        model.setSHA256("lr2-extra-longnote");
        model.setWavList(new String[]{"", "hold.ogg", "finish.ogg"});
        TimeLine startLine = line(0, 0);
        LongNote start = new LongNote(1, 20_000, 100_000);
        start.setType(LongNote.TYPE_LONGNOTE);
        startLine.setNote(0, start);
        TimeLine endLine = line(0.5, 1_000_000);
        LongNote end = new LongNote(-1);
        end.setType(LongNote.TYPE_LONGNOTE);
        endLine.setNote(0, end);
        start.setPair(end);
        endLine.setNote(6, new NormalNote(2));
        model.setAllTimeLine(new TimeLine[]{startLine, endLine});

        new BMSIRManiacModifier(settings).modify(model);

        NormalNote collapsed = assertInstanceOf(NormalNote.class, startLine.getNote(0));
        assertEquals(1, collapsed.getWav());
        assertEquals(20_000, collapsed.getMicroStarttime());
        assertEquals(100_000, collapsed.getMicroDuration());
        assertNull(endLine.getNote(0));
    }

    @Test
    void addNotesAlsoCollapsesExistingLongNotesLikeLr2() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAddNotes(100);
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        model.setSHA256("lr2-add-notes-longnote");
        TimeLine startLine = line(0, 0);
        LongNote start = new LongNote(1);
        start.setType(LongNote.TYPE_LONGNOTE);
        startLine.setNote(0, start);
        TimeLine endLine = line(0.5, 1_000_000);
        LongNote end = new LongNote(-1);
        end.setType(LongNote.TYPE_LONGNOTE);
        endLine.setNote(0, end);
        start.setPair(end);
        model.setAllTimeLine(new TimeLine[]{startLine, endLine});

        new BMSIRManiacModifier(settings).modify(model);

        assertInstanceOf(NormalNote.class, startLine.getNote(0));
        assertNull(endLine.getNote(0));
    }

    @Test
    void addNotesFillsEachDpSideIndependently() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAddNotes(100);
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_14K);
        model.setBpm(120);
        model.setSHA256("lr2-add-notes-dp");
        TimeLine line = new TimeLine(0, 0, Mode.BEAT_14K.key);
        line.setBPM(120);
        line.setNote(0, new NormalNote(1));
        line.setNote(8, new NormalNote(2));
        model.setAllTimeLine(new TimeLine[]{line});

        new BMSIRManiacModifier(settings).modify(model);

        int left = 0;
        int right = 0;
        for (int lane = 0; lane < 8; lane++) if (line.getNote(lane) != null) left++;
        for (int lane = 8; lane < 16; lane++) if (line.getNote(lane) != null) right++;
        assertEquals(2, left);
        assertEquals(2, right);
    }

    @Test
    void randomSequenceMatchesDxLibMt19937GetRand() {
        BMSIRManiacModifier.LR2Random random = new BMSIRManiacModifier.LR2Random(5489);
        assertEquals(82, random.inclusive(100));
        assertEquals(13, random.inclusive(100));
        assertEquals(91, random.inclusive(100));
        assertEquals(84, random.inclusive(100));
        assertEquals(12, random.inclusive(100));
    }

    @Test
    void addLongNotesEndsAtTheMidpointBetweenStarts() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAddLongNotes(100);
        settings.setGenerationSeedOverride(5489L);
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        model.setSHA256("lr2-add-longnotes");
        TimeLine first = line(0, 0);
        first.setNote(0, new NormalNote(1));
        TimeLine second = line(0.5, 1_000_000);
        second.setNote(0, new NormalNote(2));
        TimeLine chartEnd = line(1.0, 2_000_000);
        model.setAllTimeLine(new TimeLine[]{first, second, chartEnd});

        new BMSIRManiacModifier(settings).modify(model);

        LongNote start = (LongNote) first.getNote(0);
        TimeLine midpoint = java.util.Arrays.stream(model.getAllTimeLines())
                .filter(item -> item.getMicroTime() == 500_000L)
                .findFirst()
                .orElseThrow();
        assertSame(start.getPair(), midpoint.getNote(0));
    }

    @Test
    void addLongNotesKeepsTheOriginalEndWhenTheNewEndIsOccupied() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAddLongNotes(100);
        settings.setGenerationSeedOverride(5489L);
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        model.setSHA256("lr2-add-longnotes-collision");
        TimeLine startLine = line(0, 0);
        LongNote start = new LongNote(1);
        start.setType(LongNote.TYPE_LONGNOTE);
        startLine.setNote(0, start);
        TimeLine oldEndLine = line(0.1, 200_000);
        LongNote oldEnd = new LongNote(-1);
        oldEnd.setType(LongNote.TYPE_LONGNOTE);
        oldEndLine.setNote(0, oldEnd);
        start.setPair(oldEnd);
        TimeLine blockedMidpoint = line(0.25, 500_000);
        blockedMidpoint.setNote(0, new MineNote(-1, 4.0));
        TimeLine next = line(0.5, 1_000_000);
        next.setNote(0, new NormalNote(2));
        model.setAllTimeLine(new TimeLine[]{startLine, oldEndLine, blockedMidpoint, next});

        new BMSIRManiacModifier(settings).modify(model);

        assertSame(oldEnd, oldEndLine.getNote(0));
        assertSame(oldEnd, start.getPair());
    }

    @Test
    void addMinesUsesTheGapAfterALongNoteEnd() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setAddMines(100);
        settings.setGenerationSeedOverride(5489L);
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        model.setSHA256("chart");
        TimeLine startLine = line(0, 0);
        LongNote start = new LongNote(1);
        start.setType(LongNote.TYPE_LONGNOTE);
        startLine.setNote(0, start);
        TimeLine endLine = line(0.2, 400_000);
        LongNote end = new LongNote(-1);
        end.setType(LongNote.TYPE_LONGNOTE);
        endLine.setNote(0, end);
        start.setPair(end);
        TimeLine next = line(0.5, 1_000_000);
        next.setNote(0, new NormalNote(2));
        model.setAllTimeLine(new TimeLine[]{startLine, endLine, next});

        new BMSIRManiacModifier(settings).modify(model);

        TimeLine midpoint = java.util.Arrays.stream(model.getAllTimeLines())
                .filter(item -> item.getMicroTime() == 700_000L)
                .findFirst()
                .orElseThrow();
        assertTrue(midpoint.getNote(0) instanceof MineNote);
    }

    @Test
    void optionValueChangesPlacementIdentity() {
        BMSIRManiacSettings low = new BMSIRManiacSettings();
        low.setAddNotes(10);
        BMSIRManiacSettings high = new BMSIRManiacSettings();
        high.setAddNotes(100);
        assertNotEquals(low.virtualChartId("chart"), high.virtualChartId("chart"));
    }

    @Test
    void loudnessFillsEmptyLanesAtOneHundredPercent() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setLoudness(100);
        BMSModel model = model();
        int original = model.getTotalNotes();
        new BMSIRManiacModifier(settings).modify(model);
        assertTrue(model.getTotalNotes() > original);
    }

    @Test
    void softLandingChangesScrollDeterministically() {
        BMSIRManiacSettings settings = new BMSIRManiacSettings();
        settings.setSoftLanding(2);
        BMSModel first = model();
        BMSModel second = model();
        new BMSIRManiacModifier(settings).modify(first);
        new BMSIRManiacModifier(settings).modify(second);
        assertTrue(java.util.Arrays.stream(first.getAllTimeLines())
                .anyMatch(line -> line.getScroll() != 1.0));
        for (int index = 0; index < first.getAllTimeLines().length; index++) {
            assertEquals(first.getAllTimeLines()[index].getScroll(),
                    second.getAllTimeLines()[index].getScroll());
        }
    }

    private static BMSModel model() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        model.setSHA256("chart");
        TimeLine[] lines = new TimeLine[8];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = new TimeLine(i / 4.0, i * 500_000L, Mode.BEAT_7K.key);
            lines[i].setBPM(120);
            lines[i].setNote(i % 7, new NormalNote(i + 1));
            lines[i].addBackGroundNote(new NormalNote(i + 1));
        }
        model.setAllTimeLine(lines);
        return model;
    }

    private static TimeLine line(double section, long time) {
        TimeLine line = new TimeLine(section, time, Mode.BEAT_7K.key);
        line.setBPM(120);
        return line;
    }
}
