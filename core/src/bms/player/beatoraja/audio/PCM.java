package bms.player.beatoraja.audio;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jflac.FLACDecoder;
import org.jflac.metadata.StreamInfo;

import com.badlogic.gdx.backends.lwjgl3.audio.OggInputStream;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.StreamUtils;
import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.MP3Decoder;
import javazoom.jl.decoder.OutputBuffer;

/**
 * PCM音源処理用クラス
 * 
 * @author exch
 */
public abstract class PCM<T> {
	private static final Logger logger = LoggerFactory.getLogger(PCM.class);

	protected static final boolean USE_UNSAFE = false;

	/**
	 * チャンネル数
	 */
	public final int channels;
	/**
	 * 音源のサンプリングレート(Hz)
	 */
	public final int sampleRate;
	/**
	 * PCMデータ
	 */
	public final T sample;
	/**
	 * PCMデータ開始位置
	 */
	public final int start;
	/**
	 * PCMデータ長
	 */	
	public final int len;

	PCM(int channels, int sampleRate, int start, int len, T sample) {
		this.channels = channels;
		this.sampleRate = sampleRate;
		this.start = start;
		this.len = len;
		this.sample = sample;
	}

	public static PCM load(Path p, AudioDriver driver) {
		return load(SongResources.fromPath(p), driver);
	}

	public static PCM load(SongResource resource, AudioDriver driver) {
		try {
			return load(resource, driver, false, Long.MAX_VALUE);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}

	/**
	 * Loads one PCM resource into GC-managed buffers while enforcing a strict
	 * per-resource allocation budget. This is used by background preview
	 * generation; normal gameplay keeps the existing direct-buffer path.
	 */
	static PCM loadBounded(SongResource resource, AudioDriver driver, long maxAllocatedBytes)
			throws IOException {
		if (maxAllocatedBytes <= 0) {
			throw new IllegalArgumentException("PCM allocation budget must be positive");
		}
		return load(resource, driver, true, maxAllocatedBytes);
	}

	private static PCM load(
			SongResource resource,
			AudioDriver driver,
			boolean heapBuffers,
			long maxAllocatedBytes) throws IOException {
		PCMLoader loader = new PCMLoader(driver, heapBuffers, maxAllocatedBytes);
		loader.loadPCM(resource);
		long rawPcmBytes = loader.pcm.capacity();
		long convertedPcmBytes = estimatedPcmBytes(loader);
		ensureWithinBudget(
				checkedAdd(rawPcmBytes, convertedPcmBytes),
				maxAllocatedBytes,
				resource.displayPath());

		PCM pcm;
		if(loader.bitsPerSample > 16) {
//			System.out.println("FLOAT");
			pcm = FloatPCM.loadPCM(loader);
		} else if(loader.bitsPerSample == 16) {
			if(loader.pcm.isDirect()) {
				pcm = ShortDirectPCM.loadPCM(loader);
			} else {
				pcm = ShortPCM.loadPCM(loader);
			}
		} else {
			// TODO BytePCMのバグが解消されたら切替
//			pcm = BytePCM.loadPCM(loader);
			pcm = ShortPCM.loadPCM(loader);
		}

		long allocatedBytes = checkedAdd(rawPcmBytes, pcm.memoryBytes());
		ensureWithinBudget(allocatedBytes, maxAllocatedBytes, resource.displayPath());

		// TODO PCMLoader側での逐次変換が実装されたら削除
		if(((AbstractAudioDriver)driver).channels != 0 && pcm.channels != ((AbstractAudioDriver)driver).channels) {
			long estimatedBytes = scaledBytes(
					pcm.memoryBytes(),
					((AbstractAudioDriver)driver).channels,
					pcm.channels);
			ensureWithinBudget(
					checkedAdd(allocatedBytes, estimatedBytes),
					maxAllocatedBytes,
					resource.displayPath());
			PCM converted = pcm.changeChannels(((AbstractAudioDriver)driver).channels);
			allocatedBytes = checkedAdd(allocatedBytes, converted.memoryBytes());
			ensureWithinBudget(allocatedBytes, maxAllocatedBytes, resource.displayPath());
			pcm = converted;
		}
		if(((AbstractAudioDriver)driver).getSampleRate() != 0 && pcm.sampleRate != ((AbstractAudioDriver)driver).getSampleRate()) {
			long estimatedBytes = scaledBytes(
					pcm.memoryBytes(),
					((AbstractAudioDriver)driver).getSampleRate(),
					pcm.sampleRate);
			ensureWithinBudget(
					checkedAdd(allocatedBytes, estimatedBytes),
					maxAllocatedBytes,
					resource.displayPath());
			PCM converted = pcm.changeSampleRate(((AbstractAudioDriver)driver).getSampleRate());
			allocatedBytes = checkedAdd(allocatedBytes, converted.memoryBytes());
			ensureWithinBudget(allocatedBytes, maxAllocatedBytes, resource.displayPath());
			pcm = converted;
		}

		if(pcm.validate()) {
			return pcm;
		}
		logger.warn("音源の読み込みに失敗しました - file : {}", resource.displayPath());
		return null;
	}

	long memoryBytes() {
		if (sample instanceof byte[] bytes) {
			return bytes.length;
		}
		if (sample instanceof short[] shorts) {
			return (long) shorts.length * Short.BYTES;
		}
		if (sample instanceof float[] floats) {
			return (long) floats.length * Float.BYTES;
		}
		if (sample instanceof ByteBuffer buffer) {
			return buffer.capacity();
		}
		return 0L;
	}

	private static long estimatedPcmBytes(PCMLoader loader) throws IOException {
		if (loader.bitsPerSample <= 0) {
			throw new IOException("Invalid PCM sample size");
		}
		long inputBytesPerSample = Math.max(1L, (loader.bitsPerSample + 7L) / 8L);
		long sampleCount = loader.pcm.limit() / inputBytesPerSample;
		long outputBytesPerSample = loader.bitsPerSample > 16
				? Float.BYTES
				: Short.BYTES;
		return checkedMultiply(sampleCount, outputBytesPerSample);
	}

	private static long scaledBytes(long bytes, int numerator, int denominator) throws IOException {
		if (bytes < 0 || numerator <= 0 || denominator <= 0) {
			throw new IOException("Invalid PCM conversion size");
		}
		if (bytes > Long.MAX_VALUE / numerator) {
			return Long.MAX_VALUE;
		}
		long product = bytes * numerator;
		long rounding = denominator - 1L;
		return product > Long.MAX_VALUE - rounding
				? Long.MAX_VALUE
				: (product + rounding) / denominator;
	}

	private static long checkedAdd(long left, long right) {
		if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
			return Long.MAX_VALUE;
		}
		return left + right;
	}

	private static long checkedMultiply(long left, long right) {
		if (left < 0 || right < 0 || (left != 0 && right > Long.MAX_VALUE / left)) {
			return Long.MAX_VALUE;
		}
		return left * right;
	}

	private static void ensureWithinBudget(long bytes, long maximum, String displayName)
			throws IOException {
		if (bytes < 0 || bytes > maximum) {
			throw new PcmLimitExceededException(
					displayName + " exceeds the PCM allocation budget");
		}
	}

	static final class PcmLimitExceededException extends IOException {
		private PcmLimitExceededException(String message) {
			super(message);
		}
	}
	
	public static PCM load(String name, AudioDriver driver) {
		for(Path path : AudioDriver.getPaths(name)) {
			PCM pcm = PCM.load(path,driver);
			if(pcm != null) {
				return pcm;
			}			
		}
		return null;
	}
	
	/**
	 * サンプリングレートを変更したPCMを返す
	 * 
	 * @param sample
	 *            サンプリングレート
	 * @return サンプリングレート変更後のPCM
	 */
	public abstract PCM<T> changeSampleRate(int sample);

	/**
	 * 再生速度を変更したPCMを返す
	 * 
	 * @param rate
	 *            再生速度。基準は1.0
	 * @return 再生速度を変更したPCM
	 */
	public abstract PCM<T> changeFrequency(float rate);
	
	/**
	 * チャンネル数を変更したPCMを返す
	 * 
	 * @param channels
	 *            チャンネル数
	 * @return チャンネル数を変更したPCM
	 */
	public abstract PCM<T> changeChannels(int channels);
	/**
	 * トリミングしたPCMを返す
	 * 
	 * @param starttime
	 *            開始時間(us)
	 * @param duration
	 *            再生時間(us)
	 * @return トリミングしたPCM
	 */
	public abstract PCM<T> slice(long starttime, long duration);
	
	public abstract boolean validate();
	
	protected static ByteBuffer getDirectByteBuffer(int capacity) {
		ByteBuffer result = USE_UNSAFE ? BufferUtils.newUnsafeByteBuffer(capacity) : ByteBuffer.allocateDirect(capacity);
		return result.order(ByteOrder.LITTLE_ENDIAN);
	}

	static class PCMLoader {
		
		ByteBuffer pcm;
		int channels = 0;
		int sampleRate = 0;
		int bitsPerSample = 0;
		int blockAlign = 0;
		
		private final AudioDriver driver;
		private final boolean heapBuffers;
		private final long maxDecodedBytes;
		
		public PCMLoader(AudioDriver driver) {
			this(driver, false, Long.MAX_VALUE);
		}

		PCMLoader(AudioDriver driver, boolean heapBuffers, long maxDecodedBytes) {
			this.driver = driver;
			this.heapBuffers = heapBuffers;
			this.maxDecodedBytes = maxDecodedBytes;
		};

		private ByteBuffer allocatePcmBuffer(int capacity, String displayName) throws IOException {
			ensureWithinBudget(capacity, maxDecodedBytes, displayName);
			ByteBuffer result = heapBuffers
					? ByteBuffer.allocate(capacity)
					: getDirectByteBuffer(capacity);
			return result.order(ByteOrder.LITTLE_ENDIAN);
		}

		private BoundedPcmOutputStream newPcmOutputStream(long preferredCapacity) {
			long boundedPreferred = heapBuffers
					? Math.min(preferredCapacity, 64L * 1024L)
					: preferredCapacity;
			return new BoundedPcmOutputStream(boundedPreferred, maxDecodedBytes);
		}

		private void ensureOutputWithinBudget(
				BoundedPcmOutputStream output,
				String displayName) throws IOException {
			if (output.limitExceeded()) {
				throw new PcmLimitExceededException(
						displayName + " exceeds the PCM decode budget");
			}
		}

		private ByteBuffer decodedPcmBuffer(
				BoundedPcmOutputStream output,
				String displayName) throws IOException {
			if (heapBuffers) {
				ByteBuffer result = ByteBuffer.wrap(output.buffer()).order(ByteOrder.LITTLE_ENDIAN);
				result.limit(output.size());
				return result;
			}
			return allocatePcmBuffer(output.size(), displayName)
					.put(output.buffer(), 0, output.size());
		}

		private static void copyToHeapBuffer(InputStream input, ByteBuffer output)
				throws IOException {
			byte[] chunk = new byte[Math.min(4096, Math.max(1, output.remaining()))];
			while (output.hasRemaining()) {
				int length = input.read(chunk);
				if (length < 0) {
					throw new EOFException("Unexpected end of PCM stream");
				}
				if (length > output.remaining()) {
					throw new IOException("PCM stream is larger than its declared size");
				}
				output.put(chunk, 0, length);
			}
		}
		
		public void loadPCM(Path p) throws IOException {
			loadPCM(SongResources.fromPath(p));
		}

		public void loadPCM(SongResource resource) throws IOException {
			try (InputStream input = resource.openStream()) {
				loadPCM(input, resource.displayPath());
			}
		}

		public void loadPCM(InputStream source, String displayName) throws IOException {
			// TODO prefferedSampleRate, prefferedChannelsを使って逐次変換し、メモリ確保のコストを減らす
			// final long time = System.nanoTime();
			pcm = null;

			final String name = displayName.toLowerCase(java.util.Locale.ROOT);
			//WAVFile wavfile = WAVFile.fromFile(p);
			if (name.endsWith(".wav")) {
				try (WavInputStream input = new WavInputStream(new BufferedInputStream(source))) {
					switch(input.type) {
					case 1:
                    case 3:
						{
						channels = input.channels;
						sampleRate = input.sampleRate;
						bitsPerSample = input.bitsPerSample;
						
						if(bitsPerSample == 16) {
							pcm = allocatePcmBuffer(input.dataRemaining, displayName);
							if (heapBuffers) {
								copyToHeapBuffer(input, pcm);
							} else {
								StreamUtils.copyStream(input, pcm);
							}
						} else {
							BoundedPcmOutputStream output = newPcmOutputStream(input.dataRemaining);
							StreamUtils.copyStream(input, output);
							ensureOutputWithinBudget(output, displayName);
							pcm = ByteBuffer.wrap(output.buffer()).order(ByteOrder.LITTLE_ENDIAN);
							pcm.limit(output.size());
						}
						
						break;					
					}
					case 2:
					    {
						channels = input.channels;
						sampleRate = input.sampleRate;
						bitsPerSample = 16;
						blockAlign = input.blockAlign;
//						logger.info("channels: " + channels);
//						logger.info("sample rate: " + sampleRate);
//						logger.info("block align" + blockAlign);

						BoundedPcmOutputStream inputByteStream = newPcmOutputStream(input.dataRemaining);
						StreamUtils.copyStream(input, inputByteStream);
						ensureOutputWithinBudget(inputByteStream, displayName);
						ByteBuffer inputByteBuffer = ByteBuffer.wrap(inputByteStream.buffer())
								.order(ByteOrder.LITTLE_ENDIAN);
						inputByteBuffer.limit(inputByteStream.size());
						long samplesPerBlock = ((long) input.blockAlign - input.channels * 6L)
								* 2L / input.channels;
						long blockCount = input.blockAlign > 0
								? inputByteBuffer.remaining() / input.blockAlign
								: Long.MAX_VALUE;
						long decodedBytes = samplesPerBlock > 0
								? checkedMultiply(
										checkedMultiply(blockCount, samplesPerBlock),
										checkedMultiply(input.channels, 2L))
								: Long.MAX_VALUE;
						ensureWithinBudget(
								checkedAdd(inputByteStream.buffer().length, decodedBytes),
								maxDecodedBytes,
								displayName);

						MSADPCMDecoder decoder = new MSADPCMDecoder(channels, sampleRate, blockAlign);
						pcm = decoder.decode(inputByteBuffer);


						logger.info("Filename: {}", displayName);
						break;
					}

					case 85:
						// mp3
					{
						try {
							Bitstream bitstream = new Bitstream(new ByteArrayInputStream(
									StreamUtils.copyStreamToByteArray(input, input.dataRemaining)));
							BoundedPcmOutputStream output = newPcmOutputStream(4096);
							MP3Decoder decoder = new MP3Decoder();
							OutputBuffer outputBuffer = null;
							while (true) {
								Header header = bitstream.readFrame();
								if (header == null)
									break;
								if (outputBuffer == null) {
									channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
									outputBuffer = new OutputBuffer(channels, false);
									decoder.setOutputBuffer(outputBuffer);
									sampleRate = header.getSampleRate();
								}
								try {
									decoder.decodeFrame(header, bitstream);
								} catch (Exception ignored) {
									// JLayer's decoder throws
									// ArrayIndexOutOfBoundsException
									// sometimes!?
								}
								bitstream.closeFrame();
								output.write(outputBuffer.getBuffer(), 0, outputBuffer.reset());
								if (output.limitExceeded()) {
									break;
								}
							}
							bitstream.close();
							ensureOutputWithinBudget(output, displayName);
							pcm = decodedPcmBuffer(output, displayName);
							bitsPerSample = 16;
						} catch (BitstreamException e) {
							e.printStackTrace();
						}
						break;					
					}
					default:
						throw new IOException(displayName + " unsupported WAV format ID : " + input.type);
					}
					} catch (PcmLimitExceededException error) {
						throw error;
					} catch (Throwable e) {
						logger.warn("WAV処理中の例外 - file : {} error : {}{}", displayName, e.getMessage(), e.toString());
					}
			} else if (name.endsWith(".ogg")) {
				// ogg
				try (OggInputStream input = new OggInputStream(new BufferedInputStream(source))) {
					// final long time = System.nanoTime();
					// OptimizedByteArrayOutputStream output = new
					// OptimizedByteArrayOutputStream(4096);
					BoundedPcmOutputStream output = newPcmOutputStream((long) input.getLength() * 16L);
					byte[] buff = new byte[4096];
					while (!input.atEnd()) {
						int length = input.read(buff);
						if (length == -1)
							break;
						output.write(buff, 0, length);
						if (output.limitExceeded()) {
							break;
						}
					}
					ensureOutputWithinBudget(output, displayName);
					
					channels = input.getChannels();
					sampleRate = input.getSampleRate();
					bitsPerSample = 16;
					
					pcm = decodedPcmBuffer(output, displayName);
//					System.out.println(name + " - length : " + input.getLength() + " ( " + input.getLength() * 16 + " ) " + " , bytes : " + bytes);
				} catch (PcmLimitExceededException error) {
					throw error;
				} catch (Throwable ex) {
				}
			} else if (name.endsWith(".mp3")) {
				// mp3
				try {
					Bitstream bitstream = new Bitstream(new BufferedInputStream(source));
					BoundedPcmOutputStream output = newPcmOutputStream(4096);
					MP3Decoder decoder = new MP3Decoder();
					OutputBuffer outputBuffer = null;
					while (true) {
						Header header = bitstream.readFrame();
						if (header == null)
							break;
						if (outputBuffer == null) {
							channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
							outputBuffer = new OutputBuffer(channels, false);
							decoder.setOutputBuffer(outputBuffer);
							sampleRate = header.getSampleRate();
						}
						try {
							decoder.decodeFrame(header, bitstream);
						} catch (Exception ignored) {
							// JLayer's decoder throws
							// ArrayIndexOutOfBoundsException
							// sometimes!?
						}
						bitstream.closeFrame();
						output.write(outputBuffer.getBuffer(), 0, outputBuffer.reset());
						if (output.limitExceeded()) {
							break;
						}
					}
					bitstream.close();
					ensureOutputWithinBudget(output, displayName);
					pcm = decodedPcmBuffer(output, displayName);
					bitsPerSample = 16;
				} catch (PcmLimitExceededException error) {
					throw error;
				} catch (Throwable ex) {
				}
			} else if (name.endsWith(".flac")) {
				// flac
				try {
					FLACDecoder input = new FLACDecoder(new BufferedInputStream(source));
					input.readMetadata();
					StreamInfo info = input.getStreamInfo();
					
					channels = info.getChannels();
					sampleRate = info.getSampleRate();
					bitsPerSample = info.getBitsPerSample();
					
					long expectedBytes = info.getTotalSamples() > 0
							? checkedMultiply(
									checkedMultiply(info.getTotalSamples(), Math.max(1L, channels)),
									Math.max(1L, (bitsPerSample + 7L) / 8L))
							: 4096L;
					BoundedPcmOutputStream output = newPcmOutputStream(expectedBytes);
					input.addPCMProcessor(new FlacProcessor(output));
					
					input.decodeFrames();
					ensureOutputWithinBudget(output, displayName);
					
					if(bitsPerSample == 16) {
						pcm = decodedPcmBuffer(output, displayName);
					} else {
						pcm = ByteBuffer.wrap(output.buffer()).order(ByteOrder.LITTLE_ENDIAN);
						pcm.limit(output.size());						
					}
				} catch (PcmLimitExceededException error) {
					throw error;
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			}

			if(pcm == null) {
				throw new IOException(displayName + " : can't convert to PCM");
			}
			ensureWithinBudget(pcm.limit(), maxDecodedBytes, displayName);
			
			int bytes = pcm.limit();
			bytes -= bytes % (channels > 1 ? bitsPerSample / 4 : bitsPerSample / 8);
//			final int orgbytes = bytes;
			while(bytes > channels * bitsPerSample / 8) {
				boolean zero = true;
				for(int i = 0;i < channels * bitsPerSample / 8;i++){
					zero &= (pcm.get(bytes - i - 1) == 0x00);
				}
				if(zero) {
					bytes -= channels * bitsPerSample / 8;
				} else {
					break;
				}
			}
//			if(bytes != orgbytes) {
//				logger.info("終端の無音データ除外 - " + p.getFileName().toString() + " : " + (orgbytes - bytes) + " bytes");
//			}
			if(bytes < channels * bitsPerSample / 8) {
				throw new IOException(displayName + " : 0 samples");
			}
			if(sampleRate == 0) {
				throw new IOException(displayName + " : 0 sample rate");
			}
			pcm.limit(bytes);
			
//			System.out.println(p.getFileName().toString() + " - " + sampleRate + " Hz, " + bitsPerSample + " bits, " + channels + " channels");
			}
		}

	private static final class BoundedPcmOutputStream extends ByteArrayOutputStream {
		private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

		private final int maximum;
		private boolean limitExceeded;

		private BoundedPcmOutputStream(long preferredCapacity, long maximumBytes) {
			super(initialCapacity(preferredCapacity, maximumBytes));
			this.maximum = (int) Math.min(MAX_ARRAY_SIZE, Math.max(0L, maximumBytes));
		}

		@Override
		public synchronized void write(int value) {
			if (limitExceeded || count >= maximum) {
				limitExceeded = true;
				return;
			}
			super.write(value);
		}

		@Override
		public synchronized void write(byte[] source, int offset, int length) {
			if (source == null) {
				throw new NullPointerException();
			}
			if (offset < 0 || length < 0 || offset > source.length - length) {
				throw new IndexOutOfBoundsException();
			}
			if (limitExceeded || length > maximum - count) {
				limitExceeded = true;
				return;
			}
			super.write(source, offset, length);
		}

		private byte[] buffer() {
			return buf;
		}

		private boolean limitExceeded() {
			return limitExceeded;
		}

		private static int initialCapacity(long preferredCapacity, long maximumBytes) {
			long maximum = Math.min(MAX_ARRAY_SIZE, Math.max(0L, maximumBytes));
			long preferred = Math.max(32L, preferredCapacity);
			return (int) Math.min(preferred, maximum);
		}
	}

	/** @author Nathan Sweet */
	private static class WavInputStream extends FilterInputStream {
		private int dataRemaining;
		/**
		 * PCMのタイプ
		 */
		private final int type;
		private final int channels;
		private final int sampleRate;

		private int blockAlign = -1;
		/**
		 * 1サンプル当たりのビット数
		 */
		private final int bitsPerSample;		

		WavInputStream(InputStream p) {
			super(p);
			try {
				if (read() != 'R' || read() != 'I' || read() != 'F' || read() != 'F')
					throw new RuntimeException("RIFF header not found: " + p.toString());

				skipFully(4);

				if (read() != 'W' || read() != 'A' || read() != 'V' || read() != 'E')
					throw new RuntimeException("Invalid wave file header: " + p.toString());

				int fmtChunkLength = seekToChunk('f', 'm', 't', ' ');

				type = read() & 0xff | (read() & 0xff) << 8;

				channels = read() & 0xff | (read() & 0xff) << 8;

				sampleRate = read() & 0xff | (read() & 0xff) << 8 | (read() & 0xff) << 16 | (read() & 0xff) << 24;

				skipFully(4);

				blockAlign = read() & 0xff | (read() & 0xff) <<8;

				bitsPerSample = read() & 0xff | (read() & 0xff) << 8;

				skipFully(fmtChunkLength - 16);

				dataRemaining = seekToChunk('d', 'a', 't', 'a');
			} catch (Throwable ex) {
				StreamUtils.closeQuietly(this);
				throw new RuntimeException("Error reading WAV file: " + p.toString(), ex);
			}
		}

		private int seekToChunk(char c1, char c2, char c3, char c4) throws IOException {
			while (true) {
				boolean found = read() == c1;
				found &= read() == c2;
				found &= read() == c3;
				found &= read() == c4;
				int chunkLength = read() & 0xff | (read() & 0xff) << 8 | (read() & 0xff) << 16 | (read() & 0xff) << 24;
				if (chunkLength == -1)
					throw new IOException("Chunk not found: " + c1 + c2 + c3 + c4);
				if (found)
					return chunkLength;
				skipFully(chunkLength);
			}
		}

		private void skipFully(int count) throws IOException {
			while (count > 0) {
				long skipped = in.skip(count);
				if (skipped <= 0)
					throw new EOFException("Unable to skip.");
				count -= skipped;
			}
		}

		public int read(byte[] buffer) throws IOException {
			if (dataRemaining == 0)
				return -1;
			int length = Math.min(super.read(buffer), dataRemaining);
			if (length == -1)
				return -1;
			dataRemaining -= length;
			return length;
		}
	}
}
