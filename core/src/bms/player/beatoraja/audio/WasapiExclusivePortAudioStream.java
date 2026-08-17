package bms.player.beatoraja.audio;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

/**
 * PortAudio blocking output opened with the Windows WASAPI exclusive flag.
 *
 * <p>The bundled JPortAudio binding cannot populate
 * {@code PaStreamParameters.hostApiSpecificStreamInfo}. JNA is already a
 * client dependency, so this class calls the same bundled PortAudio 19.6 DLL
 * directly only for the opt-in exclusive path.</p>
 */
public final class WasapiExclusivePortAudioStream implements PortAudioOutputStream {
	static final int PA_FLOAT_32 = 1;
	static final int PA_WASAPI = 13;
	static final int PA_WIN_WASAPI_EXCLUSIVE = 1;
	static final int PA_OUTPUT_UNDERFLOWED = -9980;

	private final NativePortAudio portAudio;
	private Pointer stream;

	WasapiExclusivePortAudioStream(
			int device,
			int channels,
			double sampleRate,
			int framesPerBuffer,
			double suggestedLatency) {
		this(loadNativePortAudio(), device, channels, sampleRate, framesPerBuffer,
				suggestedLatency);
	}

	WasapiExclusivePortAudioStream(
			NativePortAudio portAudio,
			int device,
			int channels,
			double sampleRate,
			int framesPerBuffer,
			double suggestedLatency) {
		this.portAudio = portAudio;

		WasapiStreamInfo wasapi = new WasapiStreamInfo();
		wasapi.size = wasapi.size();
		wasapi.hostApiType = PA_WASAPI;
		wasapi.version = 1;
		wasapi.flags = PA_WIN_WASAPI_EXCLUSIVE;
		wasapi.write();

		StreamParameters output = new StreamParameters();
		output.device = device;
		output.channelCount = channels;
		output.sampleFormat = PA_FLOAT_32;
		output.suggestedLatency = suggestedLatency;
		output.hostApiSpecificStreamInfo = wasapi.getPointer();
		output.write();

		PointerByReference openedStream = new PointerByReference();
		checkError(portAudio.Pa_OpenStream(
				openedStream,
				null,
				output,
				sampleRate,
				framesPerBuffer,
				0,
				null,
				null));
		stream = openedStream.getValue();
		if (stream == null) {
			throw new IllegalStateException("PortAudio returned a null WASAPI stream");
		}
	}

	@Override
	public void start() {
		checkError(portAudio.Pa_StartStream(stream));
	}

	@Override
	public boolean write(float[] buffer, int frames) {
		int error = portAudio.Pa_WriteStream(stream, buffer, frames);
		if (error == PA_OUTPUT_UNDERFLOWED) {
			return true;
		}
		checkError(error);
		return false;
	}

	@Override
	public void stop() {
		if (stream != null) {
			checkError(portAudio.Pa_StopStream(stream));
		}
	}

	@Override
	public void close() {
		if (stream != null) {
			Pointer closing = stream;
			stream = null;
			checkError(portAudio.Pa_CloseStream(closing));
		}
	}

	private void checkError(int error) {
		if (error < 0) {
			String detail = portAudio.Pa_GetErrorText(error);
			throw new IllegalStateException(
					"PortAudio error " + error + (detail == null ? "" : ": " + detail));
		}
	}

	static String nativeLibraryName(String architecture) {
		return architecture != null && architecture.contains("64")
				? "portaudio_x64"
				: "portaudio_x86";
	}

	private static NativePortAudio loadNativePortAudio() {
		return Native.load(
				nativeLibraryName(System.getProperty("os.arch")),
				NativePortAudio.class);
	}

	public interface NativePortAudio extends Library {
		int Pa_OpenStream(
				PointerByReference stream,
				Pointer inputParameters,
				StreamParameters outputParameters,
				double sampleRate,
				int framesPerBuffer,
				int streamFlags,
				Pointer callback,
				Pointer userData);

		int Pa_StartStream(Pointer stream);

		int Pa_WriteStream(Pointer stream, float[] buffer, int frames);

		int Pa_StopStream(Pointer stream);

		int Pa_CloseStream(Pointer stream);

		String Pa_GetErrorText(int error);
	}

	@Structure.FieldOrder({
			"device",
			"channelCount",
			"sampleFormat",
			"suggestedLatency",
			"hostApiSpecificStreamInfo",
	})
	public static final class StreamParameters extends Structure {
		public int device;
		public int channelCount;
		public int sampleFormat;
		public double suggestedLatency;
		public Pointer hostApiSpecificStreamInfo;

		StreamParameters() {
			super(ALIGN_MSVC);
		}
	}

	@Structure.FieldOrder({
			"size",
			"hostApiType",
			"version",
			"flags",
			"channelMask",
			"hostProcessorOutput",
			"hostProcessorInput",
			"threadPriority",
			"streamCategory",
			"streamOption",
	})
	public static final class WasapiStreamInfo extends Structure {
		public int size;
		public int hostApiType;
		public int version;
		public int flags;
		public int channelMask;
		public Pointer hostProcessorOutput;
		public Pointer hostProcessorInput;
		public int threadPriority;
		public int streamCategory;
		public int streamOption;

		WasapiStreamInfo() {
			super(ALIGN_MSVC);
		}
	}
}
