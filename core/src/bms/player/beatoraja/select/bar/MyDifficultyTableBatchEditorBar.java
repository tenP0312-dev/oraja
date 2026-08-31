package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
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
        return "マイ難易度表を編集(一括)";
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
        if (!state.ready() || state.selectionRequired()) {
            options.add(new FunctionBar((currentSelector, self) ->
                    BMSIRArenaClient.reloadMyDifficultyTable(),
                    "マイ難易度表を再読み込み", STYLE_SPECIAL));
        } else if (state.levels().isEmpty()) {
            options.add(new FunctionBar((currentSelector, self) -> {},
                    "登録済みレベルがありません", STYLE_SEARCH));
        } else {
            for (String level : state.levels()) {
                options.add(new FunctionBar((currentSelector, self) -> {
                    if (BMSIRArenaClient.startMyDifficultyTableBatchEdit(level)) {
                        currentSelector.getBarManager().updateBar(null);
                        currentSelector.play(FOLDER_OPEN);
                    }
                }, state.symbol() + level + " を編集", STYLE_TABLE));
            }
        }
        return options.toArray(new Bar[0]);
    }
}
