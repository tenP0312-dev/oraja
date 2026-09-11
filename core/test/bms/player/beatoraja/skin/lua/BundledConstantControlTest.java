package bms.player.beatoraja.skin.lua;

import bms.player.beatoraja.skin.json.JSONSkinLoader;
import bms.player.beatoraja.skin.json.JsonSkin;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BundledConstantControlTest {
    @Test
    void constantIndicatorAndActionUseDisplayTimeConstant() throws Exception {
        InspectingLoader loader = new InspectingLoader();
        assertNotNull(loader.loadHeader(Path.of("../assets/skin/default/select.json")));
        JsonSkin.ImageSet control = Arrays.stream(loader.skin().imageset)
                .filter(item -> "option-constant".equals(item.id)).findFirst().orElseThrow();
        assertEquals(400, control.ref);
        assertEquals(400, control.act.getEventId());
    }

    private static class InspectingLoader extends JSONSkinLoader {
        JsonSkin.Skin skin() { return sk; }
    }
}
