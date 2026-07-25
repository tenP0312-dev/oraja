package bms.player.beatoraja.bmsir;

import bms.model.BMSModel;
import bms.model.ChartInformation;
import bms.model.LongNote;
import bms.model.Mode;
import bms.model.TimeLine;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.ir.IRChartData;
import bms.player.beatoraja.ir.IRScoreData;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BMSIRLongNotePolicyTest {

    @Test
    void normalizesDecoderModeAndExplicitCnHcnNotesToLn() {
        Path path = Path.of("chart.bms");
        int[] randoms = {2, 4};
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setChartInformation(
                new ChartInformation(path, BMSModel.LNTYPE_HELLCHARGENOTE, randoms));
        model.setLnmode(LongNote.TYPE_HELLCHARGENOTE);

        TimeLine cnStartLine = new TimeLine(0.0, 0, Mode.BEAT_7K.key);
        TimeLine cnEndLine = new TimeLine(0.5, 500_000, Mode.BEAT_7K.key);
        LongNote cnStart = new LongNote(1);
        LongNote cnEnd = new LongNote(-2);
        cnStart.setType(LongNote.TYPE_CHARGENOTE);
        cnStartLine.setNote(0, cnStart);
        cnEndLine.setNote(0, cnEnd);
        cnStart.setPair(cnEnd);

        TimeLine hcnStartLine = new TimeLine(1.0, 1_000_000, Mode.BEAT_7K.key);
        TimeLine hcnEndLine = new TimeLine(1.5, 1_500_000, Mode.BEAT_7K.key);
        LongNote hcnStart = new LongNote(2);
        LongNote hcnEnd = new LongNote(-2);
        hcnStart.setType(LongNote.TYPE_HELLCHARGENOTE);
        hcnStartLine.setNote(1, hcnStart);
        hcnEndLine.setNote(1, hcnEnd);
        hcnStart.setPair(hcnEnd);
        model.setAllTimeLine(new TimeLine[] {
                cnStartLine, cnEndLine, hcnStartLine, hcnEndLine
        });

        BMSIRLongNotePolicy.normalizeModel(model);

        assertEquals(BMSModel.LNTYPE_LONGNOTE, model.getLntype());
        assertEquals(LongNote.TYPE_LONGNOTE, model.getLnmode());
        assertEquals(LongNote.TYPE_LONGNOTE, cnStart.getType());
        assertEquals(LongNote.TYPE_LONGNOTE, cnEnd.getType());
        assertEquals(LongNote.TYPE_LONGNOTE, hcnStart.getType());
        assertEquals(LongNote.TYPE_LONGNOTE, hcnEnd.getType());
        assertEquals(2, model.getTotalNotes());
        assertEquals(path, model.getChartInformation().path);
        assertArrayEquals(randoms, model.getRandom());
    }

    @Test
    void playerConfigurationCannotSelectCnOrHcn() {
        PlayerConfig config = new PlayerConfig();

        config.setLnmode(BMSModel.LNTYPE_HELLCHARGENOTE);

        assertEquals(BMSModel.LNTYPE_LONGNOTE, config.getLnmode());
    }

    @Test
    void outgoingIrMetadataCannotAdvertiseCnOrHcn() {
        SongData song = new SongData();
        song.setFeature(SongData.FEATURE_HELLCHARGENOTE);
        IRChartData chart = new IRChartData(song, BMSModel.LNTYPE_HELLCHARGENOTE);
        ScoreData score = new ScoreData();
        score.setMode(BMSModel.LNTYPE_HELLCHARGENOTE);
        IRScoreData irScore = new IRScoreData(score);

        assertEquals(BMSModel.LNTYPE_LONGNOTE, chart.lntype);
        assertEquals(true, chart.hasLN);
        assertEquals(false, chart.hasCN);
        assertEquals(false, chart.hasHCN);
        assertEquals(BMSModel.LNTYPE_LONGNOTE, irScore.lntype);
    }
}
