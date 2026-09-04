package bms.player.beatoraja.skin.scene;

import bms.player.beatoraja.MainState;

/** A skin object whose animation state must be evaluated for every render. */
public interface FrameDrivenSkinObject {
	/**
	 * @param stateTimeMillis state/preview time used by the normal destination
	 * @param nowMicros absolute render clock used by the scene timeline
	 */
	void prepareFrame(long stateTimeMillis, long nowMicros, MainState state);
}
