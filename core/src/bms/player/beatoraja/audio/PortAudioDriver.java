package bms.player.beatoraja.audio;

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Arrays;

import com.portaudio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.AudioConfig.WasapiMode;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.system.TimingDiagnostics;

/**
 * PortAudioドライバ
 * 
 * @author exch
 */
public class PortAudioDriver extends AbstractAudioDriver<PCM> implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(PortAudioDriver.class);

	private static DeviceInfo[] devices;
	private static DeviceOption[] deviceOptions;
	
	private PortAudioOutputStream stream;

	/**
	 * ミキサー入力
	 */
	private final MixerInput[] inputs;

	private long idcount;
	
	private boolean stop = false;
	
	private final float[] buffer;
	
	private final Thread mixer;

	public static DeviceInfo[] getDevices() {
		if(devices == null) {
			PortAudio.initialize();
			
			devices = new DeviceInfo[PortAudio.getDeviceCount()];
			for(int i = 0;i < devices.length;i++) {
				devices[i] = PortAudio.getDeviceInfo(i);
			}
		}
		return devices;
	}

	public static DeviceOption[] getDeviceOptions() {
		if (deviceOptions == null) {
			DeviceInfo[] availableDevices = getDevices();
			deviceOptions = new DeviceOption[availableDevices.length];
			for (int i = 0; i < availableDevices.length; i++) {
				DeviceInfo device = availableDevices[i];
				HostApiInfo hostApi = PortAudio.getHostApiInfo(device.hostApi);
				deviceOptions[i] = new DeviceOption(
						i,
						device.name,
						hostApi.type,
						hostApi.name,
						device.maxOutputChannels);
			}
		}
		return deviceOptions;
	}

	public static DeviceOption[] getDeviceOptions(DriverType driver) {
		if (driver != DriverType.ASIO) {
			return getDeviceOptions();
		}
		if (!isWindows()) {
			throw new AsioUnavailableException(AsioUnavailableReason.UNSUPPORTED_PLATFORM);
		}
		try {
			DeviceOption[] options = getDeviceOptions();
			return selectDeviceOptions(
					driver,
					options,
					true,
					hasHostApiType(PortAudio.HOST_API_TYPE_ASIO));
		} catch (AsioUnavailableException error) {
			if (error.reason() == AsioUnavailableReason.HOST_API_UNAVAILABLE) {
				logAsioLibraryError(error);
			}
			throw error;
		} catch (Throwable error) {
			logAsioLibraryError(error);
			throw new AsioUnavailableException(
					AsioUnavailableReason.HOST_API_UNAVAILABLE,
					error);
		}
	}

	private static void logAsioLibraryError(Throwable error) {
		logger.error(
				"ASIO host API unavailable: jportaudioLibrary={} portaudioLibrary={} java.library.path={}",
				jPortAudioLibraryName(),
				portAudioLibraryName(),
				System.getProperty("java.library.path", ""),
				error);
	}

	private static String jPortAudioLibraryName() {
		return System.getProperty("os.arch", "").contains("64")
				? "jportaudio_x64.dll"
				: "jportaudio_x86.dll";
	}

	private static String portAudioLibraryName() {
		return System.getProperty("os.arch", "").contains("64")
				? "portaudio_x64.dll"
				: "portaudio_x86.dll";
	}

	static DeviceOption[] selectDeviceOptions(
			DriverType driver,
			DeviceOption[] options,
			boolean windows,
			boolean asioHostApiAvailable) {
		if (driver != DriverType.ASIO) {
			return options;
		}
		if (!windows) {
			throw new AsioUnavailableException(AsioUnavailableReason.UNSUPPORTED_PLATFORM);
		}
		if (!asioHostApiAvailable) {
			throw new AsioUnavailableException(AsioUnavailableReason.HOST_API_UNAVAILABLE);
		}
		DeviceOption[] asioOptions = Arrays.stream(options)
				.filter(DeviceOption::isAsio)
				.filter(DeviceOption::hasOutputChannels)
				.toArray(DeviceOption[]::new);
		if (asioOptions.length == 0) {
			throw new AsioUnavailableException(AsioUnavailableReason.NO_OUTPUT_DEVICE);
		}
		return asioOptions;
	}

	private static boolean hasHostApiType(int hostApiType) {
		for (int index = 0; index < PortAudio.getHostApiCount(); index++) {
			if (PortAudio.getHostApiInfo(index).type == hostApiType) {
				return true;
			}
		}
		return false;
	}

	public static DeviceOption findDeviceOption(
			DeviceOption[] options,
			String name,
			int hostApiType) {
		if (options.length == 0) {
			throw new IllegalStateException("PortAudio output device is not available");
		}
		if (name != null && hostApiType >= 0) {
			for (DeviceOption option : options) {
				if (name.equals(option.name()) && hostApiType == option.hostApiType()) {
					return option;
				}
			}
		}
		if (name != null) {
			for (DeviceOption option : options) {
				if (name.equals(option.name())) {
					return option;
				}
			}
		}
		return options[0];
	}

	public static boolean isWasapiModeSelectable(
			DriverType driver,
			DeviceOption option,
			boolean windows) {
		return driver == DriverType.PortAudio
				&& option != null
				&& option.isWasapi()
				&& windows;
	}

	public static boolean shouldUseWasapiExclusive(
			WasapiMode mode,
			DeviceOption option,
			boolean windows) {
		return mode == WasapiMode.EXCLUSIVE
				&& isWasapiModeSelectable(DriverType.PortAudio, option, windows);
	}

	public static boolean isWindows() {
		String osName = System.getProperty("os.name", "");
		return osName.toLowerCase().contains("win");
	}

	public PortAudioDriver(Config config) {
		super(config.getSongResourceGen());
		AudioConfig audioConfig = config.getAudioConfig();
		DeviceOption deviceOption = findDeviceOption(
				getDeviceOptions(audioConfig.getDriver()),
				audioConfig.getDriverName(),
				audioConfig.getDriverHostApi());
		validateSelectedDevice(audioConfig.getDriver(), deviceOption);
		int deviceId = deviceOption.index();
		DeviceInfo deviceInfo = getDevices()[deviceId];
		
		setSampleRate(audioConfig.getSampleRate() <= 0
				? (int) deviceInfo.defaultSampleRate
				: audioConfig.getSampleRate());
		channels = 2;
//		System.out.println( "  deviceId    = " + deviceId );
//		System.out.println( "  sampleRate  = " + sampleRate );
//		System.out.println( "  device name = " + deviceInfo.name );

		StreamParameters streamParameters = new StreamParameters();
		streamParameters.channelCount = channels;
		streamParameters.device = deviceId;
		int framesPerBuffer = audioConfig.getDeviceBufferSize();
		streamParameters.suggestedLatency = ((double)framesPerBuffer) / getSampleRate();
		logger.info(
				"PortAudio output device selected mode={} device={} hostApi={} sampleRate={} framesPerBuffer={}",
				audioConfig.getDriver(),
				deviceOption.name(),
				deviceOption.hostApiName(),
				getSampleRate(),
				framesPerBuffer);
//		System.out.println( "  suggestedLatency = " + streamParameters.suggestedLatency );

		int flags = 0;
		
		// JPortAudio cannot pass WASAPI host-specific stream information. Keep it
		// for shared/non-WASAPI output and use JNA only for explicit exclusive mode.
		if (shouldUseWasapiExclusive(audioConfig.getWasapiMode(), deviceOption, isWindows())) {
			stream = new WasapiExclusivePortAudioStream(
					deviceId,
					channels,
					getSampleRate(),
					framesPerBuffer,
					streamParameters.suggestedLatency);
		} else {
			stream = new JPortAudioOutputStream(PortAudio.openStream(
					null,
					streamParameters,
					getSampleRate(),
					framesPerBuffer,
					flags));
		}

		try {
			stream.start();
		} catch (RuntimeException | Error error) {
			stream.close();
			stream = null;
			throw error;
		}
		TimingDiagnostics.audioConfigured(
				audioConfig.getDriver(),
				deviceOption.hostApiName(),
				getSampleRate(),
				framesPerBuffer
		);

		mixer = new Thread(this);
		buffer = new float[framesPerBuffer * channels];
		inputs = new MixerInput[audioConfig.getDeviceSimultaneousSources()];
		for (int i = 0; i < inputs.length; i++) {
			inputs[i] = new MixerInput();
		}
		mixer.start();
	}

	static void validateSelectedDevice(DriverType driver, DeviceOption option) {
		if (driver == DriverType.ASIO
				&& (option == null || !option.isAsio() || !option.hasOutputChannels())) {
			throw new AsioUnavailableException(AsioUnavailableReason.INVALID_DEVICE);
		}
	}

	@Override
	protected PCM getKeySound(Path p) {
		return PCM.load(p.toString(), this);
	}

	@Override
	protected PCM getKeySound(SongResource resource) {
		return PCM.load(resource, this);
	}

	@Override
	protected PCM getKeySound(PCM pcm) {
		return pcm;
	}

	@Override
	protected void play(PCM pcm, int channel, float volume, float pitch) {
		put(pcm, channel, volume, pitch, false);
	}

	@Override
	protected void play(AudioElement<PCM> id, float volume, boolean loop) {
		id.id = put(id.audio, -1, volume, 1.0f, loop);
	}

	@Override
	protected void setVolume(AudioElement<PCM> id, float volume) {
		for (MixerInput input : inputs) {
			if (input.id == id.id) {
				input.volume = volume;
				break;
			}
		}
	}

	@Override
	protected void disposeKeySound(PCM pcm) {
		stop(pcm);
	}

	private long put(PCM pcm, int channel, float volume, float pitch, boolean loop) {
		long timingStarted = TimingDiagnostics.start();
		synchronized (inputs) {
			for (MixerInput input : inputs) {
				if (input.pos == -1) {
					input.pcm = pcm;
					input.volume = volume;
					input.pitch = pitch;
					input.loop = loop;
					input.id = idcount++;
					input.channel = channel;
					input.queuedAtNanos = TimingDiagnostics.start();
					input.pos = 0;
					TimingDiagnostics.finish(
							TimingDiagnostics.Metric.PORTAUDIO_ENQUEUE,
							timingStarted
					);
					return input.id;
				}
			}
		}
		TimingDiagnostics.increment(
				TimingDiagnostics.Counter.PORTAUDIO_ENQUEUE_REJECTED
		);
		TimingDiagnostics.finish(
				TimingDiagnostics.Metric.PORTAUDIO_ENQUEUE,
				timingStarted
		);
		return -1;
	}

	@Override
	protected boolean isPlaying(PCM id) {
		synchronized (inputs) {
			for (MixerInput input : inputs) {
				if (input.pcm == id) {
					return input.pos != -1;
				}
			}				
		}
		return false;
	}


	@Override
	protected void stop(PCM id) {
		synchronized (inputs) {
			for (MixerInput input : inputs) {
				if (input.pcm == id) {
					input.pos = -1;
				}
			}				
		}
	}

	@Override
	protected void stop(PCM id, int channel) {
		synchronized (inputs) {
			for (MixerInput input : inputs) {
				if (input.pcm == id && input.channel == channel) {
					input.pos = -1;
				}
			}
		}
	}

	@Override
	protected void setVolume(PCM id, int channel, float volume) {
		synchronized (inputs) {
			for (MixerInput input : inputs) {
				if (input.pcm == id && input.channel == channel) {
					input.volume = volume;
				}
			}
		}
	}

	public void run() {
		while(!stop) {
			long mixStarted = TimingDiagnostics.start();
			final float gpitch = getGlobalPitch();
			synchronized (inputs) {
				if (mixStarted != 0) {
					for (MixerInput input : inputs) {
						if (input.pos != -1 && input.queuedAtNanos != 0) {
							TimingDiagnostics.finish(
									TimingDiagnostics.Metric.PORTAUDIO_ENQUEUE_TO_MIX,
									input.queuedAtNanos
							);
							input.queuedAtNanos = 0;
						}
					}
				}
				for (int i = 0; i < buffer.length; i+=2) {
					float wav_l = 0;
					float wav_r = 0;
					for (MixerInput input : inputs) {
						if (input.pos != -1) {
							if(input.pcm instanceof FloatPCM) {
								final float[] sample = (float[]) input.pcm.sample;
								wav_l += sample[input.pos + input.pcm.start] * input.volume;
								wav_r += sample[input.pos+1 + input.pcm.start] * input.volume;																
							} else if(input.pcm instanceof ShortDirectPCM) {
								final ByteBuffer sample = (ByteBuffer) input.pcm.sample;
								wav_l += ((float) sample.getShort((input.pos + input.pcm.start) * 2)) * input.volume / Short.MAX_VALUE;
								wav_r += ((float) sample.getShort((input.pos+1 + input.pcm.start) * 2)) * input.volume / Short.MAX_VALUE;																
							} else if(input.pcm instanceof ShortPCM) {
								final short[] sample = (short[]) input.pcm.sample;
								wav_l += ((float) sample[input.pos + input.pcm.start]) * input.volume / Short.MAX_VALUE;
								wav_r += ((float) sample[input.pos+1 + input.pcm.start]) * input.volume / Short.MAX_VALUE;																
							} else if(input.pcm instanceof BytePCM) {
								final byte[] sample = (byte[]) input.pcm.sample;
								wav_l += ((float) (sample[input.pos + input.pcm.start] - 128)) * input.volume / Byte.MAX_VALUE;
								wav_r += ((float) (sample[input.pos+1 + input.pcm.start] - 128)) * input.volume / Byte.MAX_VALUE;																
							}
							input.posf += gpitch * input.pitch;
							int inc = (int)input.posf;
							if (inc > 0) {
								input.pos += 2 * inc;
								input.posf -= (float)inc;
							}
							if (input.pos >= input.pcm.len) {
								input.pos = input.loop ? 0 : -1;
							}
						}
					}
					buffer[i] = wav_l;
					buffer[i+1] = wav_r;
				}						
			}
			TimingDiagnostics.finish(
					TimingDiagnostics.Metric.PORTAUDIO_MIX,
					mixStarted
			);
			
			long writeStarted = TimingDiagnostics.start();
			try {
				if (stream.write(buffer, buffer.length / 2)) {
					TimingDiagnostics.increment(
							TimingDiagnostics.Counter.PORTAUDIO_UNDERFLOW
					);
				}
			} catch(Throwable e) {
				TimingDiagnostics.increment(
						TimingDiagnostics.Counter.PORTAUDIO_WRITE_ERROR
				);
				e.printStackTrace();
			} finally {
				TimingDiagnostics.finish(
						TimingDiagnostics.Metric.PORTAUDIO_WRITE,
						writeStarted
				);
			}
			
		}
	}		

	public void dispose() {
		super.dispose();
		if(stream != null) {
			stop = true;
			long l = System.currentTimeMillis();
			while(mixer.isAlive() && System.currentTimeMillis() - l < 1000);
			stream.stop();
			stream.close();
			
			stream = null;

			PortAudio.terminate();
//			System.out.println( "JPortAudio test complete." );			
		}
	}

	static class MixerInput {
		public PCM pcm;
		public float volume;
		public float pitch;
		public int pos = -1;
		public float posf = 0.0f;
		public boolean loop;
		public long id;
		public int channel = -1;
		public long queuedAtNanos;
	}

	public record DeviceOption(
			int index,
			String name,
			int hostApiType,
			String hostApiName,
			int maxOutputChannels) {
		public DeviceOption(int index, String name, int hostApiType, String hostApiName) {
			this(index, name, hostApiType, hostApiName, 2);
		}

		public boolean isWasapi() {
			return hostApiType == PortAudio.HOST_API_TYPE_WASAPI;
		}

		public boolean isAsio() {
			return hostApiType == PortAudio.HOST_API_TYPE_ASIO;
		}

		public boolean hasOutputChannels() {
			return maxOutputChannels > 0;
		}

		@Override
		public String toString() {
			return hostApiName + ": " + name;
		}
	}

	public enum AsioUnavailableReason {
		UNSUPPORTED_PLATFORM,
		HOST_API_UNAVAILABLE,
		NO_OUTPUT_DEVICE,
		INVALID_DEVICE,
	}

	public static final class AsioUnavailableException extends IllegalStateException {
		private final AsioUnavailableReason reason;

		AsioUnavailableException(AsioUnavailableReason reason) {
			this(reason, null);
		}

		AsioUnavailableException(AsioUnavailableReason reason, Throwable cause) {
			super(switch (reason) {
			case UNSUPPORTED_PLATFORM -> "ASIO output is available only on Windows";
			case HOST_API_UNAVAILABLE -> "The loaded PortAudio DLL does not provide the ASIO Host API";
			case NO_OUTPUT_DEVICE -> "No ASIO output device is available; install or connect an ASIO driver";
			case INVALID_DEVICE -> "The selected output device does not belong to the ASIO Host API";
			}, cause);
			this.reason = reason;
		}

		public AsioUnavailableReason reason() {
			return reason;
		}
	}

	private static final class JPortAudioOutputStream implements PortAudioOutputStream {
		private final BlockingStream stream;

		private JPortAudioOutputStream(BlockingStream stream) {
			this.stream = stream;
		}

		@Override
		public void start() {
			stream.start();
		}

		@Override
		public boolean write(float[] buffer, int frames) {
			return stream.write(buffer, frames);
		}

		@Override
		public void stop() {
			stream.stop();
		}

		@Override
		public void close() {
			stream.close();
		}
	}
}
