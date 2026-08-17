package bms.player.beatoraja.launcher;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;

import bms.player.beatoraja.exceptions.PlayerConfigException;
import bms.player.beatoraja.external.ScoreDataImporter;

import bms.tool.mdprocessor.HttpDownloadProcessor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import org.apache.commons.lang3.compare.ComparableUtils;

import bms.model.Mode;
import bms.player.beatoraja.*;
import bms.player.beatoraja.play.JudgeAlgorithm;
import bms.player.beatoraja.play.TargetProperty;
import bms.player.beatoraja.arena.bmsir.BMSIRNumpadAction;
import bms.player.beatoraja.arena.bmsir.BMSIRSelectKeyMode;
import bms.player.beatoraja.arena.bmsir.BMSIRScoreDatabaseExport;
import bms.player.beatoraja.song.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebView;
import javafx.stage.*;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Callback;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Beatorajaの設定ダイアログ
 *
 * @author exch
 */
public class PlayConfigurationView implements Initializable {
	private static final Logger logger = LoggerFactory.getLogger(PlayConfigurationView.class);
    // TODO スキンプレビュー機能
	private String dbUpdateCheckDialogMessage;

	@FXML
	private Hyperlink newversion;
    @FXML
    private Hyperlink changelog;
    @FXML
    private Label arenaIdentity;
    @FXML
    private ComboBox<String> bmsirRulesetProfile;

    @FXML
	private VBox root;
	@FXML
	private HBox playerPanel;
	@FXML
	private StackPane playerPanelHost;
	@FXML
	private VBox configurationContent;
	@FXML
	private TabPane configurationTabs;
	@FXML
	private VBox sidebarRail;
	@FXML
	private ListView<Tab> sidebarNavigation;
	@FXML
	private TextField sidebarSearch;
	@FXML
	private ToggleButton sidebarPlayerSummary;
	@FXML
	private Label sidebarPlayerInitial;
	@FXML
	private Label sidebarPlayerId;
	@FXML
	private Label sidebarPlayerDetail;
	@FXML
	private Label sidebarPlayerDisclosure;
	@FXML
	private VBox contextHelpPanel;
	@FXML
	private Canvas contextHelpGraphic;
	@FXML
	private Label contextHelpTitle;
	@FXML
	private Label contextHelpDescription;
	@FXML
	private VBox classicPlayOptionContent;
	@FXML
	private ScrollPane sidebarPlayOptionScroll;
	@FXML
	private VBox sidebarPlayOptionGroups;
	@FXML
	private ComboBox<Config.ConfigurationLayout> configurationLayout;
	@FXML
	private Tab videoTab;
	@FXML
	private Tab audioTab;
	@FXML
	private Tab resourceTab;
	@FXML
	private Tab inputTab;
	@FXML
	private Tab skinTab;
	@FXML
	private Tab musicselectTab;
	@FXML
	private Tab optionTab;
	@FXML
	private Tab otherTab;
	@FXML
	private Tab bmsirSpecificTab;
	@FXML
	private Tab irTab;
	@FXML
	private Tab tableTab;
	@FXML
    private Tab streamTab;
	@FXML
	private Tab discordTab;
	@FXML
	private Tab obsTab;
	@FXML
	private HBox controlPanel;

	@FXML
	private ComboBox<String> players;
	@FXML
	private TextField playername;
	@FXML
	private CheckBox bmsirOneBassEnabled;
	@FXML
	private CheckBox bmsirStartHerePreviewEnabled;
	@FXML
	private CheckBox bmsirDanLocalSyncEnabled;
	@FXML
	private ComboBox<String> bmsirStartButtonAction;
	@FXML
	private ComboBox<String> bmsirSelectButtonAction;
	@FXML
	private ComboBox<String> bmsirSelectDifficultyDisplay;
	@FXML
	private CheckBox bmsirSelectModeAll;
	@FXML
	private CheckBox bmsirSelectMode7k;
	@FXML
	private CheckBox bmsirSelectMode14k;
	@FXML
	private CheckBox bmsirSelectMode9k;
	@FXML
	private CheckBox bmsirSelectMode5k;
	@FXML
	private CheckBox bmsirSelectMode10k;
	@FXML
	private CheckBox bmsirSelectMode24k;
	@FXML
	private CheckBox bmsirSelectMode24kDp;
	@FXML
	private CheckBox bmsirHideMissingTableSongs;
	@FXML
	private CheckBox bmsirLongNoteFixed;
	@FXML
	private ComboBox<String> bmsirArenaLanguage;
	@FXML
	private ComboBox<String> bmsirArenaTargetMode;
	@FXML
	private ComboBox<String> bmsirArenaGraphOrder;
	@FXML
	private ComboBox<String> bmsirCoverControlMode;
	@FXML
	private Spinner<Integer> bmsirCoverChangeStep;
	@FXML
	private CheckBox bmsirCoverHispeedAutoAdjustEnabled;
	@FXML
	private CheckBox bmsirJudgeRankSortEnabled;
	@FXML
	private CheckBox bmsirJudgeRankSortSkinNoticeEnabled;
	@FXML
	private ComboBox<String> bmsirNumpad0;
	@FXML
	private ComboBox<String> bmsirNumpad1;
	@FXML
	private ComboBox<String> bmsirNumpad2;
	@FXML
	private ComboBox<String> bmsirNumpad3;
	@FXML
	private ComboBox<String> bmsirNumpad4;
	@FXML
	private ComboBox<String> bmsirNumpad5;
	@FXML
	private ComboBox<String> bmsirNumpad6;
	@FXML
	private ComboBox<String> bmsirNumpad7;
	@FXML
	private ComboBox<String> bmsirNumpad8;
	@FXML
	private ComboBox<String> bmsirNumpad9;
	@FXML
	private Spinner<Integer> bmsirNumpadJudgeTimingStep;
	@FXML
	private CheckBox bmsirJudgeTimingRestoreEnabled;
	@FXML
	private CheckBox bmsirInfoNotificationsEnabled;
	@FXML
	private Button bmsirExportVanillaScoreDb;

	private List<ComboBox<String>> bmsirNumpadCombos;
	private List<CheckBox> bmsirSelectModeChecks;
	private List<Tab> configurationTabOrder = List.of();
	private List<Node> classicPlayerPanelNodes = List.of();
	private final Map<Tab, ContextHelp> tabContextHelp = new IdentityHashMap<>();
	private final Map<String, ContextHelp> controlContextHelp = new HashMap<>();
	private GridPane sidebarPlayerEditor;
	private Label sidebarDisplayNameLabel;
	private boolean playerEditorUsesSidebarLayout;
	private boolean sidebarPlayOptionsInitialized;
	private boolean englishUi;

	@FXML
	private ComboBox<PlayMode> playconfig;
	/**
	 * ハイスピード
	 */
	@FXML
	private Spinner<Double> hispeed;

	@FXML
	private GridPane lr2configuration;
	@FXML
	private GridPane lr2configurationassist;
	@FXML
	private ComboBox<Integer> fixhispeed;
	@FXML
	private Spinner<Integer> gvalue;
	@FXML
	private CheckBox enableConstant;
	@FXML
	private Spinner<Integer> constFadeinTime;
	@FXML
	private Spinner<Double> hispeedmargin;
	@FXML
	private CheckBox hispeedautoadjust;

	@FXML
	private ComboBox<Integer> scoreop;
	@FXML
	private ComboBox<Integer> scoreop2;
	@FXML
	private ComboBox<Integer> doubleop;
	@FXML
	private ComboBox<Integer> gaugeop;
	@FXML
	private ComboBox<Integer> lntype;
	@FXML
	private CheckBox enableLanecover;
	@FXML
	private Spinner<Integer> lanecover;
	@FXML
	private Spinner<Integer> lanecovermarginlow;
	@FXML
	private Spinner<Integer> lanecovermarginhigh;
	@FXML
	private Spinner<Integer> lanecoverswitchduration;
	@FXML
	private CheckBox enableLift;
	@FXML
	private Spinner<Integer> lift;
	@FXML
	private CheckBox enableHidden;
	@FXML
	private Spinner<Integer> hidden;

	@FXML
	private TextField bgmpath;
	@FXML
	private TextField soundpath;

	@FXML
	private NumericSpinner<Integer> notesdisplaytiming;
	@FXML
	private CheckBox notesdisplaytimingautoadjust;
	@FXML
	private CheckBox bpmguide;
	@FXML
	private ComboBox<Integer> gaugeautoshift;
	@FXML
	private ComboBox<Integer> bottomshiftablegauge;
	@FXML
	private CheckBox customjudge;
	@FXML
	private Spinner<Integer> njudgepg;
	@FXML
	private Spinner<Integer> njudgegr;
	@FXML
	private Spinner<Integer> njudgegd;
	@FXML
	private Spinner<Integer> sjudgepg;
	@FXML
	private Spinner<Integer> sjudgegr;
	@FXML
	private Spinner<Integer> sjudgegd;
	@FXML
	private ComboBox<Integer> minemode;
	@FXML
	private ComboBox<Integer> scrollmode;
	@FXML
	private ComboBox<Integer> longnotemode;
	@FXML
	private CheckBox forcedcnendings;
	@FXML
	private Slider longnoterate;
	@FXML
	private Spinner<Integer> hranthresholdbpm;
	@FXML
	private ComboBox<Integer> seventoninepattern;
	@FXML
	private ComboBox<Integer> seventoninetype;
	@FXML
	private Spinner<Integer> exitpressduration;
	@FXML
	private CheckBox chartpreview;
	@FXML
	private CheckBox guidese;
	@FXML
	private CheckBox windowhold;
	@FXML
	private Spinner<Integer> extranotedepth;

	@FXML
	private CheckBox judgeregion;
	@FXML
	private CheckBox markprocessednote;
	@FXML
	private CheckBox showhiddennote;
	@FXML
	private CheckBox showpastnote;
	@FXML
	private ComboBox<String> target;

	@FXML
	private ComboBox<Integer> judgealgorithm;

    @FXML
	private ComboBox<Integer> autosavereplay1;
	@FXML
	private ComboBox<Integer> autosavereplay2;
	@FXML
	private ComboBox<Integer> autosavereplay3;
	@FXML
	private ComboBox<Integer> autosavereplay4;

    @FXML
    private CheckBox usecim;

    @FXML
	private TextField txtTwitterConsumerKey;
    @FXML
	private PasswordField txtTwitterConsumerSecret;

    @FXML
    private Button twitterAuthButton;
    @FXML
    private Label txtTwitterAuthenticated;
    @FXML
    private TextField txtTwitterPIN;
    @FXML
    private Button twitterPINButton;

	@FXML
	private CheckBox enableIpfs;
	@FXML
	private TextField ipfsurl;

	@FXML
	private CheckBox enableHttp;
	@FXML
	private ComboBox<String> httpDownloadSource;
	@FXML
	private TextField defaultDownloadURL;
	@FXML
	private TextField overrideDownloadURL;

	@FXML
	private VBox skin;
	@FXML
	private VideoConfigurationView videoController;
	@FXML
	private AudioConfigurationView audioController;
	@FXML
	private InputConfigurationView inputController;
	@FXML
	private ResourceConfigurationView resourceController;
	@FXML
	private MusicSelectConfigurationView musicselectController;
	@FXML
	private SkinConfigurationView skinController;
	@FXML
	private IRConfigurationView irController;
	@FXML
	private TableEditorView tableController;
	@FXML
    private StreamEditorView streamController;
	@FXML
	private DiscordConfigurationView discordController;
	@FXML
	private ObsConfigurationView obsController;
	@FXML
	private TrainerView trainerController;

	private Config config;
	private PlayerConfig player;

	private MainLoader loader;

	private boolean songUpdated = false;

	private RequestToken requestToken = null;

	@FXML
	public CheckBox clipboardScreenshot;

	static void initComboBox(ComboBox<Integer> combo, final String[] values) {
		combo.setCellFactory((param) -> new OptionListCell(values));
		combo.setButtonCell(new OptionListCell(values));
		for (int i = 0; i < values.length; i++) {
			combo.getItems().add(i);
		}
	}

	private static int bmsirArenaTargetModeIndex(String mode) {
		return switch (mode) {
			case PlayerConfig.BMSIR_ARENA_TARGET_LEADER -> 1;
			case PlayerConfig.BMSIR_ARENA_TARGET_ABOVE -> 2;
			case PlayerConfig.BMSIR_ARENA_TARGET_SPECIFIED -> 3;
			default -> 0;
		};
	}

	private static String bmsirArenaTargetModeValue(int index) {
		return switch (index) {
			case 1 -> PlayerConfig.BMSIR_ARENA_TARGET_LEADER;
			case 2 -> PlayerConfig.BMSIR_ARENA_TARGET_ABOVE;
			case 3 -> PlayerConfig.BMSIR_ARENA_TARGET_SPECIFIED;
			default -> PlayerConfig.BMSIR_ARENA_TARGET_OFF;
		};
	}

	private static int bmsirArenaGraphOrderIndex(String order) {
		return PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY.equals(order) ? 1 : 0;
	}

	private static String bmsirArenaGraphOrderValue(int index) {
		return index == 1
				? PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_ENTRY
				: PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_RANK;
	}

	private static int bmsirCoverControlModeIndex(String mode) {
		return switch (mode) {
			case PlayerConfig.BMSIR_COVER_CONTROL_LR2 -> 1;
			case PlayerConfig.BMSIR_COVER_CONTROL_EXTENDED -> 2;
			default -> 0;
		};
	}

	private static String bmsirCoverControlModeValue(int index) {
		return switch (index) {
			case 1 -> PlayerConfig.BMSIR_COVER_CONTROL_LR2;
			case 2 -> PlayerConfig.BMSIR_COVER_CONTROL_EXTENDED;
			default -> PlayerConfig.BMSIR_COVER_CONTROL_ORAJA;
		};
	}

	private static int bmsirSelectButtonActionIndex(String action) {
		return switch (action) {
			case PlayerConfig.BMSIR_SELECT_ACTION_DIFFICULTY -> 1;
			case PlayerConfig.BMSIR_SELECT_ACTION_KEY_MODE -> 2;
			default -> 0;
		};
	}

	private static String bmsirSelectButtonActionValue(int index) {
		return switch (index) {
			case 1 -> PlayerConfig.BMSIR_SELECT_ACTION_DIFFICULTY;
			case 2 -> PlayerConfig.BMSIR_SELECT_ACTION_KEY_MODE;
			default -> PlayerConfig.BMSIR_SELECT_ACTION_OPTION;
		};
	}

	private static int bmsirSelectDifficultyDisplayIndex(String display) {
		return PlayerConfig.BMSIR_SELECT_DIFFICULTY_DISPLAY_LR2.equals(display)
				? 1
				: 0;
	}

	private static String bmsirSelectDifficultyDisplayValue(int index) {
		return index == 1
				? PlayerConfig.BMSIR_SELECT_DIFFICULTY_DISPLAY_LR2
				: PlayerConfig.BMSIR_SELECT_DIFFICULTY_DISPLAY_SEPARATE;
	}

	public void initialize(URL arg0, ResourceBundle arg1) {
		final long t = System.currentTimeMillis();
		final boolean english = !"ja".equalsIgnoreCase(arg1.getLocale().getLanguage());
		englishUi = english;
		dbUpdateCheckDialogMessage = arg1.getString("REBUILD_DATABASE_MESSAGE");
		arenaIdentity.setText(Version.getArenaDisplayName());
		bmsirArenaLanguage.getItems().setAll("日本語", "English");
		List<String> shortButtonActions = english
				? List.of(
						"None",
						"Change difficulty",
						"Change key mode"
				)
				: List.of(
						"なし",
						"難易度変更",
						"鍵盤数変更"
				);
		bmsirStartButtonAction.getItems().setAll(shortButtonActions);
		bmsirSelectButtonAction.getItems().setAll(shortButtonActions);
		bmsirSelectDifficultyDisplay.getItems().setAll(english
				? List.of(
						"Separate rows; move the cursor",
						"One grouped row (LR2 style)"
				)
				: List.of(
						"個別表示（カーソルを移動）",
						"1曲にまとめる（LR2式）"
				));
		bmsirSelectModeChecks = List.of(
				bmsirSelectModeAll,
				bmsirSelectMode7k,
				bmsirSelectMode14k,
				bmsirSelectMode9k,
				bmsirSelectMode5k,
				bmsirSelectMode10k,
				bmsirSelectMode24k,
				bmsirSelectMode24kDp
		);
		bmsirRulesetProfile.getItems().setAll("LR2", "oraja");
		bmsirArenaTargetMode.getItems().setAll(english
				? List.of("OFF", "1st-place opponent", "Opponent directly above", "Specified player")
				: List.of("OFF", "1位の対戦相手", "自分の直上", "指定プレイヤー"));
		bmsirArenaGraphOrder.getItems().setAll(english
				? List.of("Rank order", "Fixed entry order")
				: List.of("順位順", "入室順固定"));
		bmsirCoverControlMode.getItems().setAll(english
				? List.of(
						"oraja default (START + keys 1-7: HI-SPEED)",
						"LR2 style (keys 6/7: SUD+ while visible)",
						"Extended (keys 6/7: SUD+/HIDDEN/LIFT)"
				)
				: List.of(
						"oraja標準（START+1～7: ハイスピード）",
						"LR2式（SUD+表示中のみ6/7: SUD+）",
						"拡張（6/7: SUD+/HIDDEN/LIFT）"
				));
		bmsirCoverChangeStep.setValueFactory(
				new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 10)
		);
		bmsirJudgeRankSortSkinNoticeEnabled.disableProperty().bind(
				bmsirJudgeRankSortEnabled.selectedProperty().not()
		);
		bmsirNumpadCombos = List.of(
				bmsirNumpad0,
				bmsirNumpad1,
				bmsirNumpad2,
				bmsirNumpad3,
				bmsirNumpad4,
				bmsirNumpad5,
				bmsirNumpad6,
				bmsirNumpad7,
				bmsirNumpad8,
				bmsirNumpad9
		);
		List<String> numpadLabels = Arrays.stream(BMSIRNumpadAction.values())
				.map(action -> action.label(english))
				.toList();
		bmsirNumpadCombos.forEach(combo -> combo.getItems().setAll(numpadLabels));
		bmsirNumpadJudgeTimingStep.setValueFactory(
				new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 1)
		);
		lr2configuration.setHgap(25);
		lr2configuration.setVgap(4);
		lr2configurationassist.setHgap(25);
		lr2configurationassist.setVgap(4);


		String[] scoreOptions = new String[] { "OFF", "MIRROR", "RANDOM", "R-RANDOM", "S-RANDOM", "SPIRAL", "H-RANDOM",
				"ALL-SCR", "RANDOM-EX", "S-RANDOM-EX" };
		initComboBox(scoreop, scoreOptions);
		initComboBox(scoreop2, scoreOptions);
		initComboBox(doubleop, new String[] { "OFF", "FLIP" });
		initComboBox(seventoninepattern, new String[] { "OFF", "SC1KEY2~8", "SC1KEY3~9", "SC2KEY3~9", "SC8KEY1~7", "SC9KEY1~7", "SC9KEY2~8" });
		String[] seventoninestring = new String[]{arg1.getString("SEVEN_TO_NINE_OFF"),arg1.getString("SEVEN_TO_NINE_NO_MASHING"),arg1.getString("SEVEN_TO_NINE_ALTERNATION")};
		initComboBox(seventoninetype, seventoninestring);
		initComboBox(gaugeop, new String[] { "ASSIST EASY", "EASY", "NORMAL", "HARD", "EX-HARD", "HAZARD" });
		initComboBox(fixhispeed, new String[] { "OFF", "START BPM", "MAX BPM", "MAIN BPM", "MIN BPM" });
		playconfig.getItems().setAll(PlayMode.values());
		initComboBox(lntype, new String[] { "LONG NOTE", "CHARGE NOTE", "HELL CHARGE NOTE" });
		lntype.getSelectionModel().select(0);
		lntype.setDisable(true);
		initComboBox(gaugeautoshift, new String[] { "NONE", "CONTINUE", "SURVIVAL TO GROOVE","BEST CLEAR","SELECT TO UNDER" });
		initComboBox(bottomshiftablegauge, new String[] { "ASSIST EASY", "EASY", "NORMAL" });
		initComboBox(minemode, new String[] { "OFF", "REMOVE", "ADD RANDOM", "ADD NEAR", "ADD ALL" });
		initComboBox(scrollmode, new String[] { "OFF", "REMOVE", "ADD" });
		initComboBox(longnotemode, new String[] { "OFF", "REMOVE", "ADD LN", "ADD CN", "ADD HCN", "ADD ALL" });

		initComboBox(judgealgorithm, new String[] { arg1.getString("JUDGEALG_LR2"), arg1.getString("JUDGEALG_AC"), arg1.getString("JUDGEALG_BOTTOM_PRIORITY") });
		String[] autosaves = new String[]{arg1.getString("NONE"),arg1.getString("BETTER_SCORE"),arg1.getString("BETTER_OR_SAME_SCORE"),arg1.getString("BETTER_MISSCOUNT")
				,arg1.getString("BETTER_OR_SAME_MISSCOUNT"),arg1.getString("BETTER_COMBO"),arg1.getString("BETTER_OR_SAME_COMBO"),
				arg1.getString("BETTER_LAMP"),arg1.getString("BETTER_OR_SAME_LAMP"),arg1.getString("BETTER_ALL"),arg1.getString("ALWAYS")};
		initComboBox(autosavereplay1, autosaves);
		initComboBox(autosavereplay2, autosaves);
		initComboBox(autosavereplay3, autosaves);
		initComboBox(autosavereplay4, autosaves);

		httpDownloadSource.getItems().setAll(HttpDownloadProcessor.DOWNLOAD_SOURCES.keySet());
		notesdisplaytiming.setValueFactoryValues(PlayerConfig.JUDGETIMING_MIN, PlayerConfig.JUDGETIMING_MAX, 0, 1);
		resourceController.init(this);
		discordController.init(this);
		obsController.init(this);
		initializeConfigurationShell(arg1);

		checkNewVersion();
		logger.info("初期化時間(ms) : " + (System.currentTimeMillis() - t));
	}

	private void initializeConfigurationShell(ResourceBundle bundle) {
		configurationLayout.getItems().setAll(Config.ConfigurationLayout.values());
		configurationLayout.setCellFactory(list -> configurationLayoutCell(bundle));
		configurationLayout.setButtonCell(configurationLayoutCell(bundle));
		configurationLayout.valueProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null) {
				applyConfigurationLayout(newValue);
			}
		});

		configurationTabOrder = List.copyOf(configurationTabs.getTabs());
		sidebarNavigation.getItems().setAll(configurationTabOrder);
		sidebarNavigation.setPlaceholder(new Label(
				englishUi ? "No matching settings" : "一致する設定はありません"
		));
		sidebarNavigation.setCellFactory(list -> configurationTabCell());
		sidebarSearch.textProperty().addListener(
				(observable, oldValue, newValue) -> updateSidebarNavigationItems(newValue)
		);
		sidebarNavigation.getSelectionModel().selectedItemProperty().addListener(
				(observable, oldTab, newTab) -> {
					if (newTab != null && configurationTabs.getSelectionModel().getSelectedItem() != newTab) {
						configurationTabs.getSelectionModel().select(newTab);
					}
				}
		);
		configurationTabs.getSelectionModel().selectedItemProperty().addListener(
				(observable, oldTab, newTab) -> {
					if (newTab != null) {
						sidebarNavigation.getSelectionModel().select(newTab);
						showTabContextHelp(newTab);
						updateSidebarTabPresentation(newTab);
					}
				}
		);
		sidebarNavigation.getSelectionModel().select(
				configurationTabs.getSelectionModel().getSelectedItem()
		);

		sidebarPlayerSummary.selectedProperty().addListener(
				(observable, oldValue, selected) -> updatePlayerPanelVisibility()
		);
		players.valueProperty().addListener((observable, oldValue, newValue) -> updateSidebarPlayerSummary());
		playername.textProperty().addListener((observable, oldValue, newValue) -> updateSidebarPlayerSummary());
		bmsirRulesetProfile.valueProperty().addListener((observable, oldValue, newValue) -> updateSidebarPlayerSummary());

		initializeSidebarPlayerEditor();
		initializeContextHelp();
		initializeSidebarPlayOptions();
		sidebarNavigation.refresh();
		configurationLayout.setValue(Config.ConfigurationLayout.CLASSIC);
	}

	private ListCell<Tab> configurationTabCell() {
		return new ListCell<>() {
			private final Canvas icon = new Canvas(24, 22);
			private final Label label = new Label();
			private final HBox row = new HBox(10, icon, label);

			{
				row.setAlignment(Pos.CENTER_LEFT);
				row.setMouseTransparent(true);
				label.getStyleClass().add("sidebar-nav-label");
			}

			@Override
			protected void updateItem(Tab item, boolean empty) {
				super.updateItem(item, empty);
				refreshGraphic();
			}

			@Override
			public void updateSelected(boolean selected) {
				super.updateSelected(selected);
				refreshGraphic();
			}

			private void refreshGraphic() {
				Tab item = getItem();
				if (isEmpty() || item == null) {
					setText(null);
					setGraphic(null);
					return;
				}
				label.setText(item.getText());
				HelpGraphic graphic = Optional.ofNullable(tabContextHelp.get(item))
						.map(ContextHelp::graphic)
						.orElse(HelpGraphic.OTHER);
				drawSidebarGraphic(icon, graphic, isSelected());
				setText(null);
				setGraphic(row);
				setAccessibleText(item.getText());
			}
		};
	}

	private void updateSidebarNavigationItems(String query) {
		String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<Tab> filtered = configurationTabOrder.stream()
				.filter(tab -> normalized.isEmpty()
						|| tab.getText().toLowerCase(Locale.ROOT).contains(normalized))
				.toList();
		sidebarNavigation.getItems().setAll(filtered);
		Tab current = configurationTabs.getSelectionModel().getSelectedItem();
		if (filtered.contains(current)) {
			sidebarNavigation.getSelectionModel().select(current);
		}
	}

	private void initializeSidebarPlayerEditor() {
		classicPlayerPanelNodes = List.copyOf(playerPanel.getChildren());
		sidebarDisplayNameLabel = new Label(englishUi ? "Display name" : "表示名");
		sidebarPlayerEditor = new GridPane();
		sidebarPlayerEditor.getStyleClass().add("sidebar-player-editor");

		ColumnConstraints playerColumn = new ColumnConstraints();
		playerColumn.setPercentWidth(30);
		playerColumn.setHgrow(Priority.ALWAYS);
		ColumnConstraints nameColumn = new ColumnConstraints();
		nameColumn.setPercentWidth(62);
		nameColumn.setHgrow(Priority.ALWAYS);
		ColumnConstraints addColumn = new ColumnConstraints();
		addColumn.setPercentWidth(8);
		sidebarPlayerEditor.getColumnConstraints().setAll(playerColumn, nameColumn, addColumn);
	}

	private void useSidebarPlayerEditor(boolean sidebar) {
		if (playerEditorUsesSidebarLayout == sidebar) {
			return;
		}
		if (sidebar) {
			playerPanel.getChildren().clear();
			sidebarPlayerEditor.getChildren().clear();
			sidebarPlayerEditor.add(classicPlayerPanelNodes.get(0), 0, 0);
			sidebarPlayerEditor.add(sidebarDisplayNameLabel, 1, 0);
			sidebarPlayerEditor.add(classicPlayerPanelNodes.get(1), 0, 1);
			sidebarPlayerEditor.add(classicPlayerPanelNodes.get(2), 1, 1);
			sidebarPlayerEditor.add(classicPlayerPanelNodes.get(3), 2, 1);
			sidebarPlayerEditor.add(classicPlayerPanelNodes.get(4), 0, 2, 3, 1);
			sidebarPlayerEditor.add(classicPlayerPanelNodes.get(5), 0, 3, 3, 1);
			for (int index : List.of(1, 2, 5)) {
				if (classicPlayerPanelNodes.get(index) instanceof Control control) {
					control.setMaxWidth(Double.MAX_VALUE);
					GridPane.setHgrow(control, Priority.ALWAYS);
				}
			}
			playerPanelHost.getChildren().setAll(sidebarPlayerEditor);
		} else {
			sidebarPlayerEditor.getChildren().removeAll(classicPlayerPanelNodes);
			playerPanel.getChildren().setAll(classicPlayerPanelNodes);
			playerPanelHost.getChildren().setAll(playerPanel);
		}
		playerEditorUsesSidebarLayout = sidebar;
	}

	private ListCell<Config.ConfigurationLayout> configurationLayoutCell(ResourceBundle bundle) {
		return new ListCell<>() {
			@Override
			protected void updateItem(Config.ConfigurationLayout item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null
						? null
						: bundle.getString(item == Config.ConfigurationLayout.SIDEBAR
								? "CONFIGURATION_LAYOUT_SIDEBAR"
								: "CONFIGURATION_LAYOUT_CLASSIC"));
			}
		};
	}

	private void applyConfigurationLayout(Config.ConfigurationLayout layout) {
		boolean sidebar = layout == Config.ConfigurationLayout.SIDEBAR;
		useSidebarPlayerEditor(sidebar);
		setManagedVisible(sidebarRail, sidebar);
		setManagedVisible(contextHelpPanel, sidebar);
		setManagedVisible(classicPlayOptionContent, !sidebar);
		setManagedVisible(sidebarPlayOptionScroll, sidebar);
		if (sidebar) {
			if (!configurationContent.getStyleClass().contains("sidebar-mode")) {
				configurationContent.getStyleClass().add("sidebar-mode");
			}
			if (!configurationTabs.getStyleClass().contains("sidebar-content-tabs")) {
				configurationTabs.getStyleClass().add("sidebar-content-tabs");
			}
			sidebarNavigation.getSelectionModel().select(
					configurationTabs.getSelectionModel().getSelectedItem()
			);
			showTabContextHelp(configurationTabs.getSelectionModel().getSelectedItem());
		} else {
			configurationContent.getStyleClass().remove("sidebar-mode");
			configurationTabs.getStyleClass().remove("sidebar-content-tabs");
			configurationTabs.getStyleClass().remove("sidebar-form-tab");
			sidebarPlayerSummary.setSelected(false);
		}
		updateSidebarTabPresentation(configurationTabs.getSelectionModel().getSelectedItem());
		updatePlayerPanelVisibility();
	}

	private void updateSidebarTabPresentation(Tab tab) {
		boolean sidebarPlayOptions = configurationLayout.getValue() == Config.ConfigurationLayout.SIDEBAR
				&& tab == optionTab;
		if (sidebarPlayOptions) {
			if (!configurationTabs.getStyleClass().contains("sidebar-form-tab")) {
				configurationTabs.getStyleClass().add("sidebar-form-tab");
			}
		} else {
			configurationTabs.getStyleClass().remove("sidebar-form-tab");
		}
	}

	private static void setManagedVisible(Node node, boolean visible) {
		node.setManaged(visible);
		node.setVisible(visible);
	}

	private void updatePlayerPanelVisibility() {
		boolean sidebar = configurationLayout.getValue() == Config.ConfigurationLayout.SIDEBAR;
		setManagedVisible(playerPanelHost, !sidebar || sidebarPlayerSummary.isSelected());
	}

	private void updateSidebarPlayerSummary() {
		if (sidebarPlayerSummary == null) {
			return;
		}
		String playerId = players.getValue() == null || players.getValue().isBlank()
				? "player1"
				: players.getValue();
		String displayName = playername.getText() == null || playername.getText().isBlank()
				? "NO NAME"
				: playername.getText().trim();
		String ruleset = bmsirRulesetProfile.getValue() == null
				? "LR2"
				: bmsirRulesetProfile.getValue();
		sidebarPlayerInitial.setText(playerInitial(playerId));
		sidebarPlayerId.setText(playerId);
		sidebarPlayerDetail.setText(displayName + " · " + ruleset);
		sidebarPlayerSummary.setAccessibleText(
				(englishUi ? "Player settings: " : "プレイヤー設定: ")
						+ playerId + ", " + displayName + ", " + ruleset
		);
	}

	private static String playerInitial(String playerId) {
		if (playerId.matches("(?i)player\\d+")) {
			return "P" + playerId.replaceAll("\\D", "");
		}
		String compact = playerId.replaceAll("\\s+", "");
		return compact.substring(0, Math.min(2, compact.length())).toUpperCase(Locale.ROOT);
	}

	private void initializeContextHelp() {
		registerTabHelp(videoTab,
				"画面",
				"表示先と描画負荷、BGAの見せ方を決めます。まず「画面モード」「解像度」「垂直同期」だけ確認すれば十分です。",
				"Video",
				"Choose the display target, rendering load, and BGA presentation. Start with Display Mode, Resolution, and Vsync.",
				HelpGraphic.DISPLAY);
		registerTabHelp(audioTab, "音声", "出力方式、遅延、同時発音数と音量を調整します。", "Audio", "Configure output, latency, simultaneous sounds, and volume.", HelpGraphic.AUDIO);
		registerTabHelp(inputTab, "入力", "鍵盤モード、コントローラー、スクラッチ入力を設定します。", "Input", "Configure key mode, controllers, and scratch input.", HelpGraphic.INPUT);
		registerTabHelp(resourceTab, "リソース", "BMSフォルダ、難易度表、楽曲データベースの更新方法を管理します。", "Resources", "Manage BMS folders, difficulty tables, and song database updates.", HelpGraphic.RESOURCE);
		registerTabHelp(musicselectTab, "選曲", "選曲画面の移動、プレビュー、検索と表示方法を調整します。", "Music Select", "Adjust navigation, previews, search, and presentation in Music Select.", HelpGraphic.MUSIC);
		registerTabHelp(optionTab, "プレイ OP", "プレイ中の見え方・譜面オプション・ゲージを設定します。本体と同じ項目順のまま、各設定の説明を追加しています。", "Play Options", "Configure note visibility, chart options, and gauges in the original order, with an explanation for every setting.", HelpGraphic.PLAY);
		registerTabHelp(skinTab, "スキン", "使用するスキンとスキン固有の項目、BGM・効果音フォルダを選びます。", "Skin", "Choose skins, skin-specific values, BGM, and sound folders.", HelpGraphic.SKIN);
		registerTabHelp(otherTab, "その他", "設定画面の表示方式、キャッシュ、ダウンロードなどの補助設定です。", "Other", "Auxiliary settings for the configuration layout, cache, and downloads.", HelpGraphic.OTHER);
		registerTabHelp(bmsirSpecificTab, "BMS-IR固有設定", "BMS-IR向けの操作、表示、同期、ショートカットを設定します。", "BMS-IR Features", "Configure BMS-IR controls, presentation, synchronization, and shortcuts.", HelpGraphic.BMSIR);
		registerTabHelp(irTab, "IR", "IRアカウント、送信方法、ライバル取得とArena接続を設定します。", "IR", "Configure the IR account, score sending, rivals, and Arena connection.", HelpGraphic.IR);
		registerTabHelp(tableTab, "Table", "コースとフォルダをまとめたローカルテーブルを編集します。", "Table", "Edit local tables containing courses and folders.", HelpGraphic.TABLE);
		registerTabHelp(streamTab, "Stream", "配信リクエストの受付と表示件数を設定します。", "Stream", "Configure stream requests and how many are retained.", HelpGraphic.STREAM);
		registerTabHelp(discordTab, "Discord", "Rich Presenceとスコア送信用Webhookを設定します。", "Discord", "Configure Rich Presence and score Webhooks.", HelpGraphic.CHAT);
		registerTabHelp(obsTab, "OBS", "OBS WebSocketへ接続し、録画とシーン切替をゲーム状態に連動させます。", "OBS", "Connect to OBS WebSocket and link recording and scenes to game state.", HelpGraphic.OBS);

		registerControlHelp("displayMode", "画面モード", "ウィンドウ、ボーダーレス、フルスクリーンのどれで起動するかを選びます。", "Display Mode", "Choose Window, Borderless, or Fullscreen startup.", HelpGraphic.DISPLAY);
		registerControlHelp("resolution", "解像度", "ゲーム画面の幅と高さです。ディスプレイとスキンに合う値を選びます。", "Resolution", "Choose the game width and height to match the display and skin.", HelpGraphic.DISPLAY);
		registerControlHelp("monitor", "表示モニター", "複数画面を使用している場合の起動先を選びます。", "Monitor", "Choose which display receives the game window.", HelpGraphic.DISPLAY);
		registerControlHelp("vSync", "垂直同期", "画面更新をモニターの周期に合わせ、ティアリングを抑えます。遅延が気になる場合はOFFも試せます。", "Vsync", "Synchronize frames to the monitor to reduce tearing; try OFF when latency matters more.", HelpGraphic.DISPLAY);
		registerControlHelp("maxFps", "最大FPS", "垂直同期がOFFのときの描画上限です。0は上限なしです。", "Maximum FPS", "Cap rendering when Vsync is off; 0 means uncapped.", HelpGraphic.DISPLAY);
		registerControlHelp("bgaOp", "BGA", "譜面の背景アニメーションを表示するか決めます。負荷を下げたい場合はOFFにします。", "BGA", "Choose whether chart background animation is shown; turn it off to reduce load.", HelpGraphic.DISPLAY);
		registerControlHelp("missLayerTime", "ミスレイヤー表示時間", "ミス時に表示されるBGAレイヤーの長さをミリ秒で設定します。", "Miss-layer duration", "Set how long the miss BGA layer stays visible, in milliseconds.", HelpGraphic.DISPLAY);
		registerControlHelp("bgaExpand", "BGAの拡大方法", "縦横比を保つ、領域いっぱいに広げる、拡大しない、から選びます。", "BGA scaling", "Choose aspect-preserving, full-area, or no expansion.", HelpGraphic.DISPLAY);
		registerControlHelp("audio", "音声出力", "OpenAL、PortAudio、ASIOなど使用する出力方式を選びます。", "Audio output", "Choose OpenAL, PortAudio, ASIO, or another available output path.", HelpGraphic.AUDIO);
		registerControlHelp("audiobuffer", "オーディオバッファ", "小さいほど遅延は減りますが、音切れしやすくなります。", "Audio buffer", "Lower values reduce latency but make dropouts more likely.", HelpGraphic.AUDIO);
		registerControlHelp("inputduration", "最小入力間隔", "同じ入力を再び受け付けるまでの最短時間を設定します。", "Minimum input interval", "Set the shortest interval before the same input is accepted again.", HelpGraphic.INPUT);
		registerControlHelp("bmsroot", "BMS Path", "楽曲を置いているルートフォルダを登録します。", "BMS Path", "Register the root folders containing songs.", HelpGraphic.RESOURCE);
		registerControlHelp("tableurl", "難易度表", "選曲画面へ読み込む難易度表を管理します。", "Difficulty tables", "Manage difficulty tables loaded into Music Select.", HelpGraphic.RESOURCE);
		registerControlHelp("songPreview", "楽曲プレビュー", "選曲中の試聴をOFF、1回、ループから選びます。", "Song preview", "Choose off, one-shot, or looping audio preview in Music Select.", HelpGraphic.MUSIC);
		registerControlHelp("hispeed", "HI-SPEED", "ノーツのスクロール速度倍率です。", "HI-SPEED", "Set the note scroll-speed multiplier.", HelpGraphic.PLAY);
		registerControlHelp("gvalue", "ノーツ表示時間", "ノーツが判定位置へ届くまでの表示時間、いわゆる緑数字です。単位はmsです。", "Note display time", "Set the time until notes reach the judgment line, commonly called green number, in ms.", HelpGraphic.PLAY);
		registerControlHelp("chartpreview", "チャートプレビュー", "楽曲ロード中にSTARTまたはSELECTを押している間、プレイ画面上で譜面の流れを先行表示します。選曲時の試聴機能ではありません。", "Chart Preview", "While loading, hold START or SELECT to preview chart movement on the play field. This is not the Music Select audio preview.", HelpGraphic.PLAY);
		registerControlHelp("configurationLayout", "設定画面", "クラシックは従来の上タブ、サイドバーは左側のカテゴリと図付き説明を使用します。設定項目と保存内容は共通です。", "Configuration screen", "Classic uses the existing top tabs; Sidebar uses left categories and illustrated help. Both edit the same settings.", HelpGraphic.OTHER);
		registerControlHelp("irpassword", "Password", "IRへログインするためのパスワードです。画面上では伏せて表示されます。", "Password", "The IR login password; it remains masked on screen.", HelpGraphic.IR);
		registerControlHelp("obsWsConnectButton", "OBSへ接続", "入力した接続先を使ってOBSからシーン一覧を取得します。", "Connect to OBS", "Use the entered connection details to retrieve scenes from OBS.", HelpGraphic.OBS);

		for (Tab tab : configurationTabOrder) {
			installContextHelp(tab, tab.getContent());
		}
		showTabContextHelp(configurationTabs.getSelectionModel().getSelectedItem());
	}

	private void initializeSidebarPlayOptions() {
		if (sidebarPlayOptionsInitialized) {
			return;
		}

		VBox visibility = sidebarSettingCard(
				sidebarSettingRow("設定対象モード", "Mode",
						"7K、14Kなど、どのプレイモードの設定を編集するかを選びます。",
						"Choose which play mode, such as 7K or 14K, you are editing.",
						sidebarCombo(playconfig)),
				sidebarSettingRow("HI-SPEED", "HI-SPEED",
						"ノーツのスクロール速度倍率です。",
						"Sets the note scroll-speed multiplier.",
						sidebarSpinner(hispeed)),
				sidebarSettingRow("ノーツ表示時間", "Note display time",
						"ノーツが判定位置へ届くまでの表示時間（いわゆる緑数字）です。単位は ms です。",
						"Time until notes reach the judgment line, commonly called the green number. The unit is ms.",
						sidebarSpinner(gvalue)),
				sidebarSettingRow("レーンカバー", "Lane Cover",
						"ONにするとレーン上部を隠します。右の数値が隠す量です。",
						"When ON, hides the upper part of the lane. The value on the right is the cover amount.",
						sidebarToggleWithValue(enableLanecover, sidebarSpinner(lanecover))),
				sidebarSettingRow("変化間隔（低速）", "Change step (low)",
						"低いHI-SPEED帯で、カバー操作1回あたりに変える量です。",
						"Amount changed by one cover operation in the lower HI-SPEED range.",
						sidebarSpinner(lanecovermarginlow)),
				sidebarSettingRow("変化間隔（高速）", "Change step (high)",
						"高いHI-SPEED帯で、カバー操作1回あたりに変える量です。",
						"Amount changed by one cover operation in the higher HI-SPEED range.",
						sidebarSpinner(lanecovermarginhigh)),
				sidebarSettingRow("変化速度切り替え時間", "Change-speed switch time",
						"カバー操作を長押ししたとき、低速から高速変更へ切り替わるまでの時間です。単位は ms です。",
						"Time before a held cover operation switches from slow to fast changes, in ms.",
						sidebarSpinner(lanecoverswitchduration)),
				sidebarSettingRow("LIFT", "LIFT",
						"ONにすると判定位置を上へ持ち上げます。右の数値が持ち上げる量です。",
						"When ON, raises the judgment line. The value on the right is the lift amount.",
						sidebarToggleWithValue(enableLift, sidebarSpinner(lift))),
				sidebarSettingRow("HIDDEN", "HIDDEN",
						"ONにするとレーン下側でノーツを隠します。右の数値が隠す量です。",
						"When ON, hides notes near the bottom of the lane. The value on the right is the hidden amount.",
						sidebarToggleWithValue(enableHidden, sidebarSpinner(hidden))),
				sidebarSettingRow("ノーツ表示タイミング", "Note display timing",
						"ノーツ描画だけを前後にずらします。音・判定タイミングは変わりません。",
						"Offsets note drawing only. Audio and judgment timing do not change.",
						sidebarSpinner(notesdisplaytiming)),
				sidebarSettingRow("ノーツ表示タイミング自動調整", "Auto-adjust note display timing",
						"プレイ結果に合わせてノーツ表示タイミングを自動調整します。",
						"Automatically adjusts note display timing from play results.",
						sidebarToggle(notesdisplaytimingautoadjust)),
				sidebarSettingRow("ハイスピード固定", "HI-SPEED fix",
						"BPMが変化する譜面で、どのBPMを基準にスクロール速度を固定するかを選びます。",
						"Choose which BPM is used to keep scroll speed stable on charts with BPM changes.",
						sidebarCombo(fixhispeed)),
				sidebarSettingRow("HI-SPEED変化間隔", "HI-SPEED change step",
						"プレイ中にHI-SPEEDを変更するときの1回あたりの変化量です。",
						"Amount changed by one HI-SPEED operation during play.",
						sidebarSpinner(hispeedmargin)),
				sidebarSettingRow("HI-SPEED固定自動調整", "Auto-adjust HI-SPEED fix",
						"レーンカバー変更時に、現在のBPMへHI-SPEED固定を自動で合わせます。",
						"Automatically updates the HI-SPEED fix for the current BPM when Lane Cover changes.",
						sidebarToggle(hispeedautoadjust))
		);

		VBox chart = sidebarSettingCard(
				sidebarSettingRow("譜面オプション", "Note modifier",
						"1P側のノーツ配置を変更します。OFF、MIRROR、RANDOMなどから選びます。",
						"Changes the 1P note layout. Choose OFF, MIRROR, RANDOM, and other modifiers.",
						sidebarCombo(scoreop)),
				sidebarSettingRow("譜面オプション（2P）", "Note modifier (2P)",
						"DP時の2P側ノーツ配置を変更します。",
						"Changes the 2P note layout in DP play.",
						sidebarCombo(scoreop2)),
				sidebarSettingRow("DPオプション", "DP option",
						"DP譜面の左右を入れ替えるFLIPを設定します。",
						"Enables FLIP to swap the two sides of a DP chart.",
						sidebarCombo(doubleop)),
				sidebarSettingRow("ゲージ", "Gauge",
						"プレイに使用するグルーブゲージの種類を選びます。",
						"Choose the groove gauge used for play.",
						sidebarCombo(gaugeop)),
				sidebarSettingRow("ロングノート種類", "Long-note type",
						"ロングノートの判定方式を選びます。通常は譜面側の指定を使用します。",
						"Choose the long-note judgment type. Normally the chart definition is used.",
						sidebarCombo(lntype)),
				sidebarSettingRow("CN終端をLN上に置く", "CN endings on LNs",
						"CNの終端が別のLNと重なる配置を許可します。",
						"Allows CN endings to overlap another long note.",
						sidebarToggle(forcedcnendings)),
				sidebarSettingRow("H-RAN連打BPM（16分）", "H-RANDOM threshold BPM",
						"H-RANDOMが連打とみなす16分間隔の基準BPMです。",
						"Threshold BPM used by H-RANDOM to recognize 16th-note repetitions.",
						sidebarSpinner(hranthresholdbpm)),
				sidebarSettingRow("ノーツ表示時間固定", "CONSTANT",
						"ONにすると譜面のBPM変化にかかわらずノーツの表示時間を一定にします。右はフェードイン時間です。",
						"Keeps note display time constant through BPM changes. The value on the right is the fade-in time.",
						sidebarToggleWithValue(enableConstant, sidebarSpinner(constFadeinTime)))
		);

		VBox assist = sidebarSettingCard(
				sidebarSettingRow("EXPAND JUDGE", "EXPAND JUDGE",
						"鍵盤とスクラッチの判定幅を個別に拡大・縮小できるようにします。",
						"Enables separate scaling of key and scratch judgment windows.",
						sidebarToggle(customjudge)),
				sidebarSettingRow("鍵盤の判定幅", "Key judgment windows",
						"鍵盤のPG・GR・GD判定幅を標準値に対する百分率で設定します。",
						"Sets key PG, GR, and GD windows as percentages of the standard values.",
						sidebarLabeledInputs(
								new String[] { "PG", "GR", "GD" },
								sidebarSpinner(njudgepg), sidebarSpinner(njudgegr), sidebarSpinner(njudgegd))),
				sidebarSettingRow("スクラッチの判定幅", "Scratch judgment windows",
						"スクラッチのPG・GR・GD判定幅を標準値に対する百分率で設定します。",
						"Sets scratch PG, GR, and GD windows as percentages of the standard values.",
						sidebarLabeledInputs(
								new String[] { "PG", "GR", "GD" },
								sidebarSpinner(sjudgepg), sidebarSpinner(sjudgegr), sidebarSpinner(sjudgegd))),
				sidebarSettingRow("BPMガイド", "BPM guide",
						"プレイ中にBPM変化の目安を表示します。",
						"Shows guidance for BPM changes during play.",
						sidebarToggle(bpmguide)),
				sidebarSettingRow("判定エリア表示", "Show judgment area",
						"判定が有効になる範囲をレーン上に表示します。",
						"Shows the active judgment area on the lane.",
						sidebarToggle(judgeregion)),
				sidebarSettingRow("不可視ノーツ表示", "Show hidden notes",
						"通常は見えないノーツを補助表示します。",
						"Displays notes that are normally invisible.",
						sidebarToggle(showhiddennote)),
				sidebarSettingRow("処理済ノーツ別表示", "Mark processed notes",
						"判定済みのノーツを区別して表示します。",
						"Visually distinguishes notes that have already been judged.",
						sidebarToggle(markprocessednote)),
				sidebarSettingRow("GUIDE SE", "GUIDE SE",
						"プレイを補助するガイド音を有効にします。",
						"Enables guide sound effects during play.",
						sidebarToggle(guidese)),
				sidebarSettingRow("WINDOW HOLD", "WINDOW HOLD",
						"ウィンドウのフォーカス状態を保持する補助機能です。",
						"Keeps the game window focus behavior active as an assist.",
						sidebarToggle(windowhold)),
				sidebarSettingRow("過去のメモを表示", "Show past notes",
						"判定位置を通過したノーツを残して表示します。",
						"Keeps notes visible after they pass the judgment line.",
						sidebarToggle(showpastnote)),
				sidebarSettingRow("LR2 EXTRA MODE", "LR2 EXTRA MODE",
						"LR2互換の追加ノーツ生成レベルを選びます。0は無効です。",
						"Selects the LR2-compatible extra-note generation level. 0 disables it.",
						sidebarSpinner(extranotedepth)),
				sidebarSettingRow("Mine Modify Mode", "Mine Modify Mode",
						"地雷ノーツを削除・追加する変換方法を選びます。",
						"Choose how mine notes are removed or generated.",
						sidebarCombo(minemode)),
				sidebarSettingRow("Scroll Modify Mode", "Scroll Modify Mode",
						"スクロール速度変化を削除・追加する変換方法を選びます。",
						"Choose how scroll-speed changes are removed or generated.",
						sidebarCombo(scrollmode)),
				sidebarSettingRow("Long Note Modify Mode", "Long Note Modify Mode",
						"通常ノーツとロングノートを変換する方法を選びます。",
						"Choose how normal notes and long notes are converted.",
						sidebarCombo(longnotemode)),
				sidebarSettingRow("Long Note Modify Rate", "Long Note Modify Rate",
						"ロングノート変換を適用する割合です。",
						"Sets the proportion of notes affected by long-note conversion.",
						sidebarSlider(longnoterate))
		);

		VBox result = sidebarSettingCard(
				sidebarSettingRow("ターゲットスコア", "Target score",
						"プレイ中の比較対象として表示するスコアを選びます。",
						"Choose the score used as the in-play comparison target.",
						sidebarCombo(target)),
				sidebarSettingRow("判定アルゴリズム", "Judgment algorithm",
						"同時入力付近で、どのノーツを優先して判定するかを選びます。",
						"Choose which notes receive priority around simultaneous inputs.",
						sidebarCombo(judgealgorithm)),
				sidebarSettingRow("Gauge Auto Shift", "Gauge Auto Shift",
						"ゲージ失敗時に別のゲージへ移行する方法を選びます。",
						"Choose how the gauge changes after a gauge failure.",
						sidebarCombo(gaugeautoshift)),
				sidebarSettingRow("ゲージ遷移の下限", "Bottom shiftable gauge",
						"Gauge Auto Shiftで移行できる最も低いゲージを選びます。",
						"Choose the lowest gauge that Gauge Auto Shift may reach.",
						sidebarCombo(bottomshiftablegauge)),
				sidebarSettingRow("リプレイ自動保存 1", "Auto-save Replay 1",
						"リプレイスロット1を自動保存する条件を選びます。",
						"Choose when Replay slot 1 is saved automatically.",
						sidebarCombo(autosavereplay1)),
				sidebarSettingRow("リプレイ自動保存 2", "Auto-save Replay 2",
						"リプレイスロット2を自動保存する条件を選びます。",
						"Choose when Replay slot 2 is saved automatically.",
						sidebarCombo(autosavereplay2)),
				sidebarSettingRow("リプレイ自動保存 3", "Auto-save Replay 3",
						"リプレイスロット3を自動保存する条件を選びます。",
						"Choose when Replay slot 3 is saved automatically.",
						sidebarCombo(autosavereplay3)),
				sidebarSettingRow("リプレイ自動保存 4", "Auto-save Replay 4",
						"リプレイスロット4を自動保存する条件を選びます。",
						"Choose when Replay slot 4 is saved automatically.",
						sidebarCombo(autosavereplay4)),
				sidebarSettingRow("7 to 9", "7 to 9",
						"7鍵譜面を9鍵へ割り当てるパターンを選びます。",
						"Choose how a 7-key chart is mapped to 9 keys.",
						sidebarCombo(seventoninepattern)),
				sidebarSettingRow("SCタイプ", "SC type",
						"7鍵から9鍵へ変換したときのスクラッチ配置方法を選びます。",
						"Choose the scratch placement used for 7-to-9 conversion.",
						sidebarCombo(seventoninetype)),
				sidebarSettingRow("START+SELECT終了まで時間", "START+SELECT exit delay",
						"STARTとSELECTを押してからプレイを終了するまでの時間です。単位は ms です。",
						"Time START and SELECT must be held before exiting play, in ms.",
						sidebarSpinner(exitpressduration)),
				sidebarSettingRow("チャートプレビュー", "Chart Preview",
						"楽曲ロード中にSTARTまたはSELECTを押している間、プレイ画面で譜面の流れを先行表示します。選曲時の試聴ではありません。",
						"While loading, hold START or SELECT to preview chart movement on the play field. This is not Music Select audio preview.",
						sidebarToggle(chartpreview))
		);

		sidebarPlayOptionGroups.getChildren().setAll(visibility, chart, assist, result);
		sidebarPlayOptionsInitialized = true;
	}

	private VBox sidebarSettingCard(VBox... rows) {
		VBox card = new VBox();
		card.getStyleClass().add("sidebar-settings-card");
		card.getChildren().setAll(rows);
		return card;
	}

	private VBox sidebarSettingRow(String jaTitle, String enTitle,
			String jaDescription, String enDescription, Node editorNode) {
		String title = uiText(jaTitle, enTitle);
		String description = uiText(jaDescription, enDescription);
		Label titleLabel = new Label(title);
		titleLabel.setWrapText(true);
		titleLabel.getStyleClass().add("sidebar-setting-title");
		HBox.setHgrow(titleLabel, Priority.ALWAYS);

		HBox editor = new HBox(editorNode);
		editor.setAlignment(Pos.CENTER_LEFT);
		editor.getStyleClass().add("sidebar-setting-editor");
		if (editorNode instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(region, Priority.ALWAYS);
		}

		HBox main = new HBox(18, titleLabel, editor);
		main.setAlignment(Pos.CENTER_LEFT);
		main.getStyleClass().add("sidebar-setting-main");

		Label descriptionLabel = new Label(description);
		descriptionLabel.setWrapText(true);
		descriptionLabel.getStyleClass().add("sidebar-setting-row-description");

		VBox row = new VBox(8, main, descriptionLabel);
		row.getStyleClass().add("sidebar-setting-row");
		row.setAccessibleText(title + ". " + description);
		Control firstControl = firstControl(editorNode);
		if (firstControl != null) {
			titleLabel.setLabelFor(firstControl);
			firstControl.setAccessibleText(title + ". " + description);
		}
		installSidebarRowHelp(row, new ContextHelp(title, description, HelpGraphic.PLAY));
		return row;
	}

	private String uiText(String japanese, String english) {
		return englishUi ? english : japanese;
	}

	private <T> ComboBox<T> sidebarCombo(ComboBox<T> source) {
		ComboBox<T> mirror = new ComboBox<>();
		mirror.setItems(source.getItems());
		mirror.setConverter(source.getConverter());
		Callback<ListView<T>, ListCell<T>> cellFactory = source.getCellFactory();
		if (cellFactory != null) {
			mirror.setCellFactory(cellFactory);
			mirror.setButtonCell(cellFactory.call(new ListView<>()));
		}
		mirror.setValue(source.getValue());
		mirror.valueProperty().bindBidirectional(source.valueProperty());
		mirror.disableProperty().bind(source.disableProperty());
		mirror.getStyleClass().add("sidebar-setting-control");
		return mirror;
	}

	private <T> Spinner<T> sidebarSpinner(Spinner<T> source) {
		NumericSpinner<T> mirror = new NumericSpinner<>();
		mirror.setEditable(source.isEditable());
		SpinnerValueFactory<T> valueFactory = copySpinnerValueFactory(source.getValueFactory());
		mirror.setValueFactory(valueFactory);
		valueFactory.valueProperty().bindBidirectional(source.getValueFactory().valueProperty());
		mirror.disableProperty().bind(source.disableProperty());
		mirror.getStyleClass().add("sidebar-setting-control");
		return mirror;
	}

	@SuppressWarnings("unchecked")
	private static <T> SpinnerValueFactory<T> copySpinnerValueFactory(SpinnerValueFactory<T> original) {
		if (original instanceof SpinnerValueFactory.IntegerSpinnerValueFactory integerFactory) {
			return (SpinnerValueFactory<T>) new SpinnerValueFactory.IntegerSpinnerValueFactory(
					integerFactory.getMin(), integerFactory.getMax(), integerFactory.getValue(),
					integerFactory.getAmountToStepBy()
			);
		}
		if (original instanceof SpinnerValueFactory.DoubleSpinnerValueFactory doubleFactory) {
			return (SpinnerValueFactory<T>) new SpinnerValueFactory.DoubleSpinnerValueFactory(
					doubleFactory.getMin(), doubleFactory.getMax(), doubleFactory.getValue(),
					doubleFactory.getAmountToStepBy()
			);
		}
		throw new IllegalArgumentException("Unsupported spinner value factory: " + original.getClass().getName());
	}

	private HBox sidebarToggle(CheckBox source) {
		ToggleButton toggle = new ToggleButton();
		toggle.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		toggle.setGraphic(new Circle(8, Color.WHITE));
		toggle.getStyleClass().add("sidebar-switch");
		toggle.selectedProperty().bindBidirectional(source.selectedProperty());
		toggle.disableProperty().bind(source.disableProperty());
		Label state = new Label();
		state.getStyleClass().add("sidebar-switch-state");
		state.textProperty().bind(Bindings.when(toggle.selectedProperty()).then("ON").otherwise("OFF"));
		HBox result = new HBox(8, toggle, state);
		result.setAlignment(Pos.CENTER_LEFT);
		result.getStyleClass().add("sidebar-toggle-control");
		return result;
	}

	private HBox sidebarToggleWithValue(CheckBox source, Node valueControl) {
		HBox result = new HBox(12, sidebarToggle(source), valueControl);
		result.setAlignment(Pos.CENTER_LEFT);
		result.getStyleClass().add("sidebar-compound-control");
		if (valueControl instanceof Control control) {
			control.disableProperty().unbind();
			control.disableProperty().bind(source.disableProperty().or(source.selectedProperty().not()));
		}
		if (valueControl instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(region, Priority.ALWAYS);
		}
		return result;
	}

	private Slider sidebarSlider(Slider source) {
		Slider mirror = new Slider(source.getMin(), source.getMax(), source.getValue());
		mirror.setBlockIncrement(source.getBlockIncrement());
		mirror.setMajorTickUnit(source.getMajorTickUnit());
		mirror.setMinorTickCount(source.getMinorTickCount());
		mirror.valueProperty().bindBidirectional(source.valueProperty());
		mirror.disableProperty().bind(source.disableProperty());
		mirror.getStyleClass().add("sidebar-setting-control");
		return mirror;
	}

	private HBox sidebarLabeledInputs(String[] labels, Node... controls) {
		HBox result = new HBox(8);
		result.setAlignment(Pos.CENTER_LEFT);
		result.getStyleClass().add("sidebar-compact-inputs");
		for (int index = 0; index < controls.length; index++) {
			Label label = new Label(labels[index]);
			label.getStyleClass().add("sidebar-compact-label");
			VBox item = new VBox(3, label, controls[index]);
			item.getStyleClass().add("sidebar-compact-input");
			if (controls[index] instanceof Region region) {
				region.setMaxWidth(Double.MAX_VALUE);
				VBox.setVgrow(region, Priority.ALWAYS);
			}
			HBox.setHgrow(item, Priority.ALWAYS);
			result.getChildren().add(item);
		}
		return result;
	}

	private void installSidebarRowHelp(VBox row, ContextHelp help) {
		row.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> showContextHelp(help));
		row.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
			if (!containsFocusedControl(row)) {
				showTabContextHelp(optionTab);
			}
		});
		forEachControl(row, control -> control.focusedProperty().addListener(
				(observable, oldValue, focused) -> {
					if (focused) {
						showContextHelp(help);
					} else {
						Platform.runLater(() -> {
							if (!row.isHover() && !containsFocusedControl(row)) {
								showTabContextHelp(optionTab);
							}
						});
					}
				}
		));
	}

	private static void forEachControl(Node node, java.util.function.Consumer<Control> consumer) {
		if (node instanceof Control control) {
			consumer.accept(control);
		}
		if (node instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				forEachControl(child, consumer);
			}
		}
	}

	private static boolean containsFocusedControl(Node node) {
		if (node instanceof Control control && control.isFocused()) {
			return true;
		}
		if (node instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				if (containsFocusedControl(child)) {
					return true;
				}
			}
		}
		return false;
	}

	private static Control firstControl(Node node) {
		if (node instanceof Control control) {
			return control;
		}
		if (node instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				Control control = firstControl(child);
				if (control != null) {
					return control;
				}
			}
		}
		return null;
	}

	private void registerTabHelp(Tab tab, String jaTitle, String jaDescription,
			String enTitle, String enDescription, HelpGraphic graphic) {
		tabContextHelp.put(tab, new ContextHelp(
				englishUi ? enTitle : jaTitle,
				englishUi ? enDescription : jaDescription,
				graphic
		));
	}

	private void registerControlHelp(String id, String jaTitle, String jaDescription,
			String enTitle, String enDescription, HelpGraphic graphic) {
		controlContextHelp.put(id, new ContextHelp(
				englishUi ? enTitle : jaTitle,
				englishUi ? enDescription : jaDescription,
				graphic
		));
	}

	private void installContextHelp(Tab tab, Node node) {
		if (isContextHelpControl(node)) {
			ContextHelp help = contextHelpFor(tab, (Control) node);
			node.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> showContextHelp(help));
			node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
				if (!node.isFocused()) {
					showTabContextHelp(tab);
				}
			});
			node.focusedProperty().addListener((observable, oldValue, focused) -> {
				if (focused) {
					showContextHelp(help);
				} else if (!node.isHover()) {
					showTabContextHelp(tab);
				}
			});
		}
		if (node instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				installContextHelp(tab, child);
			}
		}
	}

	private static boolean isContextHelpControl(Node node) {
		return node instanceof ButtonBase
				|| node instanceof ComboBoxBase<?>
				|| node instanceof Spinner<?>
				|| node instanceof Slider
				|| node instanceof TextInputControl
				|| node instanceof ListView<?>
				|| node instanceof TableView<?>;
	}

	private ContextHelp contextHelpFor(Tab tab, Control control) {
		if (control.getId() != null && controlContextHelp.containsKey(control.getId())) {
			return controlContextHelp.get(control.getId());
		}
		String title = controlTitle(control);
		String description;
		if (englishUi) {
			description = control instanceof ButtonBase
					? "Run “" + title + "”. The existing operation is shared by Classic and Sidebar layouts."
					: "Change “" + title + "”. The selected value is saved through the existing configuration path.";
		} else {
			description = control instanceof ButtonBase
					? "「" + title + "」を実行します。操作内容はクラシックとサイドバーで共通です。"
					: "「" + title + "」を変更します。選択した値は既存の設定保存経路へ保存されます。";
		}
		HelpGraphic graphic = Optional.ofNullable(tabContextHelp.get(tab))
				.map(ContextHelp::graphic)
				.orElse(HelpGraphic.OTHER);
		return new ContextHelp(title, description, graphic);
	}

	private String controlTitle(Control control) {
		if (control instanceof Labeled labeled
				&& labeled.getText() != null
				&& !labeled.getText().isBlank()) {
			return labeled.getText().trim();
		}
		String nearby = nearbyLabel(control);
		if (nearby != null) {
			return nearby;
		}
		String id = control.getId();
		if (id == null || id.isBlank()) {
			return englishUi ? "This setting" : "この設定";
		}
		return id.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
				.replace('_', ' ')
				.trim();
	}

	private String nearbyLabel(Node control) {
		Node anchor = control;
		Parent parent = control.getParent();
		for (int depth = 0; parent != null && depth < 3; depth++) {
			if (parent instanceof GridPane grid && grid.getChildren().contains(anchor)) {
				int row = Optional.ofNullable(GridPane.getRowIndex(anchor)).orElse(0);
				int column = Optional.ofNullable(GridPane.getColumnIndex(anchor)).orElse(0);
				Label nearest = null;
				int nearestColumn = Integer.MIN_VALUE;
				for (Node sibling : grid.getChildren()) {
					if (sibling instanceof Label label) {
						int labelRow = Optional.ofNullable(GridPane.getRowIndex(label)).orElse(0);
						int labelColumn = Optional.ofNullable(GridPane.getColumnIndex(label)).orElse(0);
						if (labelRow == row && labelColumn <= column && labelColumn > nearestColumn) {
							nearest = label;
							nearestColumn = labelColumn;
						}
					}
				}
				if (nearest != null && nearest.getText() != null && !nearest.getText().isBlank()) {
					return nearest.getText().trim();
				}
			}
			if (parent instanceof Pane pane && pane.getChildren().contains(anchor)) {
				int index = pane.getChildren().indexOf(anchor);
				for (int siblingIndex = index - 1; siblingIndex >= 0; siblingIndex--) {
					Node sibling = pane.getChildren().get(siblingIndex);
					if (sibling instanceof Label label
							&& label.getText() != null
							&& !label.getText().isBlank()) {
						return label.getText().trim();
					}
				}
			}
			anchor = parent;
			parent = parent.getParent();
		}
		return null;
	}

	private void showTabContextHelp(Tab tab) {
		ContextHelp help = tabContextHelp.get(tab);
		if (help != null) {
			showContextHelp(help);
		}
	}

	private void showContextHelp(ContextHelp help) {
		contextHelpTitle.setText(help.title());
		contextHelpDescription.setText(help.description());
		contextHelpGraphic.setAccessibleText(help.title() + ". " + help.description());
		drawContextHelpGraphic(help.graphic());
	}

	private void drawContextHelpGraphic(HelpGraphic graphic) {
		drawHelpGraphic(contextHelpGraphic, graphic, Color.web("#1976d2"), 2.2);
	}

	private void drawSidebarGraphic(Canvas canvas, HelpGraphic graphic, boolean selected) {
		drawHelpGraphic(canvas, graphic,
				selected ? Color.WHITE : Color.web("#617084"), 1.7);
	}

	private void drawHelpGraphic(Canvas canvas, HelpGraphic graphic, Color color, double strokeWidth) {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		double width = canvas.getWidth();
		double height = canvas.getHeight();
		gc.clearRect(0, 0, width, height);

		final double graphicX = 16;
		final double graphicY = 10;
		final double graphicWidth = 100;
		final double graphicHeight = 66;
		double scale = Math.min(width / graphicWidth, height / graphicHeight);
		double offsetX = (width - graphicWidth * scale) / 2 - graphicX * scale;
		double offsetY = (height - graphicHeight * scale) / 2 - graphicY * scale;

		gc.save();
		gc.translate(offsetX, offsetY);
		gc.scale(scale, scale);
		gc.setStroke(color);
		gc.setFill(color);
		gc.setLineWidth(strokeWidth / scale);
		switch (graphic) {
			case DISPLAY -> {
				gc.strokeRoundRect(25, 17, 82, 47, 6, 6);
				gc.strokeLine(54, 70, 78, 70);
				gc.strokeLine(66, 64, 66, 70);
				gc.strokeLine(36, 28, 96, 28);
			}
			case AUDIO -> {
				gc.strokeLine(30, 33, 42, 33);
				gc.strokeLine(42, 33, 58, 21);
				gc.strokeLine(58, 21, 58, 61);
				gc.strokeLine(58, 61, 42, 49);
				gc.strokeLine(42, 49, 30, 49);
				gc.strokeArc(62, 24, 31, 35, -55, 110, javafx.scene.shape.ArcType.OPEN);
				gc.strokeArc(62, 16, 48, 51, -48, 96, javafx.scene.shape.ArcType.OPEN);
			}
			case INPUT -> {
				gc.strokeRoundRect(20, 21, 92, 43, 6, 6);
				for (int column = 0; column < 6; column++) {
					gc.strokeRoundRect(27 + column * 13, 29, 9, 9, 2, 2);
					gc.strokeRoundRect(27 + column * 13, 43, 9, 9, 2, 2);
				}
				gc.strokeRoundRect(43, 56, 47, 4, 2, 2);
			}
			case RESOURCE -> {
				gc.strokeRoundRect(20, 27, 92, 42, 6, 6);
				gc.strokeLine(20, 34, 61, 34);
				gc.strokeLine(24, 24, 48, 24);
				gc.strokeLine(48, 24, 56, 29);
			}
			case MUSIC -> {
				gc.strokeLine(48, 21, 48, 57);
				gc.strokeLine(48, 21, 86, 15);
				gc.strokeLine(86, 15, 86, 50);
				gc.fillOval(34, 52, 16, 12);
				gc.fillOval(72, 45, 16, 12);
			}
			case PLAY -> {
				for (int row = 0; row < 3; row++) {
					double y = 25 + row * 18;
					gc.strokeLine(25, y, 107, y);
					gc.fillOval(43 + row * 18, y - 5, 10, 10);
				}
			}
			case SKIN -> {
				gc.strokeOval(31, 15, 69, 55);
				gc.fillOval(46, 27, 9, 9);
				gc.fillOval(65, 22, 9, 9);
				gc.fillOval(81, 35, 9, 9);
				gc.strokeOval(42, 48, 22, 14);
			}
			case OTHER -> {
				gc.strokeOval(45, 20, 42, 42);
				gc.strokeOval(58, 33, 16, 16);
				for (int angle = 0; angle < 8; angle++) {
					double radians = Math.toRadians(angle * 45);
					gc.strokeLine(66 + Math.cos(radians) * 24, 41 + Math.sin(radians) * 24,
							66 + Math.cos(radians) * 31, 41 + Math.sin(radians) * 31);
				}
			}
			case BMSIR, IR -> {
				gc.strokeOval(25, 25, 31, 31);
				gc.strokeOval(76, 25, 31, 31);
				gc.strokeLine(51, 31, 81, 50);
				gc.strokeLine(51, 50, 81, 31);
			}
			case TABLE -> {
				gc.strokeRoundRect(27, 16, 78, 54, 4, 4);
				gc.strokeLine(27, 34, 105, 34);
				gc.strokeLine(27, 52, 105, 52);
				gc.strokeLine(53, 16, 53, 70);
				gc.strokeLine(79, 16, 79, 70);
			}
			case STREAM -> {
				gc.fillOval(61, 37, 10, 10);
				gc.strokeArc(46, 25, 40, 34, -50, 100, javafx.scene.shape.ArcType.OPEN);
				gc.strokeArc(35, 15, 62, 54, -48, 96, javafx.scene.shape.ArcType.OPEN);
				gc.strokeLine(66, 47, 66, 68);
			}
			case CHAT -> {
				gc.strokeRoundRect(22, 17, 64, 38, 8, 8);
				gc.strokeLine(38, 55, 31, 66);
				gc.strokeRoundRect(54, 35, 57, 31, 7, 7);
				gc.strokeLine(97, 66, 104, 72);
			}
			case OBS -> {
				gc.strokeOval(42, 17, 48, 48);
				gc.strokeArc(48, 23, 32, 32, 15, 95, javafx.scene.shape.ArcType.OPEN);
				gc.strokeArc(48, 23, 32, 32, 135, 95, javafx.scene.shape.ArcType.OPEN);
				gc.strokeArc(48, 23, 32, 32, 255, 95, javafx.scene.shape.ArcType.OPEN);
			}
		}
		gc.restore();
	}

	private record ContextHelp(String title, String description, HelpGraphic graphic) {
	}

	private enum HelpGraphic {
		DISPLAY, AUDIO, INPUT, RESOURCE, MUSIC, PLAY, SKIN, OTHER,
		BMSIR, IR, TABLE, STREAM, CHAT, OBS
	}

    @FXML
    private void whatsNewPopup() {
        ResourceBundle bundle = ResourceBundle.getBundle("resources.UIResources");

        final Stage whatsNewStage = new Stage();
        Runnable whatsNewRunnable = () -> {
            // JavaFX UI code must be run inside a Platform run context
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    whatsNewStage.setResizable(true);
                    // This modality freezes the launcher/primary stage
                    whatsNewStage.initModality(Modality.APPLICATION_MODAL);
                    whatsNewStage.setTitle(Version.getArenaDisplayName() + " — What's New");
                    whatsNewStage.initStyle(StageStyle.DECORATED);

                    WebView webView = new WebView();
                    try {
                        String whatsNewHTMLLocation = getClass().getResource("/resources/whatsnew.html").toURI().toString();
                        webView.getEngine().load(whatsNewHTMLLocation);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }

                    Button gotItButton = new Button();
                    gotItButton.setText("Got it");
                    gotItButton.prefHeight(28.0);
                    gotItButton.minWidth(140.0);
                    gotItButton.setOnAction(new EventHandler<ActionEvent>() {
                        @Override public void handle(ActionEvent e) {
                            whatsNewStage.hide();
                        }
                    });

                    VBox root = new VBox(10);
                    root.setPrefWidth(800.0);
                    root.setPrefHeight(600.0);
                    root.setStyle("-fx-padding: 20; -fx-alignment: center;");
                    root.getChildren().addAll(webView, gotItButton);

                    Scene scene = new Scene(root);
                    whatsNewStage.setScene(scene);

                    whatsNewStage.show();
                }
            });
        };
        new Thread(whatsNewRunnable).start();
    }

	private void checkNewVersion() {
		Runnable newVersionCheckRunnable = () -> {
			final String message = MainLoader.getVersionChecker().getMessage();
			final String downloadURL = MainLoader.getVersionChecker().getDownloadURL();
			Platform.runLater(() -> {
				newversion.setText(message);
				if(downloadURL != null) {
					newversion.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent event) {
							Desktop desktop = Desktop.getDesktop();
                            java.awt.EventQueue.invokeLater(() -> {
                                try {
                                    URI uri;
                                    uri = new URI(downloadURL);
                                    desktop.browse(uri);
                                }
                                catch (Exception e) {
                                    logger.warn("最新版URLアクセス時例外:" + e.getMessage());
                                }
                            });
						}
					});
				}
			});
		};

		new Thread(newVersionCheckRunnable).start();
	}

	public void setBMSInformationLoader(MainLoader loader) {
		this.loader = loader;
	}

	/**
	 * ダイアログの項目を更新する
	 */
	public void update(Config config) {
		this.config = config;
		configurationLayout.setValue(config.getConfigurationLayout());

        // Show the What's New popup upon version change
        String currentVersion = Version.getVersion();
        String lastVersion = config.getLastBootedVersion();
        // If current version is greater than last version
        if (Version.compareToString(lastVersion) > 0) {
            whatsNewPopup();
            config.setLastBootedVersion(currentVersion);
        }

		players.getItems().setAll(PlayerConfig.readAllPlayerID(config.getPlayerpath()));
		videoController.update(config);
		audioController.update(config.getAudioConfig());
		musicselectController.update(config);

		bgmpath.setText(config.getBgmpath());
		soundpath.setText(config.getSoundpath());

		resourceController.update(config);
		discordController.update(config);
		obsController.update(config);

		skinController.update(config);
        // int b = Boolean.valueOf(config.getJKOC()).compareTo(false);

        usecim.setSelected(config.isCacheSkinImage());
        clipboardScreenshot.setSelected(config.isSetClipboardWhenScreenshot());

		enableIpfs.setSelected(config.isEnableIpfs());
		ipfsurl.setText(config.getIpfsUrl());

		enableHttp.setSelected(config.isEnableHttp());
		httpDownloadSource.setValue(config.getDownloadSource());
		defaultDownloadURL.setText(config.getDefaultDownloadURL());
		overrideDownloadURL.setText(config.getOverrideDownloadURL());

		if(players.getItems().contains(config.getPlayername())) {
			players.setValue(config.getPlayername());
		} else {
			players.getSelectionModel().select(0);
		}
		updatePlayer();
		updateSidebarPlayerSummary();

		try {
			Class.forName("org.sqlite.JDBC");
			tableController.init(MainLoader.getScoreDatabaseAccessor());
			tableController.update(Paths.get(config.getTablepath() + "/" + "default.json"));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void changePlayer() {
		commitPlayer();
		updatePlayer();
	}

	public void addPlayer() {
		String[] ids = PlayerConfig.readAllPlayerID(config.getPlayerpath());
		for(int i = 1;i < 1000;i++) {
			String playerid = "player" + i;
			boolean b = true;
			for(String id : ids) {
				if(playerid.equals(id)) {
					b =false;
					break;
				}
			}
			if(b) {
				PlayerConfig.create(config.getPlayerpath(), playerid);
				players.getItems().add(playerid);
				break;
			}
		}
	}

	public void updatePlayer() {
        try {
            player = PlayerConfig.readPlayerConfig(config.getPlayerpath(), players.getValue());
        } catch (PlayerConfigException e) {
            logger.warn("Player config failed to load: " + e.getLocalizedMessage());
			player = PlayerConfig.validatePlayerConfig("player1", new PlayerConfig());
        }
        playername.setText(player.getName());
		bmsirOneBassEnabled.setSelected(player.isBmsirOneBassEnabled());
		bmsirStartHerePreviewEnabled.setSelected(
				player.isBmsirStartHerePreviewEnabled()
		);
		bmsirDanLocalSyncEnabled.setSelected(
				player.isBmsirDanLocalSyncEnabled()
		);
		bmsirStartButtonAction.getSelectionModel().select(
				bmsirSelectButtonActionIndex(player.getBmsirStartButtonAction())
		);
		bmsirSelectButtonAction.getSelectionModel().select(
				bmsirSelectButtonActionIndex(player.getBmsirSelectButtonAction())
		);
		bmsirSelectDifficultyDisplay.getSelectionModel().select(
				bmsirSelectDifficultyDisplayIndex(
						player.getBmsirSelectDifficultyDisplay()
				)
		);
		Set<String> selectModes = new HashSet<>(
				Arrays.asList(player.getBmsirSelectKeyModes())
		);
		BMSIRSelectKeyMode[] selectableModes = BMSIRSelectKeyMode.values();
		for (int index = 0; index < selectableModes.length; index++) {
			bmsirSelectModeChecks.get(index).setSelected(
					selectModes.contains(selectableModes[index].id())
			);
		}
		bmsirHideMissingTableSongs.setSelected(
				player.isBmsirHideMissingTableSongs()
		);
		bmsirLongNoteFixed.setSelected(true);
		bmsirLongNoteFixed.setDisable(true);
		bmsirArenaLanguage.getSelectionModel().select(
				"en".equals(player.getBmsirArenaLanguage()) ? 1 : 0
		);
		bmsirRulesetProfile.getSelectionModel().select(
				"oraja".equals(player.getBmsirRulesetProfile()) ? 1 : 0
		);
		bmsirArenaTargetMode.getSelectionModel().select(
				bmsirArenaTargetModeIndex(player.getBmsirArenaTargetMode())
		);
		bmsirArenaGraphOrder.getSelectionModel().select(
				bmsirArenaGraphOrderIndex(player.getBmsirArenaGraphOrder())
		);
		bmsirCoverControlMode.getSelectionModel().select(
				bmsirCoverControlModeIndex(player.getBmsirCoverControlMode())
		);
		bmsirCoverChangeStep.getValueFactory().setValue(
				player.getBmsirCoverChangeStep()
		);
		bmsirCoverHispeedAutoAdjustEnabled.setSelected(
				player.isBmsirCoverHispeedAutoAdjustEnabled()
		);
		bmsirJudgeRankSortEnabled.setSelected(
				player.isBmsirJudgeRankSortEnabled()
		);
		bmsirJudgeRankSortSkinNoticeEnabled.setSelected(
				player.isBmsirJudgeRankSortSkinNoticeEnabled()
		);
		String[] numpadActions = player.getBmsirNumpadActions();
		for (int index = 0; index < bmsirNumpadCombos.size(); index++) {
			bmsirNumpadCombos.get(index).getSelectionModel().select(
					BMSIRNumpadAction.fromId(numpadActions[index]).ordinal()
			);
		}
		bmsirNumpadJudgeTimingStep.getValueFactory().setValue(
				player.getBmsirNumpadJudgeTimingStep()
		);
		bmsirJudgeTimingRestoreEnabled.setSelected(
				player.isBmsirJudgeTimingRestoreEnabled()
		);
		bmsirInfoNotificationsEnabled.setSelected(
				player.isBmsirInfoNotificationsEnabled()
		);

		videoController.updatePlayer(player);
		musicselectController.updatePlayer(player);

		scoreop.getSelectionModel().select(player.getRandom());
		scoreop2.getSelectionModel().select(player.getRandom2());
		doubleop.getSelectionModel().select(player.getDoubleoption());
		seventoninepattern.getSelectionModel().select(player.getSevenToNinePattern());
		seventoninetype.getSelectionModel().select(player.getSevenToNineType());
		exitpressduration.getValueFactory().setValue(player.getExitPressDuration());
		chartpreview.setSelected(player.isChartPreview());
		guidese.setSelected(player.isGuideSE());
		windowhold.setSelected(player.isWindowHold());
		gaugeop.getSelectionModel().select(player.getGauge());
		lntype.getSelectionModel().select(0);

		notesdisplaytiming.getValueFactory().setValue(player.getJudgetiming());
		notesdisplaytimingautoadjust.setSelected(player.isNotesDisplayTimingAutoAdjust());

		bpmguide.setSelected(player.isBpmguide());
		gaugeautoshift.setValue(player.getGaugeAutoShift());
		bottomshiftablegauge.setValue(player.getBottomShiftableGauge());

		customjudge.setSelected(player.isCustomJudge());
		njudgepg.getValueFactory().setValue(player.getKeyJudgeWindowRatePerfectGreat());
		njudgegr.getValueFactory().setValue(player.getKeyJudgeWindowRateGreat());
		njudgegd.getValueFactory().setValue(player.getKeyJudgeWindowRateGood());
		sjudgepg.getValueFactory().setValue(player.getScratchJudgeWindowRatePerfectGreat());
		sjudgegr.getValueFactory().setValue(player.getScratchJudgeWindowRateGreat());
		sjudgegd.getValueFactory().setValue(player.getScratchJudgeWindowRateGood());
		minemode.getSelectionModel().select(player.getMineMode());
		scrollmode.getSelectionModel().select(player.getScrollMode());
		longnotemode.getSelectionModel().select(player.getLongnoteMode());
		forcedcnendings.setSelected(player.isForcedCNEndings());
		longnoterate.setValue(player.getLongnoteRate());
		hranthresholdbpm.getValueFactory().setValue(player.getHranThresholdBPM());
		judgeregion.setSelected(player.isShowjudgearea());
		markprocessednote.setSelected(player.isMarkprocessednote());
		extranotedepth.getValueFactory().setValue(player.getBmsirExtraMode());

		autosavereplay1.getSelectionModel().select(player.getAutoSaveReplay()[0]);
		autosavereplay2.getSelectionModel().select(player.getAutoSaveReplay()[1]);
		autosavereplay3.getSelectionModel().select(player.getAutoSaveReplay()[2]);
		autosavereplay4.getSelectionModel().select(player.getAutoSaveReplay()[3]);

		String[] targets = player.getTargetlist();
		target.getItems().setAll(targets);
		target.setValue(player.getTargetid());
		showhiddennote.setSelected(player.isShowhiddennote());
		showpastnote.setSelected(player.isShowpastnote());

		irController.update(player);
		streamController.update(player);
		//trainerController.update(player);

		txtTwitterPIN.setDisable(true);
		twitterPINButton.setDisable(true);
		if(player.getTwitterAccessToken() != null && !player.getTwitterAccessToken().isEmpty()) {
			txtTwitterAuthenticated.setVisible(true);
		} else {
			txtTwitterAuthenticated.setVisible(false);
		}

		pc = null;
		playconfig.setValue(PlayMode.BEAT_7K);
		updatePlayConfig();

		inputController.update(config, player);
		skinController.update(player);
	}

	/**
	 * ダイアログの項目をconfig.xmlに反映する
	 */
	public void commit() {
	    videoController.commit(config);
		audioController.commit();
		musicselectController.commit();

		config.setPlayername(players.getValue());
		config.setConfigurationLayout(configurationLayout.getValue());

		config.setBgmpath(bgmpath.getText());
		config.setSoundpath(soundpath.getText());

		resourceController.commit();
		discordController.commit();
		obsController.commit();

        // jkoc_hack is integer but *.setJKOC needs boolean type

        config.setCacheSkinImage(usecim.isSelected());

		config.setEnableIpfs(enableIpfs.isSelected());
		config.setIpfsUrl(ipfsurl.getText());

		config.setEnableHttp(enableHttp.isSelected());
		config.setDownloadSource(httpDownloadSource.getValue());
		config.setOverrideDownloadURL(overrideDownloadURL.getText());

		config.setClipboardWhenScreenshot(clipboardScreenshot.isSelected());

		commitPlayer();

		Config.write(config);

		tableController.commit();
	}

	public void commitPlayer() {
		if(player == null) {
			return;
		}
		if(playername.getText().length() > 0) {
			player.setName(playername.getText());
		}
		player.setBmsirRulesetProfile(
				bmsirRulesetProfile.getSelectionModel().getSelectedIndex() == 1
						? "oraja"
						: "lr2"
		);
		player.setBmsirOneBassEnabled(bmsirOneBassEnabled.isSelected());
		player.setBmsirStartHerePreviewEnabled(
				bmsirStartHerePreviewEnabled.isSelected()
		);
		player.setBmsirDanLocalSyncEnabled(
				bmsirDanLocalSyncEnabled.isSelected()
		);
		player.setBmsirStartButtonAction(
				bmsirSelectButtonActionValue(
						bmsirStartButtonAction.getSelectionModel().getSelectedIndex()
				)
		);
		player.setBmsirSelectButtonAction(
				bmsirSelectButtonActionValue(
						bmsirSelectButtonAction.getSelectionModel().getSelectedIndex()
				)
		);
		player.setBmsirSelectDifficultyDisplay(
				bmsirSelectDifficultyDisplayValue(
						bmsirSelectDifficultyDisplay.getSelectionModel()
								.getSelectedIndex()
				)
		);
		List<String> selectModes = new ArrayList<>();
		BMSIRSelectKeyMode[] selectableModes = BMSIRSelectKeyMode.values();
		for (int index = 0; index < selectableModes.length; index++) {
			if (bmsirSelectModeChecks.get(index).isSelected()) {
				selectModes.add(selectableModes[index].id());
			}
		}
		player.setBmsirSelectKeyModes(selectModes.toArray(String[]::new));
		player.setBmsirHideMissingTableSongs(
				bmsirHideMissingTableSongs.isSelected()
		);
		player.setBmsirArenaLanguage(
				bmsirArenaLanguage.getSelectionModel().getSelectedIndex() == 1
						? "en"
						: "ja"
		);
		player.setBmsirArenaTargetMode(
				bmsirArenaTargetModeValue(
						bmsirArenaTargetMode.getSelectionModel()
								.getSelectedIndex()
				)
		);
		player.setBmsirArenaGraphOrder(
				bmsirArenaGraphOrderValue(
						bmsirArenaGraphOrder.getSelectionModel()
								.getSelectedIndex()
				)
		);
		player.setBmsirCoverControlMode(
				bmsirCoverControlModeValue(
						bmsirCoverControlMode.getSelectionModel().getSelectedIndex()
				)
		);
		player.setBmsirCoverChangeStep(getValue(bmsirCoverChangeStep));
		player.setBmsirCoverHispeedAutoAdjustEnabled(
				bmsirCoverHispeedAutoAdjustEnabled.isSelected()
		);
		player.setBmsirJudgeRankSortEnabled(
				bmsirJudgeRankSortEnabled.isSelected()
		);
		player.setBmsirJudgeRankSortSkinNoticeEnabled(
				bmsirJudgeRankSortSkinNoticeEnabled.isSelected()
		);
		String[] numpadActions = new String[BMSIRNumpadAction.KEY_COUNT];
		for (int index = 0; index < bmsirNumpadCombos.size(); index++) {
			int selected = bmsirNumpadCombos.get(index)
					.getSelectionModel()
					.getSelectedIndex();
			numpadActions[index] = selected >= 0
					? BMSIRNumpadAction.values()[selected].id()
					: BMSIRNumpadAction.NONE.id();
		}
		player.setBmsirNumpadActions(numpadActions);
		player.setBmsirNumpadJudgeTimingStep(
				getValue(bmsirNumpadJudgeTimingStep)
		);
		player.setBmsirJudgeTimingRestoreEnabled(
				bmsirJudgeTimingRestoreEnabled.isSelected()
		);
		player.setBmsirInfoNotificationsEnabled(
				bmsirInfoNotificationsEnabled.isSelected()
		);

		videoController.commitPlayer(player);
		musicselectController.commitPlayer();

		player.setRandom(scoreop.getValue());
		player.setRandom2(scoreop2.getValue());
		player.setDoubleoption(doubleop.getValue());
		player.setSevenToNinePattern(seventoninepattern.getValue());
		player.setSevenToNineType(seventoninetype.getValue());
		player.setExitPressDuration(getValue(exitpressduration));
		player.setChartPreview(chartpreview.isSelected());
		player.setGuideSE(guidese.isSelected());
		player.setWindowHold(windowhold.isSelected());
		player.setGauge(gaugeop.getValue());
		player.setLnmode(0);
		player.setJudgetiming(getValue(notesdisplaytiming));
		player.setNotesDisplayTimingAutoAdjust(notesdisplaytimingautoadjust.isSelected());

		player.setBpmguide(bpmguide.isSelected());
		player.setGaugeAutoShift(gaugeautoshift.getValue());
		player.setBottomShiftableGauge(bottomshiftablegauge.getValue());
		player.setCustomJudge(customjudge.isSelected());
		player.setKeyJudgeWindowRatePerfectGreat(getValue(njudgepg));
		player.setKeyJudgeWindowRateGreat(getValue(njudgegr));
		player.setKeyJudgeWindowRateGood(getValue(njudgegd));
		player.setScratchJudgeWindowRatePerfectGreat(getValue(sjudgepg));
		player.setScratchJudgeWindowRateGreat(getValue(sjudgegr));
		player.setScratchJudgeWindowRateGood(getValue(sjudgegd));
		player.setMineMode(minemode.getValue());
		player.setScrollMode(scrollmode.getValue());
		player.setLongnoteMode(longnotemode.getValue());
		player.setForcedCNEndings(forcedcnendings.isSelected());
		player.setLongnoteRate(longnoterate.getValue());
		player.setHranThresholdBPM(getValue(hranthresholdbpm));
		player.setMarkprocessednote(markprocessednote.isSelected());
		player.setBmsirExtraMode(extranotedepth.getValue());

		player.setAutoSaveReplay( new int[]{autosavereplay1.getValue(),autosavereplay2.getValue(),
				autosavereplay3.getValue(),autosavereplay4.getValue()});

		player.setShowjudgearea(judgeregion.isSelected());
		player.setTargetid(target.getValue());

		player.setShowhiddennote(showhiddennote.isSelected());
		player.setShowpastnote(showpastnote.isSelected());

		inputController.commit();
		irController.commit();
		streamController.commit();

		updatePlayConfig();
		skinController.commit();

		PlayerConfig.write(config.getPlayerpath(), player);
	}

    @FXML
	public void addBGMPath() {
    	String s = showDirectoryChooser("BGMのルートフォルダを選択してください");
    	if(s != null) {
        	bgmpath.setText(s);
    	}
	}

    @FXML
	public void addSoundPath() {
    	String s = showDirectoryChooser("効果音のルートフォルダを選択してください");
    	if(s != null) {
    		soundpath.setText(s);
    	}
	}

    private String showFileChooser(String title) {
    	FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		File f = chooser.showOpenDialog(null);
		return f != null ? f.getPath() : null;
    }

    private String showDirectoryChooser(String title) {
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle(title);
		File f = chooser.showDialog(null);
		return f != null ? f.getPath() : null;
    }

	private PlayMode pc = null;

    @FXML
	public void updatePlayConfig() {
		if (pc != null) {
			PlayConfig conf = player.getPlayConfig(Mode.valueOf(pc.name())).getPlayconfig();
			conf.setHispeed(getValue(hispeed).floatValue());
			conf.setDuration(getValue(gvalue));
			conf.setEnableConstant(enableConstant.isSelected());
			conf.setConstantFadeinTime(getValue(constFadeinTime));
			conf.setHispeedMargin(getValue(hispeedmargin).floatValue());
			Integer selectedFixHispeed = fixhispeed.getValue();
			if (selectedFixHispeed != null) {
				conf.setFixhispeed(selectedFixHispeed);
			}
			conf.setEnablelanecover(enableLanecover.isSelected());
			conf.setLanecover(getValue(lanecover) / 1000f);
			conf.setLanecovermarginlow(getValue(lanecovermarginlow) / 1000f);
			conf.setLanecovermarginhigh(getValue(lanecovermarginhigh) / 1000f);
			conf.setLanecoverswitchduration(getValue(lanecoverswitchduration));
			conf.setEnablelift(enableLift.isSelected());
			conf.setEnablehidden(enableHidden.isSelected());
			conf.setLift(getValue(lift) / 1000f);
			conf.setHidden(getValue(hidden) / 1000f);
			conf.setJudgetype(JudgeAlgorithm.values()[judgealgorithm.getValue()].name());
			conf.setHispeedAutoAdjust(hispeedautoadjust.isSelected());
		}
		pc = playconfig.getValue();
		PlayConfig conf = player.getPlayConfig(Mode.valueOf(pc.name())).getPlayconfig();
		hispeed.getValueFactory().setValue((double) conf.getHispeed());
		gvalue.getValueFactory().setValue(conf.getDuration());
		enableConstant.setSelected(conf.isEnableConstant());
		constFadeinTime.getValueFactory().setValue(conf.getConstantFadeinTime());
		hispeedmargin.getValueFactory().setValue((double) conf.getHispeedMargin());
		fixhispeed.setValue(conf.getFixhispeed());
		enableLanecover.setSelected(conf.isEnablelanecover());
		lanecover.getValueFactory().setValue((int) (conf.getLanecover() * 1000));
		lanecovermarginlow.getValueFactory().setValue((int) (conf.getLanecovermarginlow() * 1000));
		lanecovermarginhigh.getValueFactory().setValue((int) (conf.getLanecovermarginhigh() * 1000));
		lanecoverswitchduration.getValueFactory().setValue(conf.getLanecoverswitchduration());
		enableLift.setSelected(conf.isEnablelift());
		enableHidden.setSelected(conf.isEnablehidden());
		lift.getValueFactory().setValue((int) (conf.getLift() * 1000));
		hidden.getValueFactory().setValue((int) (conf.getHidden() * 1000));
		judgealgorithm.setValue(JudgeAlgorithm.getIndex(conf.getJudgetype()));
		hispeedautoadjust.setSelected(conf.isEnableHispeedAutoAdjust());
	}

	private <T> T getValue(Spinner<T> spinner) {
		spinner.getValueFactory()
				.setValue(spinner.getValueFactory().getConverter().fromString(spinner.getEditor().getText()));
		return spinner.getValue();
	}

    @FXML
	public void start() {
		commit();
		playerPanelHost.setDisable(true);
		videoTab.setDisable(true);
		audioTab.setDisable(true);
		inputTab.setDisable(true);
		resourceTab.setDisable(true);
		optionTab.setDisable(true);
		otherTab.setDisable(true);
		bmsirSpecificTab.setDisable(true);
		irTab.setDisable(true);
		streamTab.setDisable(true);
		discordTab.setDisable(true);
		obsTab.setDisable(true);
		sidebarNavigation.setDisable(true);
		sidebarPlayerSummary.setDisable(true);
		controlPanel.setDisable(true);

		// Minimise the stage after start
		Stage stage = (Stage) root.getScene().getWindow();
		stage.setIconified(true);

        // On linux the main play/GL loop needs to run on a thread separate
        // from the JavaFX thread; otherwise the launcher becomes uninteractable
        // and certain calls such as opening the file manager, or the browser.
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            Runnable play =
                () -> MainLoader.play(null,
                                      bms.player.beatoraja.BMSPlayerMode.PLAY,
                                      true,
                                      config,
                                      player,
                                      songUpdated);
            new Thread(play, "Play Thread").start();
        }
        else {
            MainLoader.play(null, bms.player.beatoraja.BMSPlayerMode.PLAY, true, config, player, songUpdated);
        }
	}

    @FXML
	public void loadAllBMS() {
		commit();
		if (confirmFullDatabaseUpdate()) {
			loadBMS(null, true);
		}
	}

    @FXML
	public void loadDiffBMS() {
		commit();
		loadBMS(null, false);
	}

	public void loadBMSPath(String updatepath){
		commit();
    	loadBMS(updatepath, false);
	}

	private boolean confirmFullDatabaseUpdate() {
		Alert confirmAlert = new Alert(
				Alert.AlertType.NONE,
				dbUpdateCheckDialogMessage,
				ButtonType.OK,
				ButtonType.CANCEL
		);
		return confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
	}

	/**
	 * BMSを読み込み、楽曲データベースを更新する
	 *
	 * @param updateAll
	 *            falseの場合は追加削除分のみを更新する
	 */
	public void loadBMS(String updatepath, boolean updateAll) {
		commit();

		ResourceBundle bundle = ResourceBundle.getBundle("resources.UIResources");
		final Stage loadingBarStage = new Stage();
		SongDatabaseUpdateListener songDatabaseUpdateListener = new SongDatabaseUpdateListener();
		Runnable progressRunnable = () -> {
			// JavaFX UI code must be run inside a Platform run context
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    loadingBarStage.setResizable(false);
					// This modality freezes the launcher/primary stage
                    loadingBarStage.initModality(Modality.APPLICATION_MODAL);
                    loadingBarStage.setTitle(bundle.getString("PROGRESS_BMS_TITLE"));
                    loadingBarStage.initStyle(StageStyle.UTILITY);

                    ProgressBar progressBar = new ProgressBar();
                    progressBar.setPrefWidth(400);

                    Label messageLabel = new Label(bundle.getString("PROGRESS_BMS_LABEL"));
					Supplier<String> getProcessStatusText = () -> String.format(
							bundle.getString("PROGRESS_BMS_STATUS"),
							songDatabaseUpdateListener.getBMSFilesCount(),
							songDatabaseUpdateListener.getProcessedBMSFilesCount(),
							songDatabaseUpdateListener.getNewBMSFilesCount()
					);
					Label processStatusLabel = new Label(getProcessStatusText.get());
					AnimationTimer timer = new AnimationTimer() {
						private long lastUpdate = -1;
						private final long interval = 1000_000_000;

						@Override
						public void handle(long now) {
							if (now - lastUpdate >= interval) {
								processStatusLabel.setText(getProcessStatusText.get());
								lastUpdate = now;
							}
						}
					};
					timer.start();

                    VBox root = new VBox(10);
                    root.setStyle("-fx-padding: 20; -fx-alignment: center;");
                    root.getChildren().addAll(messageLabel, processStatusLabel, progressBar);

                    Scene scene = new Scene(root);
                    loadingBarStage.setScene(scene);

					// Prevents closing. This has the side effect of preventing windowing system close requests but
					// the application can still be force killed by the user if necessary
					loadingBarStage.setOnCloseRequest(Event::consume);
                    loadingBarStage.show();
					loadingBarStage.setOnHidden(e -> timer.stop());
                }
            });
        };

        Runnable loadBMSRunnable = () -> {
            try {
                SongDatabaseAccessor songdb = MainLoader.getScoreDatabaseAccessor();
                SongInformationAccessor infodb = config.isUseSongInfo() ?
                        new SongInformationAccessor(Paths.get("songinfo.db").toString()) : null;
                logger.info("song.db更新開始");
                songdb.updateSongDatas(updatepath, config.getBmsroot(), updateAll, false, infodb, songDatabaseUpdateListener);
                logger.info("song.db更新完了");
                songUpdated = true;

				// Once again, JavaFX UI code must be run inside a Platform context. Hide progress bar and resume
				// normal launcher behaviour
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						loadingBarStage.hide();
						if (songDatabaseUpdateListener.getArchivesScanned() > 0) {
							String summary = String.format(bundle.getString("ARCHIVE_SCAN_RESULT"),
									songDatabaseUpdateListener.getArchivesLoaded(),
									songDatabaseUpdateListener.getArchivesRejected());
							Alert.AlertType type = songDatabaseUpdateListener.getArchivesRejected() > 0
									? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION;
							if (songDatabaseUpdateListener.getArchivesRejected() > 0) {
								summary += "\n" + String.format(bundle.getString("ARCHIVE_SCAN_LAST_FAILURE"),
										songDatabaseUpdateListener.getLastArchiveFailure());
							}
							new Alert(type, summary, ButtonType.OK).show();
						}
					}
				});
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        };

        new Thread(progressRunnable).start();
        new Thread(loadBMSRunnable).start();
	}

    @FXML
	public void importScoreDataFromLR2() {
		FileChooser chooser = new FileChooser();
		chooser.getExtensionFilters().setAll(new ExtensionFilter("Lunatic Rave 2 Score Database File", "*.db"));
		chooser.setTitle("LRのスコアデータベースを選択してください");
		File dir = chooser.showOpenDialog(null);
		if (dir == null) {
			return;
		}

		try {
			Class.forName("org.sqlite.JDBC");
			SongDatabaseAccessor songdb = MainLoader.getScoreDatabaseAccessor();
			String player = players.getValue();
			ScoreDatabaseAccessor scoredb = new ScoreDatabaseAccessor(config.getPlayerpath() + File.separatorChar + player + File.separatorChar + "score.db");
			scoredb.createTable();

			ScoreDataImporter scoreimporter = new ScoreDataImporter(scoredb);
			scoreimporter.importFromLR2ScoreDatabase(dir.getPath(), songdb);

		} catch (ClassNotFoundException e1) {
		}

	}

	@FXML
	public void exportBmsirVanillaScoreDatabase() {
		if (config == null || players.getValue() == null) return;
		bmsirExportVanillaScoreDb.setDisable(true);
		Path playerDirectory = Paths.get(config.getPlayerpath(), players.getValue());
		Thread worker = new Thread(() -> {
			try {
				BMSIRScoreDatabaseExport.ExportResult result =
						BMSIRScoreDatabaseExport.export(playerDirectory);
				Platform.runLater(() -> {
					bmsirExportVanillaScoreDb.setDisable(false);
					Alert alert = new Alert(Alert.AlertType.INFORMATION);
					alert.setTitle("BMS-IR Arena");
					alert.setHeaderText("Vanilla score database created");
					alert.setContentText(
							result.path() + "\n\nNormal scores: " + result.normalScores()
							+ "\nExcluded MANIAC scores: " + result.excludedManiacScores()
					);
					alert.showAndWait();
				});
			} catch (Exception error) {
				logger.warn("Vanilla score database export failed: {}", error.getMessage());
				Platform.runLater(() -> {
					bmsirExportVanillaScoreDb.setDisable(false);
					Alert alert = new Alert(Alert.AlertType.ERROR);
					alert.setTitle("BMS-IR Arena");
					alert.setHeaderText("Score database export failed");
					alert.setContentText(error.getMessage());
					alert.showAndWait();
				});
			}
		}, "bmsir-score-db-export");
		worker.setDaemon(true);
		worker.start();
	}

	@FXML
	public void startTwitterAuth() {
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setOAuthConsumerKey(txtTwitterConsumerKey.getText());
		cb.setOAuthConsumerSecret(txtTwitterConsumerSecret.getText());
		cb.setOAuthAccessToken(null);
		cb.setOAuthAccessTokenSecret(null);
		TwitterFactory twitterfactory = new TwitterFactory(cb.build());
		Twitter twitter = twitterfactory.getInstance();
		try {
			requestToken = twitter.getOAuthRequestToken();
			Desktop desktop = Desktop.getDesktop();
			URI uri = new URI(requestToken.getAuthorizationURL());
			desktop.browse(uri);
			player.setTwitterConsumerKey(txtTwitterConsumerKey.getText());
			player.setTwitterConsumerSecret(txtTwitterConsumerSecret.getText());
			player.setTwitterAccessToken("");
			player.setTwitterAccessTokenSecret("");
			txtTwitterPIN.setDisable(false);
			twitterPINButton.setDisable(false);
			txtTwitterAuthenticated.setVisible(false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@FXML
	public void startPINAuth() {
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setOAuthConsumerKey(player.getTwitterConsumerKey());
		cb.setOAuthConsumerSecret(player.getTwitterConsumerSecret());
		cb.setOAuthAccessToken(null);
		cb.setOAuthAccessTokenSecret(null);
		TwitterFactory twitterfactory = new TwitterFactory(cb.build());
		Twitter twitter = twitterfactory.getInstance();
		try {
			AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, txtTwitterPIN.getText());
			player.setTwitterAccessToken(accessToken.getToken());
			player.setTwitterAccessTokenSecret(accessToken.getTokenSecret());
			commit();
			update(config);
		} catch (TwitterException e) {
			e.printStackTrace();
		}
	}

    @FXML
	public void exit() {
		commit();
		Platform.exit();
		System.exit(0);
	}

	static class OptionListCell extends ListCell<Integer> {

		private final String[] strings;

		public OptionListCell(String[] strings) {
			this.strings = strings;
		}

		@Override
		protected void updateItem(Integer arg0, boolean arg1) {
			super.updateItem(arg0, arg1);
			if (arg0 != null) {
				setText(strings[arg0]);
			}
		}
	}

	enum PlayMode {
		BEAT_5K("5KEYS"),
		BEAT_7K("7KEYS"),
		BEAT_10K("10KEYS"),
		BEAT_14K("14KEYS"),
		POPN_9K("9KEYS"),
		KEYBOARD_24K("24KEYS"),
		KEYBOARD_24K_DOUBLE("24KEYS DOUBLE");

		public final String name;

		private PlayMode(String name) {
			this.name = name;
		}

		public String toString() {
			return name;
		}
	}
}
