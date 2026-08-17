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
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		Document document;
		try (InputStream input = getClass().getResourceAsStream(
				"/bms/player/beatoraja/launcher/PlayConfigurationView.fxml")) {
			assertNotNull(input);
			document = factory.newDocumentBuilder().parse(input);
		}

		Element sidebar = elementWithFxId(document, "sidebarRail");
		assertEquals("220.0", sidebar.getAttribute("minWidth"));
		assertEquals("220.0", sidebar.getAttribute("prefWidth"));
		assertEquals("220.0", sidebar.getAttribute("maxWidth"));
		assertEquals("TextField", elementWithFxId(document, "sidebarSearch").getTagName());
		assertEquals("VBox", elementWithFxId(document, "contextHelpPanel").getTagName());
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
