package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.select.MusicSelector;

import java.util.ArrayList;

import static bms.player.beatoraja.SystemSoundManager.SoundType.FOLDER_OPEN;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SEARCH;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SPECIAL;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_TABLE;

/** Level picker for controller-only multi-chart My Difficulty Table editing. */
public final class MyDifficultyTableBatchEditorBar extends DirectoryBar {
    public MyDifficultyTableBatchEditorBar(MusicSelector selector) {
        super(selector, true);
        setSortable(false);
    }

    @Override
    public String getTitle() {
        return BMSIRArenaI18n.text(
                "マイ難易度表を編集（一括）",
                "Edit My Difficulty Table (batch)"
        );
    }

    @Override
    public int getLamp(boolean isPlayer) {
        return 0;
    }

    @Override
    public Bar[] getChildren() {
        ArrayList<Bar> options = new ArrayList<>();
        BMSIRArenaClient.MyDifficultyTableEditorState state =
                BMSIRArenaClient.myDifficultyTableEditorState(null);
        if (state.busy()) {
            options.add(new FunctionBar((currentSelector, self) -> {},
                    BMSIRArenaI18n.text("マイ難易度表を通信中です", "My Difficulty Table request in progress"),
                    STYLE_SEARCH));
        } else if (!state.ready() || state.selectionRequired()) {
            options.add(new FunctionBar((currentSelector, self) ->
                    BMSIRArenaClient.reloadMyDifficultyTable(),
                    BMSIRArenaI18n.text("マイ難易度表を再読み込み", "Reload My Difficulty Table"),
                    STYLE_SPECIAL));
        } else if (!state.levelEditable()) {
            options.add(new FunctionBar((currentSelector, self) -> {},
                    BMSIRArenaI18n.text(
                            "この表のレベルはマスター表から同期されます",
                            "Levels in this table are synchronized from its master table"
                    ), STYLE_SEARCH));
        } else if (state.levels().isEmpty()) {
            options.add(new FunctionBar((currentSelector, self) -> {},
                    BMSIRArenaI18n.text("登録済みレベルがありません", "There are no registered levels"),
                    STYLE_SEARCH));
        } else {
            for (String level : state.levels()) {
                String displayLevel = state.symbol().isBlank()
                        ? BMSIRArenaI18n.text("レベル", "Level ") + level
                        : state.symbol() + level;
                options.add(new FunctionBar((currentSelector, self) -> {
                    if (BMSIRArenaClient.startMyDifficultyTableBatchEdit(level)) {
                        currentSelector.getBarManager().updateBar(null);
                        currentSelector.play(FOLDER_OPEN);
                    }
                }, BMSIRArenaI18n.text(displayLevel + " を編集", "Edit " + displayLevel), STYLE_TABLE));
            }
        }
        return options.toArray(new Bar[0]);
    }
}
