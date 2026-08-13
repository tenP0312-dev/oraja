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

import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;

/** Read-only 7z implementation backed by Apache Commons Compress. */
public final class SevenZipSongArchive extends SongArchive {

	private static final byte[] SIGNATURE = { 0x37, 0x7a, (byte) 0xbc, (byte) 0xaf, 0x27, 0x1c };
	private static final int MAX_DECODER_MEMORY_KIB = 512 * 1024;

	public SevenZipSongArchive() {
		super(".7z");
	}

	@Override
	public boolean matchesSignature(byte[] signature, int length) {
		if (length < SIGNATURE.length) {
			return false;
		}
		for (int index = 0; index < SIGNATURE.length; index++) {
			if (signature[index] != SIGNATURE[index]) {
				return false;
			}
		}
		return true;
	}

	@Override
	public List<String> listEntries(Path archive) throws IOException {
		try (SevenZFile sevenZ = open(archive)) {
			List<String> entries = new ArrayList<>();
			Set<String> names = new HashSet<>();
			long totalSize = 0;
			int entryCount = 0;
			for (SevenZArchiveEntry entry : sevenZ.getEntries()) {
				if (++entryCount > MAX_ENTRY_COUNT) {
					throw new IOException("7z contains too many entries: " + archive);
				}
				validate(entry, archive);
				if (entry.isDirectory()) {
					continue;
				}
				long size = entry.getSize();
				if (size < 0 || totalSize + size < totalSize || totalSize + size > MAX_EXTRACTED_SIZE) {
					throw new IOException("7z is too large after decompression: " + archive);
				}
				totalSize += size;
				String name = normalizeEntryNameOrNull(entry.getName());
				if (name == null || !names.add(name)) {
					throw new IOException("Unsafe 7z entry: " + entry.getName());
				}
				entries.add(name);
			}
			return entries;
		} catch (PasswordRequiredException e) {
			throw new IOException("Password protected 7z archives are not supported: " + archive, e);
		}
	}

	@Override
	public long entrySize(Path archive, String entryName) throws IOException {
		try (SevenZFile sevenZ = open(archive)) {
			SevenZArchiveEntry entry = findEntry(sevenZ, archive, entryName);
			return entry.getSize();
		} catch (PasswordRequiredException e) {
			throw new IOException("Password protected 7z archives are not supported: " + archive, e);
		}
	}

	@Override
	public InputStream openEntry(Path archive, String entryName) throws IOException {
		SevenZFile sevenZ = open(archive);
		try {
			SevenZArchiveEntry entry = findEntry(sevenZ, archive, entryName);
			InputStream stream = new FilterInputStream(sevenZ.getInputStream(entry)) {
				@Override
				public void close() throws IOException {
					try {
						super.close();
					} finally {
						sevenZ.close();
					}
				}
			};
			return limitStream(stream, entry.getSize(), "7z resource is too large: " + entryName);
		} catch (IOException | RuntimeException e) {
			sevenZ.close();
			if (e instanceof PasswordRequiredException) {
				throw new IOException("Password protected 7z archives are not supported: " + archive, e);
			}
			throw e;
		}
	}

	@Override
	public byte[] readEntry(Path archive, String entryName) throws IOException {
		try (SevenZFile sevenZ = open(archive)) {
			SevenZArchiveEntry entry = findEntry(sevenZ, archive, entryName);
			long size = entry.getSize();
			if (size > MAX_CHART_SIZE) {
				throw new IOException("7z chart is too large: " + entryName);
			}
			try (InputStream input = sevenZ.getInputStream(entry);
					ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.max(size, 0))) {
				return readLimited(input, output, MAX_CHART_SIZE, "7z chart is too large: " + entryName);
			}
		} catch (PasswordRequiredException e) {
			throw new IOException("Password protected 7z archives are not supported: " + archive, e);
		}
	}

	private SevenZFile open(Path archive) throws IOException {
		try {
			return SevenZFile.builder()
					.setFile(archive.toFile())
					.setMaxMemoryLimitKb(MAX_DECODER_MEMORY_KIB)
					.get();
		} catch (PasswordRequiredException e) {
			throw new IOException("Password protected 7z archives are not supported: " + archive, e);
		}
	}

	private SevenZArchiveEntry findEntry(SevenZFile sevenZ, Path archive, String entryName) throws IOException {
		for (SevenZArchiveEntry entry : sevenZ.getEntries()) {
			validate(entry, archive);
			if (!entry.isDirectory() && entryName.equals(normalizeEntryNameOrNull(entry.getName()))) {
				return entry;
			}
		}
		throw new IOException("7z entry does not exist: " + entryName);
	}

	private void validate(SevenZArchiveEntry entry, Path archive) throws IOException {
		if (entry.isAntiItem()) {
			throw new IOException("7z anti-items are not supported: " + archive);
		}
		if (entry.getContentMethods() != null) {
			for (SevenZMethodConfiguration method : entry.getContentMethods()) {
				if (method.getMethod() == SevenZMethod.AES256SHA256) {
					throw new IOException("Password protected 7z entries are not supported: " + archive);
				}
			}
		}
	}
}
