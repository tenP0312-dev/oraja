package bms.player.beatoraja.play;

import bms.model.*;
import java.util.*;

/** Deterministic gameplay engine without rendering dependencies. */
public final class NantokaManiaJudge {
    public interface Listener {
        void judge(int lane, Note note, int judge, long time, long difference);
        void suppress(int lane, LongNote end);
        void recover(int lane);
        void sound(int lane, Note note);
        void mine(int lane, MineNote note);
    }

    private record Due(long time, int kind, int lane, Note note) { }
    private static final int ARRIVAL = 0, EXPIRY = 1;
    private static final class Track {
        final Note[] notes;
        final boolean scratch;
        final BitSet held = new BitSet();
        LongNote end;
        LongNote body;
        int direction = -1;
        int cursor;
        Track(Note[] notes, boolean scratch) { this.notes = notes; this.scratch = scratch; }
    }

    private final Track[] tracks;
    private final int lnType;
    private final boolean autoplay;
    private final Listener listener;
    private final PriorityQueue<Due> due = new PriorityQueue<>(Comparator
            .comparingLong(Due::time).thenComparingInt(Due::kind).thenComparingInt(Due::lane));
    private long time = Long.MIN_VALUE;
    private long tick = 1;

    public NantokaManiaJudge(BMSModel model, boolean autoplay, Listener listener) {
        this.lnType = model.getLntype();
        this.autoplay = autoplay;
        this.listener = listener;
        Lane[] lanes = model.getLanes();
        tracks = new Track[lanes.length];
        for (int lane = 0; lane < lanes.length; lane++) {
            boolean scratch = false;
            for (int key : model.getMode().scratchKey) if (key == lane) scratch = true;
            tracks[lane] = new Track(lanes[lane].getNotes(), scratch);
            for (Note note : tracks[lane].notes) {
                due.add(new Due(note.getMicroTime(), ARRIVAL, lane, note));
                if (note instanceof NormalNote || note instanceof LongNote) {
                    due.add(new Due(note.getMicroTime() + NantokaManiaRules.lateLimit(scratch) + 1,
                            EXPIRY, lane, note));
                }
            }
        }
    }

    private boolean hell(LongNote note) {
        return note.getType() == LongNote.TYPE_HELLCHARGENOTE
                || note.getType() == LongNote.TYPE_UNDEFINED && lnType == BMSModel.LNTYPE_HELLCHARGENOTE;
    }

    private boolean legacy(LongNote note) {
        return note.getType() == LongNote.TYPE_LONGNOTE
                || note.getType() == LongNote.TYPE_UNDEFINED && lnType == BMSModel.LNTYPE_LONGNOTE;
    }

    public void advanceTo(long until) {
        if (until < time) return;
        while (true) {
            long nextTick = tick * 400_000L / 3;
            long nextDue = due.isEmpty() ? Long.MAX_VALUE : due.peek().time;
            if (Math.min(nextTick, nextDue) > until) break;
            if (nextDue <= nextTick) {
                Due event = due.remove();
                time = event.time;
                if (event.kind == ARRIVAL) arrive(event); else expire(event);
            } else {
                time = nextTick;
                tick++;
                for (int lane = 0; lane < tracks.length; lane++) {
                    Track track = tracks[lane];
                    if (track.body == null) continue;
                    if (autoplay || !track.held.isEmpty()) listener.recover(lane);
                    else listener.judge(lane, track.body, 5, time, 0);
                }
            }
        }
        time = until;
    }

    private void arrive(Due event) {
        Track track = tracks[event.lane];
        Note note = event.note;
        if (note instanceof MineNote mine) {
            if (!track.held.isEmpty()) listener.mine(event.lane, mine);
            return;
        }
        if (note instanceof LongNote ln) {
            if (ln.isEnd()) {
                if (track.body == ln.getPair()) track.body = null;
                if (ln.getState() == 0 && (autoplay || legacy(ln) && track.end == ln
                        && !track.held.isEmpty())) {
                    emit(event.lane, ln, 0, event.time, 0);
                    if (track.end == ln) track.end = null;
                }
            } else {
                if (hell(ln) && ln.getPair().getState() == 0) track.body = ln;
                if (autoplay && ln.getState() == 0) {
                    start(event.lane, ln, 0, event.time, 0, 0);
                }
            }
        } else if (autoplay && note instanceof NormalNote && note.getState() == 0) {
            emit(event.lane, note, 0, event.time, 0);
        }
    }

    private void expire(Due event) {
        if (event.note.getState() != 0) return;
        Track track = tracks[event.lane];
        emit(event.lane, event.note, 4, event.time, event.time - event.note.getMicroTime());
        if (event.note instanceof LongNote ln) {
            if (ln.isEnd()) {
                if (track.end == ln) track.end = null;
                if (track.body == ln.getPair()) track.body = null;
            } else if (hell(ln)) {
                if (ln.getPair().getState() == 0) track.end = ln.getPair();
            } else {
                suppress(event.lane, ln.getPair());
            }
        }
    }

    /** Restore keys held before the song began without creating a tap judgment. */
    public void initiallyHeld(int lane, int direction) { tracks[lane].held.set(direction); }

    /** Called in timestamp order; same-time key changes precede advanceTo(time). */
    public void input(int lane, int direction, boolean pressed, long at) {
        // A polled device can deliver a timestamp just before the last update.
        // Retain its actual timing instead of moving it across a judge boundary.
        Track track = tracks[lane];
        boolean wasPressed = track.held.get(direction);
        track.held.set(direction, pressed);
        if (wasPressed == pressed) return;
        LongNote end = track.end;
        if (pressed) {
            if (end != null && end.getState() == 0) {
                if (track.scratch && track.direction >= 0 && track.direction != direction) {
                    finish(lane, end, at);
                }
                // Re-entering HCN restores held state without another note judgment.
                if (track.direction < 0) track.direction = direction;
                return;
            }
            if (track.body != null && track.body.getState() != 0
                    && track.body.getPair().getState() == 0) {
                track.end = track.body.getPair();
                track.direction = direction;
                return;
            }
            Note candidate = null;
            int candidateJudge = 6;
            while (track.cursor < track.notes.length
                    && track.notes[track.cursor].getMicroTime() + NantokaManiaRules.lateLimit(track.scratch) < at) {
                track.cursor++;
            }
            for (int i = track.cursor; i < track.notes.length; i++) {
                Note note = track.notes[i];
                if (note.getMicroTime() - at >= 350_000) break;
                if (note.getState() != 0 || note instanceof MineNote
                        || note instanceof LongNote ln && ln.isEnd()) continue;
                int judge = NantokaManiaRules.judge(at - note.getMicroTime(), track.scratch);
                if (judge == 6) continue;
                if (candidate == null || candidateJudge >= 3 && judge <= 2) {
                    candidate = note;
                    candidateJudge = judge;
                }
                if (candidateJudge <= 2) break;
            }
            if (candidate != null) {
                if (candidate instanceof LongNote ln && candidateJudge < 4) {
                    start(lane, ln, candidateJudge, at, at - candidate.getMicroTime(), direction);
                } else {
                    emit(lane, candidate, candidateJudge, at, at - candidate.getMicroTime());
                }
            } else {
                // Preserve ordinary manual keysounds when no judgeable note exists.
                int low = 0, high = track.notes.length;
                while (low < high) {
                    int mid = (low + high) >>> 1;
                    if (track.notes[mid].getMicroTime() < at) low = mid + 1;
                    else high = mid;
                }
                Note nearest = null;
                for (int i = low; i < track.notes.length; i++) {
                    if (keysoundNote(track.notes[i])) { nearest = track.notes[i]; break; }
                }
                if (nearest == null) {
                    for (int i = low - 1; i >= 0; i--) {
                        if (keysoundNote(track.notes[i])) { nearest = track.notes[i]; break; }
                    }
                }
                if (nearest != null) listener.sound(lane, nearest);
            }
        } else if (end != null && end.getState() == 0 && track.held.isEmpty()) {
            if (track.scratch) return; // BSS requires a reverse, never just a stop.
            long difference = at - end.getMicroTime();
            if (hell(end) && difference <= NantokaManiaRules.earlyGreat(false)) return;
            finish(lane, end, at);
        }
    }

    private void start(int lane, LongNote start, int judge, long at, long difference, int direction) {
        emit(lane, start, judge, at, difference);
        Track track = tracks[lane];
        if (judge <= 2 || hell(start)) {
            track.end = start.getPair();
            track.direction = direction;
        } else suppress(lane, start.getPair());
    }

    private static boolean keysoundNote(Note note) {
        return note instanceof NormalNote || note instanceof LongNote ln && !ln.isEnd();
    }

    private void finish(int lane, LongNote end, long at) {
        Track track = tracks[lane];
        long difference = at - end.getMicroTime();
        int base = NantokaManiaRules.judge(difference, track.scratch);
        int judge = base <= 2 ? 0 : base == 3 ? 3 : 4;
        emit(lane, end, judge, at, difference);
        track.end = null;
        track.body = null;
        track.direction = -1;
    }

    private void emit(int lane, Note note, int judge, long at, long difference) {
        if (judge != 5) note.setState(judge + 1);
        listener.judge(lane, note, judge, at, difference);
        if (judge < 4) listener.sound(lane, note);
    }

    private void suppress(int lane, LongNote end) {
        if (end.getState() != 0) return;
        end.setState(7);
        listener.suppress(lane, end);
    }

    public LongNote processing(int lane) { return tracks[lane].end; }
    public LongNote passing(int lane) { return tracks[lane].body; }
    public boolean holding(int lane) { return autoplay || !tracks[lane].held.isEmpty(); }
}
