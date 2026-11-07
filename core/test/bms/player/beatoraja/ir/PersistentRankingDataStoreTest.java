package bms.player.beatoraja.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        IRStatus status = status("BMS-IR", "190031");
        IRRankingContext context = new IRRankingContext(0, false);
        SongData chart = chartTarget("f".repeat(64));
        // Build the former JSON file with one entry, the way older clients wrote it.
        PersistentRankingDataStore writer = new PersistentRankingDataStore();
        writer.save(directory.resolve("source").toString(), "player", status, context, chart,
                new IRScoreData[]{score("fixture", 100, ClearType.Hard)});
        PersistentRankingDataStore.Entry entry = onlyEntry(writer, directory.resolve("source"), status, context, chart);
        writer.close();
        entry.scores[0].gaugeHistory = IRGaugeHistory.fromGaugeLog(null);
        writeLegacy(directory.resolve("player"), entry);

        PersistentRankingDataStore store = new PersistentRankingDataStore();
        IRScoreData restored = store.load(directory.toString(), "player", status, context, chart)[0];
        assertEquals(200, restored.getExscore());
        // Depending on the writer, the old record is either empty or a tagged list
        // ({"class": ..., "items": [0]}); both restore without dropping the ranking.
        assertNotNull(restored.gaugeHistory);
        for (java.util.List<Float> channel : java.util.List.of(restored.gaugeHistory.easy(), restored.gaugeHistory.groove(),
                restored.gaugeHistory.hard(), restored.gaugeHistory.exhard())) {
            assertTrue(channel.isEmpty() || channel.equals(java.util.List.of(0f)), channel.toString());
        }
        store.close();
    }

    @Test
    void legacyJsonIsMigratedOnceAndKeptAsMigratedFile() throws Exception {
        IRStatus status = status("https://ir.example", "account");
        IRRankingContext context = new IRRankingContext(0, false);
        SongData first = chartTarget("1".repeat(64));
        SongData second = chartTarget("2".repeat(64));
        PersistentRankingDataStore writer = new PersistentRankingDataStore();
        Path source = directory.resolve("source");
        writer.save(source.toString(), "player", status, context, first, new IRScoreData[]{score("a", 11, ClearType.Hard)});
        writer.save(source.toString(), "player", status, context, second, new IRScoreData[]{score("b", 22, ClearType.Easy)});
        PersistentRankingDataStore.Entry firstEntry = onlyEntry(writer, source, status, context, first);
        PersistentRankingDataStore.Entry secondEntry = onlyEntry(writer, source, status, context, second);
        writer.close();
        Path profile = directory.resolve("player");
        writeLegacy(profile, firstEntry, secondEntry);

        PersistentRankingDataStore store = new PersistentRankingDataStore();
        assertEquals(11, store.load(directory.toString(), "player", status, context, first)[0].epg);
        assertEquals(22, store.load(directory.toString(), "player", status, context, second)[0].epg);
        assertFalse(Files.exists(profile.resolve("ir-ranking-cache.json")));
        assertTrue(Files.exists(profile.resolve("ir-ranking-cache.json.migrated")));
        assertTrue(Files.exists(profile.resolve("ir-ranking-cache.sqlite")));
        store.close();

        // A JSON file that appears again later is not imported a second time.
        writeLegacy(profile, firstEntry);
        PersistentRankingDataStore reopened = new PersistentRankingDataStore();
        reopened.save(directory.toString(), "player", status, context, first, new IRScoreData[]{score("a", 33, ClearType.Hard)});
        reopened.close();
        PersistentRankingDataStore again = new PersistentRankingDataStore();
        assertEquals(33, again.load(directory.toString(), "player", status, context, first)[0].epg);
        assertTrue(Files.exists(profile.resolve("ir-ranking-cache.json")));
        again.close();
    }

    @Test
    void scopedSaveReplacesTheChartRowAndLastGoodSeesIt() {
        PersistentRankingDataStore store = new PersistentRankingDataStore();
        IRStatus status = status("https://ir.example", "account");
        IRRankingContext context = new IRRankingContext(0, false);
        String identity = "song:" + "9".repeat(64);
        store.save(directory.toString(), "player", status, context, identity, new IRScoreData[]{score("a", 1, ClearType.Hard)});
        store.saveScoped(directory.toString(), "player", status, context, identity, "rivals-v2",
                new IRScoreData[]{score("a", 2, ClearType.Hard)});
        assertNull(store.load(directory.toString(), "player", status, context, identity));
        assertNull(store.loadScoped(directory.toString(), "player", status, context, identity, "rivals-v1"));
        assertEquals(2, store.loadScoped(directory.toString(), "player", status, context, identity, "rivals-v2")[0].epg);
        assertEquals(2, store.loadLastGood(directory.toString(), "player", status, context, identity)[0].epg);
        store.close();
    }

    @Test
    void oldestRowsAreEvictedBeyondTheLimit() {
        PersistentRankingDataStore store = new PersistentRankingDataStore(3);
        IRStatus status = status("https://ir.example", "account");
        IRRankingContext context = new IRRankingContext(0, false);
        for (int index = 0; index < 5; index++) {
            store.save(directory.toString(), "player", status, context, "song:" + index,
                    new IRScoreData[]{score("a", index, ClearType.Hard)});
        }
        assertNull(store.load(directory.toString(), "player", status, context, "song:0"));
        assertNull(store.load(directory.toString(), "player", status, context, "song:1"));
        for (int index = 2; index < 5; index++) {
            assertEquals(index, store.load(directory.toString(), "player", status, context, "song:" + index)[0].epg);
        }
        store.close();
    }

    @Test
    void damagedDatabaseIsSetAsideAndCachingContinues() throws Exception {
        Path profile = Files.createDirectories(directory.resolve("player"));
        Files.writeString(profile.resolve("ir-ranking-cache.sqlite"), "not a database");
        PersistentRankingDataStore store = new PersistentRankingDataStore();
        IRStatus status = status("https://ir.example", "account");
        IRRankingContext context = new IRRankingContext(0, false);
        store.save(directory.toString(), "player", status, context, "song:x", new IRScoreData[]{score("a", 7, ClearType.Hard)});
        assertEquals(7, store.load(directory.toString(), "player", status, context, "song:x")[0].epg);
        assertTrue(Files.exists(profile.resolve("ir-ranking-cache.sqlite.corrupt")));
        store.close();
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
        IRStatus status = status("https://ir.example", "account");
        IRRankingContext context = new IRRankingContext(0, false);
        SongData chart = chartTarget("b".repeat(64));
        PersistentRankingDataStore writer = new PersistentRankingDataStore();
        Path source = directory.resolve("source");
        writer.save(source.toString(), "player", status, context, chart,
                new IRScoreData[]{score("rival", 250, ClearType.FullCombo)});
        PersistentRankingDataStore.Entry entry = onlyEntry(writer, source, status, context, chart);
        writer.close();
        Path profile = directory.resolve("player");
        writeLegacy(profile, entry);
        Files.move(profile.resolve("ir-ranking-cache.json"), profile.resolve("ir-ranking-cache.json.bak"));
        Files.writeString(profile.resolve("ir-ranking-cache.json"), "{broken");

        PersistentRankingDataStore store = new PersistentRankingDataStore();
        IRScoreData[] restored = store.load(directory.toString(), "player", status, context, chart);
        assertEquals(250, restored[0].epg);
        store.close();
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

    /** Reads back the stored entry for one chart through the public API. */
    private static PersistentRankingDataStore.Entry onlyEntry(PersistentRankingDataStore store, Path root,
            IRStatus status, IRRankingContext context, SongData chart) throws Exception {
        IRScoreData[] scores = store.load(root.toString(), "player", status, context, chart);
        PersistentRankingDataStore.Entry entry = new PersistentRankingDataStore.Entry();
        entry.key = legacyKey(status, context, chart);
        entry.baseKey = entry.key;
        entry.savedAt = System.currentTimeMillis();
        entry.scores = java.util.Arrays.stream(scores).map(PersistentRankingDataStore.ScoreEntry::new)
                .toArray(PersistentRankingDataStore.ScoreEntry[]::new);
        return entry;
    }

    private static String legacyKey(IRStatus status, IRRankingContext context, SongData chart) throws Exception {
        String identity = String.join("\n", status.config.getIrname(), status.player.id,
                Integer.toString(context.lnmode()), Boolean.toString(context.forceLn()),
                "song:" + chart.getSha256().toLowerCase());
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return bms.model.BMSDecoder.convertHexString(digest);
    }

    /** Writes the former single-file JSON cache with the old default writer. */
    private static void writeLegacy(Path profile, PersistentRankingDataStore.Entry... entries) throws Exception {
        Files.createDirectories(profile);
        PersistentRankingDataStore.CacheFile file = new PersistentRankingDataStore.CacheFile();
        file.entries.addAll(java.util.List.of(entries));
        com.badlogic.gdx.utils.Json oldJson = new com.badlogic.gdx.utils.Json();
        oldJson.setUsePrototypes(false);
        Files.writeString(profile.resolve("ir-ranking-cache.json"), oldJson.toJson(file));
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
