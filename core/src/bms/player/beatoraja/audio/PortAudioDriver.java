package bms.player.beatoraja.audio;

import java.nio.ByteBuffer;
import java.nio.file.*;

import com.portaudio.*;
import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.AudioConfig.WasapiMode;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.song.SongResource;

/**
 * PortAudioドライバ
 * 
 * @author exch
 */
public class PortAudioDriver extends AbstractAudioDriver<PCM> implements Runnable {

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
						hostApi.name);
			}
		}
		return deviceOptions;
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
				getDeviceOptions(),
				audioConfig.getDriverName(),
				audioConfig.getDriverHostApi());
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

		mixer = new Thread(this);
		buffer = new float[framesPerBuffer * channels];
		inputs = new MixerInput[audioConfig.getDeviceSimultaneousSources()];
		for (int i = 0; i < inputs.length; i++) {
			inputs[i] = new MixerInput();
		}
		mixer.start();
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
		synchronized (inputs) {
			for (MixerInput input : inputs) {
				if (input.pos == -1) {
					input.pcm = pcm;
					input.volume = volume;
					input.pitch = pitch;
					input.loop = loop;
					input.id = idcount++;
					input.channel = channel;
					input.pos = 0;
					return input.id;
				}
			}
		}
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
			final float gpitch = getGlobalPitch();
			synchronized (inputs) {
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
			
			try {
				stream.write( buffer, buffer.length / 2);
			} catch(Throwable e) {
				e.printStackTrace();
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
	}

	public record DeviceOption(
			int index,
			String name,
			int hostApiType,
			String hostApiName) {
		public boolean isWasapi() {
			return hostApiType == PortAudio.HOST_API_TYPE_WASAPI;
		}

		@Override
		public String toString() {
			return hostApiName + ": " + name;
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
