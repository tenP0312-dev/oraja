package bms.player.beatoraja.camera;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bytedeco.javacpp.Loader;

/** Isolate device drivers and their potentially blocking open/read calls from the game JVM. */
public final class FFmpegCameraProcess {
    private static final java.util.Set<ProcessHandle> CHILDREN = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(FFmpegCameraProcess::terminateAll, "hand-camera-shutdown"));
    }
    private static final ScheduledExecutorService REAPER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "hand-camera-process-reaper");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * {@code value} is the FFmpeg input for this run, {@code label} the device name and {@code id} the OS
     * unique ID when FFmpeg reports one (DirectShow alternative name). Empty label/id mean unknown.
     */
    public record Device(String value, String label, String id) {
        public Device {
            value = value == null ? "" : value;
            label = label == null ? "" : label;
            id = id == null ? "" : id;
        }
        public Device(String value, String label) { this(value, label, ""); }
        public String displayName() { return label.isEmpty() ? value : label; }
        @Override public String toString() { return displayName(); }
    }

    /** The remembered camera is not connected now. Never fall back to another device. */
    static final class DeviceNotFoundException extends Exception {
        DeviceNotFoundException() { super(null, null, false, false); }
    }

    private FFmpegCameraProcess() { }

    static Process start(ProcessBuilder builder) throws java.io.IOException {
        Process process = builder.start();
        CHILDREN.add(process.toHandle());
        return process;
    }

    static void terminateAll() {
        // MainLoader may call System.exit before the daemon reaper's delayed kill runs.
        for (ProcessHandle handle : CHILDREN) if (handle.isAlive()) handle.destroyForcibly();
    }

    public static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
    }

    static List<String> command(String executable, String platform, HandCameraSettings settings) {
        List<String> command = new ArrayList<>(List.of(executable, "-hide_banner", "-loglevel", "error", "-nostdin"));
        String size = settings.width() + "x" + settings.height();
        switch (platform) {
            case "windows" -> command.addAll(List.of("-f", "dshow", "-video_size", size, "-framerate",
                    Integer.toString(settings.fps()), "-i", "video=" + settings.device()));
            case "macos" -> {
                if (!settings.device().matches("[0-9]+")) throw new IllegalArgumentException("Expected a camera index");
                command.addAll(List.of("-f", "avfoundation", "-video_size", size, "-framerate",
                        Integer.toString(settings.fps()), "-i", settings.device() + ":none"));
            }
            default -> {
                if (!settings.device().matches("/dev/video[0-9]+")) {
                    throw new IllegalArgumentException("Expected a local /dev/video device");
                }
                command.addAll(List.of("-f", "v4l2", "-video_size", size, "-framerate",
                        Integer.toString(settings.fps()), "-i", settings.device()));
            }
        }
        // stdout is fixed-size RGB frames only. Audio and network inputs are never enabled.
        command.addAll(List.of("-an", "-sn", "-dn", "-vf", "scale=" + size.replace('x', ':')
                + ",fps=" + settings.fps(), "-threads", "1", "-pix_fmt", "rgb24", "-f", "rawvideo", "pipe:1"));
        return command;
    }

    static CameraCapture.Source open(HandCameraSettings settings) throws Exception {
        if (settings.remembersIdentity()) {
            // Indexes and enumeration order change between launches; look the camera up by its identity.
            settings = settings.withDevice(HandCameraDeviceResolver.resolve(settings, devices())
                    .orElseThrow(DeviceNotFoundException::new).value());
        }
        String executable = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
        Process process = start(new ProcessBuilder(command(executable, platform(), settings))
                .redirectError(ProcessBuilder.Redirect.DISCARD));
        var lastFrame = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
        var watchdog = REAPER.scheduleAtFixedRate(() -> {
            if (System.nanoTime() - lastFrame.get() > 10_000_000_000L) stop(process);
        }, 1, 1, TimeUnit.SECONDS);
        return new CameraCapture.Source() {
            public InputStream pixels() { return process.getInputStream(); }
            public void frameRead() { lastFrame.set(System.nanoTime()); }
            public void close() { watchdog.cancel(false); stop(process); }
            public void awaitStopped() throws Exception {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.toHandle().destroyForcibly();
                process.getInputStream().close();
                if (!process.isAlive()) CHILDREN.remove(process.toHandle());
            }
        };
    }

    static void stop(Process process) {
        // Process.destroy() also closes synchronized pipe streams and may wait for a blocked reader.
        // Signal through ProcessHandle here; only the reader worker closes its stream after exit.
        ProcessHandle handle = process.toHandle();
        handle.destroy();
        REAPER.schedule(() -> {
            if (handle.isAlive()) handle.destroyForcibly();
            if (!handle.isAlive()) CHILDREN.remove(handle);
        }, 250, TimeUnit.MILLISECONDS);
    }

    /** Called only by the operator's Refresh button, on a background thread. No preview capture. */
    public static List<Device> devices() throws Exception {
        String platform = platform();
        if (platform.equals("linux")) {
            try (var files = java.nio.file.Files.list(java.nio.file.Path.of("/dev"))) {
                return files.filter(path -> path.getFileName().toString().matches("video[0-9]+"))
                        .sorted().map(path -> new Device(path.toString(), path.toString())).toList();
            }
        }
        String executable = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
        Process process = start(new ProcessBuilder(executable, "-hide_banner", "-nostdin", "-f",
                platform.equals("windows") ? "dshow" : "avfoundation", "-list_devices", "true", "-i", "dummy")
                .redirectErrorStream(true));
        var timeout = REAPER.schedule(() -> stop(process), 5, TimeUnit.SECONDS);
        try (InputStream input = process.getInputStream()) {
            // Enumeration output is bounded and stays local; do not log device names.
            String output = new String(input.readNBytes(65536), StandardCharsets.UTF_8);
            return parseDevices(platform, output);
        } finally {
            timeout.cancel(false);
            stop(process);
        }
    }

    static List<Device> parseDevices(String platform, String output) {
        List<Device> devices = new ArrayList<>();
        boolean video = false;
        // FFmpeg 5+ prints `"Name" (video)` and, on the next line, `Alternative name "@device_..."`.
        Pattern windows = Pattern.compile("\\\"([^\\\"]+)\\\" \\(([a-z, ]+)\\)");
        Pattern alternative = Pattern.compile("Alternative name \\\"([^\\\"]+)\\\"");
        Pattern mac = Pattern.compile("\\] \\[(\\d+)\\] (.+)$");
        for (String line : output.split("\\R")) {
            if (platform.equals("windows")) {
                Matcher id = alternative.matcher(line);
                if (id.find()) {
                    if (video) {
                        Device last = devices.remove(devices.size() - 1);
                        devices.add(new Device(last.value(), last.label(), id.group(1)));
                    }
                    video = false;
                    continue;
                }
                Matcher match = windows.matcher(line);
                video = match.find() && List.of(match.group(2).split(", *")).contains("video");
                if (video) devices.add(new Device(match.group(1), match.group(1)));
            } else {
                if (line.contains("AVFoundation video devices:")) video = true;
                if (line.contains("AVFoundation audio devices:")) video = false;
                Matcher match = mac.matcher(line);
                if (video && match.find() && !match.group(2).startsWith("Capture screen ")) {
                    devices.add(new Device(match.group(1), match.group(2)));
                }
            }
        }
        if (platform.equals("windows")) {
            // DirectShow opens the first device with a matching name; address duplicates by their unique ID.
            List<Device> named = List.copyOf(devices);
            devices.replaceAll(device -> !device.id().isEmpty() && named.stream()
                    .filter(other -> other.label().equals(device.label())).count() > 1
                    ? new Device(device.id(), device.label(), device.id()) : device);
        }
        return List.copyOf(devices);
    }
}
