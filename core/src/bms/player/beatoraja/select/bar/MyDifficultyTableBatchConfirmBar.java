package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.select.MusicSelector;

import static bms.player.beatoraja.SystemSoundManager.SoundType.OPTION_CHANGE;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SEARCH;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SPECIAL;

/** Confirmation shown after holding START+SELECT in My Difficulty Table batch edit mode. */
public final class MyDifficultyTableBatchConfirmBar extends DirectoryBar {
    public MyDifficultyTableBatchConfirmBar(MusicSelector selector) {
        super(selector, true);
        setSortable(false);
    }

    @Override
    public String getTitle() {
        return "一括編集を反映しますか？";
    }

    @Override
    public int getLamp(boolean isPlayer) {
        return 0;
    }

    @Override
    public Bar[] getChildren() {
        return new Bar[] {
                new FunctionBar((currentSelector, self) -> {
                    if (BMSIRArenaClient.applyMyDifficultyTableBatchChanges()) {
                        currentSelector.play(OPTION_CHANGE);
                    }
                }, "はい（反映する）", STYLE_SPECIAL),
                action("いいえ（編集に戻る）", STYLE_SEARCH, () -> {}),
                new FunctionBar((currentSelector, self) -> {
                    BMSIRArenaClient.closeMyDifficultyTableBatchConfirmation();
                    currentSelector.getBarManager().updateBar(new MyDifficultyTableBatchEditorBar(currentSelector));
                    currentSelector.play(OPTION_CHANGE);
                }, "編集対象レベルを変更する", FunctionBar.STYLE_TABLE),
                action("変更を破棄して終了", STYLE_SEARCH,
                        BMSIRArenaClient::cancelMyDifficultyTableBatchEdit)
        };
    }

    private FunctionBar action(String title, int style, Runnable action) {
        return new FunctionBar((currentSelector, self) -> {
            action.run();
            BMSIRArenaClient.closeMyDifficultyTableBatchConfirmation();
            currentSelector.getBarManager().close();
            currentSelector.play(OPTION_CHANGE);
        }, title, style);
    }
}
