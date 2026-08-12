package bms.player.beatoraja.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IRConnectionManagerTest {

	@Test
	void convertsNestedDirectoryAndWindowsClassPaths() {
		assertEquals(
				"bms.player.beatoraja.ir.providers.ExampleConnection",
				IRConnectionManager.toClassName("bms/player/beatoraja/ir/providers/ExampleConnection.class")
		);
		assertEquals(
				"bms.player.beatoraja.ir.providers.ExampleConnection",
				IRConnectionManager.toClassName("bms\\player\\beatoraja\\ir\\providers\\ExampleConnection.class")
		);
	}

	@Test
	void ignoresMetadataInnerClassesAndUnrelatedPaths() {
		assertNull(IRConnectionManager.toClassName("bms/player/beatoraja/ir/package-info.class"));
		assertNull(IRConnectionManager.toClassName("bms/player/beatoraja/ir/Example$Helper.class"));
		assertNull(IRConnectionManager.toClassName("other/Example.class"));
		assertNull(IRConnectionManager.toClassName("bms/player/beatoraja/ir/Example.txt"));
	}
}
