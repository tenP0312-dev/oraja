package bms.player.beatoraja.config;

/** Coalesce edits and allow a complete frame to be presented before GL-bound loading. */
final class DeferredSkinPreview {
	private boolean pending;
	private long requestedFrame;

	void request(long frame) {
		pending = true;
		requestedFrame = frame;
	}

	void cancel() {
		pending = false;
	}

	boolean take(long frame) {
		if (!pending || frame - requestedFrame < 2) return false;
		pending = false;
		return true;
	}
}
