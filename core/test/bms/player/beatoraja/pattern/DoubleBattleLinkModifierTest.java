package bms.player.beatoraja.pattern;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;
import bms.player.beatoraja.modmenu.RandomTrainer;
import bms.player.beatoraja.pattern.LaneShuffleModifier.DoubleBattleLinkModifier;
import bms.player.beatoraja.pattern.LaneShuffleModifier.LaneRandomShuffleModifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DoubleBattleLinkModifierTest {
    @Test
    void syncCopiesTheModifiedFirstSideToTheSecondSide() {
        BMSModel model = modelWithDistinctFirstSide(Mode.BEAT_14K);
        new DoubleBattleLinkModifier(false).modify(model);

        assertSecondSide(model, false);
    }

    @Test
    void symmetryCopiesTheModifiedFirstSideInReverseOrder() {
        BMSModel model = modelWithDistinctFirstSide(Mode.BEAT_14K);
        new DoubleBattleLinkModifier(true).modify(model);

        assertSecondSide(model, true);
    }

    @Test
    void randomPatternSupportsMultipleScratchLanesInFortyEightKeyMode() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.KEYBOARD_24K_DOUBLE);
        model.setAllTimeLine(new TimeLine[0]);
        LaneRandomShuffleModifier modifier = new LaneRandomShuffleModifier(1, false);
        modifier.setSeed(1234L);
        modifier.modify(model);

        int[] pattern = modifier.getRandomPattern(Mode.KEYBOARD_24K_DOUBLE);
        assertEquals(Mode.KEYBOARD_24K_DOUBLE.key / 2, pattern.length);
        for (int scratch : Mode.KEYBOARD_24K_DOUBLE.scratchKey) {
            if (scratch >= Mode.KEYBOARD_24K_DOUBLE.key / 2) {
                assertEquals(scratch, pattern[scratch - Mode.KEYBOARD_24K_DOUBLE.key / 2]);
            }
        }
    }

    @Test
    void secondSideTrainerOrderRejectsInvalidPermutations() {
        RandomTrainer.setLaneOrder2P("7654321");
        assertEquals("7654321", RandomTrainer.getLaneOrder2P());
        RandomTrainer.setLaneOrder2P("1111111");
        RandomTrainer.setLaneOrder2P("123456");
        assertEquals("7654321", RandomTrainer.getLaneOrder2P());
        RandomTrainer.setLaneOrder2P("1234567");
    }

    private static BMSModel modelWithDistinctFirstSide(Mode mode) {
        BMSModel model = new BMSModel();
        model.setMode(mode);
        TimeLine timeline = new TimeLine(0, 0, mode.key);
        int[] first = PatternModifier.getKeysForPlayer(mode, 0, false);
        int[] second = PatternModifier.getKeysForPlayer(mode, 1, false);
        for (int index = 0; index < first.length; index++) {
            timeline.setNote(first[index], new NormalNote(index + 1));
            timeline.setNote(second[index], new NormalNote(100 + index));
        }
        model.setAllTimeLine(new TimeLine[]{timeline});
        return model;
    }

    private static void assertSecondSide(BMSModel model, boolean symmetry) {
        int[] first = PatternModifier.getKeysForPlayer(model.getMode(), 0, false);
        int[] second = PatternModifier.getKeysForPlayer(model.getMode(), 1, false);
        int[] actual = new int[second.length];
        for (int index = 0; index < second.length; index++) {
            actual[index] = model.getAllTimeLines()[0].getNote(second[index]).getWav();
        }
        int[] expected = new int[first.length];
        for (int index = 0; index < first.length; index++) {
            int source = symmetry ? first.length - 1 - index : index;
            expected[index] = model.getAllTimeLines()[0].getNote(first[source]).getWav();
        }
        assertArrayEquals(expected, actual);
    }
}
