package bms.player.beatoraja.arena.bmsir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BMSIRManiacApiClientTest {
	@Test
	void alwaysUsesHttpsForKnownArenaSchemes() {
		String path = "/api/bmsir-arena/v1/maniac/ranking";
		for (String server : new String[]{
				"ws://www.bms-ir.org/ws/arena",
				"wss://www.bms-ir.org/ws/arena",
				"http://www.bms-ir.org",
				"https://www.bms-ir.org"
		}) {
			assertEquals(
					"https://www.bms-ir.org" + path,
					BMSIRManiacApiClient.endpoint(server, path).toString()
			);
		}
	}

	@Test
	void rejectsUnknownOrCredentialBearingEndpoints() {
		assertThrows(
				IllegalArgumentException.class,
				() -> BMSIRManiacApiClient.endpoint("ftp://www.bms-ir.org", "/api")
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> BMSIRManiacApiClient.endpoint("https://user@www.bms-ir.org", "/api")
		);
	}

	@Test
	void validatesSpToDpOwnerSyncIdentityBeforeImport() {
		String base = "a".repeat(64);
		BMSIRManiacSettings settings = new BMSIRManiacSettings();
		settings.setSpToDpDifficulty(2);
		ObjectNode item = new ObjectMapper().createObjectNode();
		item.put("base_sha256", base);
		item.put("ranking_class", "SP_TO_DP");
		item.put("canonical_options", settings.canonicalOptions());
		item.put("algorithm_version", BMSIRManiacSettings.ALGORITHM_VERSION);
		item.put("virtual_chart_id", settings.virtualChartId(base));
		item.put("generation_seed", Long.toUnsignedString(settings.generationSeed(base)));
		item.put("placement_hash", "b".repeat(64));

		BMSIRManiacSettings restored = BMSIRManiacApiClient.validatedSyncSettings(item);
		assertNotNull(restored);
		assertEquals(BMSIRManiacSettings.RankingClass.SP_TO_DP, restored.rankingClass());
		assertEquals(
				"bmsir-maniac-v1-9f5d5eac2efbbe6785ea67262974cc79"
						+ "dcb263b36ff263cdabcbf1ed87c4dd33",
				settings.virtualChartId(base)
		);
		assertEquals("11483438718480268903",
				Long.toUnsignedString(settings.generationSeed(base)));

		ObjectNode mismatch = item.deepCopy();
		mismatch.put("virtual_chart_id", "bmsir-maniac-v1-" + "c".repeat(64));
		assertNull(BMSIRManiacApiClient.validatedSyncSettings(mismatch));
		mismatch = item.deepCopy();
		mismatch.put("generation_seed", "1");
		assertNull(BMSIRManiacApiClient.validatedSyncSettings(mismatch));
		mismatch = item.deepCopy();
		mismatch.put("placement_hash", "not-a-hash");
		assertNull(BMSIRManiacApiClient.validatedSyncSettings(mismatch));
	}
}
