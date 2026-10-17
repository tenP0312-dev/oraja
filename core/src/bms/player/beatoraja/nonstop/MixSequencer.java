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

    /** A library song that may be used. */
    public record Candidate(String sha256, String md5, String title, String artist, String genre, double bpm,
            boolean steadyTempo, long lengthMs, int notes, int level, String path) {

        /** Title without difficulty decorations, used to avoid the same song twice. */
        public String songKey() {
            String key = title == null ? "" : title.toLowerCase(Locale.ROOT);
            key = key.replaceAll("[\\[\\(（【<＜].*?[\\]\\)）】>＞]", "");
            key = key.replaceAll("\\s+-[^-]+-\\s*$", "");
            return key.replaceAll("\\s+", " ").trim() + "|" + (artist == null ? "" : artist.toLowerCase(Locale.ROOT));
        }
    }

    public enum GenreRule {
        /** Only songs whose genre matches the reference. */
        STRICT,
        /** Prefer close genres but allow others. */
        SIMILAR,
        /** Genre does not matter. */
        ANY
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
        public int minLevel = 0;
        public int maxLevel = 99;
        public Curve curve = Curve.RISING;
        /** Allowed tempo difference between neighbours before it costs (ratio). */
        public double tempoTolerance = 0.04;
        /** Treat half and double tempo as compatible. */
        public boolean halfDouble = true;
        public long seed = System.nanoTime();
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
            copy.minLevel = minLevel;
            copy.maxLevel = maxLevel;
            copy.curve = curve;
            copy.tempoTolerance = tempoTolerance;
            copy.halfDouble = halfDouble;
            copy.seed = seed;
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
        Random random = new Random(settings.seed);
        String referenceGenre = settings.genre == null || settings.genre.isBlank()
                ? (first != null ? first.genre() : fixed.isEmpty() ? "" : fixed.get(0).candidate().genre())
                : settings.genre;
        List<Candidate> usable = new ArrayList<>();
        for (Candidate candidate : pool) {
            if (candidate.level() < settings.minLevel || candidate.level() > settings.maxLevel) {
                continue;
            }
            if (candidate.lengthMs() > 0 && candidate.lengthMs() < 30_000L) {
                continue;
            }
            if (settings.genreRule == GenreRule.STRICT && !referenceGenre.isBlank()
                    && !normalizeGenre(candidate.genre()).equals(normalizeGenre(referenceGenre))) {
                continue;
            }
            usable.add(candidate);
        }
        int[] levels = {Math.max(settings.minLevel, minLevel(usable)), Math.min(settings.maxLevel, maxLevel(usable))};
        double[] jitter = new double[usable.size()];
        for (int i = 0; i < jitter.length; i++) {
            jitter[i] = random.nextDouble() * 0.35;
        }

        List<State> beam = new ArrayList<>();
        beam.add(new State(List.of(), Set.of(), 0, 0));
        List<State> finished = new ArrayList<>();
        int maxSteps = Math.max(1, settings.maxTracks);
        for (int step = 0; step < maxSteps && !beam.isEmpty(); step++) {
            List<State> next = new ArrayList<>();
            for (State state : beam) {
                Candidate forced = fixedAt(fixed, step);
                if (step == 0 && forced == null && first != null) {
                    forced = first;
                }
                if (forced != null) {
                    if (!state.used().contains(forced.songKey())) {
                        next.add(extend(state, forced, cost(state, forced, step, referenceGenre, settings, levels), settings));
                    }
                    continue;
                }
                for (int c = 0; c < usable.size(); c++) {
                    Candidate candidate = usable.get(c);
                    if (state.used().contains(candidate.songKey()) || isFixedElsewhere(fixed, candidate)) {
                        continue;
                    }
                    double cost = cost(state, candidate, step, referenceGenre, settings, levels) + jitter[c];
                    next.add(extend(state, candidate, cost, settings));
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
        return finished.stream().min(Comparator.comparingDouble(State::cost)).map(State::songs).orElse(List.of());
    }

    private static State withLengthCost(State state, Settings settings) {
        double miss = Math.abs(state.totalMs() - settings.targetMs) / (double) Math.max(60_000L, settings.targetMs);
        return new State(state.songs(), state.used(), state.totalMs(), state.cost() + miss * 6.0);
    }

    private static State extend(State state, Candidate candidate, double cost, Settings settings) {
        List<Candidate> songs = new ArrayList<>(state.songs());
        songs.add(candidate);
        Set<String> used = new HashSet<>(state.used());
        used.add(candidate.songKey());
        long length = candidate.lengthMs() > 0 ? candidate.lengthMs() : 120_000L;
        if (settings.segmentMs > 0) {
            length = Math.min(length, settings.segmentMs);
        }
        long total = state.totalMs() + length - (state.songs().isEmpty() ? 0 : settings.joinMs);
        return new State(songs, used, total, state.cost() + cost);
    }

    static double cost(State state, Candidate candidate, int step, String referenceGenre, Settings settings,
            int[] levels) {
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
        if (!referenceGenre.isBlank() && settings.genreRule != GenreRule.ANY) {
            cost += genreCost(referenceGenre, candidate.genre()) * 1.5;
        }
        // difficulty follows the requested curve over the expected length
        double progress = Math.min(1.0, state.totalMs() / (double) Math.max(1L, settings.targetMs));
        int low = levels[0];
        int high = levels[1];
        if (high > low) {
            double target = low + (high - low) * settings.curve.at(progress);
            cost += Math.abs(candidate.level() - target) / Math.max(1.0, (high - low) / 3.0);
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

    private static boolean isFixedElsewhere(List<Fixed> fixed, Candidate candidate) {
        for (Fixed item : fixed) {
            if (item.candidate().songKey().equals(candidate.songKey())) {
                return true;
            }
        }
        return false;
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

    private static int minLevel(List<Candidate> pool) {
        int level = Integer.MAX_VALUE;
        for (Candidate candidate : pool) {
            level = Math.min(level, candidate.level());
        }
        return level == Integer.MAX_VALUE ? 0 : level;
    }

    private static int maxLevel(List<Candidate> pool) {
        int level = 0;
        for (Candidate candidate : pool) {
            level = Math.max(level, candidate.level());
        }
        return level;
    }
}
