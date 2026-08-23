package bms.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BMSDecoderBaseTest {
    private static final Charset MS932 = Charset.forName("MS932");

    @Test
    void ordinaryBase36StillTreatsUpperAndLowerCaseIdsAsEqual() {
        BMSModel model = decode(""
                + "#WAVA0 upper.wav\n"
                + "#WAVa0 lower.wav\n"
                + "#00111:A0a0\n");

        assertEquals(36, model.getBase());
        assertEquals(List.of(1, 1), playableWavIds(model));
    }

    @Test
    void earlyBase62KeepsUpperAndLowerCaseIdsDistinct() {
        BMSModel model = decode(""
                + "#BASE 62\n"
                + "#WAVA0 upper.wav\n"
                + "#WAVa0 lower.wav\n"
                + "#00111:A0a0\n");

        assertEquals(62, model.getBase());
        assertEquals(List.of(0, 1), playableWavIds(model));
    }

    @Test
    void lateBase62UsesTheSameWavMappingAsEarlyBase62() {
        BMSModel model = decode(""
                + "#WAVA0 upper.wav\n"
                + "#WAVa0 lower.wav\n"
                + "#BASE 62\n"
                + "#00111:A0a0\n");

        assertEquals(62, model.getBase());
        assertEquals(List.of(0, 1), playableWavIds(model));
    }

    @Test
    void lateBase62PreservesRandomBranchFilteringForDeferredHeaders() {
        byte[] chart = chart(""
                + "#RANDOM 2\n"
                + "#IF 1\n"
                + "#WAVA0 skipped.wav\n"
                + "#ENDIF\n"
                + "#IF 2\n"
                + "#WAVA0 selected.wav\n"
                + "#ENDIF\n"
                + "#ENDRANDOM\n"
                + "#BASE 62\n"
                + "#00111:A0\n");

        BMSModel model = new BMSDecoder().decode(chart, false, new int[]{2});

        assertEquals(List.of("selected.wav"), List.of(model.getWavList()));
        assertEquals(List.of(0), playableWavIds(model));
    }

    @Test
    void skippedBase62DoesNotChangeAnOrdinaryBase36Chart() {
        byte[] chart = chart(""
                + "#RANDOM 2\n"
                + "#IF 1\n"
                + "#BASE 62\n"
                + "#ENDIF\n"
                + "#ENDRANDOM\n"
                + "#WAVA0 upper.wav\n"
                + "#WAVa0 lower.wav\n"
                + "#00111:A0a0\n");

        BMSModel model = new BMSDecoder().decode(chart, false, new int[]{2});

        assertEquals(36, model.getBase());
        assertEquals(List.of(1, 1), playableWavIds(model));
    }

    @Test
    void lateBase62AlsoAppliesToLnobj() {
        BMSModel model = decode(""
                + "#WAVA0 hold.wav\n"
                + "#WAVa0 end.wav\n"
                + "#LNOBJ a0\n"
                + "#BASE 62\n"
                + "#00111:A0a0\n");

        assertEquals(ChartDecoder.parseInt62("a0", 0), model.getLnobj());
        assertInstanceOf(LongNote.class, firstPlayableNote(model));
    }

    private static BMSModel decode(String body) {
        return new BMSDecoder().decode(chart(body), false, null);
    }

    private static byte[] chart(String body) {
        return ("#PLAYER 1\n"
                + "#TITLE BASE TEST\n"
                + "#BPM 120\n"
                + "#TOTAL 100\n"
                + body).getBytes(MS932);
    }

    private static List<Integer> playableWavIds(BMSModel model) {
        List<Integer> ids = new ArrayList<>();
        for (TimeLine timeline : model.getAllTimeLines()) {
            Note note = timeline.getNote(0);
            if (note != null) {
                ids.add(note.getWav());
            }
        }
        return ids;
    }

    private static Note firstPlayableNote(BMSModel model) {
        for (TimeLine timeline : model.getAllTimeLines()) {
            Note note = timeline.getNote(0);
            if (note != null) {
                return note;
            }
        }
        throw new AssertionError("chart did not produce a playable note");
    }
}
