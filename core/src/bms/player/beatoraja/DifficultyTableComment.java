package bms.player.beatoraja;

/** Normalizes optional per-chart difficulty-table comments for display. */
public final class DifficultyTableComment {
    public static final String BREAK_MARKER = "[[BR]]";
    public static final int MAX_LENGTH = 4096;
    public static final int MAX_EDITOR_LENGTH = MAX_LENGTH * BREAK_MARKER.length();

    private DifficultyTableComment() {
    }

    public static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String normalized = value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(BREAK_MARKER, "\n");
        StringBuilder result = new StringBuilder(Math.min(normalized.length(), MAX_LENGTH));
        int acceptedCodePoints = 0;
        for (int index = 0;
                index < normalized.length() && acceptedCodePoints < MAX_LENGTH;
                index += Character.charCount(normalized.codePointAt(index))) {
            int codePoint = normalized.codePointAt(index);
            if (codePoint == '\n' || (codePoint >= ' ' && codePoint != 0x7f)) {
                result.appendCodePoint(codePoint);
                acceptedCodePoints++;
            }
        }
        return result.toString().strip();
    }

    public static String toEditorText(String value) {
        return normalize(value).replace("\n", BREAK_MARKER);
    }
}
