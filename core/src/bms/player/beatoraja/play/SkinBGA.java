package bms.player.beatoraja.play;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.MainState;
import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.skin.Skin.SkinObjectRenderer;
import bms.player.beatoraja.camera.HandCameraView;
import bms.player.beatoraja.skin.SkinCamera;
import bms.player.beatoraja.skin.SkinObject;
import bms.player.beatoraja.skin.StretchType;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static bms.player.beatoraja.skin.SkinProperty.*;

/**
 * BGAオブジェクト
 * 
 * @author exch
 */
public class SkinBGA extends SkinObject {
	
	private BMSPlayer player;
	private long time;
	private final TextureRegion handCamera = new TextureRegion();
	private boolean mirrorHandCamera, flipHandCameraVertical;
	/** Whether the skin places the hand camera itself; resolved on first draw. */
	private Boolean skinHasCamera;

	public SkinBGA(int bgaExpand) {
		switch (bgaExpand) {
		case Config.BGAEXPAND_FULL:
			setStretch(StretchType.STRETCH);
			break;
		case Config.BGAEXPAND_KEEP_ASPECT_RATIO:
			setStretch(StretchType.KEEP_ASPECT_RATIO_FIT_INNER);
			break;
		case Config.BGAEXPAND_OFF:
			setStretch(StretchType.KEEP_ASPECT_RATIO_NO_EXPANDING);
			break;
		}
	}
	
	@Override
	public void prepare(long time, MainState state) {
		if(player == null) {
			player = (BMSPlayer)state;
		}
		this.time = time;
		super.prepare(time, state);
		if(draw) {
			final int s = player.getState();
			player.resource.getBGAManager().prepareBGA(
					s == BMSPlayer.STATE_PRELOAD || s == BMSPlayer.STATE_PRACTICE || s == BMSPlayer.STATE_READY ? -1
							: player.timer.getNowTime(TIMER_PLAY));
		}
	}

	public void draw(SkinObjectRenderer sprite) {
		final PlayerResource resource = player.resource;
		if (resource.getPlayMode().mode == BMSPlayerMode.Mode.PRACTICE) {
			player.getPracticeConfiguration().draw(region, sprite, time, player);
		} else if (!drawHandCamera(sprite) && resource.getBGAManager() != null) {
			resource.getBGAManager().drawBGA(this,sprite,region);
		}		
	}

	/**
	 * Hand camera in the BGA frame for skins without a camera part. Requested from draw, which runs every
	 * frame while the frame is shown; the BGA keeps running underneath and is shown while no frame is available.
	 */
	private boolean drawHandCamera(SkinObjectRenderer sprite) {
		if (skinHasCamera == null) {
			skinHasCamera = player.getSkin() != null && hasCameraPart(player.getSkin().getAllSkinObjects());
		}
		// A skin with its own camera part already places the camera; showing it here too would duplicate it.
		if (skinHasCamera) {
			return false;
		}
		final var manager = player.main.getHandCameraManager();
		final Texture texture = manager == null ? null : manager.requestBgaFrameTexture();
		if (texture == null) {
			return false;
		}
		mirrorHandCamera = player.main.getConfig().isHandCameraMirror();
		flipHandCameraVertical = player.main.getConfig().isHandCameraFlipVertical();
		final HandCameraView view = HandCameraView.from(player.main.getConfig());
		final int[] source = view.source(new int[] {0, 0, texture.getWidth(), texture.getHeight()}, texture.getWidth(),
				texture.getHeight(), mirrorHandCamera, flipHandCameraVertical);
		handCamera.setTexture(texture);
		handCamera.setRegion(source[0], source[1], source[2], source[3]);
		// The BGA frame stays fixed; a shifted camera image is cut off at its edges.
		drawClipped(sprite, handCamera, region.x + view.dx(region.width), region.y + view.dy(region.height),
				region.width, region.height, region);
		return true;
	}

	static boolean hasCameraPart(SkinObject[] objects) {
		if (objects == null) return false;
		for (SkinObject object : objects) {
			if (object instanceof SkinCamera) return true;
		}
		return false;
	}

	@Override
	protected void transformDrawImage(TextureRegion image) {
		// Only the hand camera is drawn through SkinObject.draw; the BGA processor draws directly.
		if (mirrorHandCamera || flipHandCameraVertical) image.flip(mirrorHandCamera, flipHandCameraVertical);
	}

	@Override
	public void dispose() {

	}
}
