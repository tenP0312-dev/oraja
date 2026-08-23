package bms.player.beatoraja.select;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTextFieldCommandTest {
    @Test
    void deleteScoreCommandIsCaseInsensitiveButDoesNotMatchSearchText() {
        assertTrue(SearchTextField.isDeleteScoreCommand("/deletescore"));
        assertTrue(SearchTextField.isDeleteScoreCommand("  /DeleteScore "));
        assertFalse(SearchTextField.isDeleteScoreCommand("delete score"));
        assertFalse(SearchTextField.isDeleteScoreCommand(null));
    }
}
