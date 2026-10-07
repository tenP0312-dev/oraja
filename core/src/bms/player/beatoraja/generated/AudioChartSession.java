package bms.player.beatoraja.generated;

import bms.player.beatoraja.audio.AudioFileDecoder;
import bms.player.beatoraja.bmsir.BMSIRTestPlayFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * One dropped audio file: background analysis, player adjustments, and the
 * generated chart written under {@link BMSIRTestPlayFolder#DIRECTORY_NAME} so
 * that the existing work-folder policy disables score saving, IR submission,
 * and Arena selection for it.
 */
public final class AudioChartSession {
    private static final Logger logger = LoggerFactory.getLogger(AudioChartSession.class);

    public static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "ogg", "wav", "flac");
    private static final int DECODE_SAMPLE_RATE = 44100;
    private static final long MAX_FILE_BYTES = 200L << 20;
    private static final double ONSET_RADIUS_SEC = 0.02;
    private static final Charset BMS_CHARSET = Charset.forName("MS932");

    public enum State { ANALYZING, READY, FAILED }

    private static volatile AudioChartSession current;

    private final Path audio;
    private final boolean window;
    private final Runnable onFinished;
    private volatile State state = State.ANALYZING;
    private volatile String error;
    private volatile AudioGridEstimator.Result result;
    private double bpm;
    private double firstBeatSec;
    private long seed;
    private String key;
    private volatile boolean restoredAdjustment;

    private AudioChartSession(Path audio, boolean window, Runnable onFinished) {
        this.audio = audio;
        this.window = window;
        this.onFinished = onFinished;
    }

    /** Difficulty settings remembered in the player config, shared by the drop window and the folder. */
    public static GeneratedChartBuilder.Settings settings(bms.player.beatoraja.PlayerConfig config) {
        if (config == null) {
            return new GeneratedChartBuilder.Settings(GeneratedChartBuilder.DEFAULT_DENSITY, 1, 2, false);
        }
        return new GeneratedChartBuilder.Settings(config.isGeneratedChartFollowMusic(),
                config.getGeneratedChartDensity(), config.getGeneratedChartDivision(),
                config.isGeneratedChartRepeatBars(), config.getGeneratedChartMinChord(),
                config.getGeneratedChartMaxChord(), config.isGeneratedChartScratch());
    }

    /** Audio files, or movies whose audio is extracted and shown as BGA (#459). */
    public static boolean isSupportedFile(Path path) {
        return isAudioFile(path) || VideoAudioExtractor.isVideoFile(path);
    }

    public static boolean isAudioFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && AUDIO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Starts analysing a dropped audio file in the drop window, replacing any previous session. */
    public static AudioChartSession start(Path audio) {
        return start(audio, true, null);
    }

    /**
     * Starts analysing an audio file, replacing any previous session.
     *
     * @param window     whether the drop window shows this session (false for the Music Select folder)
     * @param onFinished run on the analysis thread once the session is READY or FAILED; may be null
     */
    public static AudioChartSession start(Path audio, boolean window, Runnable onFinished) {
        AudioChartSession session = new AudioChartSession(audio, window, onFinished);
        current = session;
        Thread worker = new Thread(session::analyze, "generated-chart-analysis");
        worker.setDaemon(true);
        worker.start();
        return session;
    }

    public static AudioChartSession current() {
        return current;
    }

    public static void close() {
        current = null;
    }

    private void analyze() {
        try {
            analyzeOrFail();
        } finally {
            if (onFinished != null) {
                onFinished.run();
            }
        }
    }

    private void analyzeOrFail() {
        try {
            Path source = audio;
            if (VideoAudioExtractor.isVideoFile(audio)) {
                // the movie's audio, aligned to its first frame, is what gets analysed and played
                source = extractedAudio();
            } else if (Files.size(audio) > MAX_FILE_BYTES) {
                // the whole file is decoded in memory; a 15-minute WAV is ~160 MB
                fail("the file is larger than " + (MAX_FILE_BYTES >> 20) + " MB");
                return;
            }
            AudioFileDecoder.MonoAudio decoded = AudioFileDecoder.decodeMono(source, DECODE_SAMPLE_RATE);
            if (decoded == null) {
                fail("could not decode the audio file");
                return;
            }
            long started = System.nanoTime();
            AudioGridEstimator.Result estimate = AudioGridEstimator.analyze(decoded.samples(), decoded.sampleRate());
            logger.info("Generated chart analysis: {} bpm={} firstBeat={}s confidence={} drift={}ms ({} ms)",
                    audio.getFileName(), estimate.bpm(), estimate.firstBeatSec(), estimate.confidence(),
                    estimate.maxDriftMs(), (System.nanoTime() - started) / 1_000_000);
            synchronized (this) {
                result = estimate;
                bpm = estimate.bpm();
                firstBeatSec = estimate.firstBeatSec();
                seed = initialSeed();
                double[] saved = key != null ? loadGrid(generatedRoot().resolve(key), estimate) : null;
                if (saved != null) {
                    bpm = saved[0];
                    firstBeatSec = saved[1];
                    restoredAdjustment = true;
                }
            }
            state = State.READY;
        } catch (IllegalArgumentException exception) {
            fail(exception.getMessage());
        } catch (Throwable throwable) {
            logger.warn("Generated chart analysis failed: {}", audio, throwable);
            fail(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    private static final String EXTRACTED_AUDIO = "audio.wav";

    /** Extracts (once per file) the movie's audio next to the generated chart. */
    private Path extractedAudio() throws IOException {
        Path directory = generatedRoot().resolve(audioKey());
        Files.createDirectories(directory);
        Path wav = directory.resolve(EXTRACTED_AUDIO);
        if (!Files.isRegularFile(wav)) {
            long started = System.nanoTime();
            VideoAudioExtractor.Extracted extracted = VideoAudioExtractor.extract(audio, wav);
            logger.info("Generated chart video audio: {} {} ({} ms)", audio.getFileName(), extracted,
                    (System.nanoTime() - started) / 1_000_000);
        }
        return wav;
    }

    private void fail(String message) {
        error = message;
        state = State.FAILED;
    }

    private long initialSeed() {
        try {
            key = audioKey();
            return Long.parseLong(key.substring(0, 15), 16);
        } catch (IOException | RuntimeException exception) {
            return audio.getFileName().toString().hashCode();
        }
    }

    public Path audio() {
        return audio;
    }

    /** Whether the drop window shows this session. */
    public boolean window() {
        return window;
    }

    public State state() {
        return state;
    }

    public String error() {
        return error;
    }

    public AudioGridEstimator.Result result() {
        return result;
    }

    public synchronized double bpm() {
        return bpm;
    }

    public synchronized double firstBeatSec() {
        return firstBeatSec;
    }

    public synchronized long seed() {
        return seed;
    }

    public synchronized void setBpm(double value) {
        if (value >= 20.0 && value <= 999.0) {
            bpm = value;
        }
    }

    public synchronized void scaleBpm(double factor) {
        setBpm(bpm * factor);
    }

    /** Moves the grid by half a beat: swaps beats and 8th off-beats. */
    public synchronized void shiftHalfBeat() {
        double beat = 60.0 / bpm;
        firstBeatSec += beat / 2;
        // stay near the detected start, so pressing twice returns to the original phase
        double anchor = result != null ? result.firstBeatSec() : 0.0;
        if (firstBeatSec > anchor + beat * 0.75) {
            firstBeatSec -= beat;
        }
        firstBeatSec = Math.max(0.0, firstBeatSec);
    }

    public synchronized void nudgeMs(double milliseconds) {
        firstBeatSec = Math.max(0.0, firstBeatSec + milliseconds / 1000.0);
    }

    public synchronized void resetAdjustments() {
        if (result != null) {
            bpm = result.bpm();
            firstBeatSec = result.firstBeatSec();
        }
        restoredAdjustment = false;
    }

    /** Whether the BPM / first beat came from this file's saved correction. */
    public boolean restoredAdjustment() {
        return restoredAdjustment;
    }

    public synchronized void reshuffle() {
        seed++;
    }

    /**
     * Copies the audio next to a freshly written chart in the work folder.
     *
     * @return the chart to play
     */
    public Path writeChart(GeneratedChartBuilder.Settings settings) throws IOException {
        AudioGridEstimator.Result estimate = result;
        if (estimate == null) {
            throw new IllegalStateException("analysis is not finished");
        }
        double chartBpm;
        double chartFirstBeat;
        long chartSeed;
        synchronized (this) {
            chartBpm = bpm;
            chartFirstBeat = firstBeatSec;
            chartSeed = seed;
        }
        Path directory = generatedRoot().resolve(audioKey());
        Files.createDirectories(directory);
        String audioName;
        String bgaName = null;
        if (VideoAudioExtractor.isVideoFile(audio)) {
            audioName = EXTRACTED_AUDIO;
            bgaName = "bga." + extension(audio);
            placeBeside(directory.resolve(bgaName));
        } else {
            audioName = "audio." + extension(audio);
            Path copy = directory.resolve(audioName);
            if (!Files.exists(copy) || Files.size(copy) != Files.size(audio)) {
                Files.copy(audio, copy, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        String title = stripExtension(audio.getFileName().toString());
        GeneratedChartBuilder.Chart chart = GeneratedChartBuilder.build(
                title,
                audioName,
                bgaName,
                chartBpm,
                chartFirstBeat,
                estimate.lastSoundSec(),
                new GeneratedChartBuilder.Onsets() {
                    @Override
                    public double strength(double timeSec) {
                        return estimate.onsetStrengthAt(timeSec, ONSET_RADIUS_SEC);
                    }

                    @Override
                    public double[] bands(double timeSec) {
                        return estimate.bandStrengthsAt(timeSec, ONSET_RADIUS_SEC);
                    }

                    @Override
                    public double peakTime(double fromSec, double toSec) {
                        return estimate.onsetPeakTime(fromSec, toSec);
                    }

                    @Override
                    public double[] barStarts(double firstBarSec, double barSec, int bars) {
                        return estimate.barStarts(firstBarSec, barSec, bars);
                    }
                },
                settings,
                chartSeed);
        saveGrid(directory, chartBpm, chartFirstBeat, estimate);
        Path bms = directory.resolve("chart.bms");
        Files.write(bms, chart.text().getBytes(BMS_CHARSET));
        logger.info("Generated chart written: {} ({} notes, {} positions, {} repeated bars)",
                bms, chart.notes(), chart.positions(), chart.repeated());
        return bms;
    }

    private static final String GRID_FILE = "grid.properties";

    /**
     * Remembers the player's BPM / first beat for this file. The analysed values
     * are stored too, so a correction is only reused for the same analysis.
     */
    static void saveGrid(Path directory, double bpm, double firstBeat, AudioGridEstimator.Result estimate) {
        java.util.Properties grid = new java.util.Properties();
        grid.setProperty("bpm", Double.toString(bpm));
        grid.setProperty("firstBeatSec", Double.toString(firstBeat));
        grid.setProperty("analysedBpm", Double.toString(estimate.bpm()));
        grid.setProperty("analysedFirstBeatSec", Double.toString(estimate.firstBeatSec()));
        try (var out = Files.newOutputStream(directory.resolve(GRID_FILE))) {
            grid.store(out, "generated chart grid correction");
        } catch (IOException exception) {
            logger.warn("Could not save the generated chart grid: {}", directory, exception);
        }
    }

    /** @return {bpm, firstBeatSec} saved for the same analysis, or null */
    static double[] loadGrid(Path directory, AudioGridEstimator.Result estimate) {
        Path file = directory.resolve(GRID_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        java.util.Properties grid = new java.util.Properties();
        try (var in = Files.newInputStream(file)) {
            grid.load(in);
            double analysedBpm = Double.parseDouble(grid.getProperty("analysedBpm"));
            double analysedFirst = Double.parseDouble(grid.getProperty("analysedFirstBeatSec"));
            if (Math.abs(analysedBpm - estimate.bpm()) > 1e-6
                    || Math.abs(analysedFirst - estimate.firstBeatSec()) > 1e-6) {
                return null;
            }
            double bpm = Double.parseDouble(grid.getProperty("bpm"));
            double first = Double.parseDouble(grid.getProperty("firstBeatSec"));
            return bpm >= 20.0 && bpm <= 999.0 && first >= 0.0 ? new double[] {bpm, first} : null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /** Charts live under the work-folder marker, so they never save scores or reach IR/Arena. */
    static Path generatedRoot() {
        return Path.of(BMSIRTestPlayFolder.DIRECTORY_NAME, "generated").toAbsolutePath();
    }

    private String audioKey() throws IOException {
        try (InputStream input = Files.newInputStream(audio)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** A hard link to the source file (no second copy of a large movie), or a copy if linking fails. */
    private void placeBeside(Path target) throws IOException {
        if (Files.exists(target) && Files.size(target) == Files.size(audio)) {
            return;
        }
        Files.deleteIfExists(target);
        try {
            Files.createLink(target, audio);
        } catch (IOException | UnsupportedOperationException exception) {
            Files.copy(audio, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
