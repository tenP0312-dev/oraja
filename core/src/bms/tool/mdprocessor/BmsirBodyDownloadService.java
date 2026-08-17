package bms.tool.mdprocessor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import bms.player.beatoraja.song.archive.SongArchives;

/** Downloads and validates an untrusted BMS-IR body archive without extracting it. */
final class BmsirBodyDownloadService {

	static final long DEFAULT_MAX_DOWNLOAD_SIZE = 2L * 1024 * 1024 * 1024;
	static final URI DEFAULT_WAYBACK_AVAILABILITY = URI.create("https://archive.org/wayback/available");
	private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
	private static final int READ_TIMEOUT_MILLIS = 60_000;
	private static final int MAX_REDIRECTS = 5;
	private static final int MAX_WAYBACK_RESPONSE_SIZE = 1024 * 1024;
	private static final Set<String> CHART_EXTENSIONS = Set.of(".bms", ".bme", ".bml", ".pms", ".bmson");
	private static final ObjectMapper JSON = new ObjectMapper();

	private final Path downloadDirectory;
	private final URI waybackAvailability;
	private final long maxDownloadSize;

	BmsirBodyDownloadService(Path downloadDirectory) {
		this(downloadDirectory, DEFAULT_WAYBACK_AVAILABILITY, DEFAULT_MAX_DOWNLOAD_SIZE);
	}

	BmsirBodyDownloadService(Path downloadDirectory, URI waybackAvailability, long maxDownloadSize) {
		this.downloadDirectory = downloadDirectory.toAbsolutePath().normalize();
		this.waybackAvailability = waybackAvailability;
		this.maxDownloadSize = maxDownloadSize;
	}

	static boolean isEligibleBodyUrl(String value) {
		try {
			URI uri = validatedHttpUri(value);
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
			return !(isBmsirHost(host) && (path.equals("/new/song") || path.startsWith("/new/song/")
					|| path.equals("/new/songs") || path.startsWith("/new/songs/")));
		} catch (IOException | RuntimeException ignored) {
			return false;
		}
	}

	InstallResult install(DownloadTask task) throws IOException {
		if (!isValidMd5(task.getHash())) {
			throw new IOException("The requested chart MD5 is invalid");
		}
		URI original = validatedHttpUri(task.getUrl());
		IOException liveFailure;
		try {
			return installFrom(original, task, false);
		} catch (IOException error) {
			liveFailure = error;
		}

		URI snapshot;
		try {
			snapshot = findWaybackSnapshot(original);
		} catch (IOException error) {
			throw combinedFailure("Live URL failed and the Wayback lookup failed", liveFailure, error);
		}
		if (snapshot == null || snapshot.equals(original)) {
			throw combinedFailure("Live URL failed and no Wayback snapshot is available", liveFailure, null);
		}
		try {
			return installFrom(snapshot, task, true);
		} catch (IOException error) {
			throw combinedFailure("Live URL and Wayback snapshot both failed", liveFailure, error);
		}
	}

	private InstallResult installFrom(URI source, DownloadTask task, boolean wayback) throws IOException {
		Files.createDirectories(downloadDirectory);
		Path staging = Files.createTempFile(downloadDirectory, ".bmsir-", ".part");
		try {
			URI finalSource = download(source, staging, task);
			String extension = SongArchives.detectedExtension(staging);
			if (extension == null) {
				throw new IOException("Response is not a ZIP, RAR4/RAR5, or 7z archive");
			}
			verifyRequestedChart(staging, task.getHash());
			Path destination = downloadDirectory.resolve(
					"bmsir-" + task.getHash().toLowerCase(Locale.ROOT) + extension);
			moveWithoutReplacing(staging, destination);
			return new InstallResult(destination, finalSource, wayback);
		} catch (RuntimeException error) {
			throw new IOException("Archive validation failed: " + error.getMessage(), error);
		} finally {
			Files.deleteIfExists(staging);
		}
	}

	private URI download(URI source, Path destination, DownloadTask task) throws IOException {
		URI current = source;
		for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
			HttpURLConnection connection = open(current);
			try {
				int status = connection.getResponseCode();
				if (isRedirect(status)) {
					if (redirect == MAX_REDIRECTS) {
						throw new IOException("Too many HTTP redirects");
					}
					String location = connection.getHeaderField("Location");
					if (location == null || location.isBlank()) {
						throw new IOException("HTTP redirect has no Location header");
					}
					current = validatedHttpUri(current.resolve(location).toString());
					continue;
				}
				if (status != HttpURLConnection.HTTP_OK) {
					throw new IOException("Unexpected HTTP response: " + status);
				}
				long declaredSize = connection.getContentLengthLong();
				if (declaredSize > maxDownloadSize) {
					throw new IOException("Archive exceeds the download size limit");
				}
				task.setContentLength(declaredSize);
				task.setDownloadSize(0);
				try (InputStream input = connection.getInputStream();
						var output = Files.newOutputStream(destination)) {
					byte[] buffer = new byte[8192];
					long downloaded = 0;
					int read;
					while ((read = input.read(buffer)) >= 0) {
						downloaded += read;
						if (downloaded > maxDownloadSize) {
							throw new IOException("Archive exceeds the download size limit");
						}
						output.write(buffer, 0, read);
						task.setDownloadSize(downloaded);
					}
				}
				task.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Downloaded);
				return current;
			} finally {
				connection.disconnect();
			}
		}
		throw new IOException("Too many HTTP redirects");
	}

	private URI findWaybackSnapshot(URI original) throws IOException {
		String separator = waybackAvailability.getRawQuery() == null ? "?" : "&";
		URI request = validatedHttpUri(waybackAvailability + separator + "url="
				+ URLEncoder.encode(original.toString(), StandardCharsets.UTF_8));
		HttpURLConnection connection = open(request);
		try {
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("Wayback Availability API returned " + connection.getResponseCode());
			}
			byte[] body;
			try (InputStream input = connection.getInputStream()) {
				body = input.readNBytes(MAX_WAYBACK_RESPONSE_SIZE + 1);
			}
			if (body.length > MAX_WAYBACK_RESPONSE_SIZE) {
				throw new IOException("Wayback Availability API response is too large");
			}
			JsonNode response = JSON.readTree(new ByteArrayInputStream(body));
			if (response == null) {
				return null;
			}
			JsonNode closest = response.path("archived_snapshots").path("closest");
			if (!closest.path("available").asBoolean(false)
					|| !"200".equals(closest.path("status").asText())) {
				return null;
			}
			URI snapshot = validatedHttpUri(closest.path("url").asText());
			if (!isAllowedWaybackHost(snapshot.getHost())) {
				throw new IOException("Wayback API returned an unexpected snapshot host");
			}
			return snapshot;
		} finally {
			connection.disconnect();
		}
	}

	private boolean isAllowedWaybackHost(String host) {
		String normalized = host.toLowerCase(Locale.ROOT);
		if (normalized.equals("web.archive.org")) {
			return true;
		}
		String endpointHost = waybackAvailability.getHost();
		return endpointHost != null && normalized.equals(endpointHost.toLowerCase(Locale.ROOT));
	}

	private static HttpURLConnection open(URI uri) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		connection.setInstanceFollowRedirects(false);
		connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
		connection.setReadTimeout(READ_TIMEOUT_MILLIS);
		connection.setRequestProperty("User-Agent", "BMS-IR-Arena-oraja/BodyDownloader");
		connection.setRequestProperty("Accept-Encoding", "identity");
		return connection;
	}

	private static void verifyRequestedChart(Path archive, String expectedMd5) throws IOException {
		SongArchives.ArchiveContents contents = SongArchives.readContents(archive);
		boolean foundChart = false;
		for (String entry : contents.entries()) {
			String lower = entry.toLowerCase(Locale.ROOT);
			if (CHART_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
				continue;
			}
			foundChart = true;
			byte[] chart = SongArchives.readEntry(archive, entry);
			if (md5(chart).equalsIgnoreCase(expectedMd5)) {
				return;
			}
		}
		if (!foundChart) {
			throw new IOException("Archive contains no supported BMS chart");
		}
		throw new IOException("Archive does not contain the requested chart MD5");
	}

	private static String md5(byte[] value) throws IOException {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value));
		} catch (NoSuchAlgorithmException error) {
			throw new IOException("MD5 is unavailable", error);
		}
	}

	private static void moveWithoutReplacing(Path source, Path destination) throws IOException {
		if (Files.exists(destination)) {
			throw new FileAlreadyExistsException("Archive already exists: " + destination.getFileName());
		}
		Files.move(source, destination);
	}

	private static URI validatedHttpUri(String value) throws IOException {
		if (value == null || value.isBlank()) {
			throw new IOException("Download URL is empty");
		}
		URI uri;
		try {
			uri = URI.create(value.trim()).normalize();
		} catch (IllegalArgumentException error) {
			throw new IOException("Download URL is invalid", error);
		}
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
				|| uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
			throw new IOException("Only plain HTTP(S) archive URLs are accepted");
		}
		return uri;
	}

	private static boolean isValidMd5(String value) {
		return value != null && value.matches("(?i)[0-9a-f]{32}");
	}

	private static boolean isRedirect(int status) {
		return status == HttpURLConnection.HTTP_MOVED_PERM
				|| status == HttpURLConnection.HTTP_MOVED_TEMP
				|| status == HttpURLConnection.HTTP_SEE_OTHER
				|| status == 307 || status == 308;
	}

	private static boolean isBmsirHost(String host) {
		return host.equals("bms-ir.org") || host.equals("www.bms-ir.org");
	}

	private static IOException combinedFailure(String message, IOException first, IOException second) {
		String details = message + ": " + first.getMessage();
		if (second != null && second.getMessage() != null) {
			details += " / " + second.getMessage();
		}
		IOException result = new IOException(details, first);
		if (second != null) {
			result.addSuppressed(second);
		}
		return result;
	}

	record InstallResult(Path archive, URI source, boolean wayback) {
	}
}
