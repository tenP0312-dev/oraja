package bms.player.beatoraja.play.bga;

import bms.player.beatoraja.Config;

/** Immutable normalized limits; zero-valued overrides use the selected profile. */
public record BgaQualityProfile(int fps, int maxWidth, int maxHeight, int queueLength,
        int preloadWindowMs, int preloadCount, int decoders, long memoryBytes,
        boolean dropLateFrames) {
    public enum Mode { OFF, PERFORMANCE, BALANCED, QUALITY }
    public static BgaQualityProfile from(Config config) {
        Mode mode = config.getBgaQualityMode();
        if (config.getBga() == Config.BGA_OFF || mode == Mode.OFF) {
            return new BgaQualityProfile(0, 0, 0, 0, 0, 0, 0, 0, true);
        }
        int fps = mode == Mode.PERFORMANCE ? 30 : 60;
        int width = mode == Mode.PERFORMANCE ? 1280 : mode == Mode.BALANCED ? 1920 : 0;
        int height = mode == Mode.PERFORMANCE ? 720 : mode == Mode.BALANCED ? 1080 : 0;
        int queue = mode == Mode.PERFORMANCE ? 2 : 3;
        int window = mode == Mode.PERFORMANCE ? 500 : mode == Mode.BALANCED ? 750 : 1250;
        int preloads = mode == Mode.PERFORMANCE ? 1 : 2;
        return new BgaQualityProfile(config.getBgaMaxFps() == 0 ? fps : config.getBgaMaxFps(),
                config.getBgaMaxWidth() == 0 ? width : config.getBgaMaxWidth(),
                config.getBgaMaxHeight() == 0 ? height : config.getBgaMaxHeight(),
                config.getBgaDecodedQueueLength() == 0 ? queue : config.getBgaDecodedQueueLength(),
                config.getBgaPreloadWindowMs() < 0 ? window : config.getBgaPreloadWindowMs(),
                config.getBgaPreloadResourceCount() < 0 ? preloads : config.getBgaPreloadResourceCount(),
                config.getBgaMaxActiveDecoders(), config.getBgaNativeMemoryBudgetMb() * 1024L * 1024L,
                config.isBgaDropLateFrames());
    }
    public int[] dimensions(int sourceWidth, int sourceHeight, int displayWidth, int displayHeight) {
        if (sourceWidth < 1 || sourceHeight < 1) throw new IllegalArgumentException("Invalid video dimensions");
        double scale = Math.min(1, Math.min((maxWidth == 0 ? sourceWidth : maxWidth) / (double) sourceWidth,
                (maxHeight == 0 ? sourceHeight : maxHeight) / (double) sourceHeight));
        if (displayWidth > 0 && displayHeight > 0) scale = Math.min(scale,
                Math.min(displayWidth / (double) sourceWidth, displayHeight / (double) sourceHeight));
        return new int[] { Math.max(1, (int) (sourceWidth * scale)), Math.max(1, (int) (sourceHeight * scale)) };
    }
}
