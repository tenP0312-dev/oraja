package bms.player.beatoraja.nonstop;

import bms.player.beatoraja.TableData;
import bms.player.beatoraja.song.SongData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One difficulty axis for the generator (Issue #552). Normal charts ☆1–☆12
 * sit at 1–12 and insane charts ★1–★25 at 13–37, so ☆12 is just below ★1.
 * A chart's place comes from the player's difficulty tables first; charts on
 * no table are estimated from their note density, and #PLAYLEVEL is only the
 * last resort (insane charts say 12 or 99 alike).
 */
public final class MixDifficulty {

    /** Highest normal level; insane ★n sits at {@code NORMAL_MAX + n}. */
    public static final int NORMAL_MAX = 12;
    /** Highest insane level shown by the generator. */
    public static final int INSANE_MAX = 25;

    public enum Scale {
        /** LV1–LV12 (☆). */
        NORMAL("LV", 1, NORMAL_MAX),
        /** ★1–★25. */
        INSANE("★", 1, INSANE_MAX);

        public final String prefix;
        public final int min;
        public final int max;

        Scale(String prefix, int min, int max) {
            this.prefix = prefix;
            this.min = min;
            this.max = max;
        }

        /** Position of level {@code n} of this scale on the axis. */
        public double axis(int n) {
            return this == NORMAL ? n : NORMAL_MAX + n;
        }

        /**
         * Lower bound on the axis for a range starting at {@code n}. The lowest
         * step is open downwards (LV0, ★0 and ★? are kept).
         */
        public double lower(int n) {
            if (n <= min) {
                return this == NORMAL ? Double.NEGATIVE_INFINITY : NORMAL_MAX + 0.01;
            }
            return axis(n) - 0.5;
        }

        /** Upper bound for a range ending at {@code n}; ★25 is open upwards (★★ charts). */
        public double upper(int n) {
            if (this == INSANE && n >= max) {
                return Double.POSITIVE_INFINITY;
            }
            // LV12 ends below ★0
            return this == NORMAL ? Math.min(NORMAL_MAX, axis(n) + 0.49) : axis(n) + 0.49;
        }
    }

    // average notes per second -> axis; a rough fit of typical ☆/★ charts
    private static final double[][] DENSITY_KNOTS = {
            {1.5, 1}, {3.0, 3}, {5.0, 6}, {7.0, 9}, {8.5, 11}, {9.5, 12},
            {10.5, 13}, {12.5, 17}, {15.0, 22}, {17.5, 27}, {20.5, 32}, {24.0, 37}};
    /** Typical ratio of the busiest second to the average. */
    private static final double TYPICAL_PEAK_RATIO = 1.7;

    private MixDifficulty() {
    }

    /** "☆5" / "★3" style label of an axis value. */
    public static String label(double axis) {
        if (Double.isNaN(axis)) {
            return "?";
        }
        long rounded = Math.round(axis);
        if (axis <= NORMAL_MAX + 0.5) {
            return "☆" + Math.max(0, rounded);
        }
        return "★" + Math.max(0, rounded - NORMAL_MAX);
    }

    /**
     * Axis value of a table folder, or NaN when the table is not a ☆/★ style
     * table. The symbol comes from the folder name ("★12"), otherwise from the
     * table's tag.
     */
    public static double fromTable(String folderName, String tableTag) {
        String symbol = symbol(folderName);
        if (symbol.isEmpty()) {
            symbol = symbol(tableTag);
        }
        Integer number = TableData.parseDisplayLevel(folderName);
        boolean insane = symbol.contains("★") || symbol.contains("▼");
        boolean normal = !insane && (symbol.contains("☆") || symbol.contains("▽"));
        if (!insane && !normal) {
            return Double.NaN;
        }
        if (number == null) {
            // ★? and similar unrated folders
            return insane ? NORMAL_MAX + 0.5 : Double.NaN;
        }
        if (normal) {
            return Math.min(number, NORMAL_MAX);
        }
        if (symbol.contains("★★")) {
            // overjoy ★★n starts around the top of the insane table
            return NORMAL_MAX + INSANE_MAX + number;
        }
        return number == 0 ? NORMAL_MAX + 0.5 : NORMAL_MAX + number;
    }

    private static String symbol(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder symbol = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '★' || c == '☆' || c == '▼' || c == '▽') {
                symbol.append(c);
            } else if (c >= '0' && c <= '9') {
                break;
            }
        }
        return symbol.toString();
    }

    /**
     * Estimate for a chart on no table, from its average and peak density
     * (notes in the busiest second, 0 when unknown); #PLAYLEVEL when the
     * length is unknown too.
     */
    public static double estimate(int notes, long lengthMs, double peakDensity, int playLevel) {
        if (notes <= 0 || lengthMs < 10_000L) {
            return playLevel <= NORMAL_MAX ? Math.max(0, playLevel) : NORMAL_MAX + Math.min(playLevel - NORMAL_MAX, INSANE_MAX);
        }
        double average = notes / (lengthMs / 1000.0);
        double busy = average;
        if (peakDensity > 0) {
            // a chart with spikes plays harder than its average says, a flat one a little easier
            busy = 0.75 * average + 0.25 * (peakDensity / TYPICAL_PEAK_RATIO);
        }
        return interpolate(busy);
    }

    static double interpolate(double density) {
        double[][] k = DENSITY_KNOTS;
        if (density <= k[0][0]) {
            return Math.max(0, k[0][1] * density / k[0][0]);
        }
        for (int i = 1; i < k.length; i++) {
            if (density <= k[i][0]) {
                double t = (density - k[i - 1][0]) / (k[i][0] - k[i - 1][0]);
                return k[i - 1][1] + t * (k[i][1] - k[i - 1][1]);
            }
        }
        double[] a = k[k.length - 2];
        double[] b = k[k.length - 1];
        double slope = (b[1] - a[1]) / (b[0] - a[0]);
        return Math.min(NORMAL_MAX + INSANE_MAX + 10, b[1] + (density - b[0]) * slope);
    }

    /**
     * Table places by chart hash (sha256 and md5, lower case). A chart on
     * several tables keeps the ☆/★ place over a ▽/▼ one, then the first table.
     */
    public static Map<String, Double> index(TableData[] tables) {
        Map<String, Double> places = new HashMap<>();
        Map<String, Integer> priority = new HashMap<>();
        if (tables == null) {
            return places;
        }
        for (TableData table : tables) {
            if (table == null || table.getFolder() == null) {
                continue;
            }
            for (TableData.TableFolder folder : table.getFolder()) {
                if (folder == null || folder.getSong() == null) {
                    continue;
                }
                double axis = fromTable(folder.getName(), table.getTag());
                if (Double.isNaN(axis)) {
                    continue;
                }
                String symbol = symbol(folder.getName()).isEmpty() ? symbol(table.getTag()) : symbol(folder.getName());
                int rank = symbol.contains("★") || symbol.contains("☆") ? 0 : 1;
                for (SongData song : folder.getSong()) {
                    if (song == null) {
                        continue;
                    }
                    put(places, priority, song.getSha256(), axis, rank);
                    put(places, priority, song.getMd5(), axis, rank);
                }
            }
        }
        return places;
    }

    private static void put(Map<String, Double> places, Map<String, Integer> priority, String hash, double axis, int rank) {
        if (hash == null || hash.isEmpty()) {
            return;
        }
        String key = hash.toLowerCase(Locale.ROOT);
        Integer old = priority.get(key);
        if (old == null || rank < old) {
            places.put(key, axis);
            priority.put(key, rank);
        }
    }

    /** Table place of a chart, or NaN. */
    public static double lookup(Map<String, Double> places, String sha256, String md5) {
        Double axis = null;
        if (sha256 != null && !sha256.isEmpty()) {
            axis = places.get(sha256.toLowerCase(Locale.ROOT));
        }
        if (axis == null && md5 != null && !md5.isEmpty()) {
            axis = places.get(md5.toLowerCase(Locale.ROOT));
        }
        return axis == null ? Double.NaN : axis;
    }
}
