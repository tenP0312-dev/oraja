package bms.player.beatoraja.arena.bmsir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Bounded binary protocol between the Arena body and the macOS IME helper. */
final class ArenaInlineTextEditorProtocol {
    static final int SIGNAL_READY = 1;
    static final int SIGNAL_ACCEPT = 2;
    static final int SIGNAL_CANCEL = 3;
    static final int SIGNAL_ERROR = 4;

    private static final int MAGIC = 0x424d5349;
    private static final int VERSION = 1;
    private static final int MAX_CODE_POINTS = 10_000;
    private static final int MAX_DIMENSION = 16_384;

    private ArenaInlineTextEditorProtocol() {
    }

    static void writeRequest(DataOutputStream output, Request request) throws IOException {
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeLong(request.parentPid());
        output.writeUTF(request.initialValue());
        output.writeInt(request.maxCodePoints());
        output.writeInt(request.x());
        output.writeInt(request.y());
        output.writeInt(request.width());
        output.writeInt(request.height());
    }

    static Request readRequest(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) {
            throw new IOException("Invalid IME helper protocol magic");
        }
        if (input.readInt() != VERSION) {
            throw new IOException("Unsupported IME helper protocol version");
        }
        Request request = new Request(
                input.readLong(),
                input.readUTF(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt()
        );
        validate(request);
        return request;
    }

    static void writeReady(DataOutputStream output) throws IOException {
        output.writeByte(SIGNAL_READY);
        output.flush();
    }

    static void writeAccept(DataOutputStream output, String value) throws IOException {
        output.writeByte(SIGNAL_ACCEPT);
        output.writeUTF(value == null ? "" : value);
        output.flush();
    }

    static void writeCancel(DataOutputStream output) throws IOException {
        output.writeByte(SIGNAL_CANCEL);
        output.flush();
    }

    static void writeError(DataOutputStream output) throws IOException {
        output.writeByte(SIGNAL_ERROR);
        output.flush();
    }

    static int readSignal(DataInputStream input) throws IOException {
        int signal = input.readUnsignedByte();
        if (signal < SIGNAL_READY || signal > SIGNAL_ERROR) {
            throw new IOException("Invalid IME helper signal");
        }
        return signal;
    }

    static String readAcceptedText(DataInputStream input) throws IOException {
        String value = input.readUTF();
        if (value.codePointCount(0, value.length()) > MAX_CODE_POINTS) {
            throw new IOException("IME helper result is too long");
        }
        return value;
    }

    private static void validate(Request request) throws IOException {
        if (request.parentPid() <= 0) {
            throw new IOException("Invalid IME helper parent PID");
        }
        if (request.maxCodePoints() < 0 || request.maxCodePoints() > MAX_CODE_POINTS) {
            throw new IOException("Invalid IME helper character limit");
        }
        if (
                request.width() < 24
                        || request.height() < 16
                        || request.width() > MAX_DIMENSION
                        || request.height() > MAX_DIMENSION
        ) {
            throw new IOException("Invalid IME helper dimensions");
        }
        if (request.initialValue().codePointCount(0, request.initialValue().length()) > MAX_CODE_POINTS) {
            throw new IOException("IME helper initial value is too long");
        }
    }

    record Request(
            long parentPid,
            String initialValue,
            int maxCodePoints,
            int x,
            int y,
            int width,
            int height
    ) {
        Request {
            initialValue = initialValue == null ? "" : initialValue;
        }
    }
}
