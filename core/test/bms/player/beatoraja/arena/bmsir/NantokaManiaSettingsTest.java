package bms.player.beatoraja.arena.bmsir;

import bms.model.Mode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NantokaManiaSettingsTest {
    @Test void offPreservesExistingIdentitiesAndOnRoundTripsSeparately() {
        var settings = new BMSIRManiacSettings();
        settings.setSpToDpDifficulty(2);
        String canonical = settings.canonicalOptions();
        String storage = settings.storageChartId("a".repeat(64));
        String virtual = settings.virtualChartId("a".repeat(64));
        long seed = settings.generationSeed("a".repeat(64));
        assertFalse(canonical.contains("nantoka"));
        assertEquals("bmsir-maniac-v1-3fd08e14c037c00f44b89ac523cbccc2a22e2be0ff64e3488bb2a7175e194cc6", virtual);
        settings.setNantokaMania(true);
        assertTrue(settings.isActive());
        assertNotEquals(storage, settings.storageChartId("a".repeat(64)));
        assertEquals(BMSIRManiacSettings.RankingClass.LOCAL_ONLY, settings.rankingClass());
        assertNull(settings.virtualChartId("a".repeat(64)));
        assertFalse(BMSIRManiacApiClient.canSubmit(settings));
        assertFalse(BMSIRManiacPlayContext.allowsDuringArena(settings, Mode.BEAT_7K));
        var copy = BMSIRManiacSettings.fromCanonicalOptions(settings.canonicalOptions());
        assertNotNull(copy); assertTrue(copy.isNantokaMania());
        assertEquals(settings.canonicalOptions(), new BMSIRManiacSettings(copy).canonicalOptions());
        settings.setNantokaMania(false);
        assertEquals(canonical, settings.canonicalOptions());
        assertEquals(storage, settings.storageChartId("a".repeat(64)));
        assertEquals(virtual, settings.virtualChartId("a".repeat(64)));
        assertEquals(seed, settings.generationSeed("a".repeat(64)));
    }

    @Test void nantokaManiaOnlyAllowsCourseButAnyOtherEffectBlocksIt() {
        var settings = new BMSIRManiacSettings();
        settings.setNantokaMania(true);
        assertTrue(settings.isNantokaManiaOnly());
        assertTrue(BMSIRManiacPlayContext.allowsDuringCourse(settings, Mode.BEAT_7K));
        assertFalse(BMSIRManiacPlayContext.allowsDuringCourse(settings, Mode.POPN_9K));
        assertFalse(BMSIRManiacPlayContext.allowsDuringCourse(null, Mode.BEAT_7K));

        settings.setTornado(20);
        assertFalse(settings.isNantokaManiaOnly());
        assertFalse(BMSIRManiacPlayContext.allowsDuringCourse(settings, Mode.BEAT_7K));
        settings.setTornado(0);

        settings.setDoubleBattle(true);
        assertFalse(settings.isNantokaManiaOnly());
        settings.setDoubleBattle(false);

        settings.setExtraMode(1);
        assertFalse(settings.isNantokaManiaOnly());
        settings.setExtraMode(0);

        assertTrue(settings.isNantokaManiaOnly());
        settings.setNantokaMania(false);
        assertFalse(settings.isNantokaManiaOnly());
    }

    @Test void unsupportedKeysDoNotApplyAndOwnerSyncCannotImportAsNormal() {
        var settings = new BMSIRManiacSettings(); settings.setNantokaMania(true);
        assertNull(BMSIRManiacPlayContext.effectiveSettings(settings, Mode.POPN_9K));
        assertTrue(BMSIRManiacPlayContext.effectiveSettings(settings, Mode.BEAT_14K).isNantokaMania());
        var item = new ObjectMapper().createObjectNode();
        item.put("canonical_options", settings.canonicalOptions());
        item.put("base_sha256", "a".repeat(64)); item.put("ranking_class", "MANIAC_STANDARD");
        item.put("algorithm_version", 1); item.put("placement_hash", "b".repeat(64));
        assertNull(BMSIRManiacApiClient.validatedSyncSettings(item));
    }
}
