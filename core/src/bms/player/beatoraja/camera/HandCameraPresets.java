package bms.player.beatoraja.camera;

import java.util.ArrayList;
import java.util.List;

/** Choices offered by the in-game menu. The current value is always listed, so a launcher value is kept. */
public final class HandCameraPresets {
    private HandCameraPresets() { }

    public record Size(int width, int height) {
        @Override public String toString() { return width + "x" + height; }
    }

    static final List<Size> SIZES = List.of(new Size(320, 240), new Size(640, 360), new Size(640, 480),
            new Size(800, 600), new Size(1280, 720), new Size(1920, 1080));
    static final List<Integer> FPS = List.of(15, 24, 30, 60);

    public static List<Size> sizes(int width, int height) {
        List<Size> sizes = new ArrayList<>(SIZES);
        Size current = new Size(width, height);
        if (!sizes.contains(current)) sizes.add(current);
        sizes.sort((a, b) -> a.width() != b.width() ? Integer.compare(a.width(), b.width())
                : Integer.compare(a.height(), b.height()));
        return List.copyOf(sizes);
    }

    public static List<Integer> fps(int current) {
        List<Integer> values = new ArrayList<>(FPS);
        if (!values.contains(current)) values.add(current);
        values.sort(null);
        return List.copyOf(values);
    }
}
