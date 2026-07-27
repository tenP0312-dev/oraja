package bms.player.beatoraja.play.bga;

import java.util.Objects;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the JavaCPP FFmpeg runtime before any movie decoder thread is started.
 */
public final class FFmpegNativeLoader {
	private static final Logger logger = LoggerFactory.getLogger(FFmpegNativeLoader.class);
	private static final NativePreloader PRELOADER =
			new NativePreloader(FFmpegFrameGrabber::tryLoad);

	private FFmpegNativeLoader() {
	}

	public static boolean preload() {
		return PRELOADER.preload();
	}

	enum State {
		NOT_STARTED,
		INITIALIZING,
		READY,
		FAILED,
	}

	@FunctionalInterface
	interface NativeLoadAction {
		void load() throws Throwable;
	}

	static final class NativePreloader {
		private final NativeLoadAction action;
		private State state = State.NOT_STARTED;
		private Throwable failure;

		NativePreloader(NativeLoadAction action) {
			this.action = Objects.requireNonNull(action);
		}

		boolean preload() {
			boolean restoreInterrupt = Thread.interrupted();
			synchronized (this) {
				while (state == State.INITIALIZING) {
					try {
						wait();
					} catch (InterruptedException e) {
						restoreInterrupt = true;
					}
				}
				if (state == State.READY || state == State.FAILED) {
					restoreInterrupt(restoreInterrupt);
					return state == State.READY;
				}
				state = State.INITIALIZING;
			}

			logger.info("FFmpeg native preload starting");
			boolean loaded = false;
			Throwable loadFailure = null;
			try {
				action.load();
				loaded = true;
				logger.info("FFmpeg native libraries preloaded");
			} catch (Throwable e) {
				loadFailure = e;
				logger.error("FFmpeg native preload failed", e);
			} finally {
				synchronized (this) {
					failure = loadFailure;
					state = loaded ? State.READY : State.FAILED;
					notifyAll();
				}
				restoreInterrupt(restoreInterrupt);
			}
			return loaded;
		}

		synchronized State state() {
			return state;
		}

		synchronized Throwable failure() {
			return failure;
		}

		private static void restoreInterrupt(boolean interrupted) {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
