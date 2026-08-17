package bms.player.beatoraja.launcher;

import bms.player.beatoraja.Config;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceConfigurationViewTest {
	@Test
	void configuredBuiltInTablesRemainVisibleAndMarkedAsAdded() {
		String configured = Config.AVAILABLE_TABLEURL[0];
		String available = Config.AVAILABLE_TABLEURL[1];

		List<ResourceConfigurationView.TableChoice> choices = ResourceConfigurationView.tableChoices(
				List.of(available), List.of(configured));

		assertEquals(Config.AVAILABLE_TABLEURL.length, choices.size());
		assertTrue(choice(choices, configured).alreadyAdded());
		assertFalse(choice(choices, available).alreadyAdded());
	}

	@Test
	void activeCustomUrlsDoNotBecomeBuiltInChoices() {
		String customUrl = "https://example.invalid/custom-table.html";

		List<ResourceConfigurationView.TableChoice> choices = ResourceConfigurationView.tableChoices(
				List.of(), List.of(customUrl));

		assertFalse(choices.stream().anyMatch(choice -> choice.url().equals(customUrl)));
	}

	@Test
	void legacyAvailableUrlsStaySelectableAfterTheBuiltInChoices() {
		String availableUrl = "https://example.invalid/available-table.html";

		List<ResourceConfigurationView.TableChoice> choices = ResourceConfigurationView.tableChoices(
				List.of(availableUrl), List.of());

		assertEquals(availableUrl, choices.get(choices.size() - 1).url());
		assertFalse(choices.get(choices.size() - 1).alreadyAdded());
	}

	private ResourceConfigurationView.TableChoice choice(
			List<ResourceConfigurationView.TableChoice> choices, String url) {
		return choices.stream()
				.filter(choice -> choice.url().equals(url))
				.findFirst()
				.orElseThrow();
	}
}
