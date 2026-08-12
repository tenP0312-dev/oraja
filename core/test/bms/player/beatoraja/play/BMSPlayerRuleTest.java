package bms.player.beatoraja.play;

import bms.model.Mode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSPlayerRuleTest {
    @AfterEach
    void resetRuleProfile() {
        BMSPlayerRule.clearArenaRuleProfileOverride();
        BMSPlayerRule.setConfiguredRuleProfile(BMSPlayerRule.PROFILE_LR2);
    }

    @Test
    void configuredProfileSelectsLr2OrOrajaRules() {
        BMSPlayerRule.setConfiguredRuleProfile("lr2");
        assertEquals(
                BMSPlayerRule.LR2,
                BMSPlayerRule.getBMSPlayerRule(Mode.BEAT_7K)
        );
		assertEquals(
				BMSPlayerRule.NoteJudgementBehavior.LR2ORAJA,
				BMSPlayerRule.getBMSPlayerRule(Mode.BEAT_7K).noteJudgement
		);

        BMSPlayerRule.setConfiguredRuleProfile("oraja");
        assertEquals(
                BMSPlayerRule.Beatoraja_7,
                BMSPlayerRule.getBMSPlayerRule(Mode.BEAT_7K)
        );
        assertEquals(
                BMSPlayerRule.Beatoraja_9,
                BMSPlayerRule.getBMSPlayerRule(Mode.POPN_9K)
        );
		assertEquals(
				BMSPlayerRule.NoteJudgementBehavior.BEATORAJA,
				BMSPlayerRule.getBMSPlayerRule(Mode.BEAT_7K).noteJudgement
		);
    }

    @Test
    void arenaOverrideDoesNotOverwriteTheSavedProfile() {
        BMSPlayerRule.setConfiguredRuleProfile("oraja");
        BMSPlayerRule.setArenaRuleProfileOverride("lr2");

        assertEquals("oraja", BMSPlayerRule.getConfiguredRuleProfileId());
        assertEquals("lr2", BMSPlayerRule.getActiveRuleProfileId());
        assertEquals(
                BMSPlayerRule.LR2,
                BMSPlayerRule.getBMSPlayerRule(Mode.BEAT_14K)
        );

        BMSPlayerRule.clearArenaRuleProfileOverride();
        assertEquals("oraja", BMSPlayerRule.getActiveRuleProfileId());
        assertEquals(
                BMSPlayerRule.Beatoraja_7,
                BMSPlayerRule.getBMSPlayerRule(Mode.BEAT_14K)
        );
    }

    @Test
    void unknownProfilesFallBackToLr2() {
        BMSPlayerRule.setConfiguredRuleProfile("unknown");
        assertEquals("lr2", BMSPlayerRule.getConfiguredRuleProfileId());
    }

    @Test
    void defaultTotalFollowsTheActiveProfile() {
        BMSPlayerRule.setConfiguredRuleProfile("lr2");
        assertEquals(
                352.0,
                BMSPlayerRule.calculateDefaultTotal(Mode.BEAT_7K, 1000),
                0.0001
        );

        BMSPlayerRule.setConfiguredRuleProfile("oraja");
        assertEquals(
                Math.max(260.0, 7.605 * 1000 / (0.01 * 1000 + 6.5)),
                BMSPlayerRule.calculateDefaultTotal(Mode.BEAT_7K, 1000),
                0.0001
        );

        BMSPlayerRule.setArenaRuleProfileOverride("lr2");
        assertEquals(
                352.0,
                BMSPlayerRule.calculateDefaultTotal(Mode.BEAT_7K, 1000),
                0.0001
        );
    }
}
