package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaClient;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.select.MusicSelector;

import java.util.function.BooleanSupplier;

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
        return BMSIRArenaI18n.text(
                "一括編集を反映しますか？",
                "Apply the batch edits?"
        );
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
                }, BMSIRArenaI18n.text("はい（反映する）", "Yes (apply)"), STYLE_SPECIAL),
                action(
                        BMSIRArenaI18n.text("いいえ（編集に戻る）", "No (return to editing)"),
                        STYLE_SEARCH,
                        () -> true
                ),
                new FunctionBar((currentSelector, self) -> {
                    if (BMSIRArenaClient.isMyDifficultyTableBusy()) {
                        return;
                    }
                    BMSIRArenaClient.closeMyDifficultyTableBatchConfirmation();
                    currentSelector.getBarManager().updateBar(new MyDifficultyTableBatchEditorBar(currentSelector));
                    currentSelector.play(OPTION_CHANGE);
                }, BMSIRArenaI18n.text("編集対象レベルを変更する", "Change the target level"),
                        FunctionBar.STYLE_TABLE),
                action(BMSIRArenaI18n.text("変更を破棄して終了", "Discard changes and exit"), STYLE_SEARCH,
                        BMSIRArenaClient::cancelMyDifficultyTableBatchEdit)
        };
    }

    private FunctionBar action(String title, int style, BooleanSupplier action) {
        return new FunctionBar((currentSelector, self) -> {
            if (BMSIRArenaClient.isMyDifficultyTableBusy() || !action.getAsBoolean()) {
                return;
            }
            BMSIRArenaClient.closeMyDifficultyTableBatchConfirmation();
            currentSelector.getBarManager().close();
            currentSelector.play(OPTION_CHANGE);
        }, title, style);
    }
}
