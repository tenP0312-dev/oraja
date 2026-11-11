package bms.player.beatoraja.nonstop;

import bms.model.BMSModel;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.audio.BMSLoudnessAnalyzer;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Finds the songs of a set in the local library and loads their charts with
 * the player's own settings (long-note mode, forced LN), so a set plays
 * exactly like its songs do on their own.
 */
public final class MixLibrary {
    private static final Logger logger = LoggerFactory.getLogger(MixLibrary.class);
    private static final int CACHE_SIZE = 40;
    private static final long LOUDNESS_TIMEOUT_MS = 60_000L;

    private final MainController main;
    private final Map<String, Loaded> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Loaded> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /** A chart loaded for mixing. */
    public record Loaded(SongData song, BMSModel model, SongResource chart, MixTrackAnalysis analysis) {
    }

    private static MixLibrary shared;

    public MixLibrary(MainController main) {
        this.main = main;
    }

    /** One library (and chart cache) shared by the editor and Music Select's NONSTOP MIX folder. */
    public static synchronized MixLibrary shared(MainController main) {
        if (shared == null || shared.main != main) {
            shared = new MixLibrary(main);
        }
        return shared;
    }

    public MainController main() {
        return main;
    }

    private static final long FIND_TTL_MS = 5_000L;
    private final Map<String, Object[]> found = new java.util.HashMap<>();

    /** The local song of a track, or null when the player does not have it. Cached briefly for the overlay. */
    public SongData find(MixTrack track) {
        String key = track.sha256 + "|" + track.md5;
        long now = System.currentTimeMillis();
        synchronized (found) {
            Object[] hit = found.get(key);
            if (hit != null && now - (Long) hit[1] < FIND_TTL_MS) {
                return (SongData) hit[0];
            }
        }
        SongData song = lookup(track);
        synchronized (found) {
            if (found.size() > 4096) {
                found.clear();
            }
            found.put(key, new Object[] {song, now});
        }
        return song;
    }

    private SongData lookup(MixTrack track) {
        if (main == null || main.getSongDatabase() == null) {
            return null;
        }
        List<String> hashes = new ArrayList<>(2);
        if (!track.sha256.isEmpty()) {
            hashes.add(track.sha256);
        }
        if (!track.md5.isEmpty()) {
            hashes.add(track.md5);
        }
        if (hashes.isEmpty()) {
            return null;
        }
        SongData[] songs = main.getSongDatabase().getSongDatas(hashes.toArray(String[]::new));
        for (SongData song : songs) {
            if (song.getPath() != null && (track.sha256.isEmpty() || track.sha256.equals(song.getSha256()))) {
                return song;
            }
        }
        return songs.length > 0 && songs[0].getPath() != null ? songs[0] : null;
    }

    /** Tracks of a plan that are not in the local library. */
    public List<MixTrack> missing(MixPlan plan) {
        List<MixTrack> missing = new ArrayList<>();
        for (MixTrack track : plan.tracks) {
            if (find(track) == null) {
                missing.add(track);
            }
        }
        return missing;
    }

    public static MixTrack trackFrom(SongData song) {
        MixTrack track = new MixTrack();
        track.sha256 = song.getSha256() == null ? "" : song.getSha256();
        track.md5 = song.getMd5() == null ? "" : song.getMd5();
        track.title = song.getTitle();
        track.subtitle = song.getSubtitle();
        track.artist = song.getArtist();
        track.genre = song.getGenre();
        track.bpm = song.getMaxbpm();
        track.level = song.getLevel();
        track.localPath = song.getPath() == null ? "" : song.getPath();
        return track;
    }

    /**
     * Loads (or returns the cached) chart of a track. A chart with #RANDOM gets
     * its drawn branch written back to the track so the set stays the same.
     */
    public Loaded load(MixTrack track) {
        SongData song = find(track);
        if (song == null) {
            throw new IllegalStateException(text("曲が見つかりません: ", "Song not found: ") + track.displayTitle());
        }
        PlayerResource resource = main.getPlayerResource();
        int lnmode = main.getPlayerConfig().getLnmode();
        String key = song.getSha256() + "|" + lnmode + "|" + main.getPlayerConfig().isBmsirForceLn() + "|"
                + Arrays.toString(track.random);
        synchronized (cache) {
            Loaded cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Path path = Path.of(song.getPath());
        BMSModel model = resource.loadDetachedBMSModel(path, lnmode, track.random);
        if (model == null) {
            throw new IllegalStateException(text("譜面を読み込めません: ", "Could not load the chart: ") + track.displayTitle());
        }
        if (track.random == null && model.getRandom() != null && model.getRandom().length > 0) {
            track.random = model.getRandom().clone();
        }
        if (track.sha256.isEmpty() && model.getSHA256() != null) {
            track.sha256 = model.getSHA256();
        }
        track.localPath = song.getPath();
        Loaded loaded = new Loaded(song, model, SongResources.fromPath(path), MixTrackAnalysis.of(model));
        synchronized (cache) {
            cache.put(song.getSha256() + "|" + lnmode + "|" + main.getPlayerConfig().isBmsirForceLn() + "|"
                    + Arrays.toString(track.random), loaded);
        }
        return loaded;
    }

    /**
     * Per-song volume factors that bring every song to the set's median
     * loudness; 1.0 for songs that could not be analysed.
     */
    public double[] loudnessGains(List<Loaded> songs, java.util.function.IntConsumer progress) {
        double[] gains = new double[songs.size()];
        Arrays.fill(gains, 1.0);
        BMSLoudnessAnalyzer analyzer = main.getLoudnessAnalyzer();
        if (analyzer == null || !analyzer.isAvailable()) {
            return gains;
        }
        double[] lufs = new double[songs.size()];
        List<Double> measured = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            lufs[i] = Double.NaN;
            try {
                Future<BMSLoudnessAnalyzer.AnalysisResult> task = analyzer.analyzeAsync(songs.get(i).model());
                BMSLoudnessAnalyzer.AnalysisResult result = task.get(LOUDNESS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (result.success && Double.isFinite(result.loudnessLUFS)) {
                    lufs[i] = result.loudnessLUFS;
                    measured.add(result.loudnessLUFS);
                }
            } catch (Exception e) {
                logger.warn("NONSTOP MIX loudness analysis failed: {}", e.getMessage());
            }
            if (progress != null) {
                progress.accept(i + 1);
            }
        }
        if (measured.isEmpty()) {
            return gains;
        }
        measured.sort(Double::compare);
        double target = measured.get(measured.size() / 2);
        for (int i = 0; i < gains.length; i++) {
            if (Double.isFinite(lufs[i])) {
                gains[i] = Math.max(0.3, Math.min(1.2, Math.pow(10.0, (target - lufs[i]) / 20.0)));
            }
        }
        return gains;
    }

    private static String text(String japanese, String english) {
        return bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n.text(japanese, english);
    }
}
