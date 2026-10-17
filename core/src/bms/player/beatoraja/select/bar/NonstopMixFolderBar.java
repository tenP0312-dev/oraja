package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.nonstop.MixJson;
import bms.player.beatoraja.nonstop.MixLibrary;
import bms.player.beatoraja.nonstop.MixPlan;
import bms.player.beatoraja.nonstop.MixResult;
import bms.player.beatoraja.nonstop.MixStore;
import bms.player.beatoraja.select.MusicSelector;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Queue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SEARCH;

/**
 * Music Select's NONSTOP MIX folder (#539): the saved sets, then the IR's
 * published sets. Entering a set lists rows that play it with the decide
 * button only, so a set is started from a controller like a course. Sets are
 * still made in the F5 editor.
 */
public final class NonstopMixFolderBar extends DirectoryBar {
    private final Supplier<MixStore> store;

    public NonstopMixFolderBar(MusicSelector selector) {
        this(selector, () -> selector == null ? null : store(selector));
    }

    NonstopMixFolderBar(MusicSelector selector, Supplier<MixStore> store) {
        super(selector, true);
        this.store = store;
        setSortable(false);
    }

    static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    static MixStore store(MusicSelector selector) {
        return MixStore.forPlayer(selector.main.getConfig().getPlayerpath(), selector.main.getConfig().getPlayername());
    }

    static MixLibrary library(MusicSelector selector) {
        return selector == null ? null : MixLibrary.shared(selector.main);
    }

    @Override
    public String getTitle() {
        return "NONSTOP MIX";
    }

    @Override
    public int getLamp(boolean isPlayer) {
        return 0;
    }

    @Override
    public Bar[] getChildren() {
        MixStore sets = store.get();
        List<Bar> children = new ArrayList<>();
        if (sets != null) {
            Map<String, MixResult> best = sets.bestByIdentity();
            for (MixPlan plan : sets.listSets()) {
                children.add(new NonstopMixSetBar(selector, plan, best.get(MixJson.playIdentity(plan))));
            }
        }
        if (children.isEmpty()) {
            // a hint keeps the folder enterable and says where sets come from
            children.add(new FunctionBar((currentSelector, self) -> currentSelector.getBarManager().updateBar(),
                    t("保存済みのセットがありません（F5 → Modメニュー → NONSTOP MIXで作れます）",
                            "No saved sets (make one in F5 > Mod menu > NONSTOP MIX)"), STYLE_SEARCH));
        }
        children.add(new NonstopMixIrFolderBar(selector));
        return children.toArray(new Bar[0]);
    }

    /** Rebuilds the open folder after background work, if Music Select still shows a NONSTOP MIX bar list. */
    static void refreshWhenVisible(MusicSelector selector) {
        if (Gdx.app == null || selector == null) {
            return;
        }
        Gdx.app.postRunnable(() -> refreshNow(selector));
    }

    static void refreshNow(MusicSelector selector) {
        if (selector.main.getCurrentState() == selector && showing(selector)) {
            selector.getBarManager().updateBar();
        }
    }

    /** @return whether the open bar list is inside the NONSTOP MIX folder */
    static boolean showing(MusicSelector selector) {
        Queue<DirectoryBar> directory = selector.getBarManager().getDirectory();
        return directory.size > 0 && isNonstopMixBar(directory.last());
    }

    static boolean isNonstopMixBar(Bar bar) {
        return bar instanceof NonstopMixFolderBar || bar instanceof NonstopMixSetBar
                || bar instanceof NonstopMixIrFolderBar || bar instanceof NonstopMixIrSetBar;
    }
}
