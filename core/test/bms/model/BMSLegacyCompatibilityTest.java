package bms.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.Charset;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BMSLegacyCompatibilityTest {
    @TempDir Path directory;
    private BMSModel decode(String body, boolean legacy, int[] random) {
        BMSDecoder decoder = new BMSDecoder(); decoder.setLegacyCompatibility(legacy);
        return decoder.decode(("#PLAYER 1\n#TITLE Fixture\n#BPM 120\n#TOTAL 100\n" + body)
                .getBytes(Charset.forName("MS932")), false, random);
    }
    @Test void whitespaceChannelsKeepOriginalHashAndRestoreOldReplayInterpretation() {
        String body = "#WAV01 a.wav\n#00111 0101\n";
        BMSModel current = decode(body, false, null), old = decode(body, true, null);
        assertEquals(2, current.getTotalNotes()); assertEquals(3, old.getTotalNotes());
        assertEquals(current.getMD5(), old.getMD5()); assertEquals(current.getSHA256(), old.getSHA256());
        assertEquals("1", current.getValues().get(BMSDecoder.COMPATIBILITY_AFFECTED));
        assertEquals("1", old.getValues().get(BMSDecoder.COMPATIBILITY_REVISION));
        assertFalse(bms.player.beatoraja.BMSIRParserCompatibility.affected(old));
        BMSModel standard = decode(body.replace("11 ", "11:"), false, null);
        assertEquals(standard.toChartString(), current.toChartString());
    }
    @Test void selectedRandomBranchAndLateBase62ArePreserved() {
        String body = "#WAVA0 a.wav\n#WAVa0 b.wav\n#BASE 62\n#RANDOM 2\n#IF 1\n#00111 A0a0\n#ENDIF\n#IF 2\n#00111:A0\n#ENDIF\n#ENDRANDOM\n";
        BMSModel first = decode(body, false, new int[]{1});
        assertEquals(62, first.getBase()); assertEquals(2, first.getTotalNotes());
        assertEquals("1", first.getValues().get(BMSDecoder.COMPATIBILITY_AFFECTED));
        BMSModel second = decode(body, false, new int[]{2});
        assertEquals(1, second.getTotalNotes()); assertEquals("1", second.getValues().get(BMSDecoder.COMPATIBILITY_AFFECTED));
        assertEquals(first.getSHA256(), second.getSHA256());
    }
    @Test void conditionalLnobjUsesOneStorageNamespaceAcrossBranches() {
        String body = "#WAV01 a.wav\n#LNOBJ ZZ\n#00111:01\n#00211:ZZ\n#RANDOM 2\n#IF 1\n#00311:ZZ\n#ENDIF\n#ENDRANDOM\n";
        BMSModel first = decode(body, false, new int[]{1}), second = decode(body, false, new int[]{2});
        assertEquals(2, first.getTotalNotes()); assertEquals(1, second.getTotalNotes());
        assertEquals(bms.player.beatoraja.BMSIRParserCompatibility.storageHash(first, first.getSHA256()),
                bms.player.beatoraja.BMSIRParserCompatibility.storageHash(second, second.getSHA256()));
        assertNotEquals(first.getSHA256(), bms.player.beatoraja.BMSIRParserCompatibility.storageHash(first, first.getSHA256()));
    }
    @Test void unmatchedEndpointsAreNormalAndHistoricalInterpretationsRemainAvailable() {
        String body = "#WAV01 a.wav\n#WAVZZ end.wav\n#LNOBJ ZZ\n#00111:01\n#00211:ZZ\n#00311:ZZ\n";
        BMSModel current = decode(body, false, null), old = decode(body, true, null);
        assertEquals(2, current.getTotalNotes()); assertEquals(1, old.getTotalNotes());
        assertTrue(current.getAllTimeLines()[current.getAllTimeLines().length - 1].getNote(0) instanceof NormalNote);
        assertEquals(current.getSHA256(), old.getSHA256());
        assertEquals(1, decode(body.replace("#00311:ZZ\n", ""), false, null).getTotalNotes());
        assertEquals(1, decode("#LNOBJ ZZ\n#00111:ZZ\n", false, null).getTotalNotes());
        BMSDecoder v2 = new BMSDecoder(); v2.setCompatibilityRevision(2);
        assertEquals(0, v2.decode("#BPM 120\n#LNOBJ ZZ\n#00111:ZZ\n".getBytes(Charset.forName("MS932")), false, null).getTotalNotes());
    }
    @Test void bmp00IsInitialOnlyAndUsesTheDenseHeaderMapping() {
        BMSModel model = decode("#BMP05 later.png\n#BMP00 default.png\n#WAV01 a.wav\n#00111:01\n#00206:05\n#00311:01\n", false, null);
        assertEquals(1, model.getAllTimeLines()[0].getEventlayer()[0].sequence[0][0].id);
        TimeLine second = java.util.Arrays.stream(model.getAllTimeLines()).filter(t -> t.getSection() == 2).findFirst().orElseThrow();
        assertEquals(0, second.getEventlayer()[0].sequence[0][0].id);
        TimeLine third = java.util.Arrays.stream(model.getAllTimeLines()).filter(t -> t.getSection() == 3).findFirst().orElseThrow();
        assertEquals(0, third.getEventlayer().length);
        BMSModel explicit = decode("#BMP05 explicit.png\n#BMP00 default.png\n#00006:05\n", false, null);
        assertEquals(0, explicit.getAllTimeLines()[0].getEventlayer()[0].sequence[0][0].id);
    }
    @Test void standardBmsonLayerIsPersistentAndIndependentFromPoorAtTheSamePulse() throws Exception {
        Path path = directory.resolve("fixture.bmson");
        Files.writeString(path, """
          {"version":"1.0.0","info":{"title":"Fixture","artist":"","mode_hint":"beat-7k","init_bpm":120,"judge_rank":100,"total":100,"resolution":240},
           "sound_channels":[{"name":"a.wav","notes":[{"x":1,"y":960,"l":0,"c":false}]}],
           "bga":{"bga_header":[{"id":7,"name":"main.png"},{"id":99,"name":"layer.png"}],
                  "bga_events":[{"y":0,"id":7}],"layer_events":[{"y":0,"id":99},{"y":960,"id":7}],
                  "poor_events":[{"y":0,"id":7}]}}
          """);
        BMSModel model = new BMSONDecoder(0).decode(path);
        assertNotNull(model); assertEquals("1", model.getValues().get(BMSONDecoder.FORMAT_MARKER));
        TimeLine first = model.getAllTimeLines()[0];
        assertEquals(0, first.getBGA()); assertEquals(1, first.getLayer());
        assertEquals(1, first.getEventlayer().length); assertEquals(Layer.EventType.MISS, first.getEventlayer()[0].event.type);
        assertEquals(0, model.getAllTimeLines()[model.getAllTimeLines().length - 1].getLayer());
    }
}
