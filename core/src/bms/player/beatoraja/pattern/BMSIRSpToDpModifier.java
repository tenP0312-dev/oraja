package bms.player.beatoraja.pattern;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Mode;
import bms.model.Note;
import bms.model.TimeLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Deterministically distributes an SP 5KEY/7KEY chart across both DP sides. */
final class BMSIRSpToDpModifier {
    private static final long MOVE_THRESHOLD_US = 400_000L;
    private static final int BIAS_TOLERANCE = 1;
    private static final int MOVE_PENALTY = 6;
    private static final int BALANCE_PENALTY = 5;
    private static final int REPEATED_SCRATCH_SIDE_PENALTY = 2;

    private BMSIRSpToDpModifier() {
    }

    static boolean apply(BMSModel model, int requestedDifficulty) {
        if (model == null || requestedDifficulty <= 0 || !supports(model.getMode())) {
            return false;
        }

        Mode sourceMode = model.getMode();
        Mode targetMode = sourceMode == Mode.BEAT_5K ? Mode.BEAT_10K : Mode.BEAT_14K;
        int difficulty = Math.max(1, Math.min(3, requestedDifficulty));
        int playableBefore = model.getTotalNotes();
        int visibleBefore = countVisible(model, sourceMode.key);
        int hiddenBefore = countHidden(model, sourceMode.key);

        List<Unit> units = units(model, sourceMode);
        Assignment assignment = new Assignment(sourceMode, difficulty, units);
        Map<Note, Integer> sides = assignment.assign();

        model.setMode(targetMode);
        model.setPlayer(3);
        for (Unit unit : units) {
            move(unit, sides.getOrDefault(unit.note(), 0), sourceMode, targetMode);
        }

        if (model.getTotalNotes() != playableBefore
                || countVisible(model, targetMode.key) != visibleBefore
                || countHidden(model, targetMode.key) != hiddenBefore) {
            throw new IllegalStateException("SP-to-DP conversion changed the chart note count");
        }
        return true;
    }

    private static boolean supports(Mode mode) {
        return mode == Mode.BEAT_5K || mode == Mode.BEAT_7K;
    }

    private static List<Unit> units(BMSModel model, Mode sourceMode) {
        IdentityHashMap<Note, Slot> owners = new IdentityHashMap<>();
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < sourceMode.key; lane++) {
                Note visible = timeline.getNote(lane);
                if (visible != null) owners.put(visible, new Slot(timeline, lane, false));
                Note hidden = timeline.getHiddenNote(lane);
                if (hidden != null) owners.put(hidden, new Slot(timeline, lane, true));
            }
        }

        List<Unit> result = new ArrayList<>();
        for (Map.Entry<Note, Slot> entry : owners.entrySet()) {
            Note note = entry.getKey();
            if (note instanceof LongNote longNote && longNote.isEnd()) continue;
            Slot slot = entry.getValue();
            Slot pair = note instanceof LongNote longNote && longNote.getPair() != null
                    ? owners.get(longNote.getPair())
                    : null;
            result.add(new Unit(
                    note,
                    slot,
                    pair,
                    measure(slot.timeline()),
                    sourceMode.isScratchKey(slot.lane())
            ));
        }
        result.sort(Comparator
                .comparingLong((Unit unit) -> unit.slot().timeline().getMicroTime())
                .thenComparing(unit -> !unit.scratch())
                .thenComparing(unit -> unit.slot().hidden())
                .thenComparingInt(unit -> unit.slot().lane())
                .thenComparingInt(unit -> unit.note().getWav()));
        return result;
    }

    private static void move(Unit unit, int side, Mode sourceMode, Mode targetMode) {
        int destination = destinationLane(unit.slot().lane(), side, sourceMode, targetMode);
        clear(unit.slot());
        set(unit.slot(), destination, unit.note());
        if (unit.pair() != null && unit.note() instanceof LongNote longNote) {
            clear(unit.pair());
            set(unit.pair(), destination, longNote.getPair());
        }
    }

    private static int destinationLane(int sourceLane, int side, Mode sourceMode, Mode targetMode) {
        if (sourceMode.isScratchKey(sourceLane)) return targetMode.scratchKey[side];
        return side == 0 ? sourceLane : sourceMode.key + sourceLane;
    }

    private static void clear(Slot slot) {
        if (slot.hidden()) slot.timeline().setHiddenNote(slot.lane(), null);
        else slot.timeline().setNote(slot.lane(), null);
    }

    private static void set(Slot slot, int lane, Note note) {
        if (slot.hidden()) slot.timeline().setHiddenNote(lane, note);
        else slot.timeline().setNote(lane, note);
    }

    private static int countVisible(BMSModel model, int lanes) {
        int count = 0;
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < lanes; lane++) {
                if (timeline.getNote(lane) != null) count++;
            }
        }
        return count;
    }

    private static int countHidden(BMSModel model, int lanes) {
        int count = 0;
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < lanes; lane++) {
                if (timeline.getHiddenNote(lane) != null) count++;
            }
        }
        return count;
    }

    private static int measure(TimeLine timeline) {
        return (int) Math.floor(timeline.getSection() + 1.0e-9);
    }

    private record Slot(TimeLine timeline, int lane, boolean hidden) {
    }

    private record Unit(Note note, Slot slot, Slot pair, int measure, boolean scratch) {
    }

    private record Interval(long start, long end) {
        private boolean overlaps(Interval other) {
            return start <= other.end && other.start <= end;
        }
    }

    private record ScratchWindow(Interval interval, int side) {
    }

    private record Profile(long scratchGuardUs) {
        private static Profile forDifficulty(int difficulty) {
            return switch (difficulty) {
                case 1 -> new Profile(160_000L);
                case 2 -> new Profile(120_000L);
                default -> new Profile(80_000L);
            };
        }
    }

    static long scratchGuardUsForDifficulty(int requestedDifficulty) {
        int difficulty = Math.max(1, Math.min(3, requestedDifficulty));
        return Profile.forDifficulty(difficulty).scratchGuardUs();
    }

    static long scratchMergeGapUsForDifficulty(int requestedDifficulty) {
        return scratchGuardUsForDifficulty(requestedDifficulty) * 2L;
    }

    private static final class ScratchPhrase {
        private final List<Unit> units = new ArrayList<>();
        private long start;
        private long end;

        private ScratchPhrase(Unit unit, Interval interval) {
            units.add(unit);
            start = interval.start();
            end = interval.end();
        }

        private void add(Unit unit, Interval interval) {
            units.add(unit);
            start = Math.min(start, interval.start());
            end = Math.max(end, interval.end());
        }

        private Interval interval() {
            return new Interval(start, end);
        }
    }

    private static final class ScratchGroup {
        private final List<ScratchPhrase> phrases = new ArrayList<>();

        private List<Unit> units() {
            List<Unit> result = new ArrayList<>();
            for (ScratchPhrase phrase : phrases) result.addAll(phrase.units);
            return result;
        }
    }

    private static final class Assignment {
        private final Profile profile;
        private final List<Unit> units;
        private final Map<Integer, Map<Integer, Integer>> wavSides = new HashMap<>();
        private final Map<Integer, int[]> measureCounts = new HashMap<>();
        private final List<ScratchWindow> scratchWindows = new ArrayList<>();
        private final long[] lastLaneTime;
        private final int[] lastLaneSide;
        private int lastScratchGroupSide = -1;

        private Assignment(Mode sourceMode, int difficulty, List<Unit> units) {
            this.profile = Profile.forDifficulty(difficulty);
            this.units = units;
            this.lastLaneTime = new long[sourceMode.key];
            this.lastLaneSide = new int[sourceMode.key];
            java.util.Arrays.fill(lastLaneTime, Long.MIN_VALUE / 4);
            java.util.Arrays.fill(lastLaneSide, -1);
        }

        private Map<Note, Integer> assign() {
            IdentityHashMap<Note, Integer> result = new IdentityHashMap<>();
            for (ScratchGroup group : scratchGroups(scratchPhrases())) {
                int side = chooseScratchGroup(group);
                for (ScratchPhrase phrase : group.phrases) {
                    scratchWindows.add(new ScratchWindow(phrase.interval(), side));
                    for (Unit unit : phrase.units) {
                        result.put(unit.note(), side);
                        remember(unit, side);
                    }
                }
                lastScratchGroupSide = side;
            }

            for (Unit unit : units) {
                if (!unit.scratch() || !unit.slot().hidden()) continue;
                int side = select(unit, baseCost(unit, 0), baseCost(unit, 1));
                result.put(unit.note(), side);
                remember(unit, side);
            }

            for (Unit unit : units) {
                if (unit.scratch()) continue;
                int side = chooseKey(unit);
                result.put(unit.note(), side);
                remember(unit, side);
                lastLaneSide[unit.slot().lane()] = side;
                lastLaneTime[unit.slot().lane()] = playableInterval(unit).end();
            }
            return result;
        }

        private List<ScratchPhrase> scratchPhrases() {
            List<ScratchPhrase> phrases = new ArrayList<>();
            for (Unit unit : units) {
                if (!unit.scratch() || unit.slot().hidden()) continue;
                Interval playable = playableInterval(unit);
                Interval reserved = new Interval(
                        playable.start() - profile.scratchGuardUs(),
                        playable.end() + profile.scratchGuardUs()
                );
                ScratchPhrase previous = phrases.isEmpty() ? null : phrases.get(phrases.size() - 1);
                if (previous != null && previous.interval().overlaps(reserved)) {
                    previous.add(unit, reserved);
                } else {
                    phrases.add(new ScratchPhrase(unit, reserved));
                }
            }
            return phrases;
        }

        private List<ScratchGroup> scratchGroups(List<ScratchPhrase> phrases) {
            List<ScratchGroup> groups = new ArrayList<>();
            if (phrases.isEmpty()) return groups;

            int[] connections = new int[phrases.size() + 1];
            for (Unit unit : units) {
                if (unit.scratch() || unit.slot().hidden()) continue;
                Interval key = playableInterval(unit);
                int first = firstPhraseEndingAtOrAfter(phrases, key.start());
                int last = lastPhraseStartingAtOrBefore(phrases, key.end());
                if (first >= 0 && last > first
                        && phrases.get(first).interval().overlaps(key)
                        && phrases.get(last).interval().overlaps(key)) {
                    connections[first]++;
                    connections[last]--;
                }
            }

            ScratchGroup current = new ScratchGroup();
            groups.add(current);
            int activeConnections = 0;
            for (int index = 0; index < phrases.size(); index++) {
                current.phrases.add(phrases.get(index));
                activeConnections += connections[index];
                if (index + 1 < phrases.size() && activeConnections == 0) {
                    current = new ScratchGroup();
                    groups.add(current);
                }
            }
            return groups;
        }

        private int firstPhraseEndingAtOrAfter(List<ScratchPhrase> phrases, long time) {
            int low = 0;
            int high = phrases.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (phrases.get(middle).end < time) low = middle + 1;
                else high = middle;
            }
            return low < phrases.size() ? low : -1;
        }

        private int lastPhraseStartingAtOrBefore(List<ScratchPhrase> phrases, long time) {
            int low = 0;
            int high = phrases.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (phrases.get(middle).start <= time) low = middle + 1;
                else high = middle;
            }
            return low == 0 ? -1 : low - 1;
        }

        private int chooseScratchGroup(ScratchGroup group) {
            List<Unit> scratchUnits = group.units();
            long left = scratchGroupCost(scratchUnits, 0);
            long right = scratchGroupCost(scratchUnits, 1);
            if (lastScratchGroupSide == 0) left += REPEATED_SCRATCH_SIDE_PENALTY;
            if (lastScratchGroupSide == 1) right += REPEATED_SCRATCH_SIDE_PENALTY;
            if (left != right) return left < right ? 0 : 1;
            return lastScratchGroupSide < 0 ? 0 : 1 - lastScratchGroupSide;
        }

        private long scratchGroupCost(List<Unit> scratchUnits, int side) {
            long cost = 0;
            Map<Integer, int[]> projectedCounts = new HashMap<>();
            for (Unit unit : scratchUnits) {
                // Scratch sample identity must not pin separated phrases to one side.
                int[] counts = projectedCounts.computeIfAbsent(unit.measure(), measure -> {
                    int[] existing = measureCounts.get(measure);
                    return existing == null ? new int[2] : existing.clone();
                });
                int excess = counts[side] - counts[1 - side] - BIAS_TOLERANCE;
                if (excess >= 0) cost += (excess + 1) * BALANCE_PENALTY;
                counts[side]++;
            }
            return cost;
        }

        private int chooseKey(Unit unit) {
            boolean leftReserved = !unit.slot().hidden() && scratchReserved(unit, 0);
            boolean rightReserved = !unit.slot().hidden() && scratchReserved(unit, 1);
            if (leftReserved && rightReserved) {
                throw new IllegalStateException("SP-to-DP scratch reservations blocked both sides");
            }
            if (leftReserved) return 1;
            if (rightReserved) return 0;
            int left = baseCost(unit, 0) + moveCost(unit, 0);
            int right = baseCost(unit, 1) + moveCost(unit, 1);
            return select(unit, left, right);
        }

        private int baseCost(Unit unit, int side) {
            int cost = wavCost(unit, side);
            int[] counts = measureCounts.computeIfAbsent(unit.measure(), ignored -> new int[2]);
            int excess = counts[side] - counts[1 - side] - BIAS_TOLERANCE;
            if (excess >= 0) cost += (excess + 1) * BALANCE_PENALTY;
            return cost;
        }

        private int wavCost(Unit unit, int side) {
            int cost = 0;
            int wav = unit.note().getWav();
            if (wav >= 0) {
                Integer current = wavSides
                        .getOrDefault(unit.measure(), Map.of())
                        .get(wav);
                if (current != null && current != side) cost += 10;
                Integer previous = wavSides
                        .getOrDefault(unit.measure() - 1, Map.of())
                        .get(wav);
                if (previous != null && previous != side) cost += 4;
            }
            return cost;
        }

        private boolean scratchReserved(Unit unit, int side) {
            Interval playable = playableInterval(unit);
            int low = 0;
            int high = scratchWindows.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (scratchWindows.get(middle).interval().end() < playable.start()) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            for (int index = low; index < scratchWindows.size(); index++) {
                ScratchWindow scratch = scratchWindows.get(index);
                if (scratch.interval().start() > playable.end()) break;
                if (scratch.side() == side && scratch.interval().overlaps(playable)) return true;
            }
            return false;
        }

        private int moveCost(Unit unit, int side) {
            int lane = unit.slot().lane();
            if (lastLaneSide[lane] < 0 || lastLaneSide[lane] == side) return 0;
            long elapsed = unit.slot().timeline().getMicroTime() - lastLaneTime[lane];
            return elapsed < MOVE_THRESHOLD_US ? MOVE_PENALTY : 0;
        }

        private int select(Unit unit, int left, int right) {
            if (left != right) return left < right ? 0 : 1;
            int[] counts = measureCounts.computeIfAbsent(unit.measure(), ignored -> new int[2]);
            if (counts[0] != counts[1]) return counts[0] < counts[1] ? 0 : 1;
            int parity = unit.note().getWav() * 31 + unit.slot().lane() * 7 + unit.measure();
            return Math.floorMod(parity, 2);
        }

        private void remember(Unit unit, int side) {
            measureCounts.computeIfAbsent(unit.measure(), ignored -> new int[2])[side]++;
            if (unit.note().getWav() >= 0) {
                wavSides.computeIfAbsent(unit.measure(), ignored -> new HashMap<>())
                        .putIfAbsent(unit.note().getWav(), side);
            }
        }

        private Interval playableInterval(Unit unit) {
            long first = unit.slot().timeline().getMicroTime();
            long second = unit.pair() == null
                    ? first
                    : unit.pair().timeline().getMicroTime();
            return new Interval(Math.min(first, second), Math.max(first, second));
        }
    }
}
