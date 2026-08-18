package bms.player.beatoraja;

import bms.player.beatoraja.skin.property.StringPropertyFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DifficultyTableCommentTest {
    @Test
    void normalizesMarkerLineEndingsAndControlCharacters() {
        assertEquals(
                "first\nsecond\nthird\nfourth",
                DifficultyTableComment.normalize(
                        " first[[BR]]second\r\nthird\rfourth\u0001 "
                )
        );
    }

    @Test
    void convertsCanonicalLineBreaksBackToTheSingleLineEditorMarker() {
        assertEquals(
                "first[[BR]]second",
                DifficultyTableComment.toEditorText("first\r\nsecond")
        );
    }

    @Test
    void exposesTheCommentThroughTheStableSkinPropertyNameAndId() {
        assertSame(
                StringPropertyFactory.getStringProperty(1004),
                StringPropertyFactory.getStringProperty("tablecomment")
        );
    }

    @Test
    void boundsCanonicalDataByCodePointWithoutCuttingASurrogatePair() {
        assertEquals(
                DifficultyTableComment.MAX_LENGTH,
                DifficultyTableComment.normalize("a".repeat(5000)).length()
        );
        String nearlyFull = "a".repeat(DifficultyTableComment.MAX_LENGTH - 1);
        assertEquals(
                nearlyFull + "😀",
                DifficultyTableComment.normalize(nearlyFull + "😀")
        );
    }
}
