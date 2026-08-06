package bms.player.beatoraja;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.Matrix4;

import java.util.ArrayList;
import java.util.List;

final class StartupProgressRenderer {
    private enum Status {
        WAITING,
        RUNNING,
        OK,
        SKIP,
        ERROR
    }

    private static final class Entry {
        private final String label;
        private Status status;
        private String detail;

        private Entry(String label, Status status, String detail) {
            this.label = label;
            this.status = status;
            this.detail = detail;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final SpriteBatch batch;
    private final FreeTypeFontGenerator generator;
    private final BitmapFont titleFont;
    private final BitmapFont bodyFont;
    private int width;
    private int height;

    StartupProgressRenderer(Config config) {
        width = Gdx.graphics.getWidth();
        height = Gdx.graphics.getHeight();
        batch = SpriteBatchHelper.createSpriteBatch();
        generator = new FreeTypeFontGenerator(
                Gdx.files.internal(config.getSystemfontpath())
        );
        titleFont = createFont(Math.max(22, height / 24));
        bodyFont = createFont(Math.max(16, height / 38));
        resize(width, height);
    }

    private BitmapFont createFont(int size) {
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = size;
        parameter.incremental = true;
        parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS;
        return generator.generateFont(parameter);
    }

    int addCompleted(String label, String detail) {
        entries.add(new Entry(label, Status.OK, detail));
        return entries.size() - 1;
    }

    int addWaiting(String label) {
        entries.add(new Entry(label, Status.WAITING, ""));
        return entries.size() - 1;
    }

    void running(int index) {
        update(index, Status.RUNNING, "");
    }

    void complete(int index, StartupTask.Result result) {
        update(
                index,
                result.outcome() == StartupTask.Outcome.SKIP
                        ? Status.SKIP
                        : Status.OK,
                result.detail()
        );
    }

    void error(int index, String reason) {
        update(index, Status.ERROR, reason);
    }

    private void update(int index, Status status, String detail) {
        Entry entry = entries.get(index);
        entry.status = status;
        entry.detail = detail == null ? "" : detail;
    }

    void render() {
        Gdx.gl.glClearColor(0.025f, 0.03f, 0.035f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();

        titleFont.setColor(Color.WHITE);
        titleFont.draw(
                batch,
                Version.getArenaDisplayName(),
                42,
                height - 38
        );
        bodyFont.setColor(new Color(0.7f, 0.75f, 0.8f, 1f));
        bodyFont.draw(batch, "起動処理", 44, height - 84);

        int lineHeight = Math.max(25, bodyFont.getLineHeight() > 0
                ? (int) bodyFont.getLineHeight() + 7
                : 28);
        int available = Math.max(1, (height - 130) / lineHeight);
        int first = Math.max(0, entries.size() - available);
        float statusX = Math.max(280, width * 0.48f);
        float y = height - 122;
        for (int index = first; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            bodyFont.setColor(Color.WHITE);
            bodyFont.draw(batch, entry.label, 48, y);
            bodyFont.setColor(statusColor(entry.status));
            bodyFont.draw(batch, statusText(entry), statusX, y);
            y -= lineHeight;
        }
        batch.end();
    }

    private static Color statusColor(Status status) {
        return switch (status) {
            case WAITING -> new Color(0.5f, 0.55f, 0.6f, 1f);
            case RUNNING -> new Color(0.35f, 0.8f, 1f, 1f);
            case OK -> new Color(0.35f, 0.9f, 0.55f, 1f);
            case SKIP -> new Color(1f, 0.78f, 0.25f, 1f);
            case ERROR -> new Color(1f, 0.35f, 0.35f, 1f);
        };
    }

    private static String statusText(Entry entry) {
        String prefix = switch (entry.status) {
            case WAITING -> "待機中";
            case RUNNING -> "処理中...";
            case OK -> "OK";
            case SKIP -> "SKIP";
            case ERROR -> "ERROR";
        };
        return entry.detail.isBlank() ? prefix : prefix + ": " + entry.detail;
    }

    void resize(int width, int height) {
        this.width = width;
        this.height = height;
        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, height));
    }

    void dispose() {
        titleFont.dispose();
        bodyFont.dispose();
        generator.dispose();
        batch.dispose();
    }
}
