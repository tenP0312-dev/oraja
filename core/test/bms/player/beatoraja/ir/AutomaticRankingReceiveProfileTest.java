package bms.player.beatoraja.ir;

import bms.player.beatoraja.*;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticRankingReceiveProfileTest {
    @TempDir Path directory;
    private static final IRRankingContext CONTEXT = new IRRankingContext(0, false);

    @Test
    void selectionMigratesOldFullCacheAndRestoresOnlyMatchingReceiveSettings() throws Exception {
        for (boolean course : new boolean[]{false, true}) {
            Fixture f = new Fixture(course);
            f.store.save(f.path(), "fixture", f.status, CONTEXT, f.identity(), scores(3));
            f.scope.set("until-self"); f.rows = scores(1);
            RankingData first = f.select();
            assertEquals(1, first.getTotalPlayer());
            assertEquals(1, f.reads.get());
            assertEquals(1, f.store.loadScoped(f.path(), "fixture", f.status, CONTEXT, f.identity(), "until-self").length);
            assertNotEquals(Thread.currentThread().getName(), f.worker.get());
            assertEquals(1, f.select().getTotalPlayer()); // restart: same scope needs no row request
            assertEquals(1, f.reads.get());
            f.scope.set("score-only"); f.rows = scores(0);
            first.loadForSelection(f.state, f.target, CONTEXT, false); await(first);
            assertEquals(0, first.getTotalPlayer()); // next scheduled refresh also replaces memory rows
            assertEquals(2, f.reads.get());
            assertEquals(0, f.select().getTotalPlayer()); // successful empty cache survives restart
            assertEquals(2, f.reads.get());
            assertNull(f.store.loadScoped(f.path(), "fixture", f.status, CONTEXT, f.identity(), "until-self"));
        }
    }

    @Test
    void actualResponseScopeWinsWhenSettingsChangeDuringTheRequest() throws Exception {
        Fixture f = new Fixture(false);
        f.scope.set("full"); f.actualScope = "score-only"; f.rows = scores(0);
        assertEquals(0, f.select().getTotalPlayer());
        assertNull(f.store.loadScoped(f.path(), "fixture", f.status, CONTEXT, f.identity(), "full"));
        f.scope.set("score-only");
        assertEquals(0, f.select().getTotalPlayer());
        assertEquals(1, f.reads.get());
    }

    @Test
    void failedProfileOrRankingKeepsLastGoodCacheAndUsesFailureRetryDelay() throws Exception {
        Fixture f = new Fixture(true);
        f.rows = scores(2); f.select();
        f.scope.set("score-only"); f.profileFailed = true;
        RankingData offline = f.select();
        assertEquals(2, offline.getTotalPlayer());
        assertEquals(15_000L, offline.reloadDelay(CONTEXT, false, offline.getLastUpdateTime()));
        assertEquals(1, f.reads.get());
        f.profileFailed = false; f.rankingFailed = true;
        assertEquals(2, f.select().getTotalPlayer());
        assertEquals(2, f.store.loadLastGood(f.path(), "fixture", f.status, CONTEXT, f.identity()).length);
        f.rankingFailed = false; f.rows = scores(0);
        assertEquals(0, f.select().getTotalPlayer());
    }

    @Test
    void legacyIrKeepsItsExistingCacheAndSettingsCheckIsSingleFlightOffTheCallerThread() throws Exception {
        Fixture f = new Fixture(false);
        f.unsupported = true;
        f.store.save(f.path(), "fixture", f.status, CONTEXT, f.identity(), scores(2));
        assertEquals(2, f.select().getTotalPlayer());
        assertEquals(0, f.reads.get());
        f.unsupported = false;
        f.entered = new CountDownLatch(1); f.finish = new CountDownLatch(1);
        RankingData waiting = new RankingData();
        waiting.loadForSelection(f.state, f.target, CONTEXT, false);
        assertTrue(f.entered.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 10; i++) waiting.loadForSelection(f.state, f.target, CONTEXT, false);
        assertEquals(2, f.profiles.get()); // one prior legacy selection, one active selection
        f.finish.countDown(); await(waiting);
        assertEquals(1, f.reads.get());
    }

    private final class Fixture {
        final AtomicReference<String> scope = new AtomicReference<>("full");
        final AtomicReference<String> worker = new AtomicReference<>();
        final AtomicInteger reads = new AtomicInteger(), profiles = new AtomicInteger();
        volatile IRScoreData[] rows = scores(1);
        volatile boolean profileFailed, rankingFailed, unsupported;
        volatile String actualScope;
        volatile CountDownLatch entered, finish;
        final Object target;
        final PersistentRankingDataStore store = new PersistentRankingDataStore();
        final MainController.IRStatus status;
        final MainState state;
        Fixture(boolean course) {
            SongData song = new SongData(); song.setMd5("a".repeat(32)); song.setSha256("a".repeat(64)); song.setMode(7);
            CourseData grade = new CourseData(); grade.setName("fixture"); grade.setSong(new SongData[]{song});
            target = course ? grade : song;
            IRConnection connection = (IRConnection)Proxy.newProxyInstance(IRConnection.class.getClassLoader(),
                new Class<?>[]{IRConnection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getRankingCacheScope")) {
                        profiles.incrementAndGet(); worker.set(Thread.currentThread().getName());
                        if (entered != null) { entered.countDown(); finish.await(5, TimeUnit.SECONDS); }
                        return unsupported ? null : response(!profileFailed, scope.get());
                    }
                    if (method.getName().equals("getRankingResponseCacheScope")) return actualScope;
                    if (method.getName().equals("getPlayData") || method.getName().equals("getCoursePlayData")) {
                        reads.incrementAndGet(); return response(!rankingFailed, rows);
                    }
                    return null;
                });
            IRConfig ir = new IRConfig(); ir.setIrname("BMS-IR"); ir.setImportrival(true);
            status = new MainController.IRStatus(ir, connection, new IRPlayerData("190031", "fixture", ""));
            Config config = new Config(); config.setPlayerpath(path());
            PlayerConfig player = new PlayerConfig(); player.setId("fixture");
            MainController main = new MainController(null, config, player, null, false) {
                @Override public IRStatus[] getIRStatus() { return new IRStatus[]{status}; }
                @Override public PersistentRankingDataStore getPersistentRankingDataStore() { return store; }
                @Override public String getPlayerPath() { return Fixture.this.path(); }
                @Override public RivalDataAccessor getRivalDataAccessor() { return new RivalDataAccessor(); }
            };
            state = new MainState(main, null) { public void create() {} public void render() {} };
        }
        String path() { return directory.resolve(target instanceof CourseData ? "course" : "song").toString(); }
        String identity() { return RankingData.cacheIdentity(target, CONTEXT); }
        RankingData select() throws Exception {
            RankingData ranking = new RankingData();
            ranking.loadForSelection(state, target, CONTEXT, false); await(ranking);
            assertEquals(RankingData.FINISH, ranking.getState());
            return ranking;
        }
    }

    private static void await(RankingData ranking) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ranking.isAccessInFlight() && System.nanoTime() < end) Thread.sleep(5);
        assertFalse(ranking.isAccessInFlight());
    }
    private static <T> IRResponse<T> response(boolean ok, T value) {
        return new IRResponse<>() { public boolean isSucceeded(){return ok;}
            public String getMessage(){return "fixture";} public T getData(){return value;} };
    }
    private static IRScoreData[] scores(int count) {
        IRScoreData[] result = new IRScoreData[count];
        for(int i=0;i<count;i++) { ScoreData score = new ScoreData(); score.setPlayer("rival"+i);
            score.setClear(ClearType.Hard.id); score.setEpg(100-i); result[i] = new IRScoreData(score); }
        return result;
    }
}
