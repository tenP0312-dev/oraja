package bms.player.beatoraja.nonstop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MixGenreFamilyTest {

    private static void in(MixGenreFamily family, String... genres) {
        for (String genre : genres) {
            assertTrue(family.matches(genre), family + " should match " + genre);
        }
    }

    private static void notIn(MixGenreFamily family, String... genres) {
        for (String genre : genres) {
            assertFalse(family.matches(genre), family + " should not match " + genre);
        }
    }

    @Test
    void familiesCoverTheUsualSpellings() {
        in(MixGenreFamily.DRUM_N_BASS, "Drum'n'Bass", "DRUM&BASS", "drum and bass", "DnB", "D&B", "ドラムンベース",
                "Liquid Funk", "NEUROFUNK", "Jungle");
        in(MixGenreFamily.HARDCORE, "HAPPY HARDCORE", "Gabber", "SPEEDCORE", "frenchcore", "UK HARDCORE", "ハードコア",
                "HARDSTYLE");
        in(MixGenreFamily.TRANCE, "TRANCE", "Psy-Trance", "PSYTRANCE", "GOA", "Uplifting Trance", "トランス");
        in(MixGenreFamily.TECHNO, "TECHNO", "Minimal Techno", "ACID", "テクノ");
        in(MixGenreFamily.HOUSE, "HOUSE", "Progressive House", "ELECTRO HOUSE", "UK GARAGE", "ハウス");
        in(MixGenreFamily.ARTCORE, "ARTCORE", "Art Core", "アートコア");
        in(MixGenreFamily.EUROBEAT, "EUROBEAT", "Euro Beat", "ユーロビート");
        in(MixGenreFamily.J_CORE, "J-CORE", "JCORE", "Ｊ－ＣＯＲＥ", "Jコア");
        in(MixGenreFamily.POP, "POP", "J-POP", "JPOP", "アニソン", "ポップス");
        in(MixGenreFamily.ROCK, "ROCK", "J-ROCK", "HEAVY METAL", "メロディックスピードメタル");
    }

    @Test
    void shortKeywordsMatchWholeWordsOnly() {
        notIn(MixGenreFamily.POP, "POPCORN BREAKS", "HARDCORE");
        notIn(MixGenreFamily.JAZZ_FUNK, "NEUROFUNK");
        notIn(MixGenreFamily.HARDCORE, "ARTCORE", "J-CORE", "BREAKCORE");
        notIn(MixGenreFamily.J_CORE, "HARDCORE", "ARTCORE");
        notIn(MixGenreFamily.ARTCORE, "HARDCORE");
        assertTrue(MixGenreFamily.of("").isEmpty());
        assertTrue(MixGenreFamily.of(null).isEmpty());
        // a genre may belong to several families
        assertEquals(List.of(MixGenreFamily.TECHNO, MixGenreFamily.POP), MixGenreFamily.of("TECHNO POP"));
    }
}
