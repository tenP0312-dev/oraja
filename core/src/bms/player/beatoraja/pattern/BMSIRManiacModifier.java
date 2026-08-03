package bms.player.beatoraja.pattern;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.MineNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.Note;
import bms.model.TimeLine;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Applies deterministic LR2-style chart-changing MANIAC options. */
public final class BMSIRManiacModifier extends PatternModifier {
    private static final long MIN_INSERT_GAP_US = 200_000L;

    private final BMSIRManiacSettings settings;
    private String placementHash;

    public BMSIRManiacModifier(BMSIRManiacSettings settings) {
        this.settings = new BMSIRManiacSettings(settings);
        setAssistLevel(AssistLevel.ASSIST);
    }

    @Override
    public void modify(BMSModel model) {
        if (model == null || !settings.isActive()) {
            placementHash = model == null ? null : placementHash(model);
            return;
        }
        long seed = settings.generationSeed(model.getSHA256());
        LR2Random random = new LR2Random((int) seed);

        if (settings.getExtraMode() > 0) {
            applyExtraMode(model, settings.getExtraMode());
        }
        if (settings.getAddNotes() > 0) {
            applyAddNotes(model, settings.getAddNotes(), random);
        }
        if (settings.getAddLongNotes() > 0) {
            applyAddLongNotes(model, settings.getAddLongNotes(), random);
        }
        if (settings.getAddMines() > 0) {
            applyAddMines(model, settings.getAddMines(), random);
        }
        if (settings.getLoudness() > 0) {
            applyLoudness(model, settings.getLoudness(), random);
        }
        if (settings.getSoftLanding() > 0) {
            applySoftLanding(model, settings.getSoftLanding(), random);
        }
        placementHash = placementHash(model);
    }

    public String getPlacementHash() {
        return placementHash;
    }

    private static void applyExtraMode(BMSModel model, int requestedLevel) {
        TimeLine[] timelines = model.getAllTimeLines();
        int noteCount = model.getTotalNotes();
        int level = requestedLevel - 1;
        if (level == 0 && noteCount >= 1_200) level = 2;
        else if (level == 0 && noteCount >= 1_000) level = 1;
        else if (level == 1 && noteCount >= 1_000) level = 2;

        int keysPerSide = switch (model.getMode()) {
            case BEAT_5K, BEAT_10K -> 5;
            case BEAT_7K, BEAT_14K -> 7;
            case POPN_5K -> 5;
            case POPN_9K -> 9;
            case KEYBOARD_24K, KEYBOARD_24K_DOUBLE -> 24;
        };
        long minimumGap = minimumExtraGap(model.getBpm(), level);
        Map<Integer, Integer> preferredLanes = preferredLanesByWav(model);
        long[] lastPlacedAt = new long[model.getMode().key];
        Arrays.fill(lastPlacedAt, Long.MIN_VALUE / 4);
        int alternatingSide = 0;

        for (TimeLine timeline : timelines) {
            long now = timeline.getMicroTime();
            boolean[] occupied = occupiedAt(timeline, model.getMode().key);
            for (Note background : timeline.getBackGroundNotes().clone()) {
                Integer preferred = preferredLanes.get(background.getWav());
                if (preferred == null) continue;
                int lane = normalizeExtraLane(
                        preferred,
                        model.getMode(),
                        keysPerSide,
                        alternatingSide
                );
                if (model.getMode().player == 2) alternatingSide ^= 1;
                lane = nearestAvailableLane(
                        lane,
                        occupied,
                        lastPlacedAt,
                        now,
                        minimumGap,
                        model.getMode(),
                        keysPerSide
                );
                if (lane < 0) continue;
                timeline.removeBackGroundNote(background);
                timeline.setNote(lane, background);
                occupied[lane] = true;
                lastPlacedAt[lane] = now;
            }
        }
    }

    private static long minimumExtraGap(double bpm, int level) {
        if (bpm <= 0) return 125_000L;
        double numerator = switch (level) {
            case 0 -> 60_000_000.0;
            case 1 -> 45_000_000.0;
            default -> 30_000_000.0;
        };
        return Math.max(125_000L, Math.round(numerator / bpm));
    }

    private static Map<Integer, Integer> preferredLanesByWav(BMSModel model) {
        Map<Integer, int[]> counts = new HashMap<>();
        int lanes = model.getMode().key;
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < lanes; lane++) {
                Note note = timeline.getNote(lane);
                if (!(note instanceof NormalNote) && !(note instanceof LongNote)) continue;
                counts.computeIfAbsent(note.getWav(), ignored -> new int[lanes])[lane]++;
            }
        }
        Map<Integer, Integer> result = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : counts.entrySet()) {
            int bestLane = 0;
            for (int lane = 1; lane < entry.getValue().length; lane++) {
                if (entry.getValue()[lane] > entry.getValue()[bestLane]) bestLane = lane;
            }
            result.put(entry.getKey(), bestLane);
        }
        return result;
    }

    private static int normalizeExtraLane(
            int lane,
            Mode mode,
            int keysPerSide,
            int alternatingSide
    ) {
        if (mode.player == 1) return Math.min(lane, mode.key - 1);
        int sideWidth = mode.key / 2;
        int local = lane % sideWidth;
        if (local > keysPerSide) local = keysPerSide;
        return alternatingSide == 0 ? local : sideWidth + local;
    }

    private static int nearestAvailableLane(
            int preferred,
            boolean[] occupied,
            long[] lastPlacedAt,
            long now,
            long minimumGap,
            Mode mode,
            int keysPerSide
    ) {
        int sideWidth = mode.player == 2 ? mode.key / 2 : mode.key;
        int sideStart = mode.player == 2 && preferred >= sideWidth ? sideWidth : 0;
        int localPreferred = preferred - sideStart;
        for (int distance = 0; distance <= keysPerSide; distance++) {
            int first = localPreferred + (distance % 2 == 0 ? distance : -distance);
            int second = localPreferred - (distance % 2 == 0 ? distance : -distance);
            for (int local : new int[]{first, second}) {
                if (local < 0 || local >= sideWidth) continue;
                int lane = sideStart + local;
                if (!occupied[lane] && now - lastPlacedAt[lane] >= minimumGap) return lane;
            }
        }
        return -1;
    }

    private static boolean[] occupiedAt(TimeLine timeline, int lanes) {
        boolean[] result = new boolean[lanes];
        for (int lane = 0; lane < lanes; lane++) result[lane] = timeline.getNote(lane) != null;
        return result;
    }

    private static void applyAddNotes(BMSModel model, int percent, LR2Random random) {
        int lanes = model.getMode().key;
        for (TimeLine timeline : model.getAllTimeLines()) {
            boolean[] occupied = occupiedAt(timeline, lanes);
            int original = 0;
            for (int lane = 0; lane < lanes; lane++) {
                Note note = timeline.getNote(lane);
                if (note instanceof NormalNote || (note instanceof LongNote ln && !ln.isEnd())) {
                    original++;
                }
            }
            for (int index = 0; index < original; index++) {
                if (random.inclusive(100) > percent) continue;
                List<Integer> empty = new ArrayList<>();
                for (int lane = 0; lane < lanes; lane++) {
                    if (!occupied[lane]) empty.add(lane);
                }
                if (empty.isEmpty()) break;
                int lane = empty.get(random.inclusive(empty.size() - 1));
                timeline.setNote(lane, new NormalNote(-1));
                occupied[lane] = true;
            }
        }
    }

    private static void applyAddLongNotes(BMSModel model, int percent, LR2Random random) {
        TreeMap<Long, TimeLine> timelines = timelinesByTime(model);
        int lanes = model.getMode().key;
        for (int lane = 0; lane < lanes; lane++) {
            if (model.getMode().isScratchKey(lane)) continue;
            List<TimeLine> starts = noteTimelines(model, lane);
            for (int index = 0; index + 1 < starts.size(); index++) {
                TimeLine startLine = starts.get(index);
                TimeLine nextLine = starts.get(index + 1);
                Note source = startLine.getNote(lane);
                if (!(source instanceof NormalNote) || random.inclusive(99) >= percent) continue;
                long endTime = (startLine.getMicroTime() + nextLine.getMicroTime()) / 2;
                if (endTime <= startLine.getMicroTime()) continue;
                TimeLine endLine = timelineAt(timelines, endTime, lanes);
                if (endLine.getNote(lane) != null) continue;
                LongNote start = new LongNote(source.getWav(), source.getMicroStarttime(), source.getMicroDuration());
                LongNote end = new LongNote(-1);
                start.setType(LongNote.TYPE_LONGNOTE);
                end.setType(LongNote.TYPE_LONGNOTE);
                startLine.setNote(lane, start);
                endLine.setNote(lane, end);
                start.setPair(end);
            }
        }
        model.setAllTimeLine(timelines.values().toArray(TimeLine[]::new));
    }

    private static void applyAddMines(BMSModel model, int percent, LR2Random random) {
        TreeMap<Long, TimeLine> timelines = timelinesByTime(model);
        int lanes = model.getMode().key;
        for (int lane = 0; lane < lanes; lane++) {
            if (model.getMode().isScratchKey(lane)) continue;
            List<TimeLine> notes = noteTimelines(model, lane);
            for (int index = 0; index + 1 < notes.size(); index++) {
                TimeLine left = notes.get(index);
                TimeLine right = notes.get(index + 1);
                long gap = right.getMicroTime() - left.getMicroTime();
                if (gap <= MIN_INSERT_GAP_US || random.inclusive(99) >= percent) continue;
                long mineTime = left.getMicroTime() + gap / 2;
                TimeLine mineLine = timelineAt(timelines, mineTime, lanes);
                if (mineLine.getNote(lane) == null) {
                    mineLine.setNote(lane, new MineNote(-1, 4.0));
                }
            }
        }
        model.setAllTimeLine(timelines.values().toArray(TimeLine[]::new));
    }

    private static void applyLoudness(BMSModel model, int percent, LR2Random random) {
        int lanes = model.getMode().key;
        int sideWidth = model.getMode().player == 2 ? lanes / 2 : lanes;
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int player = 0; player < model.getMode().player; player++) {
                int start = player * sideWidth;
                int end = Math.min(lanes, start + sideWidth);
                int sourceWav = -1;
                for (int lane = start; lane < end; lane++) {
                    Note note = timeline.getNote(lane);
                    if (note instanceof NormalNote
                            || (note instanceof LongNote longNote && !longNote.isEnd())) {
                        sourceWav = note.getWav();
                        break;
                    }
                }
                if (sourceWav < 0 || random.inclusive(100) > percent) continue;
                for (int lane = start; lane < end; lane++) {
                    if (timeline.getNote(lane) == null) {
                        timeline.setNote(lane, new NormalNote(sourceWav));
                    }
                }
            }
        }
    }

    private static void applySoftLanding(BMSModel model, int level, LR2Random random) {
        double factor = 1.0;
        for (TimeLine timeline : model.getAllTimeLines()) {
            boolean hasPlayableNote = false;
            for (int lane = 0; lane < model.getMode().key; lane++) {
                if (timeline.getNote(lane) != null) {
                    hasPlayableNote = true;
                    break;
                }
            }
            if ((level == 1 && timeline.getSectionLine())
                    || (level == 2 && hasPlayableNote)) {
                factor = softLandingFactor(random);
            }
            timeline.setScroll(timeline.getScroll() * factor);
        }
    }

    private static double softLandingFactor(LR2Random random) {
        double value = (random.inclusive(100) + 100.0) / 100.0;
        return random.inclusive(1) == 0 ? value : 1.0 / value;
    }

    private static TreeMap<Long, TimeLine> timelinesByTime(BMSModel model) {
        TreeMap<Long, TimeLine> result = new TreeMap<>();
        for (TimeLine timeline : model.getAllTimeLines()) result.put(timeline.getMicroTime(), timeline);
        return result;
    }

    private static List<TimeLine> noteTimelines(BMSModel model, int lane) {
        List<TimeLine> result = new ArrayList<>();
        for (TimeLine timeline : model.getAllTimeLines()) {
            Note note = timeline.getNote(lane);
            if (note instanceof NormalNote || note instanceof LongNote) result.add(timeline);
        }
        result.sort(Comparator.comparingLong(TimeLine::getMicroTime));
        return result;
    }

    private static TimeLine timelineAt(TreeMap<Long, TimeLine> timelines, long time, int lanes) {
        TimeLine existing = timelines.get(time);
        if (existing != null) return existing;
        Map.Entry<Long, TimeLine> lower = timelines.floorEntry(time);
        Map.Entry<Long, TimeLine> upper = timelines.ceilingEntry(time);
        double section;
        double bpm;
        if (lower != null && upper != null && !lower.getKey().equals(upper.getKey())) {
            double position = (double) (time - lower.getKey()) / (upper.getKey() - lower.getKey());
            section = lower.getValue().getSection()
                    + (upper.getValue().getSection() - lower.getValue().getSection()) * position;
            bpm = lower.getValue().getBPM();
        } else if (lower != null) {
            section = lower.getValue().getSection();
            bpm = lower.getValue().getBPM();
        } else if (upper != null) {
            section = upper.getValue().getSection();
            bpm = upper.getValue().getBPM();
        } else {
            section = 0;
            bpm = 0;
        }
        TimeLine created = new TimeLine(section, time, lanes);
        created.setBPM(bpm);
        timelines.put(time, created);
        return created;
    }

    public static String placementHash(BMSModel model) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (TimeLine timeline : model.getAllTimeLines()) {
                for (int lane = 0; lane < model.getMode().key; lane++) {
                    Note note = timeline.getNote(lane);
                    if (note == null) continue;
                    String kind = note instanceof LongNote ln
                            ? "L" + (ln.isEnd() ? "E" : "S")
                            : note instanceof MineNote ? "M" : "N";
                    String row = timeline.getMicroTime() + ":" + lane + ":" + kind
                            + ":" + note.getWav() + "\n";
                    digest.update(row.getBytes(StandardCharsets.UTF_8));
                }
            }
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest.digest()) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    /** DxLib-compatible MT19937 and inclusive GetRand(max). */
    static final class LR2Random {
        private final int[] state = new int[624];
        private int index = 624;

        LR2Random(int seed) {
            state[0] = seed;
            for (int i = 1; i < state.length; i++) {
                long previous = Integer.toUnsignedLong(state[i - 1]);
                state[i] = (int) (1812433253L * (previous ^ (previous >>> 30)) + i);
            }
        }

        int inclusive(int maximum) {
            if (maximum <= 0) return 0;
            long random = Integer.toUnsignedLong(next());
            return (int) ((random * (maximum + 1L)) >>> 32);
        }

        private int next() {
            if (index >= state.length) twist();
            int value = state[index++];
            value ^= value >>> 11;
            value ^= (value << 7) & 0x9d2c5680;
            value ^= (value << 15) & 0xefc60000;
            value ^= value >>> 18;
            return value;
        }

        private void twist() {
            for (int i = 0; i < state.length; i++) {
                int value = (state[i] & 0x80000000) | (state[(i + 1) % 624] & 0x7fffffff);
                state[i] = state[(i + 397) % 624] ^ (value >>> 1);
                if ((value & 1) != 0) state[i] ^= 0x9908b0df;
            }
            index = 0;
        }
    }
}
