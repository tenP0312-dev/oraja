package bms.player.beatoraja.skin.property;

import bms.player.beatoraja.MainController;
import bms.player.beatoraja.Version;
import bms.player.beatoraja.skin.SkinProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinVersionCompatibilityTest {

    @Test
    void brandedApplicationNameDoesNotSelectTheBuiltInArenaSkinApi() {
        StringProperty property = StringPropertyFactory.getStringProperty(
                SkinProperty.STRING_VERSION
        );
        assertNotNull(property);

        String skinVersion = property.get(null);
        assertEquals(Version.getLongVersion(), skinVersion);
        assertEquals(Version.getSkinVersion(), skinVersion);
        assertFalse(skinVersion.contains("Arena"));

        assertEquals("Arena oraja", Version.getArenaWindowTitle());
        assertEquals(Version.getArenaWindowTitle(), MainController.getWindowTitle());
        assertTrue(Version.getArenaDisplayName().startsWith("Arena oraja "));
        assertEquals(Version.getArenaDisplayName(), MainController.getVersion());
    }
}
