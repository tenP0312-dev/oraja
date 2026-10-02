package bms.player.beatoraja.ir;

import bms.player.beatoraja.ClearType;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import bms.player.beatoraja.*;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RankingReloadPolicyTest {
    @TempDir Path directory;
    private static final IRRankingContext LN = new IRRankingContext(0, true);

    @Test
    void returnToForcedLnBypassesFreshTtlButOrdinarySelectionKeepsIt() {
        RankingData ranking = ranking(0);
        long now = ranking.getLastUpdateTime();
        assertEquals(605_000L, ranking.reloadDelay(LN, false, now));
        assertEquals(5_000L, ranking.reloadDelay(LN, true, now));
        assertEquals(5_000L, ranking.reloadDelay(new IRRankingContext(2, false), true, now));
        assertEquals(600_000L, ranking.reloadDelay(LN, false, now + 5_000L));
        assertEquals(1, ranking.getTotalPlayer());
        assertTrue(ranking.isCompatible(LN));
    }

    @Test
    void incompatibleForcedLnCacheDoesNotEarnTenMinutesOfFreshness() {
        for (int type : new int[] {1, 2}) {
            RankingData ranking = ranking(type);
            assertFalse(ranking.isCompatible(LN));
            assertTrue(ranking.isCompatible(new IRRankingContext(type, false)));
            assertEquals(5_000L, ranking.reloadDelay(LN, false, ranking.getLastUpdateTime()));
            ranking.discardIncompatibleScores(LN);
            assertEquals(0, ranking.getTotalPlayer());
            assertNull(ranking.getScore(0));
            assertEquals(RankingData.NONE, ranking.getState());
        }
    }

    @Test
    void failureAndSuccessfulEmptyRankingHaveShortRetryWindows() {
        RankingData failed = new RankingData();
        failed.failAccess();
        assertEquals(15_000L, failed.reloadDelay(LN, false, failed.getLastUpdateTime()));
        assertEquals(5_000L, failed.reloadDelay(LN, false, failed.getLastUpdateTime() + 10_000L));
        RankingData empty = new RankingData();
        empty.restoreCachedScores(new IRScoreData[0]);
        assertEquals(65_000L, empty.reloadDelay(LN, false, empty.getLastUpdateTime()));
        assertEquals(5_000L, empty.reloadDelay(LN, true, empty.getLastUpdateTime()));
    }

    @Test
    void lateResponseUpdatesOnlyTheCacheEntryOfItsCapturedContext() {
        SongData song = new SongData();
        song.setSha256("a".repeat(64));
        song.setFeature(SongData.FEATURE_UNDEFINEDLN);
        RankingDataCache cache = new RankingDataCache();
        IRRankingContext cnContext = new IRRankingContext(1, false);
        RankingData cn = new RankingData();
        RankingData ln = ranking(0);
        cache.put(song, cnContext, cn);
        cache.put(song, LN, ln);
        cn.restoreCachedScores(new IRScoreData[] {score(1)});
        assertSame(ln, cache.get(song, LN));
        assertEquals(0, cache.get(song, LN).getScore(0).lntype);
        assertEquals(1, cache.get(song, cnContext).getScore(0).lntype);
    }

    @Test
    void backgroundRefreshIsSingleFlightAndFailureKeepsCompatibleRows() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        IRConnection connection = (IRConnection) Proxy.newProxyInstance(IRConnection.class.getClassLoader(),
                new Class<?>[] {IRConnection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCoursePlayData")) {
                        calls.incrementAndGet(); entered.countDown();
                        assertEquals(0, ((IRCourseData) args[1]).lntype);
                        assertTrue(finish.await(5, TimeUnit.SECONDS));
                        return new IRResponse<IRScoreData[]>() {
                            public boolean isSucceeded() { return false; }
                            public String getMessage() { return "controlled failure"; }
                            public IRScoreData[] getData() { return null; }
                        };
                    }
                    return null;
                });
        Config config = new Config(); config.setPlayerpath(directory.toString());
        PlayerConfig player = new PlayerConfig(); player.setId("fixture");
        IRConfig irConfig = new IRConfig(); irConfig.setImportrival(true);
        MainController controller = new MainController(null, config, player, null, false) {
            @Override public IRStatus[] getIRStatus() {
                return new IRStatus[] {new IRStatus(irConfig, connection, new IRPlayerData("fixture", "fixture", ""))};
            }
        };
        MainState state = new MainState(controller, null) {
            public void create() {} public void render() {}
        };
        CourseData course = new CourseData();
        SongData song = new SongData(); song.setMd5("a".repeat(32)); song.setSha256("a".repeat(64));
        course.setSong(new SongData[] {song});
        RankingData ranking = ranking(0);
        ranking.load(state, course, LN, true);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            for (int i = 0; i < 10; i++) ranking.load(state, course, LN, true);
            assertEquals(1, calls.get());
            assertEquals(1, ranking.getTotalPlayer());
            assertEquals(RankingData.FINISH, ranking.getState());
        } finally { finish.countDown(); }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ranking.isAccessInFlight() && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse(ranking.isAccessInFlight());
        assertEquals(1, ranking.getTotalPlayer());
        assertEquals(15_000L, ranking.reloadDelay(LN, false, ranking.getLastUpdateTime()));
    }

    @Test
    void courseRankingsFromBeforeHashNormalizationAreNotRestored() {
        CourseData course = new CourseData(); course.setName("Satellite fixture");
        SongData song = new SongData(); song.setSha256("a".repeat(64));
        course.setSong(new SongData[] {song});
        MainController.IRStatus status = new MainController.IRStatus(new IRConfig(), null,
                new IRPlayerData("fixture", "fixture", ""));
        PersistentRankingDataStore store = new PersistentRankingDataStore();
        String current = RankingData.cacheIdentity(course, LN);
        assertTrue(current.startsWith("course:v2:"));
        store.save(directory.toString(), "fixture", status, LN, current.replace("course:v2:", "course:"),
                new IRScoreData[] {score(0)});
        assertNull(store.load(directory.toString(), "fixture", status, LN, current));
        store.save(directory.toString(), "fixture", status, LN, course, new IRScoreData[] {score(0)});
        assertEquals(1, store.load(directory.toString(), "fixture", status, LN, current).length);
    }

    private static RankingData ranking(int type) {
        RankingData ranking = new RankingData();
        ranking.restoreCachedScores(new IRScoreData[] {score(type)});
        return ranking;
    }

    private static IRScoreData score(int type) {
        return new IRScoreData(null, type, 0, "rival", ClearType.Hard, 123456L,
                100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0L, 10, 10, 10, 0, 0, 0L, 0, 0, null, null, null, null);
    }
}
