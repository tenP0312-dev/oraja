package bms.player.beatoraja.skin.scene;

/** A contextual, user-facing scene validation failure. */
public final class SceneValidationException extends Exception {
	public SceneValidationException(String message) {
		super(message);
	}

	public SceneValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}
