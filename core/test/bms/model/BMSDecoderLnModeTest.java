package bms.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSDecoderLnModeTest {
    @Test
    void authoredChargeNoteModeIsNotForcedBackToLongNoteMode() {
        byte[] chart = ("#TITLE LNMODE TEST\n"
                + "#BPM 120\n"
                + "#LNMODE 2\n"
                + "#WAV01 test.wav\n"
                + "#00111:0100\n").getBytes(Charset.forName("MS932"));

        BMSModel model = new BMSDecoder().decode(chart, false, null);

        assertEquals(2, model.getLnmode());
    }
}
