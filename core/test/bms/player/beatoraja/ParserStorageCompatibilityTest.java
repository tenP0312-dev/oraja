package bms.player.beatoraja;

import bms.model.*;
import bms.player.beatoraja.ir.IRChartData;
import bms.player.beatoraja.ir.IRScoreData;
import bms.player.beatoraja.input.*;
import bms.player.beatoraja.play.*;
import bms.player.beatoraja.song.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.sql.DriverManager;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ParserStorageCompatibilityTest {
    @TempDir Path directory;
    private Path chart() throws Exception {
        return Files.writeString(directory.resolve("fixture.bms"),
                "#PLAYER 1\n#TITLE Fixture\n#BPM 120\n#TOTAL 100\n#WAV01 a.wav\n#00111 0101\n", Charset.forName("MS932"));
    }
    private BMSModel decode(Path path, boolean legacy) {
        BMSDecoder decoder = new BMSDecoder(); decoder.setLegacyCompatibility(legacy); return decoder.decode(path);
    }
    private Config config() throws Exception {
        Config config = new Config(); config.setPlayerpath(directory.toString()); config.setPlayername("fixture");
        config.setAudioConfig(new AudioConfig());
        Files.createDirectories(directory.resolve("fixture")); return config;
    }
    private ScoreData score(BMSModel model, int pg) {
        ScoreData score = new ScoreData(model.getMode()); score.setSha256(model.getSHA256());
        score.setNotes(model.getTotalNotes()); score.setEpg(pg); score.setMinbp(model.getTotalNotes() - pg);
        score.setRule(BMSPlayerRule.LR2); score.setJudgeAlgorithm(JudgeAlgorithm.Combo);
        score.setDeviceType(BMSPlayerInputDevice.Type.KEYBOARD); score.setClear(1); return score;
    }
    private ReplayData replay(int revision) {
        ReplayData replay = new ReplayData(); replay.bmsirParserRevision = revision;
        replay.keylog = new KeyInputLog[]{new KeyInputLog(1000, 0, true)}; return replay;
    }
    @Test void oldBestAndLogsAreNotTransposedOntoTheCorrectedScale() throws Exception {
        Path path = chart(); BMSModel old = decode(path, true), current = decode(path, false);
        PlayDataAccessor data = new PlayDataAccessor(config());
        data.writeScoreData(score(old, 3), old, 0, true);
        ScoreData played = score(current, 1); data.writeScoreData(played, current, 0, true);
        assertEquals(6, data.readScoreData(old, 0).getExscore()); assertEquals(3, data.readScoreData(old, 0).getNotes());
        assertEquals(2, data.readScoreData(current, 0).getExscore()); assertEquals(2, data.readScoreData(current, 0).getNotes());
        SongData song = new SongData(current, false);
        assertEquals(2, data.readScoreData(song, 0).getExscore());
        Map<SongData, ScoreData> batch = new IdentityHashMap<>(); data.readScoreDatas(batch::put, new SongData[]{song}, 0);
        assertEquals(2, batch.get(song).getExscore());
        for (int selector : new int[]{1, 2}) {
            data.readScoreDatas(batch::put, new SongData[]{song}, selector);
            assertEquals(2, batch.get(song).getExscore());
            assertEquals(data.readScoreData(song, selector).getExscore(), batch.get(song).getExscore());
        }
        assertEquals(current.getSHA256(), new IRChartData(song).sha256);
        assertEquals(current.getMD5(), new IRChartData(song).md5);
        assertNotEquals(current.getSHA256(), new IRScoreData(played).sha256);
        data.deleteScoreData(current, 0); assertNull(data.readScoreData(current, 0));
        assertEquals(6, data.readScoreData(old, 0).getExscore());
    }
    @Test void courseStorageAndOldReplayFallbackKeepBothInterpretations() throws Exception {
        Path path = chart(); BMSModel old = decode(path, true), current = decode(path, false);
        Config config = config(); PlayDataAccessor data = new PlayDataAccessor(config);
        BMSModel[] previous = {old}, corrected = {current};
        CourseData.CourseDataConstraint[] rules = {};
        data.writeScoreData(score(old, 3), previous, 0, 0, rules, true);
        data.writeScoreData(score(current, 1), corrected, 0, 0, rules, true);
        assertEquals(6, data.readScoreData(previous, 0, 0, rules).getExscore());
        assertEquals(2, data.readScoreData(corrected, 0, 0, rules).getExscore());
        data.wrireReplayData(replay(1), old, 0, 0);
        ReplayData legacy = data.readReplayData(current, 0, 0); assertNotNull(legacy); assertEquals(1, legacy.bmsirParserRevision);
        PlayerConfig player = new PlayerConfig(); player.setBmsirForceLn(false);
        PlayerResource resource = new PlayerResource(config, player, null, null);
        Field model = PlayerResource.class.getDeclaredField("model"); model.setAccessible(true); model.set(resource, current);
        Field chart = PlayerResource.class.getDeclaredField("chartResource"); chart.setAccessible(true); chart.set(resource, SongResources.fromPath(path));
        BMSModel restored = resource.loadBMSModelForReplay(legacy);
        resource.installRecordedReplayModel(restored);
        assertEquals(3, restored.getTotalNotes()); assertEquals(3, resource.getBMSModel().getTotalNotes());
        assertEquals(3, resource.getSongdata().getNotes()); assertEquals(2, current.getTotalNotes());
        data.wrireReplayData(replay(2), current, 0, 0);
        assertEquals(2, data.readReplayData(current, 0, 0).bmsirParserRevision);
        assertEquals(1, data.readReplayData(old, 0, 0).bmsirParserRevision);
        data.deleteReplayData(current, 0, 0); assertNotNull(data.readReplayData(old, 0, 0));
        data.wrireReplayData(new ReplayData[]{replay(1)}, previous, 0, 1, rules);
        assertEquals(1, data.readReplayData(corrected, 0, 1, rules)[0].bmsirParserRevision);
    }
    @Test void selectedRandomBranchCannotOverwriteTheOldRawBest() throws Exception {
        Path path = Files.writeString(directory.resolve("random.bms"),
                "#PLAYER 1\n#BPM 120\n#TOTAL 100\n#WAV01 a.wav\n#RANDOM 2\n#IF 1\n#00111 0101\n#ENDIF\n#IF 2\n#00111:01\n#ENDIF\n#ENDRANDOM\n");
        BMSDecoder previous = new BMSDecoder(); previous.setLegacyCompatibility(true);
        BMSModel old = previous.decode(new ChartInformation(path, 0, new int[]{1}));
        BMSModel corrected = new BMSDecoder().decode(new ChartInformation(path, 0, new int[]{2}));
        PlayDataAccessor data = new PlayDataAccessor(config());
        data.writeScoreData(score(old, old.getTotalNotes()), old, 0, true);
        assertNull(data.readScoreData(corrected, 0));
        data.writeScoreData(score(corrected, 1), corrected, 0, true);
        assertEquals(2, data.readScoreData(corrected, 0).getExscore());
        assertEquals(old.getTotalNotes() * 2, data.readScoreData(old, 0).getExscore());
    }
    @Test void correctedLongNotesKeepSelectorAndForcedCachePolicies() throws Exception {
        Path path = Files.writeString(directory.resolve("ln.bms"),
                "#PLAYER 1\n#BPM 120\n#TOTAL 100\n#WAV01 a.wav\n#LNMODE 2\n#LNOBJ ZZ\n#00111:01\n#00211:ZZ\n#00311:ZZ\n");
        BMSModel authored = new BMSDecoder().decode(path);
        SongData catalog = new SongData(authored, false);
        assertEquals(3, catalog.getNotes());
        BMSModel forced = new BMSDecoder().decode(path); BMSIRLongNoteMode.apply(forced);
        assertEquals(2, forced.getTotalNotes());
        PlayerConfig player = new PlayerConfig(); player.setBmsirForceLn(true);
        PlayDataAccessor data = new PlayDataAccessor(config(), player);
        data.writeScoreData(score(forced, 1), forced, 0, true);
        var cache = new bms.player.beatoraja.select.ScoreDataCache() {
            protected boolean isForcedLn() { return true; }
            protected ScoreData readScoreDatasFromSource(SongData song, int mode) { return data.readScoreData(song, mode); }
            protected void readScoreDatasFromSource(ScoreDatabaseAccessor.ScoreDataCollector collector, SongData[] songs, int mode) {
                data.readScoreDatas(collector, songs, mode);
            }
        };
        assertEquals(2, cache.readScoreData(catalog, 0).getNotes());
        cache.clear();
        Map<SongData, ScoreData> batch = new IdentityHashMap<>();
        cache.readScoreDatas(batch::put, new SongData[]{catalog}, 0);
        assertEquals(2, batch.get(catalog).getNotes());
        cache.update(catalog, 0); assertEquals(2, cache.readScoreData(catalog, 0).getNotes());
        assertNull(data.readScoreData(catalog, 1));
        data.readScoreDatas(batch::put, new SongData[]{catalog}, 1); assertNull(batch.get(catalog));
        // Undefined authored LN must not borrow its mode0 PB while CN/HCN is selected.
        Path undefinedPath = Files.writeString(directory.resolve("undefined.bms"), Files.readString(path).replace("#LNMODE 2\n", ""));
        BMSModel undefined = new BMSDecoder().decode(undefinedPath);
        PlayDataAccessor ordinary = new PlayDataAccessor(config());
        ordinary.writeScoreData(score(undefined, 1), undefined, 0, true);
        SongData undefinedSong = new SongData(undefined, false);
        ordinary.readScoreDatas(batch::put, new SongData[]{undefinedSong}, 1); assertNull(batch.get(undefinedSong));
    }
    @Test void normalDatabaseUpdateRefreshesOldMetadataWithoutFileChanges() throws Exception {
        Path root = directory.resolve("songs"); Files.createDirectories(root);
        Path path = Files.copy(chart(), root.resolve("fixture.bms"));
        String[] roots = {root.toString()}; Path database = directory.resolve("song.db");
        SQLiteSongDatabaseAccessor data = new SQLiteSongDatabaseAccessor(database.toString(), roots);
        data.updateSongDatas(null, roots, false, false, null);
        SongData song = data.getSongDatas("title", "Fixture")[0]; assertEquals(5, song.getParsercompatibility());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database); var statement = connection.createStatement()) {
            statement.execute("UPDATE song SET parsercompatibility=0, notes=3");
        }
        data.updateSongDatas(null, roots, false, false, null);
        SongData refreshed = data.getSongDatas("title", "Fixture")[0];
        assertEquals(5, refreshed.getParsercompatibility()); assertEquals(2, refreshed.getNotes());
        assertEquals(decode(path, false).getSHA256(), refreshed.getSha256());
    }
    @Test void folderUpdateWithOldMetadataKeepsSongsOutsideThatFolder() throws Exception {
        Path root = directory.resolve("library"); Path kept = root.resolve("kept"); Path downloaded = root.resolve("downloaded");
        Files.createDirectories(kept); Files.createDirectories(downloaded);
        Files.copy(chart(), kept.resolve("fixture.bms"));
        String[] roots = {root.toString()}; Path database = directory.resolve("folder-song.db");
        SQLiteSongDatabaseAccessor data = new SQLiteSongDatabaseAccessor(database.toString(), roots);
        data.updateSongDatas(null, roots, false, false, null);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database); var statement = connection.createStatement()) {
            statement.execute("UPDATE song SET parsercompatibility=NULL, notes=3");
        }
        // A chart download rescans only its own folder; it must not rebuild the whole catalog from it.
        Files.writeString(downloaded.resolve("new.bms"), Files.readString(chart()).replace("Fixture", "Downloaded"));
        data.updateSongDatas(downloaded.toString(), roots, false, false, null);
        assertEquals(1, data.getSongDatas("title", "Downloaded").length);
        SongData stale = data.getSongDatas("title", "Fixture")[0];
        assertEquals(3, stale.getNotes()); assertEquals(0, stale.getParsercompatibility());
        data.updateSongDatas(kept.toString(), roots, false, false, null);
        SongData refreshed = data.getSongDatas("title", "Fixture")[0];
        assertEquals(5, refreshed.getParsercompatibility()); assertEquals(2, refreshed.getNotes());
        assertEquals(1, data.getSongDatas("title", "Downloaded").length);
    }
}
