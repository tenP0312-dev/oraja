package bms.player.beatoraja.skin.property;

import bms.model.BMSModel;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegerPropertyFactoryBMSIRTest {

    @Test
    void dedicatedLnModeIgnoresReusedCnAndHcnSongMetadata() {
        SongData cn = new SongData();
        cn.setFeature(SongData.FEATURE_CHARGENOTE);
        SongData hcn = new SongData();
        hcn.setFeature(SongData.FEATURE_HELLCHARGENOTE);

        assertEquals(
                BMSModel.LNTYPE_LONGNOTE,
                IntegerPropertyFactory.dedicatedClientLnMode(cn)
        );
        assertEquals(
                BMSModel.LNTYPE_LONGNOTE,
                IntegerPropertyFactory.dedicatedClientLnMode(hcn)
        );
    }
}
