package bms.player.beatoraja.modmenu;

/** Stable player-config identifiers; independent of labels and menu ordering. */
public enum ModMenuItem {
    RATE_MODIFIER("rate_modifier", "再生速度変更", "Rate Modifier"),
    RANDOM_TRAINER("random_trainer", "RANDOM配置指定", "Random Trainer"),
    JUDGE_TRAINER("judge_trainer", "判定トレーナー", "Judge Trainer"),
    SKIN_CONFIGURATION("skin_configuration", "スキン設定", "Skin Configuration"),
    SKIN_WIDGET_MANAGER("skin_widget_manager", "スキンウィジェット管理", "Skin Widget Manager"),
    SONG_MANAGER("song_manager", "楽曲管理", "Song Manager"),
    DOWNLOAD_TASKS("download_tasks", "ダウンロード状況", "Download Tasks"),
    PERFORMANCE_MONITOR("performance_monitor", "パフォーマンスモニター", "Performance Monitor"),
    MISC_SETTINGS("misc_settings", "その他設定", "Misc Settings"),
    LEGACY_ARENA_MENU("legacy_arena_menu", "従来Arenaメニュー", "Legacy Arena Menu"),
    LEGACY_ARENA_GRAPH("legacy_arena_graph", "従来Arenaグラフ", "Legacy Arena Graph"),
    BMSIR_ARENA_OVERLAY("bmsir_arena_overlay", "BMS-IR Arenaオーバーレイ", "BMS-IR Arena Overlay"),
    ARENA_DEBUG("arena_debug", "Arena orajaデバッグ情報", "Arena oraja Debug Information"),
    MANIAC_OPTIONS("maniac_options", "MANIAC OPTIONS", "MANIAC OPTIONS");

    public final String id;
    public final String displayName;
    public final String englishName;

    ModMenuItem(String id, String displayName, String englishName) {
        this.id = id;
        this.displayName = displayName;
        this.englishName = englishName;
    }
}
