package bms.player.beatoraja.play;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NantokaManiaRulesTest {
    @Test void fixedWindowsIgnoreChartRankAndCustomRates() {
        for (int rank : new int[]{0, 25, 75, 100, 400}) {
            long[][] window = JudgeProperty.NANTOKA_MANIA.getJudge(
                    JudgeProperty.NoteType.NOTE, rank, new int[]{0, 200, 300});
            assertArrayEquals(new long[]{-16_916, 16_416}, window[0]);
            assertArrayEquals(new long[]{-116_916, 116_416}, window[2]);
        }
    }

    @Test void grooveGaugeAdapterUsesModeRulesAndKeepsOrdinaryProfileUnchanged() {
        var f = new NantokaManiaJudgeTest.Fixture(bms.model.Mode.BEAT_7K);
        for (int i = 0; i < 100; i++) f.note(0, 1_000_000L + i * 100_000L);
        f.begin(false);
        GrooveGauge gauge = GrooveGauge.create(f.model, GrooveGauge.NORMAL, 0, GaugeProperty.LR2);
        assertEquals(22f, gauge.getValue()); gauge.update(0);
        assertEquals(24.66f, gauge.getValue(), 0.0001f);
        assertEquals(24, gauge.getGauge().getDisplayValue());
        gauge.setValue(79.98f); assertFalse(gauge.isQualified());
        gauge.setValue(80f); assertTrue(gauge.isQualified());
        assertSame(GaugeProperty.GaugeElementProperty.NORMAL_NANTOKA, gauge.getGauge().getProperty());
        f.model.getValues().clear();
        GrooveGauge normal = GrooveGauge.create(f.model, GrooveGauge.NORMAL, 0, GaugeProperty.LR2);
        assertSame(GaugeProperty.GaugeElementProperty.NORMAL_LR2, normal.getGauge().getProperty());
    }

    @Test void everyBoundaryKeepsItsSpecifiedOpenAndClosedSide() {
        for (boolean scratch : new boolean[]{false, true}) {
            int[] seeds = scratch ? new int[]{-17,-9,-4,-1,0,1,4,9,17}
                    : new int[]{-15,-7,-2,-1,0,1,2,7,15};
            int[] at = {5,3,2,1,0,0,1,2,3};
            int[] after = {3,2,1,0,0,1,2,3,6};
            for (int i = 0; i < seeds.length; i++) {
                long boundary = NantokaManiaRules.boundary(seeds[i]);
                assertEquals(at[i], NantokaManiaRules.judge(boundary, scratch), "at " + seeds[i]);
                assertEquals(after[i], NantokaManiaRules.judge(boundary + 1, scratch), "after " + seeds[i]);
            }
            assertEquals(6, NantokaManiaRules.judge(-350_000, scratch));
            assertEquals(5, NantokaManiaRules.judge(-349_999, scratch));
        }
    }

    @Test void extendedLateWindowIncludesScratchAt270Milliseconds() {
        assertEquals(3, NantokaManiaRules.judge(270_000, true));
        assertEquals(6, NantokaManiaRules.judge(270_000, false));
        assertEquals(3, NantokaManiaRules.judge(250_250, false));
        assertEquals(6, NantokaManiaRules.judge(250_251, false));
        assertArrayEquals(new long[]{-16_916, 16_416}, NantokaManiaRules.windows(false)[0]);
    }

    @Test void recoveryUsesIntegerEffectiveNotesIncluding350Boundary() {
        for (int[] sample : new int[][]{{0,0,0},{100,133,66},{349,38,19},
                {350,38,19},{353,37,18},{2000,14,7}}) {
            NantokaManiaGauge pg = new NantokaManiaGauge(GrooveGauge.NORMAL, sample[0]);
            pg.update(0, 1);
            assertEquals(1100 + sample[1], pg.units());
            NantokaManiaGauge good = new NantokaManiaGauge(GrooveGauge.NORMAL, sample[0]);
            good.update(2, 1);
            assertEquals(1100 + sample[2], good.units());
        }
    }

    @Test void hardAndDanCorrectionUsePreJudge1666ThresholdAndIntegerDivision() {
        NantokaManiaGauge hard = new NantokaManiaGauge(GrooveGauge.HARD, 100);
        hard.setUnits(1666); hard.update(3, 1); assertEquals(1416, hard.units());
        hard.setUnits(1665); hard.update(5, 1); assertEquals(1540, hard.units());
        hard.setUnits(1665); hard.update(4, 1); assertEquals(1440, hard.units());
        NantokaManiaGauge dan = new NantokaManiaGauge(GrooveGauge.CLASS, 100);
        dan.setUnits(1665); dan.update(3, 1); assertEquals(1623, dan.units());
        dan.setUnits(1665); dan.update(4, 1); assertEquals(1603, dan.units());
        NantokaManiaGauge ex = new NantokaManiaGauge(GrooveGauge.EXHARD, 100);
        ex.setUnits(1665); ex.update(3, 1); assertEquals(1165, ex.units());
    }

    @Test void failureFloorsAndDisplayRemainSeparate() {
        NantokaManiaGauge hard = new NantokaManiaGauge(GrooveGauge.HARD, 100);
        hard.setUnits(225); hard.update(3, 1); assertEquals(100, hard.units());
        hard.update(0, 1); assertEquals(108, hard.units());
        hard.update(3, 1); assertEquals(0, hard.units());
        hard.update(0, 1); assertEquals(0, hard.units());
        NantokaManiaGauge normal = new NantokaManiaGauge(GrooveGauge.NORMAL, 100);
        normal.setUnits(150); normal.update(4, 1); assertEquals(100, normal.units());
        normal.setUnits(1199); assertEquals(22, normal.displayPercent());
        assertEquals(23.98f, normal.percent(), 0.0001f);
        normal.setUnits(4999); normal.update(0, 1); assertEquals(5000, normal.units());
        NantokaManiaGauge hazard = new NantokaManiaGauge(GrooveGauge.HAZARD, 100);
        hazard.update(2, 1); assertEquals(5000, hazard.units());
        hazard.update(5, 1); assertEquals(0, hazard.units());
    }
}
