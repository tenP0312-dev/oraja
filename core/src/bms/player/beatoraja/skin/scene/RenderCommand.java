package bms.player.beatoraja.skin.scene;

import bms.player.beatoraja.skin.scene.render.BlendMode;

/** One allocation-free, depth-ordered scene draw command. */
public final class RenderCommand {
	public int textureIndex;
	public int meshIndex;
	public BlendMode blend = BlendMode.NORMAL;
	public boolean premultipliedAlpha;
	public final float[] world = new float[16];
	public final float[] colorMul = new float[4];
	public final float[] colorAdd = new float[4];
	public final float[] hsl = new float[3];
	public final float[] uvRect = new float[4];
	public int projection;
	/** camera center xyz followed by focal length. */
	public final float[] camera = new float[4];
	public int maskCount;
	public final float[][] maskWorld;
	public final float[][] maskRect;
	public final int[] maskProjection;
	public final float[][] maskCamera;

	RenderCommand(int maxMasks) {
		maskWorld = new float[maxMasks][16];
		maskRect = new float[maxMasks][4];
		maskProjection = new int[maxMasks];
		maskCamera = new float[maxMasks][4];
	}
}
