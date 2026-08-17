package bms.player.beatoraja.skin.property;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.player.beatoraja.ReplayData;
import bms.player.beatoraja.pattern.Random;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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

    @Test
    void fixedShufflePatternsUseLocalOneBasedLaneNumbers() {
        ReplayData replay = replay(Random.RANDOM, Random.RANDOM);
        replay.laneShufflePattern = new int[][]{
                {2, 0, 1, 3, 4, 5, 6, 7},
                {9, 10, 8, 11, 12, 13, 14, 15}
        };

        assertEquals(3, assigned(replay, Mode.BEAT_14K, 0, false));
        assertEquals(1, assigned(replay, Mode.BEAT_14K, 1, false));
        assertEquals(2, assigned(replay, Mode.BEAT_14K, 0, true));
        assertEquals(3, assigned(replay, Mode.BEAT_14K, 1, true));
    }

    @Test
    void randomExIncludesTheScratchDestination() {
        ReplayData replay = replay(Random.RANDOM_EX, Random.RANDOM_EX);
        replay.laneShufflePattern = new int[][]{
                {7, 1, 2, 3, 4, 5, 6, 0},
                {15, 9, 10, 11, 12, 13, 14, 8}
        };

        assertEquals(8, assigned(replay, Mode.BEAT_14K, 0, false));
        assertEquals(1, assigned(replay, Mode.BEAT_14K, -1, false));
        assertEquals(8, assigned(replay, Mode.BEAT_14K, 0, true));
        assertEquals(1, assigned(replay, Mode.BEAT_14K, -1, true));
    }

    @Test
    void unsupportedOrIncompletePatternsReturnZero() {
        ReplayData replay = replay(Random.MIRROR, Random.S_RANDOM);
        replay.laneShufflePattern = new int[][]{{0, 1, 2, 3, 4, 5, 6, 7}};

        assertEquals(0, assigned(replay, Mode.BEAT_14K, 0, false));
        assertEquals(0, assigned(replay, Mode.BEAT_14K, 0, true));
        assertEquals(0, assigned(null, Mode.BEAT_7K, 0, false));
        assertEquals(0, assigned(replay(Random.RANDOM, Random.RANDOM), Mode.BEAT_7K, 0, false));

        replay = replay(Random.RANDOM, Random.RANDOM);
        replay.laneShufflePattern = new int[][]{{9}, null};
        assertEquals(0, assigned(replay, Mode.BEAT_7K, 0, false));
        assertEquals(0, assigned(replay, Mode.BEAT_14K, 0, true));
        assertEquals(0, assigned(replay, Mode.BEAT_7K, 8, false));
    }

    private static ReplayData replay(Random first, Random second) {
        ReplayData replay = new ReplayData();
        replay.randomoption = generalOptionId(first);
        replay.randomoption2 = generalOptionId(second);
        return replay;
    }

    private static int generalOptionId(Random option) {
        return Arrays.asList(Random.OPTION_GENERAL).indexOf(option);
    }

    private static int assigned(ReplayData replay, Mode mode, int key, boolean secondSide) {
        return IntegerPropertyFactory.IndexType.assignedLane(replay, mode, key, secondSide);
    }
}
