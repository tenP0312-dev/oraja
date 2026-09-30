package bms.player.beatoraja.launcher;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.AudioConfig.FrequencyType;
import bms.player.beatoraja.AudioConfig.WasapiMode;
import bms.player.beatoraja.AudioConfig.EqualizerMode;
import bms.player.beatoraja.audio.PortAudioDriver;
import bms.player.beatoraja.audio.PortAudioDriver.AsioUnavailableException;
import bms.player.beatoraja.audio.PortAudioDriver.DeviceOption;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Orientation;
import javafx.util.StringConverter;

public class AudioConfigurationView implements Initializable {
	private static final Logger logger = LoggerFactory.getLogger(AudioConfigurationView.class);

	@FXML
	private ComboBox<DriverType> audio;
	@FXML
	private ComboBox<DeviceOption> audioname;
	@FXML
	private ComboBox<String> wasapiMode;
	@FXML
	private Spinner<Integer> audiobuffer;
	@FXML
	private Spinner<Integer> audiosim;
	@FXML
	private ComboBox<Integer> audiosamplerate;
	@FXML
	private Slider systemvolume;
	@FXML
	private Spinner<Double> systemVolumeSpinner;
	@FXML
	private Slider keyvolume;
	@FXML
	private Spinner<Double> keyVolumeSpinner;
	@FXML
	private Slider bgvolume;
	@FXML
	private Spinner<Double> bgVolumeSpinner;
	@FXML
	private CheckBox normalizeVolume;
	@FXML private ComboBox<EqualizerMode> equalizerMode;
	@FXML private FlowPane equalizerBands;
	@FXML private VBox equalizerPanel;
	@FXML private Label equalizerTitle;
	@FXML private Label equalizerPreampLabel;
	@FXML private Label equalizerNotice;
	@FXML private NumericSpinner<Double> equalizerPreamp;
	private final List<NumericSpinner<Double>> equalizerSpinners = new ArrayList<>();
	private AudioConfig equalizerDraft = new AudioConfig();
	private EqualizerMode editedEqualizerMode = EqualizerMode.OFF;
	@FXML
	private ComboBox<FrequencyType> audioFreqOption;
	@FXML
	private ComboBox<FrequencyType> audioFastForward;
	@FXML
	private CheckBox loopResultSound;
	@FXML
	private CheckBox loopCourseResultSound;
	
	private AudioConfig config;
	private ResourceBundle resources;

	public void initialize(URL arg0, ResourceBundle arg1) {
		audio.getItems().setAll(DriverType.OpenAL , DriverType.PortAudio);
		if (PortAudioDriver.isWindows()) {
			audio.getItems().add(DriverType.ASIO);
		}
		resources = arg1;
		equalizerMode.getItems().setAll(EqualizerMode.values());
		equalizerMode.setConverter(new StringConverter<EqualizerMode>() {
			@Override public String toString(EqualizerMode mode) {
				return mode == null ? "" : resources.getString("EQ_MODE_" + mode.name());
			}
			@Override public EqualizerMode fromString(String text) { return EqualizerMode.OFF; }
		});
		equalizerPreamp.setValueFactory(new DoubleSpinnerValueFactory(-24, 0, 0, 0.5));
		equalizerTitle.setLabelFor(equalizerMode);
		equalizerPreampLabel.setLabelFor(equalizerPreamp);
		audiosamplerate.getItems().setAll(null, 44100, 48000);
		wasapiMode.getItems().setAll(
				arg1.getString("WASAPI_SHARED"),
				arg1.getString("WASAPI_EXCLUSIVE"));

		audioFreqOption.getItems().setAll(FrequencyType.UNPROCESSED , FrequencyType.FREQUENCY);
		audioFastForward.getItems().setAll(FrequencyType.UNPROCESSED , FrequencyType.FREQUENCY);
		bindSliderToSpinner(systemvolume, systemVolumeSpinner);
		bindSliderToSpinner(keyvolume, keyVolumeSpinner);
		bindSliderToSpinner(bgvolume, bgVolumeSpinner);
	}

	public void update(AudioConfig config) {
		this.config = config;
		equalizerDraft = new AudioConfig();
		equalizerDraft.setSwitchEqualizerGains(config.getSwitchEqualizerGains());
		equalizerDraft.setLr2EqualizerGains(config.getLr2EqualizerGains());
		editedEqualizerMode = EqualizerMode.OFF;
		equalizerSpinners.clear();
		equalizerMode.setValue(config.getEqualizerMode());
		equalizerPreamp.getValueFactory().setValue(config.getEqualizerPreamp());
		updateEqualizer();
		
		audio.setValue(config.getDriver());
		audiobuffer.getValueFactory().setValue(config.getDeviceBufferSize());
		audiosim.getValueFactory().setValue(config.getDeviceSimultaneousSources());
		audiosamplerate.setValue(config.getSampleRate() > 0 ? config.getSampleRate() : null);
		audioFreqOption.setValue(config.getFreqOption());
		audioFastForward.setValue(config.getFastForward());
		wasapiMode.getSelectionModel().select(
				config.getWasapiMode() == WasapiMode.EXCLUSIVE ? 1 : 0);
		systemvolume.setValue((double)config.getSystemvolume());
		keyvolume.setValue((double)config.getKeyvolume());
		bgvolume.setValue((double)config.getBgvolume());
		normalizeVolume.setSelected(config.isNormalizeVolume());
		loopResultSound.setSelected(config.isLoopResultSound());
		loopCourseResultSound.setSelected(config.isLoopCourseResultSound());

		updateAudioDriver();
		updateNormalizeVolume();
	}
	
	public void commit() {
		storeEqualizerDraft();
		config.setEqualizerMode(equalizerMode.getValue());
		config.setSwitchEqualizerGains(equalizerDraft.getSwitchEqualizerGains());
		config.setLr2EqualizerGains(equalizerDraft.getLr2EqualizerGains());
		commitEqualizerSpinner(equalizerPreamp);
		config.setEqualizerPreamp(equalizerPreamp.getValue());
		config.setDriver(audio.getValue());
		DeviceOption selectedDevice = audioname.getValue();
		if (selectedDevice != null) {
			config.setDriverName(selectedDevice.name());
			config.setDriverHostApi(selectedDevice.hostApiType());
		}
		config.setWasapiMode(
				wasapiMode.getSelectionModel().getSelectedIndex() == 1
						? WasapiMode.EXCLUSIVE
						: WasapiMode.SHARED);
		config.setDeviceBufferSize(audiobuffer.getValue());
		config.setDeviceSimultaneousSources(audiosim.getValue());
		config.setSampleRate(audiosamplerate.getValue() != null ? audiosamplerate.getValue() : 0);
		config.setFreqOption(audioFreqOption.getValue());
		config.setFastForward(audioFastForward.getValue());
		config.setSystemvolume((float) systemvolume.getValue());
		config.setKeyvolume((float) keyvolume.getValue());
		config.setBgvolume((float) bgvolume.getValue());
		config.setNormalizeVolume(normalizeVolume.isSelected());
		config.setLoopResultSound(loopResultSound.isSelected());
		config.setLoopCourseResultSound(loopCourseResultSound.isSelected());
	}
	
	@FXML
	public void updateNormalizeVolume() {
		boolean enabled = normalizeVolume.isSelected();
		keyvolume.setDisable(enabled);
		keyVolumeSpinner.setDisable(enabled);
		bgvolume.setDisable(enabled);
		bgVolumeSpinner.setDisable(enabled);
	}

    @FXML
	public void updateAudioDriver() {
		switch(audio.getValue()) {
		case OpenAL:
			audioname.setDisable(true);
			audioname.getItems().clear();
			audiobuffer.setDisable(false);
			audiosim.setDisable(false);
			updateWasapiModeAvailability();
			break;
		case PortAudio:
		case ASIO:
			try {
				DeviceOption[] devices = PortAudioDriver.getDeviceOptions(audio.getValue());
				if(devices.length == 0) {
					throw new RuntimeException("ドライバが見つかりません");
				}
				audioname.setPromptText("");
				audioname.getItems().setAll(devices);
				audioname.setValue(PortAudioDriver.findDeviceOption(
						devices,
						config.getDriverName(),
						config.getDriverHostApi()));
				audioname.setDisable(false);
				audiobuffer.setDisable(false);
				audiosim.setDisable(false);
				updateWasapiModeAvailability();
//				PortAudio.terminate();
			} catch (AsioUnavailableException e) {
				logger.error("ASIOは選択できません : {}", e.getMessage());
				audioname.getItems().clear();
				audioname.setValue(null);
				audioname.setPromptText(resources.getString(switch (e.reason()) {
				case UNSUPPORTED_PLATFORM -> "ASIO_UNSUPPORTED_PLATFORM";
				case HOST_API_UNAVAILABLE -> "ASIO_HOST_API_UNAVAILABLE";
				case NO_OUTPUT_DEVICE -> "ASIO_DEVICE_UNAVAILABLE";
				case INVALID_DEVICE -> "ASIO_INVALID_DEVICE";
				}));
				audioname.setDisable(true);
				audiobuffer.setDisable(false);
				audiosim.setDisable(false);
				updateWasapiModeAvailability();
			} catch(Throwable e) {
				logger.error("PortAudioは選択できません : {}", e.getMessage());
				audio.setValue(DriverType.OpenAL);
			}
			break;
		}
	}

	@FXML
	public void updateWasapiModeAvailability() {
		wasapiMode.setDisable(!PortAudioDriver.isWasapiModeSelectable(
				audio.getValue(),
				audioname.getValue(),
				PortAudioDriver.isWindows()));
		updateEqualizerAvailability();
	}

	private void storeEqualizerDraft() {
		double[] gains = new double[equalizerSpinners.size()];
		for (int i = 0; i < gains.length; i++) {
			commitEqualizerSpinner(equalizerSpinners.get(i));
			gains[i] = equalizerSpinners.get(i).getValue();
		}
		if (editedEqualizerMode == EqualizerMode.SWITCH) equalizerDraft.setSwitchEqualizerGains(gains);
		if (editedEqualizerMode == EqualizerMode.LR2) equalizerDraft.setLr2EqualizerGains(gains);
	}

	private static void commitEqualizerSpinner(NumericSpinner<Double> spinner) {
		if (spinner.getValue() == null || !Double.isFinite(spinner.getValue())) spinner.getValueFactory().setValue(0.0);
		try {
			Double value = spinner.getValueFactory().getConverter().fromString(spinner.getEditor().getText());
			if (value == null || !Double.isFinite(value)) {
				spinner.getEditor().setText(spinner.getValue().toString());
			} else {
				spinner.getValueFactory().setValue(value);
			}
		} catch (NumberFormatException e) {
			spinner.getEditor().setText(spinner.getValue().toString());
		}
	}

	@FXML public void updateEqualizer() {
		storeEqualizerDraft();
		editedEqualizerMode = equalizerMode.getValue() != null ? equalizerMode.getValue() : EqualizerMode.OFF;
		equalizerSpinners.clear();
		equalizerBands.getChildren().clear();
		double[] frequencies = editedEqualizerMode.getFrequencies();
		double[] gains = editedEqualizerMode == EqualizerMode.SWITCH
				? equalizerDraft.getSwitchEqualizerGains() : equalizerDraft.getLr2EqualizerGains();
		for (int i = 0; i < frequencies.length; i++) {
			String frequency = frequencies[i] >= 1000
					? (frequencies[i] / 1000) + " kHz" : (int) frequencies[i] + " Hz";
			Label label = new Label(frequency);
			Slider slider = new Slider(-12, 12, gains[i]);
			slider.setOrientation(Orientation.VERTICAL);
			slider.setPrefHeight(120);
			slider.setBlockIncrement(0.5);
			slider.setMajorTickUnit(6);
			slider.setMinorTickCount(11);
			slider.setSnapToTicks(true);
			slider.setShowTickMarks(true);
			slider.setAccessibleText(frequency + " " + resources.getString("EQ_GAIN"));
			NumericSpinner<Double> spinner = new NumericSpinner<>();
			spinner.setValueFactory(new DoubleSpinnerValueFactory(-12, 12, gains[i], 0.5));
			spinner.setEditable(true);
			spinner.setPrefWidth(80);
			spinner.setMaxWidth(80);
			spinner.setAccessibleText(frequency + " " + resources.getString("EQ_GAIN"));
			label.setLabelFor(spinner);
			bindSliderToSpinner(slider, spinner);
			VBox column = new VBox(5, label, slider, spinner);
			column.setAlignment(javafx.geometry.Pos.CENTER);
			equalizerBands.getChildren().add(column);
			equalizerSpinners.add(spinner);
		}
		updateEqualizerAvailability();
	}

	@FXML public void resetEqualizer() {
		for (NumericSpinner<Double> spinner : equalizerSpinners) spinner.getValueFactory().setValue(0.0);
	}

	private void updateEqualizerAvailability() {
		if (equalizerNotice == null) return;
		boolean supported = audio.getValue() == DriverType.PortAudio || audio.getValue() == DriverType.ASIO;
		equalizerNotice.setText(resources.getString(supported ? "EQ_DESCRIPTION" : "EQ_OPENAL_UNSUPPORTED"));
		equalizerBands.setDisable(!supported || editedEqualizerMode == EqualizerMode.OFF);
		equalizerPreamp.setDisable(!supported || editedEqualizerMode == EqualizerMode.OFF);
	}

	private static void bindSliderToSpinner(Slider slider, Spinner<Double> spinner) {
		Bindings.bindBidirectional(
				slider.valueProperty().asObject(),
				spinner.getValueFactory().valueProperty()
		);
	}
}
