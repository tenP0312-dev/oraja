package bms.player.beatoraja.select;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedPreviewSelectorTest {

    @Test
    void selectsTheDensestEightSecondWindowWithLeadIn() {
        int[][] distribution = new int[100][7];
        for (int second = 30; second < 38; second++) {
            distribution[second][5] = 10;
        }

        assertEquals(
                OptionalLong.of(29_500),
                GeneratedPreviewSelector.selectStartMs(distribution, 100_000));
    }

    @Test
    void longNoteBodiesUseLowerWeightThanPlayableHeadsAndTaps() {
        int[][] distribution = new int[60][7];
        for (int second = 10; second < 18; second++) {
            distribution[second][4] = 10;
        }
        for (int second = 30; second < 38; second++) {
            distribution[second][5] = 3;
        }

        assertEquals(
                OptionalLong.of(29_500),
                GeneratedPreviewSelector.selectStartMs(distribution, 60_000));
    }

    @Test
    void emptyDistributionFallsBackNearFortyFivePercent() {
        assertEquals(
                OptionalLong.of(45_000),
                GeneratedPreviewSelector.selectStartMs(new int[100][7], 100_000));
        assertEquals(
                OptionalLong.of(0),
                GeneratedPreviewSelector.selectStartMs(new int[10][7], 10_000));
    }

    @Test
    void absentDistributionAndLengthStartsFromTheBeginning() {
        assertEquals(OptionalLong.of(0), GeneratedPreviewSelector.selectStartMs(null, 0));
    }
}
