package bms.player.beatoraja.nonstop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Picks and orders songs for a set (Issue #535 §4). A beam search keeps
 * several partial sets alive, so a song is not chosen only because it fits
 * the previous one: tempo, genre, the difficulty curve, artist variety and
 * the target length are scored over the whole set.
 */
public final class MixSequencer {

    /**
     * A library song that may be used. {@code difficulty} is the place on the
     * {@link MixDifficulty} axis (table first, then density).
     */
    public record Candidate(String sha256, String md5, String title, String artist, String genre, double bpm,
            boolean steadyTempo, long lengthMs, int notes, int level, double difficulty, String path) {

        /** Title and artist without chart decorations, used to avoid the same song twice. */
        public String songKey() {
            return MixSequencer.songKey(title, artist);
        }

        /** Folder of the chart file; charts of one song share it. Empty when unknown. */
        public String folderKey() {
            if (path == null || path.isBlank()) {
                return "";
            }
            String normalized = path.replace('\\', '/');
            int slash = normalized.lastIndexOf('/');
            return slash <= 0 ? "" : normalized.substring(0, slash).toLowerCase(Locale.ROOT);
        }

        /** Keys that mark the same song: the title key, and the folder when known. */
        List<String> sameSongKeys() {
            String folder = folderKey();
            return folder.isEmpty() ? List.of("t:" + songKey()) : List.of("t:" + songKey(), "f:" + folder);
        }
    }

    /** Title and artist without chart decorations. */
    public static String songKey(String title, String artist) {
        return normalizeTitle(title) + "|" + normalizeArtist(artist);
    }

    static String normalizeTitle(String title) {
        String raw = title == null ? "" : java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        String key = raw;
        // [ANOTHER], (7keys), 【SP】, <HARD>, -INSANE-, ~LN~ and the like name the chart, not the song
        key = key.replaceAll("[\\[\\(【<「『].*?[\\]\\)】>」』]", " ");
        key = key.replaceAll("\\s+-[^-]+-\\s*$", " ");
        key = key.replaceAll("\\s+[~〜][^~〜]+[~〜]\\s*$", " ");
        key = key.replaceAll("[\\s\\p{Punct}・]+", " ").trim();
        // a title that is only decoration keeps it
        return key.isEmpty() ? raw.replaceAll("\\s+", " ").trim() : key;
    }

    static String normalizeArtist(String artist) {
        String key = artist == null ? "" : java.text.Normalizer.normalize(artist, java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        // "composer / obj:charter" differs between charts of one song
        key = key.replaceAll("\\s*(/|obj\\s*[.:]|譜面).*$", "");
        key = key.replaceAll("[\\[\\(【<].*?[\\]\\)】>]", " ");
        return key.replaceAll("[\\s\\p{Punct}]+", " ").trim();
    }

    public enum GenreRule {
        /** Only songs whose genre matches the reference. */
        STRICT,
        /** Prefer close genres but allow others. */
        SIMILAR,
        /** Genre does not matter. */
        ANY
    }

    /**
     * How far "same genre only" reaches (#565). When too few songs match, the
     * generator widens it step by step instead of making a one-song set.
     */
    public enum GenreReach {
        /** The same genre name (or the chosen family). */
        EXACT,
        /** Genres sharing a word ("Brutal Hardcore" and "Hardcore"). */
        WORD,
        /** The genre's broad family ({@link MixGenreFamily}). */
        FAMILY,
        /** Every genre; close genres are still preferred by the cost. */
        ALL
    }

    /**
     * @param reach        how far the genre rule had to reach
     * @param genre        the genre name the set follows (empty with a family or none)
     * @param exactMatches songs matching at the first step (-1 when the rule does not filter)
     */
    public record Selection(List<Candidate> songs, GenreReach reach, String genre, int exactMatches) {
        /** True when the set had to use genres beyond the requested one. */
        public boolean widened() {
            return exactMatches >= 0 && reach != GenreReach.EXACT;
        }
    }

    public enum Curve {
        FLAT, RISING, FALLING, WAVE, PEAK_END;

        /** Target position 0..1 inside the difficulty range at {@code x} (0..1 of the set). */
        public double at(double x) {
            double t = Math.max(0, Math.min(1, x));
            return switch (this) {
                case FLAT -> 0.5;
                case RISING -> t;
                case FALLING -> 1 - t;
                case WAVE -> 0.5 - 0.5 * Math.cos(t * Math.PI * 4);
                case PEAK_END -> t < 0.75 ? t * 0.8 : 0.6 + (t - 0.75) * 1.6;
            };
        }
    }

    public static final class Settings {
        public long targetMs = 15 * 60_000L;
        public GenreRule genreRule = GenreRule.SIMILAR;
        /** Genre the set follows; empty takes the first song's genre. */
        public String genre = "";
        /** Range on the {@link MixDifficulty} axis. */
        public double minDifficulty = Double.NEGATIVE_INFINITY;
        public double maxDifficulty = Double.POSITIVE_INFINITY;
        /** At most one chart per song (same folder, or same title and artist). */
        public boolean oneChartPerSong = true;
        public Curve curve = Curve.RISING;
        /** Allowed tempo difference between neighbours before it costs (ratio). */
        public double tempoTolerance = 0.04;
        /** Treat half and double tempo as compatible. */
        public boolean halfDouble = true;
        public long seed = System.nanoTime();
        /** Broad genre family the set follows instead of {@link #genre} (null: none). */
        public MixGenreFamily genreFamily;
        /** Extra cost per song key (see {@link Candidate#songKey()}) for songs of recent sets. */
        public java.util.Map<String, Double> recentSongs = java.util.Map.of();
        /** Random cost added per candidate so runs differ. */
        public double jitter = 0.9;
        public int beamWidth = 24;
        public int maxTracks = 40;
        /** How much of each song is used on average (for the length estimate). */
        public long segmentMs;
        /** Estimated overlap per join. */
        public long joinMs = 8000L;

        public Settings copy() {
            Settings copy = new Settings();
            copy.targetMs = targetMs;
            copy.genreRule = genreRule;
            copy.genre = genre;
            copy.minDifficulty = minDifficulty;
            copy.maxDifficulty = maxDifficulty;
            copy.oneChartPerSong = oneChartPerSong;
            copy.curve = curve;
            copy.tempoTolerance = tempoTolerance;
            copy.halfDouble = halfDouble;
            copy.seed = seed;
            copy.genreFamily = genreFamily;
            copy.recentSongs = recentSongs;
            copy.jitter = jitter;
            copy.beamWidth = beamWidth;
            copy.maxTracks = maxTracks;
            copy.segmentMs = segmentMs;
            copy.joinMs = joinMs;
            return copy;
        }
    }

    /** A song that must appear at a fixed position. */
    public record Fixed(int position, Candidate candidate) {
    }

    private record State(List<Candidate> songs, Set<String> used, long totalMs, double cost) {
    }

    private MixSequencer() {
    }

    /**
     * @param pool  songs that may be used
     * @param fixed songs that must stay at their positions (may be empty)
     * @param first optional reference song that opens the set (null for none)
     * @return the chosen songs in order; empty when nothing fits
     */
    public static List<Candidate> sequence(List<Candidate> pool, List<Fixed> fixed, Candidate first, Settings settings) {
        return select(pool, fixed, first, settings).songs();
    }

    /** Like {@link #sequence}, and also reports how far the genre rule had to reach. */
    public static Selection select(List<Candidate> pool, List<Fixed> fixed, Candidate first, Settings settings) {
        String referenceGenre = referenceGenre(settings, fixed, first);
        if (!filtersGenre(settings, referenceGenre)) {
            return new Selection(run(pool, fixed, first, settings, referenceGenre, GenreReach.ALL),
                    GenreReach.ALL, referenceGenre, -1);
        }
        int needed = neededSongs(settings, fixed.size() + (first != null ? 1 : 0));
        int exact = -1;
        for (GenreReach reach : reaches(settings, referenceGenre)) {
            int count = eligible(pool, fixed, first, settings, referenceGenre, reach).usable().size();
            if (exact < 0) {
                exact = count;
            }
            if (count >= needed || reach == GenreReach.ALL) {
                return new Selection(run(pool, fixed, first, settings, referenceGenre, reach), reach, referenceGenre, exact);
            }
        }
        throw new IllegalStateException("the last reach takes every genre");
    }

    static String referenceGenre(Settings settings, List<Fixed> fixed, Candidate first) {
        return settings.genre == null || settings.genre.isBlank()
                ? (first != null ? first.genre() : fixed.isEmpty() ? "" : fixed.get(0).candidate().genre())
                : settings.genre;
    }

    private static boolean filtersGenre(Settings settings, String referenceGenre) {
        return settings.genreRule == GenreRule.STRICT
                && (settings.genreFamily != null || !normalizeGenre(referenceGenre).isEmpty());
    }

    /** The widening steps for the rule: a chosen family can only widen to every genre. */
    static List<GenreReach> reaches(Settings settings, String referenceGenre) {
        if (settings.genreFamily != null) {
            return List.of(GenreReach.EXACT, GenreReach.ALL);
        }
        List<GenreReach> reaches = new ArrayList<>(List.of(GenreReach.EXACT, GenreReach.WORD));
        if (!MixGenreFamily.of(referenceGenre).isEmpty()) {
            reaches.add(GenreReach.FAMILY);
        }
        reaches.add(GenreReach.ALL);
        return reaches;
    }

    /** Songs the set still needs besides the fixed ones, from the target length and the song limit. */
    static int neededSongs(Settings settings, int fixedSongs) {
        int room = Math.max(1, settings.maxTracks - fixedSongs);
        long perSong = Math.max(30_000L, (settings.segmentMs > 0 ? settings.segmentMs : 120_000L) - settings.joinMs);
        long target = Math.min(settings.targetMs, Long.MAX_VALUE / 8);
        long songs = (target + perSong - 1) / perSong;
        return (int) Math.min(room, Math.max(2L, songs));
    }

    /** Whether a #GENRE passes "same genre only" at the given reach. */
    static boolean genreMatches(String genre, String referenceGenre, MixGenreFamily family, GenreReach reach) {
        if (reach == GenreReach.ALL) {
            return true;
        }
        if (family != null) {
            return family.matches(genre);
        }
        String normalized = normalizeGenre(genre);
        if (normalized.isEmpty()) {
            return false;
        }
        boolean same = normalized.equals(normalizeGenre(referenceGenre));
        return switch (reach) {
            case EXACT -> same;
            case WORD -> same || genreCost(referenceGenre, genre) < 1.0;
            case FAMILY -> same || genreCost(referenceGenre, genre) < 1.0
                    || MixGenreFamily.of(referenceGenre).stream().anyMatch(f -> f.matches(genre));
            case ALL -> true;
        };
    }

    private record Eligible(List<Candidate> usable, List<List<String>> keys) {
    }

    private static Eligible eligible(List<Candidate> pool, List<Fixed> fixed, Candidate first, Settings settings,
            String referenceGenre, GenreReach reach) {
        boolean filter = filtersGenre(settings, referenceGenre);
        // songs the set already has to contain: no other chart of them may be picked
        Set<String> reserved = new HashSet<>();
        if (settings.oneChartPerSong) {
            for (Fixed item : fixed) {
                reserved.addAll(item.candidate().sameSongKeys());
            }
            if (first != null) {
                reserved.addAll(first.sameSongKeys());
            }
        }
        List<Candidate> usable = new ArrayList<>();
        List<List<String>> usableKeys = new ArrayList<>();
        for (Candidate candidate : pool) {
            if (candidate.difficulty() < settings.minDifficulty || candidate.difficulty() > settings.maxDifficulty) {
                continue;
            }
            if (candidate.lengthMs() > 0 && candidate.lengthMs() < 30_000L) {
                continue;
            }
            if (filter && !genreMatches(candidate.genre(), referenceGenre, settings.genreFamily, reach)) {
                continue;
            }
            List<String> keys = settings.oneChartPerSong ? candidate.sameSongKeys() : List.of("#" + usable.size());
            if (overlaps(reserved, keys)) {
                continue;
            }
            usable.add(candidate);
            usableKeys.add(keys);
        }
        return new Eligible(usable, usableKeys);
    }

    private static List<Candidate> run(List<Candidate> pool, List<Fixed> fixed, Candidate first, Settings settings,
            String referenceGenre, GenreReach reach) {
        Random random = new Random(settings.seed);
        Eligible eligible = eligible(pool, fixed, first, settings, referenceGenre, reach);
        List<Candidate> usable = eligible.usable();
        List<List<String>> usableKeys = eligible.keys();
        double[] levels = {
                Math.max(settings.minDifficulty, minDifficulty(usable)),
                Math.min(settings.maxDifficulty, maxDifficulty(usable))};
        double[] jitter = new double[usable.size()];
        for (int i = 0; i < jitter.length; i++) {
            jitter[i] = random.nextDouble() * Math.max(0, settings.jitter);
        }
        Candidate opener = first != null || fixedAt(fixed, 0) != null ? null
                : pickOpener(usable, jitter, referenceGenre, settings, levels, random);

        List<State> beam = new ArrayList<>();
        beam.add(new State(List.of(), Set.of(), 0, 0));
        List<State> finished = new ArrayList<>();
        int maxSteps = Math.max(1, settings.maxTracks);
        for (int step = 0; step < maxSteps && !beam.isEmpty(); step++) {
            List<State> next = new ArrayList<>();
            for (State state : beam) {
                Candidate forced = fixedAt(fixed, step);
                if (step == 0 && forced == null) {
                    forced = first != null ? first : opener;
                }
                int before = next.size();
                if (forced != null) {
                    // a locked song always keeps its place, even next to another chart of itself
                    List<String> keys = forced == opener ? usableKeys.get(usable.indexOf(opener)) : List.of();
                    next.add(extend(state, forced, keys, cost(state, forced, step, referenceGenre, settings, levels),
                            settings));
                    continue;
                }
                for (int c = 0; c < usable.size(); c++) {
                    Candidate candidate = usable.get(c);
                    if (overlaps(state.used(), usableKeys.get(c))) {
                        continue;
                    }
                    double cost = cost(state, candidate, step, referenceGenre, settings, levels) + jitter[c];
                    next.add(extend(state, candidate, usableKeys.get(c), cost, settings));
                }
                if (next.size() == before && remainingFixed(fixed, state.songs().size()) == 0) {
                    // nothing left to add: the set ends here, shorter than the target
                    finished.add(withLengthCost(state, settings));
                }
            }
            next.sort(Comparator.comparingDouble(State::cost));
            beam = new ArrayList<>();
            for (State state : next) {
                boolean done = state.totalMs() >= settings.targetMs * 0.97 && remainingFixed(fixed, state.songs().size()) == 0;
                if (done) {
                    finished.add(withLengthCost(state, settings));
                } else if (beam.size() < settings.beamWidth) {
                    beam.add(state);
                }
                if (beam.size() >= settings.beamWidth && finished.size() >= settings.beamWidth) {
                    break;
                }
            }
        }
        for (State state : beam) {
            finished.add(withLengthCost(state, settings));
        }
        return pickFinished(finished, random);
    }

    /** Cost margin inside which openers and finished sets count as equally good. */
    private static final double OPENER_SLACK = 1.0;
    private static final double FINISH_SLACK = 0.6;

    /** A random opener among the ones that fit about as well as the best (Issue #563). */
    private static Candidate pickOpener(List<Candidate> usable, double[] jitter, String referenceGenre, Settings settings,
            double[] levels, Random random) {
        if (usable.isEmpty()) {
            return null;
        }
        State empty = new State(List.of(), Set.of(), 0, 0);
        double[] costs = new double[usable.size()];
        double best = Double.POSITIVE_INFINITY;
        for (int c = 0; c < usable.size(); c++) {
            costs[c] = cost(empty, usable.get(c), 0, referenceGenre, settings, levels) + jitter[c];
            best = Math.min(best, costs[c]);
        }
        List<Candidate> good = new ArrayList<>();
        for (int c = 0; c < usable.size(); c++) {
            if (costs[c] <= best + OPENER_SLACK) {
                good.add(usable.get(c));
            }
        }
        return good.get(random.nextInt(good.size()));
    }

    /** A random one of the finished sets close to the best, so equal-looking sets take turns. */
    private static List<Candidate> pickFinished(List<State> finished, Random random) {
        if (finished.isEmpty()) {
            return List.of();
        }
        finished.sort(Comparator.comparingDouble(State::cost));
        double best = finished.get(0).cost();
        int close = 0;
        while (close < finished.size() && finished.get(close).cost() <= best + FINISH_SLACK) {
            close++;
        }
        return finished.get(random.nextInt(Math.max(1, close))).songs();
    }

    private static boolean overlaps(Set<String> used, List<String> keys) {
        for (String key : keys) {
            if (used.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static State withLengthCost(State state, Settings settings) {
        double miss = Math.abs(state.totalMs() - settings.targetMs) / (double) Math.max(60_000L, settings.targetMs);
        return new State(state.songs(), state.used(), state.totalMs(), state.cost() + miss * 6.0);
    }

    private static State extend(State state, Candidate candidate, List<String> keys, double cost, Settings settings) {
        List<Candidate> songs = new ArrayList<>(state.songs());
        songs.add(candidate);
        Set<String> used = state.used();
        if (!keys.isEmpty()) {
            used = new HashSet<>(used);
            used.addAll(keys);
        }
        long length = candidate.lengthMs() > 0 ? candidate.lengthMs() : 120_000L;
        if (settings.segmentMs > 0) {
            length = Math.min(length, settings.segmentMs);
        }
        long total = state.totalMs() + length - (state.songs().isEmpty() ? 0 : settings.joinMs);
        return new State(songs, used, total, state.cost() + cost);
    }

    static double cost(State state, Candidate candidate, int step, String referenceGenre, Settings settings,
            double[] levels) {
        double cost = 0;
        Candidate previous = state.songs().isEmpty() ? null : state.songs().get(state.songs().size() - 1);
        if (previous != null) {
            cost += tempoCost(previous.bpm(), candidate.bpm(), settings) * 3.0;
            if (!candidate.steadyTempo()) {
                cost += 0.4;
            }
            if (sameArtist(previous, candidate)) {
                cost += 1.5;
            }
            int from = Math.max(0, state.songs().size() - 4);
            for (int i = from; i < state.songs().size() - 1; i++) {
                if (sameArtist(state.songs().get(i), candidate)) {
                    cost += 0.4;
                }
            }
            cost += genreCost(previous.genre(), candidate.genre()) * (settings.genreRule == GenreRule.ANY ? 0.3 : 1.2);
        }
        if (settings.genreFamily != null && settings.genreRule != GenreRule.ANY) {
            cost += settings.genreFamily.matches(candidate.genre()) ? 0 : 1.5;
        } else if (!referenceGenre.isBlank() && settings.genreRule != GenreRule.ANY) {
            cost += genreCost(referenceGenre, candidate.genre()) * 1.5;
        }
        // songs of recently generated or saved sets give way to others
        Double recent = settings.recentSongs.get(candidate.songKey());
        if (recent != null) {
            cost += recent;
        }
        // difficulty follows the requested curve over the expected length
        double progress = Math.min(1.0, state.totalMs() / (double) Math.max(1L, settings.targetMs));
        double low = levels[0];
        double high = levels[1];
        if (high > low) {
            double target = low + (high - low) * settings.curve.at(progress);
            cost += Math.abs(candidate.difficulty() - target) / Math.max(1.0, (high - low) / 3.0);
        }
        return cost;
    }

    static double tempoCost(double a, double b, Settings settings) {
        if (a <= 0 || b <= 0) {
            return 1.0;
        }
        double distance = Math.abs(Math.log(b / a));
        if (settings.halfDouble) {
            double ln2 = Math.log(2);
            distance = Math.min(distance, Math.abs(Math.abs(Math.log(b / a)) - ln2) + 0.02);
        }
        double tolerance = Math.log(1 + Math.max(0.001, settings.tempoTolerance));
        double ratio = distance / tolerance;
        return ratio <= 1 ? ratio * 0.2 : Math.min(4.0, 0.2 + (ratio - 1) * 0.35);
    }

    /** 0 for the same genre, up to 1 for unrelated genres (by shared words). */
    static double genreCost(String a, String b) {
        String x = normalizeGenre(a);
        String y = normalizeGenre(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0.5;
        }
        if (x.equals(y)) {
            return 0;
        }
        Set<String> wordsA = new HashSet<>(List.of(x.split(" ")));
        Set<String> wordsB = new HashSet<>(List.of(y.split(" ")));
        Set<String> shared = new HashSet<>(wordsA);
        shared.retainAll(wordsB);
        if (!shared.isEmpty()) {
            return 0.35 - 0.2 * shared.size() / (double) Math.max(wordsA.size(), wordsB.size());
        }
        if (x.contains(y) || y.contains(x)) {
            return 0.3;
        }
        return 1.0;
    }

    public static String normalizeGenre(String genre) {
        if (genre == null) {
            return "";
        }
        String normalized = java.text.Normalizer.normalize(genre, java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private static boolean sameArtist(Candidate a, Candidate b) {
        return a.artist() != null && !a.artist().isBlank() && a.artist().equalsIgnoreCase(b.artist());
    }

    private static Candidate fixedAt(List<Fixed> fixed, int position) {
        for (Fixed item : fixed) {
            if (item.position() == position) {
                return item.candidate();
            }
        }
        return null;
    }

    private static int remainingFixed(List<Fixed> fixed, int placed) {
        int remaining = 0;
        for (Fixed item : fixed) {
            if (item.position() >= placed) {
                remaining++;
            }
        }
        return remaining;
    }

    private static double minDifficulty(List<Candidate> pool) {
        double level = Double.POSITIVE_INFINITY;
        for (Candidate candidate : pool) {
            level = Math.min(level, candidate.difficulty());
        }
        return level == Double.POSITIVE_INFINITY ? 0 : level;
    }

    private static double maxDifficulty(List<Candidate> pool) {
        double level = Double.NEGATIVE_INFINITY;
        for (Candidate candidate : pool) {
            level = Math.max(level, candidate.difficulty());
        }
        return level == Double.NEGATIVE_INFINITY ? 0 : level;
    }
}
