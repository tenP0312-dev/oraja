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
        default void bodyTick(int lane, LongNote note, long at) { judge(lane, note, 5, at, 0); }
        default void refresh(int lane, boolean poor) { }
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
        long scratchEndInputTime = Long.MIN_VALUE;
        int cursor;
        int hcnState;
        Track(Note[] notes, boolean scratch) { this.notes = notes; this.scratch = scratch; }
    }

    private final Track[] tracks;
    private final int lnType;
    private final boolean autoplay;
    private final Listener listener;
    private final boolean doublePlay;
    private final LegacyNantokaManiaJudge legacy;
    private final PriorityQueue<Due> due = new PriorityQueue<>(Comparator
            .comparingLong(Due::time).thenComparingInt(Due::kind).thenComparingInt(Due::lane));
    private long time = Long.MIN_VALUE;
    private long tick = 1;
    private long displayTick = 1;

    public NantokaManiaJudge(BMSModel model, boolean autoplay, Listener listener) {
        this.lnType = model.getLntype();
        this.autoplay = autoplay;
        this.listener = listener;
        this.doublePlay = model.getMode().player == 2;
        this.legacy = NantokaManiaRules.legacy(model)
                ? new LegacyNantokaManiaJudge(model, autoplay, listener) : null;
        Lane[] lanes = model.getLanes();
        tracks = new Track[lanes.length];
        for (int lane = 0; lane < lanes.length; lane++) {
            boolean scratch = false;
            for (int key : model.getMode().scratchKey) if (key == lane) scratch = true;
            tracks[lane] = new Track(lanes[lane].getNotes(), scratch);
            for (Note note : tracks[lane].notes) {
                due.add(new Due(note.getMicroTime(), ARRIVAL, lane, note));
                if (note instanceof NormalNote || note instanceof LongNote) {
                    due.add(new Due(NantokaManiaRules.expiry(note.getMicroTime()),
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
        if (legacy != null) { legacy.advanceTo(until); return; }
        if (until < time) return;
        long processedTime = Long.MIN_VALUE;
        while (true) {
            long nextTick = tick * 400_000L / 3;
            long nextDisplay = displayTick * 500_000L / 3;
            long nextDue = due.isEmpty() ? Long.MAX_VALUE : due.peek().time;
            long next = Math.min(Math.min(nextTick, nextDue), nextDisplay);
            if (next > until) break;
            if (processedTime != Long.MIN_VALUE && processedTime != next) {
                for (Track track : tracks) updateHcnState(track);
            }
            processedTime = next;
            if (nextDue <= nextTick && nextDue <= nextDisplay) {
                Due event = due.remove();
                time = event.time;
                if (event.kind == ARRIVAL) arrive(event); else expire(event);
            } else if (nextTick <= nextDisplay) {
                time = nextTick;
                tick++;
                for (int lane = 0; lane < tracks.length; lane++) {
                    Track track = tracks[lane];
                    LongNote body = track.body;
                    boolean laneHeld = track.end != null && hell(track.end);
                    if (body == null && laneHeld) body = track.end.getPair();
                    if (body == null) continue;
                    if (track.hcnState == 1) listener.bodyTick(lane, body, time);
                    else if ((track.hcnState == 2 || laneHeld) && (autoplay || !track.held.isEmpty())) {
                        listener.recover(lane);
                    }
                }
            } else {
                time = nextDisplay;
                displayTick++;
                for (int lane = 0; lane < tracks.length; lane++) {
                    Track track = tracks[lane];
                    if (track.body != null && track.hcnState == 1) listener.refresh(lane, true);
                    else if (track.end != null && (autoplay || !track.held.isEmpty())) listener.refresh(lane, false);
                }
            }
        }
        time = until;
        for (Track track : tracks) updateHcnState(track);
    }

    private void updateHcnState(Track track) {
        if (track.body != null && track.hcnState != 0) {
            track.hcnState = autoplay || !track.held.isEmpty() ? 2 : 1;
        }
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
                if (track.body == ln.getPair()) { track.body = null; track.hcnState = 0; }
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
                track.hcnState = 1;
            } else {
                suppress(event.lane, ln.getPair());
            }
        }
    }

    /** Restore keys held before the song began without creating a tap judgment. */
    public void initiallyHeld(int lane, int direction) {
        if (legacy != null) legacy.initiallyHeld(lane, direction);
        else tracks[lane].held.set(direction);
    }

    /** Called in timestamp order; same-time key changes precede advanceTo(time). */
    public void input(int lane, int direction, boolean pressed, long at) {
        if (legacy != null) { legacy.input(lane, direction, pressed, at); return; }
        // Misses and chart-end events precede same-time input; the HCN tick follows it.
        while (!due.isEmpty() && due.peek().time <= at) {
            Due event = due.remove();
            if (event.kind == ARRIVAL) arrive(event); else expire(event);
        }
        // A polled device can deliver a timestamp just before the last update.
        // Retain its actual timing instead of moving it across a judge boundary.
        Track track = tracks[lane];
        boolean wasPressed = track.held.get(direction);
        track.held.set(direction, pressed);
        if (wasPressed == pressed) return;
        LongNote end = track.end;
        if (pressed) {
            // A reversal can arrive as release then press at the same timestamp.
            // Keep its held-state change, but do not judge a second note with it.
            if (track.scratch && track.scratchEndInputTime == at) return;
            if (end != null && end.getState() == 0) {
                if (track.scratch && track.direction >= 0 && track.direction != direction) {
                    finish(lane, end, at);
                }
                // Re-entering HCN restores held state without another note judgment.
                if (track.direction < 0) track.direction = direction;
                return;
            }
            if (track.body != null && track.body.getState() != 0
                    && track.hcnState != 0) {
                // Restore HCN recovery only; consumed endpoints never reappear.
                return;
            }
            Note candidate = null;
            int candidateJudge = 6;
            while (track.cursor < track.notes.length
                    && track.notes[track.cursor].getMicroTime() + 250_000 < at) {
                track.cursor++;
            }
            for (int i = track.cursor; i < track.notes.length; i++) {
                Note note = track.notes[i];
                if (note.getMicroTime() - at >= 350_000) break;
                if (note instanceof MineNote
                        || note instanceof LongNote ln && ln.isEnd()) continue;
                if (!NantokaManiaRules.inputCandidate(at - note.getMicroTime())) continue;
                int judge = note.getState() != 0 ? 5
                        : NantokaManiaRules.judge(at - note.getMicroTime(), track.scratch, doublePlay);
                if (judge == 6) continue;
                if (candidate == null || candidate.getState() != 0 && note.getState() == 0
                        || candidate.getState() == 0 && note.getState() == 0
                        && Math.abs(at - note.getMicroTime()) < Math.abs(at - candidate.getMicroTime())) {
                    candidate = note;
                    candidateJudge = judge;
                }
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
            finish(lane, end, at);
        }
    }

    private void start(int lane, LongNote start, int judge, long at, long difference, int direction) {
        emit(lane, start, judge, at, difference);
        Track track = tracks[lane];
        if (hell(start)) {
            track.body = start;
            track.hcnState = judge <= 2 ? 2 : 1;
        }
        if (judge <= 2) {
            track.end = start.getPair();
            track.direction = direction;
        } else if (!hell(start)) suppress(lane, start.getPair());
    }

    private static boolean keysoundNote(Note note) {
        return note instanceof NormalNote || note instanceof LongNote ln && !ln.isEnd();
    }

    private void finish(int lane, LongNote end, long at) {
        Track track = tracks[lane];
        if (track.scratch) track.scratchEndInputTime = at;
        long difference = at - end.getMicroTime();
        int base = NantokaManiaRules.judge(difference, track.scratch, doublePlay);
        int judge = base <= 2 ? 0 : base == 3 ? 3
                : difference > NantokaManiaRules.boundary(track.scratch ? (doublePlay ? 19 : 17) : 15) ? 5 : 4;
        emit(lane, end, judge, at, difference);
        if (judge == 5) suppress(lane, end); // Consume the endpoint even for internal code 9.
        track.end = null;
        if (hell(end)) {
            if (judge >= 3 && track.body != null) track.hcnState = 1;
        } else track.body = null;
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

    public LongNote processing(int lane) { return legacy != null ? legacy.processing(lane) : tracks[lane].end; }
    public LongNote passing(int lane) { return legacy != null ? legacy.passing(lane) : tracks[lane].body; }
    public boolean holding(int lane) { return legacy != null ? legacy.holding(lane) : autoplay || !tracks[lane].held.isEmpty(); }
}
