package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.generated.AudioChartSession;
import bms.player.beatoraja.generated.GeneratedChartBuilder;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedAudioBarsTest {
    @TempDir
    Path directory;

    @AfterEach
    void closeSession() {
        AudioChartSession.close();
    }

    @Test
    void folderListsSubfoldersThenAudioFilesByName() throws Exception {
        Files.createDirectories(directory.resolve("b-folder"));
        Files.createDirectories(directory.resolve("A-folder"));
        Files.createDirectories(directory.resolve(".hidden"));
        for (String name : new String[] {"zeta.mp3", "Alpha.OGG", "beta.wav", "notes.txt", "chart.bms", ".dot.mp3"}) {
            Files.writeString(directory.resolve(name), "x");
        }
        List<String> names = GeneratedAudioFolderBar.entries(directory).stream()
                .map(path -> path.getFileName().toString()).toList();
        assertEquals(List.of("A-folder", "b-folder", "Alpha.OGG", "beta.wav", "zeta.mp3"), names);
        assertEquals(List.of(), GeneratedAudioFolderBar.entries(directory.resolve("missing")));
    }

    @Test
    void rootFolderAppearsOnlyForAnExistingConfiguredFolder() {
        assertNull(GeneratedAudioFolderBar.root(null, ""));
        assertNull(GeneratedAudioFolderBar.root(null, null));
        assertNull(GeneratedAudioFolderBar.root(null, directory.resolve("missing").toString()));
        assertNotNull(GeneratedAudioFolderBar.root(null, directory.toString()));
    }

    @Test
    void emptyFolderStillHasOneRow() {
        GeneratedAudioFolderBar root = GeneratedAudioFolderBar.root(null, directory.toString());
        Bar[] children = root.getChildren();
        assertEquals(1, children.length);
        assertInstanceOf(FunctionBar.class, children[0]);
    }

    @Test
    void settingCyclesStayInRange() {
        assertEquals(2, GeneratedAudioChartBar.nextDensity(1));
        assertEquals(4, GeneratedAudioChartBar.nextDensity(3));
        assertEquals(1, GeneratedAudioChartBar.nextDensity(4));
        assertEquals(8, GeneratedAudioChartBar.nextDivision(4));
        assertEquals(12, GeneratedAudioChartBar.nextDivision(8));
        assertEquals(16, GeneratedAudioChartBar.nextDivision(12));
        assertEquals(24, GeneratedAudioChartBar.nextDivision(16));
        assertEquals(4, GeneratedAudioChartBar.nextDivision(24));
        assertEquals(2, GeneratedAudioChartBar.nextMinChord(1));
        assertEquals(1, GeneratedAudioChartBar.nextMinChord(GeneratedChartBuilder.KEYS));
        assertEquals(4, GeneratedAudioChartBar.nextMaxChord(2, 3));
        assertEquals(2, GeneratedAudioChartBar.nextMaxChord(2, GeneratedChartBuilder.KEYS));

        PlayerConfig config = new PlayerConfig();
        config.setGeneratedChartChords(GeneratedAudioChartBar.nextMinChord(2), 2);
        assertEquals(3, config.getGeneratedChartMinChord());
        assertEquals(3, config.getGeneratedChartMaxChord(), "max follows min upward");
        config.setGeneratedChartChords(0, 99);
        assertEquals(1, config.getGeneratedChartMinChord());
        assertEquals(7, config.getGeneratedChartMaxChord());
        config.setGeneratedChartDensity(9);
        assertEquals(4, config.getGeneratedChartDensity());
        config.setGeneratedChartDensity(0);
        assertEquals(1, config.getGeneratedChartDensity());
    }

    @Test
    void settingsAreRememberedInThePlayerConfig() {
        PlayerConfig config = new PlayerConfig();
        assertEquals(new GeneratedChartBuilder.Settings(3, 1, 2, false), AudioChartSession.settings(config));
        config.setGeneratedChartDensity(4);
        config.setGeneratedChartChords(2, 4);
        config.setGeneratedChartScratch(true);
        config.setGeneratedChartFollowMusic(false);
        config.setGeneratedChartDivision(16);
        config.setGeneratedChartRepeatBars(false);
        config.setGeneratedChartTriplet(true);
        Json json = new Json();
        PlayerConfig restored = json.fromJson(PlayerConfig.class, json.toJson(config));
        restored.validate();
        assertEquals(new GeneratedChartBuilder.Settings(false, 4, 16, true, false, 2, 4, true),
                AudioChartSession.settings(restored));
        config.setGeneratedChartDivision(12);
        assertEquals(12, config.getGeneratedChartDivision());
        config.setGeneratedChartDivision(6);
        assertEquals(8, config.getGeneratedChartDivision());
        assertEquals(new GeneratedChartBuilder.Settings(3, 1, 2, false), AudioChartSession.settings(null));
        // a config written by 0.4.14.96-0.4.14.98 still loads (old field ignored like other unknown fields)
        Json lenient = new Json();
        lenient.setIgnoreUnknownFields(true);
        PlayerConfig old = lenient.fromJson(PlayerConfig.class, "{\"generatedChartDivision\":16}");
        old.validate();
        assertEquals(3, old.getGeneratedChartDensity());
        assertEquals(16, old.getGeneratedChartDivision(), "the old division now drives the fixed grid");
        assertTrue(old.isGeneratedChartFollowMusic());
        assertTrue(old.isGeneratedChartRepeatBars());
    }

    @Test
    void audioFolderIsAnOptionalSystemSetting() {
        Config config = new Config();
        assertEquals("", config.getGeneratedChartAudioDirectory());
        config.setGeneratedChartAudioDirectory("generated-audio");
        Json json = new Json();
        assertEquals("generated-audio", json.fromJson(Config.class, json.toJson(config)).getGeneratedChartAudioDirectory());
    }

    @Test
    void failedAnalysisOffersARetryRowAndFolderSessionsStayOutOfTheDropWindow() throws Exception {
        AudioChartSession session = AudioChartSession.start(directory.resolve("missing.mp3"), false, null);
        long deadline = System.currentTimeMillis() + 5000;
        while (session.state() == AudioChartSession.State.ANALYZING && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(AudioChartSession.State.FAILED, session.state());
        assertFalse(session.window());
        List<KeyedFunctionBar> rows = GeneratedAudioChartBar.rows(session, new PlayerConfig());
        assertEquals(List.of("status", "retry"), rows.stream().map(KeyedFunctionBar::getSelectionKey).toList());
        assertTrue(AudioChartSession.start(directory.resolve("other.mp3")).window());
    }

    @Test
    void rowLabelsNameTheSongAndAvoidGlyphsMissingFromSkinFonts() {
        assertEquals("my song.v2", GeneratedAudioChartBar.songName(Path.of("dir", "my song.v2.mp3")));
        AudioChartSession session = AudioChartSession.start(directory.resolve("song name.mp3"), false, null);
        for (KeyedFunctionBar row : GeneratedAudioChartBar.rows(session, new PlayerConfig())) {
            assertFalse(row.getTitle().contains("\u25b6") || row.getTitle().contains("\u301c"), row.getTitle());
            if (row.getSelectionKey().equals("status") && session.state() == AudioChartSession.State.ANALYZING) {
                assertTrue(row.getTitle().contains("song name"), row.getTitle());
            }
        }
    }

    @Test
    void keyedRowsKeepTheCursorWhenTheirLabelChanges() {
        KeyedFunctionBar before = new KeyedFunctionBar("density", (s, b) -> { }, "Note amount: As the music", FunctionBar.STYLE_TABLE);
        KeyedFunctionBar after = new KeyedFunctionBar("density", (s, b) -> { }, "Note amount: Dense", FunctionBar.STYLE_TABLE);
        KeyedFunctionBar other = new KeyedFunctionBar("scratch", (s, b) -> { }, "Note amount: As the music", FunctionBar.STYLE_TABLE);
        assertTrue(KeyedFunctionBar.sameRow(before, after));
        assertFalse(KeyedFunctionBar.sameRow(before, other));
        FunctionBar plainA = new FunctionBar((s, b) -> { }, "Same", FunctionBar.STYLE_TABLE);
        FunctionBar plainB = new FunctionBar((s, b) -> { }, "Same", FunctionBar.STYLE_TABLE);
        assertTrue(KeyedFunctionBar.sameRow(plainA, plainB), "unkeyed rows still match by class and title");
    }
}
