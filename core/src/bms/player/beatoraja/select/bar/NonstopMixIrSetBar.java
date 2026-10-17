package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.nonstop.MixIrClient;
import bms.player.beatoraja.nonstop.MixJson;
import bms.player.beatoraja.nonstop.MixPlan;
import bms.player.beatoraja.nonstop.MixStore;
import bms.player.beatoraja.select.MusicSelector;
import com.badlogic.gdx.Gdx;

import java.io.IOException;

import static bms.player.beatoraja.SystemSoundManager.SoundType.OPTION_CHANGE;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SEARCH;
import static bms.player.beatoraja.select.bar.NonstopMixFolderBar.t;

/**
 * One published set in the IR folder (#539). Entering it downloads the set
 * (once per revision), keeps it as a saved set like the editor's import, and
 * then shows the same rows as a saved set.
 */
public final class NonstopMixIrSetBar extends DirectoryBar {
    private final MixIrClient.Listing listing;
    private volatile NonstopMixSetBar set;
    private volatile boolean busy;
    private volatile String error = "";

    public NonstopMixIrSetBar(MusicSelector selector, MixIrClient.Listing listing) {
        super(selector, true);
        this.listing = listing;
        setSortable(false);
    }

    @Override
    public String getTitle() {
        String name = listing.name == null || listing.name.isBlank() ? t("（名前のないセット）", "(untitled set)") : listing.name;
        return listing.author == null || listing.author.isBlank() ? name : name + " / " + listing.author;
    }

    @Override
    public int getLamp(boolean isPlayer) {
        return 0;
    }

    @Override
    public Bar[] getChildren() {
        NonstopMixSetBar ready = set;
        if (ready != null) {
            return ready.getChildren();
        }
        if (!busy && error.isEmpty() && selector != null) {
            fetch();
        }
        if (!error.isEmpty()) {
            return new Bar[] {new KeyedFunctionBar("status", (current, self) -> {
                error = "";
                refresh(current);
            }, error + t("（決定でもう一度）", " (press to retry)"), STYLE_SEARCH)};
        }
        return new Bar[] {new KeyedFunctionBar("status", (current, self) -> refresh(current),
                t("IRからセットを取得しています（決定で更新）", "Downloading the set (press to refresh)"), STYLE_SEARCH)};
    }

    private void fetch() {
        MixStore store = NonstopMixFolderBar.store(selector);
        MixPlan local = saved(store, listing);
        if (local != null) {
            set = new NonstopMixSetBar(selector, local, store.best(MixJson.playIdentity(local)));
            return;
        }
        busy = true;
        MixIrClient.get(selector.main, listing.id, result -> {
            if (Gdx.app == null) {
                return;
            }
            Gdx.app.postRunnable(() -> {
                busy = false;
                if (!result.ok()) {
                    error = result.error();
                } else {
                    MixPlan plan = result.value();
                    try {
                        store.saveSet(plan);
                    } catch (IOException e) {
                        // still playable this session; the editor can save it later
                    }
                    set = new NonstopMixSetBar(selector, plan, store.best(MixJson.playIdentity(plan)));
                }
                NonstopMixFolderBar.refreshNow(selector);
            });
        });
    }

    /** A set already downloaded at this revision, so entering again does not add a copy. */
    static MixPlan saved(MixStore store, MixIrClient.Listing listing) {
        for (MixPlan plan : store.listSets()) {
            if (listing.id.equals(plan.irId) && plan.irRevision == listing.revision) {
                return plan;
            }
        }
        return null;
    }

    private static void refresh(MusicSelector selector) {
        selector.getBarManager().updateBar();
        selector.play(OPTION_CHANGE);
    }
}
