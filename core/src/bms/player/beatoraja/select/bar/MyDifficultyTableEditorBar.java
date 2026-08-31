package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.song.SongData;

import java.util.ArrayList;

import static bms.player.beatoraja.SystemSoundManager.SoundType.OPTION_CHANGE;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SEARCH;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SPECIAL;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_TABLE;

/** Controller-navigable editor for the selected chart in the owner's My Difficulty Table. */
public final class MyDifficultyTableEditorBar extends DirectoryBar {
    private final SongData song;

    public MyDifficultyTableEditorBar(MusicSelector selector, SongData song) {
        super(selector, true);
        this.song = song;
        setSortable(false);
    }

    @Override
    public String getTitle() {
        return "マイ難易度表を編集(レベル)";
    }

    @Override
    public int getLamp(boolean isPlayer) {
        return 0;
    }

    @Override
    public Bar[] getChildren() {
        ArrayList<Bar> options = new ArrayList<>();
        BMSIRArenaClient.MyDifficultyTableEditorState state =
                BMSIRArenaClient.myDifficultyTableEditorState(song);
        if (!state.ready()) {
            options.add(action("マイ難易度表を再読み込み", STYLE_SPECIAL, () ->
                    BMSIRArenaClient.reloadMyDifficultyTable()));
            return options.toArray(new Bar[0]);
        }
        if (state.selectionRequired()) {
            options.add(action("編集するマイ難易度表を選択してください", STYLE_SPECIAL, () ->
                    BMSIRArenaClient.reloadMyDifficultyTable()));
            return options.toArray(new Bar[0]);
        }
        if (state.levels().isEmpty()) {
            options.add(action("登録済みレベルがありません", STYLE_SEARCH, () -> {}));
        } else if (state.levelEditable()) {
            for (String level : state.levels()) {
                options.add(action(state.symbol() + level + " に"
                        + (state.entryExists() ? "変更を保留" : "追加を保留"), STYLE_TABLE,
                        () -> BMSIRArenaClient.stageMyDifficultyTableLevel(song, level)));
            }
        }
        if (state.entryExists() && !state.removalStaged()) {
            options.add(action("表から削除を保留", STYLE_SPECIAL, () ->
                    BMSIRArenaClient.stageMyDifficultyTableRemoval(song)));
        }
        if (state.pendingChangeCount() > 0) {
            options.add(action(state.pendingChangeCount() + "件の保留を一括反映", STYLE_SPECIAL, () ->
                    BMSIRArenaClient.applyMyDifficultyTableChanges()));
            options.add(action("保留中の編集をすべて破棄", STYLE_SEARCH, () ->
                    BMSIRArenaClient.discardMyDifficultyTableChanges()));
        }
        return options.toArray(new Bar[0]);
    }

    private FunctionBar action(String title, int style, Runnable action) {
        return new FunctionBar((currentSelector, self) -> {
            action.run();
            currentSelector.getBarManager().updateBar();
            currentSelector.play(OPTION_CHANGE);
        }, title, style);
    }
}
