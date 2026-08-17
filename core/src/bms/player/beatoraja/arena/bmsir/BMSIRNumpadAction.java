package bms.player.beatoraja.arena.bmsir;

import java.util.Arrays;

/** Actions assignable to the physical numeric keypad. */
public enum BMSIRNumpadAction {
    NONE("none", "なし", "None"),
    JUDGE_AUTO("judge_auto", "判定タイミング自動調整 ON/OFF", "Toggle automatic judge timing"),
    JUDGE_PLUS("judge_plus", "判定タイミング +", "Judge timing +"),
    JUDGE_MINUS("judge_minus", "判定タイミング -", "Judge timing -"),
    KEY_CONFIG("key_config", "キーコンフィグ", "Key configuration"),
    SKIN_CONFIG("skin_config", "スキンコンフィグ", "Skin configuration"),
    BMS_SEARCH("bms_search", "BMS検索", "BMS search"),
    MODE_FILTER("mode_filter", "キーモードフィルター変更", "Change key-mode filter"),
    SORT("sort", "ソート変更", "Change sort"),
    REPLAY("replay", "リプレイ切替", "Change replay"),
    RIVAL("rival", "ライバル変更", "Change rival"),
    // Keep the old stored ID so existing shortcut assignments migrate in place.
    SHOW_ALL_CHARTS("same_folder", "選択曲の全譜面表示", "Show all charts for selected song"),
    OPEN_DOCUMENT("open_document", "同梱テキスト表示", "Show included text"),
    OPEN_IR("open_ir", "IR表示", "Open IR"),
    FAVORITE_SONG("favorite_song", "曲のお気に入り", "Toggle song favorite"),
    FAVORITE_CHART("favorite_chart", "譜面のお気に入り", "Toggle chart favorite"),
    UPDATE_FOLDER("update_folder", "曲フォルダ更新", "Refresh song folders"),
    OPEN_FOLDER("open_folder", "選択曲のフォルダを開く", "Open selected chart folder"),
    PRACTICE("practice", "プラクティス開始", "Start practice"),
    AUTOPLAY("autoplay", "オートプレイ開始", "Start autoplay"),
    ARENA_OVERLAY("arena_overlay", "Arenaオーバーレイ ON/OFF", "Toggle Arena overlay"),
    MOD_MENU("mod_menu", "Modメニュー ON/OFF", "Toggle Mod menu"),
    FPS("fps", "FPS表示 ON/OFF", "Toggle FPS display"),
    FULLSCREEN("fullscreen", "フルスクリーン ON/OFF", "Toggle fullscreen"),
    SCREENSHOT("screenshot", "スクリーンショット", "Screenshot");

    public static final int KEY_COUNT = 10;

    private final String id;
    private final String label;
    private final String englishLabel;

    BMSIRNumpadAction(String id, String label, String englishLabel) {
        this.id = id;
        this.label = label;
        this.englishLabel = englishLabel;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String label(boolean english) {
        return english ? englishLabel : label;
    }

    public static BMSIRNumpadAction fromId(String id) {
        if (id == null) {
            return NONE;
        }
        return Arrays.stream(values())
                .filter(action -> action.id.equalsIgnoreCase(id))
                .findFirst()
                .orElse(NONE);
    }

    public static String[] defaultIds() {
        String[] defaults = legacyDefaultIds();
        defaults[8] = SHOW_ALL_CHARTS.id;
        return defaults;
    }

    private static String[] legacyDefaultIds() {
        String[] defaults = new String[KEY_COUNT];
        Arrays.fill(defaults, NONE.id);
        defaults[0] = JUDGE_AUTO.id;
        defaults[3] = JUDGE_MINUS.id;
        defaults[7] = SKIN_CONFIG.id;
        defaults[9] = JUDGE_PLUS.id;
        return defaults;
    }

    public static String[] normalizeIds(String[] ids) {
        String[] normalized = defaultIds();
        if (ids == null) {
            return normalized;
        }
        for (int index = 0; index < Math.min(ids.length, KEY_COUNT); index++) {
            normalized[index] = fromId(ids[index]).id;
        }
        if (matchesLegacyDefaults(ids)) {
            normalized[8] = SHOW_ALL_CHARTS.id;
        }
        return normalized;
    }

    private static boolean matchesLegacyDefaults(String[] ids) {
        if (ids.length != KEY_COUNT) {
            return false;
        }
        String[] legacyDefaults = legacyDefaultIds();
        for (int index = 0; index < KEY_COUNT; index++) {
            if (!fromId(ids[index]).id.equals(legacyDefaults[index])) {
                return false;
            }
        }
        return true;
    }
}
