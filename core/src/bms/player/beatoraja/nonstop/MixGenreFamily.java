package bms.player.beatoraja.nonstop;

import java.util.ArrayList;
import java.util.List;

/**
 * Broad genre families for the generator (Issue #563). BMS #GENRE is free
 * text ("Drum'n'Bass", "DnB", "ドラムンベース"…), so each family lists the
 * usual spellings. Genres are compared after {@link MixSequencer#normalizeGenre}.
 * A genre may belong to several families ("Techno Pop").
 */
public enum MixGenreFamily {
    TRANCE("トランス", "Trance",
            "trance", "psytrance", "psy", "goa", "uplifting", "トランス"),
    TECHNO("テクノ", "Techno",
            "techno", "minimal", "acid", "テクノ"),
    HARDCORE("ハードコア", "Hardcore",
            "hardcore", "gabber", "gabba", "speedcore", "frenchcore", "terrorcore", "splittercore", "extratone",
            "hardstyle", "hard dance", "uk hard", "ハードコア", "ガバ", "スピードコア", "ハードスタイル"),
    DRUM_N_BASS("ドラムンベース", "Drum'n'bass",
            "drumnbass", "drumandbass", "drumsandbass", "drum bass", "dnb", "d b", "jungle", "neurofunk",
            "liquid funk", "ドラムンベース", "ドラム ン ベース", "ジャングル"),
    HOUSE("ハウス", "House",
            "house", "garage", "2 step", "ハウス"),
    ARTCORE("アートコア", "Artcore",
            "artcore", "art core", "アートコア"),
    EUROBEAT("ユーロビート", "Eurobeat",
            "eurobeat", "euro beat", "italo", "para para", "ユーロビート", "ユーロ"),
    J_CORE("Jコア", "J-core",
            "jcore", "j core", "jコア"),
    BREAKCORE("ブレイクコア・ブレイクビーツ", "Breakcore / breakbeat",
            "breakcore", "breakbeat", "breaks", "big beat", "amen", "ブレイクコア", "ブレイクビーツ"),
    DUBSTEP("ダブステップ・ベース", "Dubstep / bass",
            "dubstep", "brostep", "riddim", "future bass", "bass music", "trap", "ダブステップ", "フューチャーベース"),
    ELECTRONICA("エレクトロニカ", "Electronica",
            "electronica", "idm", "glitch", "chiptune", "chip tune", "8bit", "エレクトロニカ", "チップチューン"),
    POP("ポップ", "Pop",
            "pop", "jpop", "kpop", "anison", "idol", "ポップ", "アニソン", "アイドル", "歌謡"),
    ROCK("ロック", "Rock",
            "rock", "metal", "punk", "jrock", "ロック", "メタル", "パンク"),
    HIP_HOP("ヒップホップ", "Hip hop",
            "hiphop", "rap", "ヒップホップ", "ラップ"),
    JAZZ_FUNK("ジャズ・ファンク", "Jazz / funk",
            "jazz", "fusion", "funk", "soul", "ジャズ", "フュージョン", "ファンク", "ソウル"),
    CLASSICAL("クラシック・オーケストラ", "Classical / orchestral",
            "classic", "classical", "orchestra", "orchestral", "symphonic", "baroque", "piano",
            "クラシック", "オーケストラ", "ピアノ", "バロック"),
    AMBIENT("アンビエント・チル", "Ambient / chill",
            "ambient", "chill", "lo fi", "lofi", "downtempo", "アンビエント", "チル"),
    WORLD("ワールド・和風", "World / Japanese folk",
            "world", "ethnic", "celtic", "latin", "和風", "和楽", "民族", "ケルト", "ワールド", "エスニック");

    /** ASCII keywords this short must match a whole word ("pop" is not "popcorn"). */
    private static final int WORD_MATCH_BELOW = 5;

    private final String japanese;
    private final String english;
    private final List<String> keywords;

    MixGenreFamily(String japanese, String english, String... keywords) {
        this.japanese = japanese;
        this.english = english;
        List<String> normalized = new ArrayList<>();
        for (String keyword : keywords) {
            normalized.add(MixSequencer.normalizeGenre(keyword));
        }
        this.keywords = List.copyOf(normalized);
    }

    public String label(boolean japaneseText) {
        return japaneseText ? japanese : english;
    }

    /** True when the #GENRE belongs to this family. */
    public boolean matches(String genre) {
        String normalized = MixSequencer.normalizeGenre(genre);
        if (normalized.isEmpty()) {
            return false;
        }
        String padded = " " + normalized + " ";
        String squashed = normalized.replace(" ", "");
        for (String keyword : keywords) {
            if (keyword.isEmpty()) {
                continue;
            }
            boolean ascii = keyword.chars().allMatch(c -> c < 128);
            if (!ascii) {
                if (normalized.contains(keyword) || squashed.contains(keyword.replace(" ", ""))) {
                    return true;
                }
            } else if (keyword.replace(" ", "").length() < WORD_MATCH_BELOW || keyword.contains(" ")
                    && keyword.replace(" ", "").length() < WORD_MATCH_BELOW + 2) {
                if (padded.contains(" " + keyword + " ")) {
                    return true;
                }
            } else if (squashed.contains(keyword.replace(" ", ""))) {
                return true;
            }
        }
        return false;
    }

    /** Every family the #GENRE belongs to. */
    public static List<MixGenreFamily> of(String genre) {
        List<MixGenreFamily> families = new ArrayList<>();
        for (MixGenreFamily family : values()) {
            if (family.matches(genre)) {
                families.add(family);
            }
        }
        return families;
    }
}
