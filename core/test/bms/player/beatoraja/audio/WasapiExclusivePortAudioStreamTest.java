package bms.player.beatoraja.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import bms.player.beatoraja.audio.WasapiExclusivePortAudioStream.NativePortAudio;
import bms.player.beatoraja.audio.WasapiExclusivePortAudioStream.StreamParameters;
import bms.player.beatoraja.audio.WasapiExclusivePortAudioStream.WasapiStreamInfo;

class WasapiExclusivePortAudioStreamTest {
	@Test
	void windowsPortAudioStructuresMatchTheBundledNineteenSixAbi() {
		assertEquals(Native.POINTER_SIZE == 8 ? 56 : 40, new WasapiStreamInfo().size());
		assertEquals("portaudio_x64",
				WasapiExclusivePortAudioStream.nativeLibraryName("amd64"));
		assertEquals("portaudio_x86",
				WasapiExclusivePortAudioStream.nativeLibraryName("x86"));
	}

	@Test
	void exclusiveOpenPassesWasapiHostSpecificInformation() {
		FakePortAudio nativePortAudio = new FakePortAudio();
		WasapiExclusivePortAudioStream stream = new WasapiExclusivePortAudioStream(
				nativePortAudio, 7, 2, 48_000, 128, 128.0 / 48_000.0);

		assertEquals(7, nativePortAudio.device);
		assertEquals(2, nativePortAudio.channels);
		assertEquals(48_000, nativePortAudio.sampleRate);
		assertEquals(128, nativePortAudio.framesPerBuffer);
		assertEquals(13, nativePortAudio.wasapiHostApiType);
		assertEquals(1, nativePortAudio.wasapiVersion);
		assertEquals(1, nativePortAudio.wasapiFlags);

		stream.start();
		assertFalse(stream.write(new float[256], 128));
		nativePortAudio.writeResult = WasapiExclusivePortAudioStream.PA_OUTPUT_UNDERFLOWED;
		assertTrue(stream.write(new float[256], 128));
		stream.stop();
		stream.close();

		assertTrue(nativePortAudio.started);
		assertTrue(nativePortAudio.stopped);
		assertTrue(nativePortAudio.closed);
	}

	private static final class FakePortAudio implements NativePortAudio {
		private int device;
		private int channels;
		private double sampleRate;
		private int framesPerBuffer;
		private int wasapiHostApiType;
		private int wasapiVersion;
		private int wasapiFlags;
		private int writeResult;
		private boolean started;
		private boolean stopped;
		private boolean closed;

		@Override
		public int Pa_OpenStream(
				PointerByReference stream,
				Pointer inputParameters,
				StreamParameters outputParameters,
				double sampleRate,
				int framesPerBuffer,
				int streamFlags,
				Pointer callback,
				Pointer userData) {
			this.device = outputParameters.device;
			this.channels = outputParameters.channelCount;
			this.sampleRate = sampleRate;
			this.framesPerBuffer = framesPerBuffer;
			Pointer wasapi = outputParameters.hostApiSpecificStreamInfo;
			this.wasapiHostApiType = wasapi.getInt(4);
			this.wasapiVersion = wasapi.getInt(8);
			this.wasapiFlags = wasapi.getInt(12);
			stream.setValue(Pointer.createConstant(1));
			return 0;
		}

		@Override
		public int Pa_StartStream(Pointer stream) {
			started = true;
			return 0;
		}

		@Override
		public int Pa_WriteStream(Pointer stream, float[] buffer, int frames) {
			return writeResult;
		}

		@Override
		public int Pa_StopStream(Pointer stream) {
			stopped = true;
			return 0;
		}

		@Override
		public int Pa_CloseStream(Pointer stream) {
			closed = true;
			return 0;
		}

		@Override
		public String Pa_GetErrorText(int error) {
			return "test error";
		}
	}
}
