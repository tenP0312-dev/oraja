package bms.player.beatoraja.arena.bmsir;

import java.util.Arrays;

/** Actions assignable to the physical numeric keypad. */
public enum BMSIRNumpadAction {
    NONE("none", "なし"),
    JUDGE_AUTO("judge_auto", "判定タイミング自動調整 ON/OFF"),
    JUDGE_PLUS("judge_plus", "判定タイミング +"),
    JUDGE_MINUS("judge_minus", "判定タイミング -"),
    KEY_CONFIG("key_config", "キーコンフィグ"),
    SKIN_CONFIG("skin_config", "スキンコンフィグ"),
    BMS_SEARCH("bms_search", "BMS検索"),
    MODE_FILTER("mode_filter", "キーモードフィルター変更"),
    SORT("sort", "ソート変更"),
    REPLAY("replay", "リプレイ切替"),
    RIVAL("rival", "ライバル変更"),
    SAME_FOLDER("same_folder", "同一フォルダ譜面表示"),
    OPEN_DOCUMENT("open_document", "同梱テキスト表示"),
    OPEN_IR("open_ir", "IR表示"),
    FAVORITE_SONG("favorite_song", "曲のお気に入り"),
    FAVORITE_CHART("favorite_chart", "譜面のお気に入り"),
    UPDATE_FOLDER("update_folder", "曲フォルダ更新"),
    OPEN_FOLDER("open_folder", "選択曲のフォルダを開く"),
    PRACTICE("practice", "プラクティス開始"),
    AUTOPLAY("autoplay", "オートプレイ開始"),
    ARENA_OVERLAY("arena_overlay", "Arenaオーバーレイ ON/OFF"),
    MOD_MENU("mod_menu", "Modメニュー ON/OFF"),
    FPS("fps", "FPS表示 ON/OFF"),
    FULLSCREEN("fullscreen", "フルスクリーン ON/OFF"),
    SCREENSHOT("screenshot", "スクリーンショット");

    public static final int KEY_COUNT = 10;

    private final String id;
    private final String label;

    BMSIRNumpadAction(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
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
        return normalized;
    }
}
