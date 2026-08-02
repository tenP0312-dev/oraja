package bms.player.beatoraja.skin.property;

import bms.player.beatoraja.skin.SkinProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BMSIRJudgeTimingSkinApiTest {

    @Test
    void restoreSettingIsRegisteredForLuaSkinsAndLegacyProperties() {
        assertNotNull(BooleanPropertyFactory.getBooleanProperty(
                SkinProperty.OPTION_BMSIR_JUDGE_TIMING_RESTORE
        ));
        assertNotNull(EventFactory.getEvent("bmsir_judge_timing_restore"));
        assertEquals(
                SkinProperty.BUTTON_BMSIR_JUDGE_TIMING_RESTORE,
                EventFactory.getEvent("bmsir_judge_timing_restore").getEventId()
        );
    }
}
