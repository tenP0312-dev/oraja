package bms.player.beatoraja.launcher;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlayConfigurationSidebarLayoutTest {
	private static final String FXML_NAMESPACE = "http://javafx.com/fxml/1";

	@Test
	void sidebarRailKeepsOneWidthAndProvidesSearch() throws Exception {
		Document document = loadFxml("PlayConfigurationView.fxml");

		Element sidebar = elementWithFxId(document, "sidebarRail");
		assertEquals("220.0", sidebar.getAttribute("minWidth"));
		assertEquals("220.0", sidebar.getAttribute("prefWidth"));
		assertEquals("220.0", sidebar.getAttribute("maxWidth"));
		assertEquals("TextField", elementWithFxId(document, "sidebarSearch").getTagName());
		assertEquals("VBox", elementWithFxId(document, "contextHelpPanel").getTagName());
		assertEquals("VBox", elementWithFxId(document, "classicPlayOptionContent").getTagName());
		Element sidebarPlayOptions = elementWithFxId(document, "sidebarPlayOptionScroll");
		assertEquals("ScrollPane", sidebarPlayOptions.getTagName());
		assertEquals("false", sidebarPlayOptions.getAttribute("managed"));
		assertEquals("false", sidebarPlayOptions.getAttribute("visible"));
		assertEquals("VBox", elementWithFxId(document, "sidebarPlayOptionGroups").getTagName());
	}

	@Test
	void complexSidebarPagesExposeStableActionAndWorkspaceNodes() throws Exception {
		Document play = loadFxml("PlayConfigurationView.fxml");
		for (String id : new String[] {
				"addBgmPathButton", "addSoundPathButton", "importScoreButton"
		}) {
			assertNotNull(elementWithFxId(play, id));
		}

		Document resource = loadFxml("ResourceConfigurationView.fxml");
		for (String id : new String[] {
				"bmsroot", "addSongPathButton", "downloadDirectoryButton", "workDirectoryButton",
				"tableurl", "chooseTablesButton", "addTableUrlButton",
				"updateDatabaseButton", "rebuildDatabaseButton"
		}) {
			assertNotNull(elementWithFxId(resource, id));
		}

		Document skin = loadFxml("SkinConfigurationView.fxml");
		assertNotNull(elementWithFxId(skin, "skinUpdateButton"));
		assertNotNull(elementWithFxId(skin, "skinconfig"));

		Document table = loadFxml("TableEditorView.fxml");
		assertNotNull(elementWithFxId(table, "tableSaveButton"));
		assertNotNull(elementWithFxId(table, "tableEditorTabs"));

		Document discord = loadFxml("DiscordConfigurationView.fxml");
		for (String id : new String[] {
				"addWebhookButton", "removeWebhookButton", "moveWebhookUpButton", "moveWebhookDownButton"
		}) {
			assertNotNull(elementWithFxId(discord, id));
		}
	}

	private Document loadFxml(String name) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		try (InputStream input = getClass().getResourceAsStream(
				"/bms/player/beatoraja/launcher/" + name)) {
			assertNotNull(input);
			return factory.newDocumentBuilder().parse(input);
		}
	}

	private static Element elementWithFxId(Document document, String id) {
		NodeList elements = document.getElementsByTagName("*");
		for (int index = 0; index < elements.getLength(); index++) {
			Element element = (Element) elements.item(index);
			if (id.equals(element.getAttributeNS(FXML_NAMESPACE, "id"))) {
				return element;
			}
		}
		throw new AssertionError("Missing fx:id: " + id);
	}
}
