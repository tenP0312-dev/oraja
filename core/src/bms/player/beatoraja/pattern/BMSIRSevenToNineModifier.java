package bms.player.beatoraja.pattern;

import bms.model.*;

import java.util.*;

/** Disposable, deterministic 7KEY + scratch to 9KEY trial converter. */
public final class BMSIRSevenToNineModifier {
    public static final String MODEL_PREVIEW = "bmsir.seven_to_nine.preview";
    public static final String MODEL_REMOVED = "bmsir.seven_to_nine.removed";
    // Community-listed impossible triples, using one-based physical button numbers.
    private static final int[] FORBIDDEN = {147, 148, 149, 158, 159, 169, 258, 259, 269, 369};
    private static final boolean[] SAFE = new boolean[512];
    // Trial heuristics, not universal human reach/speed limits.
    private static final long RECENT_US = 80_000;
    private static final long PHRASE_US = 500_000;

    static {
        for (int mask = 0; mask < SAFE.length; mask++) {
            boolean safe = Integer.bitCount(mask) <= 6;
            for (int triple : FORBIDDEN) {
                int bits = (1 << (triple / 100 - 1))
                        | (1 << (triple / 10 % 10 - 1)) | (1 << (triple % 10 - 1));
                if ((mask & bits) == bits) safe = false;
            }
            SAFE[mask] = safe;
        }
    }

    private BMSIRSevenToNineModifier() { }

    public static boolean isApplied(BMSModel model) {
        return model != null && "1".equals(model.getValues().get(MODEL_PREVIEW));
    }

    static boolean safeChord(int mask) {
        return mask >= 0 && mask < SAFE.length && SAFE[mask];
    }

    public static boolean apply(BMSModel model) {
        if (model == null || model.getMode() != Mode.BEAT_7K || isApplied(model)) return false;
        TimeLine[] timelines = model.getAllTimeLines();
        IdentityHashMap<Note, TimeLine> owners = new IdentityHashMap<>();
        for (TimeLine tl : timelines) {
            for (int lane = 0; lane < 8; lane++) {
                if (tl.getNote(lane) != null) owners.put(tl.getNote(lane), tl);
            }
        }
        SortedMap<Long, List<Unit>> chords = new TreeMap<>();
        List<Hidden> hidden = new ArrayList<>();
        for (TimeLine tl : timelines) {
            for (int lane = 0; lane < 8; lane++) {
                Note note = tl.getNote(lane);
                if (note != null && !(note instanceof MineNote)
                        && !(note instanceof LongNote ln && ln.isEnd())) {
                    TimeLine end = note instanceof LongNote ln ? owners.get(ln.getPair()) : null;
                    chords.computeIfAbsent(tl.getMicroTime(), ignored -> new ArrayList<>())
                            .add(new Unit(tl, lane, note, end));
                }
                if (tl.getHiddenNote(lane) != null) {
                    hidden.add(new Hidden(tl, lane == 7 ? 0 : lane + 1, tl.getHiddenNote(lane)));
                }
                tl.setNote(lane, null);
                tl.setHiddenNote(lane, null);
            }
        }
        model.setMode(Mode.POPN_9K);
        model.setPlayer(1);
        for (Hidden h : hidden) h.timeline().setHiddenNote(h.lane(), h.note());

        long[] heldUntil = new long[9];
        long[] lastHit = new long[9];
        Arrays.fill(heldUntil, Long.MIN_VALUE);
        Arrays.fill(lastHit, Long.MIN_VALUE);
        int[] previousLane = new int[8];
        Arrays.fill(previousLane, -1);
        long[] previousTime = new long[8];
        Arrays.fill(previousTime, Long.MIN_VALUE);
        int lastSingleSource = -1, lastSingleTarget = -1;
        long lastSingleTime = Long.MIN_VALUE;
        int removed = 0;
        for (Map.Entry<Long, List<Unit>> entry : chords.entrySet()) {
            long now = entry.getKey();
            List<Unit> units = entry.getValue();
            units.sort(Comparator.comparingInt(Unit::source));
            int held = 0, recent = 0;
            for (int lane = 0; lane < 9; lane++) {
                // Reserve the release timestamp too: an LN end owns that slot.
                if (heldUntil[lane] >= now) held |= 1 << lane;
                if (lastHit[lane] != Long.MIN_VALUE && now - lastHit[lane] < RECENT_US) recent |= 1 << lane;
            }
            Search search = new Search(units, now, held, recent, previousLane, previousTime,
                    lastHit, lastSingleSource, lastSingleTarget,
                    lastSingleTime != Long.MIN_VALUE && now - lastSingleTime <= PHRASE_US);
            search.visit(0, 0, held, 0, 0);
            int singleSource = -1, singleTarget = -1, normalCount = 0;
            for (int i = 0; i < units.size(); i++) {
                Unit unit = units.get(i);
                int lane = search.best[i];
                if (lane < 0) {
                    unit.start().addBackGroundNote(unit.note());
                    if (unit.end() != null && unit.note() instanceof LongNote ln) {
                        unit.end().addBackGroundNote(ln.getPair());
                    }
                    removed++;
                    continue;
                }
                unit.start().setNote(lane, unit.note());
                if (unit.end() != null && unit.note() instanceof LongNote ln) {
                    unit.end().setNote(lane, ln.getPair());
                    heldUntil[lane] = unit.end().getMicroTime();
                }
                lastHit[lane] = now;
                previousLane[unit.source()] = lane;
                previousTime[unit.source()] = now;
                if (unit.source() < 7) {
                    normalCount++;
                    singleSource = unit.source();
                    singleTarget = lane;
                }
            }
            if (normalCount == 1) {
                lastSingleSource = singleSource;
                lastSingleTarget = singleTarget;
                lastSingleTime = now;
            } else if (normalCount > 1) {
                lastSingleSource = -1;
            }
        }
        model.getValues().put(MODEL_PREVIEW, "1");
        model.getValues().put(MODEL_REMOVED, Integer.toString(removed));
        return true;
    }

    private record Unit(TimeLine start, int source, Note note, TimeLine end) { }
    private record Hidden(TimeLine timeline, int lane, Note note) { }

    /** Monotonic normal-key placement, with an independent scratch lane.
     * Maximize retained starts first; movement costs only break equal-size ties.
     * Exhaustive search is bounded by the eight source lanes and nine targets.
     */
    private static final class Search {
        final List<Unit> units;
        final int recent, held;
        final int[] current, best;
        final long[][] costs;
        int bestCount = -1;
        long bestCost = Long.MAX_VALUE;

        Search(List<Unit> units, long now, int held, int recent, int[] previousLane,
               long[] previousTime, long[] lastHit, int lastSource, int lastTarget,
               boolean recentSingle) {
            this.units = units;
            this.held = held;
            this.recent = recent;
            current = new int[units.size()];
            best = new int[units.size()];
            Arrays.fill(best, -1);
            costs = new long[units.size()][9];
            long normalCount = units.stream().filter(u -> u.source() < 7).count();
            for (int i = 0; i < units.size(); i++) {
                int source = units.get(i).source();
                boolean continuity = previousTime[source] != Long.MIN_VALUE
                        && now - previousTime[source] <= PHRASE_US;
                int preferred = source == 7
                        ? (continuity && previousLane[7] <= 4 ? 8 : 0) : source + 1;
                for (int lane = 0; lane < 9; lane++) {
                    long cost = 2L * Math.abs(lane - preferred);
                    if (source == 7) {
                        cost *= 3;
                        if ((lane & 1) != 0) cost += 5;
                    } else if (continuity) {
                        cost += 3L * Math.abs(lane - previousLane[source]);
                    }
                    if (lastHit[lane] != Long.MIN_VALUE && now - lastHit[lane] < RECENT_US) cost += 30;
                    if (source < 7 && normalCount == 1 && recentSingle && lastSource >= 0) {
                        int direction = Integer.signum(source - lastSource);
                        if (direction != 0 && Integer.signum(lane - lastTarget) != direction) cost += 40;
                        cost += Math.abs((lane - lastTarget) - (source - lastSource));
                    }
                    costs[i][lane] = cost;
                }
            }
        }

        void visit(int index, int minimum, int used, int count, long cost) {
            int capacity = Math.min(units.size() - index, 6 - Integer.bitCount(used));
            if (count + capacity < bestCount) return;
            if (count + capacity == bestCount && cost >= bestCost) return;
            if (index == units.size()) {
                long totalCost = cost + (SAFE[used | recent] ? 0 : 80);
                if (count > bestCount || count == bestCount && totalCost < bestCost) {
                    bestCount = count;
                    bestCost = totalCost;
                    System.arraycopy(current, 0, best, 0, current.length);
                }
                return;
            }
            Unit unit = units.get(index);
            // Malformed/unpaired LNs cannot acquire an indefinitely held lane.
            boolean valid = !(unit.note() instanceof LongNote)
                    || unit.end() != null && unit.end().getMicroTime() > unit.start().getMicroTime();
            if (valid) {
                for (int lane = unit.source() == 7 ? 0 : minimum; lane < 9; lane++) {
                    int next = used | (1 << lane);
                    if ((used & (1 << lane)) != 0 || !SAFE[next]) continue;
                    current[index] = lane;
                    visit(index + 1, unit.source() == 7 ? minimum : lane + 1,
                            next, count + 1, cost + costs[index][lane]);
                }
            }
            current[index] = -1;
            visit(index + 1, minimum, used, count, cost);
        }
    }
}
