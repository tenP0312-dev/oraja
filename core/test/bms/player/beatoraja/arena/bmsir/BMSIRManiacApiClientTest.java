package bms.player.beatoraja.arena.bmsir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
