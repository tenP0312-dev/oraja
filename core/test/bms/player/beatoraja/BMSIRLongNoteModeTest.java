package bms.player.beatoraja;

import bms.model.*;
import bms.player.beatoraja.ir.IRChartData;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BMSIRLongNoteModeTest {
    @TempDir Path directory;

    @Test
    void everyBmsLnmodeAndLnobjUsesSelectedModeWithoutChangingChartIdentity() throws Exception {
        for (int authored = 0; authored <= 3; authored++) {
            for (boolean lnobj : new boolean[]{false, true}) {
                Path chart = Files.writeString(directory.resolve(authored + "-" + lnobj + ".bms"),
                        "#TITLE Forced LN test\n#BPM 120\n#RANK 2\n#WAV01 test.wav\n"
                                + "#LNMODE " + authored + "\n#00112:01\n"
                                + (lnobj ? "#LNOBJ 02\n#00111:0102\n" : "#00151:0101\n"));
                verifyModes(chart, 2, 3);
            }
        }
    }

    @Test
    void bmsonPerNoteTypesAndHeaderAreOverriddenTogether() throws Exception {
        Path chart = Files.writeString(directory.resolve("mixed.bmson"), """
                {"version":"1.0.0","info":{"title":"Mixed LN","artist":"test",
                "mode_hint":"beat-7k","init_bpm":120,"judge_rank":100,"total":100,
                "resolution":240,"ln_type":3},"lines":[],"bpm_events":[],"stop_events":[],
                "sound_channels":[{"name":"test.wav","notes":[
                {"x":1,"y":0,"l":240,"t":1}, {"x":2,"y":0,"l":240,"t":2},
                {"x":3,"y":0,"l":240,"t":3}, {"x":4,"y":0,"l":240,"t":0},
                {"x":5,"y":0,"l":0}]}]}
                """);
        verifyModes(chart, 5, 9);
    }

    private void verifyModes(Path chart, int lnNotes, int cnNotes) throws Exception {
        byte[] original = Files.readAllBytes(chart);
        for (int selected = 0; selected <= 2; selected++) {
            BMSModel authored = ChartDecoder.getDecoder(chart).decode(new ChartInformation(chart, selected, null));
            BMSModel forced = ChartDecoder.getDecoder(chart).decode(new ChartInformation(chart, selected, null));
            BMSIRLongNoteMode.apply(forced);
            assertNotNull(forced);
            assertEquals(authored.getMD5(), forced.getMD5());
            assertEquals(authored.getSHA256(), forced.getSHA256());
            assertEquals(selected, forced.getLntype());
            assertEquals(0, forced.getLnmode());
            assertEquals(selected == 0 ? lnNotes : cnNotes, forced.getTotalNotes());
            List<LongNote> starts = new ArrayList<>();
            for (TimeLine timeline : forced.getAllTimeLines()) {
                for (int lane = 0; lane < forced.getMode().key; lane++) {
                    if (timeline.getNote(lane) instanceof LongNote note) {
                        assertEquals(LongNote.TYPE_UNDEFINED, note.getType());
                        assertNotNull(note.getPair());
                        assertSame(note, note.getPair().getPair());
                        if (!note.isEnd()) starts.add(note);
                    }
                }
            }
            assertFalse(starts.isEmpty());
            int features = BMSIRLongNoteMode.authoredFeatures(forced);
            BMSIRLongNoteMode.apply(forced);
            assertEquals(features, BMSIRLongNoteMode.authoredFeatures(forced));
            IRChartData ir = new IRChartData(new SongData(forced, false));
            assertEquals(selected, ir.lntype);
            assertEquals(forced.getTotalNotes(), ir.notes);
            assertTrue(ir.hasUndefinedLN);
            assertFalse(ir.hasCN);
            assertFalse(ir.hasHCN);
            assertArrayEquals(original, Files.readAllBytes(chart));
        }
    }

    @Test
    void randomBranchReloadUsesForcedPolicyAndKeepsBranch() throws Exception {
        Path chart = Files.writeString(directory.resolve("random.bms"), """
                #TITLE Random LN
                #BPM 120
                #WAV01 test.wav
                #LNMODE 3
                #RANDOM 2
                #IF 1
                #00151:0101
                #ENDIF
                #IF 2
                #00152:0101
                #00111:01
                #ENDIF
                #ENDRANDOM
                """);
        BMSModel model = new BMSDecoder().decode(new ChartInformation(chart, 0, new int[]{2}));
        BMSIRLongNoteMode.apply(model);
        assertArrayEquals(new int[]{2}, model.getRandom());
        assertEquals(2, model.getTotalNotes());
        assertEquals(0, model.getLnmode());
        assertTrue(model.containsUndefinedLongNote());
    }

    @Test
    void catalogOnlyRankingRequestsDeclareEveryLongNoteChartModeSelectable() {
        for (int feature : new int[]{SongData.FEATURE_UNDEFINEDLN, SongData.FEATURE_LONGNOTE,
                SongData.FEATURE_CHARGENOTE, SongData.FEATURE_HELLCHARGENOTE}) {
            SongData song = new SongData();
            song.setFeature(feature);
            for (int mode = 0; mode <= 2; mode++) {
                IRChartData request = new IRChartData(song, mode);
                assertTrue(request.hasUndefinedLN);
                assertEquals(mode, request.lntype);
            }
        }
        assertFalse(new IRChartData(new SongData(), 1).hasUndefinedLN);
    }
}
