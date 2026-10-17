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
        /** Sets generated recently in this session, newest last; their songs are used less. */
        public List<MixPlan> recentSets = List.of();
        /** Saved sets, read on the worker thread; their songs are used a little less. */
        public java.util.function.Supplier<List<MixPlan>> savedSets = List::of;
    }

    /** Extra cost for a song from a recently generated set, and from a saved set. */
    static final double RECENT_PENALTY = 0.8;
    static final double SAVED_PENALTY = 0.35;

    public enum State { RUNNING, DONE, FAILED, CANCELLED }

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile State state = State.RUNNING;
    private volatile String step = "";
    private volatile String error = "";
    private volatile MixPlan result;
    private final List<String> notes = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.Map<MixTrack, Double> difficulties =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private Scope scope = Scope.EVERYTHING;

    private MixGenerator() {
    }

    public static MixGenerator start(MixPlan base, Request request, MixLibrary library) {
        MixGenerator job = new MixGenerator();
        job.scope = request.scope;
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

    /** What this job was asked to choose; the request object itself is reused by the editor. */
    public Scope scope() {
        return scope;
    }

    /**
     * Whether applying a result onto {@code current} makes a new set instead of
     * rewriting it (#555). A whole new selection is a different set; keeping the
     * current id would overwrite the saved one on the next save.
     */
    public static boolean makesNewSet(MixPlan current, Scope scope) {
        return scope == Scope.EVERYTHING && !current.tracks.isEmpty();
    }

    /** Messages for the player about what could not be satisfied. */
    public List<String> notes() {
        return notes;
    }

    /** Place on the {@link MixDifficulty} axis of each chosen track (for the result list). */
    public double difficulty(MixTrack track) {
        Double axis = difficulties.get(track);
        return axis == null ? Double.NaN : axis;
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
        Difficulties levels = Difficulties.load(library);

        switch (request.scope) {
            case ORDER_AND_JOINS -> {
                for (int i = 0; i < plan.tracks.size(); i++) {
                    MixTrack track = plan.tracks.get(i);
                    MixSequencer.Candidate candidate = candidate(track, library, levels);
                    existing.put(candidate, track);
                    if (track.lockSong) {
                        fixed.add(new MixSequencer.Fixed(i, candidate));
                    } else {
                        pool.add(candidate);
                    }
                }
                settings.targetMs = Long.MAX_VALUE / 4;
                settings.maxTracks = plan.tracks.size();
                settings.minDifficulty = Double.NEGATIVE_INFINITY;
                settings.maxDifficulty = Double.POSITIVE_INFINITY;
                // only reorders what is there, duplicates included
                settings.oneChartPerSong = false;
                settings.genreRule = MixSequencer.GenreRule.ANY;
            }
            case RANGE -> {
                for (int i = 0; i < plan.tracks.size(); i++) {
                    if (i < request.rangeFrom || i > request.rangeTo || plan.tracks.get(i).lockSong) {
                        MixTrack track = plan.tracks.get(i);
                        MixSequencer.Candidate candidate = candidate(track, library, levels);
                        existing.put(candidate, track);
                        fixed.add(new MixSequencer.Fixed(i, candidate));
                    }
                }
                pool.addAll(libraryPool(plan, request, library, levels));
                settings.targetMs = Long.MAX_VALUE / 4;
                settings.maxTracks = plan.tracks.size();
            }
            default -> {
                for (int i = 0; i < plan.tracks.size(); i++) {
                    if (plan.tracks.get(i).lockSong) {
                        MixTrack track = plan.tracks.get(i);
                        MixSequencer.Candidate candidate = candidate(track, library, levels);
                        existing.put(candidate, track);
                        fixed.add(new MixSequencer.Fixed(i, candidate));
                    }
                }
                pool.addAll(libraryPool(plan, request, library, levels));
            }
        }
        MixSequencer.Candidate reference = null;
        if (request.reference != null && request.scope == Scope.EVERYTHING) {
            reference = candidate(request.reference, library, levels);
            existing.put(reference, request.reference.copy());
        }
        if (request.scope == Scope.EVERYTHING || request.scope == Scope.RANGE) {
            List<MixPlan> saved;
            try {
                saved = request.savedSets.get();
            } catch (RuntimeException e) {
                logger.warn("NONSTOP MIX: could not read saved sets", e);
                saved = List.of();
            }
            settings.recentSongs = recentSongs(request.recentSets, saved);
        }
        if (request.scope == Scope.EVERYTHING && pool.size() < 3) {
            notes.add(text("条件に合う曲が少なすぎます", "Very few songs match the conditions"));
        }
        MixSequencer.Selection selection = MixSequencer.select(pool, fixed, reference, settings);
        if (selection.widened()) {
            notes.add(widenedNote(selection, settings.genreFamily));
        }
        List<MixSequencer.Candidate> order = selection.songs();
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
            difficulties.put(track, candidate.difficulty());
            tracks.add(track);
        }
        return tracks;
    }

    /** Song keys of recent and saved sets with their extra cost; a recent set outweighs a saved one. */
    static java.util.Map<String, Double> recentSongs(List<MixPlan> recent, List<MixPlan> saved) {
        java.util.Map<String, Double> penalties = new java.util.HashMap<>();
        if (saved != null) {
            for (MixPlan set : saved) {
                for (MixTrack track : set.tracks) {
                    penalties.merge(MixSequencer.songKey(track.title, track.artist), SAVED_PENALTY, Math::max);
                }
            }
        }
        if (recent != null) {
            for (MixPlan set : recent) {
                for (MixTrack track : set.tracks) {
                    penalties.merge(MixSequencer.songKey(track.title, track.artist), RECENT_PENALTY, Math::max);
                }
            }
        }
        return penalties;
    }

    private static MixSequencer.Candidate candidate(MixTrack track, MixLibrary library, Difficulties levels) {
        SongData song = library.find(track);
        if (song != null) {
            levels.fillInformation(List.of(song));
            return candidate(song, levels.of(song));
        }
        double axis = MixDifficulty.lookup(levels.tables, track.sha256, track.md5);
        if (Double.isNaN(axis)) {
            axis = MixDifficulty.estimate(0, 0, 0, track.level);
        }
        return new MixSequencer.Candidate(track.sha256, track.md5, track.title, track.artist, track.genre, track.bpm,
                true, 0, 0, track.level, axis, track.localPath);
    }

    static MixSequencer.Candidate candidate(SongData song, double difficulty) {
        return new MixSequencer.Candidate(song.getSha256(), song.getMd5(), song.getTitle(), song.getArtist(),
                song.getGenre(), song.getMaxbpm(), song.getMinbpm() == song.getMaxbpm(), song.getLength(),
                song.getNotes(), song.getLevel(), difficulty, song.getPath());
    }

    private static List<MixSequencer.Candidate> libraryPool(MixPlan plan, Request request, MixLibrary library,
            Difficulties levels) {
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        if (library.main() == null || library.main().getSongDatabase() == null) {
            return pool;
        }
        String filter = request.filter == null ? "" : request.filter.trim().toLowerCase(Locale.ROOT);
        List<SongData> songs = new ArrayList<>();
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
            songs.add(song);
        }
        levels.fillInformation(songs);
        for (SongData song : songs) {
            pool.add(candidate(song, levels.of(song)));
        }
        return pool;
    }

    /** Difficulty places from the player's tables, with density estimates for the rest. */
    private static final class Difficulties {
        private static final java.util.regex.Pattern HASH = java.util.regex.Pattern.compile("[0-9a-fA-F]{64}");
        private static final int INFO_CHUNK = 500;

        final java.util.Map<String, Double> tables;
        private final bms.player.beatoraja.song.SongInformationAccessor info;

        private Difficulties(java.util.Map<String, Double> tables, bms.player.beatoraja.song.SongInformationAccessor info) {
            this.tables = tables;
            this.info = info;
        }

        static Difficulties load(MixLibrary library) {
            if (library.main() == null || library.main().getConfig() == null) {
                return new Difficulties(java.util.Map.of(), null);
            }
            bms.player.beatoraja.Config config = library.main().getConfig();
            java.util.Map<String, Double> tables = java.util.Map.of();
            try {
                bms.player.beatoraja.TableData[] read =
                        new bms.player.beatoraja.TableDataAccessor(config.getTablepath()).readAll();
                // the player's table order decides between tables of the same kind
                List<String> order = config.getTableURL() == null ? List.of() : java.util.Arrays.asList(config.getTableURL());
                java.util.Arrays.sort(read, java.util.Comparator.comparingInt(table -> {
                    int index = order.indexOf(table.getUrl());
                    return index < 0 ? Integer.MAX_VALUE : index;
                }));
                tables = MixDifficulty.index(read);
            } catch (RuntimeException e) {
                logger.warn("NONSTOP MIX: could not read difficulty tables", e);
            }
            return new Difficulties(tables, library.main().getInfoDatabase());
        }

        double of(SongData song) {
            double axis = MixDifficulty.lookup(tables, song.getSha256(), song.getMd5());
            if (!Double.isNaN(axis)) {
                return axis;
            }
            double peak = song.getInformation() != null ? song.getInformation().getPeakdensity() : 0;
            return MixDifficulty.estimate(song.getNotes(), song.getLength(), peak, song.getLevel());
        }

        /** Loads peak densities in a few queries for songs that are on no table. */
        void fillInformation(List<SongData> songs) {
            if (info == null) {
                return;
            }
            java.util.Map<String, List<SongData>> missing = new java.util.HashMap<>();
            for (SongData song : songs) {
                String sha256 = song.getSha256();
                if (song.getInformation() == null && sha256 != null && HASH.matcher(sha256).matches()
                        && Double.isNaN(MixDifficulty.lookup(tables, sha256, song.getMd5()))) {
                    missing.computeIfAbsent(sha256.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(song);
                }
            }
            List<String> hashes = new ArrayList<>(missing.keySet());
            for (int from = 0; from < hashes.size(); from += INFO_CHUNK) {
                StringBuilder where = new StringBuilder("sha256 IN (");
                for (int i = from; i < Math.min(hashes.size(), from + INFO_CHUNK); i++) {
                    where.append(i == from ? "'" : ",'").append(hashes.get(i)).append('\'');
                }
                where.append(')');
                try {
                    for (bms.player.beatoraja.song.SongInformation found : info.getInformations(where.toString())) {
                        List<SongData> owners = found.getSha256() == null ? null
                                : missing.get(found.getSha256().toLowerCase(Locale.ROOT));
                        if (owners != null) {
                            for (SongData owner : owners) {
                                owner.setInformation(found);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    logger.warn("NONSTOP MIX: could not read song information", e);
                    return;
                }
            }
        }
    }

    /**
     * Songs of the player's library per widening step of "same genre only",
     * for the generator screen (#565): {exact, sharing a word, same family}.
     * Reads the song database, so call it off the render thread.
     */
    public static int[] genreCounts(MixLibrary library, int modeId, String genre, MixGenreFamily family) {
        if (library.main() == null || library.main().getSongDatabase() == null) {
            return new int[3];
        }
        List<MixSequencer.Candidate> pool = new ArrayList<>();
        for (SongData song : library.main().getSongDatabase().getSongDatasByMode(modeId, POOL_LIMIT)) {
            if (song.getPath() != null && song.getNotes() > 0) {
                pool.add(candidate(song, 0));
            }
        }
        return genreCounts(pool, genre, family);
    }

    /** Distinct songs (one per song, not per chart) matching each step. */
    static int[] genreCounts(List<MixSequencer.Candidate> pool, String genre, MixGenreFamily family) {
        MixSequencer.GenreReach[] steps = {MixSequencer.GenreReach.EXACT, MixSequencer.GenreReach.WORD,
                MixSequencer.GenreReach.FAMILY};
        int[] counts = new int[steps.length];
        for (int i = 0; i < steps.length; i++) {
            java.util.Set<String> songs = new java.util.HashSet<>();
            for (MixSequencer.Candidate candidate : pool) {
                if (candidate.lengthMs() > 0 && candidate.lengthMs() < 30_000L) {
                    continue;
                }
                if (MixSequencer.genreMatches(candidate.genre(), genre, family, steps[i])) {
                    songs.add(candidate.songKey());
                }
            }
            counts[i] = songs.size();
        }
        return counts;
    }

    /** Says that "same genre only" had too few songs and which genres were used instead (#565). */
    static String widenedNote(MixSequencer.Selection selection, MixGenreFamily family) {
        String genre = family != null
                ? family.label(!bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n.isEnglish())
                : selection.genre();
        String wider = switch (selection.reach()) {
            case WORD -> text("名前の一部が同じジャンル", "genres sharing a word");
            case FAMILY -> text("同じ系統のジャンル", "genres of the same family");
            default -> text("近いジャンルを優先してほかのジャンル", "other genres, closest first");
        };
        return String.format(Locale.ROOT, text("ジャンル「%s」の曲が%d曲しかないため、%sも使いました",
                "Only %2$d songs have the genre \"%1$s\", so %3$s were used too"), genre, selection.exactMatches(), wider);
    }

    private static String text(String japanese, String english) {
        return bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n.text(japanese, english);
    }
}
