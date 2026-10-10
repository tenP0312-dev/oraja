package bms.player.beatoraja.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bms.player.beatoraja.ClearType;
import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.MainController.IRStatus;
import bms.player.beatoraja.song.SongData;

class PersistentRankingDataStoreTest {
    @TempDir Path directory;

    @Test
    void realPluginScoreGaugeRecordSurvivesCacheRoundTrip() {
        PersistentRankingDataStore store = new PersistentRankingDataStore();
        IRStatus status = status("BMS-IR", "190031");
        IRRankingContext context = new IRRankingContext(0, false);
        SongData chart = chartTarget("f".repeat(64));
        bms.player.beatoraja.ScoreData remote = new bms.player.beatoraja.ScoreData();
        remote.setPlayer("fixture"); remote.setClear(ClearType.Hard.id); remote.setEpg(123);
        IRScoreData score = new IRScoreData(remote);
        score.gaugeHistory = new IRGaugeHistory(java.util.List.of(100f, 90f), java.util.List.of(80f),
                java.util.List.of(60f), java.util.List.of(40f));
        store.save(directory.toString(), "player", status, context, chart, new IRScoreData[]{score});
        IRScoreData restored = store.load(directory.toString(), "player", status, context, chart)[0];
        assertEquals(score.gaugeHistory, restored.gaugeHistory);
        assertEquals(246, restored.getExscore());
    }

    @Test
    void previousDefaultWriterEmptyGaugeRecordDoesNotForceRefetch() throws Exception {
        PersistentRankingDataStore store = new PersistentRankingDataStore();
        IRStatus status = status("BMS-IR", "190031");
        IRRankingContext context = new IRRankingContext(0, false);
        SongData chart = chartTarget("f".repeat(64));
        store.save(directory.toString(), "player", status, context, chart,
                new IRScoreData[]{score("fixture", 100, ClearType.Hard)});
        Path path = directory.resolve("player/ir-ranking-cache.json");
        com.badlogic.gdx.utils.Json oldJson = new com.badlogic.gdx.utils.Json();
        oldJson.setUsePrototypes(false);
        PersistentRankingDataStore.CacheFile oldFile = oldJson.fromJson(PersistentRankingDataStore.CacheFile.class, Files.readString(path));
        oldFile.entries.get(0).scores[0].gaugeHistory = IRGaugeHistory.fromGaugeLog(null);
        Files.writeString(path, oldJson.toJson(oldFile));
        IRScoreData restored = store.load(directory.toString(), "player", status, context, chart)[0];
        assertEquals(200, restored.getExscore());
        // The old generic writer omitted record components; missing history stays empty.
        assertEquals(new IRGaugeHistory(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()), restored.gaugeHistory);
    }

    @Test
    void persistsAndScopesRankingByEndpointAccountAndTarget() {
        PersistentRankingDataStore store = new PersistentRankingDataStore();
        IRStatus first = status("https://ir-one.example", "account-a");
        IRRankingContext context = new IRRankingContext(0, false);
        String chart = "a".repeat(64);
        String otherChart = "d".repeat(64);
        IRScoreData[] scores = { score("", 100, ClearType.Hard) };

        store.save(directory.toString(), "player", first, context, chartTarget(chart), scores);

        assertEquals(100, store.load(directory.toString(), "player", first, context, chartTarget(chart))[0].epg);
        assertNull(store.load(directory.toString(), "player", status("https://ir-two.example", "account-a"), context,
                chartTarget(chart)));
        assertNull(store.load(directory.toString(), "player", status("https://ir-one.example", "account-b"), context,
                chartTarget(chart)));
        assertNull(store.load(directory.toString(), "player", first, new IRRankingContext(1, false), chartTarget(chart)));
        assertNull(store.load(directory.toString(), "player", first, context, chartTarget(otherChart)));
    }

    @Test
    void damagedCacheFallsBackToBackupOrEmptyWithoutThrowing() throws Exception {
        PersistentRankingDataStore store = new PersistentRankingDataStore();
        IRStatus status = status("https://ir.example", "account");
        IRRankingContext context = new IRRankingContext(0, false);
        String chart = "b".repeat(64);
        store.save(directory.toString(), "player", status, context, chartTarget(chart),
                new IRScoreData[]{score("rival", 250, ClearType.FullCombo)});
        Path cache = directory.resolve("player").resolve("ir-ranking-cache.json");
        Files.writeString(cache, "{broken");

        IRScoreData[] restored = store.load(directory.toString(), "player", status, context, chartTarget(chart));
        assertEquals(250, restored[0].epg);
    }

    @Test
    void failedPayloadValidationDoesNotReplaceExistingCache() {
        PersistentRankingDataStore store = new PersistentRankingDataStore();
        IRStatus status = status("https://ir.example", "account");
        IRRankingContext context = new IRRankingContext(0, false);
        SongData chart = chartTarget("e".repeat(64));
        store.save(directory.toString(), "player", status, context, chart,
                new IRScoreData[]{score("rival", 300, ClearType.Hard)});

        store.save(directory.toString(), "player", status, context, chart, new IRScoreData[]{null});

        assertEquals(300, store.load(directory.toString(), "player", status, context, chart)[0].epg);
    }

    @Test
    void invalidScorePayloadIsNotPersisted() {
        PersistentRankingDataStore store = new PersistentRankingDataStore();
        IRStatus status = status("https://ir.example", "account");
        String chart = "c".repeat(64);
        store.save(directory.toString(), "player", status, new IRRankingContext(0, false), chartTarget(chart),
                new IRScoreData[]{null});
        assertNull(store.load(directory.toString(), "player", status, new IRRankingContext(0, false), chartTarget(chart)));
    }

    @Test
    void restoresDerivedRankingValuesFromScoreArray() {
        RankingData ranking = new RankingData();
        ranking.restoreCachedScores(new IRScoreData[]{
                score("rival-a", 120, ClearType.Hard),
                score("", 100, ClearType.Easy),
                score("rival-b", 100, ClearType.FullCombo)
        });
        assertEquals(2, ranking.getRank());
        assertEquals(3, ranking.getTotalPlayer());
        assertEquals(1, ranking.getClearCount(ClearType.Hard.id));
        assertEquals(1, ranking.getClearCount(ClearType.Easy.id));
        assertEquals(1, ranking.getClearCount(ClearType.FullCombo.id));
        assertEquals(2, ranking.getScoreRanking(1));
        assertEquals(2, ranking.getScoreRanking(2));
    }

    private static SongData chartTarget(String hash) {
        SongData song = new SongData();
        song.setSha256(hash);
        return song;
    }

    private static IRStatus status(String name, String account) {
        IRConfig config = new IRConfig();
        config.setIrname(name);
        return new IRStatus(config, null, new IRPlayerData(account, account, ""));
    }

    private static IRScoreData score(String player, int pgreat, ClearType clear) {
        return new IRScoreData(null, 0, 0, player, clear, 123456L,
                pgreat, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0L, 10, 10, 10, 0, 0, 0L, 0, 0,
                null, null, null, null);
    }
}
