package bms.player.beatoraja.launcher;

import java.text.NumberFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.StringJoiner;

import bms.player.beatoraja.song.SongDatabaseUpdateListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Displays the complete result of one song-library update. */
final class SongUpdateSummaryDialog {
	static final double DIALOG_WIDTH = 720.0;

	private SongUpdateSummaryDialog() {
	}

	static void show(Window owner, ResourceBundle resources, SongDatabaseUpdateListener listener) {
		Dialog<ButtonType> dialog = new Dialog<>();
		if (owner != null) {
			dialog.initOwner(owner);
			dialog.initModality(Modality.WINDOW_MODAL);
		}
		dialog.setTitle(resources.getString("SONG_UPDATE_RESULT_TITLE"));
		dialog.setHeaderText(resources.getString("SONG_UPDATE_RESULT_HEADER"));

		DialogPane pane = dialog.getDialogPane();
		String stylesheet = SongUpdateSummaryDialog.class.getResource("LauncherStyle.css").toExternalForm();
		pane.getStylesheets().add(stylesheet);
		pane.getStyleClass().add("song-update-result-dialog");
		pane.setPrefWidth(DIALOG_WIDTH);

		boolean hasArchiveFailures = listener.getArchivesRejected() > 0;
		pane.setGraphic(statusGraphic(resources, hasArchiveFailures));
		pane.setContent(resultContent(resources, listener));

		ButtonType closeType = new ButtonType(
				resources.getString("SONG_UPDATE_RESULT_CLOSE"),
				ButtonBar.ButtonData.OK_DONE);
		pane.getButtonTypes().setAll(closeType);
		Button closeButton = (Button) pane.lookupButton(closeType);
		closeButton.setDefaultButton(true);
		closeButton.setAccessibleText(resources.getString("SONG_UPDATE_RESULT_CLOSE"));
		dialog.show();
	}

	private static VBox resultContent(ResourceBundle resources, SongDatabaseUpdateListener listener) {
		boolean hasArchives = listener.getArchivesScanned() > 0;
		Label description = wrappingLabel(resources.getString(hasArchives
				? "SONG_UPDATE_RESULT_DESCRIPTION"
				: "SONG_UPDATE_RESULT_DESCRIPTION_NO_ARCHIVES"));
		description.getStyleClass().add("song-update-result-description");

		VBox content = new VBox(14,
				description,
				resultSection(resources.getString("SONG_UPDATE_RESULT_CHARTS"), List.of(
						new Statistic(resources.getString("SONG_UPDATE_RESULT_DETECTED"),
								formatCount(resources, listener.getBMSFilesCount())),
						new Statistic(resources.getString("SONG_UPDATE_RESULT_PROCESSED"),
								formatCount(resources, listener.getProcessedBMSFilesCount())),
						new Statistic(resources.getString("SONG_UPDATE_RESULT_ADDED_UPDATED"),
								formatCount(resources, listener.getNewBMSFilesCount()))), false));

		if (hasArchives) {
			content.getChildren().add(resultSection(resources.getString("SONG_UPDATE_RESULT_ARCHIVES"), List.of(
					new Statistic(resources.getString("SONG_UPDATE_RESULT_ARCHIVES_CHECKED"),
							formatCount(resources, listener.getArchivesScanned())),
					new Statistic(resources.getString("SONG_UPDATE_RESULT_ARCHIVES_LOADED"),
							formatCount(resources, listener.getArchivesLoaded())),
					new Statistic(resources.getString("SONG_UPDATE_RESULT_ARCHIVES_UNREADABLE"),
							formatCount(resources, listener.getArchivesRejected()))), true));

			Label footnote = wrappingLabel(resources.getString("SONG_UPDATE_RESULT_ARCHIVE_FOOTNOTE"));
			footnote.getStyleClass().add("song-update-result-footnote");
			content.getChildren().add(footnote);
		}

		if (listener.getArchivesRejected() > 0) {
			Label failure = wrappingLabel(String.format(
					resources.getString("SONG_UPDATE_RESULT_LAST_FAILURE"),
					listener.getLastArchiveFailure()));
			failure.getStyleClass().add("song-update-result-failure");
			content.getChildren().add(failure);
		}

		content.getStyleClass().add("song-update-result-content");
		content.setAccessibleText(accessibleSummary(resources, listener));
		return content;
	}

	private static VBox resultSection(String headingText, List<Statistic> statistics, boolean secondary) {
		Label heading = new Label(headingText);
		heading.getStyleClass().add("song-update-result-section-title");

		GridPane metrics = new GridPane();
		metrics.getStyleClass().add("song-update-result-metrics");
		for (int index = 0; index < statistics.size(); index++) {
			ColumnConstraints column = new ColumnConstraints();
			column.setPercentWidth(100.0 / statistics.size());
			column.setHgrow(Priority.ALWAYS);
			metrics.getColumnConstraints().add(column);

			Statistic statistic = statistics.get(index);
			Label label = new Label(statistic.label());
			label.getStyleClass().add("song-update-result-stat-label");
			Label value = new Label(statistic.value());
			value.getStyleClass().add("song-update-result-stat-value");
			VBox metric = new VBox(4, label, value);
			metric.setAlignment(Pos.CENTER);
			metric.getStyleClass().add("song-update-result-metric");
			if (index > 0) {
				metric.getStyleClass().add("song-update-result-metric-divided");
			}
			metrics.add(metric, index, 0);
		}

		VBox section = new VBox(8, heading, metrics);
		section.getStyleClass().add("song-update-result-section");
		if (secondary) {
			section.getStyleClass().add("song-update-result-section-secondary");
		}
		return section;
	}

	private static Label wrappingLabel(String text) {
		Label label = new Label(text);
		label.setWrapText(true);
		label.setMaxWidth(Double.MAX_VALUE);
		return label;
	}

	private static Node statusGraphic(ResourceBundle resources, boolean warning) {
		Circle background = new Circle(22);
		background.getStyleClass().add("song-update-result-icon-background");
		StackPane graphic;
		if (warning) {
			background.getStyleClass().add("song-update-result-icon-warning");
			Label mark = new Label("!");
			mark.getStyleClass().add("song-update-result-icon-warning-mark");
			graphic = new StackPane(background, mark);
		} else {
			SVGPath mark = new SVGPath();
			mark.setContent("M 7 22 L 16 30 L 34 11");
			mark.getStyleClass().add("song-update-result-icon-check");
			graphic = new StackPane(background, mark);
		}
		graphic.setAccessibleText(resources.getString(warning
				? "SONG_UPDATE_RESULT_STATUS_WARNING"
				: "SONG_UPDATE_RESULT_STATUS_COMPLETE"));
		return graphic;
	}

	static String formatCount(ResourceBundle resources, int count) {
		String number = NumberFormat.getIntegerInstance(resources.getLocale()).format(count);
		return String.format(resources.getString("SONG_UPDATE_RESULT_COUNT"), number);
	}

	static String accessibleSummary(ResourceBundle resources, SongDatabaseUpdateListener listener) {
		List<Statistic> chartStatistics = List.of(
				new Statistic(resources.getString("SONG_UPDATE_RESULT_DETECTED"),
						formatCount(resources, listener.getBMSFilesCount())),
				new Statistic(resources.getString("SONG_UPDATE_RESULT_PROCESSED"),
						formatCount(resources, listener.getProcessedBMSFilesCount())),
				new Statistic(resources.getString("SONG_UPDATE_RESULT_ADDED_UPDATED"),
						formatCount(resources, listener.getNewBMSFilesCount())));
		String result = resources.getString("SONG_UPDATE_RESULT_HEADER") + ". "
				+ resources.getString("SONG_UPDATE_RESULT_CHARTS") + ": "
				+ joinStatistics(chartStatistics);
		if (listener.getArchivesScanned() > 0) {
			List<Statistic> archiveStatistics = List.of(
					new Statistic(resources.getString("SONG_UPDATE_RESULT_ARCHIVES_CHECKED"),
							formatCount(resources, listener.getArchivesScanned())),
					new Statistic(resources.getString("SONG_UPDATE_RESULT_ARCHIVES_LOADED"),
							formatCount(resources, listener.getArchivesLoaded())),
					new Statistic(resources.getString("SONG_UPDATE_RESULT_ARCHIVES_UNREADABLE"),
							formatCount(resources, listener.getArchivesRejected())));
			result += "; " + resources.getString("SONG_UPDATE_RESULT_ARCHIVES") + ": "
					+ joinStatistics(archiveStatistics);
		}
		if (listener.getArchivesRejected() > 0) {
			result += ". " + String.format(
					resources.getString("SONG_UPDATE_RESULT_LAST_FAILURE"),
					listener.getLastArchiveFailure());
		}
		return result;
	}

	private static String joinStatistics(List<Statistic> statistics) {
		StringJoiner summary = new StringJoiner(", ");
		statistics.forEach(statistic -> summary.add(statistic.label() + " " + statistic.value()));
		return summary.toString();
	}

	private record Statistic(String label, String value) {
	}
}
