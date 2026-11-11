package bms.player.beatoraja;

import bms.model.*;
import bms.player.beatoraja.select.ScoreDataCache;
import bms.player.beatoraja.song.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class ParserCatalogRefreshTest {
    @TempDir Path directory;

    private Path chart(String body) throws Exception {
        return Files.writeString(directory.resolve("fixture.bms"),
                "#PLAYER 1\n#TITLE Parser fixture\n#BPM 120\n#WAV01 a.wav\n#WAVZZ end.wav\n#LNOBJ ZZ\n" + body);
    }

    private Config config() throws Exception {
        Config config = new Config(); config.setPlayerpath(directory.toString()); config.setPlayername("player");
        config.setAudioConfig(new AudioConfig()); Files.createDirectories(directory.resolve("player"));
        return config;
    }

    private SQLiteSongDatabaseAccessor catalog(SongData song) throws Exception {
        SQLiteSongDatabaseAccessor db = new SQLiteSongDatabaseAccessor(directory.resolve("song.db").toString(),
                new String[]{directory.toString()});
        db.setSongDatas(new SongData[]{song});
        return db;
    }

    private ScoreData score(BMSModel model) {
        ScoreData score = new ScoreData(model.getMode()); score.setSha256(model.getSHA256());
        score.setNotes(model.getTotalNotes()); score.setEpg(model.getTotalNotes());
        score.setClear(5); score.setMinbp(0); score.setBmsirEffectiveLnType(model.getLntype());
        return score;
    }

    @Test void savingNewGeometryRefreshesCatalogColdReadsAndVisibleCacheWithoutReplacingOldPb() throws Exception {
        Path path = chart("#00111:ZZ01\n");
        BMSDecoder decoder = new BMSDecoder(); decoder.setCompatibilityRevision(2);
        BMSModel old = decoder.decode(path);
        SongData original = new SongData(old, false); original.setTitle("User title"); original.setTag("User tag"); original.setFavorite(1);
        SQLiteSongDatabaseAccessor db = catalog(original);
        SongData visible = db.getSongDatas("sha256", old.getSHA256())[0];
        assertNull(visible.getBMSModel()); assertEquals(1, visible.getNotes());
        Config config = config(); PlayerConfig player = new PlayerConfig(); player.setBmsirForceLn(true);
        PlayDataAccessor data = new PlayDataAccessor(config, player); data.setSongDatabase(db);
        data.writeScoreData(score(old), old, 0, true);
        ScoreDataCache cache = new ScoreDataCache() {
            protected ScoreData readScoreDatasFromSource(SongData song, int mode) { return data.readScoreData(song, mode); }
            protected void readScoreDatasFromSource(ScoreDatabaseAccessor.ScoreDataCollector collector, SongData[] songs, int mode) {
                data.readScoreDatas(collector, songs, mode);
            }
        };
        assertEquals(2, cache.readScoreData(visible, 0).getExscore());
        PlayerResource resource = new PlayerResource(config, player, null, null);
        BMSModel current = resource.loadBMSModel(path, 0);
        data.writeScoreData(score(current), current, 0, true);
        assertEquals(2, data.readScoreData(old, 0).getExscore(), "Old interpretation PB is retained");
        SongData refreshed = db.getSongDatas("sha256", old.getSHA256())[0];
        assertEquals(6, refreshed.getParsercompatibility()); assertEquals(2, refreshed.getNotes());
        assertEquals("User title", refreshed.getTitle()); assertEquals("User tag", refreshed.getTag()); assertEquals(1, refreshed.getFavorite());
        assertEquals(4, data.readScoreData(refreshed, 0).getExscore());
        SongData played = new SongData(current, false);
        assertTrue(BMSIRParserCatalog.refreshVisibleSongs(played, new SongData[]{visible}));
        cache.clear(); cache.update(visible, 0);
        assertEquals(4, cache.readScoreData(visible, 0).getExscore());
        SQLiteSongDatabaseAccessor reopened = new SQLiteSongDatabaseAccessor(directory.resolve("song.db").toString(),
                new String[]{directory.toString()});
        assertEquals(4, data.readScoreData(reopened.getSongDatas("sha256", old.getSHA256())[0], 0).getExscore());
    }

    @Test void forcedLnPlayNeverPersistsTheTransformedNoteCountOrFeature() throws Exception {
        Path path = chart("#LNMODE 2\n#00111:01\n#00211:ZZ\n");
        SongData stale = new SongData(new BMSDecoder().decode(path), false); stale.setParsercompatibility(0);
        SQLiteSongDatabaseAccessor db = catalog(stale);
        Config config = config(); PlayerConfig player = new PlayerConfig(); player.setBmsirForceLn(true);
        PlayDataAccessor data = new PlayDataAccessor(config, player); data.setSongDatabase(db);
        BMSModel played = new PlayerResource(config, player, null, null).loadBMSModel(path, 0);
        assertEquals(1, played.getTotalNotes());
        data.writeScoreData(score(played), played, 0, true);
        SongData refreshed = db.getSongDatas("sha256", played.getSHA256())[0];
        assertEquals(2, refreshed.getNotes()); assertTrue(refreshed.hasChargeNote());
        assertFalse(refreshed.hasUndefinedLongNote()); assertEquals(4, refreshed.getParsercompatibility());
        assertEquals(1, data.readScoreData(refreshed, 0).getNotes(), "Forced-LN PB still uses its separate pool");
    }

    @Test void cnSelectionDoesNotChangeTheCatalogsDefaultNoteScale() throws Exception {
        Path path = chart("#00111:ZZ\n#00211:01\n#00311:ZZ\n");
        SongData stale = new SongData(new BMSDecoder().decode(path), false); stale.setParsercompatibility(0);
        SQLiteSongDatabaseAccessor db = catalog(stale);
        Config config = config(); PlayerConfig player = new PlayerConfig(); player.setBmsirForceLn(false); player.setLnmode(1);
        PlayDataAccessor data = new PlayDataAccessor(config, player); data.setSongDatabase(db);
        BMSModel played = new PlayerResource(config, player, null, null).loadBMSModel(path, 1);
        assertEquals(3, played.getTotalNotes()); assertEquals(1, played.getLntype());
        data.writeScoreData(score(played), played, 1, true);
        SongData refreshed = db.getSongDatas("sha256", played.getSHA256())[0];
        assertEquals(2, refreshed.getNotes()); assertEquals(6, refreshed.getParsercompatibility());
        assertEquals(3, data.readScoreData(refreshed, 1).getNotes());
        assertEquals(1, played.getLntype(), "Catalog capture must restore the selected play mode");
    }

    @Test void historicalReplayRandomAndAlreadyTransformedModelsCannotRefreshCatalogs() throws Exception {
        Path path = chart("#00111:ZZ01\n");
        BMSDecoder decoder = new BMSDecoder(); decoder.setCompatibilityRevision(2);
        BMSModel old = decoder.decode(path); BMSIRParserCatalog.capture(old);
        assertNull(BMSIRParserCatalog.metadata(old));
        BMSModel transformed = new BMSDecoder().decode(path); BMSIRLongNoteMode.apply(transformed);
        BMSIRParserCatalog.capture(transformed); assertNull(BMSIRParserCatalog.metadata(transformed));
        Path random = chart("#RANDOM 2\n#IF 1\n#00111:ZZ01\n#ENDIF\n#IF 2\n#00111:01\n#ENDIF\n#ENDRANDOM\n");
        BMSModel branch = new BMSDecoder().decode(new ChartInformation(random, 0, new int[]{1}));
        BMSIRParserCatalog.capture(branch); assertNull(BMSIRParserCatalog.metadata(branch));
    }

    @Test void metadataUpdatesRequireBothFileHashesAndNeverDowngradeCurrentRows() throws Exception {
        Path path = chart("#00111:ZZ01\n");
        BMSModel model = new BMSDecoder().decode(path); BMSIRParserCatalog.capture(model);
        SongData stale = new SongData(model, false); stale.setParsercompatibility(2); stale.setNotes(1);
        SQLiteSongDatabaseAccessor db = catalog(stale);
        SongData metadata = BMSIRParserCatalog.metadata(model);
        metadata.setMd5("f".repeat(32)); assertFalse(db.refreshParserMetadata(metadata));
        assertEquals(1, db.getSongDatas("sha256", model.getSHA256())[0].getNotes());
        metadata = BMSIRParserCatalog.metadata(model); assertTrue(db.refreshParserMetadata(metadata));
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("song.db")); var s = c.createStatement()) {
            s.execute("UPDATE song SET parsercompatibility=8,notes=99");
        }
        assertFalse(db.refreshParserMetadata(metadata));
        assertEquals(99, db.getSongDatas("sha256", model.getSHA256())[0].getNotes());
    }

    @Test void selectorRefreshOnlyTouchesMatchingUnloadedOldRows() throws Exception {
        BMSModel model = new BMSDecoder().decode(chart("#00111:ZZ01\n")); BMSIRParserCatalog.capture(model);
        SongData played = new SongData(model, false), visible = new SongData();
        visible.setSha256(model.getSHA256()); visible.setMd5(model.getMD5()); visible.setParsercompatibility(2);
        SongData foreign = new SongData(); foreign.setSha256("f".repeat(64)); foreign.setMd5(model.getMD5());
        SongData current = new SongData(); current.setSha256(model.getSHA256()); current.setMd5(model.getMD5());
        current.setParsercompatibility(6); current.setNotes(99);
        SongData future = new SongData(); future.setSha256(model.getSHA256()); future.setMd5(model.getMD5());
        future.setParsercompatibility(8); future.setNotes(88);
        assertTrue(BMSIRParserCatalog.refreshVisibleSongs(played, new SongData[]{visible, foreign, current, future}));
        assertEquals(2, visible.getNotes()); assertEquals(0, foreign.getParsercompatibility());
        assertEquals(99, current.getNotes());
        assertEquals(8, future.getParsercompatibility()); assertEquals(88, future.getNotes());
        assertFalse(BMSIRParserCatalog.refreshVisibleSongs(played, new SongData[]{foreign, current}));
    }

    @Test void courseSaveRefreshesItsStageCatalogWithoutCreatingSingleChartBests() throws Exception {
        Path path = chart("#00111:ZZ01\n");
        BMSDecoder previous = new BMSDecoder(); previous.setCompatibilityRevision(2);
        SongData stale = new SongData(previous.decode(path), false);
        SQLiteSongDatabaseAccessor db = catalog(stale);
        Config config = config(); PlayerConfig player = new PlayerConfig(); player.setBmsirForceLn(true);
        PlayDataAccessor data = new PlayDataAccessor(config, player); data.setSongDatabase(db);
        PlayerResource resource = new PlayerResource(config, player, null, null);
        BMSModel first = resource.loadBMSModel(path, 0), second = resource.loadBMSModel(path, 0);
        ScoreData course = score(first); course.setNotes(4); course.setEpg(4);
        data.writeScoreData(course, new BMSModel[]{first, second}, 0, 0, new CourseData.CourseDataConstraint[0], true);
        SongData refreshed = db.getSongDatas("sha256", first.getSHA256())[0];
        assertEquals(6, refreshed.getParsercompatibility()); assertEquals(2, refreshed.getNotes());
        assertNull(data.readScoreData(refreshed, 0), "Course save must not manufacture a single-chart PB");
    }

    @Test void nonUpdatingScoresLeaveThePersistentCatalogAlone() throws Exception {
        Path path = chart("#00111:ZZ01\n");
        BMSDecoder previous = new BMSDecoder(); previous.setCompatibilityRevision(2);
        SQLiteSongDatabaseAccessor db = catalog(new SongData(previous.decode(path), false));
        Config config = config(); PlayerConfig player = new PlayerConfig(); player.setBmsirForceLn(false);
        PlayDataAccessor data = new PlayDataAccessor(config, player); data.setSongDatabase(db);
        BMSModel current = new PlayerResource(config, player, null, null).loadBMSModel(path, 0);
        data.writeScoreData(score(current), current, 0, false);
        assertEquals(2, db.getSongDatas("sha256", current.getSHA256())[0].getParsercompatibility());
    }

    @Test void detachedLoadsDoNotCaptureCatalogMetadataOrAffectTheNextPlayLoad() throws Exception {
        Path path = chart("#00111:ZZ01\n");
        PlayerConfig player = new PlayerConfig(); player.setBmsirForceLn(false);
        PlayerResource resource = new PlayerResource(config(), player, null, null);
        BMSModel detached = resource.loadDetachedBMSModel(path, 0, null);
        assertNotNull(detached); assertNull(BMSIRParserCatalog.metadata(detached));
        BMSModel play = resource.loadBMSModel(path, 0);
        assertEquals(2, BMSIRParserCatalog.metadata(play).getNotes());
    }

    @Test void ordinaryRandomLoadCannotReplaceTheCatalogWithOneSelectedBranch() throws Exception {
        Path path = chart("#RANDOM 2\n#IF 1\n#00111:ZZ01\n#ENDIF\n"
                + "#IF 2\n#00111:01\n#ENDIF\n#ENDRANDOM\n");
        BMSDecoder previous = new BMSDecoder(); previous.setCompatibilityRevision(2);
        SongData stale = new SongData(previous.decode(new ChartInformation(path, 0, new int[]{1})), false);
        SQLiteSongDatabaseAccessor db = catalog(stale);
        Config config = config(); PlayerConfig player = new PlayerConfig(); player.setBmsirForceLn(false);
        PlayDataAccessor data = new PlayDataAccessor(config, player); data.setSongDatabase(db);
        BMSModel played = new PlayerResource(config, player, null, null).loadBMSModel(path, 0);

        assertNull(BMSIRParserCatalog.metadata(played), "A generated RANDOM branch is not canonical catalog metadata");
        assertNotNull(played.getRandom()); assertEquals(1, played.getRandom().length);
        data.writeScoreData(score(played), played, 0, true);
        SongData retained = db.getSongDatas("sha256", played.getSHA256())[0];
        assertEquals(stale.getParsercompatibility(), retained.getParsercompatibility());
        assertEquals(stale.getNotes(), retained.getNotes()); assertTrue(retained.hasRandomSequence());
    }
}
