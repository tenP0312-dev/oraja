package bms.player.beatoraja.play;

import bms.model.LongNote;
import bms.model.NormalNote;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgeManagerRuleProfileTest {
    private static final long[][] BEATORAJA_SEVEN_KEY_WINDOWS = {
            {-20_000, 20_000},
            {-60_000, 60_000},
            {-150_000, 150_000},
            {-280_000, 220_000},
            {-150_000, 500_000}
    };

    @Test
    void multipleBadCollectionIsLr2orajaOnly() {
        NormalNote note = new NormalNote(1);

        JudgeManager.MultiBadCollector oraja =
                JudgeManager.createMultiBadCollector(BMSPlayerRule.Beatoraja_7);
        oraja.add(note, -200_000);
        assertEquals(0, oraja.size);

        JudgeManager.MultiBadCollector lr2oraja =
                JudgeManager.createMultiBadCollector(BMSPlayerRule.LR2);
        lr2oraja.add(note, -200_000);
        assertEquals(1, lr2oraja.size);
    }

    @Test
    void longNoteLateBadHandlingFollowsTheRuleProfile() {
        LongNote longNote = new LongNote(1);
        long lateBadTiming = -200_000;

        assertEquals(
                3,
                JudgeManager.resolveUnjudgedPressJudge(
                        BMSPlayerRule.Beatoraja_7.noteJudgement,
                        longNote,
                        lateBadTiming,
                        BEATORAJA_SEVEN_KEY_WINDOWS
                )
        );
        assertEquals(
                6,
                JudgeManager.resolveUnjudgedPressJudge(
                        BMSPlayerRule.LR2.noteJudgement,
                        longNote,
                        lateBadTiming,
                        BEATORAJA_SEVEN_KEY_WINDOWS
                )
        );
    }

    @Test
    void ordinaryNotesKeepBadHandlingInBothProfiles() {
        NormalNote note = new NormalNote(1);
        long lateBadTiming = -200_000;

        assertEquals(
                3,
                JudgeManager.resolveUnjudgedPressJudge(
                        BMSPlayerRule.Beatoraja_7.noteJudgement,
                        note,
                        lateBadTiming,
                        BEATORAJA_SEVEN_KEY_WINDOWS
                )
        );
        assertEquals(
                3,
                JudgeManager.resolveUnjudgedPressJudge(
                        BMSPlayerRule.LR2.noteJudgement,
                        note,
                        lateBadTiming,
                        BEATORAJA_SEVEN_KEY_WINDOWS
                )
        );
    }
}
