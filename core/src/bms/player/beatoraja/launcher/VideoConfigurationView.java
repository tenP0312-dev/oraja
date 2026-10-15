package bms.player.beatoraja.launcher;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.MainLoader;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.Resolution;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.application.Platform;
import javafx.util.StringConverter;
import bms.player.beatoraja.camera.FFmpegCameraProcess;
import bms.player.beatoraja.camera.HandCameraDeviceResolver;
import bms.player.beatoraja.camera.HandCameraSettings;
import javafx.util.Pair;

import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;

public class VideoConfigurationView implements Initializable {
	@FXML
	private ComboBox<Resolution> resolution;
	@FXML
	private ComboBox<Config.DisplayMode> displayMode;
	@FXML
	private ComboBox<String> bgaOp;
	@FXML
	private ComboBox<String> bgaExpand;
    @FXML private ComboBox<bms.player.beatoraja.play.bga.BgaQualityProfile.Mode> bgaQuality;
    @FXML private CheckBox bgaAsyncPipeline;
    @FXML private CheckBox bgaPerformanceStats;
    @FXML private CheckBox handCameraEnabled;
    @FXML private ComboBox<FFmpegCameraProcess.Device> handCameraDevice;
    @FXML private Button handCameraRefresh;
    @FXML private Label handCameraStatus;
    @FXML private Spinner<Integer> handCameraWidth, handCameraHeight, handCameraFps;
    @FXML private Spinner<Integer> handCameraOffsetX, handCameraOffsetY, handCameraZoom, handCameraFocusX, handCameraFocusY;
    @FXML private CheckBox handCameraMirror, handCameraBgaFrame;
    private FFmpegCameraProcess.Device savedHandCamera;
    private ResourceBundle resources;

	@FXML
	private CheckBox vSync;

	@FXML
	private Spinner<Integer> maxFps;
	@FXML
	private Spinner<Integer> missLayerTime;

	@FXML
	private ComboBox<String> monitor;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.resources = resources;
        handCameraDevice.setConverter(new StringConverter<>() {
            public String toString(FFmpegCameraProcess.Device device) { return device == null ? "" : device.displayName(); }
            public FFmpegCameraProcess.Device fromString(String text) {
                var candidates = new java.util.ArrayList<FFmpegCameraProcess.Device>();
                candidates.add(handCameraDevice.getValue());
                candidates.addAll(handCameraDevice.getItems());
                candidates.add(savedHandCamera);
                return HandCameraDeviceResolver.select(text, candidates);
            }
        });
		updateResolutions();
        bgaQuality.getItems().setAll(bms.player.beatoraja.play.bga.BgaQualityProfile.Mode.values());

		displayMode.getItems().setAll(Config.DisplayMode.values());
		monitor.getItems().setAll(Arrays.stream(Lwjgl3ApplicationConfiguration.getMonitors()).map(monitor -> String.format("%s [%s, %s]", monitor.name, Integer.toString(monitor.virtualX), Integer.toString(monitor.virtualY))).toList());
    }

    public void update(Config config) {
        handCameraEnabled.setSelected(config.isHandCameraEnabled());
        // Show the remembered camera by name; the game looks it up again by name/ID when it opens it.
        savedHandCamera = HandCameraDeviceResolver.saved(HandCameraSettings.from(config));
        handCameraDevice.setValue(savedHandCamera);
        handCameraWidth.getValueFactory().setValue(config.getHandCameraWidth());
        handCameraHeight.getValueFactory().setValue(config.getHandCameraHeight());
        handCameraFps.getValueFactory().setValue(config.getHandCameraFps());
        handCameraMirror.setSelected(config.isHandCameraMirror());
        handCameraBgaFrame.setSelected(config.isHandCameraBgaFrame());
        var view = bms.player.beatoraja.camera.HandCameraView.from(config);
        handCameraOffsetX.getValueFactory().setValue(view.offsetX());
        handCameraOffsetY.getValueFactory().setValue(view.offsetY());
        handCameraZoom.getValueFactory().setValue(view.zoom());
        handCameraFocusX.getValueFactory().setValue(view.focusX());
        handCameraFocusY.getValueFactory().setValue(view.focusY());
		displayMode.setValue(config.getDisplaymode());
		resolution.setValue(config.getResolution());
		vSync.setSelected(config.isVsync());
		monitor.setValue(config.getMonitorName());
		bgaOp.getSelectionModel().select(config.getBga());
		bgaExpand.getSelectionModel().select(config.getBgaExpand());
        bgaQuality.setValue(config.getBgaQualityMode());
        bgaAsyncPipeline.setSelected(config.isBgaAsyncPipeline());
        bgaPerformanceStats.setSelected(config.isBgaShowPerformanceStats());
		maxFps.getValueFactory().setValue(config.getMaxFramePerSecond());
	}

	public void updatePlayer(PlayerConfig player) {
		missLayerTime.getValueFactory().setValue(player.getMisslayerDuration());
	}

	public void commit(Config config) {
        config.setHandCameraEnabled(handCameraEnabled.isSelected());
        HandCameraDeviceResolver.remember(config,
                handCameraDevice.getConverter().fromString(handCameraDevice.getEditor().getText()));
        config.setHandCameraWidth(handCameraWidth.getValue());
        config.setHandCameraHeight(handCameraHeight.getValue());
        config.setHandCameraFps(handCameraFps.getValue());
        config.setHandCameraMirror(handCameraMirror.isSelected());
        config.setHandCameraBgaFrame(handCameraBgaFrame.isSelected());
        config.setHandCameraView(new bms.player.beatoraja.camera.HandCameraView(handCameraOffsetX.getValue(),
                handCameraOffsetY.getValue(), handCameraZoom.getValue(), handCameraFocusX.getValue(),
                handCameraFocusY.getValue()));
		config.setResolution(resolution.getValue());
		config.setDisplaymode(displayMode.getValue());
		config.setVsync(vSync.isSelected());
		config.setMonitorName(monitor.getValue());
		config.setBga(bgaOp.getSelectionModel().getSelectedIndex());
		config.setBgaExpand(bgaExpand.getSelectionModel().getSelectedIndex());
        config.setBgaQualityMode(bgaQuality.getValue());
        config.setBgaAsyncPipeline(bgaAsyncPipeline.isSelected());
        config.setBgaShowPerformanceStats(bgaPerformanceStats.isSelected());
		config.setMaxFramePerSecond(maxFps.getValue());
	}

	public void commitPlayer(PlayerConfig player) {
		player.setMisslayerDuration(missLayerTime.getValue());
	}

	@FXML
	public void refreshHandCameras() {
        var selected = handCameraDevice.getConverter().fromString(handCameraDevice.getEditor().getText());
        handCameraRefresh.setDisable(true);
        handCameraDevice.setDisable(true);
        handCameraStatus.setText(resources.getString("HAND_CAMERA_LOADING"));
        Thread thread = new Thread(() -> {
            try {
                var devices = FFmpegCameraProcess.devices();
                Platform.runLater(() -> {
                    handCameraDevice.getItems().setAll(devices);
                    // Enumeration order and macOS indexes change; match by identity, not by the old value.
                    handCameraDevice.setValue(HandCameraDeviceResolver.refreshed(selected, devices));
                    handCameraStatus.setText(resources.getString(devices.isEmpty()
                            ? "HAND_CAMERA_EMPTY" : "HAND_CAMERA_READY"));
                    handCameraRefresh.setDisable(false);
                    handCameraDevice.setDisable(false);
                });
            } catch (Exception | LinkageError error) {
                Platform.runLater(() -> {
                    handCameraStatus.setText(resources.getString("HAND_CAMERA_FAILED"));
                    handCameraRefresh.setDisable(false);
                    handCameraDevice.setDisable(false);
                });
            }
        }, "hand-camera-device-list");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
	public void updateResolutions() {
		Resolution oldValue = resolution.getValue();
		resolution.getItems().clear();

		if (displayMode.getValue() == Config.DisplayMode.FULLSCREEN) {
			Graphics.DisplayMode[] displays = MainLoader.getAvailableDisplayMode();
			for(Resolution r : Resolution.values()) {
				for(Graphics.DisplayMode display : displays) {
					if(display.width == r.width && display.height == r.height) {
						resolution.getItems().add(r);
						break;
					}
				}
			}
		} else {
			Graphics.DisplayMode display = MainLoader.getDesktopDisplayMode();
			for(Resolution r : Resolution.values()) {
				if (r.width <= display.width && r.height <= display.height) {
					resolution.getItems().add(r);
				}
			}
		}
		resolution.setValue(resolution.getItems().contains(oldValue)
				? oldValue : resolution.getItems().get(resolution.getItems().size() - 1));
	}
}
