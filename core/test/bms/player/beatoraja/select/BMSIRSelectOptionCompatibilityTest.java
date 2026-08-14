package bms.player.beatoraja.select;

import bms.player.beatoraja.PlayerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BMSIRSelectOptionCompatibilityTest {
    @Test
    void judgeExtensionAndNoticeDefaultsAreIndependent() {
        PlayerConfig player = new PlayerConfig();

        assertTrue(player.isBmsirJudgeRankSortEnabled());
        assertTrue(player.isBmsirJudgeRankSortSkinNoticeEnabled());

        player.setBmsirJudgeRankSortSkinNoticeEnabled(false);
        assertFalse(player.isBmsirJudgeRankSortSkinNoticeEnabled());
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
    void disablingJudgeExtensionNormalizesItsActiveValue() {
        PlayerConfig player = new PlayerConfig();
        player.setBmsirJudgeRankSortEnabled(true);
        player.setSort(BarSorter.defaultSorter.length - 1);
        player.setSortid(BarSorter.JUDGE.name());
        player.setBmsirJudgeRankSortEnabled(false);
        assertEquals(0, player.getSort());
        assertEquals(BarSorter.TITLE.name(), player.getSortid());
    }

}
