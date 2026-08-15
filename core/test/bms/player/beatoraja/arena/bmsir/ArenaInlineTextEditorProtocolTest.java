package bms.player.beatoraja.arena.bmsir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ArenaInlineTextEditorProtocolTest {
    @Test
    void requestRoundTripPreservesUnicodeAndGeometry() throws Exception {
        ArenaInlineTextEditorProtocol.Request request =
                new ArenaInlineTextEditorProtocol.Request(
                        1234,
                        "日本語🎵",
                        200,
                        40,
                        80,
                        640,
                        32
                );
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ArenaInlineTextEditorProtocol.writeRequest(
                new DataOutputStream(bytes),
                request
        );

        assertEquals(
                request,
                ArenaInlineTextEditorProtocol.readRequest(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))
                )
        );
    }

    @Test
    void acceptedTextRoundTripPreservesJapaneseInput() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ArenaInlineTextEditorProtocol.writeReady(new DataOutputStream(bytes));
        ArenaInlineTextEditorProtocol.writeAccept(
                new DataOutputStream(bytes),
                "変換済み🎵"
        );
        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray())
        );

        assertEquals(
                ArenaInlineTextEditorProtocol.SIGNAL_READY,
                ArenaInlineTextEditorProtocol.readSignal(input)
        );
        assertEquals(
                ArenaInlineTextEditorProtocol.SIGNAL_ACCEPT,
                ArenaInlineTextEditorProtocol.readSignal(input)
        );
        assertEquals(
                "変換済み🎵",
                ArenaInlineTextEditorProtocol.readAcceptedText(input)
        );
    }

    @Test
    void rejectsUnboundedWindowDimensions() throws Exception {
        ArenaInlineTextEditorProtocol.Request request =
                new ArenaInlineTextEditorProtocol.Request(
                        1234,
                        "",
                        200,
                        0,
                        0,
                        20_000,
                        32
                );
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ArenaInlineTextEditorProtocol.writeRequest(
                new DataOutputStream(bytes),
                request
        );

        assertThrows(
                IOException.class,
                () -> ArenaInlineTextEditorProtocol.readRequest(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))
                )
        );
    }
}
