package bms.player.beatoraja.play.bga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BGImageProcessorPreparationTest {
    @Test
    void advancesDisposalsAndUploadsWithinIndependentBudgets() {
        ArrayDeque<String> pendingDisposals = new ArrayDeque<>(List.of("old-a", "old-b", "old-c"));
        BGImageProcessor.IncrementalPreparation<String> preparation =
                new BGImageProcessor.IncrementalPreparation<>(pendingDisposals, new int[] {4, 8, 15});
        List<String> disposed = new ArrayList<>();
        List<Integer> uploaded = new ArrayList<>();

        preparation.advance(1, 1, disposed::add, uploaded::add);

        assertEquals(List.of("old-a"), disposed);
        assertEquals(List.of(4), uploaded);
        assertFalse(preparation.isComplete());

        preparation.advance(4, 1, disposed::add, uploaded::add);

        assertEquals(List.of("old-a", "old-b", "old-c"), disposed);
        assertEquals(List.of(4, 8), uploaded);
        assertFalse(preparation.isComplete());

        preparation.advance(0, 1, disposed::add, uploaded::add);
        assertEquals(List.of(4, 8, 15), uploaded);
        assertTrue(preparation.isComplete());
    }

    @Test
    void carriesPendingDisposalsIntoReplacementPreparation() {
        ArrayDeque<String> pendingDisposals = new ArrayDeque<>(List.of("old-a", "old-b"));
        BGImageProcessor.IncrementalPreparation<String> preparation =
                new BGImageProcessor.IncrementalPreparation<>(pendingDisposals, new int[] {1});
        ArrayDeque<String> replacementDisposals = new ArrayDeque<>();

        preparation.drainDisposalsTo(replacementDisposals);

        assertEquals(List.of("old-a", "old-b"), List.copyOf(replacementDisposals));
        assertFalse(preparation.isComplete());
    }
}
