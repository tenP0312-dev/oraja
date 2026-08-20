package bms.player.beatoraja.launcher;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.ResourceBundle;

import javafx.geometry.Pos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayConfigurationSidebarLayoutTest {
	private static final String FXML_NAMESPACE = "http://javafx.com/fxml/1";

	@Test
	void sidebarRailKeepsOneWidthAndProvidesSearch() throws Exception {
		Document document = loadFxml("PlayConfigurationView.fxml");

		Element sidebar = elementWithFxId(document, "sidebarRail");
		assertEquals("220.0", sidebar.getAttribute("minWidth"));
		assertEquals("220.0", sidebar.getAttribute("prefWidth"));
		assertEquals("220.0", sidebar.getAttribute("maxWidth"));
		Element search = elementWithFxId(document, "sidebarSearch");
		assertEquals("TextField", search.getTagName());
		assertEquals("%CONFIGURATION_SEARCH", search.getAttribute("accessibleText"));
		Element noResults = elementWithFxId(document, "sidebarSearchNoResults");
		assertEquals("VBox", noResults.getTagName());
		assertEquals("false", noResults.getAttribute("managed"));
		assertEquals("false", noResults.getAttribute("visible"));
		assertTrue(elementWithFxId(document, "configurationContent").getParentNode()
				.isSameNode(noResults.getParentNode()));
		Element contextHelp = elementWithFxId(document, "contextHelpPanel");
		assertEquals("VBox", contextHelp.getTagName());
		assertEquals("false", contextHelp.getAttribute("managed"));
		assertEquals("false", contextHelp.getAttribute("visible"));
		assertEquals("VBox", elementWithFxId(document, "classicPlayOptionContent").getTagName());
		Element sidebarPlayOptions = elementWithFxId(document, "sidebarPlayOptionScroll");
		assertEquals("ScrollPane", sidebarPlayOptions.getTagName());
		assertEquals("false", sidebarPlayOptions.getAttribute("managed"));
		assertEquals("false", sidebarPlayOptions.getAttribute("visible"));
		assertEquals("VBox", elementWithFxId(document, "sidebarPlayOptionGroups").getTagName());
	}

	@Test
	void sidebarScalarRowsUseOneTrailingEditorColumn() {
		assertEquals(480.0, PlayConfigurationView.SIDEBAR_EDITOR_COLUMN_WIDTH);
		assertEquals(Pos.CENTER_RIGHT, PlayConfigurationView.SIDEBAR_STANDALONE_TOGGLE_ALIGNMENT);
	}

	@Test
	void bodyDownloadWarningIsAvailableInEnglishAndJapanese() {
		ResourceBundle english = ResourceBundle.getBundle("resources.UIResources", Locale.ROOT);
		ResourceBundle japanese = ResourceBundle.getBundle("resources.UIResources", Locale.JAPAN);
		assertTrue(english.getString("BMSIR_BODY_DOWNLOAD_WARNING")
				.toLowerCase(Locale.ROOT).contains("not antivirus"));
		assertTrue(japanese.getString("BMSIR_BODY_DOWNLOAD_WARNING").contains("ウイルス検査ではありません"));
	}

	@Test
	void safeTableUpdateGuidanceIsAvailableInEnglishAndJapanese() {
		ResourceBundle english = ResourceBundle.getBundle("resources.UIResources", Locale.ROOT);
		ResourceBundle japanese = ResourceBundle.getBundle("resources.UIResources", Locale.JAPAN);
		assertTrue(english.getString("TABLES_LOAD_ALL_DESCRIPTION").contains("current caches are kept"));
		assertTrue(japanese.getString("TABLES_LOAD_ALL_DESCRIPTION").contains("現在のキャッシュを残します"));
	}

	@Test
	void sidebarNoResultGuidanceIsAvailableInEnglishAndJapanese() {
		ResourceBundle english = ResourceBundle.getBundle("resources.UIResources", Locale.ROOT);
		ResourceBundle japanese = ResourceBundle.getBundle("resources.UIResources", Locale.JAPAN);
		assertEquals("No matching settings", english.getString("CONFIGURATION_SEARCH_NO_MATCHES"));
		assertTrue(english.getString("CONFIGURATION_SEARCH_NO_MATCHES_DESCRIPTION").contains("clear"));
		assertTrue(japanese.getString("CONFIGURATION_SEARCH_NO_MATCHES").contains("一致"));
		assertTrue(japanese.getString("CONFIGURATION_SEARCH_NO_MATCHES_DESCRIPTION").contains("検索を消去"));
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
				"tableurl", "updateAllTablesButton", "chooseTablesButton", "addTableUrlButton",
				"updateDatabaseButton", "rebuildDatabaseButton"
		}) {
			assertNotNull(elementWithFxId(resource, id));
		}
		assertEquals("#loadAllTables",
				elementWithFxId(resource, "updateAllTablesButton").getAttribute("onAction"));

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

	@Test
	void everySidebarSourceIsAnInjectedControllerField() throws Exception {
		assertInjectedNodes("VideoConfigurationView.fxml", VideoConfigurationView.class,
				"bgaExpand", "bgaOp", "displayMode", "maxFps", "missLayerTime", "monitor", "resolution", "vSync");
		assertInjectedNodes("AudioConfigurationView.fxml", AudioConfigurationView.class,
				"audio", "audioFastForward", "audioFreqOption", "audiobuffer", "audioname", "audiosamplerate",
				"audiosim", "bgvolume", "keyvolume", "loopCourseResultSound", "loopResultSound", "normalizeVolume",
				"systemvolume", "wasapiMode");
		assertInjectedNodes("InputConfigurationView.fxml", InputConfigurationView.class,
				"backgroundControllerInput", "controller_tableView", "inputconfig", "inputduration", "jkoc_hack",
				"mouseScratch", "mouseScratchDistance", "mouseScratchMode", "mouseScratchTimeThreshold");
		assertInjectedNodes("ResourceConfigurationView.fxml", ResourceConfigurationView.class,
				"addSongPathButton", "addTableUrlButton", "bmsroot", "chooseTablesButton", "downloadDirectoryButton",
				"rebuildDatabaseButton", "scanSongArchives", "tableurl", "updateDatabaseButton", "updatesong",
				"updateAllTablesButton", "workDirectoryButton");
		assertInjectedNodes("MusicSelectConfigurationView.fxml", MusicSelectConfigurationView.class,
				"analogScroll", "analogTicksPerScroll", "chartReplicationMode", "folderlamp", "maxsearchbar",
				"randomselect", "scrolldurationhigh", "scrolldurationlow", "shownoexistingbar", "skipDecideScreen",
				"songPreview", "useSongInfo");
		assertInjectedNodes("SkinConfigurationView.fxml", SkinConfigurationView.class,
				"skinUpdateButton", "skinconfig", "skinheaderSelector", "skintypeSelector");
		assertInjectedNodes("IRConfigurationView.fxml", IRConfigurationView.class,
				"bmsirArenaEnabled", "bmsirArenaServer", "importrival", "importscore", "irhome", "irname",
				"irpassword", "irsend", "iruserid", "primarybutton");
		assertInjectedNodes("TableEditorView.fxml", TableEditorView.class,
				"tableEditorTabs", "tableName", "tableSaveButton");
		assertInjectedNodes("StreamConfigurationView.fxml", StreamEditorView.class,
				"enableRequest", "maxRequestCount", "notifyRequest");
		assertInjectedNodes("DiscordConfigurationView.fxml", DiscordConfigurationView.class,
				"addWebhookButton", "discordRichPresence", "moveWebhookDownButton", "moveWebhookUpButton",
				"removeWebhookButton", "url", "webhookAvatar", "webhookName", "webhookOption", "webhookURL");
		assertInjectedNodes("ObsConfigurationView.fxml", ObsConfigurationView.class,
				"listContainer", "obsWsConnectButton", "obsWsEnabled", "obsWsHost", "obsWsPass", "obsWsPort",
				"obsWsRecMode", "obsWsRecStopWait");
		assertInjectedNodes("PlayConfigurationView.fxml", PlayConfigurationView.class,
				"addBgmPathButton", "addSoundPathButton", "bgmpath", "clipboardScreenshot", "configurationLayout",
				"defaultDownloadURL", "enableBmsirBodyDownload", "enableHttp", "enableIpfs", "httpDownloadSource", "importScoreButton",
				"ipfsurl", "overrideDownloadURL", "soundpath", "usecim",
				"bmsirArenaGraphOrder", "bmsirArenaLanguage", "bmsirArenaTargetMode", "bmsirCoverChangeStep",
				"bmsirCoverControlMode", "bmsirCoverHispeedAutoAdjustEnabled", "bmsirDanLocalSyncEnabled",
				"bmsirExportVanillaScoreDb", "bmsirHideMissingTableSongs", "bmsirInfoNotificationsEnabled",
				"bmsirPhysicalFolderEmpty", "bmsirPhysicalFolderFilterEnabled", "bmsirPhysicalFolderFilterOptions",
				"bmsirJudgeRankSortEnabled", "bmsirJudgeRankSortSkinNoticeEnabled", "bmsirJudgeTimingRestoreEnabled",
				"bmsirNumpad0", "bmsirNumpad1", "bmsirNumpad2", "bmsirNumpad3",
				"bmsirNumpad4", "bmsirNumpad5", "bmsirNumpad6", "bmsirNumpad7", "bmsirNumpad8",
				"bmsirNumpad9", "bmsirNumpadJudgeTimingStep", "bmsirOneBassEnabled", "bmsirSelectButtonAction",
				"bmsirSelectDifficultyDisplay", "bmsirSelectMode10k", "bmsirSelectMode14k", "bmsirSelectMode24k",
				"bmsirSelectMode24kDp", "bmsirSelectMode5k", "bmsirSelectMode7k", "bmsirSelectMode9k",
				"bmsirSelectModeAll", "bmsirStartButtonAction", "bmsirStartHerePreviewEnabled",
				"bmsirTableLevelDisplayEnabled", "bmsirVisiblePhysicalFolders");
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

	private void assertInjectedNodes(String fxml, Class<?> controller, String... ids) throws Exception {
		Document document = loadFxml(fxml);
		for (String id : ids) {
			assertNotNull(elementWithFxId(document, id),
					() -> fxml + " must declare fx:id=\"" + id + "\"");
			Field field = controller.getDeclaredField(id);
			assertNotNull(field.getAnnotation(javafx.fxml.FXML.class),
					() -> controller.getSimpleName() + "." + id + " must be injected with @FXML");
			assertTrue(javafx.scene.Node.class.isAssignableFrom(field.getType()),
					() -> controller.getSimpleName() + "." + id + " must be a JavaFX Node");
		}
	}
}
