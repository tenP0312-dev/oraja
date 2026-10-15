package bms.player.beatoraja.skin;

import bms.player.beatoraja.MainState;
import bms.player.beatoraja.camera.CameraCrop;
import bms.player.beatoraja.camera.HandCameraView;
import bms.player.beatoraja.skin.scene.FrameDrivenSkinObject;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** A camera is a destination-controlled skin object, never a skin-selected device or network source. */
public final class SkinCamera extends SkinObject implements FrameDrivenSkinObject {
    private final CameraCrop crop;
    private final TextureRegion image = new TextureRegion();
    private boolean mirror, flipVertical;
    private HandCameraView view = HandCameraView.DEFAULT;

    public SkinCamera(CameraCrop crop) {
        this.crop = crop;
        setStretch(StretchType.KEEP_ASPECT_RATIO_FIT_INNER);
    }

    @Override public boolean validate() { return crop.valid() && super.validate(); }

    @Override public void prepareFrame(long time, long nowMicros, MainState state) {
        super.prepare(time, state);
        if (!draw || !visible || color.a <= 0 || region.width <= 0 || region.height <= 0
                || state.main.getCurrentState() != state || state.main.getHandCameraManager() == null) {
            draw = false;
            return;
        }
        Texture texture = state.main.getHandCameraManager().requestTexture();
        if (texture == null) { draw = false; return; }
        mirror = state.main.getConfig().isHandCameraMirror();
        flipVertical = state.main.getConfig().isHandCameraFlipVertical();
        view = HandCameraView.from(state.main.getConfig());
        int[] source = view.source(crop.pixels(texture.getWidth(), texture.getHeight()), texture.getWidth(),
                texture.getHeight(), mirror, flipVertical);
        image.setTexture(texture);
        image.setRegion(source[0], source[1], source[2], source[3]);
    }

    @Override public void draw(Skin.SkinObjectRenderer sprite) {
        // The skin's camera frame stays fixed; a shifted image is cut off at its edges.
        drawClipped(sprite, image, region.x + view.dx(region.width), region.y + view.dy(region.height),
                region.width, region.height, region);
    }

    @Override protected void transformDrawImage(TextureRegion image) {
        if (mirror || flipVertical) image.flip(mirror, flipVertical);
    }

    @Override public void dispose() { setDisposed(); }
}
