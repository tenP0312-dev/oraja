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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
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
        if (!supportsLr2ExtraMode(model.getMode())) {
            applyExtendedExtraMode(model, requestedLevel);
            return;
        }
        TimeLine[] timelines = model.getAllTimeLines();
        int noteCount = model.getTotalNotes();
        int level = requestedLevel - 1;
        if (level == 0 && noteCount >= 1_200) level = 2;
        else if (level == 0 && noteCount >= 1_000) level = 1;
        else if (level == 1 && noteCount >= 1_000) level = 2;

        moveDpScratchesToBackground(model);
        Map<Integer, Integer> preferredLanes = lr2PreferredLanesByWav(model);
        fillUnusedLr2SoundLanes(model, preferredLanes);
        long lastPlayable = lastPlayableTime(model);
        long minimumGap = minimumExtraGap(model.getBpm(), level);
        double previousBpm = model.getBpm();
        boolean alternateDpSide = false;

        for (int timelineIndex = 0; timelineIndex < timelines.length; timelineIndex++) {
            TimeLine timeline = timelines[timelineIndex];
            if (timeline.getMicroTime() > lastPlayable) break;
            if (timeline.getBPM() > 0 && timeline.getBPM() != previousBpm) {
                minimumGap = Math.max(125_000L, Math.round(30_000_000.0 / timeline.getBPM()));
                previousBpm = timeline.getBPM();
            }
            Note[] backgrounds = timeline.getBackGroundNotes().clone();
            if (backgrounds.length == 0) continue;
            boolean[] occupied = lr2OccupiedNear(timelines, timelineIndex, minimumGap, model.getMode());
            for (Note background : backgrounds) {
                Integer preferred = preferredLanes.get(background.getWav());
                if (preferred == null || preferred < 0 || preferred >= 20) continue;
                int candidate = preferred;
                if (model.getMode().player == 2) {
                    candidate = alternateDpSide
                            ? moveLr2LaneToFirstSide(candidate)
                            : moveLr2LaneToSecondSide(candidate);
                    alternateDpSide = !alternateDpSide;
                }
                int selected = selectLr2ExtraLane(
                        candidate,
                        occupied,
                        background.getWav(),
                        model.getMode()
                );
                int modelLane = fromLr2Lane(selected, model.getMode());
                if (modelLane < 0 || timeline.getNote(modelLane) != null) continue;
                timeline.removeBackGroundNote(background);
                timeline.setNote(modelLane, background);
                occupied[selected] = true;
            }
        }
    }

    private static boolean supportsLr2ExtraMode(Mode mode) {
        return mode == Mode.BEAT_5K || mode == Mode.BEAT_7K
                || mode == Mode.BEAT_10K || mode == Mode.BEAT_14K
                || mode == Mode.POPN_5K || mode == Mode.POPN_9K;
    }

    private static void moveDpScratchesToBackground(BMSModel model) {
        if (model.getMode().player != 2) return;
        Map<Note, TimeLine> owners = noteOwners(model.getAllTimeLines());
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int scratch : model.getMode().scratchKey) {
                Note note = timeline.getNote(scratch);
                if (note == null || note instanceof LongNote longNote && longNote.isEnd()) continue;
                timeline.setNote(scratch, null);
                if (note instanceof LongNote longNote && longNote.getPair() != null) {
                    TimeLine pairOwner = owners.get(longNote.getPair());
                    if (pairOwner != null) pairOwner.setNote(scratch, null);
                }
                timeline.addBackGroundNote(new NormalNote(note.getWav()));
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

    private static Map<Integer, Integer> lr2PreferredLanesByWav(BMSModel model) {
        Map<Integer, int[]> counts = new HashMap<>();
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < model.getMode().key; lane++) {
                Note note = timeline.getNote(lane);
                if (!isPlayableStart(note)) continue;
                int lr2Lane = toLr2Lane(lane, model.getMode());
                if (lr2Lane >= 0) counts.computeIfAbsent(note.getWav(), ignored -> new int[20])[lr2Lane]++;
            }
        }
        Map<Integer, Integer> result = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : counts.entrySet()) {
            int bestLane = -1;
            int bestCount = 0;
            for (int lane = 0; lane < entry.getValue().length; lane++) {
                if (entry.getValue()[lane] > bestCount) {
                    bestLane = lane;
                    bestCount = entry.getValue()[lane];
                }
            }
            if (bestLane >= 0) result.put(entry.getKey(), bestLane);
        }
        return result;
    }

    private static void fillUnusedLr2SoundLanes(BMSModel model, Map<Integer, Integer> lanes) {
        String[] wavs = model.getWavList();
        Map<String, Integer> usedByFile = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : lanes.entrySet()) {
            String file = wavFile(wavs, entry.getKey());
            if (file != null) usedByFile.merge(file, 1, Integer::sum);
        }
        for (int wav = 1; wav < wavs.length; wav++) {
            if (lanes.containsKey(wav)) continue;
            String file = wavFile(wavs, wav);
            if (file == null || usedByFile.getOrDefault(file, 0) <= 0) continue;
            int previous = lanes.getOrDefault(wav - 1, -1);
            lanes.put(wav, fallbackLr2Lane(previous, model.getMode()));
        }
    }

    private static String wavFile(String[] wavs, int wav) {
        if (wav < 0 || wav >= wavs.length || wavs[wav] == null || wavs[wav].isBlank()) return null;
        return wavs[wav].replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static int fallbackLr2Lane(int lane, Mode mode) {
        if (lane < 1) return mode == Mode.POPN_5K || mode == Mode.POPN_9K ? 7 : -1;
        int local = lane % 10;
        if (mode == Mode.BEAT_5K || mode == Mode.BEAT_10K) {
            return switch (local) { case 1 -> 3; case 2 -> 4; case 3 -> 5; case 4 -> 1; case 5 -> 2; default -> -1; };
        }
        if (mode == Mode.BEAT_7K || mode == Mode.BEAT_14K) {
            return switch (local) { case 1, 6 -> 3; case 2 -> 4; case 3 -> 5; case 4 -> 6; case 5 -> 7; case 7 -> 2; default -> -1; };
        }
        return switch (local) { case 1 -> 3; case 2 -> 5; case 3 -> 7; case 4 -> 9; case 5 -> 2; case 6 -> 4; case 7 -> 6; case 8 -> 8; case 9 -> 1; default -> -1; };
    }

    private static long lastPlayableTime(BMSModel model) {
        long result = Long.MIN_VALUE;
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < model.getMode().key; lane++) {
                if (isPlayableStart(timeline.getNote(lane))) result = Math.max(result, timeline.getMicroTime());
            }
        }
        return result;
    }

    private static boolean[] lr2OccupiedNear(TimeLine[] timelines, int center, long gap, Mode mode) {
        boolean[] occupied = new boolean[20];
        long centerTime = timelines[center].getMicroTime();
        int first = center;
        while (first > 0 && centerTime - timelines[first - 1].getMicroTime() < gap) first--;
        int last = center;
        while (last + 1 < timelines.length && timelines[last + 1].getMicroTime() - centerTime < gap) last++;
        for (int index = first; index <= last; index++) {
            TimeLine timeline = timelines[index];
            for (int lane = 0; lane < mode.key; lane++) {
                if (!isPlayableStart(timeline.getNote(lane))) continue;
                int lr2Lane = toLr2Lane(lane, mode);
                if (lr2Lane >= 0) occupied[lr2Lane] = true;
            }
        }
        return occupied;
    }

    private static int selectLr2ExtraLane(int preferred, boolean[] occupied, int wav, Mode mode) {
        int keys = lr2KeysPerSide(mode);
        if (validLr2Lane(preferred, keys, mode.player == 2) && !occupied[preferred]) return preferred;
        if (preferred == 0 || preferred == 10) return -1;
        int shift = (wav & 1) == 0 ? -1 : 1;
        if (mode.player == 1) {
            for (int distance = 1; distance <= keys; distance++) {
                int next = preferred + distance * shift;
                int previous = preferred - distance * shift;
                if (next >= 1 && next <= keys && !occupied[next]) return next;
                if (previous >= 1 && previous <= keys && !occupied[previous]) return previous;
            }
            return -1;
        }
        int lane = preferred;
        for (int distance = 1; distance <= keys; distance++) {
            int next = lane + distance * shift;
            int previous = lane - distance * shift;
            if (validDpKey(next, keys) && !occupied[next]) return next;
            if (validDpKey(previous, keys) && !occupied[previous]) return previous;
            lane = lane <= 10 ? lane + 10 : lane - 10;
            next = lane + distance * shift;
            previous = lane - distance * shift;
            if (validDpKey(next, keys) && !occupied[next]) return next;
            if (validDpKey(previous, keys) && !occupied[previous]) return previous;
        }
        return -1;
    }

    private static int moveLr2LaneToSecondSide(int lane) {
        return lane < 10 ? lane + 10 : lane;
    }

    private static int moveLr2LaneToFirstSide(int lane) {
        return lane >= 10 ? lane - 10 : lane;
    }

    private static boolean validLr2Lane(int lane, int keys, boolean dp) {
        if (lane < 0 || lane >= 20) return false;
        if (!dp) return lane >= 0 && lane <= keys;
        int local = lane % 10;
        return local >= 0 && local <= keys;
    }

    private static boolean validDpKey(int lane, int keys) {
        return lane >= 0 && lane < 20 && lane % 10 >= 1 && lane % 10 <= keys;
    }

    private static int lr2KeysPerSide(Mode mode) {
        return switch (mode) {
            case BEAT_5K, BEAT_10K, POPN_5K -> 5;
            case BEAT_7K, BEAT_14K -> 7;
            case POPN_9K -> 9;
            default -> mode.key / Math.max(1, mode.player);
        };
    }

    private static int toLr2Lane(int lane, Mode mode) {
        if (lane < 0 || lane >= mode.key) return -1;
        if (mode == Mode.POPN_5K || mode == Mode.POPN_9K) return lane + 1;
        int sideWidth = mode.key / mode.player;
        int side = mode.player == 2 && lane >= sideWidth ? 1 : 0;
        int local = lane - side * sideWidth;
        if (mode.isScratchKey(lane)) return side * 10;
        return side * 10 + local + 1;
    }

    private static int fromLr2Lane(int lane, Mode mode) {
        if (lane < 0) return -1;
        if (mode == Mode.POPN_5K || mode == Mode.POPN_9K) {
            int value = lane - 1;
            return value >= 0 && value < mode.key ? value : -1;
        }
        int side = mode.player == 2 && lane >= 10 ? 1 : 0;
        int local = lane % 10;
        int sideWidth = mode.key / mode.player;
        if (local == 0) {
            int scratch = side * sideWidth + sideWidth - 1;
            return mode.isScratchKey(scratch) ? scratch : -1;
        }
        int result = side * sideWidth + local - 1;
        return result >= side * sideWidth && result < (side + 1) * sideWidth ? result : -1;
    }

    private static void applyExtendedExtraMode(BMSModel model, int requestedLevel) {
        int level = requestedLevel - 1;
        int noteCount = model.getTotalNotes();
        if (level == 0 && noteCount >= 1_200) level = 2;
        else if (level == 0 && noteCount >= 1_000) level = 1;
        else if (level == 1 && noteCount >= 1_000) level = 2;
        long minimumGap = minimumExtraGap(model.getBpm(), level);
        Map<Integer, Integer> preferred = new HashMap<>();
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int lane = 0; lane < model.getMode().key; lane++) {
                Note note = timeline.getNote(lane);
                if (isPlayableStart(note)) preferred.putIfAbsent(note.getWav(), lane);
            }
        }
        long[] lastPlaced = new long[model.getMode().key];
        Arrays.fill(lastPlaced, Long.MIN_VALUE / 4);
        for (TimeLine timeline : model.getAllTimeLines()) {
            boolean[] occupied = occupiedAt(timeline, model.getMode().key);
            for (Note background : timeline.getBackGroundNotes().clone()) {
                int lane = preferred.getOrDefault(background.getWav(), -1);
                if (lane < 0 || occupied[lane]
                        || timeline.getMicroTime() - lastPlaced[lane] < minimumGap) continue;
                timeline.removeBackGroundNote(background);
                timeline.setNote(lane, background);
                occupied[lane] = true;
                lastPlaced[lane] = timeline.getMicroTime();
            }
        }
    }

    private static boolean[] occupiedAt(TimeLine timeline, int lanes) {
        boolean[] result = new boolean[lanes];
        for (int lane = 0; lane < lanes; lane++) result[lane] = timeline.getNote(lane) != null;
        return result;
    }

    private static void applyAddNotes(BMSModel model, int percent, LR2Random random) {
        int[][] sides = playerLanes(model.getMode(), false);
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int[] side : sides) {
                boolean[] occupied = occupiedAt(timeline, model.getMode().key);
                int original = 0;
                for (int lane : side) if (isPlayableStart(timeline.getNote(lane))) original++;
                for (int index = 0; index < original; index++) {
                    if (random.inclusive(100) > percent) continue;
                    List<Integer> empty = new ArrayList<>();
                    for (int lane : side) if (!occupied[lane]) empty.add(lane);
                    if (empty.isEmpty()) break;
                    int lane = empty.get(random.inclusive(empty.size() - 1));
                    timeline.setNote(lane, new NormalNote(-1));
                    occupied[lane] = true;
                }
            }
        }
    }

    private static void applyAddLongNotes(BMSModel model, int percent, LR2Random random) {
        TreeMap<Long, TimeLine> timelines = timelinesByTime(model);
        int lanes = model.getMode().key;
        Map<Note, TimeLine> owners = noteOwners(timelines.values().toArray(TimeLine[]::new));
        long chartEnd = timelines.isEmpty() ? 0 : timelines.lastKey();
        for (int lane : allPlayerLanes(model.getMode(), true)) {
            List<NotePosition> starts = noteStarts(timelines, lane);
            for (int index = 0; index + 1 < starts.size(); index++) {
                if (random.inclusive(100) >= percent) continue;
                long endTime = (starts.get(index).timeline.getMicroTime()
                        + starts.get(index + 1).timeline.getMicroTime()) / 2;
                extendLongNote(timelines, owners, starts.get(index), lane, endTime, lanes);
            }
            if (percent == 100 && !starts.isEmpty()) {
                extendLongNote(timelines, owners, starts.get(starts.size() - 1), lane, chartEnd, lanes);
            }
        }
        model.setAllTimeLine(timelines.values().toArray(TimeLine[]::new));
    }

    private static void applyAddMines(BMSModel model, int percent, LR2Random random) {
        TreeMap<Long, TimeLine> timelines = timelinesByTime(model);
        int lanes = model.getMode().key;
        for (int lane : allPlayerLanes(model.getMode(), true)) {
            List<NotePosition> notes = noteStarts(timelines, lane);
            for (int index = 0; index + 1 < notes.size(); index++) {
                NotePosition left = notes.get(index);
                NotePosition right = notes.get(index + 1);
                long leftEnd = noteEndTime(left);
                long gap = right.timeline.getMicroTime() - leftEnd;
                if (gap <= MIN_INSERT_GAP_US || random.inclusive(100) >= percent) continue;
                long mineTime = leftEnd + gap / 2;
                TimeLine mineLine = timelineAt(timelines, mineTime, lanes);
                if (mineLine.getNote(lane) == null) {
                    mineLine.setNote(lane, new MineNote(-1, 4.0));
                }
            }
        }
        model.setAllTimeLine(timelines.values().toArray(TimeLine[]::new));
    }

    private static void applyLoudness(BMSModel model, int percent, LR2Random random) {
        int[][] sides = playerLanes(model.getMode(), false);
        for (TimeLine timeline : model.getAllTimeLines()) {
            for (int[] side : sides) {
                int sourceWav = -1;
                for (int lane : side) {
                    Note note = timeline.getNote(lane);
                    if (isPlayableStart(note)) {
                        sourceWav = note.getWav();
                        break;
                    }
                }
                if (sourceWav < 0 || random.inclusive(100) > percent) continue;
                for (int lane : side) {
                    if (timeline.getNote(lane) == null) {
                        timeline.setNote(lane, new NormalNote(sourceWav));
                    }
                }
            }
        }
    }

    private static int[][] playerLanes(Mode mode, boolean excludeScratch) {
        int sideWidth = mode.key / mode.player;
        int[][] result = new int[mode.player][];
        for (int player = 0; player < mode.player; player++) {
            int start = player * sideWidth;
            result[player] = java.util.stream.IntStream.range(start, start + sideWidth)
                    .filter(lane -> !excludeScratch || !mode.isScratchKey(lane))
                    .toArray();
        }
        return result;
    }

    private static int[] allPlayerLanes(Mode mode, boolean excludeScratch) {
        return Arrays.stream(playerLanes(mode, excludeScratch)).flatMapToInt(Arrays::stream).toArray();
    }

    private static boolean isPlayableStart(Note note) {
        return note instanceof NormalNote || note instanceof LongNote longNote && !longNote.isEnd();
    }

    private static Map<Note, TimeLine> noteOwners(TimeLine[] timelines) {
        Map<Note, TimeLine> result = new IdentityHashMap<>();
        for (TimeLine timeline : timelines) {
            for (int lane = 0; lane < timeline.getLaneCount(); lane++) {
                Note note = timeline.getNote(lane);
                if (note != null) result.put(note, timeline);
            }
        }
        return result;
    }

    private static List<NotePosition> noteStarts(TreeMap<Long, TimeLine> timelines, int lane) {
        List<NotePosition> result = new ArrayList<>();
        for (TimeLine timeline : timelines.values()) {
            Note note = timeline.getNote(lane);
            if (isPlayableStart(note)) result.add(new NotePosition(timeline, note));
        }
        return result;
    }

    private static long noteEndTime(NotePosition position) {
        if (position.note instanceof LongNote longNote && longNote.getPair() != null) {
            return Math.max(position.timeline.getMicroTime(), longNote.getPair().getMicroTime());
        }
        return position.timeline.getMicroTime();
    }

    private static void extendLongNote(
            TreeMap<Long, TimeLine> timelines,
            Map<Note, TimeLine> owners,
            NotePosition position,
            int lane,
            long endTime,
            int lanes
    ) {
        if (endTime <= noteEndTime(position)) return;
        TimeLine endLine = timelineAt(timelines, endTime, lanes);
        Note source = position.note;
        LongNote existingEnd = source instanceof LongNote existing ? existing.getPair() : null;
        Note target = endLine.getNote(lane);
        if (target != null && target != existingEnd) return;
        LongNote start;
        LongNote end;
        if (source instanceof LongNote existing) {
            start = existing;
            end = existingEnd;
            if (end == null) return;
            TimeLine oldEnd = owners.get(end);
            if (oldEnd != null) oldEnd.setNote(lane, null);
        } else {
            start = new LongNote(source.getWav(), source.getMicroStarttime(), source.getMicroDuration());
            end = new LongNote(-1);
            start.setType(LongNote.TYPE_LONGNOTE);
            end.setType(LongNote.TYPE_LONGNOTE);
            position.timeline.setNote(lane, start);
        }
        endLine.setNote(lane, end);
        start.setPair(end);
        owners.put(start, position.timeline);
        owners.put(end, endLine);
    }

    private record NotePosition(TimeLine timeline, Note note) {
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
