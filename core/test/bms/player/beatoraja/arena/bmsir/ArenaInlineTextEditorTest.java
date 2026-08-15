package bms.player.beatoraja.arena.bmsir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import imgui.type.ImString;
import java.util.List;
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

    @Test
    void detectsMacOsWithoutDependingOnDefaultLocale() {
        assertTrue(ArenaInlineTextEditor.isMacOs("Mac OS X"));
        assertFalse(ArenaInlineTextEditor.isMacOs("Windows 11"));
        assertFalse(ArenaInlineTextEditor.isMacOs(null));
    }

    @Test
    void buildsHelperCommandFromCurrentRuntimeInputs() {
        assertEquals(
                List.of(
                        "/runtime/bin/java",
                        "-Dapple.awt.UIElement=true",
                        "-cp",
                        "Arena-oraja.jar:lib/*",
                        ArenaInlineTextEditorHelper.class.getName()
                ),
                ArenaInlineTextEditor.helperCommand(
                        "/runtime",
                        "Arena-oraja.jar:lib/*"
                )
        );
    }

    @Test
    void sessionCompletionWinsOverLateReadySignal() {
        ArenaInlineTextEditor.Session session =
                new ArenaInlineTextEditor.Session(new ImString(17), 4);

        assertTrue(session.tryComplete());
        assertFalse(session.markReady());
        assertFalse(session.tryComplete());
        assertTrue(session.isCompleted());
        assertFalse(session.isReady());
    }

    @Test
    void readySessionCanStillCompleteOnlyOnce() {
        ArenaInlineTextEditor.Session session =
                new ArenaInlineTextEditor.Session(new ImString(17), 4);

        assertTrue(session.markReady());
        assertTrue(session.isReady());
        assertTrue(session.tryComplete());
        assertFalse(session.tryComplete());
    }
}
