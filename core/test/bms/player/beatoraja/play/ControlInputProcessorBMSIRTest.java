package bms.player.beatoraja.play;

import bms.model.Mode;
import bms.player.beatoraja.PlayConfig;
import bms.player.beatoraja.PlayerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlInputProcessorBMSIRTest {
    @Test
    void sixAndSevenKeysAreMappedForSevenAndFourteenKeyModesOnly() {
        assertTrue(ControlInputProcessor.isSixSevenKey(Mode.BEAT_7K, 5));
        assertTrue(ControlInputProcessor.isSixSevenKey(Mode.BEAT_7K, 6));
        assertTrue(ControlInputProcessor.isSixSevenKey(Mode.BEAT_14K, 14));
        assertTrue(ControlInputProcessor.isSixSevenKey(Mode.BEAT_14K, 15));
        assertFalse(ControlInputProcessor.isSixSevenKey(Mode.BEAT_5K, 5));
        assertFalse(ControlInputProcessor.isSixSevenKey(Mode.POPN_9K, 6));
    }

    @Test
    void lr2RequiresSuddenWhileExtendedAcceptsAnyCoverTarget() {
        assertFalse(ControlInputProcessor.usesSixSevenCoverControl(
                PlayerConfig.BMSIR_COVER_CONTROL_ORAJA,
                true,
                true,
                true
        ));
        assertTrue(ControlInputProcessor.usesSixSevenCoverControl(
                PlayerConfig.BMSIR_COVER_CONTROL_LR2,
                true,
                false,
                false
        ));
        assertFalse(ControlInputProcessor.usesSixSevenCoverControl(
                PlayerConfig.BMSIR_COVER_CONTROL_LR2,
                false,
                true,
                true
        ));
        assertTrue(ControlInputProcessor.usesSixSevenCoverControl(
                PlayerConfig.BMSIR_COVER_CONTROL_EXTENDED,
                false,
                true,
                false
        ));
        assertTrue(ControlInputProcessor.usesSixSevenCoverControl(
                PlayerConfig.BMSIR_COVER_CONTROL_EXTENDED,
                false,
                false,
                true
        ));
        assertFalse(ControlInputProcessor.usesSixSevenCoverControl(
                PlayerConfig.BMSIR_COVER_CONTROL_EXTENDED,
                false,
                false,
                false
        ));
    }

    @Test
    void keySevenIsTheIncreasingDirectionOnBothPlayers() {
        assertFalse(ControlInputProcessor.isSevenKey(Mode.BEAT_7K, 5));
        assertTrue(ControlInputProcessor.isSevenKey(Mode.BEAT_7K, 6));
        assertFalse(ControlInputProcessor.isSevenKey(Mode.BEAT_14K, 14));
        assertTrue(ControlInputProcessor.isSevenKey(Mode.BEAT_14K, 15));
    }

    @Test
    void sixSevenLaneCoverOnlyKeepsTheBuiltInIidxFhsReset() {
        assertFalse(ControlInputProcessor.keepsIidxFhsLaneCoverReset(
                PlayConfig.FIX_HISPEED_OFF
        ));
        assertFalse(ControlInputProcessor.keepsIidxFhsLaneCoverReset(
                PlayConfig.FIX_HISPEED_STARTBPM
        ));
        assertFalse(ControlInputProcessor.keepsIidxFhsLaneCoverReset(
                PlayConfig.FIX_HISPEED_MAINBPM
        ));
        assertTrue(ControlInputProcessor.keepsIidxFhsLaneCoverReset(
                PlayConfig.FIX_HISPEED_IIDX_FHS
        ));
    }

    @Test
    void sixSevenFhsRecalculationRequiresBothSwitchesAndCurrentBpm() {
        assertFalse(ControlInputProcessor.shouldRecalculateSixSevenHispeed(
                false, false, 150.0
        ));
        assertFalse(ControlInputProcessor.shouldRecalculateSixSevenHispeed(
                true, false, 150.0
        ));
        assertFalse(ControlInputProcessor.shouldRecalculateSixSevenHispeed(
                false, true, 150.0
        ));
        assertFalse(ControlInputProcessor.shouldRecalculateSixSevenHispeed(
                true, true, 0.0
        ));
        assertTrue(ControlInputProcessor.shouldRecalculateSixSevenHispeed(
                true, true, 150.0
        ));
    }
}
