package bms.player.beatoraja.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Keeps macOS display and idle system sleep away while the game window runs.
 *
 * <p>Controller input does not reset the macOS user-idle timer, so a session
 * played only on a controller hits the display sleep timeout. The helper
 * {@code caffeinate} holds the power assertions and, through {@code -w},
 * releases them by itself when this process exits or crashes.</p>
 */
public final class MacSleepInhibitor {
	private static final Logger logger = LoggerFactory.getLogger(MacSleepInhibitor.class);
	static final Path CAFFEINATE = Path.of("/usr/bin/caffeinate");

	private static Process process;

	private MacSleepInhibitor() {
	}

	static boolean isMac(String osName) {
		return osName != null && osName.toLowerCase(Locale.ROOT).contains("mac");
	}

	static List<String> command(long pid) {
		return List.of(CAFFEINATE.toString(), "-d", "-i", "-w", Long.toString(pid));
	}

	public static synchronized void start() {
		if (process != null || !isMac(System.getProperty("os.name")) || !Files.isExecutable(CAFFEINATE)) {
			return;
		}
		try {
			process = new ProcessBuilder(command(ProcessHandle.current().pid()))
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
			logger.info("macOS sleep inhibitor started");
		} catch (Exception e) {
			logger.warn("macOS sleep inhibitor could not start: {}", e.getMessage());
		}
	}

	public static synchronized void stop() {
		if (process != null) {
			process.destroy();
			process = null;
		}
	}
}
