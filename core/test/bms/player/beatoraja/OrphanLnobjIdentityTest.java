package bms.player.beatoraja;

import bms.model.*;
import bms.player.beatoraja.ir.*;
import bms.player.beatoraja.song.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class OrphanLnobjIdentityTest {
    @TempDir Path directory;
    private BMSModel decode(String body, int revision, int... random) {
        BMSDecoder decoder = new BMSDecoder(); decoder.setCompatibilityRevision(revision);
        return decoder.decode(("#PLAYER 3\n#BPM 220\n#TOTAL 523\n#WAV01 a.wav\n#WAVZZ end.wav\n#LNOBJ ZZ\n" + body)
                .getBytes(StandardCharsets.US_ASCII), false, random.length == 0 ? null : random);
    }
    @Test void initialEndpointOnEitherSideIsAnOrdinaryNoteInEveryMode() {
        for (int selected = 0; selected <= 2; selected++) {
            for (String channel : new String[]{"11", "16", "24", "26"}) {
                String body = "#010" + channel + ":ZZ01\n";
                BMSModel current = decode(body, 3), previous = decode(body, 2);
                current.setChartInformation(new ChartInformation(null, selected, null));
                assertEquals(2, current.getTotalNotes()); assertEquals(1, previous.getTotalNotes());
                assertEquals(current.getSHA256(), previous.getSHA256());
                assertEquals(2, IRLongNoteContract.fromModel(current, selected, false).notes());
            }
        }
    }
    @Test void invisibleNotesCannotConsumeTheUnmatchedVisibleEndpoint() {
        BMSModel current = decode("#00144:ZZ\n#01024:ZZ01\n", 3);
        assertEquals(2, current.getTotalNotes());
        assertEquals(6, new SongData(current, false).getParsercompatibility());
        assertNotEquals(current.getSHA256(), BMSIRParserCompatibility.storageHash(current, current.getSHA256()));
        assertEquals(current.getSHA256(), BMSIRParserCompatibility.previousStorageHash(current, current.getSHA256()));
    }
    @Test void priorFixesKeepTheirV2ScoreKeysButNewGeometryUsesV3() {
        String body = "#00111 0101\n";
        BMSModel v2 = decode(body, 2), v3 = decode(body, 3);
        assertEquals(BMSIRParserCompatibility.storageHash(v2, v2.getSHA256()),
                BMSIRParserCompatibility.storageHash(v3, v3.getSHA256()));
        BMSModel changed = decode(body + "#01024:ZZ01\n", 3);
        BMSModel old = decode(body + "#01024:ZZ01\n", 2);
        assertEquals(7, new SongData(changed, false).getParsercompatibility());
        assertEquals(BMSIRParserCompatibility.storageHash(old, old.getSHA256()),
                BMSIRParserCompatibility.previousStorageHash(changed, changed.getSHA256()));
    }
    @Test void randomBranchesUseOneNewStorageNamespaceAndPreservePublicHashes() {
        String body = "#RANDOM 2\n#IF 1\n#01024:ZZ01\n#ENDIF\n#IF 2\n#01024:01\n#ENDIF\n#ENDRANDOM\n";
        BMSModel first = decode(body, 3, 1), second = decode(body, 3, 2);
        assertEquals(2, first.getTotalNotes()); assertEquals(1, second.getTotalNotes());
        assertEquals(BMSIRParserCompatibility.storageHash(first, first.getSHA256()),
                BMSIRParserCompatibility.storageHash(second, second.getSHA256()));
    }
    @Test void loadedAndCatalogHashMismatchesNeverProduceAResolvedRanking() throws Exception {
        Path file = Files.writeString(directory.resolve("identity.bms"), "#BPM 120\n#00111:01\n");
        SongData song = new SongData(new BMSDecoder().decode(file), false);
        song.setMd5("f".repeat(32));
        assertEquals(-1, IRChartData.forRanking(song, 0, false).bmsirRankingNotes);
        assertEquals(-1, IRChartData.forPlaySnapshot(song, 0, false).bmsirRankingNotes);
        SongData catalog = new SongData(); catalog.setPath(file.toString());
        catalog.setMd5("e".repeat(32)); catalog.setSha256(song.getBMSModel().getSHA256());
        assertEquals(-1, IRChartData.forRanking(catalog, 0, false).bmsirRankingNotes);
    }
    @Test void currentScanDoesNotMistakeOldAffectedMetadataForRevisionThree() {
        for (int old = 0; old <= 3; old++) assertFalse(BMSIRParserCompatibility.isCurrentMetadata(old));
        for (int current = 4; current <= 7; current++) assertTrue(BMSIRParserCompatibility.isCurrentMetadata(current));
    }
    @Test void normalLongNotesAndTheNewOrphanRemainConsistentForLnCnAndHcn() {
        for (int selected = 0; selected <= 2; selected++) {
            BMSModel current = decode("#00124:ZZ\n#00224:01\n#00324:ZZ\n", 3);
            current.setChartInformation(new ChartInformation(null, selected, null));
            assertEquals(selected == 0 ? 2 : 3, current.getTotalNotes());
            assertEquals(current.getTotalNotes(), IRLongNoteContract.fromModel(current, selected, false).notes());
            BMSIRLongNoteMode.apply(current);
            current.setChartInformation(new ChartInformation(null, 0, null));
            assertEquals(2, current.getTotalNotes());
        }
    }
    @Test void revisionTwoReplayAndBestRemainSeparateFromNewOrphanGeometry() throws Exception {
        Path file = Files.writeString(directory.resolve("orphan.bms"), "#BPM 120\n#LNOBJ ZZ\n#01024:ZZ01\n");
        BMSDecoder oldDecoder = new BMSDecoder(); oldDecoder.setCompatibilityRevision(2);
        BMSModel old = oldDecoder.decode(file), current = new BMSDecoder().decode(file);
        Config config = new Config(); config.setPlayerpath(directory.toString()); config.setPlayername("fixture");
        config.setAudioConfig(new AudioConfig()); Files.createDirectories(directory.resolve("fixture"));
        PlayDataAccessor data = new PlayDataAccessor(config);
        for (BMSModel model : new BMSModel[]{old, current}) {
            ScoreData score = new ScoreData(model.getMode()); score.setSha256(model.getSHA256());
            score.setNotes(model.getTotalNotes()); score.setEpg(model.getTotalNotes()); score.setClear(1);
            score.setBmsirEffectiveLnType(0);
            data.writeScoreData(score, model, 0, true);
        }
        assertEquals(1, data.readScoreData(old, 0).getNotes());
        assertEquals(2, data.readScoreData(current, 0).getNotes());
        ReplayData replay = new ReplayData(); replay.bmsirParserRevision = 2;
        replay.keylog = new bms.player.beatoraja.input.KeyInputLog[]{new bms.player.beatoraja.input.KeyInputLog(1000, 0, true)};
        data.wrireReplayData(replay, old, 0, 0);
        assertEquals(2, data.readReplayData(current, 0, 0).bmsirParserRevision);
        PlayerConfig player = new PlayerConfig(); player.setBmsirForceLn(false);
        PlayerResource resource = new PlayerResource(config, player, null, null);
        var modelField = PlayerResource.class.getDeclaredField("model"); modelField.setAccessible(true); modelField.set(resource, current);
        var chartField = PlayerResource.class.getDeclaredField("chartResource"); chartField.setAccessible(true);
        chartField.set(resource, SongResources.fromPath(file));
        assertEquals(1, resource.loadBMSModelForReplay(replay).getTotalNotes());
    }
    @Test void unselectedRandomBranchDoesNotGuessARankingOrRestoreAnotherBranch() throws Exception {
        Path file = Files.writeString(directory.resolve("random.bms"), "#BPM 120\n#RANDOM 2\n#IF 1\n#00111:01\n#ENDIF\n#IF 2\n#00111:0101\n#ENDIF\n#ENDRANDOM\n");
        SongData catalog = new SongData(); catalog.setPath(file.toString());
        assertEquals(-1, IRChartData.forRanking(catalog, 0, false).bmsirRankingNotes);
        BMSModel loaded = new BMSDecoder().decode(new ChartInformation(file, 0, new int[]{1}));
        assertEquals(1, IRChartData.forRanking(new SongData(loaded, false), 0, false).bmsirRankingNotes);
        RankingData ranking = new RankingData(); ranking.setContract(new IRLongNoteContract(0, 1));
        ScoreData score = new ScoreData(); score.setMode(0); score.setNotes(1);
        ranking.updateScore(new IRScoreData[]{new IRScoreData(score)}, null);
        assertTrue(ranking.hasCachedScores());
        ranking.setContract(IRLongNoteContract.UNKNOWN);
        assertFalse(ranking.hasCachedScores()); assertEquals(RankingData.FAIL, ranking.getState());
    }
    @Test void courseSnapshotUsesPlayedModelsAndASeparateLocalIdentity() throws Exception {
        Path file = Files.writeString(directory.resolve("course.bms"), "#BPM 120\n#LNOBJ ZZ\n#01024:ZZ01\n");
        BMSModel loaded = new BMSDecoder().decode(file);
        SongData catalog = new SongData(); catalog.setPath(file.toString());
        catalog.setMd5(loaded.getMD5()); catalog.setSha256(loaded.getSHA256()); catalog.setNotes(1);
        CourseData course = new CourseData(); course.setSong(new SongData[]{catalog});
        IRCourseData wire = IRCourseData.forPlaySnapshot(course, 0, false, null, new BMSModel[]{loaded});
        assertEquals(2, wire.bmsirRankingNotes);
        assertEquals(BMSIRParserCompatibility.storageHash(loaded, loaded.getSHA256()), wire.bmsirScoreSha256);
    }
}
