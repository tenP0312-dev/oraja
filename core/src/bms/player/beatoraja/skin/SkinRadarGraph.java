package bms.player.beatoraja.skin;

import bms.player.beatoraja.MainState;
import bms.player.beatoraja.skin.Skin.SkinObjectRenderer;
import bms.player.beatoraja.song.NotesRadar;
import bms.player.beatoraja.song.SongData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * スキンオブジェクト:譜面傾向レーダーグラフ
 *
 * 選曲中の楽曲の{@link NotesRadar}を、6軸(NOTES/PEAK/SCRATCH/SOFLAN/CHARGE/CHORD)の
 * 六角形として塗りつぶし描画する。
 */
public class SkinRadarGraph extends SkinObject {

	private final Color fillColor;

	private MainState state;
	private SongData current;
	private TextureRegion shapetex;

	public SkinRadarGraph(String color) {
		Color parsed = Color.RED;
		if (color != null && color.length() > 0) {
			try {
				parsed = Color.valueOf(color);
			} catch (Exception e) {
			}
		}
		fillColor = new Color(parsed);
		fillColor.a = 0.5f;
	}

	@Override
	public void prepare(long time, MainState state) {
		this.state = state;
		super.prepare(time, state);
	}

	@Override
	public void draw(SkinObjectRenderer sprite) {
		if (state == null) {
			return;
		}
		SongData song = state.resource.getSongdata();
		if (song != current || shapetex == null) {
			current = song;
			updateTexture();
		}
		if (shapetex != null) {
			draw(sprite, shapetex, region.x, region.y, region.width, region.height);
		}
	}

	private void updateTexture() {
		if (shapetex != null) {
			shapetex.getTexture().dispose();
			shapetex = null;
		}
		if (current == null || current.getNotesRadar() == null) {
			return;
		}
		int width = (int) Math.abs(region.width);
		int height = (int) Math.abs(region.height);
		if (width <= 0 || height <= 0) {
			return;
		}

		NotesRadar radar = current.getNotesRadar();
		Pixmap pixmap = new Pixmap(width, height, Format.RGBA8888);
		pixmap.setBlending(Blending.None);
		pixmap.setColor(0f, 0f, 0f, 0f);
		pixmap.fill();

		int centerX = width / 2;
		int centerY = height / 2;
		int radius = Math.min(width, height) / 2 - 2;

		// 軸の並びはNOTES/PEAK/SCRATCH/SOFLAN/CHARGE/CHORDの60度刻み
		double[] axisValues = {
				radar.notes / 200.0f,
				radar.peak / 200.0f,
				radar.scratch / 200.0f,
				radar.soflan / 200.0f,
				radar.charge / 200.0f,
				radar.chord / 200.0f,
		};
		int[] vertexX = new int[6];
		int[] vertexY = new int[6];
		for (int i = 0; i < 6; i++) {
			double angle = Math.toRadians(i * 60 - 90);
			double rate = Math.min(Math.max(axisValues[i], 0.0f), 1.0f);
			int r = (int) (radius * rate);
			vertexX[i] = centerX + (int) (r * Math.cos(angle));
			vertexY[i] = centerY + (int) (r * Math.sin(angle));
		}

		pixmap.setBlending(Blending.None);
		pixmap.setColor(fillColor);
		for (int i = 0; i < 6; i++) {
			int next = (i + 1) % 6;
			pixmap.fillTriangle(centerX, centerY, vertexX[i], vertexY[i], vertexX[next], vertexY[next]);
		}

		shapetex = new TextureRegion(new Texture(pixmap));
		pixmap.dispose();
	}

	@Override
	public void dispose() {
		if (shapetex != null) {
			shapetex.getTexture().dispose();
		}
	}
}
