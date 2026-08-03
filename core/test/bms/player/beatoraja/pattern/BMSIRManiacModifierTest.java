package bms.player.beatoraja.pattern;

import bms.model.BMSModel;
import bms.model.MineNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
}
