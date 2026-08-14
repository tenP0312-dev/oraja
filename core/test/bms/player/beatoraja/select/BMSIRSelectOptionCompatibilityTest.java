package bms.player.beatoraja.select;

import bms.player.beatoraja.PlayConfig;
import bms.player.beatoraja.PlayerConfig;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRSelectOptionCompatibilityTest {
    @Test
    void extensionAndNoticeDefaultsAreIndependent() {
        PlayerConfig player = new PlayerConfig();

        assertFalse(player.isBmsirHideMissingTableSongs());
        assertFalse(player.isBmsirIidxFhsEnabled());
        assertTrue(player.isBmsirIidxFhsSkinNoticeEnabled());
        assertTrue(player.isBmsirJudgeRankSortEnabled());
        assertTrue(player.isBmsirJudgeRankSortSkinNoticeEnabled());

        player.setBmsirIidxFhsSkinNoticeEnabled(false);
        player.setBmsirJudgeRankSortSkinNoticeEnabled(true);
        assertFalse(player.isBmsirIidxFhsSkinNoticeEnabled());
        assertTrue(player.isBmsirJudgeRankSortSkinNoticeEnabled());
    }

    @Test
    void missingTableSongSettingPersistsInPlayerConfig() {
        PlayerConfig player = new PlayerConfig();

        player.setBmsirHideMissingTableSongs(true);

        assertTrue(player.isBmsirHideMissingTableSongs());
        PlayerConfig restored = new Json().fromJson(
                PlayerConfig.class,
                PlayerConfig.getConfigJson(player)
        );
        assertTrue(restored.isBmsirHideMissingTableSongs());
    }

    @Test
    void legacyHsFixCycleSkipsIidxFhsUntilEnabled() {
        PlayerConfig player = new PlayerConfig();
        PlayConfig play = new PlayConfig();
        play.setFixhispeed(PlayConfig.FIX_HISPEED_MINBPM);

        assertEquals(
                PlayConfig.FIX_HISPEED_OFF,
                BMSIRSelectOptionCompatibility.cycleHsFix(play, player, 1)
        );

        player.setBmsirIidxFhsEnabled(true);
        play.setFixhispeed(PlayConfig.FIX_HISPEED_MINBPM);
        assertEquals(
                PlayConfig.FIX_HISPEED_IIDX_FHS,
                BMSIRSelectOptionCompatibility.cycleHsFix(play, player, 1)
        );
        assertEquals(
                PlayConfig.FIX_HISPEED_MINBPM,
                BMSIRSelectOptionCompatibility.cycleHsFix(play, player, -1)
        );
    }

    @Test
    void legacySortCycleSkipsJudgeUntilEnabled() {
        PlayerConfig player = new PlayerConfig();
        player.setBmsirJudgeRankSortEnabled(false);
        player.setSort(BarSorter.defaultSorter.length - 2);

        assertEquals(
                0,
                BMSIRSelectOptionCompatibility.cycleSort(player, 1)
        );

        player.setBmsirJudgeRankSortEnabled(true);
        player.setSort(BarSorter.defaultSorter.length - 2);
        assertEquals(
                BarSorter.defaultSorter.length - 1,
                BMSIRSelectOptionCompatibility.cycleSort(player, 1)
        );
        player.setSort(0);
        assertEquals(
                BarSorter.defaultSorter.length - 1,
                BMSIRSelectOptionCompatibility.cycleSort(player, -1)
        );
    }

    @Test
    void disablingExtensionsNormalizesAnActiveAddedValue() {
        PlayerConfig player = new PlayerConfig();
        player.setBmsirIidxFhsEnabled(true);
        player.getMode7().getPlayconfig().setFixhispeed(
                PlayConfig.FIX_HISPEED_IIDX_FHS
        );
        player.setBmsirIidxFhsEnabled(false);
        assertEquals(
                PlayConfig.FIX_HISPEED_STARTBPM,
                player.getMode7().getPlayconfig().getFixhispeed()
        );

        player.setBmsirJudgeRankSortEnabled(true);
        player.setSort(BarSorter.defaultSorter.length - 1);
        player.setSortid(BarSorter.JUDGE.name());
        player.setBmsirJudgeRankSortEnabled(false);
        assertEquals(0, player.getSort());
        assertEquals(BarSorter.TITLE.name(), player.getSortid());
    }

    @Test
    void courseRuntimeStateIsNotPersistedInPlayerConfig() {
        PlayerConfig player = new PlayerConfig();
        player.getMode7().getPlayconfig().markIidxFhsSudActivated();

        assertFalse(
                PlayerConfig.getConfigJson(player).contains(
                        "iidxFhsSudActivated"
                )
        );
    }
}
