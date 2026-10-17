package bms.player.beatoraja.nonstop;

import bms.model.BMSModel;
import bms.model.ChartInformation;
import bms.model.Layer;
import bms.model.LongNote;
import bms.model.MineNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.Note;
import bms.model.TimeLine;
import bms.player.beatoraja.play.BMSPlayerRule;
import bms.player.beatoraja.song.SongResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns a {@link MixPlan} and the loaded charts into one continuous chart
 * (Issue #535 §6). The merged chart is played by the ordinary player, so judge,
 * gauge, BGA, key sounds and the result screen all work on the whole set.
 *
 * <ul>
 *     <li>Each song keeps its own timing; set time = chart time + offset.</li>
 *     <li>Only the song that owns the lanes (between two hand-offs) has playable
 *     notes. Every other note of a song that is audible at that moment is moved
 *     to the background lane, so its key sound still plays exactly once.</li>
 *     <li>Long notes never cross a hand-off or a cut: such a join is reported
 *     as an error instead of being repaired silently.</li>
 * </ul>
 */
public final class MixCompiler {
    public static final String MODEL_MARKER = "bmsir.nonstopMix";
    private static final long US = 1000L;

    private MixCompiler() {
    }

    /** One loaded song. */
    public record Source(BMSModel model, SongResource chart, double loudnessGain) {
    }

    public enum Severity { ERROR, WARNING }

    /** A problem found while compiling; {@code track}/{@code join} are -1 when not applicable. */
    public record Issue(Severity severity, int track, int join, long setTimeMs, String japanese, String english) {
    }

    /** Last chart time that still has a sound or note, used when a track plays to its end. */
    public static long chartEnd(BMSModel model) {
        long last = 0;
        for (TimeLine tl : model.getAllTimeLines()) {
            boolean used = tl.getBackGroundNotes().length > 0 || tl.getBGA() != -1;
            for (int lane = 0; !used && lane < tl.getLaneCount(); lane++) {
                used = tl.getNote(lane) != null || tl.getHiddenNote(lane) != null;
            }
            if (used) {
                last = Math.max(last, tl.getMilliTime());
            }
        }
        return last;
    }

    public static CompiledMix compile(MixPlan plan, List<Source> sources) {
        if (plan.tracks.isEmpty()) {
            throw new IllegalArgumentException("the set has no songs");
        }
        if (sources.size() != plan.tracks.size()) {
            throw new IllegalArgumentException("every song must be loaded before compiling");
        }
        plan.normalizeTransitions();
        final int n = plan.tracks.size();
        final Mode mode = plan.mode;
        final int lanes = mode.key;
        final boolean withBga = !plan.noBga;
        List<Issue> issues = new ArrayList<>();

        long[] chartEnds = new long[n];
        for (int i = 0; i < n; i++) {
            BMSModel model = sources.get(i).model();
            chartEnds[i] = chartEnd(model);
            if (model.getMode() != mode) {
                issues.add(new Issue(Severity.ERROR, i, -1, -1,
                        "鍵盤モードがセット(" + mode.hint + ")と違います: " + model.getMode().hint,
                        "Key mode differs from the set (" + mode.hint + "): " + model.getMode().hint));
            }
        }
        MixPlan.Layout layout = plan.layout(chartEnds);

        // resource tables: every track keeps its own name space
        List<String> wavs = new ArrayList<>();
        List<String> bgas = new ArrayList<>();
        int[] wavOffset = new int[n];
        int[] wavCount = new int[n];
        int[] bgaOffset = new int[n];
        List<String[]> sourceBgas = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BMSModel model = sources.get(i).model();
            wavOffset[i] = wavs.size();
            String[] list = model.getWavList();
            wavCount[i] = list == null ? 0 : list.length;
            for (int w = 0; w < wavCount[i]; w++) {
                wavs.add(MixSongResource.prefixed(i, list[w]));
            }
            bgaOffset[i] = bgas.size();
            String[] bgaList = model.getBgaList();
            sourceBgas.add(bgaList);
            if (withBga && bgaList != null) {
                for (String name : bgaList) {
                    bgas.add(MixSongResource.prefixed(i, name));
                }
            }
        }
        final int defaultMineWav = wavs.size();

        float[] staticGain = new float[n];
        for (int i = 0; i < n; i++) {
            BMSModel model = sources.get(i).model();
            double gain = Math.pow(10.0, plan.tracks.get(i).gainDb / 20.0);
            if (model.getVolwav() > 0 && model.getVolwav() < 100) {
                gain *= model.getVolwav() / 100.0;
            }
            double loudness = sources.get(i).loudnessGain();
            if (plan.normalizeLoudness && Double.isFinite(loudness) && loudness > 0) {
                gain *= loudness;
            }
            staticGain[i] = (float) Math.max(0.0, Math.min(4.0, gain));
        }

        TreeMap<Long, Slot> slots = new TreeMap<>();
        IdentityHashMap<Note, Integer> noteTrack = new IdentityHashMap<>();
        List<List<TimedNote>> trackSounds = new ArrayList<>();
        // long-note pairing: original start -> new start, kept per track
        List<Map<LongNote, LongNote>> pending = new ArrayList<>();
        int playable = 0;

        for (int i = 0; i < n; i++) {
            BMSModel model = sources.get(i).model();
            MixTrack track = plan.tracks.get(i);
            long in = Math.max(0L, track.inMs);
            long out = track.resolvedOut(chartEnds[i]);
            long offset = layout.offset()[i];
            long from = layout.playableFrom(i);
            long until = layout.playableUntil(i);
            List<TimedNote> sounds = new ArrayList<>();
            trackSounds.add(sounds);
            Map<LongNote, LongNote> lnMap = new IdentityHashMap<>();
            pending.add(lnMap);

            for (TimeLine tl : model.getAllTimeLines()) {
                long chartMs = tl.getMilliTime();
                long chartUs = tl.getMicroTime();
                if (chartMs < in || chartMs > out) {
                    continue;
                }
                long setUs = chartUs + offset * US;
                long setMs = setUs / US;
                boolean owns = setMs >= from && setMs < until;
                Slot slot = null;
                for (int lane = 0; lane < Math.min(lanes, tl.getLaneCount()); lane++) {
                    Note note = tl.getNote(lane);
                    if (note == null) {
                        continue;
                    }
                    if (note instanceof LongNote ln) {
                        LongNote start = ln.isEnd() ? ln.getPair() : ln;
                        LongNote end = ln.isEnd() ? ln : ln.getPair();
                        if (start == null || end == null) {
                            continue;
                        }
                        long startMs = start.getMilliTime() + offset;
                        long endMs = end.getMilliTime() + offset;
                        boolean startOwned = startMs >= from && startMs < until;
                        boolean endOwned = endMs >= from && endMs <= until;
                        boolean inRange = start.getMilliTime() >= in && end.getMilliTime() <= out;
                        boolean asPlayable = startOwned && endOwned && inRange;
                        if (!ln.isEnd()) {
                            if ((startOwned || endOwned) && !asPlayable) {
                                issues.add(longNoteIssue(i, startMs, startOwned, endOwned, inRange, from, until));
                            }
                        }
                        if (asPlayable) {
                            if (slot == null) {
                                slot = slot(slots, setUs, lanes);
                            }
                            if (slot.notes[lane] != null) {
                                issues.add(new Issue(Severity.ERROR, i, -1, setMs,
                                        "同じ位置に2つのノーツが重なります", "Two notes overlap at the same position"));
                                continue;
                            }
                            LongNote copy = new LongNote(remap(ln.getWav(), wavOffset[i], wavCount[i], defaultMineWav),
                                    ln.getMicroStarttime(), ln.getMicroDuration());
                            copy.setType(ln.getType());
                            copyLayered(ln, copy, wavOffset[i], wavCount[i], defaultMineWav, noteTrack, i);
                            slot.notes[lane] = copy;
                            noteTrack.put(copy, i);
                            if (!ln.isEnd()) {
                                lnMap.put(ln, copy);
                                playable++;
                                sounds.add(new TimedNote(setMs, copy, false));
                            } else {
                                LongNote startCopy = lnMap.remove(start);
                                slot.pairs.add(new LongNote[] {startCopy, copy});
                            }
                        } else if (!ln.isEnd() && ln.getWav() >= 0) {
                            if (slot == null) {
                                slot = slot(slots, setUs, lanes);
                            }
                            Note bg = background(ln, wavOffset[i], wavCount[i], defaultMineWav);
                            slot.background.add(bg);
                            noteTrack.put(bg, i);
                            sounds.add(new TimedNote(setMs, bg, true));
                        }
                        continue;
                    }
                    if (note instanceof MineNote mine) {
                        if (owns) {
                            if (slot == null) {
                                slot = slot(slots, setUs, lanes);
                            }
                            if (slot.notes[lane] == null) {
                                MineNote copy = new MineNote(remapMine(mine.getWav(), wavOffset[i], wavCount[i],
                                        defaultMineWav), mine.getDamage());
                                slot.notes[lane] = copy;
                                noteTrack.put(copy, i);
                            }
                        }
                        continue;
                    }
                    if (slot == null) {
                        slot = slot(slots, setUs, lanes);
                    }
                    if (owns && slot.notes[lane] == null) {
                        Note copy = new NormalNote(remap(note.getWav(), wavOffset[i], wavCount[i], defaultMineWav),
                                note.getMicroStarttime(), note.getMicroDuration());
                        copyLayered(note, copy, wavOffset[i], wavCount[i], defaultMineWav, noteTrack, i);
                        slot.notes[lane] = copy;
                        noteTrack.put(copy, i);
                        sounds.add(new TimedNote(setMs, copy, false));
                        playable++;
                    } else if (note.getWav() >= 0) {
                        Note bg = background(note, wavOffset[i], wavCount[i], defaultMineWav);
                        slot.background.add(bg);
                        noteTrack.put(bg, i);
                        sounds.add(new TimedNote(setMs, bg, true));
                    }
                }
                if (owns) {
                    for (int lane = 0; lane < Math.min(lanes, tl.getLaneCount()); lane++) {
                        Note hidden = tl.getHiddenNote(lane);
                        if (hidden != null) {
                            if (slot == null) {
                                slot = slot(slots, setUs, lanes);
                            }
                            Note copy = new NormalNote(remap(hidden.getWav(), wavOffset[i], wavCount[i], defaultMineWav),
                                    hidden.getMicroStarttime(), hidden.getMicroDuration());
                            slot.hidden[lane] = copy;
                            noteTrack.put(copy, i);
                        }
                    }
                }
                for (Note note : tl.getBackGroundNotes()) {
                    if (note.getWav() < 0) {
                        continue;
                    }
                    if (slot == null) {
                        slot = slot(slots, setUs, lanes);
                    }
                    Note bg = background(note, wavOffset[i], wavCount[i], defaultMineWav);
                    slot.background.add(bg);
                    noteTrack.put(bg, i);
                    sounds.add(new TimedNote(setMs, bg, true));
                }
                if (owns) {
                    boolean bgaEvent = withBga && (tl.getBGA() != -1 || tl.getLayer() != -1 || tl.getEventlayer().length > 0);
                    if (bgaEvent || tl.getSectionLine() || tl.getStop() > 0) {
                        if (slot == null) {
                            slot = slot(slots, setUs, lanes);
                        }
                        if (withBga && tl.getBGA() != -1) {
                            slot.bga = tl.getBGA() + bgaOffset[i];
                        }
                        if (withBga && tl.getLayer() != -1) {
                            slot.layer = tl.getLayer() + bgaOffset[i];
                        }
                        if (withBga && tl.getEventlayer().length > 0) {
                            slot.eventLayer = remapLayers(tl.getEventlayer(), bgaOffset[i]);
                        }
                        slot.sectionLine |= tl.getSectionLine();
                        if (tl.getMicroStop() > 0) {
                            slot.stopUs = Math.max(slot.stopUs, tl.getMicroStop());
                        }
                    }
                }
            }
            sounds.sort((a, b) -> Long.compare(a.setMs(), b.setMs()));
        }

        // keep BGA continuity: at each hand-off show what the next song shows at that moment
        for (int j = 0; j < n - 1; j++) {
            long h = layout.handoff()[j];
            int next = j + 1;
            BMSModel model = sources.get(next).model();
            long chartAt = h - layout.offset()[next];
            int bga = -1;
            int layer = -1;
            for (TimeLine tl : withBga ? model.getAllTimeLines() : new TimeLine[0]) {
                if (tl.getMilliTime() > chartAt) {
                    break;
                }
                if (tl.getBGA() != -1) {
                    bga = tl.getBGA();
                }
                if (tl.getLayer() != -1) {
                    layer = tl.getLayer();
                }
            }
            Slot slot = slot(slots, h * US, lanes);
            if (slot.bga == -1 && bga != -1) {
                slot.bga = bga + bgaOffset[next];
            }
            if (slot.layer == -1 && layer != -1) {
                slot.layer = layer + bgaOffset[next];
            }
            slot.sectionLine = true;
        }
        slot(slots, 0L, lanes);

        // tempo, scroll, stops and bar positions follow the song that owns the lanes
        List<TimeLine> timelines = new ArrayList<>(slots.size());
        double section = 0;
        long prevUs = 0;
        double prevBpm = -1;
        long prevStopEnd = 0;
        TimeLine prev = null;
        for (Map.Entry<Long, Slot> entry : slots.entrySet()) {
            long setUs = entry.getKey();
            Slot slot = entry.getValue();
            int owner = ownerAt(layout, setUs / US, n);
            BMSModel ownerModel = sources.get(owner).model();
            long chartUs = setUs - layout.offset()[owner] * US;
            double bpm = bpmAt(ownerModel, chartUs);
            double scroll = scrollAt(ownerModel, chartUs);
            if (prev != null) {
                long moving = setUs - prevUs - prev.getMicroStop();
                if (moving < 0) {
                    // an event of the other song falls inside a STOP: split the stop around it
                    long remaining = prevStopEnd - setUs;
                    prev.setStop(Math.max(0L, setUs - prevUs));
                    slot.stopUs = Math.max(slot.stopUs, remaining);
                    moving = 0;
                }
                section += moving * prevBpm / 240_000_000.0;
            }
            TimeLine tl = new TimeLine(section, setUs, lanes);
            tl.setBPM(bpm);
            tl.setScroll(scroll);
            tl.setStop(slot.stopUs);
            tl.setSectionLine(slot.sectionLine);
            tl.setBGA(slot.bga);
            tl.setLayer(slot.layer);
            if (slot.eventLayer != null) {
                tl.setEventlayer(slot.eventLayer);
            }
            for (int lane = 0; lane < lanes; lane++) {
                if (slot.notes[lane] != null) {
                    Note note = slot.notes[lane];
                    Note[] layered = note.getLayeredNotes();
                    tl.setNote(lane, note);
                    // layered notes follow their parent's position
                    for (Note l : layered) {
                        l.setSection(section);
                        l.setMicroTime(setUs);
                    }
                }
                if (slot.hidden[lane] != null) {
                    tl.setHiddenNote(lane, slot.hidden[lane]);
                }
            }
            for (Note bg : slot.background) {
                tl.addBackGroundNote(bg);
            }
            timelines.add(tl);
            prevUs = setUs;
            prevBpm = bpm;
            prevStopEnd = setUs + slot.stopUs;
            prev = tl;
        }
        for (Slot slot : slots.values()) {
            for (LongNote[] pair : slot.pairs) {
                if (pair[0] != null && pair[1] != null) {
                    pair[0].setPair(pair[1]);
                }
            }
        }
        // a long note whose end never arrived must not stay half-built
        for (TimeLine tl : timelines) {
            for (int lane = 0; lane < lanes; lane++) {
                if (tl.getNote(lane) instanceof LongNote ln && ln.getPair() == null) {
                    tl.setNote(lane, null);
                    Note bg = new NormalNote(ln.getWav(), ln.getMicroStarttime(), ln.getMicroDuration());
                    tl.addBackGroundNote(bg);
                    Integer owner = noteTrack.remove(ln);
                    if (owner != null) {
                        noteTrack.put(bg, owner);
                        replaceSound(trackSounds.get(owner), ln, bg);
                    }
                    playable--;
                }
            }
        }

        if (playable <= 0) {
            issues.add(new Issue(Severity.ERROR, -1, -1, -1, "叩けるノーツがありません", "The set has no playable notes"));
        }
        for (int j = 0; j < n - 1; j++) {
            MixTransition join = plan.transitions.get(j);
            if (join.type == MixTransition.Type.BLEND && join.overlapMs > layout.end()[j] - layout.start()[j]) {
                issues.add(new Issue(Severity.WARNING, j, j, layout.start()[j + 1],
                        "つなぎが前の曲より長くなっています", "The join is longer than the previous song"));
            }
            if (layout.end()[j + 1] <= layout.handoff()[j]) {
                issues.add(new Issue(Severity.ERROR, j + 1, j, layout.handoff()[j],
                        "次の曲が譜面交代より前に終わります", "The next song ends before the hand-off"));
            }
        }

        BMSModel merged = new BMSModel();
        merged.setAllTimeLine(timelines.toArray(TimeLine[]::new));
        merged.setMode(mode);
        merged.setWavList(wavs.toArray(String[]::new));
        merged.setBgaList(bgas.toArray(String[]::new));
        String identity = MixJson.playIdentity(plan);
        merged.setSHA256(identity);
        merged.setMD5(identity.substring(0, 32));
        merged.setTitle(plan.name == null || plan.name.isBlank() ? "NONSTOP MIX" : plan.name);
        merged.setSubTitle("NONSTOP MIX");
        merged.setArtist(n + (n == 1 ? " song" : " songs"));
        merged.setGenre("NONSTOP MIX");
        merged.setBpm(timelines.isEmpty() ? 120 : timelines.get(0).getBPM());
        merged.setPlaylevel(String.valueOf(maxLevel(plan)));
        merged.setVolwav(100);
        merged.setLnmode(sources.get(0).model().getLnmode());
        merged.setChartInformation(new ChartInformation(Path.of("nonstop-mix", plan.id + ".mix"),
                sources.get(0).model().getLntype(), null));
        applyJudge(plan, sources, merged);
        merged.setTotal(0);
        merged.setTotalType(BMSModel.TotalType.BMS);
        BMSPlayerRule.validate(merged);
        merged.setTotal(merged.getTotal() * plan.totalScale);
        merged.getValues().put(MODEL_MARKER, identity);
        String stagefile = sources.get(0).model().getStagefile();
        if (stagefile != null && !stagefile.isEmpty()) {
            merged.setStagefile(MixSongResource.prefixed(0, stagefile));
        }
        String backbmp = sources.get(0).model().getBackbmp();
        if (withBga && backbmp != null && !backbmp.isEmpty()) {
            merged.setBackbmp(MixSongResource.prefixed(0, backbmp));
        }

        List<SongResource> charts = new ArrayList<>(n);
        for (Source source : sources) {
            charts.add(source.chart());
        }
        TimedNote[][] soundTable = new TimedNote[n][];
        for (int i = 0; i < n; i++) {
            soundTable[i] = trackSounds.get(i).toArray(TimedNote[]::new);
        }
        MixLoad load = MixLoad.of(n, layout.totalMs(), sourceBgas);
        return new CompiledMix(plan.copy(), merged, MixSongResource.chart(plan.id, charts), layout, chartEnds,
                staticGain, noteTrack, soundTable, List.copyOf(issues), identity, load);
    }

    private static Issue longNoteIssue(int track, long startMs, boolean startOwned, boolean endOwned,
            boolean inRange, long from, long until) {
        if (!inRange) {
            return new Issue(Severity.ERROR, track, -1, startMs,
                    "使用区間の端でロングノーツが切れます", "A long note is cut by the edge of the used part");
        }
        long where = startOwned && !endOwned ? until : from;
        return new Issue(Severity.ERROR, track, -1, where,
                "譜面交代の位置でロングノーツが切れます", "A long note crosses the hand-off point");
    }

    private static int ownerAt(MixPlan.Layout layout, long setMs, int n) {
        for (int i = 0; i < n - 1; i++) {
            if (setMs < layout.handoff()[i]) {
                return i;
            }
        }
        return n - 1;
    }

    static double bpmAt(BMSModel model, long chartUs) {
        double bpm = model.getBpm();
        for (TimeLine tl : model.getAllTimeLines()) {
            if (tl.getMicroTime() > chartUs) {
                break;
            }
            bpm = tl.getBPM();
        }
        return bpm > 0 ? bpm : 120;
    }

    private static double scrollAt(BMSModel model, long chartUs) {
        double scroll = 1.0;
        for (TimeLine tl : model.getAllTimeLines()) {
            if (tl.getMicroTime() > chartUs) {
                break;
            }
            scroll = tl.getScroll();
        }
        return scroll;
    }

    private static void applyJudge(MixPlan plan, List<Source> sources, BMSModel merged) {
        if (plan.judge.rankIndex >= 0) {
            merged.setJudgerankType(BMSModel.JudgeRankType.BMS_RANK);
            merged.setJudgerank(plan.judge.rankIndex);
            return;
        }
        // loaded charts were already normalised to the percentage scale
        int strictest = Integer.MAX_VALUE;
        long sum = 0;
        for (Source source : sources) {
            int rank = source.model().getJudgerank();
            strictest = Math.min(strictest, rank);
            sum += rank;
        }
        int value = plan.judge == MixPlan.JudgeRule.STRICTEST ? strictest : (int) Math.round((double) sum / sources.size());
        merged.setJudgerankType(BMSModel.JudgeRankType.BMSON_JUDGERANK);
        merged.setJudgerank(Math.max(1, value));
    }

    private static int maxLevel(MixPlan plan) {
        int level = 0;
        for (MixTrack track : plan.tracks) {
            level = Math.max(level, track.level);
        }
        return level;
    }

    private static int remap(int wav, int offset, int count, int defaultMine) {
        if (wav < 0) {
            return wav;
        }
        return wav < count ? wav + offset : defaultMine;
    }

    private static int remapMine(int wav, int offset, int count, int defaultMine) {
        return wav >= 0 && wav < count ? wav + offset : defaultMine;
    }

    private static Note background(Note note, int offset, int count, int defaultMine) {
        Note bg = new NormalNote(remap(note.getWav(), offset, count, defaultMine),
                note.getMicroStarttime(), note.getMicroDuration());
        // layered sounds of a note that is not played stay with it in the background
        for (Note layered : note.getLayeredNotes()) {
            bg.addLayeredNote(new NormalNote(remap(layered.getWav(), offset, count, defaultMine),
                    layered.getMicroStarttime(), layered.getMicroDuration()));
        }
        return bg;
    }

    private static void copyLayered(Note source, Note target, int offset, int count, int defaultMine,
            IdentityHashMap<Note, Integer> noteTrack, int track) {
        for (Note layered : source.getLayeredNotes()) {
            Note copy = new NormalNote(remap(layered.getWav(), offset, count, defaultMine),
                    layered.getMicroStarttime(), layered.getMicroDuration());
            target.addLayeredNote(copy);
            noteTrack.put(copy, track);
        }
    }

    private static Layer[] remapLayers(Layer[] layers, int offset) {
        Layer[] result = new Layer[layers.length];
        for (int i = 0; i < layers.length; i++) {
            Layer layer = layers[i];
            Layer.Sequence[][] sequences = new Layer.Sequence[layer.sequence.length][];
            for (int s = 0; s < sequences.length; s++) {
                Layer.Sequence[] source = layer.sequence[s];
                sequences[s] = new Layer.Sequence[source.length];
                for (int k = 0; k < source.length; k++) {
                    Layer.Sequence item = source[k];
                    sequences[s][k] = item.isEnd() || item.id < 0 ? item : new Layer.Sequence(item.time, item.id + offset);
                }
            }
            result[i] = new Layer(layer.event, sequences);
        }
        return result;
    }

    private static void replaceSound(List<TimedNote> sounds, Note from, Note to) {
        for (int i = 0; i < sounds.size(); i++) {
            if (sounds.get(i).note() == from) {
                sounds.set(i, new TimedNote(sounds.get(i).setMs(), to, true));
                return;
            }
        }
    }

    private static Slot slot(TreeMap<Long, Slot> slots, long setUs, int lanes) {
        return slots.computeIfAbsent(setUs, key -> new Slot(lanes));
    }

    /** A sound-producing note of one track at its set time; used for live volume changes. */
    public record TimedNote(long setMs, Note note, boolean background) {
    }

    private static final class Slot {
        final Note[] notes;
        final Note[] hidden;
        final List<Note> background = new ArrayList<>(4);
        final List<LongNote[]> pairs = new ArrayList<>(1);
        int bga = -1;
        int layer = -1;
        Layer[] eventLayer;
        boolean sectionLine;
        long stopUs;

        Slot(int lanes) {
            notes = new Note[lanes];
            hidden = new Note[lanes];
        }

        @Override
        public String toString() {
            return Arrays.toString(notes);
        }
    }
}
