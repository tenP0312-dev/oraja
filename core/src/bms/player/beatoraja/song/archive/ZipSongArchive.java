package bms.player.beatoraja.song.archive;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** ZIP implementation, including CP932 fallback for legacy Japanese archives. */
public final class ZipSongArchive extends SongArchive {

	private static final String LEGACY_ZIP_ENCODING = "MS932";

	public ZipSongArchive() {
		super(".zip");
	}

	@Override
	public List<String> listEntries(Path archive) throws IOException {
		try {
			return listEntriesWithDefaultEncoding(archive);
		} catch (ZipException e) {
			return listEntriesWithLegacyEncoding(archive);
		}
	}

	@Override
	public long entrySize(Path archive, String entryName) throws IOException {
		try {
			return entrySizeWithDefaultEncoding(archive, entryName);
		} catch (ZipException e) {
			return entrySizeWithLegacyEncoding(archive, entryName);
		}
	}

	@Override
	public InputStream openEntry(Path archive, String entryName) throws IOException {
		try {
			return openEntryWithDefaultEncoding(archive, entryName);
		} catch (ZipException e) {
			return openEntryWithLegacyEncoding(archive, entryName);
		}
	}

	private long entrySizeWithDefaultEncoding(Path archive, String entryName) throws IOException {
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			ZipEntry entry = zip.getEntry(entryName);
			if (entry == null || entry.isDirectory()) {
				throw new IOException("ZIP entry does not exist: " + entryName);
			}
			return entry.getSize();
		}
	}

	private long entrySizeWithLegacyEncoding(Path archive, String entryName) throws IOException {
		try (var zip = new org.apache.commons.compress.archivers.zip.ZipFile(archive.toFile(), LEGACY_ZIP_ENCODING)) {
			var entry = zip.getEntry(entryName);
			if (entry == null || entry.isDirectory()) {
				throw new IOException("ZIP entry does not exist: " + entryName);
			}
			return entry.getSize();
		}
	}

	@Override
	public byte[] readEntry(Path archive, String entryName) throws IOException {
		try {
			return readEntryWithDefaultEncoding(archive, entryName);
		} catch (ZipException e) {
			return readEntryWithLegacyEncoding(archive, entryName);
		}
	}

	private InputStream openEntryWithDefaultEncoding(Path archive, String entryName) throws IOException {
		ZipFile zip = new ZipFile(archive.toFile());
		try {
			ZipEntry entry = zip.getEntry(entryName);
			if (entry == null || entry.isDirectory()) {
				throw new IOException("ZIP entry does not exist: " + entryName);
			}
			return limitStream(closeArchiveWithStream(zip.getInputStream(entry), zip), entry.getSize(),
					"ZIP resource is too large: " + entryName);
		} catch (IOException | RuntimeException e) {
			zip.close();
			throw e;
		}
	}

	private InputStream openEntryWithLegacyEncoding(Path archive, String entryName) throws IOException {
		var zip = new org.apache.commons.compress.archivers.zip.ZipFile(archive.toFile(), LEGACY_ZIP_ENCODING);
		try {
			var entry = zip.getEntry(entryName);
			if (entry == null || entry.isDirectory()) {
				throw new IOException("ZIP entry does not exist: " + entryName);
			}
			validateReadableEntry(zip, entry, archive);
			return limitStream(closeArchiveWithStream(zip.getInputStream(entry), zip), entry.getSize(),
					"ZIP resource is too large: " + entryName);
		} catch (IOException | RuntimeException e) {
			zip.close();
			throw e;
		}
	}

	private InputStream closeArchiveWithStream(InputStream stream, AutoCloseable archive) {
		return new FilterInputStream(stream) {
			@Override
			public void close() throws IOException {
				try {
					super.close();
				} finally {
					try {
						archive.close();
					} catch (Exception e) {
						if (e instanceof IOException ioException) {
							throw ioException;
						}
						throw new IOException(e);
					}
				}
			}
		};
	}

	private List<String> listEntriesWithDefaultEncoding(Path archive) throws IOException {
		List<String> entries = new ArrayList<>();
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			if (zip.size() > MAX_ENTRY_COUNT) {
				throw new IOException("ZIP contains too many entries: " + archive);
			}
			Set<String> names = new HashSet<>();
			long totalSize = 0;
			var zipEntries = zip.entries();
			while (zipEntries.hasMoreElements()) {
				ZipEntry entry = zipEntries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				totalSize = checkedArchiveSize(totalSize, entry.getSize(), archive);
				addEntry(entries, names, entry.getName());
			}
		}
		return entries;
	}

	private List<String> listEntriesWithLegacyEncoding(Path archive) throws IOException {
		List<String> entries = new ArrayList<>();
		try (var zip = new org.apache.commons.compress.archivers.zip.ZipFile(archive.toFile(), LEGACY_ZIP_ENCODING)) {
			Set<String> names = new HashSet<>();
			var zipEntries = zip.getEntries();
			int entryCount = 0;
			long totalSize = 0;
			while (zipEntries.hasMoreElements()) {
				if (++entryCount > MAX_ENTRY_COUNT) {
					throw new IOException("ZIP contains too many entries: " + archive);
				}
				var entry = zipEntries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				validateReadableEntry(zip, entry, archive);
				totalSize = checkedArchiveSize(totalSize, entry.getSize(), archive);
				addEntry(entries, names, entry.getName());
			}
		}
		return entries;
	}

	private byte[] readEntryWithDefaultEncoding(Path archive, String entryName) throws IOException {
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			ZipEntry entry = zip.getEntry(entryName);
			if (entry == null || entry.isDirectory()) {
				throw new IOException("ZIP entry does not exist: " + entryName);
			}
			return readEntry(zip.getInputStream(entry), entry.getSize(), entryName);
		}
	}

	private byte[] readEntryWithLegacyEncoding(Path archive, String entryName) throws IOException {
		try (var zip = new org.apache.commons.compress.archivers.zip.ZipFile(archive.toFile(), LEGACY_ZIP_ENCODING)) {
			var entry = zip.getEntry(entryName);
			if (entry == null || entry.isDirectory()) {
				throw new IOException("ZIP entry does not exist: " + entryName);
			}
			return readEntry(zip.getInputStream(entry), entry.getSize(), entryName);
		}
	}

	private byte[] readEntry(InputStream input, long entrySize, String entryName) throws IOException {
		if (entrySize > MAX_CHART_SIZE) {
			throw new IOException("ZIP chart is too large: " + entryName);
		}
		try (input; ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.max(entrySize, 0))) {
			return readLimited(input, output, MAX_CHART_SIZE, "ZIP chart is too large: " + entryName);
		}
	}

	private void addEntry(List<String> entries, Set<String> names, String entryName) throws IOException {
		entries.add(checkedEntryName(names, entryName));
	}

	private String checkedEntryName(Set<String> names, String entryName) throws IOException {
		String name = normalizeEntryNameOrNull(entryName);
		if (name == null || !names.add(name)) {
			throw new IOException("Unsafe ZIP entry: " + entryName);
		}
		return name;
	}

	private long checkedArchiveSize(long totalSize, long entrySize, Path archive) throws IOException {
		if (entrySize < 0) {
			return totalSize;
		}
		long next = totalSize + entrySize;
		if (next < totalSize || next > MAX_EXTRACTED_SIZE) {
			throw new IOException("ZIP is too large after decompression: " + archive);
		}
		return next;
	}

	private void validateReadableEntry(org.apache.commons.compress.archivers.zip.ZipFile zip,
			org.apache.commons.compress.archivers.zip.ZipArchiveEntry entry, Path archive) throws IOException {
		if (entry.getGeneralPurposeBit().usesEncryption() || !zip.canReadEntryData(entry)) {
			throw new IOException("Encrypted or unsupported ZIP entries are not supported: " + archive);
		}
	}

}
