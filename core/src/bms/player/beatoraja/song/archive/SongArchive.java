package bms.player.beatoraja.song.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Base class for a song archive format. Implementations provide archive-format
 * specific I/O while this class centralizes limits and path validation.
 */
public abstract class SongArchive {

	protected static final long MAX_EXTRACTED_SIZE = 4L * 1024 * 1024 * 1024;
	protected static final long MAX_CHART_SIZE = 64L * 1024 * 1024;
	protected static final long MAX_RESOURCE_SIZE = 1024L * 1024 * 1024;
	protected static final int MAX_ENTRY_COUNT = 100_000;

	private final List<String> extensions;

	protected SongArchive(String... extensions) {
		this.extensions = Arrays.stream(extensions)
				.map(extension -> extension.toLowerCase(Locale.ROOT))
				.toList();
	}

	public final boolean supports(Path path) {
		Path filename = path.getFileName();
		if (filename == null || !Files.isRegularFile(path)) {
			return false;
		}
		String name = filename.toString().toLowerCase(Locale.ROOT);
		return extensions.stream().anyMatch(name::endsWith);
	}

	/** Returns whether the leading bytes identify this archive format. */
	public abstract boolean matchesSignature(byte[] signature, int length);

	public final List<String> extensions() {
		return extensions;
	}

	public abstract List<String> listEntries(Path archive) throws IOException;

	/** Opens one entry. Closing the returned stream must also close the archive. */
	public abstract InputStream openEntry(Path archive, String entryName) throws IOException;

	public abstract long entrySize(Path archive, String entryName) throws IOException;

	public abstract byte[] readEntry(Path archive, String entryName) throws IOException;

	protected static String normalizeEntryName(String entryName) {
		String normalized = normalizeEntryNameOrNull(entryName);
		if (normalized == null) {
			throw new IllegalArgumentException("Unsafe archive entry: " + entryName);
		}
		return normalized;
	}

	protected static String normalizeEntryNameOrNull(String entryName) {
		String normalized = entryName.replace('\\', '/');
		while (normalized.startsWith("./")) {
			normalized = normalized.substring(2);
		}
		if (normalized.isEmpty() || normalized.startsWith("/") || normalized.contains("//")) {
			return null;
		}
		for (String component : normalized.split("/")) {
			if (component.equals(".") || component.equals("..") || component.isEmpty()) {
				return null;
			}
		}
		return normalized;
	}

	protected static byte[] readLimited(InputStream input, ByteArrayOutputStream output, long maxSize,
			String errorMessage) throws IOException {
		byte[] buffer = new byte[8192];
		long size = 0;
		int read;
		while ((read = input.read(buffer)) >= 0) {
			size += read;
			if (size > maxSize) {
				throw new IOException(errorMessage);
			}
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	protected static InputStream limitStream(InputStream input, long declaredSize, String errorMessage)
			throws IOException {
		if (declaredSize > MAX_RESOURCE_SIZE) {
			input.close();
			throw new IOException(errorMessage);
		}
		return new java.io.FilterInputStream(input) {
			private long consumed;

			@Override
			public int read() throws IOException {
				int value = super.read();
				if (value >= 0) {
					checkSize(1);
				}
				return value;
			}

			@Override
			public int read(byte[] buffer, int offset, int length) throws IOException {
				int count = super.read(buffer, offset, length);
				if (count > 0) {
					checkSize(count);
				}
				return count;
			}

			private void checkSize(int count) throws IOException {
				consumed += count;
				if (consumed > MAX_RESOURCE_SIZE) {
					throw new IOException(errorMessage);
				}
			}
		};
	}

}
