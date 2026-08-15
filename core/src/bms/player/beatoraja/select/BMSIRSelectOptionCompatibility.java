package bms.player.beatoraja.select;

import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.modmenu.ImGuiNotify;

/**
 * Keeps the BMS-IR-added Music Select sort value optional for legacy skins and
 * gives players an independently configurable fallback notice.
 */
public final class BMSIRSelectOptionCompatibility {
    private static final int LEGACY_SORT_COUNT = defaultSortIndex(
            BarSorter.JUDGE
    );

    private BMSIRSelectOptionCompatibility() {
    }

    public static int cycleSort(PlayerConfig playerConfig, int direction) {
        if (playerConfig == null) {
            return Integer.MIN_VALUE;
        }
        int count = playerConfig.isBmsirJudgeRankSortEnabled()
                ? BarSorter.defaultSorter.length
                : LEGACY_SORT_COUNT;
        int current = playerConfig.getSort();
        if (current < 0 || current >= count) {
            current = defaultSortIndex(BarSorter.TITLE);
        }
        return Math.floorMod(current + (direction >= 0 ? 1 : -1), count);
    }

    private static int defaultSortIndex(BarSorter target) {
        for (int index = 0; index < BarSorter.defaultSorter.length; index++) {
            if (BarSorter.defaultSorter[index] == target) {
                return index;
            }
        }
        throw new IllegalStateException(
                target.name() + " is missing from the default sort cycle"
        );
    }

    public static boolean notifyJudgeRankSortIfEnabled(PlayerConfig playerConfig) {
        if (playerConfig == null
                || !playerConfig.isBmsirJudgeRankSortSkinNoticeEnabled()) {
            return false;
        }
        ImGuiNotify.info(BMSIRArenaI18n.text(
                "判定難易度ソートを選択しました（未対応スキンでは正常に表示されない場合があります）",
                "Judge-rank sort selected (unsupported skins may not display it correctly)"
        ), 4000);
        return true;
    }
}
