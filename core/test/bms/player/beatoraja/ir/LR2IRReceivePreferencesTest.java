package bms.player.beatoraja.ir;

import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class LR2IRReceivePreferencesTest {
    @Test
    void actualLoadSendsViewerAndKeepsEmptyRestrictedResponseSeparateFromFullResponse() {
        SongData song = new SongData();
        song.setMd5("471" + "b".repeat(29));
        song.setSha256("b".repeat(64));
        song.setMode(7);
        IRChartData chart = new IRChartData(song);
        AtomicInteger requests = new AtomicInteger();
        Function<String, String> server = form -> {
            requests.incrementAndGet();
            assertFalse(form.contains("private-password"));
            if (form.contains("&id=190031&")) return "#<ranking></ranking>";
            assertTrue(form.contains("&id=190032&"), form);
            return "#<ranking><score><name>Fixture</name><id>190033</id>"
                    + "<clear>4</clear><notes>100</notes><combo>90</combo>"
                    + "<pg>80</pg><gr>10</gr><minbp>3</minbp></score></ranking>";
        };
        IRConfig[] restricted = {config("BMS-IR", "190031")};
        IRConfig[] full = {config("Other IR", "12345"), config("BMS-IR", "190032")};
        assertEquals(0, LR2IRAccessor.getScoreData(chart, restricted, server).getValue().length);
        LeaderboardEntry[] rows = LR2IRAccessor.getScoreData(chart, full, server).getValue();
        assertEquals(1, rows.length);
        assertEquals(190033, rows[0].getLR2Id());
        assertEquals(170, rows[0].getIrScore().getExscore());
        assertEquals(0, LR2IRAccessor.getScoreData(chart, restricted, server).getValue().length);
        assertEquals(1, LR2IRAccessor.getScoreData(chart, full, server).getValue().length);
        assertEquals(2, requests.get());
    }

    @Test
    void dedicatedLeaderboardUsesBmsIrAccountEvenWithAnotherPrimaryIr() {
        IRConfig[] configs = {config("Other IR", "12345"), null,
                config("BMS-IR", " 190031 ")};
        String form = request(configs);
        assertEquals("songmd5=" + "a".repeat(32) + "&id=190031&lastupdate=", form);
        assertFalse(form.contains("12345"));
        assertFalse(form.contains("private-password"));
    }

    @Test
    void accountsHaveSeparateRequestAndCacheKeys() {
        String first = request(new IRConfig[] {config("BMS-IR", "190031")});
        String second = request(new IRConfig[] {config("BMS-IR", "190032")});
        String anonymous = request(null);
        // LR2IRAccessor keys its short ranking cache with this complete form.
        assertNotEquals(first, second);
        assertNotEquals(first, anonymous);
        assertNotEquals(second, anonymous);
    }

    @Test
    void missingOrInvalidBmsIrAccountNeverUsesAnotherServicesIdentity() {
        assertEquals("0", LR2IRAccessor.bmsirViewerId(null));
        assertEquals("0", LR2IRAccessor.bmsirViewerId(new IRConfig[0]));
        assertEquals("0", LR2IRAccessor.bmsirViewerId(
                new IRConfig[] {config("Other IR", "190031")}));
        for (String id : new String[] {"", "guest", "0", "-1", "2147483648", "1&client_view=all"}) {
            assertEquals("0", LR2IRAccessor.bmsirViewerId(
                    new IRConfig[] {config("BMS-IR", id), config("Other IR", "190031")}));
        }
    }

    private static String request(IRConfig[] configurations) {
        return new LR2IRAccessor.LR2IRSongData("a".repeat(32),
                LR2IRAccessor.bmsirViewerId(configurations)).toUrlEncodedForm();
    }

    private static IRConfig config(String name, String id) {
        return new IRConfig() {
            @Override public String getIrname() { return name; }
            @Override public String getUserid() { return id; }
            @Override public String getPassword() { return "private-password"; }
        };
    }
}
