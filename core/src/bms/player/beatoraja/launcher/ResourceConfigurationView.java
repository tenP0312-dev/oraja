package bms.player.beatoraja.launcher;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.TableDataAccessor;
import bms.player.beatoraja.arena.bmsir.BMSIRPhysicalFolderFilter;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import javafx.util.Pair;

public class ResourceConfigurationView implements Initializable {

	@FXML
	private ListView<String> bmsroot;
	@FXML
	private EditableTableView<TableInfo> tableurl;
	@FXML
	private CheckBox updatesong;
	@FXML
	private CheckBox scanSongArchives;
	@FXML
	private Button addSongPathButton;
	@FXML
	private Button downloadDirectoryButton;
	@FXML
	private Button workDirectoryButton;
	@FXML
	private Button chooseTablesButton;
	@FXML
	private Button addTableUrlButton;
	@FXML
	private Button updateAllTablesButton;
	@FXML
	private Button updateDatabaseButton;
	@FXML
	private Button rebuildDatabaseButton;
	@FXML
	private CheckBox bmsirPhysicalFolderFilterEnabled;
	@FXML
	private VBox bmsirPhysicalFolderFilterOptions;
	@FXML
	private VBox bmsirVisiblePhysicalFolders;
	@FXML
	private Label bmsirPhysicalFolderEmpty;

	private Config config;
	private ResourceBundle resources;
	private final LinkedHashSet<String> availableTableUrls = new LinkedHashSet<>();
	private final LinkedHashMap<String, CheckBox> bmsirPhysicalFolderChecks =
			new LinkedHashMap<>();
	private final LinkedHashSet<String> retainedVisiblePhysicalFolders =
			new LinkedHashSet<>();
	private PlayConfigurationView main;
	private String downloadDirectory;
	private String workDirectory;
	private boolean englishUi;

	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {
		resources = arg1;
		englishUi = !"ja".equalsIgnoreCase(arg1.getLocale().getLanguage());
		bmsirPhysicalFolderFilterOptions.visibleProperty().bind(
				bmsirPhysicalFolderFilterEnabled.selectedProperty()
		);
		bmsirPhysicalFolderFilterOptions.managedProperty().bind(
				bmsirPhysicalFolderFilterEnabled.selectedProperty()
		);
		bmsroot.setCellFactory(new Callback<>() {
			@Override
			public ListCell<String> call(ListView<String> param) {
				return new ListCell<>() {
					private final MenuItem updateItem = menuItem("BMS_PATH_UPDATE_SELECTED", () -> updateSongPath(getItem()));
					private final MenuItem openItem = menuItem("BMS_PATH_OPEN", () -> openSongPath(getItem()));
					private final MenuItem copyItem = menuItem("BMS_PATH_COPY", () -> copySongPath(getItem()));
					private final MenuItem removeItem = menuItem("BMS_PATH_REMOVE", () -> removeSongPath(getItem()));
					private final ContextMenu contextMenu = new ContextMenu(
							updateItem, new SeparatorMenuItem(), openItem, copyItem,
							new SeparatorMenuItem(), removeItem);

					{
						setOnContextMenuRequested(event -> {
							if (!isEmpty()) {
								bmsroot.getSelectionModel().clearAndSelect(getIndex());
							}
						});
					}

					@Override
					protected void updateItem(String item, boolean empty) {
						super.updateItem(item, empty);
						if (item != null && !empty) {
							StringBuilder label = new StringBuilder(item);
							if (samePath(item, downloadDirectory)) {
								label.append(' ').append(resources.getString("BMS_PATH_DOWNLOAD_DIRECTORY_SUFFIX"));
							}
							if (samePath(item, workDirectory)) {
								label.append(' ').append(resources.getString("BMS_PATH_WORK_DIRECTORY_SUFFIX"));
							}
							setText(label.toString());
							setStyle("");
							setContextMenu(contextMenu);
						} else {
							setText("");
							setStyle("");
							setContextMenu(null);
						}
					}
				};
			}
		});
	}
	
	void init(PlayConfigurationView main) {
		this.main = main;

		TableColumn<TableInfo,String> nameColumn = new TableColumn<>(resources.getString("TABLE_NAME_STATUS"));
        nameColumn.setCellValueFactory((p) -> p.getValue().nameStatusProperty());
        nameColumn.setSortable(false);
		nameColumn.setPrefWidth(210);

		TableColumn<TableInfo,String> commentColumn = new TableColumn<>(resources.getString("TABLE_COMMENT"));
		commentColumn.setCellValueFactory((p) -> p.getValue().commentProperty());
		commentColumn.setSortable(false);
		commentColumn.setPrefWidth(300);

		TableColumn<TableInfo,String> urlColumn = new TableColumn<>(resources.getString("TABLE_URL_COLUMN"));
		urlColumn.setCellValueFactory((p) -> p.getValue().urlProperty());
		urlColumn.setSortable(false);
		urlColumn.setPrefWidth(320);

		tableurl.getColumns().setAll(nameColumn, commentColumn, urlColumn);
		tableurl.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tableurl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		tableurl.setRowFactory(view -> new TableRow<>() {
			private final MenuItem updateItem = menuItem("TABLE_UPDATE_SELECTED", () -> updateTable(getItem()));
			private final MenuItem editUrlItem = menuItem("TABLE_EDIT_URL", () -> editTableUrl(getItem()));
			private final MenuItem moveUpItem = menuItem("TABLE_MOVE_UP", () -> moveTable(getItem(), -1));
			private final MenuItem moveDownItem = menuItem("TABLE_MOVE_DOWN", () -> moveTable(getItem(), 1));
			private final MenuItem removeItem = menuItem("TABLE_REMOVE", () -> removeTable(getItem()));
			private final ContextMenu contextMenu = new ContextMenu(
					updateItem, editUrlItem, new SeparatorMenuItem(), moveUpItem, moveDownItem,
					new SeparatorMenuItem(), removeItem);

			{
				setOnContextMenuRequested(event -> {
					if (!isEmpty()) {
						tableurl.getSelectionModel().clearAndSelect(getIndex());
					}
				});
			}

			@Override
			protected void updateItem(TableInfo item, boolean empty) {
				super.updateItem(item, empty);
				setContextMenu(empty || item == null ? null : contextMenu);
			}
		});

		// Ctrl+C listener
		tableurl.setOnKeyPressed(e -> {
			if (e.isControlDown() && e.getCode().equals(KeyCode.C)) {
				Clipboard clipboard = Clipboard.getSystemClipboard();
				ClipboardContent content = new ClipboardContent();
				String selection = tableurl.getSelectionModel().getSelectedItems().stream()
						.map(TableInfo::getUrl)
						.collect(Collectors.joining("\n"));
				content.putString(selection);
				clipboard.setContent(content);
			}
		});
		bmsroot.getItems().addListener(
				(ListChangeListener<String>) change -> {
					rememberVisiblePhysicalFolderChecks();
					rebuildPhysicalFolderChecks();
				}
		);
	}

	void updatePlayer(PlayerConfig player) {
		bmsirPhysicalFolderFilterEnabled.setSelected(
				player.isBmsirPhysicalFolderFilterEnabled()
		);
		retainedVisiblePhysicalFolders.clear();
		retainedVisiblePhysicalFolders.addAll(
				Arrays.asList(player.getBmsirVisiblePhysicalFolderPaths())
		);
		rebuildPhysicalFolderChecks();
	}

	void commitPlayer(PlayerConfig player) {
		player.setBmsirPhysicalFolderFilterEnabled(
				bmsirPhysicalFolderFilterEnabled.isSelected()
		);
		player.setBmsirVisiblePhysicalFolderPaths(
				bmsirPhysicalFolderChecks.entrySet().stream()
						.filter(entry -> entry.getValue().isSelected())
						.map(Map.Entry::getKey)
						.toArray(String[]::new)
		);
	}

	private void rememberVisiblePhysicalFolderChecks() {
		for (Map.Entry<String, CheckBox> entry : bmsirPhysicalFolderChecks.entrySet()) {
			String path = entry.getKey();
			retainedVisiblePhysicalFolders.removeIf(
					stored -> BMSIRPhysicalFolderFilter.samePath(stored, path)
			);
			if (entry.getValue().isSelected()) {
				retainedVisiblePhysicalFolders.add(path);
			}
		}
	}

	private void rebuildPhysicalFolderChecks() {
		bmsirPhysicalFolderChecks.clear();
		bmsirVisiblePhysicalFolders.getChildren().clear();
		for (String path : bmsroot.getItems()) {
			if (path == null || path.isBlank()) {
				continue;
			}
			CheckBox check = new CheckBox(path);
			check.setMnemonicParsing(false);
			check.setWrapText(true);
			check.setMaxWidth(Double.MAX_VALUE);
			check.setTooltip(new Tooltip(path));
			check.setAccessibleText(
					(englishUi ? "Show physical folder: " : "表示する物理フォルダー: ")
							+ path
			);
			check.setSelected(BMSIRPhysicalFolderFilter.containsPath(
					retainedVisiblePhysicalFolders,
					path
			));
			bmsirPhysicalFolderChecks.put(path, check);
			bmsirVisiblePhysicalFolders.getChildren().add(check);
		}
		boolean empty = bmsirPhysicalFolderChecks.isEmpty();
		bmsirPhysicalFolderEmpty.setVisible(empty);
		bmsirPhysicalFolderEmpty.setManaged(empty);
	}

    public void update(Config config) {
    	this.config = config;
		this.downloadDirectory = config.getDownloadDirectory();
		this.workDirectory = config.getWorkDirectory();
		bmsroot.getItems().setAll(config.getBmsroot());
		updatesong.setSelected(config.isUpdatesong());
		scanSongArchives.setSelected(config.isScanSongArchives());

		availableTableUrls.clear();
		availableTableUrls.addAll(Arrays.asList(Config.AVAILABLE_TABLEURL));
		if (config.getAvailableURL() != null) {
			availableTableUrls.addAll(Arrays.asList(config.getAvailableURL()));
		}
		availableTableUrls.removeAll(Arrays.asList(config.getTableURL()));
		config.setAvailableURL(availableTableUrls.toArray(new String[0]));
		TableInfo.populateList(tableurl.getItems(), config.getTableURL());
	}

	public void commit() {
		config.setBmsroot(bmsroot.getItems().toArray(new String[0]));
		config.setUpdatesong(updatesong.isSelected());
		config.setScanSongArchives(scanSongArchives.isSelected());
		config.setTableURL(TableInfo.toUrlArray(tableurl.getItems()));
		config.setAvailableURL(availableTableUrls.toArray(new String[0]));
		config.setDownloadDirectory(downloadDirectory);
		config.setWorkDirectory(workDirectory);
	}

    @FXML
	public void refreshLocalTableInfo() {
		ResourceBundle bundle = ResourceBundle.getBundle("resources.UIResources");
		// Functions in ResourceConfigurationView are not run on the JavaFX Application Thread and so this little
		// workaround has to be performed here to allow the progress bar to function as expected.
		final Stage[] loadingBarStage = new Stage[1];
		Runnable progressRunnable = () -> {
			// JavaFX UI code must be run inside a Platform run context
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					loadingBarStage[0] = new Stage();
					loadingBarStage[0].setResizable(false);
					// This modality freezes the launcher/primary stage
					loadingBarStage[0].initModality(Modality.APPLICATION_MODAL);
					loadingBarStage[0].setTitle(bundle.getString("PROGRESS_TABLE_TITLE"));
					// This prevents users from seeing typical windowing system buttons
					loadingBarStage[0].initStyle(StageStyle.UTILITY);

					ProgressBar progressBar = new ProgressBar();
					progressBar.setPrefWidth(300);

					Label messageLabel = new Label(bundle.getString("PROGRESS_TABLE_LABEL"));

					VBox root = new VBox(10);
					root.setStyle("-fx-padding: 20; -fx-alignment: center;");
					root.getChildren().addAll(messageLabel, progressBar);

					Scene scene = new Scene(root);
					loadingBarStage[0].setScene(scene);

					// Prevents closing. This has the side effect of preventing windowing system close requests but
					// the application can still be force killed by the user if necessary
					loadingBarStage[0].setOnCloseRequest(Event::consume);
					loadingBarStage[0].show();
				}
			});
		};

		Runnable loadTableRunnable = () -> {
			String[] urls = TableInfo.toUrlArray(tableurl.getItems());
			TableDataAccessor tda = new TableDataAccessor(config.getTablepath());
			HashMap<String,String> urlToTableNameMap = tda.readLocalTableNames(urls);
			for (TableInfo tableInfo : tableurl.getItems()) {
				String tableName = (urlToTableNameMap == null) ? null : urlToTableNameMap.get(tableInfo.getUrl());
				tableInfo.setNameStatus((tableName == null) ? "not loaded" : tableName);
			}

			// Once again, JavaFX UI code must be run inside a Platform context. Hide progress bar and resume
			// normal launcher behaviour
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					loadingBarStage[0].hide();
				}
			});
		};

		new Thread(progressRunnable).start();
		new Thread(loadTableRunnable).start();
	}

    @FXML
	public void loadAllTables() {
		commit();
		Stage loadingBarStage = createTableProgressStage();
		updateAllTablesButton.setDisable(true);
		loadingBarStage.show();

		String[] configuredUrls = config.getTableURL().clone();
		List<TableInfo> visibleTables = new ArrayList<>(tableurl.getItems());
		Thread worker = new Thread(() -> {
			TableDataAccessor accessor = new TableDataAccessor(config.getTablepath());
			TableDataAccessor.TableUpdateResult result = accessor.replaceAllTableData(configuredUrls);
			String[] visibleUrls = visibleTables.stream().map(TableInfo::getUrl).toArray(String[]::new);
			HashMap<String, String> names = accessor.readLocalTableNames(visibleUrls);
			Platform.runLater(() -> {
				updateVisibleTableNames(visibleTables, names);
				loadingBarStage.hide();
				updateAllTablesButton.setDisable(false);
				showAllTableUpdateResult(result);
			});
		}, "Difficulty Table Update All");
		worker.start();
	}

    @FXML
	public void loadSelectedTables() {
		commit();
		ResourceBundle bundle = ResourceBundle.getBundle("resources.UIResources");
		try {
			Files.createDirectories(Paths.get(config.getTablepath()));
		} catch (IOException e) {
		}

		final Stage[] loadingBarStage = new Stage[1];
		Runnable progressRunnable = () -> {
			// JavaFX UI code must be run inside a Platform run context
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					loadingBarStage[0] = new Stage();
					loadingBarStage[0].setResizable(false);
					// This modality freezes the launcher/primary stage
					loadingBarStage[0].initModality(Modality.APPLICATION_MODAL);
					loadingBarStage[0].setTitle(bundle.getString("PROGRESS_TABLE_TITLE"));
					// This prevents users from seeing typical windowing system buttons
					loadingBarStage[0].initStyle(StageStyle.UTILITY);

					ProgressBar progressBar = new ProgressBar();
					progressBar.setPrefWidth(300);

					Label messageLabel = new Label(bundle.getString("PROGRESS_TABLE_LABEL"));

					VBox root = new VBox(10);
					root.setStyle("-fx-padding: 20; -fx-alignment: center;");
					root.getChildren().addAll(messageLabel, progressBar);

					Scene scene = new Scene(root);
					loadingBarStage[0].setScene(scene);

					// Prevents closing. This has the side effect of preventing windowing system close requests but
					// the application can still be force killed by the user if necessary
					loadingBarStage[0].setOnCloseRequest(Event::consume);
					loadingBarStage[0].show();
				}
			});
		};

		Runnable loadTableRunnable = () -> {
			TableDataAccessor tda = new TableDataAccessor(config.getTablepath());
			String[] urls = TableInfo.toUrlArray(tableurl.getSelectionModel().getSelectedItems());
			tda.updateTableData(urls);
			refreshLocalTableInfo();

			// Once again, JavaFX UI code must be run inside a Platform context. Hide progress bar and resume
			// normal launcher behaviour
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					loadingBarStage[0].hide();
				}
			});
		};

		new Thread(progressRunnable).start();
		new Thread(loadTableRunnable).start();
	}

    @FXML
	public void loadNewTables() {
		commit();
		ResourceBundle bundle = ResourceBundle.getBundle("resources.UIResources");
		try {
			Files.createDirectories(Paths.get(config.getTablepath()));
		} catch (IOException e) {
		}

		final Stage[] loadingBarStage = new Stage[1];
		Runnable progressRunnable = () -> {
			// JavaFX UI code must be run inside a Platform run context
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					loadingBarStage[0] = new Stage();
					loadingBarStage[0].setResizable(false);
					// This modality freezes the launcher/primary stage
					loadingBarStage[0].initModality(Modality.APPLICATION_MODAL);
					loadingBarStage[0].setTitle(bundle.getString("PROGRESS_TABLE_TITLE"));
					// This prevents users from seeing typical windowing system buttons
					loadingBarStage[0].initStyle(StageStyle.UTILITY);

					ProgressBar progressBar = new ProgressBar();
					progressBar.setPrefWidth(300);

					Label messageLabel = new Label(bundle.getString("PROGRESS_TABLE_LABEL"));

					VBox root = new VBox(10);
					root.setStyle("-fx-padding: 20; -fx-alignment: center;");
					root.getChildren().addAll(messageLabel, progressBar);

					Scene scene = new Scene(root);
					loadingBarStage[0].setScene(scene);

					// Prevents closing. This has the side effect of preventing windowing system close requests but
					// the application can still be force killed by the user if necessary
					loadingBarStage[0].setOnCloseRequest(Event::consume);
					loadingBarStage[0].show();
				}
			});
		};

		Runnable loadTableRunnable = () -> {
			TableDataAccessor tda = new TableDataAccessor(config.getTablepath());
			tda.loadNewTableData(config.getTableURL());
			refreshLocalTableInfo();

			// Once again, JavaFX UI code must be run inside a Platform context. Hide progress bar and resume
			// normal launcher behaviour
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					loadingBarStage[0].hide();
				}
			});
		};

		new Thread(progressRunnable).start();
		new Thread(loadTableRunnable).start();
	}


    @FXML
	public void addSongPath() {
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle("楽曲のルートフォルダを選択してください");
		File f = chooser.showDialog(null);
		if (f != null) {
			final String defaultPath = new File(".").getAbsoluteFile().getParent() + File.separatorChar;;
			String targetPath = f.getAbsolutePath();
			if(targetPath.startsWith(defaultPath)) {
				targetPath = f.getAbsolutePath().substring(defaultPath.length());
			}
			boolean unique = true;
			for (String path : bmsroot.getItems()) {
				if (path.equals(targetPath) || targetPath.startsWith(path + File.separatorChar)) {
					unique = false;
					break;
				}
			}
			if (unique) {
				bmsroot.getItems().add(targetPath);
				main.loadBMSPath(targetPath);
			}
		}
	}

    @FXML
	public void onSongPathDragOver(DragEvent ev) {
		Dragboard db = ev.getDragboard();
		if (db.hasFiles()) {
			ev.acceptTransferModes(TransferMode.COPY_OR_MOVE);
		}
		ev.consume();
	}

    @FXML
	public void songPathDragDropped(final DragEvent ev) {
		Dragboard db = ev.getDragboard();
		if (db.hasFiles()) {
			for (File f : db.getFiles()) {
				if (f.isDirectory()) {
					final String defaultPath = new File(".").getAbsoluteFile().getParent() + File.separatorChar;;
					String targetPath = f.getAbsolutePath();
					if(targetPath.startsWith(defaultPath)) {
						targetPath = f.getAbsolutePath().substring(defaultPath.length());
					}
					boolean unique = true;
					for (String path : bmsroot.getItems()) {
						if (path.equals(targetPath) || targetPath.startsWith(path + File.separatorChar)) {
							unique = false;
							break;
						}
					}
					if (unique) {
						bmsroot.getItems().add(targetPath);
						main.loadBMSPath(targetPath);
					}
				}
			}
		}
	}

    @FXML
	public void removeSongPath() {
		removeSongPath(bmsroot.getSelectionModel().getSelectedItem());
	}

	@FXML
	public void markAsDownloadDirectory() {
		String selected = bmsroot.getSelectionModel().getSelectedItem();
		if (selected == null) {
			return;
		}
		downloadDirectory = selected;
		bmsroot.refresh();
	}

	@FXML
	public void markAsWorkDirectory() {
		String selected = bmsroot.getSelectionModel().getSelectedItem();
		if (selected == null) {
			return;
		}
		workDirectory = selected;
		bmsroot.refresh();
	}

	@FXML
	public void showAvailableTables() {
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle(resources.getString("CHOOSE_EXISTING_TABLES"));
		dialog.setHeaderText(null);
		ButtonType applyButtonType = new ButtonType(
				resources.getString("APPLY_TABLE_SELECTION"), ButtonBar.ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);

		Label instructions = new Label(resources.getString("CHOOSE_EXISTING_TABLES_DESCRIPTION"));
		instructions.setWrapText(true);
		List<CheckBox> selectors = new ArrayList<>();
		EnumMap<Config.AvailableTableGroup, List<TableChoiceRow>> rowsByGroup =
				new EnumMap<>(Config.AvailableTableGroup.class);
		for (Config.AvailableTableGroup group : Config.AvailableTableGroup.values()) {
			rowsByGroup.put(group, new ArrayList<>());
		}
		List<String> configuredTableUrls = tableurl.getItems().stream()
				.map(TableInfo::getUrl)
				.toList();
		List<TableChoice> choices = tableChoices(availableTableUrls, configuredTableUrls);
		List<String> initiallySelectedTableUrls = choices.stream()
				.filter(TableChoice::alreadyAdded)
				.map(TableChoice::url)
				.toList();
		for (TableChoice choiceState : choices) {
			String tableUrl = choiceState.url();
			TableInfo info = new TableInfo(tableUrl);
			String name = info.getNameStatus().isBlank() ? tableUrl : info.getNameStatus();
			CheckBox selector = new CheckBox(name);
			selector.setUserData(tableUrl);
			selector.setSelected(choiceState.alreadyAdded());
			selector.setMinWidth(280);
			selector.setPrefWidth(300);
			selector.setMaxWidth(320);
			selector.setTextOverrun(OverrunStyle.ELLIPSIS);
			selectors.add(selector);

			String detailText = info.getComment();
			if (choiceState.alreadyAdded()) {
				String alreadyAdded = resources.getString("TABLE_ALREADY_ADDED");
				detailText = detailText.isBlank() ? alreadyAdded : detailText + " · " + alreadyAdded;
			}
			Label detail = new Label(detailText);
			detail.setMaxWidth(Double.MAX_VALUE);
			String tooltipText = (detailText.isBlank() ? name : detailText) + "\n" + tableUrl;
			selector.setTooltip(new Tooltip(tooltipText));
			detail.setTooltip(new Tooltip(tooltipText));
			HBox choice = new HBox(10, selector, detail);
			choice.setAlignment(Pos.CENTER_LEFT);
			choice.setPadding(new Insets(3, 6, 3, 6));
			HBox.setHgrow(detail, Priority.ALWAYS);
			String searchText = String.join("\n", name, detailText, tableUrl).toLowerCase(Locale.ROOT);
			rowsByGroup.get(choiceState.group()).add(
					new TableChoiceRow(choiceState, choice, searchText));
		}

		VBox beginnerPrimaryChoices = choiceRows(rowsByGroup.get(Config.AvailableTableGroup.BEGINNER), false);
		VBox crossGameChoices = choiceRows(rowsByGroup.get(Config.AvailableTableGroup.BEGINNER), true);
		TitledPane crossGamePane = tableGroupPane(
				resources.getString("TABLE_GROUP_CROSS_GAME") + " (" + crossGameChoices.getChildren().size() + ")",
				crossGameChoices, false);
		VBox beginnerChoices = new VBox(4, beginnerPrimaryChoices, crossGamePane);
		TitledPane beginnerPane = tableGroupPane(
				groupTitle("TABLE_GROUP_BEGINNER", rowsByGroup.get(Config.AvailableTableGroup.BEGINNER)),
				beginnerChoices, true);
		TitledPane bmsirPane = tableGroupPane(
				groupTitle("TABLE_GROUP_BMSIR", rowsByGroup.get(Config.AvailableTableGroup.BMSIR)),
				choiceRows(rowsByGroup.get(Config.AvailableTableGroup.BMSIR), null), true);
		TitledPane otherPane = tableGroupPane(
				groupTitle("TABLE_GROUP_OTHER", rowsByGroup.get(Config.AvailableTableGroup.OTHER)),
				choiceRows(rowsByGroup.get(Config.AvailableTableGroup.OTHER), null), false);

		VBox groups = new VBox(6, beginnerPane, bmsirPane, otherPane);
		Label noMatches = new Label(resources.getString("TABLE_SEARCH_NO_MATCHES"));
		noMatches.setVisible(false);
		noMatches.setManaged(false);

		TextField search = new TextField();
		search.setPromptText(resources.getString("TABLE_SEARCH_PROMPT"));
		Label searchLabel = new Label(resources.getString("TABLE_SEARCH_LABEL"));
		searchLabel.setLabelFor(search);
		search.textProperty().addListener((observable, oldValue, newValue) -> {
			String query = newValue == null ? "" : newValue.strip().toLowerCase(Locale.ROOT);
			boolean beginnerMatches = updateTableChoiceVisibility(
					rowsByGroup.get(Config.AvailableTableGroup.BEGINNER), query);
			boolean bmsirMatches = updateTableChoiceVisibility(
					rowsByGroup.get(Config.AvailableTableGroup.BMSIR), query);
			boolean otherMatches = updateTableChoiceVisibility(
					rowsByGroup.get(Config.AvailableTableGroup.OTHER), query);
			boolean searching = !query.isEmpty();
			boolean crossGameMatches = rowsByGroup.get(Config.AvailableTableGroup.BEGINNER).stream()
					.anyMatch(row -> row.choice().crossGameSpecific() && row.node().isVisible());
			beginnerPane.setVisible(!searching || beginnerMatches);
			beginnerPane.setManaged(!searching || beginnerMatches);
			bmsirPane.setVisible(!searching || bmsirMatches);
			bmsirPane.setManaged(!searching || bmsirMatches);
			otherPane.setVisible(!searching || otherMatches);
			otherPane.setManaged(!searching || otherMatches);
			crossGamePane.setVisible(!searching || crossGameMatches);
			crossGamePane.setManaged(!searching || crossGameMatches);
			if (searching) {
				beginnerPane.setExpanded(beginnerMatches);
				bmsirPane.setExpanded(bmsirMatches);
				otherPane.setExpanded(otherMatches);
				crossGamePane.setExpanded(crossGameMatches);
			} else {
				beginnerPane.setExpanded(true);
				bmsirPane.setExpanded(true);
				otherPane.setExpanded(false);
				crossGamePane.setExpanded(false);
			}
			boolean hasMatches = beginnerMatches || bmsirMatches || otherMatches;
			noMatches.setVisible(searching && !hasMatches);
			noMatches.setManaged(searching && !hasMatches);
		});

		if (selectors.isEmpty()) {
			groups.getChildren().setAll(new Label(resources.getString("NO_AVAILABLE_TABLES")));
		}
		ScrollPane scroll = new ScrollPane(groups);
		scroll.setFitToWidth(true);
		scroll.setPrefViewportWidth(680);
		scroll.setPrefViewportHeight(360);
		VBox content = new VBox(8, instructions, searchLabel, search, noMatches, scroll);
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().setPrefWidth(720);

		Node applyButton = dialog.getDialogPane().lookupButton(applyButtonType);
		Runnable updateApplyButton = () -> {
			List<String> selectedTableUrls = selectors.stream()
					.filter(CheckBox::isSelected)
					.map(selector -> (String) selector.getUserData())
					.toList();
			applyButton.setDisable(!tableSelectionChanges(
					initiallySelectedTableUrls, selectedTableUrls).hasChanges());
		};
		updateApplyButton.run();
		selectors.forEach(selector -> selector.selectedProperty().addListener((observable, oldValue, newValue) ->
				updateApplyButton.run()));

		if (dialog.showAndWait().orElse(ButtonType.CANCEL) != applyButtonType) {
			return;
		}

		List<String> selectedTableUrls = selectors.stream()
				.filter(CheckBox::isSelected)
				.map(selector -> (String) selector.getUserData())
				.toList();
		TableSelectionChanges changes = tableSelectionChanges(
				initiallySelectedTableUrls, selectedTableUrls);
		Set<String> removedUrls = new HashSet<>(changes.removedUrls());
		tableurl.getItems().removeIf(info -> removedUrls.contains(info.getUrl()));
		availableTableUrls.addAll(changes.removedUrls());

		List<TableInfo> added = new ArrayList<>();
		for (String tableUrl : changes.addedUrls()) {
			if (hasTableUrl(tableUrl)) {
				continue;
			}
			TableInfo info = new TableInfo(tableUrl);
			tableurl.getItems().add(info);
			availableTableUrls.remove(tableUrl);
			added.add(info);
		}
		commit();
		loadTableUrls(added.stream().map(TableInfo::getUrl).toArray(String[]::new));
	}

	@FXML
	public void showAddTableUrlDialog() {
		showTableUrlDialog(null);
	}

	@FXML
	public void loadDiffBMS() {
		if (main != null) {
			main.loadDiffBMS();
		}
	}

	@FXML
	public void loadAllBMS() {
		if (main != null) {
			main.loadAllBMS();
		}
	}

	private MenuItem menuItem(String resourceKey, Runnable action) {
		MenuItem item = new MenuItem(resources.getString(resourceKey));
		item.setOnAction(event -> action.run());
		return item;
	}

	private void updateSongPath(String path) {
		if (path == null || main == null || !bmsroot.getItems().contains(path)) {
			return;
		}
		main.loadBMSPath(path);
	}

	private void openSongPath(String path) {
		if (path == null) {
			return;
		}
		try {
			Path directory = Path.of(path).toAbsolutePath().normalize();
			if (!Files.isDirectory(directory) || !Desktop.isDesktopSupported()) {
				showWarning("BMS_PATH_OPEN_FAILED", directory.toString());
				return;
			}
			Desktop.getDesktop().open(directory.toFile());
		} catch (IOException | SecurityException e) {
			showWarning("BMS_PATH_OPEN_FAILED", path);
		}
	}

	private void copySongPath(String path) {
		if (path == null) {
			return;
		}
		ClipboardContent content = new ClipboardContent();
		content.putString(path);
		Clipboard.getSystemClipboard().setContent(content);
	}

	private void removeSongPath(String path) {
		if (path == null) {
			return;
		}
		if (samePath(path, downloadDirectory)) {
			showWarning("BMS_PATH_DOWNLOAD_REMOVE_BLOCKED", path);
			return;
		}
		if (samePath(path, workDirectory)) {
			showWarning("BMS_PATH_WORK_REMOVE_BLOCKED", path);
			return;
		}
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.setTitle(resources.getString("BMS_PATH_REMOVE_TITLE"));
		confirm.setHeaderText(resources.getString("BMS_PATH_REMOVE_HEADER"));
		confirm.setContentText(resources.getString("BMS_PATH_REMOVE_DESCRIPTION") + "\n\n" + path);
		if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
			bmsroot.getItems().remove(path);
		}
	}

	private static boolean samePath(String left, String right) {
		if (left == null || right == null) {
			return false;
		}
		return Path.of(left).toAbsolutePath().normalize()
				.equals(Path.of(right).toAbsolutePath().normalize());
	}

	private void showWarning(String resourceKey, String detail) {
		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle(resources.getString("RESOURCE_SETTINGS_TITLE"));
		alert.setHeaderText(resources.getString(resourceKey));
		alert.setContentText(detail);
		alert.showAndWait();
	}

	private void updateTable(TableInfo info) {
		if (info == null) {
			return;
		}
		loadTableUrls(new String[]{info.getUrl()});
	}

	private void editTableUrl(TableInfo info) {
		if (info != null) {
			showTableUrlDialog(info);
		}
	}

	private void showTableUrlDialog(TableInfo editing) {
		TextInputDialog dialog = new TextInputDialog(editing == null ? "" : editing.getUrl());
		dialog.setTitle(resources.getString(editing == null ? "ADD_TABLE_FROM_URL" : "TABLE_EDIT_URL"));
		dialog.setHeaderText(resources.getString(editing == null
				? "ADD_TABLE_FROM_URL_DESCRIPTION"
				: "TABLE_EDIT_URL_DESCRIPTION"));
		dialog.setContentText(resources.getString("TABLE_URL_COLUMN"));

		Optional<String> result = dialog.showAndWait();
		if (result.isEmpty()) {
			return;
		}
		String newUrl = result.get().trim();
		if (!isHttpUrl(newUrl)) {
			showWarning("TABLE_URL_INVALID", newUrl);
			return;
		}
		if (tableurl.getItems().stream().anyMatch(info -> info != editing && info.getUrl().equals(newUrl))) {
			showWarning("TABLE_URL_DUPLICATE", newUrl);
			return;
		}

		if (editing == null) {
			TableInfo info = new TableInfo(newUrl);
			tableurl.getItems().add(info);
			availableTableUrls.remove(newUrl);
			commit();
			loadTableUrls(new String[]{newUrl});
			return;
		}

		String oldUrl = editing.getUrl();
		if (oldUrl.equals(newUrl)) {
			return;
		}
		availableTableUrls.add(oldUrl);
		availableTableUrls.remove(newUrl);
		editing.setUrl(newUrl);
		tableurl.refresh();
		commit();
		loadTableUrls(new String[]{newUrl});
	}

	private boolean isHttpUrl(String value) {
		try {
			URL parsed = new URL(value);
			return ("http".equalsIgnoreCase(parsed.getProtocol())
					|| "https".equalsIgnoreCase(parsed.getProtocol()))
					&& parsed.getHost() != null && !parsed.getHost().isBlank();
		} catch (IOException e) {
			return false;
		}
	}

	private boolean hasTableUrl(String value) {
		return tableurl.getItems().stream().anyMatch(info -> info.getUrl().equals(value));
	}

	static List<TableChoice> tableChoices(Collection<String> availableUrls, Collection<String> configuredUrls) {
		LinkedHashSet<String> knownUrls = new LinkedHashSet<>(Arrays.asList(Config.AVAILABLE_TABLEURL));
		knownUrls.addAll(availableUrls);
		Set<String> configured = new HashSet<>(configuredUrls);
		return knownUrls.stream()
				.map(url -> new TableChoice(
						url,
						configured.contains(url),
						Config.availableTableGroup(url),
						Config.isCrossGameSpecificTable(url)))
				.toList();
	}

	static TableSelectionChanges tableSelectionChanges(
			Collection<String> initiallySelectedUrls,
			Collection<String> selectedUrls) {
		LinkedHashSet<String> initial = new LinkedHashSet<>(initiallySelectedUrls);
		LinkedHashSet<String> selected = new LinkedHashSet<>(selectedUrls);
		List<String> added = selected.stream()
				.filter(url -> !initial.contains(url))
				.toList();
		List<String> removed = initial.stream()
				.filter(url -> !selected.contains(url))
				.toList();
		return new TableSelectionChanges(added, removed);
	}

	private VBox choiceRows(List<TableChoiceRow> rows, Boolean crossGameSpecific) {
		VBox choices = new VBox(1);
		rows.stream()
				.filter(row -> crossGameSpecific == null
						|| row.choice().crossGameSpecific() == crossGameSpecific)
				.forEach(row -> choices.getChildren().add(row.node()));
		return choices;
	}

	private TitledPane tableGroupPane(String title, Node content, boolean expanded) {
		TitledPane pane = new TitledPane(title, content);
		pane.setAnimated(false);
		pane.setExpanded(expanded);
		return pane;
	}

	private String groupTitle(String resourceKey, List<TableChoiceRow> rows) {
		return resources.getString(resourceKey) + " (" + rows.size() + ")";
	}

	static boolean tableChoiceMatches(String searchText, String query) {
		return query == null || query.isBlank()
				|| searchText.toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT));
	}

	private boolean updateTableChoiceVisibility(List<TableChoiceRow> rows, String query) {
		boolean anyVisible = false;
		for (TableChoiceRow row : rows) {
			boolean visible = tableChoiceMatches(row.searchText(), query);
			row.node().setVisible(visible);
			row.node().setManaged(visible);
			anyVisible |= visible;
		}
		return anyVisible;
	}

	record TableChoice(
			String url,
			boolean alreadyAdded,
			Config.AvailableTableGroup group,
			boolean crossGameSpecific) {
	}

	record TableSelectionChanges(List<String> addedUrls, List<String> removedUrls) {
		boolean hasChanges() {
			return !addedUrls.isEmpty() || !removedUrls.isEmpty();
		}
	}

	private record TableChoiceRow(
			TableChoice choice,
			HBox node,
			String searchText) {
	}

	private void moveTable(TableInfo info, int direction) {
		if (info == null) {
			return;
		}
		int from = tableurl.getItems().indexOf(info);
		int to = from + direction;
		if (from < 0 || to < 0 || to >= tableurl.getItems().size()) {
			return;
		}
		tableurl.getItems().remove(from);
		tableurl.getItems().add(to, info);
		tableurl.getSelectionModel().clearAndSelect(to);
		commit();
	}

	private void removeTable(TableInfo info) {
		if (info == null) {
			return;
		}
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.setTitle(resources.getString("TABLE_REMOVE_TITLE"));
		confirm.setHeaderText(resources.getString("TABLE_REMOVE_HEADER"));
		confirm.setContentText(info.displayName() + "\n" + info.getUrl());
		if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
			return;
		}
		tableurl.getItems().remove(info);
		availableTableUrls.add(info.getUrl());
		commit();
	}

	private Stage createTableProgressStage() {
		Stage loadingBarStage = new Stage();
		loadingBarStage.setResizable(false);
		loadingBarStage.initModality(Modality.APPLICATION_MODAL);
		loadingBarStage.setTitle(resources.getString("PROGRESS_TABLE_TITLE"));
		loadingBarStage.initStyle(StageStyle.UTILITY);
		ProgressBar progressBar = new ProgressBar();
		progressBar.setPrefWidth(300);
		Label messageLabel = new Label(resources.getString("PROGRESS_TABLE_LABEL"));
		VBox root = new VBox(10, messageLabel, progressBar);
		root.setStyle("-fx-padding: 20; -fx-alignment: center;");
		loadingBarStage.setScene(new Scene(root));
		loadingBarStage.setOnCloseRequest(Event::consume);
		return loadingBarStage;
	}

	private void updateVisibleTableNames(List<TableInfo> visibleTables, HashMap<String, String> names) {
		for (TableInfo info : visibleTables) {
			String loadedName = names == null ? null : names.get(info.getUrl());
			info.setNameStatus(loadedName == null
					? resources.getString("TABLE_NOT_LOADED")
					: loadedName);
		}
		tableurl.refresh();
	}

	private void showAllTableUpdateResult(TableDataAccessor.TableUpdateResult result) {
		Alert alert;
		if (result.success()) {
			alert = new Alert(Alert.AlertType.INFORMATION);
			alert.setHeaderText(resources.getString("TABLES_LOAD_ALL_SUCCESS"));
			alert.setContentText(MessageFormat.format(
					resources.getString("TABLES_LOAD_ALL_SUCCESS_DESCRIPTION"),
					result.updatedCount()));
		} else if (!result.failedUrls().isEmpty()) {
			alert = new Alert(Alert.AlertType.WARNING);
			alert.setHeaderText(resources.getString("TABLES_LOAD_ALL_FAILED"));
			alert.setContentText(MessageFormat.format(
					resources.getString("TABLES_LOAD_ALL_FETCH_FAILED_DESCRIPTION"),
					String.join("\n", result.failedUrls())));
		} else {
			alert = new Alert(Alert.AlertType.ERROR);
			alert.setHeaderText(resources.getString("TABLES_LOAD_ALL_FAILED"));
			alert.setContentText(resources.getString("TABLES_LOAD_ALL_CACHE_FAILED_DESCRIPTION"));
		}
		alert.setTitle(resources.getString("TABLES_LOAD_ALL"));
		alert.showAndWait();
	}

	private void loadTableUrls(String[] urls) {
		if (urls == null || urls.length == 0) {
			return;
		}
		commit();
		try {
			Files.createDirectories(Paths.get(config.getTablepath()));
		} catch (IOException ignored) {
		}

		Stage loadingBarStage = createTableProgressStage();
		loadingBarStage.show();

		List<TableInfo> visibleTables = new ArrayList<>(tableurl.getItems());
		Thread worker = new Thread(() -> {
			TableDataAccessor accessor = new TableDataAccessor(config.getTablepath());
			accessor.updateTableData(urls);
			String[] visibleUrls = visibleTables.stream().map(TableInfo::getUrl).toArray(String[]::new);
			HashMap<String, String> names = accessor.readLocalTableNames(visibleUrls);
			Platform.runLater(() -> {
				updateVisibleTableNames(visibleTables, names);
				loadingBarStage.hide();
			});
		}, "Difficulty Table Update");
		worker.start();
	}

	private static class TableInfo {
		private StringProperty url;
		public void setUrl(String value) {
			urlProperty().set(value);
			Pair<String, String> nameComment = tableNameComment.get(value);
			if (nameComment == null) {
				setNameStatus("");
				setComment("");
			} else {
				setNameStatus(nameComment.getKey());
				setComment(nameComment.getValue());
			}
		}
		public String getUrl() { return urlProperty().get(); }
		public StringProperty urlProperty() { 
			if (url == null) url = new SimpleStringProperty(this, "url");
			return url; 
		}
		private StringProperty nameStatus;
		public void setNameStatus(String value) { nameStatusProperty().set(value); }
		public String getNameStatus() { return nameStatusProperty().get(); }
		public StringProperty nameStatusProperty() { 
			if (nameStatus == null) nameStatus = new SimpleStringProperty(this, "nameStatus");
			return nameStatus; 
		}

		private StringProperty comment;
		public void setComment(String value) { commentProperty().set(value); }
		public String getComment() { return commentProperty().get(); }
		public StringProperty commentProperty() {
			if (comment == null) comment = new SimpleStringProperty(this, "comment");
			return comment;
		}

		public TableInfo(String url) {
			setUrl(url);
		}

		public String displayName() {
			return getNameStatus().isBlank() ? getUrl() : getNameStatus();
		}

		public static String[] toUrlArray(List<TableInfo> list) {
			String[] urls = new String[list.size()];
			int i = 0;
			for (TableInfo tableInfo : list) {
				urls[i++] = tableInfo.getUrl();
			}
			return urls;
		}

		public static void populateList(List<TableInfo> list, String[] urls) {
			list.clear();
			for (String url : urls) {
				list.add(new TableInfo(url));
			}
		}
	}

	public static Map<String, Pair<String, String>> tableNameComment = Map.ofEntries(
			// Java string literals are UTF-16 by default, my apologies for escape code spaghetti
			//
			// An entry is defined by (TableURL --- > [ TableName, TableDescription ])
			// sl,st,stardust,starlight
			Map.entry("https://mqppppp.neocities.org/StardustTable.html", new Pair<String, String>("Stardust", "Beginner \u26061-\u26067")),
			Map.entry("https://djkuroakari.github.io/starlighttable.html", new Pair<String, String>("Stardust", "Intermediate \u26067-\u260612")),
			Map.entry("https://stellabms.xyz/sl/table.html", new Pair<String, String>("Satellite", "Insane \u260611-\u260519")),
			Map.entry("https://stellabms.xyz/st/table.html", new Pair<String, String>("Stella", "High Insane to Overjoy \u260519-\u2605\u26057")),
			// the insanes
			Map.entry("https://darksabun.club/table/archive/normal1/", new Pair<String, String>("\u901a\u5e38\u96e3\u6613\u5ea6\u8868 (Normal 1)", "Beginner to Intermediate \u26061-\u260612")),
			Map.entry("https://darksabun.club/table/archive/insane1/", new Pair<String, String>("\u767a\u72c2BMS\u96e3\u6613\u5ea6\u8868 (Insane 1)", "Insane \u26051-\u260525")),
			Map.entry("http://rattoto10.jounin.jp/table.html", new Pair<String, String>("NEW GENERATION \u901a\u5e38 (Normal 2)", "Post 2016 Normal Table \u26061-\u260612")),
			Map.entry("http://rattoto10.jounin.jp/table_insane.html", new Pair<String, String>("NEW GENERATION \u767a\u72c2 (Insane 2)", "Post 2016 Insane Table \u26051-\u260525")),
			// overjoy
			Map.entry("https://rattoto10.jounin.jp/table_overjoy.html", new Pair<String, String>("\u7b2c3\u671fOverjoy", "Third-generation Overjoy table. \u2605\u26050-\u2605\u26057")),
			Map.entry("https://lr2.sakura.ne.jp/overjoy.php", new Pair<String, String>("Overjoy", "Current Overjoy table")),
			Map.entry("https://bmsnormal2.syuriken.jp/table_insane.html", new Pair<String, String>("\u7b2c2\u767a\u72c2\u96e3\u6613\u5ea6", "Second insane table")),
			Map.entry("https://bmsnormal2.syuriken.jp/table.html", new Pair<String, String>("\u7b2c2\u901a\u5e38\u96e3\u6613\u5ea6\u8868", "Second normal table")),
			Map.entry("https://stellabms.xyz/sr/table.html", new Pair<String, String>("STELLAVERSE Starlight", "Normal difficulty sr1-sr12")),
			Map.entry("https://rattoto10.web.fc2.com/kuse_library/table.html", new Pair<String, String>("\u7656\u8b5c\u9762\u30e9\u30a4\u30d6\u30e9\u30ea\u30fc", "Quirky-pattern library")),
			Map.entry("https://rattoto10.web.fc2.com/kuse_library/sub/table.html", new Pair<String, String>("\u7656\u8b5c\u9762\u30e9\u30a4\u30d6\u30e9\u30ea\u30fc \u30b5\u30d6", "Quirky-pattern library subtable")),
			Map.entry("https://mplwtch.github.io/Solomon/", new Pair<String, String>("Solomon\u96e3\u6613\u5ea6\u8868", "BMS-IR supported table")),
			Map.entry("https://monsta-bms.github.io/appamada/", new Pair<String, String>("\u4e0d\u653e\u9038", "BMS-IR supported table")),
			Map.entry("https://potechang.github.io/like_st/", new Pair<String, String>("BMS\u73fe\u4ee3\u767a\u72c2\u8b5c\u9762\u5bc4\u305b\u96c6\u3081", "Modern insane-chart collection")),
			Map.entry("https://verticalsub.web.fc2.com/jiriki/table.html", new Pair<String, String>("BMS\u5730\u529b\u8b5c\u9762\u30a2\u30fc\u30ab\u30a4\u30d6", "Fundamental-skill chart archive")),
			Map.entry("https://akaza2nd.github.io/zetsu/", new Pair<String, String>("\u8155\u30ac\u30c1\u96e3\u6613\u5ea6\u8868", "Chord and physical-skill table")),
			Map.entry("https://lets-go-time-hell.github.io/mini-jack/", new Pair<String, String>("\u9023\u6253\u7d61\u307f\u96e3\u6613\u5ea6\u8868", "Mini-jack and repeated-note table")),
			Map.entry("https://kenpel.github.io/objeCraft/table14/", new Pair<String, String>("\u767a\u72c214keyBMS\u95c7\u934b\u96e3\u6613\u5ea6\u8868", "14-key BMS table")),
			// stream + chordjack
			Map.entry("https://lets-go-time-hell.github.io/code-stream-table/", new Pair<String, String>("16\u5206\u4e71 (16th streams)", "Chordstream focus. Wide difficulty \u260611-\u260520+")),
			Map.entry("https://lets-go-time-hell.github.io/Arm-Shougakkou-table/", new Pair<String, String>("\u30a6\u30fc\u30c7\u30aa\u30b7\u5c0f\u5b66\u6821 (Ude table)", "Chordjack/wide chords focus. Satellite difficulty")),
			Map.entry("https://su565fx.web.fc2.com/Gachimijoy/gachimijoy.html", new Pair<String, String>("gachimijoy", "Hard chordjack. \u2605\u26050-\u2605\u26057")),
			// stellaverse quirked up
			Map.entry("https://stellabms.xyz/so/table.html", new Pair<String, String>("Solar", "Insane-style charts. Satellite difficulty")),
			Map.entry("https://stellabms.xyz/sn/table.html", new Pair<String, String>("Supernova", "Insane-style charts. Stella difficulty")),
			// osu
			Map.entry("https://air-afother.github.io/osu-table/", new Pair<String, String>("osu!", "Table for osu! star rating")),
			// AI
			Map.entry("https://bms.hexlataia.xyz/tables/ai.html", new Pair<String, String>("Hex's AI", "Algorithmically assigned difficulty. Insane and LN range")),
			// Library
			Map.entry("https://bms.hexlataia.xyz/tables/db.html", new Pair<String, String>("\u767a\u72c2\u96e3\u6613\u5ea6\u30c7\u30fc\u30bf\u30d9\u30fc\u30b9 (Hex's DB)", "Manually assigned difficulty. Insane \u26050-\u260525+")),
			Map.entry("https://bms.hexlataia.xyz/tables/olduploader.html", new Pair<String, String>("\u65e7\u30a2\u30d7\u30ed\u30c0\u8868 (Hex's Old uploader)", "Manually assigned difficulty. Mostly Insane \u260610-\u260525+ with LN + Scratch ratings")),
			Map.entry("https://stellabms.xyz/upload.html", new Pair<String, String>("Stella Uploader", "Stellaverse uploader. Insane \u26051-\u260525+")),
			Map.entry("https://exturbow.github.io/github.io/index.html", new Pair<String, String>("BMS\u56f3\u66f8\u9928 (Turbow's Toshokan)", "Rates BMS event submissions. Wide difficulty \u26061-\u260525+")),
			//Map.entry("http://upl.konjiki.jp/", new Pair<String, String>("差分アップローダー (Black train uploader)", "Manually assigned difficulty. Insane \u26050-\u260525+")),
			// Beginner-friendly cross-game tables hosted by BMS-IR
			Map.entry("https://www.bms-ir.org/new/table/61", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - \u5168\u66f2\u30de\u30b9\u30bf\u30fc", "Beginner-friendly master; levels sync to the game-specific tables")),
			Map.entry("https://www.bms-ir.org/new/table/62", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - CHUNITHM", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/63", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - maimai", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/64", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - SOUND VOLTEX", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/65", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Phigros", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/66", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Arcaea", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/67", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Muse Dash", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/68", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - \u592a\u9f13\u306e\u9054\u4eba", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/69", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - \u30aa\u30f3\u30b2\u30ad", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/70", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - GROOVE COASTER", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/71", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Cytus\u30b7\u30ea\u30fc\u30ba", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/72", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - DEEMO\u30b7\u30ea\u30fc\u30ba", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/73", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - VOEZ", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/74", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Lanota", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/75", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - KALPA\u30b7\u30ea\u30fc\u30ba", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/76", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Rotaeno", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/77", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Paradigm: Reboot", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/78", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Orzmic", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/79", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - ChainBeeT", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/80", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - DanceRail\u30b7\u30ea\u30fc\u30ba", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/81", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - RAVON", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/82", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Dynamix", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/83", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - MUSYNC / MUSYNX", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/84", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Pump It Up", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/85", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - WACCA", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/86", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - DJMAX\u30b7\u30ea\u30fc\u30ba", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/87", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - EZ2ON\u30b7\u30ea\u30fc\u30ba", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/88", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - EZ2AC / EZ2DJ\u30b7\u30ea\u30fc\u30ba", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/89", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - D4DJ Groovy Mix", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/90", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Tone Sphere", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/91", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - MUSIC DIVER", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/92", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Polaris Chord", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/93", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - TAKUMI\u00b3", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/94", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Rizline", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/95", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - MUSECA", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/96", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Nostalgia", "Game-specific beginner table")),
			Map.entry("https://www.bms-ir.org/new/table/97", new Pair<String, String>("\u4ed6\u6a5f\u7a2e\u53ce\u9332BMS - Synchronica", "Game-specific beginner table")),
			// Other beginner-oriented tables remain in the general Other section.
			Map.entry("http://fezikedifficulty.futene.net/list.html", new Pair<String, String>("\u6c60\u7530\u7684 (Ikeda's Beginner)", "Beginner focused table. 19 levels \u26061-\u260611+")),
			// LN
			Map.entry("https://ladymade-star.github.io/luminous/table.html", new Pair<String, String>("Luminous", "Active LN table. \u25c61-\u25c627")),
			Map.entry("https://vinylhouse.web.fc2.com/lntougou/difficulty.html", new Pair<String, String>("Longnote\u7d71\u5408\u8868 (LN Combined)", "\u25c61-\u25c627")),
			Map.entry("http://flowermaster.web.fc2.com/lrnanido/gla/LN.html", new Pair<String, String>("\u7b2c3\u671fLN\u96e3\u6613\u5ea6\u8868", "BMS-IR supported LN table \u25c61-\u25c626")),
			Map.entry("https://skar-wem.github.io/ln/", new Pair<String, String>("LN Curtain", "Full/inverse LN charts. \u25c61-\u25c626")),
			Map.entry("http://cerqant.web.fc2.com/zindy/table.html", new Pair<String, String>("zindy LN", "Difficult shield stair patterns. Hard LN \u25c615-\u25c627+")),
			Map.entry("https://notepara.com/glassist/lnoj", new Pair<String, String>("LNoverjoy", "Hard LN table. \u25c615-\u25c627")),
			// Scratch
			Map.entry("https://egret9.github.io/Scramble/", new Pair<String, String>("Scramble", "Active scratch table")),
			Map.entry("http://minddnim.web.fc2.com/sara/3rd_hard/bms_sara_3rd_hard.html", new Pair<String, String>("\u76bf\u96e3\u6613\u5ea6\u8868(3rd) (Sara 3rd)", "Old scratch table")),
			// delay
			Map.entry("https://lets-go-time-hell.github.io/Delay-joy-table/", new Pair<String, String>("\u30c7\u30a3\u30ec\u30a4joy (delayjoy)", "Delay focus. Wide difficulty with heavy stella bias")),
			Map.entry("https://kamikaze12345.github.io/github.io/delaytrainingtable/table.html", new Pair<String, String>("DELAY Training Table", "Comprehensive delay table. Wide difficulty \u26051-\u2605\u26057")),
			Map.entry("https://wrench616.github.io/Delay/", new Pair<String, String>("Delay\u5c0f\u5b66\u6821", "Intermediate delay table. \u26051-\u260524")),
			// High Diff
			Map.entry("https://darksabun.club/table/archive/old-overjoy/", new Pair<String, String>("Overjoy (\u65e7) (Old overjoy)", "Pre-2018 overjoy table. \u2605\u26050-\u2605\u26057")),
			Map.entry("https://monibms.github.io/Dystopia/dystopia.html", new Pair<String, String>("Dystopia", "Active hard table. dy0-dy7 is st5-st12. dy8+ is st12+")),
			Map.entry("https://www.firiex.com/tables/joverjoy", new Pair<String, String>("joverjoy", "Large alternative to overjoy, last updated 2021. \u2605\u26050-\u2605\u26057+")),
			// Hard Judge
			Map.entry("https://plyfrm.github.io/table/timing/", new Pair<String, String>("Timing Table (Hard judge Table)", "Exclusively Hard judge. Judge turns easy to clear charts into challenges \u26067-\u26052++")),
			// Artist search
			Map.entry("https://plyfrm.github.io/table/bmssearch/index.html", new Pair<String, String>("BMSSearch Artists", "Contains 2400+ unique artists and nearly 100k bms")),
			// DP
			Map.entry("https://yaruki0.net/DPlibrary/", new Pair<String, String>("DPBMS\u3068\u8af8\u611f (Bluvel table)", "DP beginner focus. \u26061-\u260612")),
			Map.entry("https://stellabms.xyz/dp/table.html", new Pair<String, String>("DP Satellite", "Stellaverse. Roughly tracks \u260610-\u260510")),
			Map.entry("https://stellabms.xyz/dpst/table.html", new Pair<String, String>("DP Stella", "Stellaverse. Roughly tracks \u260510-\u2605\u26058+")),
			Map.entry("https://deltabms.yaruki0.net/table/data/dpdelta_head.json", new Pair<String, String>("\u03b4\u96e3\u6613\u5ea6\u8868 (delta table)", "DP beginner focus. Contains IIDX equivalent GENOSIDE dans")),
			Map.entry("https://deltabms.yaruki0.net/table/data/insane_head.json", new Pair<String, String>("\u767a\u72c2DP\u96e3\u6613\u5ea6\u8868 (DP Insane)", "Rated \u26051-\u260513")),
			Map.entry("http://ereter.net/dpoverjoy/", new Pair<String, String>("DP overjoy", "Hard DP table \u260510+")),
			// Stella Extensions
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/satellite-easy.html", new Pair<String, String>("Satellite EASY", "Rated by difficulty to attain EC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/satellite-normal.html", new Pair<String, String>("Satellite NORMAL", "Rated by difficulty to attain NC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/satellite-hard.html", new Pair<String, String>("Satellite HARD", "Rated by difficulty to attain HC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/satellite-fullcombo.html", new Pair<String, String>("Satellite FULLCOMBO", "Rated by difficulty to attain FC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/stella-easy.html", new Pair<String, String>("Stella EASY", "Rated by difficulty to attain EC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/stella-normal.html", new Pair<String, String>("Stella NORMAL", "Rated by difficulty to attain NC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/stella-hard.html", new Pair<String, String>("Stella HARD", "Rated by difficulty to attain HC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/stella-fullcombo.html", new Pair<String, String>("Stella FULLCOMBO", "Rated by difficulty to attain FC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/dp-satellite-easy.html", new Pair<String, String>("DP Satellite EASY", "Rated by difficulty to attain EC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/dp-satellite-normal.html", new Pair<String, String>("DP Satellite NORMAL", "Rated by difficulty to attain NC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/dp-satellite-hard.html", new Pair<String, String>("DP Satellite HARD", "Rated by difficulty to attain HC")),
			Map.entry("https://notmichaelchen.github.io/stella-table-extensions/dp-satellite-fullcombo.html", new Pair<String, String>("DP Satellite FULLCOMBO", "Rated by difficulty to attain FC")),
			// Walkure
			Map.entry("http://walkure.net/hakkyou/for_glassist/bms/?lamp=easy", new Pair<String, String>("\u767a\u72c2BMS\u96e3\u5ea6\u63a8\u5b9a\u8868 EASY", "Rated by difficulty to attain EC")),
			Map.entry("http://walkure.net/hakkyou/for_glassist/bms/?lamp=normal", new Pair<String, String>("\u767a\u72c2BMS\u96e3\u5ea6\u63a8\u5b9a\u8868 NORMAL", "Rated by difficulty to attain NC")),
			Map.entry("http://walkure.net/hakkyou/for_glassist/bms/?lamp=hard", new Pair<String, String>("\u767a\u72c2BMS\u96e3\u5ea6\u63a8\u5b9a\u8868 HARD", "Rated by difficulty to attain HC")),
			Map.entry("http://walkure.net/hakkyou/for_glassist/bms/?lamp=fc", new Pair<String, String>("\u767a\u72c2BMS\u96e3\u5ea6\u63a8\u5b9a\u8868 FULLCOMBO", "Rated by difficulty to attain FC"))
	);
}
