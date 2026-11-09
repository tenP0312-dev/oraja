package bms.player.beatoraja.launcher;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import bms.player.beatoraja.skin.SkinHeader;

import static org.junit.jupiter.api.Assertions.*;

/** The skin tab's trust checkbox edits Config.luaTrustedSkinDirectories for one skin folder. */
class SkinLuaTrustSettingTest {
    private static SkinHeader header(String path) {
        SkinHeader header = new SkinHeader();
        header.setPath(Path.of(path));
        return header;
    }

    @Test void onlyLuaAndJsonSkinsOfferTheSetting() {
        assertTrue(SkinConfigurationView.runsLua(header("skin/litone/select.luaskin")));
        assertTrue(SkinConfigurationView.runsLua(header("skin/modern/play.json")));
        assertFalse(SkinConfigurationView.runsLua(header("skin/old/play.lr2skin")));
        assertFalse(SkinConfigurationView.runsLua(null));
    }

    @Test void trustingAndUntrustingEditsOnlyThatFolder() {
        Path folder = SkinConfigurationView.trustDirectory(header("skin/litone/select.luaskin"));
        String[] other = { "skin/another" };

        String[] trusted = SkinConfigurationView.withTrust(other, folder, true);
        assertArrayEquals(new String[] { "skin/another", "skin/litone" }, trusted);
        assertTrue(SkinConfigurationView.isTrusted(trusted, folder));
        assertTrue(SkinConfigurationView.isTrusted(trusted, folder.resolve("sub")));

        // Turning it on twice keeps a single entry.
        assertEquals(2, SkinConfigurationView.withTrust(trusted, folder, true).length);

        String[] removed = SkinConfigurationView.withTrust(trusted, folder, false);
        assertArrayEquals(other, removed);
        assertFalse(SkinConfigurationView.isTrusted(removed, folder));
        assertFalse(SkinConfigurationView.isTrusted(null, folder));
    }
}
