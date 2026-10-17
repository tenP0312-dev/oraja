package bms.player.beatoraja.nonstop;

import bms.player.beatoraja.song.SongData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Builds a set without any hand-registered join data (Issue #535 §7A):
 * picks songs from the library, orders them, chooses the used parts and the
 * joins, then checks the result with the real compiler. Locked songs, ranges
 * and joins are kept as they are.
 */
public final class MixGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MixGenerator.class);
    private static final int POOL_LIMIT = 30_000;

    public enum Scope {
        /** Choose songs, order and joins (locked items stay). */
        EVERYTHING,
        /** Keep the chosen songs; choose their order and the joins. */
        ORDER_AND_JOINS,
        /** Keep songs and order; choose used parts and joins. */
        JOINS_ONLY,
        /** Replace only the songs in [from, to]; everything else stays. */
        RANGE
    }

    public static final class Request {
        public Scope scope = Scope.EVERYTHING;
        public MixSequencer.Settings sequence = new MixSequencer.Settings();
        public MixJoinPlanner.Settings joins = new MixJoinPlanner.Settings();
        /** Only songs whose title/artist/genre contains this text (empty: all). */
        public String filter = "";
        /** Optional song the set starts from. */
        public MixTrack reference;
        public int rangeFrom;
        public int rangeTo;
    }

    public enum State { RUNNING, DONE, FAILED, CANCELLED }

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile State state = State.RUNNING;
    private volatile String step = "";
    private volatile String error = "";
    private volatile MixPlan result;
    private final List<String> notes = new java.util.concurrent.CopyOnWriteArrayList<>();

    private MixGenerator() {
    }

    public static MixGenerator start(MixPlan base, Request request, MixLibrary library) {
        MixGenerator job = new MixGenerator();
        MixPlan snapshot = base.copy();
        Thread worker = new Thread(() -> job.run(snapshot, request, library), "nonstop-mix-generate");
        worker.setDaemon(true);
        worker.start();
        return job;
    }

    public State state() {
        return state;
    }

    public String step() {
        return step;
    }

    public String error() {
        return error;
    }

    public MixPlan result() {
        return result;
    }

    /** Messages for the player about what could not be satisfied. */
    public List<String> notes() {
        return notes;
    }

    public void cancel() {
        cancelled.set(true);
    }

    private void run(MixPlan plan, Request request, MixLibrary library) {
        try {
            if (request.scope == Scope.EVERYTHING || request.scope == Scope.RANGE || request.scope == Scope.ORDER_AND_JOINS) {
                step = text("曲を選んでいます", "Choosing songs");
                List<MixTrack> chosen = chooseSongs(plan, request, library);
                if (cancelled.get()) {
                    state = State.CANCELLED;
                    return;
                }
                if (chosen.isEmpty()) {
                    error = text("条件に合う曲が見つかりませんでした。条件をゆるめてください",
                            "No songs match the conditions; try relaxing them");
                    state = State.FAILED;
                    return;
                }
                List<MixTransition> oldJoins = new ArrayList<>(plan.transitions);
                List<MixTrack> oldTracks = new ArrayList<>(plan.tracks);
                plan.tracks.clear();
                plan.transitions.clear();
                for (MixTrack track : chosen) {
                    plan.tracks.add(track);
                }
                plan.normalizeTransitions();
                // a locked join survives when both of its songs are still neighbours
                for (int j = 0; j < oldJoins.size(); j++) {
                    if (!oldJoins.get(j).locked) {
                        continue;
                    }
                    int a = plan.tracks.indexOf(oldTracks.get(j));
                    if (a >= 0 && a + 1 < plan.tracks.size() && plan.tracks.get(a + 1) == oldTracks.get(j + 1)) {
                        plan.transitions.set(a, oldJoins.get(j));
                    }
                }
            }
            step = text("譜面を読み込んでいます", "Loading charts");
            List<MixTrackAnalysis> analyses = new ArrayList<>();
            List<MixLibrary.Loaded> loaded = new ArrayList<>();
            for (int i = 0; i < plan.tracks.size(); i++) {
                if (cancelled.get()) {
                    state = State.CANCELLED;
                    return;
                }
                MixLibrary.Loaded song;
                try {
                    song = library.load(plan.tracks.get(i));
                } catch (RuntimeException e) {
                    if (plan.tracks.get(i).lockSong || request.scope == Scope.JOINS_ONLY) {
                        throw e;
                    }
                    notes.add(text("読み込めない曲を外しました: ", "Removed a song that could not be loaded: ")
                            + plan.tracks.get(i).displayTitle());
                    plan.removeTrack(i);
                    i--;
                    continue;
                }
                if (song.model().getMode() != plan.mode) {
                    notes.add(text("鍵盤モードが違う曲を外しました: ", "Removed a song of another key mode: ")
                            + plan.tracks.get(i).displayTitle());
                    plan.removeTrack(i);
                    i--;
                    continue;
                }
                loaded.add(song);
                analyses.add(song.analysis());
            }
            if (plan.tracks.isEmpty()) {
                error = text("使える曲がありません", "No usable songs");
                state = State.FAILED;
                return;
            }
            step = text("つなぎを決めています", "Planning the joins");
            MixJoinPlanner.plan(plan, analyses, request.joins);

            // check with the real compiler; joins that still cut a long note fall back to a safe cut
            List<MixCompiler.Source> sources = new ArrayList<>();
            for (MixLibrary.Loaded song : loaded) {
                sources.add(new MixCompiler.Source(song.model(), song.chart(), 1.0));
            }
            CompiledMix compiled = MixCompiler.compile(plan, sources);
            for (int pass = 0; pass < 2 && compiled.hasErrors(); pass++) {
                for (MixCompiler.Issue issue : compiled.issues()) {
                    if (issue.severity() != MixCompiler.Severity.ERROR || issue.track() < 0) {
                        continue;
                    }
                    int join = issue.join() >= 0 ? issue.join() : nearestJoin(compiled, issue);
                    if (join >= 0 && !plan.transitions.get(join).locked) {
                        MixJoinPlanner.Settings fallback = request.joins.copy();
                        fallback.allowBlend = false;
                        fallback.allowFade = pass == 0 && fallback.allowFade;
                        fallback.allowCut = true;
                        MixJoinPlanner.planJoin(plan, join, analyses.get(join), analyses.get(join + 1), fallback);
                    }
                }
                compiled = MixCompiler.compile(plan, sources);
            }
            if (compiled.hasErrors()) {
                notes.add(text("自動で解決できない問題が残っています。一覧を確認してください",
                        "Some problems could not be solved automatically; check the list"));
            }
            long total = compiled.layout().totalMs();
            if (request.scope == Scope.EVERYTHING && total < request.sequence.targetMs * 0.8) {
                notes.add(String.format(Locale.ROOT, text("目標の長さに届きません（%d分%02d秒）",
                        "Shorter than the target (%d:%02d)"), total / 60_000, total / 1000 % 60));
            }
            if (plan.origin.equals("manual") || plan.origin.isEmpty()) {
                plan.origin = "auto:v1";
            }
            result = plan;
            state = State.DONE;
        } catch (Throwable e) {
            logger.warn("NONSTOP MIX generation failed", e);
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            state = State.FAILED;
        }
    }

    private static int nearestJoin(CompiledMix compiled, MixCompiler.Issue issue) {
        int track = issue.track();
        int joins = compiled.plan().transitions.size();
        if (joins == 0) {
            return -1;
        }
        long[] handoff = compiled.layout().handoff();
        long time = issue.setTimeMs();
        if (track > 0 && track - 1 < joins && (track >= joins || Math.abs(handoff[track - 1] - time) <= Math.abs(handoff[track] - time))) {
            return track - 1;
        }
        return Math.min(track, joins - 1);
    }

    private List<MixTrack> chooseSongs(MixPlan plan, Request request, MixLibrary library) {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        List<MixSequencer.Fixed> fixed = new ArrayList<>();
        MixSequencer.Settings settings = request.sequence.copy();
        settings.segmentMs = request.joins.style.segmentMs;
        settings.joinMs = request.joins.allowBlend ? 6000L : 0L;
        java.util.Map<MixSequencer.Candidate, MixTrack> existing = new java.util.IdentityHashMap<>();

        switch (request.scope) {
            case ORDER_AND_JOINS -> {
                for (int i = 0; i < plan.tracks.size(); i++) {
                    MixTrack track = plan.tracks.get(i);
                    MixSequencer.Candidate candidate = candidate(track, library);
                    existing.put(candidate, track);
                    if (track.lockSong) {
                        fixed.add(new MixSequencer.Fixed(i, candidate));
                    } else {
                        pool.add(candidate);
                    }
                }
                settings.targetMs = Long.MAX_VALUE / 4;
                settings.maxTracks = plan.tracks.size();
                settings.minLevel = 0;
                settings.maxLevel = Integer.MAX_VALUE;
                settings.genreRule = MixSequencer.GenreRule.ANY;
            }
            case RANGE -> {
                for (int i = 0; i < plan.tracks.size(); i++) {
                    if (i < request.rangeFrom || i > request.rangeTo || plan.tracks.get(i).lockSong) {
                        MixTrack track = plan.tracks.get(i);
                        MixSequencer.Candidate candidate = candidate(track, library);
                        existing.put(candidate, track);
                        fixed.add(new MixSequencer.Fixed(i, candidate));
                    }
                }
                pool.addAll(libraryPool(plan, request, library));
                settings.targetMs = Long.MAX_VALUE / 4;
                settings.maxTracks = plan.tracks.size();
            }
            default -> {
                for (int i = 0; i < plan.tracks.size(); i++) {
                    if (plan.tracks.get(i).lockSong) {
                        MixTrack track = plan.tracks.get(i);
                        MixSequencer.Candidate candidate = candidate(track, library);
                        existing.put(candidate, track);
                        fixed.add(new MixSequencer.Fixed(i, candidate));
                    }
                }
                pool.addAll(libraryPool(plan, request, library));
            }
        }
        MixSequencer.Candidate reference = null;
        if (request.reference != null && request.scope == Scope.EVERYTHING) {
            reference = candidate(request.reference, library);
            existing.put(reference, request.reference.copy());
        }
        if (request.scope == Scope.EVERYTHING && pool.size() < 3) {
            notes.add(text("条件に合う曲が少なすぎます", "Very few songs match the conditions"));
        }
        List<MixSequencer.Candidate> order = MixSequencer.sequence(pool, fixed, reference, settings);
        List<MixTrack> tracks = new ArrayList<>();
        for (MixSequencer.Candidate candidate : order) {
            MixTrack track = existing.get(candidate);
            if (track == null) {
                track = new MixTrack();
                track.sha256 = candidate.sha256();
                track.md5 = candidate.md5();
                track.title = candidate.title();
                track.artist = candidate.artist();
                track.genre = candidate.genre();
                track.bpm = candidate.bpm();
                track.level = candidate.level();
                track.localPath = candidate.path();
            }
            tracks.add(track);
        }
        return tracks;
    }

    private static MixSequencer.Candidate candidate(MixTrack track, MixLibrary library) {
        SongData song = library.find(track);
        if (song != null) {
            return candidate(song);
        }
        return new MixSequencer.Candidate(track.sha256, track.md5, track.title, track.artist, track.genre, track.bpm,
                true, 0, 0, track.level, track.localPath);
    }

    static MixSequencer.Candidate candidate(SongData song) {
        return new MixSequencer.Candidate(song.getSha256(), song.getMd5(), song.getTitle(), song.getArtist(),
                song.getGenre(), song.getMaxbpm(), song.getMinbpm() == song.getMaxbpm(), song.getLength(),
                song.getNotes(), song.getLevel(), song.getPath());
    }

    private static List<MixSequencer.Candidate> libraryPool(MixPlan plan, Request request, MixLibrary library) {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        if (library.main() == null || library.main().getSongDatabase() == null) {
            return pool;
        }
        String filter = request.filter == null ? "" : request.filter.trim().toLowerCase(Locale.ROOT);
        for (SongData song : library.main().getSongDatabase().getSongDatasByMode(plan.mode.id, POOL_LIMIT)) {
            if (song.getPath() == null || song.getNotes() <= 0) {
                continue;
            }
            if (!filter.isEmpty()) {
                String text = (song.getTitle() + " " + song.getSubtitle() + " " + song.getArtist() + " " + song.getGenre())
                        .toLowerCase(Locale.ROOT);
                if (!text.contains(filter)) {
                    continue;
                }
            }
            pool.add(candidate(song));
        }
        return pool;
    }

    private static String text(String japanese, String english) {
        return bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n.text(japanese, english);
    }
}
