package bms.player.beatoraja.skin.property;

import bms.model.Mode;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.MainState;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.BMSResource;
import bms.player.beatoraja.audio.BMSLoudnessAnalyzer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegulSpeedSkinApiTest {
    @Test
    void legacyIndicatorReadsScrollRemovalAndDoesNotFollowConstant() throws Exception {
        PlayerConfig player = new PlayerConfig();
        var constructor = PlayerResource.class.getDeclaredConstructor(
                Config.class, PlayerConfig.class, BMSResource.class, BMSLoudnessAnalyzer.class);
        constructor.setAccessible(true);
        PlayerResource resource = constructor.newInstance(new Config(), player, null, null);
        MainState state = new MainState(null, resource) {
            public void create() {}
            public void render() {}
        };
        for (int scroll = 0; scroll <= 2; scroll++) {
            for (boolean constant : new boolean[]{false, true}) {
                player.setScrollMode(scroll);
                player.getPlayConfig(Mode.BEAT_7K).getPlayconfig().setEnableConstant(constant);
                assertEquals(scroll == 1 ? 1 : 0,
                        IntegerPropertyFactory.getImageIndexProperty(302).get(state));
                assertEquals(scroll, IntegerPropertyFactory.getImageIndexProperty(352).get(state));
            }
        }
        EventFactory.getEvent(302).exec(state);
        assertEquals(2, player.getScrollMode(), "Display ID 302 must not gain a new action");
    }

    @Test
    void existingActionsRemainSeparateWithoutInventingALegacyAction() {
        assertNull(EventFactory.getEvent("assist_constant"));
        assertSame(EventFactory.getEvent("constant"), EventFactory.getEvent(400));
        assertSame(EventFactory.getEvent("scrollmode"), EventFactory.getEvent(352));
        assertNotSame(EventFactory.getEvent(302), EventFactory.getEvent(400));
    }

}
