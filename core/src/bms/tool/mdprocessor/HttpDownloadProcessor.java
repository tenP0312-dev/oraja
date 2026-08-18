package bms.tool.mdprocessor;

import bms.player.beatoraja.MainController;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.song.SongData;
import com.badlogic.gdx.graphics.Color;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-game download processor. In charge of:
 * <ul>
 *     <li>Manage all download tasks(stored in memory)</li>
 *     <li>Accept download task submission</li>
 *     <li>Download compressed files from remote http server</li>
 *     <li>Extract & update the 'songdata.db' automatically</li>
 * </ul>
 *
 * @author Catizard
 * @implNote Remember to update DOWNLOAD_SOURCES after adding a download source
 * @since Tue, 10 Jun 2025 05:33 PM
 */
public class HttpDownloadProcessor {
    private static final Logger logger = LoggerFactory.getLogger(HttpDownloadProcessor.class);
    public static final Map<String, HttpDownloadSourceMeta> DOWNLOAD_SOURCES = new HashMap<>();
    public static final int MAXIMUM_DOWNLOAD_COUNT = 5;
    private String downloadDirectory;

    static {
        // Ginger
        HttpDownloadSourceMeta gingerDownloadSourceMeta = GingerDownloadSource.META;
        DOWNLOAD_SOURCES.put(gingerDownloadSourceMeta.getName(), gingerDownloadSourceMeta);
        // Wriggle
        HttpDownloadSourceMeta wriggleDownloadSourceMeta = WriggleDownloadSource.META;
        DOWNLOAD_SOURCES.put(wriggleDownloadSourceMeta.getName(), wriggleDownloadSourceMeta);
        // Konmai
        HttpDownloadSourceMeta konmaiDownloadSourceMeta = KonmaiDownloadSource.META;
        DOWNLOAD_SOURCES.put(konmaiDownloadSourceMeta.getName(), konmaiDownloadSourceMeta);
    }

    // id => task
    private final Map<Integer, DownloadTask> tasks = new ConcurrentHashMap<>();
    // In-memory self-add id generator
    private final AtomicInteger idGenerator = new AtomicInteger(0);
    // Multi-thread download thread pool
    private final ExecutorService executor = Executors.newFixedThreadPool(MAXIMUM_DOWNLOAD_COUNT);
    private final ExecutorService submitter = Executors.newSingleThreadExecutor();
    // A reference to the main controller, only used for updating folder and rendering the message
    private final MainController main;
    private final HttpDownloadSource httpDownloadSource;
    private final boolean bmsirBodyDownloadEnabled;
    private final BmsirBodyDownloadService bmsirBodyDownloadService;
    private final Map<String, Object> bmsirBodyUrlLocks = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Path>> retainedBmsirArchives = new ConcurrentHashMap<>();

    public HttpDownloadProcessor(MainController main, HttpDownloadSource httpDownloadSource, String downloadDirectory) {
        this(main, httpDownloadSource, downloadDirectory, false);
    }

    public HttpDownloadProcessor(MainController main, HttpDownloadSource httpDownloadSource, String downloadDirectory,
                                 boolean bmsirBodyDownloadEnabled) {
        this.main = main;
        this.httpDownloadSource = httpDownloadSource;
        this.downloadDirectory = downloadDirectory;
        this.bmsirBodyDownloadEnabled = bmsirBodyDownloadEnabled;
        this.bmsirBodyDownloadService = new BmsirBodyDownloadService(Path.of(downloadDirectory));
    }

    public static HttpDownloadSourceMeta getDefaultDownloadSource() {
        return GingerDownloadSource.META;
    }

    private Optional<DownloadTask> getTaskById(int taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    // Would be best if this returned an immutable view over the tasks,
    // without creating a copy, in the interest of efficiency,
    // however I'm not sure if that is possible in java
    public Map<Integer, DownloadTask> getAllTasks() { return tasks; }

    public boolean canDownloadSong(SongData song) {
        return song != null && song.getMd5() != null && !song.getMd5().isBlank()
                && ((bmsirBodyDownloadEnabled
                        && BmsirBodyDownloadService.isEligibleBodyUrl(song.getUrl()))
                        || httpDownloadSource != null);
    }

    /**
     * Uses an explicit table body URL when the opt-in is enabled, otherwise
     * retains the existing MD5-provider route.
     */
    public void submitSongTask(SongData song) {
		if (song == null || song.getMd5() == null || song.getMd5().isBlank()) {
            ImGuiNotify.error("The selected song has no valid MD5");
            return;
        }
        if (bmsirBodyDownloadEnabled && BmsirBodyDownloadService.isEligibleBodyUrl(song.getUrl())) {
            submitTask(song.getUrl(), song.getTitle(), song.getMd5(), DownloadTask.DownloadMode.ArchiveInPlace);
            return;
        }
        submitMD5Task(song.getMd5(), song.getTitle());
    }

    /**
     * Submit a download task based on md5
     *
     * @param md5      missing sabun's md5
     * @param taskName task name, normally sabun's name
     */
    public void submitMD5Task(String md5, String taskName) {
        logger.info("[HttpDownloadProcessor] Trying to submit new download task[{}](based on md5: {})", taskName, md5);
        if (httpDownloadSource == null) {
            logger.info("[HttpDownloadProcessor] No legacy HTTP download provider is enabled");
            ImGuiNotify.warning("No HTTP download provider is enabled for this song");
            return;
        }
        String sourceName = httpDownloadSource.getName();
        String downloadURL;
        try {
            downloadURL = httpDownloadSource.getDownloadURLBasedOnMd5(md5);
        } catch (FileNotFoundException e) {
            logger.error("[HttpDownloadProcessor] Remote server[{}] reports no such data", sourceName);
            ImGuiNotify.error(String.format("Cannot find specified song from %s", sourceName));
            return;
        } catch (RuntimeException e) {
            e.printStackTrace();
            logger.error("[HttpDownloadProcessor] Cannot get download url from remote server[{}] due to unexpected exception: {}", sourceName, e.getMessage());
			ImGuiNotify.error(String.format("%s returns a severe error: %s", sourceName, e.getMessage()));
            return;
        }

        submitTask(downloadURL, taskName, md5, DownloadTask.DownloadMode.LegacyExtract);
    }

    private void submitTask(String downloadURL, String taskName, String md5, DownloadTask.DownloadMode mode) {
        String displayName = taskName == null || taskName.isBlank() ? md5 : taskName;
        // NOTE: The reason of using executor instead of using 'synchronized' on tasks directly is forcing
        // it to run the submit step on an different thread to get rid of the re-entrant feature of 'synchronized'.
        // Alternative way is providing a wait queue and an extra thread polling submit request routinely
        Future<TaskSubmission> submit = submitter.submit(() -> {
            synchronized (tasks) {
                DownloadTask existing = tasks.values().stream()
                        .filter(task -> hasSameTaskIdentity(task, downloadURL, md5))
                        .findFirst()
                        .orElse(null);
                if (claimFailedTaskForRetry(existing)) {
                    logger.info("[HttpDownloadProcessor] Retrying failed download task[{}]({})", displayName, downloadURL);
                    ImGuiNotify.info(String.format("Retrying download task[%s]", displayName));
                    return new TaskSubmission(existing, true);
                }
                if (existing != null) {
                    logger.info("[HttpDownloadProcessor] Rejecting active or completed duplicate task[{}]({})",
                            displayName, downloadURL);
                    ImGuiNotify.warning("Already submitted");
                    return null;
                }
                int taskId = idGenerator.addAndGet(1);
                DownloadTask downloadTask = new DownloadTask(taskId, downloadURL, displayName, md5, mode);
                tasks.put(taskId, downloadTask);
                ImGuiNotify.info(String.format("New download task[%s] submitted", displayName));
                return new TaskSubmission(downloadTask, false);
            }
        });

        TaskSubmission submission;
        try {
            submission = submit.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while submitting a download task", e);
            return;
        } catch (ExecutionException e) {
            e.printStackTrace();
			logger.error("Unexpected error from submitting download task: {}", e.getMessage());
            return;
        }

        if (submission == null) {
            return;
        }

        if (submission.retry()) {
            retryDownloadTask(submission.task());
        } else {
            executeDownloadTask(submission.task());
        }
    }

    /**
     * Execute the download task, which are chained steps:
     * <ol>
     *     <li>Download the archive file from url</li>
     *     <li>Extract the package</li>
     *     <li>Update download directory</li>
     *     <li>Delete the archive file</li>
     * </ol>
     *
     * @param downloadTask task
     */
    public void executeDownloadTask(DownloadTask downloadTask) {
        if (downloadTask.getDownloadMode() == DownloadTask.DownloadMode.ArchiveInPlace) {
            executeArchiveInPlaceTask(downloadTask);
            return;
        }
        executor.submit(() -> {
            String taskName = downloadTask.getName();
            String downloadURL = downloadTask.getUrl();
            String hash = downloadTask.getHash();
            logger.info("[HttpDownloadProcessor] Trying to kick new download task[{}]({})", taskName, downloadURL);
            downloadTask.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Downloading);
            Path result = null;
            // 1) Download file from remote http server
            try {
                result = downloadFileFromURL(downloadTask, String.format("%s.7z", hash));
            } catch (Exception e) {
                e.printStackTrace();
                ImGuiNotify.error(String.format("Failed downloading from %s due to %s", httpDownloadSource.getName(), e.getMessage()));
            }
            if (result == null) {
                // Download failed, skip the remaining steps
                downloadTask.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Error);
                return;
            }
            // 2) Extract the compressed archive & update download directory automatically
            boolean successfullyExtracted = false;
            String bmsDirectory = null;
            try {
                bmsDirectory = extractCompressedFile(result.toFile(), null);
                successfullyExtracted = true;
                downloadTask.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Extracted);
            } catch (Exception e) {
                e.printStackTrace();
                ImGuiNotify.error(String.format("Failed extracting file: %s due to %s", result.getFileName(), e.getMessage()));
            }
            if (successfullyExtracted) {
                // TODO: Directory update is protected, this might cause some uncovered situation. Personally speaking,
                // I don't think this has any issue since user can always turn back to root directory
                // and update the download directory manually
                ImGuiNotify.info("Successfully downloaded & extracted. Trying to rebuild download directory");
                main.updateSong(bmsDirectory, true);
                // If everything works well, trying to delete the downloaded archive
                try {
                    Files.delete(result);
                } catch (IOException e) {
                    e.printStackTrace();
                    ImGuiNotify.error("Failed deleting archive file automatically");
                }
            }
        });
    }

    private void executeArchiveInPlaceTask(DownloadTask downloadTask) {
        executor.submit(() -> {
            logger.info("[HttpDownloadProcessor] Downloading BMS-IR body URL for [{}]", downloadTask.getName());
            downloadTask.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Downloading);
            downloadTask.setErrorMessage(null);
            try {
                String registeredUrl = downloadTask.getUrl();
                Object installLock = bmsirBodyUrlLocks.computeIfAbsent(registeredUrl, ignored -> new Object());
                BmsirBodyDownloadService.InstallResult result;
                synchronized (installLock) {
                    List<Path> retained = List.copyOf(retainedBmsirArchives.getOrDefault(
                            registeredUrl, new CopyOnWriteArrayList<>()));
                    result = bmsirBodyDownloadService.install(downloadTask, retained);
                    retainedBmsirArchives.computeIfAbsent(
                            registeredUrl, ignored -> new CopyOnWriteArrayList<>()).addIfAbsent(result.archive());
                }
                downloadTask.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Extracted);
                if (result.reused()) {
                    ImGuiNotify.info(String.format(
                            "Existing archive contains the requested chart; reusing it: %s",
                            result.archive().getFileName()));
                } else {
                    String source = result.wayback()
                            ? (result.landingPage() ? "archive link from a Wayback page" : "Wayback snapshot")
                            : (result.landingPage() ? "archive link from the registered page" : "registered body URL");
                    ImGuiNotify.info(String.format(
                            "Archive saved without extraction from %s: %s", source, result.archive().getFileName()));
                }
                rescanBodyDownloadDirectory(
                        downloadDirectory,
                        main::updateSong,
                        () -> notifyBodyDownloadRegistration(downloadTask));
            } catch (IOException error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                logger.warn("[HttpDownloadProcessor] BMS-IR body archive rejected: {}", message, error);
                downloadTask.setErrorMessage(message);
                downloadTask.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Error);
                ImGuiNotify.error("BMS-IR body download rejected: " + message);
            }
        });
    }

    private void notifyBodyDownloadRegistration(DownloadTask downloadTask) {
        boolean registered = false;
        try {
            SongData[] songs = main.getSongDatabase().getSongDatas(new String[]{downloadTask.getHash()});
            registered = songs != null && songs.length > 0;
        } catch (RuntimeException error) {
            logger.warn("[HttpDownloadProcessor] Failed to verify the downloaded chart in the song database", error);
        }
        if (registered) {
            ImGuiNotify.info(BMSIRArenaI18n.text(
                    "ダウンロードした譜面を登録しました。もう一度決定するとプレイできます",
                    "The downloaded chart is ready. Select it again to play"), 8000);
        } else {
            ImGuiNotify.warning(BMSIRArenaI18n.text(
                    "圧縮ファイルは保存しましたが、譜面を曲DBで確認できませんでした。ダウンロード先の曲更新を再実行してください",
                    "The archive was saved, but the chart was not found in the song database. Update the download folder again"),
                    10000);
        }
    }

    static void rescanBodyDownloadDirectory(String downloadDirectory,
                                            SongUpdateRequester updateSong,
                                            Runnable completion) {
        updateSong.accept(downloadDirectory, false, completion);
    }

    @FunctionalInterface
    interface SongUpdateRequester {
        void accept(String path, boolean updateParentWhenMissing, Runnable completion);
    }

    /**
     * Retry a download task
     */
    public void retryDownloadTask(DownloadTask downloadTask) {
        downloadTask.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Prepare);
        downloadTask.setErrorMessage(null);
        downloadTask.setDownloadSize(0);
        downloadTask.setContentLength(0);
        executeDownloadTask(downloadTask);
    }

    private record TaskSubmission(DownloadTask task, boolean retry) {
    }

    static boolean hasSameTaskIdentity(DownloadTask task, String downloadURL, String md5) {
        return task != null && task.getUrl().equals(downloadURL) && task.getHash().equalsIgnoreCase(md5);
    }

    static boolean claimFailedTaskForRetry(DownloadTask task) {
        if (task == null || task.getDownloadTaskStatus() != DownloadTask.DownloadTaskStatus.Error) {
            return false;
        }
        // Reserve the retry while tasks is locked so rapid repeated submissions cannot start it twice.
        task.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Prepare);
        return true;
    }

    /**
     * Download a file from url (no intermediate file protection)
     *
     * @param fallbackFileName fallback file name if remote server's response doesn't contain a valid file name
     * @return result file path, null if failed
     */
    private Path downloadFileFromURL(DownloadTask task, String fallbackFileName) {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        Path result = null;

        try {
            URL url = new URL(task.getUrl());
            conn = ((HttpURLConnection) url.openConnection());
            conn.connect();
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw new FileNotFoundException("Package not found at " + httpDownloadSource.getName());
                }
                throw new IllegalStateException("Unexpected http response code: " + responseCode);
            }
            // Prepare the file name
            String fileName = fallbackFileName;
            String contentDisposition = conn.getHeaderField("Content-Disposition");
            String candidateFileName = "";
            if (contentDisposition != null && !contentDisposition.isEmpty()) {
                Matcher matcher = Pattern.compile("filename=\"?([^\"]+)\"?").matcher(contentDisposition);
                if (matcher.find()) {
                    candidateFileName = matcher.group(1);
                }
            }
            if (candidateFileName != null && !candidateFileName.isEmpty()) {
                fileName = candidateFileName;
            }

            long contentLength = conn.getContentLengthLong();
            is = conn.getInputStream();
            result = Path.of(downloadDirectory, fileName);
            fos = new FileOutputStream(result.toFile());

            // TODO: We can bind the buffer to the worker thread instead of creating & releasing it repeatedly
            byte[] buffer = new byte[8192];
            long downloadBytes = 0;

            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                downloadBytes += read;
                task.setDownloadSize(downloadBytes);
                task.setContentLength(contentLength);
            }
            logger.info("[HttpDownloadProcessor] Download successfully to {}", result);
            task.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Downloaded);
        } catch (Exception e) {
            e.printStackTrace();
			logger.info("[HttpDownloadProcessor] Failed to download file from url: {}", e.getMessage());
            task.setDownloadSize(0);
            task.setContentLength(0);
            task.setErrorMessage(e.getMessage());
            // All other unexpected exception are rethrown as RuntimeException
            throw new RuntimeException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.disconnect();
                }
                if (is != null) {
                    is.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                // Do nothing...
            }
        }
        return result;
    }

    /**
     * Extract a compressed file into targetPath
     *
     * @param file       compressed archive
     * @param targetPath target directory, fallback to DOWNLOAD_DIRECTORY if null
     * @return the path to the directory just extracted
     */
    private String extractCompressedFile(File file, Path targetPath) {
        Path resultDirectory = targetPath == null ? Path.of(downloadDirectory) : targetPath;
        String bmsDirectory = null;
        try (SevenZFile sevenZFile = SevenZFile.builder().setFile(file).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    if (bmsDirectory == null) {
                        bmsDirectory = Paths.get(resultDirectory.toString(), entry.getName()).toAbsolutePath().toString();
                    }
                    continue;
                }
                File outputFile = new File(resultDirectory.toString(), entry.getName());
                outputFile.getParentFile().mkdirs();

                try (FileOutputStream fos = new FileOutputStream(outputFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                    byte[] buf = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = sevenZFile.read(buf)) != -1) {
                        bos.write(buf, 0, bytesRead);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
        return bmsDirectory;
    }
}
