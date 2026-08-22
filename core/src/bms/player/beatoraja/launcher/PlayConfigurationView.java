package bms.player.beatoraja.launcher;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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
import bms.player.beatoraja.play.BMSIRHispeed;
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
import javafx.geometry.Insets;
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
import javafx.scene.layout.FlowPane;
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
	static final double SIDEBAR_EDITOR_COLUMN_WIDTH = 480.0;
	static final Pos SIDEBAR_STANDALONE_TOGGLE_ALIGNMENT = Pos.CENTER_RIGHT;
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
	private VBox sidebarSearchNoResults;
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
	private CheckBox bmsirTableLevelDisplayEnabled;
	@FXML
	private CheckBox bmsirHideMissingTableSongs;
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
	private CheckBox bmsirLr2HispeedFixEnabled;
	@FXML
	private CheckBox bmsirPseudoFhsEnabled;
	@FXML
	private Label bmsirHispeedMode;
	@FXML
	private Spinner<Integer> bmsirBaseScrollSpeed;
	@FXML
	private Spinner<Integer> bmsirEquivalentGreenNumber;
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
	private final SidebarSearchIndex<Tab> sidebarSearchIndex = new SidebarSearchIndex<>();
	private final Map<Tab, Node> classicTabContents = new IdentityHashMap<>();
	private final Map<Tab, ScrollPane> sidebarTabContents = new IdentityHashMap<>();
	private final List<SidebarNodePlacement> sidebarNodePlacements = new ArrayList<>();
	private GridPane sidebarPlayerEditor;
	private Label sidebarDisplayNameLabel;
	private boolean playerEditorUsesSidebarLayout;
	private boolean sidebarPlayOptionsInitialized;
	private boolean sidebarPagesInitialized;
	private boolean sidebarNodesMoved;
	private boolean englishUi;
	private boolean updatingBmsirHispeedFields;

	private static final class SidebarNodePlacement {
		private final Node node;
		private final Pane originalParent;
		private final int originalIndex;
		private final StackPane sidebarHost;
		private final double originalMaxWidth;
		private final double originalMaxHeight;

		private SidebarNodePlacement(Node node, Pane originalParent, int originalIndex, StackPane sidebarHost) {
			this.node = node;
			this.originalParent = originalParent;
			this.originalIndex = originalIndex;
			this.sidebarHost = sidebarHost;
			this.originalMaxWidth = node instanceof Region region ? region.getMaxWidth() : Double.NaN;
			this.originalMaxHeight = node instanceof Region region ? region.getMaxHeight() : Double.NaN;
		}
	}

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
	private Button addBgmPathButton;
	@FXML
	private Button addSoundPathButton;

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
	private CheckBox enableBmsirBodyDownload;
	@FXML
	private ComboBox<String> httpDownloadSource;
	@FXML
	private TextField defaultDownloadURL;
	@FXML
	private TextField overrideDownloadURL;
	@FXML
	private Button importScoreButton;

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
		bmsirBaseScrollSpeed.setValueFactory(
				new SpinnerValueFactory.IntegerSpinnerValueFactory(
						PlayConfig.BMSIR_BASE_SCROLL_SPEED_MIN,
						PlayConfig.BMSIR_BASE_SCROLL_SPEED_MAX,
						100
				)
		);
		bmsirEquivalentGreenNumber.setValueFactory(
				new SpinnerValueFactory.IntegerSpinnerValueFactory(
						PlayConfig.DURATION_MIN,
						PlayConfig.DURATION_MAX,
						500
				)
		);
		bmsirPseudoFhsEnabled.disableProperty().bind(
				bmsirLr2HispeedFixEnabled.selectedProperty().not()
		);
		bmsirBaseScrollSpeed.disableProperty().bind(
				bmsirLr2HispeedFixEnabled.selectedProperty().not()
		);
		bmsirEquivalentGreenNumber.disableProperty().bind(
				bmsirLr2HispeedFixEnabled.selectedProperty().not()
		);
		bmsirBaseScrollSpeed.valueProperty().addListener(
				(observable, oldValue, value) -> updateBmsirEquivalentGreen()
		);
		bmsirEquivalentGreenNumber.valueProperty().addListener(
				(observable, oldValue, value) -> updateBmsirBaseFromGreen()
		);
		for (javafx.beans.value.ObservableValue<?> value : List.of(
				hispeed.valueProperty(),
				enableLanecover.selectedProperty(),
				lanecover.valueProperty(),
				enableLift.selectedProperty(),
				lift.valueProperty()
		)) {
			value.addListener((observable, oldValue, newValue) -> updateBmsirEquivalentGreen());
		}
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
		sidebarNavigation.setPlaceholder(new Label(bundle.getString("CONFIGURATION_SEARCH_NO_MATCHES")));
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
		initializeSidebarPages();
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
		List<Tab> filtered = sidebarSearchIndex.filter(configurationTabOrder, query);
		sidebarNavigation.getItems().setAll(filtered);
		Tab current = configurationTabs.getSelectionModel().getSelectedItem();
		Tab selection = SidebarSearchIndex.preferredSelection(filtered, current);
		if (selection == null) {
			sidebarNavigation.getSelectionModel().clearSelection();
		} else {
			sidebarNavigation.getSelectionModel().select(selection);
			if (selection != current) {
				configurationTabs.getSelectionModel().select(selection);
			}
		}
		updateSidebarSearchPresentation(!filtered.isEmpty());
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
		// Persistent descriptions now live with every setting row, so the former
		// illustrated context card stays out of both layouts.
		setManagedVisible(contextHelpPanel, false);
		setManagedVisible(classicPlayOptionContent, !sidebar);
		setManagedVisible(sidebarPlayOptionScroll, sidebar);
		moveSidebarNodes(sidebar);
		for (Map.Entry<Tab, Node> entry : classicTabContents.entrySet()) {
			setManagedVisible(entry.getValue(), !sidebar);
			ScrollPane sidebarPage = sidebarTabContents.get(entry.getKey());
			if (sidebarPage != null) {
				setManagedVisible(sidebarPage, sidebar);
			}
		}
		if (sidebar) {
			if (!configurationContent.getStyleClass().contains("sidebar-mode")) {
				configurationContent.getStyleClass().add("sidebar-mode");
			}
			if (!configurationTabs.getStyleClass().contains("sidebar-content-tabs")) {
				configurationTabs.getStyleClass().add("sidebar-content-tabs");
			}
			updateSidebarNavigationItems(sidebarSearch.getText());
			showTabContextHelp(configurationTabs.getSelectionModel().getSelectedItem());
		} else {
			updateSidebarSearchPresentation(true);
			configurationContent.getStyleClass().remove("sidebar-mode");
			configurationTabs.getStyleClass().remove("sidebar-content-tabs");
			configurationTabs.getStyleClass().remove("sidebar-form-tab");
			sidebarPlayerSummary.setSelected(false);
		}
		updateSidebarTabPresentation(configurationTabs.getSelectionModel().getSelectedItem());
		updatePlayerPanelVisibility();
	}

	private void updateSidebarSearchPresentation(boolean hasResults) {
		boolean showNoResults = configurationLayout.getValue() == Config.ConfigurationLayout.SIDEBAR
				&& !hasResults;
		setManagedVisible(configurationContent, !showNoResults);
		setManagedVisible(sidebarSearchNoResults, showNoResults);
	}

	private void updateSidebarTabPresentation(Tab tab) {
		boolean sidebarForm = configurationLayout.getValue() == Config.ConfigurationLayout.SIDEBAR
				&& (tab == optionTab || sidebarTabContents.containsKey(tab));
		if (sidebarForm) {
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

	private void initializeSidebarPages() {
		if (sidebarPagesInitialized) {
			return;
		}
		initializeSidebarVideo();
		initializeSidebarAudio();
		initializeSidebarInput();
		initializeSidebarResource();
		initializeSidebarMusicSelect();
		initializeSidebarSkin();
		initializeSidebarOther();
		initializeSidebarBmsir();
		initializeSidebarIr();
		initializeSidebarTable();
		initializeSidebarStream();
		initializeSidebarDiscord();
		initializeSidebarObs();
		sidebarPagesInitialized = true;
	}

	private void installSidebarPage(Tab tab, VBox... cards) {
		Node classic = tab.getContent();
		VBox page = new VBox(14, cards);
		page.setPadding(new Insets(2, 2, 16, 2));
		page.getStyleClass().add("sidebar-settings-page");

		ScrollPane sidebar = new ScrollPane(page);
		sidebar.setFitToWidth(true);
		sidebar.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		sidebar.getStyleClass().add("sidebar-settings-scroll");
		setManagedVisible(sidebar, false);

		StackPane host = new StackPane(classic, sidebar);
		classicTabContents.put(tab, classic);
		sidebarTabContents.put(tab, sidebar);
		tab.setContent(host);
	}

	private void moveSidebarNodes(boolean sidebar) {
		if (sidebarNodesMoved == sidebar) {
			return;
		}
		if (sidebar) {
			for (SidebarNodePlacement placement : sidebarNodePlacements) {
				placement.originalParent.getChildren().remove(placement.node);
				placement.sidebarHost.getChildren().setAll(placement.node);
				if (placement.node instanceof Region region) {
					region.setMaxWidth(Double.MAX_VALUE);
					region.setMaxHeight(Double.MAX_VALUE);
				}
			}
		} else {
			Map<Pane, List<SidebarNodePlacement>> byParent = new IdentityHashMap<>();
			for (SidebarNodePlacement placement : sidebarNodePlacements) {
				placement.sidebarHost.getChildren().remove(placement.node);
				byParent.computeIfAbsent(placement.originalParent, unused -> new ArrayList<>()).add(placement);
			}
			for (List<SidebarNodePlacement> placements : byParent.values()) {
				placements.sort(Comparator.comparingInt(placement -> placement.originalIndex));
				for (SidebarNodePlacement placement : placements) {
					int index = Math.min(placement.originalIndex, placement.originalParent.getChildren().size());
					placement.originalParent.getChildren().add(index, placement.node);
					if (placement.node instanceof Region region) {
						region.setMaxWidth(placement.originalMaxWidth);
						region.setMaxHeight(placement.originalMaxHeight);
					}
				}
			}
		}
		sidebarNodesMoved = sidebar;
	}

	private void initializeSidebarVideo() {
		installSidebarPage(videoTab,
				sidebarSettingCard(
						sidebarSettingRow(videoTab, "displayMode", "画面モード", "Display Mode",
								"ウィンドウ、ボーダーレス、フルスクリーンのどれで起動するかを選びます。",
								"Choose Window, Borderless, or Fullscreen startup."),
						sidebarSettingRow(videoTab, "resolution", "解像度", "Resolution",
								"ゲーム画面の幅と高さです。ディスプレイとスキンに合う値を選びます。",
								"Choose the game width and height to match the display and skin."),
						sidebarSettingRow(videoTab, "monitor", "表示モニター", "Monitor",
								"複数画面を使用している場合の起動先を選びます。",
								"Choose which display receives the game window."),
						sidebarSettingRow(videoTab, "vSync", "垂直同期", "Vsync",
								"画面更新をモニターの周期に合わせ、ティアリングを抑えます。遅延が気になる場合はOFFも試せます。",
								"Synchronize frames to the monitor to reduce tearing; try OFF when latency matters more."),
						sidebarSettingRow(videoTab, "maxFps", "最大FPS", "Maximum FPS",
								"垂直同期がOFFのときの描画上限です。0は上限なしです。",
								"Cap rendering when Vsync is off; 0 means uncapped.")
				),
				sidebarSettingCard(
						sidebarSettingRow(videoTab, "bgaOp", "BGA", "BGA",
								"譜面の背景アニメーションを表示するか決めます。負荷を下げたい場合はOFFにします。",
								"Choose whether chart background animation is shown; turn it off to reduce load."),
						sidebarSettingRow(videoTab, "missLayerTime", "ミスレイヤー表示時間", "Miss-layer duration",
								"ミス時に表示されるBGAレイヤーの長さです。単位は ms です。",
								"Set how long the miss BGA layer stays visible, in ms."),
						sidebarSettingRow(videoTab, "bgaExpand", "BGAの拡大方法", "BGA scaling",
								"縦横比を保つ、領域いっぱいに広げる、拡大しない、から選びます。",
								"Choose aspect-preserving, full-area, or no expansion.")
				)
		);
	}

	private void initializeSidebarAudio() {
		installSidebarPage(audioTab,
				sidebarSettingCard(
						sidebarSettingRow(audioTab, "audio", "音声出力", "Audio output",
								"OpenAL、PortAudio、ASIOなど、使用する出力方式を選びます。",
								"Choose OpenAL, PortAudio, ASIO, or another available output path."),
						sidebarSettingRow(audioTab, "audioname", "出力デバイス", "Output device",
								"音を出す機器を選びます。選んだ出力方式で利用できる機器だけが表示されます。",
								"Choose the output device available through the selected audio backend."),
						sidebarSettingRow(audioTab, "wasapiMode", "WASAPIモード", "WASAPI mode",
								"WindowsのWASAPI使用時に共有または排他を選びます。排他は低遅延ですが、他アプリと同時利用できません。",
								"Choose Shared or Exclusive for Windows WASAPI; Exclusive lowers latency but blocks other apps."),
						sidebarSettingRow(audioTab, "audiobuffer", "オーディオバッファ", "Audio buffer",
								"小さいほど遅延は減りますが、音切れしやすくなります。単位は KB です。",
								"Lower values reduce latency but make dropouts more likely. The unit is KB."),
						sidebarSettingRow(audioTab, "audiosim", "同時発音数", "Simultaneous sounds",
								"同時に再生できる音声数です。多い譜面で音が欠ける場合に増やします。",
								"Set how many sounds can play together; raise it if dense charts lose sounds."),
						sidebarSettingRow(audioTab, "audiosamplerate", "サンプルレート", "Sample rate",
								"出力周波数を選びます。通常はデバイス既定または44.1 kHzで十分です。",
								"Choose the output rate; the device default or 44.1 kHz is normally sufficient.")
				),
				sidebarSettingCard(
						sidebarSettingRow(audioTab, "systemvolume", "全体音量", "System volume",
								"ゲーム全体の音量を調整します。",
								"Adjust the overall game volume."),
						sidebarSettingRow(audioTab, "keyvolume", "キー音量", "Key volume",
								"譜面のキー音だけの音量を調整します。",
								"Adjust the chart key-sound volume."),
						sidebarSettingRow(audioTab, "bgvolume", "BGM音量", "BGM volume",
								"BGMや自動再生音の音量を調整します。",
								"Adjust BGM and automatically played audio."),
						sidebarSettingRow(audioTab, "normalizeVolume", "譜面音量を正規化", "Normalize chart volume",
								"曲ごとの音量差を抑えるため、読み込んだ音声の基準音量を揃えます。",
								"Reduce volume differences by normalizing loaded chart audio.")
				),
				sidebarSettingCard(
						sidebarSettingRow(audioTab, "audioFreqOption", "周波数変更時の音程", "Pitch during frequency changes",
								"速度変更時に音程も変えるか、時間伸縮で音程を保つかを選びます。",
								"Choose whether speed changes alter pitch or preserve it through time stretching."),
						sidebarSettingRow(audioTab, "audioFastForward", "早送り時の音声", "Fast-forward audio",
								"譜面プレビューなどの早送り中に使う音声処理を選びます。",
								"Choose the audio processing used while previews fast-forward."),
						sidebarSettingRow(audioTab, "loopResultSound", "リザルト音をループ", "Loop result sound",
								"通常リザルトに滞在している間、リザルト音を繰り返します。",
								"Repeat result audio while the normal result screen remains open."),
						sidebarSettingRow(audioTab, "loopCourseResultSound", "コースリザルト音をループ", "Loop course-result sound",
								"コースリザルトに滞在している間、リザルト音を繰り返します。",
								"Repeat result audio while the course result screen remains open.")
				)
		);
	}

	private void initializeSidebarInput() {
		StackPane controllerWorkspace = sidebarMovable(inputTab, "controller_tableView");
		controllerWorkspace.setMinHeight(190);
		installSidebarPage(inputTab,
				sidebarSettingCard(
						sidebarSettingRow(inputTab, "inputconfig", "設定対象モード", "Input mode",
								"7KEYS、14KEYSなど、編集する入力モードを選びます。",
								"Choose the key mode whose input mapping is being edited."),
						sidebarSettingRow(inputTab, "backgroundControllerInput", "非アクティブ時の専用コントローラー入力", "Background controller input",
								"ゲーム画面が非アクティブでも、HID・ゲームコントローラー入力を受け付けます。キーボード入力は対象外です。",
								"Accept HID/game-controller input while the game is unfocused; keyboard input remains focus-bound."),
						sidebarSettingRow(inputTab, "inputduration", "最小入力間隔", "Minimum input interval",
								"同じ入力を再び受け付けるまでの最短時間です。単位は ms です。",
								"Set the shortest interval before the same input is accepted again, in ms."),
						sidebarSettingRow(inputTab, "jkoc_hack", "JKOC HACK", "JKOC HACK",
								"一部の旧型コントローラー向け互換入力処理です。必要な機器だけで有効にします。",
								"Enable legacy compatibility input handling only for controllers that require it.")
				),
				sidebarSettingCard(
						sidebarWorkspaceRow(inputTab, "接続コントローラー", "Connected controllers",
								"各プレイサイドの機器、アナログスクラッチ、停止閾値とアルゴリズムを編集します。",
								"Edit devices, analog scratch, stop thresholds, and algorithms for each play side.",
								controllerWorkspace)
				),
				sidebarSettingCard(
						sidebarSettingRow(inputTab, "mouseScratch", "マウス皿", "Mouse scratch",
								"マウス移動をスクラッチ入力として使用します。",
								"Use mouse movement as scratch input."),
						sidebarSettingRow(inputTab, "mouseScratchMode", "マウス皿のアルゴリズム", "Mouse-scratch algorithm",
								"マウス移動をスクラッチ回転へ変換する方法を選びます。",
								"Choose how mouse movement is converted into scratch rotation."),
						sidebarSettingRow(inputTab, "mouseScratchTimeThreshold", "マウス皿の停止閾値", "Mouse-scratch stop threshold",
								"最後の移動からスクラッチ停止とみなすまでの時間です。単位は ms です。",
								"Set the time after the last movement before scratch input stops, in ms."),
						sidebarSettingRow(inputTab, "mouseScratchDistance", "マウス皿の距離", "Mouse-scratch distance",
								"スクラッチ1段階として扱うマウス移動量です。小さいほど敏感になります。",
								"Set the movement distance per scratch step; smaller values are more sensitive.")
				)
		);
	}

	private void initializeSidebarMusicSelect() {
		installSidebarPage(musicselectTab,
				sidebarSettingCard(
						sidebarSettingRow(musicselectTab, "scrolldurationlow", "最初の選曲スクロール間隔", "Initial scroll interval",
								"方向入力を押し続けたとき、連続スクロールが始まるまでの待ち時間です。単位は ms です。",
								"Set the delay before held navigation begins continuous scrolling, in ms."),
						sidebarSettingRow(musicselectTab, "scrolldurationhigh", "それ以降のスクロール間隔", "Repeated scroll interval",
								"連続スクロール開始後、次の曲へ進む間隔です。小さいほど速くなります。",
								"Set the interval between later scroll steps; smaller values move faster."),
						sidebarSettingRow(musicselectTab, "analogScroll", "アナログスクロール", "Analog scroll",
								"皿などのアナログ回転で選曲リストを移動します。",
								"Move the song list with analog rotation such as a turntable."),
						sidebarSettingRow(musicselectTab, "analogTicksPerScroll", "アナログスクロール感度", "Analog-scroll sensitivity",
								"何回分のアナログ変化で1曲送るかを決めます。小さいほど敏感です。",
								"Choose how many analog ticks move one song; smaller values are more sensitive.")
				),
				sidebarSettingCard(
						sidebarSettingRow(musicselectTab, "useSongInfo", "楽曲詳細情報データベースを使用", "Use song-information database",
								"曲名・アーティスト以外の追加情報を選曲画面で使用します。",
								"Use additional song metadata beyond title and artist in Music Select."),
						sidebarSettingRow(musicselectTab, "folderlamp", "フォルダーランプ", "Folder lamp",
								"フォルダー内のクリア状況をまとめたランプを表示します。",
								"Show an aggregate lamp for clear status inside each folder."),
						sidebarSettingRow(musicselectTab, "shownoexistingbar", "存在しない楽曲バーを表示", "Show missing-song bars",
								"難易度表にはあるがローカルにない曲も一覧へ表示します。",
								"Show table entries even when the chart is not available locally."),
						sidebarSettingRow(musicselectTab, "songPreview", "楽曲プレビュー", "Song preview",
								"選曲中の試聴方法を選びます。LOOPはプレビュー区間を繰り返します。",
								"Choose how selection audio previews play; LOOP repeats the preview segment."),
						sidebarSettingRow(musicselectTab, "randomselect", "RANDOM SELECT", "RANDOM SELECT",
								"選曲一覧にランダム選択項目を表示します。",
								"Show a random-selection entry in Music Select."),
						sidebarSettingRow(musicselectTab, "maxsearchbar", "検索バー上限数", "Search-bar limit",
								"検索結果として保持する検索バーの最大数です。",
								"Set the maximum number of search-result bars retained."),
						sidebarSettingRow(musicselectTab, "chartReplicationMode", "譜面複製モード", "Chart replication mode",
								"ライバル譜面など、複製された譜面データの扱い方を選びます。",
								"Choose how replicated chart data, such as rival charts, is handled."),
						sidebarSettingRow(musicselectTab, "skipDecideScreen", "決定画面を省略", "Skip decide screen",
								"曲決定後のDECIDE画面を省略し、読み込みへ直接進みます。",
								"Skip the DECIDE screen and proceed directly to loading.")
				)
		);
	}

	private void initializeSidebarResource() {
		StackPane bmsRoots = sidebarMovableParent(resourceTab, "bmsroot");
		bmsRoots.setMinHeight(170);
		VBox bmsButtons = new VBox(8,
				sidebarControl(resourceTab, "addSongPathButton"),
				sidebarControl(resourceTab, "downloadDirectoryButton"),
				sidebarControl(resourceTab, "workDirectoryButton")
		);
		bmsButtons.setMinWidth(210);
		HBox bmsWorkspace = new HBox(12, bmsRoots, bmsButtons);
		HBox.setHgrow(bmsRoots, Priority.ALWAYS);

		CheckBox physicalFolderFilter = (CheckBox) requireNode(
				resourceTab,
				"bmsirPhysicalFolderFilterEnabled"
		);
		StackPane physicalFolderOptions = sidebarMovable(
				resourceTab,
				"bmsirPhysicalFolderFilterOptions"
		);
		physicalFolderOptions.setMinHeight(80);
		VBox physicalFolderOptionsCard = sidebarSettingCard(
				sidebarWorkspaceRow(
						resourceTab,
						"表示する物理フォルダー",
						"Visible physical folders",
						"チェックしたBMS Pathだけを選曲ルートへ残します。全チェックを外すと物理フォルダーをすべて隠せます。",
						"Keep only checked BMS Paths at the Music Select root; leave all unchecked to hide every physical folder.",
						physicalFolderOptions
				)
		);
		physicalFolderOptionsCard.visibleProperty().bind(
				physicalFolderFilter.selectedProperty()
		);
		physicalFolderOptionsCard.managedProperty().bind(
				physicalFolderFilter.selectedProperty()
		);

		StackPane tableList = sidebarMovableParent(resourceTab, "tableurl");
		tableList.setMinHeight(260);
		VBox tableButtons = new VBox(8,
				sidebarControl(resourceTab, "updateAllTablesButton"),
				sidebarControl(resourceTab, "chooseTablesButton"),
				sidebarControl(resourceTab, "addTableUrlButton")
		);
		tableButtons.setMinWidth(210);
		HBox tableWorkspace = new HBox(12, tableList, tableButtons);
		HBox.setHgrow(tableList, Priority.ALWAYS);

		installSidebarPage(resourceTab,
				sidebarSettingCard(
						sidebarWorkspaceRow(resourceTab, "BMS Path", "BMS Path",
								"楽曲を置いているルートフォルダーを登録します。選択したルートは右クリックで個別更新・表示・削除もできます。",
								"Register song-library root folders. Right-click a selected root to update, open, copy, or remove it.",
								bmsWorkspace),
						sidebarSettingRow(resourceTab, "bmsirPhysicalFolderFilterEnabled", "選曲ルートの物理フォルダーを絞り込む", "Limit physical folders at the Music Select root",
								"OFFでは全物理フォルダーを表示します。ONではチェックしたBMS Pathだけを表示します。",
								"OFF shows every physical folder; ON shows only checked BMS Paths.")
				),
				physicalFolderOptionsCard,
				sidebarSettingCard(
						sidebarWorkspaceRow(resourceTab, "難易度表", "Difficulty tables",
								"選曲画面へ読み込む難易度表を管理します。既存表の追加とカスタムURLの追加を分けて操作できます。",
								"Manage difficulty tables loaded into Music Select, using separate built-in and custom-URL actions.",
								tableWorkspace)
				),
				sidebarSettingCard(
						sidebarSettingRow(resourceTab, "updatesong", "起動直後に楽曲更新", "Update songs after startup",
								"設定画面を閉じて起動した直後、追加・変更された楽曲をバックグラウンドで確認します。",
								"Check added or changed songs in the background immediately after startup."),
						sidebarSettingRow(resourceTab, "scanSongArchives", "ZIP/RAR/7z内の曲を展開せずに走査", "Scan songs inside ZIP/RAR/7z",
								"対応アーカイブ内のBMS/BMSONを展開せず、仮想パスのまま楽曲ライブラリーへ読み込みます。",
								"Read BMS/BMSON inside supported archives through virtual paths without extracting them."),
						sidebarSettingRow(resourceTab, "updateDatabaseButton", "楽曲読み込み", "Load songs",
								"追加・変更された楽曲だけを確認します。通常の更新はこちらを使います。",
								"Check only added or changed songs; use this for ordinary updates."),
						sidebarSettingRow(resourceTab, "rebuildDatabaseButton", "楽曲全更新", "Full song update",
								"登録済みの全楽曲を読み直します。時間がかかるため、データベースを作り直す必要がある場合だけ使います。",
								"Reread every registered song; use only when the whole database must be rebuilt.")
				)
		);
	}

	private void initializeSidebarSkin() {
		StackPane skinOptions = sidebarMovable(skinTab, "skinconfig");
		skinOptions.setMinHeight(360);
		installSidebarPage(skinTab,
				sidebarSettingCard(
						sidebarSettingRow(skinTab, "skintypeSelector", "スキン種類", "Skin category",
								"選曲、プレイ、リザルトなど、変更する画面の種類を選びます。",
								"Choose which screen category—select, play, result, and so on—is being edited."),
						sidebarSettingRow(skinTab, "skinheaderSelector", "スキン", "Skin",
								"選択した画面種類で使用するスキンを選びます。",
								"Choose the skin used by the selected screen category."),
						sidebarSettingRow(skinTab, "skinUpdateButton", "スキン一覧を更新", "Refresh skins",
								"スキンフォルダーを読み直し、選択可能なスキンを更新します。",
								"Rescan skin folders and refresh the available skin list.")
				),
				sidebarSettingCard(
						sidebarWorkspaceRow(skinTab, "スキン固有設定とプレビュー", "Skin options and preview",
								"選択中のスキンが公開しているオプション、ファイル、オフセットを編集し、対応スキンではプレビューを確認します。",
								"Edit options, files, and offsets exposed by the selected skin and inspect its preview when supported.",
								skinOptions)
				),
				sidebarSettingCard(
						sidebarSettingRow(skinTab, "BGM Path (LR2)", "BGM Path (LR2)",
								"LR2形式スキンが使うBGMフォルダーを指定します。右のボタンからフォルダーを選べます。",
								"Choose the BGM folder used by LR2-format skins; use the button to browse.",
								sidebarCompound(sidebarControl(skinTab, "bgmpath"), sidebarControl(skinTab, "addBgmPathButton"))),
						sidebarSettingRow(skinTab, "Sound Path (LR2)", "Sound Path (LR2)",
								"LR2形式スキンが使う効果音フォルダーを指定します。右のボタンからフォルダーを選べます。",
								"Choose the sound-effect folder used by LR2-format skins; use the button to browse.",
								sidebarCompound(sidebarControl(skinTab, "soundpath"), sidebarControl(skinTab, "addSoundPathButton")))
				)
		);
	}

	private void initializeSidebarOther() {
		installSidebarPage(otherTab,
				sidebarSettingCard(
						sidebarSettingRow(otherTab, "configurationLayout", "設定画面", "Configuration screen",
								"クラシックは従来の上タブ、サイドバーは左側のカテゴリと項目ごとの説明を使用します。設定内容は共通です。",
								"Classic uses top tabs; Sidebar uses left categories and per-setting explanations. Both edit the same settings.")
				),
				sidebarSettingCard(
						sidebarSettingRow(otherTab, "usecim", "スキン画像の高速化キャッシュを作成", "Create skin image cache",
								"スキン画像の読み込みを速くするCIMキャッシュを作成します。初回処理には時間がかかります。",
								"Create CIM cache files to accelerate skin image loading; the initial pass may take time."),
						sidebarSettingRow(otherTab, "clipboardScreenshot", "スクリーンショットをクリップボードへコピー", "Copy screenshots to clipboard",
								"スクリーンショット保存時、画像データをOSのクリップボードにもコピーします。",
								"Copy image data to the OS clipboard whenever a screenshot is saved."),
						sidebarSettingRow(otherTab, "importScoreButton", "LR2スコアをインポート", "Import LR2 scores",
								"既存のLR2スコアデータベースからローカルスコアを取り込みます。",
								"Import local scores from an existing LR2 score database.")
				),
				sidebarSettingCard(
						sidebarSettingRow(otherTab, "enableIpfs", "IPFSによるBMS自動ダウンロード", "Automatic BMS download via IPFS",
								"対応する楽曲をIPFSゲートウェイから自動取得できるようにします。",
								"Allow supported songs to be downloaded automatically through an IPFS gateway."),
						sidebarSettingRow(otherTab, "ipfsurl", "IPFS URL", "IPFS URL",
								"自動ダウンロードで使用するIPFSゲートウェイのURLです。",
								"Set the IPFS gateway URL used for automatic downloads."),
						sidebarSettingRow(otherTab, "enableHttp", "HTTPによるBMS自動ダウンロード", "Automatic BMS download via HTTP",
								"対応する楽曲を選択したHTTP配布元から自動取得できるようにします。",
								"Allow supported songs to be downloaded automatically from the selected HTTP provider."),
						sidebarSettingRow(otherTab, "enableBmsirBodyDownload", "BMS-IR本体URLから取得", "Download from BMS-IR body URLs",
								"既定OFF。登録URLが配布ページならZIP/RAR/7zリンクを限定抽出し、失敗時はWaybackを参照します。圧縮は展開せず、形式・構造・対象譜面MD5を検査します。ウイルス検査ではありません。",
								"Off by default. A registered distribution page may resolve bounded ZIP/RAR/7z links before one Wayback fallback. Retained archives are checked for format, structure, and chart MD5; this is not antivirus scanning."),
						sidebarSettingRow(otherTab, "httpDownloadSource", "HTTP配布元", "HTTP provider",
								"自動ダウンロードで使用する既定の配布サービスを選びます。",
								"Choose the default provider used for automatic HTTP downloads."),
						sidebarSettingRow(otherTab, "defaultDownloadURL", "既定HTTPサーバーURL", "Default HTTP server URL",
								"選択した配布元が使用するURL形式です。通常は変更しません。",
								"The URL template used by the selected provider; normally leave it unchanged."),
						sidebarSettingRow(otherTab, "overrideDownloadURL", "上書きHTTPサーバーURL", "Override HTTP server URL",
								"独自の互換サーバーを使う場合だけ指定します。空欄なら既定URLを使います。",
								"Set only for a custom compatible server; leave blank to use the default URL.")
				)
		);
	}

	private void initializeSidebarBmsir() {
		FlowPane keyModes = new FlowPane();
		keyModes.setHgap(12);
		keyModes.setVgap(8);
		keyModes.getStyleClass().add("sidebar-key-mode-list");
		for (String[] entry : new String[][] {
				{ "ALL", "bmsirSelectModeAll" },
				{ "7K", "bmsirSelectMode7k" },
				{ "14K", "bmsirSelectMode14k" },
				{ "9K", "bmsirSelectMode9k" },
				{ "5K", "bmsirSelectMode5k" },
				{ "10K", "bmsirSelectMode10k" },
				{ "24K", "bmsirSelectMode24k" },
				{ "24K DP", "bmsirSelectMode24kDp" }
		}) {
			keyModes.getChildren().add(sidebarLabeledToggle(
					entry[0], (CheckBox) requireNode(bmsirSpecificTab, entry[1])
			));
		}
		installSidebarPage(bmsirSpecificTab,
				sidebarSettingCard(
						sidebarSettingRow(bmsirSpecificTab, "bmsirArenaLanguage", "本体UI言語", "Built-in UI language",
								"Arenaオーバーレイ、フェーズ表示、MANIAC OPTIONSなど、本体組み込み画面の言語を選びます。全画面への反映には再起動が必要です。",
								"Choose the language for built-in Arena and MANIAC UI. Restart the game to apply it everywhere.")
				),
				sidebarSettingCard(
						sidebarSettingRow(bmsirSpecificTab, "bmsirOneBassEnabled", "START＋1鍵の正規1鍵固定", "START + one-key RANDOM anchor",
								"通常RANDOMで曲決定時にSTARTと任意の1鍵を押すと、その鍵へ正規譜面の1鍵レーンを固定します。",
								"During standard RANDOM, hold START and one key at song confirmation to anchor source lane 1 there."),
						sidebarSettingRow(bmsirSpecificTab, "bmsirStartHerePreviewEnabled", "譜面読込中・READY中に初手ノーツを表示", "Show first notes during loading and READY",
								"最初に発音する同時押しを、使用中スキンのノーツ画像でレーン上部またはSUD+直下へ表示します。",
								"Show the first sounding chord with the active skin's notes at the lane top or below SUD+."),
						sidebarSettingRow(bmsirSpecificTab, "bmsirDanLocalSyncEnabled", "BMS-IR段位をローカル同期", "Synchronize BMS-IR courses locally",
								"Primary IRから取得した段位コースを現在のプレイヤーへ保存します。通信失敗時は前回の正常データを残します。",
								"Save courses received from Primary IR for the current player and keep the last valid data on failure.")
				),
				sidebarSettingCard(
						sidebarSettingRow(bmsirSpecificTab, "bmsirStartButtonAction", "選曲画面のSTART短押し", "START short press in Music Select",
								"START短押しの動作を選びます。350 ms以上の長押しではプレイOPを開きます。",
								"Choose the START short-press action; holding for 350 ms opens Play Options."),
						sidebarSettingRow(bmsirSpecificTab, "bmsirSelectButtonAction", "選曲画面のSELECT短押し", "SELECT short press in Music Select",
								"SELECT短押しの動作を選びます。350 ms以上の長押しではアシストOPを開きます。",
								"Choose the SELECT short-press action; holding for 350 ms opens Assist Options."),
						sidebarSettingRow(bmsirSpecificTab, "bmsirSelectDifficultyDisplay", "難易度の表示方法", "Difficulty display",
								"譜面を個別行で表示するか、同じ曲をLR2風の1行へまとめるかを選びます。",
								"Choose separate chart rows or an LR2-style grouped row for the same song."),
						sidebarSettingRow(bmsirSpecificTab, "対象鍵盤モード", "Visible key modes",
								"選曲画面へ表示し、鍵盤数変更で巡回するモードを選びます。少なくとも1つは有効にしてください。",
								"Choose modes shown in Music Select and included in key-mode cycling; keep at least one enabled.",
								keyModes),
						sidebarSettingRow(bmsirSpecificTab, "bmsirTableLevelDisplayEnabled", "難易度表の難易度をLEVEL表示に使う", "Use difficulty-table levels for LEVEL",
								"ONでは表エントリーの最初の整数を曲バー、選択曲LEVEL、LEVELソートに使います。OFFでは譜面本来の#PLAYLEVELを使います。",
								"When enabled, use the first integer from each table entry for song bars, selected-song LEVEL, and LEVEL sorting. Disable it to use the chart's #PLAYLEVEL."),
						sidebarSettingRow(bmsirSpecificTab, "bmsirHideMissingTableSongs", "全難易度表で未所持曲を隠す", "Hide missing songs in every table",
								"難易度表フォルダーでは、ローカルに所持していない曲を一覧から隠します。通常フォルダーや検索には影響しません。",
								"Hide unavailable songs inside difficulty tables without affecting ordinary folders or searches.")
				),
				sidebarSettingCard(
						sidebarSettingRow(bmsirSpecificTab, "bmsirLr2HispeedFixEnabled", "LR2仕様のHI-SPEED固定", "LR2-style fixed HI-SPEED",
								"150 BPM基準とモード別基本スクロールで、既存のHI-SPEED FIX計算を上書きします。既定はOFFです。",
								"Override the existing HI-SPEED FIX calculation with a 150 BPM reference and per-mode base scroll. Off by default."),
					sidebarSettingRow(bmsirSpecificTab, "bmsirPseudoFhsEnabled", "疑似FHS", "Pseudo FHS",
								"プレイ中のSTART＋SELECT短押しで現在の緑数字を固定します。選曲に戻る長押し判定は、設定時間と500 msのうち長い方を使います。",
								"Short-press START + SELECT during play to latch the current green number. The exit hold uses the configured delay, with a minimum of 500 ms."),
						sidebarSettingRow(bmsirSpecificTab, "対象モード", "Target mode",
								"Play Optionで現在選択しているモードに追従します。",
								"Follows the mode currently selected in Play Options.",
								sidebarReadOnlyLabel(bmsirHispeedMode)),
						sidebarSettingRow(bmsirSpecificTab, "bmsirBaseScrollSpeed", "基本スクロール", "Base scroll",
								"100を等速として、モードごとの150 BPM基準速度を設定します。",
								"Set the per-mode 150 BPM reference speed, where 100 is 1.00x."),
						sidebarSettingRow(bmsirSpecificTab, "bmsirEquivalentGreenNumber", "換算緑数字", "Equivalent green number",
								"現在のHI-SPEED、SUD+、LIFTから換算します。入力すると基本スクロールを逆算します。",
								"Calculated from the current HI-SPEED, SUD+, and LIFT. Editing it updates base scroll.")),
				sidebarSettingCard(
						sidebarSettingRow(bmsirSpecificTab, "bmsirArenaTargetMode", "Arenaターゲット", "Arena target",
								"Arenaプレイ中のスコアグラフで比較対象にする相手を選びます。",
								"Choose the comparison target for the score graph during Arena play."),
						sidebarSettingRow(bmsirSpecificTab, "bmsirArenaGraphOrder", "Arenaグラフ順", "Arena graph order",
								"Arenaの参加者グラフを固定参加順または現在順位で並べます。",
								"Order Arena participant graphs by fixed entry order or current rank.")
				),
				sidebarSettingCard(
						sidebarSettingRow(bmsirSpecificTab, "bmsirCoverControlMode", "START＋6/7のレーンカバー操作", "START + 6/7 cover control",
								"START＋6/7でレーンカバーを動かすときの挙動を選びます。",
								"Choose how START + 6/7 changes the lane cover."),
						sidebarSettingRow(bmsirSpecificTab, "bmsirCoverChangeStep", "レーンカバー変化量", "Lane-cover change step",
								"START＋6/7を1回入力したときに変えるレーンカバー量です。",
								"Set the lane-cover amount changed by one START + 6/7 input."),
						sidebarSettingRow(bmsirSpecificTab, "bmsirCoverHispeedAutoAdjustEnabled", "レーンカバー操作時にHI-SPEEDを自動調整", "Auto-adjust HI-SPEED with cover",
								"専用レーンカバー操作時、現在BPMに合わせてHI-SPEEDを再計算します。",
								"Recalculate HI-SPEED at the current BPM during the dedicated cover operation.")
				),
				sidebarSettingCard(
						sidebarSettingRow(bmsirSpecificTab, "bmsirJudgeRankSortEnabled", "判定難易度順ソート", "Judge-rank sorting",
								"同じレベル内を譜面の判定難易度で並べ替えるソートを追加します。",
								"Add a sorter that orders charts of the same level by judgment difficulty."),
						sidebarSettingRow(bmsirSpecificTab, "bmsirJudgeRankSortSkinNoticeEnabled", "非対応スキンへ案内を表示", "Show unsupported-skin notice",
								"判定難易度順ソートの表示に未対応な選曲スキンで案内を表示します。",
								"Show a notice when the Music Select skin cannot display judge-rank sorting.")
				),
				sidebarNumpadCard(),
				sidebarSettingCard(
						sidebarSettingRow(bmsirSpecificTab, "bmsirExportVanillaScoreDb", "通常版スコアDBを書き出す", "Export vanilla score database",
								"MANIAC分離情報を除いた通常版互換のスコアデータベースを書き出します。元データは変更しません。",
								"Export a vanilla-compatible score database without MANIAC separation; the source data is unchanged.")
				)
		);
	}

	private VBox sidebarNumpadCard() {
		List<VBox> rows = new ArrayList<>();
		for (int number = 0; number <= 9; number++) {
			rows.add(sidebarSettingRow(bmsirSpecificTab, "bmsirNumpad" + number,
					"NUMPAD " + number, "NUMPAD " + number,
					"物理NUMPAD " + number + "を押したときに実行するショートカットを選びます。",
					"Choose the shortcut executed by physical NUMPAD " + number + "."));
		}
		rows.add(sidebarSettingRow(bmsirSpecificTab, "bmsirNumpadJudgeTimingStep",
				"判定タイミング変更量", "Judge-timing step",
				"NUMPADショートカットで判定タイミングを1回変更する量です。単位は ms です。",
				"Set the amount changed by one NUMPAD timing shortcut, in ms."));
		rows.add(sidebarSettingRow(bmsirSpecificTab, "bmsirJudgeTimingRestoreEnabled",
				"判定タイミングを自動復元", "Restore judgment timing",
				"一時変更した判定タイミングを、指定された復元操作で保存値へ戻せるようにします。",
				"Allow temporary judgment-timing changes to return to the saved value."));
		rows.add(sidebarSettingRow(bmsirSpecificTab, "bmsirInfoNotificationsEnabled",
				"INFO通知を表示", "Show INFO notifications",
				"NUMPAD操作などのINFOメッセージをプレイ画面へ表示します。",
				"Show INFO messages for NUMPAD actions and similar operations during play."));
		return sidebarSettingCard(rows.toArray(VBox[]::new));
	}

	private void initializeSidebarIr() {
		HBox service = new HBox(10,
				sidebarControl(irTab, "irname"),
				sidebarControl(irTab, "primarybutton"),
				sidebarControl(irTab, "irhome")
		);
		service.setAlignment(Pos.CENTER_LEFT);
		service.getStyleClass().add("sidebar-compound-control");
		Node serviceSelector = service.getChildren().get(0);
		if (serviceSelector instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(region, Priority.ALWAYS);
		}

		installSidebarPage(irTab,
				sidebarSettingCard(
						sidebarSettingRow(irTab, "IRサービス", "IR service",
								"使用するIRを選びます。「Primary」にしたIRは起動時の主要な難易度表や段位同期にも使われます。右のリンクでサービスを開けます。",
								"Choose the IR service. The Primary IR also supplies startup tables and course sync; use the link to open it.",
								service),
						sidebarSettingRow(irTab, "iruserid", "User ID", "User ID",
								"選択したIRへログインするユーザーIDです。",
								"Enter the user ID used to sign in to the selected IR."),
						sidebarSettingRow(irTab, "irpassword", "Password", "Password",
								"IRへログインするためのパスワードです。画面上では伏せて表示されます。",
								"Enter the IR login password; it remains masked on screen."),
						sidebarSettingRow(irTab, "irsend", "IR送信", "IR submission",
								"プレイ結果をIRへ送る条件を選びます。",
								"Choose when play results are submitted to the IR.")
				),
				sidebarSettingCard(
						sidebarSettingRow(irTab, "importrival", "IRからライバルスコア取得", "Import rival scores",
								"選択中のIRからライバル情報と比較スコアを取得します。",
								"Retrieve rival information and comparison scores from the selected IR."),
						sidebarSettingRow(irTab, "importscore", "IRからスコアをインポート", "Import scores from IR",
								"IR側に保存されている自分のスコアをローカルへ取り込みます。",
								"Import your scores stored by the IR into the local database.")
				),
				sidebarSettingCard(
						sidebarSettingRow(irTab, "bmsirArenaEnabled", "BMS-IR Arenaを有効にする", "Enable BMS-IR Arena",
								"起動後にArenaサーバーへ接続し、待機・対戦機能を利用できるようにします。",
								"Connect to the Arena server after startup and enable queue and match features."),
						sidebarSettingRow(irTab, "bmsirArenaServer", "Arena Server", "Arena Server",
								"BMS-IR Arenaへ接続するWebSocket URLです。通常は既定値を使用します。",
								"Set the WebSocket URL for BMS-IR Arena; normally keep the default.")
				)
		);
	}

	private void initializeSidebarTable() {
		StackPane tableEditor = sidebarMovable(tableTab, "tableEditorTabs");
		tableEditor.setMinHeight(430);
		installSidebarPage(tableTab,
				sidebarSettingCard(
						sidebarSettingRow(tableTab, "Table Name", "Table Name",
								"このローカルテーブルの名前を入力します。保存するとdefault.jsonへ反映されます。",
								"Enter the local table name; Save writes it to default.json.",
								sidebarCompound(sidebarControl(tableTab, "tableName"), sidebarControl(tableTab, "tableSaveButton")))
				),
				sidebarSettingCard(
						sidebarWorkspaceRow(tableTab, "コースとフォルダー", "Courses and folders",
								"Courseでは複数譜面のコースを、Folderではフォルダー条件を追加・削除・並べ替えます。",
								"Use Course for chart courses and Folder for folder rules; add, remove, and reorder entries here.",
								tableEditor)
				)
		);
	}

	private void initializeSidebarStream() {
		installSidebarPage(streamTab,
				sidebarSettingCard(
						sidebarSettingRow(streamTab, "enableRequest", "REQコマンドを有効にする", "Enable request command",
								"配信チャットなどから受け取るリクエストコマンドを有効にします。",
								"Enable the request command received from streaming chat or integrations."),
						sidebarSettingRow(streamTab, "notifyRequest", "リクエストを表示", "Show requests",
								"受け付けたリクエストを選曲画面へ表示します。",
								"Show accepted requests in Music Select."),
						sidebarSettingRow(streamTab, "maxRequestCount", "リクエスト最大保持数", "Maximum retained requests",
								"一覧に保持するリクエスト数の上限です。古い項目から押し出されます。",
								"Set the maximum retained request count; older entries are discarded first.")
				)
		);
	}

	private void initializeSidebarDiscord() {
		StackPane webhookTable = sidebarMovable(discordTab, "webhookURL");
		webhookTable.setMinHeight(250);
		VBox orderButtons = new VBox(8,
				sidebarControl(discordTab, "removeWebhookButton"),
				sidebarControl(discordTab, "moveWebhookUpButton"),
				sidebarControl(discordTab, "moveWebhookDownButton")
		);
		orderButtons.setMinWidth(120);
		HBox webhookWorkspace = new HBox(12, webhookTable, orderButtons);
		HBox.setHgrow(webhookTable, Priority.ALWAYS);

		installSidebarPage(discordTab,
				sidebarSettingCard(
						sidebarSettingRow(discordTab, "discordRichPresence", "Discord Rich Presence", "Discord Rich Presence",
								"Discordのプロフィールへ現在のプレイ状態や選曲情報を表示します。",
								"Show current play and selection status on the Discord profile."),
						sidebarSettingRow(discordTab, "webhookOption", "Discordへ送信するスコア内容", "Discord score payload",
								"Webhookへ送るスコア通知を無効、画像のみ、詳細埋め込みから選びます。",
								"Choose disabled, image-only, or rich-embed score notifications."),
						sidebarSettingRow(discordTab, "webhookName", "Webhook名", "Webhook name",
								"Discordへ投稿するときに表示する送信者名です。空欄ならWebhook側の既定値を使います。",
								"Set the sender name shown in Discord; blank uses the Webhook default."),
						sidebarSettingRow(discordTab, "webhookAvatar", "Webhookアイコン", "Webhook avatar",
								"投稿に使うアイコン画像のURLです。空欄ならWebhook側の既定値を使います。",
								"Set the icon-image URL for posts; blank uses the Webhook default.")
				),
				sidebarSettingCard(
						sidebarSettingRow(discordTab, "Webhook URLを追加", "Add Webhook URL",
								"送信先URLを入力して追加します。Webhook URLは画面外へ共有しないでください。",
								"Enter and add a destination URL. Do not share Webhook URLs outside this screen.",
								sidebarCompound(sidebarControl(discordTab, "url"), sidebarControl(discordTab, "addWebhookButton"))),
						sidebarWorkspaceRow(discordTab, "Webhook送信先", "Webhook destinations",
								"登録済みURLを選択し、削除または優先順の上下移動を行います。",
								"Select registered URLs to remove them or change their priority order.",
								webhookWorkspace)
				)
		);
	}

	private void initializeSidebarObs() {
		StackPane sceneMappings = sidebarMovable(obsTab, "listContainer");
		sceneMappings.setMinHeight(160);
		installSidebarPage(obsTab,
				sidebarSettingCard(
						sidebarSettingRow(obsTab, "obsWsEnabled", "OBS WebSocket制御", "OBS WebSocket control",
								"ゲーム状態に合わせたOBS録画・シーン切替を有効にします。OBS側でもWebSocketを有効にしてください。",
								"Enable OBS recording and scene control; WebSocket must also be enabled in OBS."),
						sidebarSettingRow(obsTab, "obsWsHost", "ホスト", "Host",
								"OBS WebSocketが動作しているPC名またはIPアドレスです。同じPCならlocalhostを使います。",
								"Enter the host or IP running OBS WebSocket; use localhost on the same computer."),
						sidebarSettingRow(obsTab, "obsWsPort", "ポート", "Port",
								"OBS WebSocketの待受ポートです。OBS 28以降の既定値は4455です。",
								"Enter the OBS WebSocket port; OBS 28 and later default to 4455."),
						sidebarSettingRow(obsTab, "obsWsPass", "パスワード", "Password",
								"OBS WebSocketで設定した認証パスワードです。画面上では伏せて表示されます。",
								"Enter the OBS WebSocket password; it remains masked on screen.")
				),
				sidebarSettingCard(
						sidebarSettingRow(obsTab, "obsWsRecMode", "録画連動モード", "Recording mode",
								"ゲーム状態のどの範囲でOBS録画を開始・停止するかを選びます。",
								"Choose which game-state interval starts and stops OBS recording."),
						sidebarSettingRow(obsTab, "obsWsRecStopWait", "録画停止待機時間", "Recording stop delay",
								"リザルト後などに録画停止を遅らせる時間です。単位は ms です。",
								"Set the delay before recording stops after results, in ms."),
						sidebarSettingRow(obsTab, "obsWsConnectButton", "OBSへ接続", "Connect to OBS",
								"入力した接続先を使ってOBSへ接続し、利用可能なシーン一覧を取得します。",
								"Connect using the entered details and retrieve the available OBS scenes.")
				),
				sidebarSettingCard(
						sidebarWorkspaceRow(obsTab, "シーン割り当て", "Scene mappings",
								"接続後、選曲・決定・プレイ・リザルトなど各ゲーム状態へOBSシーンを割り当てます。",
								"After connecting, assign OBS scenes to selection, decide, play, result, and other states.",
								sceneMappings)
				)
		);
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
		return sidebarSettingRow(optionTab, jaTitle, enTitle, jaDescription, enDescription, editorNode);
	}

	private VBox sidebarSettingRow(Tab tab, String jaTitle, String enTitle,
			String jaDescription, String enDescription, Node editorNode) {
		String title = uiText(jaTitle, enTitle);
		String description = uiText(jaDescription, enDescription);
		Label titleLabel = new Label(title);
		titleLabel.setWrapText(true);
		titleLabel.setAlignment(Pos.CENTER_LEFT);
		titleLabel.setMaxWidth(Double.MAX_VALUE);
		titleLabel.getStyleClass().add("sidebar-setting-title");

		HBox editor = new HBox(editorNode);
		editor.setAlignment(Pos.CENTER_LEFT);
		editor.setMinWidth(SIDEBAR_EDITOR_COLUMN_WIDTH);
		editor.setPrefWidth(SIDEBAR_EDITOR_COLUMN_WIDTH);
		editor.setMaxWidth(SIDEBAR_EDITOR_COLUMN_WIDTH);
		editor.getStyleClass().add("sidebar-setting-editor");
		if (editorNode instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(region, Priority.ALWAYS);
		}

		ColumnConstraints titleColumn = new ColumnConstraints();
		titleColumn.setHgrow(Priority.ALWAYS);
		ColumnConstraints editorColumn = new ColumnConstraints(
				SIDEBAR_EDITOR_COLUMN_WIDTH,
				SIDEBAR_EDITOR_COLUMN_WIDTH,
				SIDEBAR_EDITOR_COLUMN_WIDTH
		);
		GridPane main = new GridPane();
		main.setHgap(24);
		main.getColumnConstraints().setAll(titleColumn, editorColumn);
		main.add(titleLabel, 0, 0);
		main.add(editor, 1, 0);
		main.setMaxWidth(Double.MAX_VALUE);
		main.getStyleClass().add("sidebar-setting-main");

		Label descriptionLabel = new Label(description);
		descriptionLabel.setWrapText(true);
		descriptionLabel.setMaxWidth(Double.MAX_VALUE);
		descriptionLabel.getStyleClass().add("sidebar-setting-row-description");

		VBox row = new VBox(8, main, descriptionLabel);
		row.getStyleClass().add("sidebar-setting-row");
		row.setAccessibleText(title + ". " + description);
		Control firstControl = firstControl(editorNode);
		if (firstControl != null) {
			titleLabel.setLabelFor(firstControl);
			firstControl.setAccessibleText(title + ". " + description);
		}
		HelpGraphic graphic = Optional.ofNullable(tabContextHelp.get(tab))
				.map(ContextHelp::graphic)
				.orElse(HelpGraphic.OTHER);
		installSidebarRowHelp(tab, row, new ContextHelp(title, description, graphic));
		return row;
	}

	private VBox sidebarSettingRow(Tab tab, String id, String jaTitle, String enTitle,
			String jaDescription, String enDescription) {
		return sidebarSettingRow(tab, jaTitle, enTitle, jaDescription, enDescription, sidebarControl(tab, id));
	}

	private VBox sidebarWorkspaceRow(Tab tab, String jaTitle, String enTitle,
			String jaDescription, String enDescription, Node workspace) {
		String title = uiText(jaTitle, enTitle);
		String description = uiText(jaDescription, enDescription);
		Label titleLabel = new Label(title);
		titleLabel.setWrapText(true);
		titleLabel.getStyleClass().add("sidebar-setting-title");
		Label descriptionLabel = new Label(description);
		descriptionLabel.setWrapText(true);
		descriptionLabel.getStyleClass().add("sidebar-setting-row-description");
		if (workspace instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
			VBox.setVgrow(region, Priority.ALWAYS);
		}
		VBox row = new VBox(8, titleLabel, descriptionLabel, workspace);
		row.getStyleClass().addAll("sidebar-setting-row", "sidebar-workspace-row");
		row.setAccessibleText(title + ". " + description);
		Control firstControl = firstControl(workspace);
		if (firstControl != null) {
			titleLabel.setLabelFor(firstControl);
			firstControl.setAccessibleText(title + ". " + description);
		}
		HelpGraphic graphic = Optional.ofNullable(tabContextHelp.get(tab))
				.map(ContextHelp::graphic)
				.orElse(HelpGraphic.OTHER);
		installSidebarRowHelp(tab, row, new ContextHelp(title, description, graphic));
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
		toggle.setOnAction(event -> {
			EventHandler<ActionEvent> sourceAction = source.getOnAction();
			if (sourceAction != null) {
				sourceAction.handle(new ActionEvent(source, source));
			}
		});
		Label state = new Label();
		state.getStyleClass().add("sidebar-switch-state");
		state.textProperty().bind(Bindings.when(toggle.selectedProperty()).then("ON").otherwise("OFF"));
		HBox result = new HBox(8, toggle, state);
		result.setAlignment(SIDEBAR_STANDALONE_TOGGLE_ALIGNMENT);
		result.getStyleClass().add("sidebar-toggle-control");
		return result;
	}

	private TextInputControl sidebarTextInput(TextInputControl source) {
		TextInputControl mirror = source instanceof PasswordField ? new PasswordField() : new TextField();
		mirror.setPromptText(source.getPromptText());
		mirror.setEditable(source.isEditable());
		mirror.textProperty().bindBidirectional(source.textProperty());
		mirror.disableProperty().bind(source.disableProperty());
		mirror.getStyleClass().add("sidebar-setting-control");
		return mirror;
	}

	private ButtonBase sidebarButton(ButtonBase source) {
		ButtonBase mirror = source instanceof Hyperlink ? new Hyperlink() : new Button();
		mirror.textProperty().bind(source.textProperty());
		mirror.disableProperty().bind(source.disableProperty());
		mirror.setMnemonicParsing(source.isMnemonicParsing());
		mirror.setTooltip(source.getTooltip());
		mirror.setOnAction(event -> source.fire());
		mirror.getStyleClass().add("sidebar-setting-control");
		return mirror;
	}

	private Label sidebarReadOnlyLabel(Label source) {
		Label mirror = new Label();
		mirror.textProperty().bind(source.textProperty());
		mirror.disableProperty().bind(source.disableProperty());
		mirror.getStyleClass().add("sidebar-setting-control");
		return mirror;
	}

	private Node sidebarControl(Tab tab, String id) {
		Node source = requireNode(tab, id);
		if (source instanceof CheckBox checkBox) {
			return sidebarToggle(checkBox);
		}
		if (source instanceof ComboBox<?> comboBox) {
			return sidebarComboUnchecked(comboBox);
		}
		if (source instanceof Spinner<?> spinner) {
			return sidebarSpinnerUnchecked(spinner);
		}
		if (source instanceof Slider slider) {
			return sidebarSlider(slider);
		}
		if (source instanceof TextInputControl textInput) {
			return sidebarTextInput(textInput);
		}
		if (source instanceof ButtonBase button) {
			return sidebarButton(button);
		}
		throw new IllegalArgumentException("Unsupported Sidebar control #" + id + ": " + source.getClass().getName());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private ComboBox<?> sidebarComboUnchecked(ComboBox<?> source) {
		return sidebarCombo((ComboBox) source);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Spinner<?> sidebarSpinnerUnchecked(Spinner<?> source) {
		return sidebarSpinner((Spinner) source);
	}

	private HBox sidebarCompound(Node... nodes) {
		HBox result = new HBox(10, nodes);
		result.setAlignment(Pos.CENTER_LEFT);
		result.getStyleClass().add("sidebar-compound-control");
		for (Node node : nodes) {
			if (node instanceof Region region) {
				if (node instanceof ButtonBase) {
					region.setMaxWidth(Region.USE_PREF_SIZE);
				} else {
					region.setMaxWidth(Double.MAX_VALUE);
					HBox.setHgrow(region, Priority.ALWAYS);
				}
			}
		}
		return result;
	}

	private VBox sidebarLabeledToggle(String labelText, CheckBox source) {
		Label label = new Label(labelText);
		label.getStyleClass().add("sidebar-compact-label");
		VBox result = new VBox(3, label, sidebarToggle(source));
		result.getStyleClass().add("sidebar-compact-input");
		return result;
	}

	private Node requireNode(Tab tab, String id) {
		for (Object controller : sidebarSourceControllers(tab)) {
			Node node = injectedFxmlNode(controller, id);
			if (node != null) {
				return node;
			}
		}
		throw new IllegalStateException("Missing injected Sidebar source #" + id + " in tab " + tab.getText());
	}

	private List<Object> sidebarSourceControllers(Tab tab) {
		if (tab == videoTab) return List.of(videoController);
		if (tab == audioTab) return List.of(audioController);
		if (tab == inputTab) return List.of(inputController);
		if (tab == resourceTab) return List.of(resourceController);
		if (tab == musicselectTab) return List.of(musicselectController);
		if (tab == skinTab) return List.of(skinController, this);
		if (tab == irTab) return List.of(irController);
		if (tab == tableTab) return List.of(tableController);
		if (tab == streamTab) return List.of(streamController);
		if (tab == discordTab) return List.of(discordController);
		if (tab == obsTab) return List.of(obsController);
		return List.of(this);
	}

	private static Node injectedFxmlNode(Object controller, String id) {
		for (Class<?> type = controller.getClass(); type != null; type = type.getSuperclass()) {
			try {
				Field field = type.getDeclaredField(id);
				if (!field.isAnnotationPresent(FXML.class)) {
					return null;
				}
				field.setAccessible(true);
				Object value = field.get(controller);
				return value instanceof Node node ? node : null;
			} catch (NoSuchFieldException ignored) {
				// Continue through the controller hierarchy.
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Unable to read injected FXML source #" + id, e);
			}
		}
		return null;
	}

	private StackPane sidebarMovable(Tab tab, String id) {
		Node source = requireNode(tab, id);
		if (!(source.getParent() instanceof Pane parent)) {
			throw new IllegalStateException("Sidebar source #" + id + " is not inside a Pane");
		}
		StackPane host = new StackPane();
		host.getStyleClass().add("sidebar-workspace");
		host.setMaxWidth(Double.MAX_VALUE);
		host.setMinHeight(120);
		sidebarNodePlacements.add(new SidebarNodePlacement(
				source, parent, parent.getChildren().indexOf(source), host
		));
		return host;
	}

	private StackPane sidebarMovableParent(Tab tab, String id) {
		Node source = requireNode(tab, id);
		if (!(source.getParent() instanceof Pane sourceParent)
				|| !(sourceParent.getParent() instanceof Pane parent)) {
			throw new IllegalStateException("Sidebar source parent for #" + id + " is not movable");
		}
		StackPane host = new StackPane();
		host.getStyleClass().add("sidebar-workspace");
		host.setMaxWidth(Double.MAX_VALUE);
		host.setMinHeight(130);
		sidebarNodePlacements.add(new SidebarNodePlacement(
				sourceParent, parent, parent.getChildren().indexOf(sourceParent), host
		));
		return host;
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

	private void installSidebarRowHelp(Tab tab, VBox row, ContextHelp help) {
		sidebarSearchIndex.add(tab, help.title(), help.description());
		row.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> showContextHelp(help));
		row.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
			if (!containsFocusedControl(row)) {
				showTabContextHelp(tab);
			}
		});
		forEachControl(row, control -> control.focusedProperty().addListener(
				(observable, oldValue, focused) -> {
					if (focused) {
						showContextHelp(help);
					} else {
						Platform.runLater(() -> {
							if (!row.isHover() && !containsFocusedControl(row)) {
								showTabContextHelp(tab);
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
		ContextHelp help = new ContextHelp(
				englishUi ? enTitle : jaTitle,
				englishUi ? enDescription : jaDescription,
				graphic
		);
		tabContextHelp.put(tab, help);
		sidebarSearchIndex.add(tab, tab.getText(), help.title(), help.description());
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
		enableBmsirBodyDownload.setSelected(config.isEnableBmsirBodyDownload());
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
		bmsirTableLevelDisplayEnabled.setSelected(
				player.isBmsirTableLevelDisplayEnabled()
		);
		bmsirHideMissingTableSongs.setSelected(
				player.isBmsirHideMissingTableSongs()
		);
		resourceController.updatePlayer(player);
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
		bmsirLr2HispeedFixEnabled.setSelected(
				player.isBmsirLr2HispeedFixEnabled()
		);
		bmsirPseudoFhsEnabled.setSelected(player.isBmsirPseudoFhsEnabled());
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
		lntype.getSelectionModel().select(player.getLnmode());

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
		config.setEnableBmsirBodyDownload(enableBmsirBodyDownload.isSelected());
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
		player.setBmsirTableLevelDisplayEnabled(
				bmsirTableLevelDisplayEnabled.isSelected()
		);
		player.setBmsirHideMissingTableSongs(
				bmsirHideMissingTableSongs.isSelected()
		);
		resourceController.commitPlayer(player);
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
		player.setBmsirLr2HispeedFixEnabled(
				bmsirLr2HispeedFixEnabled.isSelected()
		);
		player.setBmsirPseudoFhsEnabled(bmsirPseudoFhsEnabled.isSelected());
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
		player.setLnmode(lntype.getValue());
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
			conf.setBmsirBaseScrollSpeed(getValue(bmsirBaseScrollSpeed));
		}
		pc = playconfig.getValue();
		if (pc == null) {
			return;
		}
		PlayConfig conf = player.getPlayConfig(Mode.valueOf(pc.name())).getPlayconfig();
		updatingBmsirHispeedFields = true;
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
		bmsirHispeedMode.setText(pc.toString());
		bmsirBaseScrollSpeed.getValueFactory().setValue(
				conf.getBmsirBaseScrollSpeed()
		);
		updatingBmsirHispeedFields = false;
		updateBmsirEquivalentGreen();
	}

	private void updateBmsirEquivalentGreen() {
		if (updatingBmsirHispeedFields
				|| bmsirBaseScrollSpeed.getValue() == null
				|| hispeed.getValue() == null) {
			return;
		}
		updatingBmsirHispeedFields = true;
		bmsirEquivalentGreenNumber.getValueFactory().setValue(
				BMSIRHispeed.equivalentGreen(bmsirHispeedCalculatorConfig())
		);
		updatingBmsirHispeedFields = false;
	}

	private void updateBmsirBaseFromGreen() {
		if (updatingBmsirHispeedFields
				|| bmsirEquivalentGreenNumber.getValue() == null) {
			return;
		}
		updatingBmsirHispeedFields = true;
		PlayConfig calculator = bmsirHispeedCalculatorConfig();
		int base = BMSIRHispeed.baseScrollSpeedForGreen(
				calculator,
				bmsirEquivalentGreenNumber.getValue()
		);
		bmsirBaseScrollSpeed.getValueFactory().setValue(base);
		calculator.setBmsirBaseScrollSpeed(base);
		bmsirEquivalentGreenNumber.getValueFactory().setValue(
				BMSIRHispeed.equivalentGreen(calculator)
		);
		updatingBmsirHispeedFields = false;
	}

	private PlayConfig bmsirHispeedCalculatorConfig() {
		PlayConfig calculator = new PlayConfig();
		calculator.setHispeed(hispeed.getValue().floatValue());
		calculator.setBmsirBaseScrollSpeed(bmsirBaseScrollSpeed.getValue());
		calculator.setEnablelanecover(enableLanecover.isSelected());
		calculator.setLanecover(lanecover.getValue() / 1000f);
		calculator.setEnablelift(enableLift.isSelected());
		calculator.setLift(lift.getValue() / 1000f);
		return calculator;
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
