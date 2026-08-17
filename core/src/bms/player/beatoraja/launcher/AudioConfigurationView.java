package bms.player.beatoraja.launcher;

import java.net.URL;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.AudioConfig.FrequencyType;
import bms.player.beatoraja.AudioConfig.WasapiMode;
import bms.player.beatoraja.audio.PortAudioDriver;
import bms.player.beatoraja.audio.PortAudioDriver.AsioUnavailableException;
import bms.player.beatoraja.audio.PortAudioDriver.DeviceOption;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;

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
	private Slider keyvolume;
	@FXML
	private Slider bgvolume;
	@FXML
	private CheckBox normalizeVolume;
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
		audiosamplerate.getItems().setAll(null, 44100, 48000);
		wasapiMode.getItems().setAll(
				arg1.getString("WASAPI_SHARED"),
				arg1.getString("WASAPI_EXCLUSIVE"));

		audioFreqOption.getItems().setAll(FrequencyType.UNPROCESSED , FrequencyType.FREQUENCY);
		audioFastForward.getItems().setAll(FrequencyType.UNPROCESSED , FrequencyType.FREQUENCY);
	}

	public void update(AudioConfig config) {
		this.config = config;
		
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
		bgvolume.setDisable(enabled);
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
	}
}
