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
    static final long SCRATCH_DENSITY_WINDOW_US = 200_000L;

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

    private record Scratch(long time, int side) {
    }

    private record Profile(
            long moveThresholdUs,
            int biasTolerance,
            int movePenalty,
            int balancePenalty
    ) {
        private static Profile forDifficulty(int difficulty) {
            return switch (difficulty) {
                case 1 -> new Profile(650_000L, 0, 8, 6);
                case 2 -> new Profile(400_000L, 1, 6, 5);
                default -> new Profile(220_000L, 2, 4, 4);
            };
        }
    }

    private static final class Assignment {
        private final Profile profile;
        private final List<Unit> units;
        private final Map<Integer, Map<Integer, Integer>> wavSides = new HashMap<>();
        private final Map<Integer, int[]> measureCounts = new HashMap<>();
        private final List<Scratch> scratches = new ArrayList<>();
        private final long[] lastLaneTime;
        private final int[] lastLaneSide;

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
            for (Unit unit : units) {
                if (!unit.scratch()) continue;
                int side = chooseScratch(unit);
                result.put(unit.note(), side);
                remember(unit, side);
                scratches.add(new Scratch(unit.slot().timeline().getMicroTime(), side));
            }
            scratches.sort(Comparator.comparingLong(Scratch::time));

            for (Unit unit : units) {
                if (unit.scratch()) continue;
                int side = chooseKey(unit);
                result.put(unit.note(), side);
                remember(unit, side);
                lastLaneSide[unit.slot().lane()] = side;
                lastLaneTime[unit.slot().lane()] = unit.slot().timeline().getMicroTime();
            }
            return result;
        }

        private int chooseScratch(Unit unit) {
            int left = baseCost(unit, 0);
            int right = baseCost(unit, 1);
            if (!scratches.isEmpty() && scratches.get(scratches.size() - 1).side() == 0) left += 2;
            if (!scratches.isEmpty() && scratches.get(scratches.size() - 1).side() == 1) right += 2;
            return select(unit, left, right);
        }

        private int chooseKey(Unit unit) {
            int left = baseCost(unit, 0) + scratchCost(unit, 0) + moveCost(unit, 0);
            int right = baseCost(unit, 1) + scratchCost(unit, 1) + moveCost(unit, 1);
            return select(unit, left, right);
        }

        private int baseCost(Unit unit, int side) {
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
            int[] counts = measureCounts.computeIfAbsent(unit.measure(), ignored -> new int[2]);
            int excess = counts[side] - counts[1 - side] - profile.biasTolerance();
            if (excess >= 0) cost += (excess + 1) * profile.balancePenalty();
            return cost;
        }

        private int scratchCost(Unit unit, int side) {
            long time = unit.slot().timeline().getMicroTime();
            int nearby = 0;
            for (Scratch scratch : scratches) {
                long distance = Math.abs(scratch.time() - time);
                if (distance <= SCRATCH_DENSITY_WINDOW_US && scratch.side() == side) nearby++;
            }
            return nearby * 12;
        }

        private int moveCost(Unit unit, int side) {
            int lane = unit.slot().lane();
            if (lastLaneSide[lane] < 0 || lastLaneSide[lane] == side) return 0;
            long elapsed = unit.slot().timeline().getMicroTime() - lastLaneTime[lane];
            return elapsed < profile.moveThresholdUs() ? profile.movePenalty() : 0;
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
    }
}
