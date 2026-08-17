package bms.player.beatoraja.launcher;

import bms.player.beatoraja.Config;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceConfigurationViewTest {
	@Test
	void builtInTablesHaveTheExpectedThreeExclusiveGroups() {
		List<ResourceConfigurationView.TableChoice> choices = ResourceConfigurationView.tableChoices(
				List.of(), List.of());

		assertEquals(109, Config.AVAILABLE_TABLEURL.length);
		assertEquals(37, Config.BEGINNER_AVAILABLE_TABLEURL.length);
		assertEquals(33, Config.BMSIR_AVAILABLE_TABLEURL.length);
		assertEquals(39, Config.OTHER_AVAILABLE_TABLEURL.length);
		assertEquals(109, new HashSet<>(Arrays.asList(Config.AVAILABLE_TABLEURL)).size());
		assertEquals(37, count(choices, Config.AvailableTableGroup.BEGINNER));
		assertEquals(33, count(choices, Config.AvailableTableGroup.BMSIR));
		assertEquals(39, count(choices, Config.AvailableTableGroup.OTHER));
	}

	@Test
	void crossGameMasterAndGameSpecificTablesStayInTheBeginnerGroup() {
		List<ResourceConfigurationView.TableChoice> choices = ResourceConfigurationView.tableChoices(
				List.of(), List.of());

		ResourceConfigurationView.TableChoice master = choice(
				choices, Config.CROSS_GAME_MASTER_TABLE_URL);
		ResourceConfigurationView.TableChoice chunithm = choice(
				choices, "https://www.bms-ir.org/new/table/62");
		assertEquals(Config.AvailableTableGroup.BEGINNER, master.group());
		assertFalse(master.crossGameSpecific());
		assertEquals(Config.AvailableTableGroup.BEGINNER, chunithm.group());
		assertTrue(chunithm.crossGameSpecific());
	}

	@Test
	void tableSearchMatchesNamesDescriptionsAndUrlsCaseInsensitively() {
		String searchText = "他機種収録BMS - CHUNITHM\nGame-specific beginner table\nhttps://www.bms-ir.org/new/table/62";

		assertTrue(ResourceConfigurationView.tableChoiceMatches(searchText, "chunithm"));
		assertTrue(ResourceConfigurationView.tableChoiceMatches(searchText, "TABLE/62"));
		assertFalse(ResourceConfigurationView.tableChoiceMatches(searchText, "maimai"));
	}

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
		assertEquals(Config.AvailableTableGroup.OTHER, choices.get(choices.size() - 1).group());
	}

	private long count(
			List<ResourceConfigurationView.TableChoice> choices,
			Config.AvailableTableGroup group) {
		return choices.stream().filter(choice -> choice.group() == group).count();
	}

	private ResourceConfigurationView.TableChoice choice(
			List<ResourceConfigurationView.TableChoice> choices, String url) {
		return choices.stream()
				.filter(choice -> choice.url().equals(url))
				.findFirst()
				.orElseThrow();
	}
}
