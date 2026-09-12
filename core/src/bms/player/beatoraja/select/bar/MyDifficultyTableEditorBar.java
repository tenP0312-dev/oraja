package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
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
        return BMSIRArenaI18n.text(
                "マイ難易度表を編集（レベル）",
                "Edit My Difficulty Table (level)"
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
                BMSIRArenaClient.myDifficultyTableEditorState(song);
        if (state.busy()) {
            options.add(action(
                    BMSIRArenaI18n.text("マイ難易度表を通信中です", "My Difficulty Table request in progress"),
                    STYLE_SEARCH,
                    () -> {}
            ));
            return options.toArray(new Bar[0]);
        }
        if (!state.ready()) {
            options.add(action(BMSIRArenaI18n.text("マイ難易度表を再読み込み", "Reload My Difficulty Table"), STYLE_SPECIAL, () ->
                    BMSIRArenaClient.reloadMyDifficultyTable()));
            return options.toArray(new Bar[0]);
        }
        if (state.selectionRequired()) {
            options.add(action(BMSIRArenaI18n.text(
                    "編集するマイ難易度表を選択してください",
                    "Select the My Difficulty Table to edit"
            ), STYLE_SPECIAL, () ->
                    BMSIRArenaClient.reloadMyDifficultyTable()));
            return options.toArray(new Bar[0]);
        }
        if (state.levels().isEmpty()) {
            options.add(action(BMSIRArenaI18n.text(
                    "登録済みレベルがありません",
                    "There are no registered levels"
            ), STYLE_SEARCH, () -> {}));
        } else if (state.levelEditable()) {
            for (String level : state.levels()) {
                String displayLevel = state.symbol().isBlank()
                        ? BMSIRArenaI18n.text("レベル", "Level ") + level
                        : state.symbol() + level;
                options.add(action(displayLevel + BMSIRArenaI18n.text(
                        state.entryExists() ? " への変更を保留" : " への追加を保留",
                        state.entryExists() ? ": stage change" : ": stage addition"
                ), STYLE_TABLE,
                        () -> BMSIRArenaClient.stageMyDifficultyTableLevel(song, level)));
            }
        }
        if (state.entryExists() && !state.removalStaged()) {
            options.add(action(BMSIRArenaI18n.text("表から削除を保留", "Stage removal from the table"), STYLE_SPECIAL, () ->
                    BMSIRArenaClient.stageMyDifficultyTableRemoval(song)));
        }
        if (state.pendingChangeCount() > 0) {
            options.add(action(BMSIRArenaI18n.text(
                    state.pendingChangeCount() + "件の保留を一括反映",
                    "Apply " + state.pendingChangeCount() + " pending changes"
            ), STYLE_SPECIAL, () ->
                    BMSIRArenaClient.applyMyDifficultyTableChanges()));
            options.add(action(BMSIRArenaI18n.text("保留中の編集をすべて破棄", "Discard all pending edits"), STYLE_SEARCH, () ->
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
