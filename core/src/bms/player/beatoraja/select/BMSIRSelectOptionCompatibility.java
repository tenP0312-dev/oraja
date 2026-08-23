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

    /**
     * Legacy image-select skins use ref 12 as an index into their own sort
     * image list. Last-played sorting is an overlay mode rather than a member
     * of that list, so retain the configured sorter as a visible fallback
     * instead of returning {@link Integer#MIN_VALUE} and hiding the object.
     */
    public static int visibleSortIndex(int configuredSort, boolean lastPlayedSort) {
        if (!lastPlayedSort) {
            return configuredSort;
        }
        if (configuredSort < 0 || configuredSort >= BarSorter.defaultSorter.length) {
            return defaultSortIndex(BarSorter.TITLE);
        }
        return configuredSort;
    }

    /**
     * Text-capable skins can still identify the effective overlay sort even
     * though image-only legacy skins intentionally retain their prior icon.
     */
    public static String visibleSortName(String configuredSort, boolean lastPlayedSort) {
        return lastPlayedSort ? "LAST_PLAYED" : configuredSort;
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
