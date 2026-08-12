package bms.player.beatoraja.arena.bmsir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArenaInlineTextEditorTest {
    @Test
    void limitsJapaneseTextByUnicodeCodePoint() {
        assertEquals(
                "日本語入力",
                ArenaInlineTextEditor.limitCodePoints("日本語入力確認", 5)
        );
    }

    @Test
    void doesNotSplitSupplementaryCharacters() {
        assertEquals(
                "A🎵B",
                ArenaInlineTextEditor.limitCodePoints("A🎵BC", 3)
        );
    }
}
