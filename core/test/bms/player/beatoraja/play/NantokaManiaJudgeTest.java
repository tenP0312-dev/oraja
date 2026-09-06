package bms.player.beatoraja.play;

import bms.model.*;
import bms.player.beatoraja.arena.bmsir.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NantokaManiaJudgeTest {
    record Result(int lane, Note note, int judge, long time) { }
    static final class Fixture implements NantokaManiaJudge.Listener {
        final BMSModel model = new BMSModel();
        final TreeMap<Long, TimeLine> lines = new TreeMap<>();
        final List<Result> results = new ArrayList<>();
        final List<Integer> recoveries = new ArrayList<>();
        final List<LongNote> suppressed = new ArrayList<>();
        NantokaManiaJudge engine;
        Fixture(Mode mode) { model.setMode(mode); model.setBpm(120); model.setSHA256("test"); }
        Note note(int lane, long time) { return add(lane, time, new NormalNote(1)); }
        Note add(int lane, long time, Note note) {
            TimeLine line = lines.computeIfAbsent(time, t -> new TimeLine(t / 2_000_000.0, t, model.getMode().key));
            line.setBPM(120); line.setNote(lane, note); return note;
        }
        LongNote hold(int lane, long start, long end, int type) {
            LongNote first = new LongNote(1), last = new LongNote(1);
            add(lane, start, first); add(lane, end, last);
            first.setType(type); last.setType(type); first.setPair(last); return first;
        }
        Fixture begin(boolean autoplay) {
            model.setAllTimeLine(lines.values().toArray(TimeLine[]::new));
            BMSIRManiacSettings settings = new BMSIRManiacSettings(); settings.setNantokaMania(true);
            BMSIRManiacPlayContext.prepare(settings, model, false);
            engine = new NantokaManiaJudge(model, autoplay, this); return this;
        }
        void input(int lane, int dir, boolean down, long time) {
            engine.advanceTo(time - 1); engine.input(lane, dir, down, time); engine.advanceTo(time);
        }
        public void judge(int lane, Note note, int judge, long at, long difference) {
            results.add(new Result(lane, note, judge, at));
        }
        public void suppress(int lane, LongNote end) { suppressed.add(end); }
        public void recover(int lane) { recoveries.add(lane); }
        public void sound(int lane, Note note) { }
        public void mine(int lane, MineNote note) { }
        List<Integer> grades() { return results.stream().map(Result::judge).toList(); }
    }

    @Test void comboPriorityDoesNotAddTheEarlierBadToTheSamePress() {
        Fixture f = new Fixture(Mode.BEAT_7K);
        Note earlier = f.note(0, 1_000_000), later = f.note(0, 1_160_000);
        f.begin(false); f.input(0, 0, true, 1_160_000);
        assertEquals(List.of(0), f.grades()); assertSame(later, f.results.get(0).note());
        assertEquals(0, earlier.getState());
        f.engine.advanceTo(1_250_251); assertEquals(List.of(0,4), f.grades());
    }

    @Test void emptyPoorDoesNotConsumeTheNoteAndCannotTargetProcessedNotes() {
        Fixture f = new Fixture(Mode.BEAT_7K); Note note = f.note(0, 1_000_000);
        f.begin(false); f.input(0, 0, true, 700_000); assertEquals(0, note.getState());
        f.input(0, 0, false, 701_000); f.input(0, 0, true, 1_000_000);
        f.input(0, 0, false, 1_001_000); f.input(0, 0, true, 1_002_000);
        assertEquals(List.of(5,0), f.grades());
    }

    @Test void bothDpScratchesUseTheExpandedLateWindow() {
        Fixture f = new Fixture(Mode.BEAT_14K);
        for (int lane : Mode.BEAT_14K.scratchKey) f.note(lane, 1_000_000);
        f.begin(false);
        for (int lane : Mode.BEAT_14K.scratchKey) f.input(lane, lane, true, 1_270_000);
        assertEquals(List.of(3,3), f.grades());
    }

    @Test void devicePollingDoesNotMoveAnInputAcrossThePerfectBoundary() {
        Fixture f = new Fixture(Mode.BEAT_7K); f.note(0, 1_000_000);
        f.begin(false); f.engine.advanceTo(1_016_917);
        f.engine.input(0, 0, true, 1_016_916);
        assertEquals(List.of(0), f.grades());
        assertEquals(1_016_916, f.results.get(0).time());
    }

    @Test void sharedTickMatchesSmallUpdatesAndOneLargeReplayUpdate() {
        Fixture small = new Fixture(Mode.BEAT_7K), large = new Fixture(Mode.BEAT_7K);
        for (Fixture f : List.of(small, large)) {
            f.hold(0, 100_000, 1_000_000, LongNote.TYPE_HELLCHARGENOTE);
            f.hold(1, 120_000, 1_000_000, LongNote.TYPE_HELLCHARGENOTE);
            f.begin(false); f.input(0, 0, true, 100_000);
        }
        for (long t = 110_000; t <= 1_400_000; t += 10_000) small.engine.advanceTo(t);
        large.engine.advanceTo(1_400_000);
        assertEquals(small.grades(), large.grades());
        assertEquals(small.results.stream().map(Result::time).toList(),
                large.results.stream().map(Result::time).toList());
        assertEquals(small.recoveries, large.recoveries);
    }

    @Test void preHeldKeyRecoversHcnWithoutAddingAStartJudgment() {
        Fixture f = new Fixture(Mode.BEAT_7K);
        f.hold(0, 100_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false); f.engine.initiallyHeld(0, 0); f.engine.advanceTo(133_333);
        assertTrue(f.grades().isEmpty()); assertEquals(List.of(0), f.recoveries);
        f.input(0, 0, false, 200_000); f.engine.advanceTo(266_666);
        assertEquals(List.of(5), f.grades());
    }

    @Test void legacyLnCountsAndScoresBothEndsWithoutChangingItsType() {
        Fixture f = new Fixture(Mode.BEAT_7K);
        LongNote ln = f.hold(0, 1_000_000, 2_000_000, LongNote.TYPE_LONGNOTE);
        f.begin(false); f.input(0, 0, true, 1_000_000); f.engine.advanceTo(2_000_000);
        assertEquals(List.of(0,0), f.grades());
        assertEquals(2, NantokaManiaRules.totalNotes(f.model));
        assertEquals(1, f.model.getTotalNotes());
        assertEquals(LongNote.TYPE_LONGNOTE, ln.getType());
        assertNull(f.engine.processing(0));
    }

    @Test void cnBadStartSuppressesTheEndWithoutAnotherJudgment() {
        Fixture f = new Fixture(Mode.BEAT_7K);
        LongNote ln = f.hold(0, 1_000_000, 2_000_000, LongNote.TYPE_CHARGENOTE);
        f.begin(false); f.input(0, 0, true, 800_000); f.engine.advanceTo(2_500_000);
        assertEquals(List.of(3), f.grades()); assertEquals(List.of(ln.getPair()), f.suppressed);
    }

    @Test void cnReleasePromotesGoodToPerfectAndKeepsBad() {
        for (long release : new long[]{1_900_000, 1_800_000}) {
            Fixture f = new Fixture(Mode.BEAT_7K);
            f.hold(0, 1_000_000, 2_000_000, LongNote.TYPE_CHARGENOTE);
            f.begin(false); f.input(0, 0, true, 1_000_000); f.input(0, 0, false, release);
            assertEquals(List.of(0, release == 1_900_000 ? 0 : 3), f.grades());
        }
    }

    @Test void hcnTicksShareTheirPhaseAndReentryAddsNoJudgment() {
        Fixture f = new Fixture(Mode.BEAT_7K);
        f.hold(0, 100_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
        f.hold(1, 120_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false); f.input(0, 0, true, 100_000); f.input(1, 1, true, 120_000);
        f.engine.advanceTo(133_333); assertEquals(List.of(0,1), f.recoveries);
        f.input(0, 0, false, 200_000); f.engine.advanceTo(266_666);
        assertEquals(List.of(0,0,5), f.grades());
        f.input(0, 0, true, 300_000); f.engine.advanceTo(400_000);
        assertEquals(List.of(0,0,5), f.grades());
        assertEquals(List.of(0,1,1,0,1), f.recoveries);
    }

    @Test void missedHcnStartKeepsBodyAndEndForReentry() {
        Fixture f = new Fixture(Mode.BEAT_7K);
        LongNote ln = f.hold(0, 100_000, 900_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(false); f.engine.advanceTo(350_251);
        assertEquals(5, ln.getState()); assertEquals(0, ln.getPair().getState());
        int count = f.results.size(); f.input(0, 0, true, 370_000);
        assertEquals(count, f.results.size()); f.engine.advanceTo(400_000);
        assertEquals(List.of(0), f.recoveries);
        f.input(0, 0, false, 890_000); assertEquals(1, ln.getPair().getState());
    }

    @Test void scratchStopDoesNotCompleteBssButReverseDoes() {
        Fixture f = new Fixture(Mode.BEAT_7K);
        LongNote ln = f.hold(7, 1_000_000, 2_000_000, LongNote.TYPE_CHARGENOTE);
        f.begin(false); f.input(7, 7, true, 1_000_000); f.input(7, 7, false, 1_900_000);
        assertEquals(0, ln.getPair().getState()); f.input(7, 8, true, 2_000_000);
        assertEquals(List.of(0,0), f.grades());
    }

    @Test void autoplayProcessesEveryLongNoteEnd() {
        Fixture f = new Fixture(Mode.BEAT_14K);
        f.hold(0, 100_000, 200_000, LongNote.TYPE_LONGNOTE);
        f.hold(7, 100_000, 200_000, LongNote.TYPE_CHARGENOTE);
        f.hold(15, 100_000, 200_000, LongNote.TYPE_HELLCHARGENOTE);
        f.begin(true); f.engine.advanceTo(500_000);
        assertEquals(Collections.nCopies(6, 0), f.grades());
    }
}
