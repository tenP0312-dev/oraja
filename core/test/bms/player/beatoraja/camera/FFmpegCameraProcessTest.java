package bms.player.beatoraja.camera;

import java.io.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FFmpegCameraProcessTest {
    private final HandCameraSettings settings = new HandCameraSettings(true, "USB camera", 640, 480, 30, false);
    @Test void commandUsesAnArgumentListAndNeverCapturesAudio() {
        var command = FFmpegCameraProcess.command("/path with spaces/ffmpeg", "windows", settings);
        assertEquals("/path with spaces/ffmpeg", command.get(0));
        assertTrue(command.contains("video=USB camera"));
        assertTrue(command.contains("dshow"));
        assertTrue(command.containsAll(List.of("-nostdin", "-an", "rgb24", "rawvideo", "pipe:1")));
        var mac = FFmpegCameraProcess.command("ffmpeg", "macos", new HandCameraSettings(true, "0", 640, 480, 30, false));
        assertTrue(mac.contains("avfoundation"));
        assertTrue(mac.contains("0:none"));
        assertThrows(IllegalArgumentException.class, () -> FFmpegCameraProcess.command("ffmpeg", "linux", settings));
    }
    @Test void enumerationIgnoresMicrophonesAndAlternativeDshowNames() {
        assertEquals(List.of(new FFmpegCameraProcess.Device("USB Camera", "USB Camera", "@device_pnp_x")),
                FFmpegCameraProcess.parseDevices("windows", "[dshow @ x] \"USB Camera\" (video)\n"
                        + "[dshow @ x] Alternative name \"@device_pnp_x\"\n[dshow @ x] \"Microphone\" (audio)"));
        assertEquals(List.of(new FFmpegCameraProcess.Device("0", "FaceTime HD Camera")),
                FFmpegCameraProcess.parseDevices("macos", "[AVFoundation @ x] AVFoundation video devices:\n"
                        + "[AVFoundation @ x] [0] FaceTime HD Camera\n[AVFoundation @ x] [1] Capture screen 0\n"
                        + "[AVFoundation @ x] AVFoundation audio devices:\n"
                        + "[AVFoundation @ x] [0] Microphone"));
    }
    @Test void aDriverThatIgnoresNormalTerminationIsKilledWithoutWaitingOnTheCaller() throws Exception {
        AtomicBoolean alive = new AtomicBoolean(true);
        Process stubborn = new Process() {
            public ProcessHandle toHandle() {
                return (ProcessHandle) java.lang.reflect.Proxy.newProxyInstance(ProcessHandle.class.getClassLoader(),
                        new Class<?>[] {ProcessHandle.class}, (proxy, method, args) -> switch (method.getName()) {
                            case "isAlive" -> alive.get();
                            case "destroy" -> false;
                            case "destroyForcibly" -> { alive.set(false); yield true; }
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> throw new AssertionError("Unexpected process operation: " + method.getName());
                        });
            }
            public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
            public InputStream getInputStream() { return InputStream.nullInputStream(); }
            public InputStream getErrorStream() { return InputStream.nullInputStream(); }
            public int waitFor() { throw new AssertionError("Caller must not wait"); }
            public int exitValue() { return alive.get() ? 1 : 0; }
            public void destroy() { throw new AssertionError("Must signal without closing pipe streams on caller"); }
            public boolean isAlive() { return alive.get(); }
            public Process destroyForcibly() { alive.set(false); return this; }
        };
        long start = System.nanoTime();
        FFmpegCameraProcess.stop(stubborn);
        assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(100));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (alive.get() && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse(alive.get());
    }
}
