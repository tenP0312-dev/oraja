package bms.player.beatoraja.arena.bmsir;

import java.util.Locale;

/** Shared language selection for Arena-only JavaFX and ImGui surfaces. */
public final class BMSIRArenaI18n {
    private static volatile String language = "ja";

    private BMSIRArenaI18n() {
    }

    public static void setLanguage(String value) {
        language = "en".equalsIgnoreCase(value) ? "en" : "ja";
    }

    public static String language() {
        return language;
    }

    public static boolean isEnglish() {
        return "en".equals(language);
    }

    public static String text(String japanese, String english) {
        return isEnglish() ? english : japanese;
    }

    public static String format(
            String japanese,
            String english,
            Object... arguments
    ) {
        return String.format(
                Locale.ROOT,
                text(japanese, english),
                arguments
        );
    }
}
