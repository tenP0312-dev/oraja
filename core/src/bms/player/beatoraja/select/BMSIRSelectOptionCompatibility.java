package bms.player.beatoraja.select;

import bms.player.beatoraja.PlayConfig;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.modmenu.ImGuiNotify;

/**
 * Keeps BMS-IR-added Music Select values optional for legacy skins and gives
 * players explicit, independently configurable fallback notices.
 */
public final class BMSIRSelectOptionCompatibility {
    private static final int LEGACY_HS_FIX_COUNT =
            PlayConfig.FIX_HISPEED_IIDX_FHS;
    private static final int LEGACY_SORT_COUNT = defaultSortIndex(
            BarSorter.JUDGE
    );

    private BMSIRSelectOptionCompatibility() {
    }

    public static int cycleHsFix(
            PlayConfig playConfig,
            PlayerConfig playerConfig,
            int direction
    ) {
        if (playConfig == null || playerConfig == null) {
            return Integer.MIN_VALUE;
        }
        int count = playerConfig.isBmsirIidxFhsEnabled()
                ? LEGACY_HS_FIX_COUNT + 1
                : LEGACY_HS_FIX_COUNT;
        int current = playConfig.getFixhispeed();
        if (current < 0 || current >= count) {
            current = PlayConfig.FIX_HISPEED_OFF;
        }
        int next = Math.floorMod(current + (direction >= 0 ? 1 : -1), count);
        playConfig.setFixhispeed(next);
        if (next == PlayConfig.FIX_HISPEED_IIDX_FHS) {
            playConfig.resetIidxFhsRuntimeState();
        }
        return next;
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

    public static boolean notifyIidxFhsIfEnabled(PlayerConfig playerConfig) {
        if (playerConfig == null
                || !playerConfig.isBmsirIidxFhsSkinNoticeEnabled()) {
            return false;
        }
        ImGuiNotify.info(BMSIRArenaI18n.text(
                "IIDX FHSを選択しました（未対応スキンではOFFと表示されます）",
                "IIDX FHS selected (unsupported skins display OFF)"
        ), 4000);
        return true;
    }

    public static boolean notifyJudgeRankSortIfEnabled(PlayerConfig playerConfig) {
        if (playerConfig == null
                || !playerConfig.isBmsirJudgeRankSortSkinNoticeEnabled()) {
            return false;
        }
        ImGuiNotify.info(BMSIRArenaI18n.text(
                "判定難易度ソートを選択しました（未対応スキンではTITLEと表示されます）",
                "Judge-rank sort selected (unsupported skins display TITLE)"
        ), 4000);
        return true;
    }
}
