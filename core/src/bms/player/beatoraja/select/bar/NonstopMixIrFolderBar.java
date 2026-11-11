package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.nonstop.MixIrClient;
import bms.player.beatoraja.select.MusicSelector;
import com.badlogic.gdx.Gdx;

import java.util.ArrayList;
import java.util.List;

import static bms.player.beatoraja.SystemSoundManager.SoundType.OPTION_CHANGE;
import static bms.player.beatoraja.select.bar.FunctionBar.STYLE_SEARCH;
import static bms.player.beatoraja.select.bar.NonstopMixFolderBar.t;

/**
 * The IR's published NONSTOP MIX sets in Music Select (#539), newest first.
 * While the IR server does not support NONSTOP MIX, the folder holds only the
 * row that says so; pressing it asks again.
 */
public final class NonstopMixIrFolderBar extends DirectoryBar {
    enum State { IDLE, LOADING, DONE, FAILED }

    /** A listing is reused for this long before the folder asks the IR again. */
    private static final long REFRESH_MS = 60_000L;

    private static volatile State state = State.IDLE;
    private static volatile List<MixIrClient.Listing> sets = List.of();
    private static volatile String error = "";
    private static volatile long loadedAt;

    public NonstopMixIrFolderBar(MusicSelector selector) {
        super(selector, true);
        setSortable(false);
    }

    @Override
    public String getTitle() {
        return t("IRのセット", "IR SETS");
    }

    @Override
    public int getLamp(boolean isPlayer) {
        return 0;
    }

    @Override
    public Bar[] getChildren() {
        if (selector != null && state != State.LOADING
                && (state == State.IDLE || System.currentTimeMillis() - loadedAt > REFRESH_MS)) {
            request(selector);
        }
        return children(selector, state, sets, error).toArray(new Bar[0]);
    }

    static List<Bar> children(MusicSelector selector, State state, List<MixIrClient.Listing> sets, String error) {
        List<Bar> children = new ArrayList<>();
        switch (state) {
            case IDLE, LOADING -> children.add(new KeyedFunctionBar("status", (current, self) -> refresh(current),
                    t("IRに問い合わせています（決定で更新）", "Asking the IR (press to refresh)"), STYLE_SEARCH));
            case FAILED -> children.add(new KeyedFunctionBar("status", (current, self) -> {
                request(current);
                refresh(current);
            }, error, STYLE_SEARCH));
            case DONE -> {
                for (MixIrClient.Listing listing : sets) {
                    children.add(new NonstopMixIrSetBar(selector, listing));
                }
                if (children.isEmpty()) {
                    children.add(new KeyedFunctionBar("status", (current, self) -> refresh(current),
                            t("公開されているセットはありません", "No published sets"), STYLE_SEARCH));
                }
            }
        }
        return children;
    }

    private static void request(MusicSelector selector) {
        state = State.LOADING;
        MixIrClient.list(selector.main, "", "", "new", 0, result -> {
            if (Gdx.app == null) {
                return;
            }
            Gdx.app.postRunnable(() -> {
                if (result.ok()) {
                    sets = List.copyOf(result.value().sets());
                    error = "";
                    state = State.DONE;
                } else {
                    sets = List.of();
                    error = result.error();
                    state = State.FAILED;
                }
                loadedAt = System.currentTimeMillis();
                NonstopMixFolderBar.refreshNow(selector);
            });
        });
    }

    private static void refresh(MusicSelector selector) {
        selector.getBarManager().updateBar();
        selector.play(OPTION_CHANGE);
    }
}
