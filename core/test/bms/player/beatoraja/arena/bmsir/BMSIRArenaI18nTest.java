package bms.player.beatoraja.arena.bmsir;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

// BMSIRArenaI18n's selected language is a shared static field. Classes run
// concurrently by default (junit-platform.properties), so every test class
// that reads or writes it must share this lock or their assertions race.
@ResourceLock("bmsir-arena-i18n-language")
class BMSIRArenaI18nTest {
    @AfterEach
    void restoreJapaneseDefault() {
        BMSIRArenaI18n.setLanguage("ja");
    }

    @Test
    void defaultsUnknownValuesToJapaneseAndSwitchesToEnglish() {
        BMSIRArenaI18n.setLanguage("unknown");
        assertEquals("準備OK", BMSIRArenaI18n.text("準備OK", "Ready"));

        BMSIRArenaI18n.setLanguage("EN");
        assertEquals("Ready", BMSIRArenaI18n.text("準備OK", "Ready"));
        assertEquals("2 players", BMSIRArenaI18n.format("%d人", "%d players", 2));
    }
}
