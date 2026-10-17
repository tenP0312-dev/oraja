package bms.player.beatoraja.nonstop;

import bms.model.BMSModel;
import bms.model.TimeLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static bms.player.beatoraja.nonstop.MixCompilerTest.chart;
import static bms.player.beatoraja.nonstop.MixCompilerTest.plan;
import static bms.player.beatoraja.nonstop.MixCompilerTest.source;
import static org.junit.jupiter.api.Assertions.*;

/** "BGAを使わない" and the heavy-set estimate (Issue #556). */
class MixBgaTest {

    private static final String BGA = "#BMP01 a.png\n#BMP02 b.mp4\n#BACKBMP back.png\n"
            + "#00104:01\n#00204:02\n#00107:01\n#00306:01\n";

    private static boolean hasBgaEvents(BMSModel model) {
        for (TimeLine tl : model.getAllTimeLines()) {
            if (tl.getBGA() != -1 || tl.getLayer() != -1 || tl.getEventlayer().length > 0) {
                return true;
            }
        }
        return false;
    }

    @Test
    void compilesWithBgaByDefault() {
        MixPlan plan = plan(2);
        CompiledMix mix = MixCompiler.compile(plan, List.of(source(chart(120, BGA), "a"), source(chart(120, BGA), "b")));
        assertFalse(mix.hasErrors(), mix.issues().toString());
        assertEquals(4, mix.model().getBgaList().length);
        assertTrue(hasBgaEvents(mix.model()));
        assertNotNull(mix.model().getBackbmp());
        assertEquals(2, mix.load().movies());
        assertEquals(2, mix.load().images());
    }

    @Test
    void noBgaLeavesOutBgaLayersAndBackbmpButKeepsThePlay() {
        MixPlan with = plan(2);
        MixPlan without = plan(2);
        without.noBga = true;
        CompiledMix a = MixCompiler.compile(with, List.of(source(chart(120, BGA), "a"), source(chart(120, BGA), "b")));
        CompiledMix b = MixCompiler.compile(without, List.of(source(chart(120, BGA), "a"), source(chart(120, BGA), "b")));
        assertFalse(b.hasErrors(), b.issues().toString());
        assertEquals(0, b.model().getBgaList().length);
        assertFalse(hasBgaEvents(b.model()));
        String backbmp = b.model().getBackbmp();
        assertTrue(backbmp == null || backbmp.isEmpty());

        // the same notes, sounds and identity: records stay with the same set
        assertEquals(a.identity(), b.identity());
        assertEquals(MixCompilerTest.playable(a.model(), Long.MIN_VALUE, Long.MAX_VALUE),
                MixCompilerTest.playable(b.model(), Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(a.model().getTotal(), b.model().getTotal());
        assertArrayEquals(a.chartEnds(), b.chartEnds());
        // the estimate still counts the files that would be loaded with BGA on
        assertEquals(a.load(), b.load());
        // and nothing about BGA is suggested once it is off
        assertFalse(b.load().suggestNoBga(true));
    }

    @Test
    void noBgaIsSavedInTheRecipeButNotInThePlayIdentity() {
        MixPlan plan = plan(1);
        String identity = MixJson.playIdentity(plan);
        plan.noBga = true;
        assertEquals(identity, MixJson.playIdentity(plan));
        assertTrue(MixJson.fromJson(MixJson.toJson(plan, true)).noBga);
        assertTrue(MixJson.fromJson(MixJson.toJson(plan, false)).noBga);
        assertFalse(MixJson.fromJson(MixJson.toJson(plan(1), true)).noBga);
    }

    @Test
    void longOrHeavySetsSuggestTurningBgaOff() {
        long minute = 60_000L;
        // a short light set is fine
        assertFalse(new MixLoad(5, 10 * minute, 2, 20).suggestNoBga(false));
        // more than 12 songs, or longer than 20 minutes
        assertTrue(new MixLoad(13, 10 * minute, 1, 0).suggestNoBga(false));
        assertFalse(new MixLoad(12, 20 * minute, 1, 0).suggestNoBga(false));
        assertTrue(new MixLoad(8, 21 * minute, 0, 1).suggestNoBga(false));
        // many movies or images even in a short set
        assertTrue(new MixLoad(4, 8 * minute, MixLoad.MOVIE_LIMIT, 0).suggestNoBga(false));
        assertFalse(new MixLoad(4, 8 * minute, MixLoad.MOVIE_LIMIT - 1, 0).suggestNoBga(false));
        assertTrue(new MixLoad(4, 8 * minute, 0, MixLoad.IMAGE_LIMIT).suggestNoBga(false));
        // no BGA at all, or BGA already off: nothing to suggest
        assertFalse(new MixLoad(30, 60 * minute, 0, 0).suggestNoBga(false));
        assertFalse(new MixLoad(30, 60 * minute, 20, 500).suggestNoBga(true));
    }

    @Test
    void countsMoviesByExtension() {
        MixLoad load = MixLoad.of(2, 1000, List.of(new String[] {"a.MP4", "b.bmp", "c.wmv", "", null},
                new String[] {"d.png", "noext"}, new String[0]));
        assertEquals(2, load.movies());
        assertEquals(3, load.images());
        assertEquals("BGA動画2本・BGA画像3枚", load.reason(true));
        assertTrue(new MixLoad(14, 25 * 60_000L, 7, 0).reason(false).startsWith("14 songs, about 25 min"));
    }
}
