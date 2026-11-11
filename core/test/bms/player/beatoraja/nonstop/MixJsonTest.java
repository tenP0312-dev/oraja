package bms.player.beatoraja.nonstop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MixJsonTest {

    static MixPlan sample() {
        MixPlan plan = new MixPlan();
        plan.name = "Night drive";
        plan.description = "house set";
        for (int i = 0; i < 3; i++) {
            MixTrack track = new MixTrack();
            track.sha256 = String.format("%064x", i + 10);
            track.md5 = String.format("%032x", i + 10);
            track.title = "song " + i;
            track.inMs = 1000L * i;
            track.outMs = 60000L + i;
            track.localPath = "/home/me/bms/song" + i + "/chart.bms";
            plan.addTrack(i, track);
        }
        plan.transitions.get(1).type = MixTransition.Type.CUT;
        plan.transitions.get(0).locked = true;
        return plan;
    }

    @Test
    void sharedRecipeRoundTripsWithoutLocalPaths() throws Exception {
        MixPlan plan = sample();
        String shared = MixJson.toText(plan, false);
        assertFalse(shared.contains("/home/me"), "a shared recipe must not carry local paths");
        assertFalse(shared.contains(plan.id), "a shared recipe must not carry the local id");
        MixPlan parsed = MixJson.fromText(shared);
        assertEquals(3, parsed.tracks.size());
        assertEquals(2, parsed.transitions.size());
        assertEquals(MixTransition.Type.CUT, parsed.transitions.get(1).type);
        assertTrue(parsed.transitions.get(0).locked);
        assertEquals(plan.tracks.get(2).sha256, parsed.tracks.get(2).sha256);
        assertEquals(MixJson.playIdentity(plan), MixJson.playIdentity(parsed));
    }

    @Test
    void renamingKeepsTheIdentityButEditingChangesIt() {
        MixPlan plan = sample();
        String before = MixJson.playIdentity(plan);
        plan.name = "renamed";
        plan.description = "changed text";
        assertEquals(before, MixJson.playIdentity(plan));
        plan.tracks.get(1).outMs += 1;
        assertNotEquals(before, MixJson.playIdentity(plan));
    }

    @Test
    void invalidValuesFallBackSafely() throws Exception {
        String text = "{\"format\":\"bmsir-nonstop-mix\",\"version\":1,\"mode\":\"nope\",\"judge\":\"X\","
                + "\"totalScale\":1e9,\"tracks\":[{\"sha256\":\"zz\",\"inMs\":-5,\"outMs\":3}],"
                + "\"transitions\":[{\"type\":\"BLEND\"}]}";
        MixPlan plan = MixJson.fromText(text);
        assertEquals(1, plan.tracks.size());
        assertEquals(0, plan.transitions.size());
        assertEquals("", plan.tracks.get(0).sha256);
        assertEquals(0, plan.tracks.get(0).inMs);
        assertEquals(MixPlan.JudgeRule.NORMAL, plan.judge);
        assertEquals(4.0, plan.totalScale);
        assertThrows(IllegalArgumentException.class,
                () -> MixJson.fromText("{\"format\":\"other\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> MixJson.fromText("{\"format\":\"bmsir-nonstop-mix\",\"version\":99}"));
    }

    @Test
    void movingATrackResetsUnlockedJoinsAroundIt() {
        MixPlan plan = sample();
        plan.transitions.get(1).overlapMs = 1234;
        plan.moveTrack(0, 2);
        assertEquals("song 1", plan.tracks.get(0).title);
        assertEquals("song 0", plan.tracks.get(2).title);
        assertEquals(2, plan.transitions.size());
        plan.removeTrack(1);
        assertEquals(1, plan.transitions.size());
    }
}
