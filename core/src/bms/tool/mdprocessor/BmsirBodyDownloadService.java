package bms.tool.mdprocessor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

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
	private static final int MAX_LANDING_PAGE_SIZE = 2 * 1024 * 1024;
	private static final int MAX_ARCHIVE_CANDIDATES = 12;
	private static final int RESPONSE_SNIFF_SIZE = 8192;
	private static final Set<String> CHART_EXTENSIONS = Set.of(".bms", ".bme", ".bml", ".pms", ".bmson");
	private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(".zip", ".rar", ".7z");
	private static final Pattern CONTENT_TYPE_CHARSET = Pattern.compile(
			"(?i)(?:^|;)\\s*charset\\s*=\\s*[\\\"']?([^\\s;\\\"']+)");
	private static final Pattern META_CHARSET = Pattern.compile(
			"(?i)<meta[^>]+charset\\s*=\\s*[\\\"']?([^\\s;\\\"'/>]+)");
	private static final ObjectMapper JSON = new ObjectMapper();

	private final Path downloadDirectory;
	private final URI waybackAvailability;
	private final long maxDownloadSize;
	private final boolean allowPrivateNetwork;

	BmsirBodyDownloadService(Path downloadDirectory) {
		this(downloadDirectory, DEFAULT_WAYBACK_AVAILABILITY, DEFAULT_MAX_DOWNLOAD_SIZE, false);
	}

	BmsirBodyDownloadService(Path downloadDirectory, URI waybackAvailability, long maxDownloadSize) {
		this(downloadDirectory, waybackAvailability, maxDownloadSize, true);
	}

	BmsirBodyDownloadService(Path downloadDirectory, URI waybackAvailability, long maxDownloadSize,
			boolean allowPrivateNetwork) {
		this.downloadDirectory = downloadDirectory.toAbsolutePath().normalize();
		this.waybackAvailability = waybackAvailability;
		this.maxDownloadSize = maxDownloadSize;
		this.allowPrivateNetwork = allowPrivateNetwork;
	}

	static boolean isEligibleBodyUrl(String value) {
		try {
			URI uri = validatedHttpUri(value);
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
			return isPotentiallyPublicHost(host)
					&& !(isBmsirHost(host) && (path.equals("/new/song") || path.startsWith("/new/song/")
					|| path.equals("/new/songs") || path.startsWith("/new/songs/")));
		} catch (IOException | RuntimeException ignored) {
			return false;
		}
	}

	InstallResult install(DownloadTask task) throws IOException {
		return install(task, List.of());
	}

	InstallResult install(DownloadTask task, List<Path> retainedArchives) throws IOException {
		if (!isValidMd5(task.getHash())) {
			throw new IOException("The requested chart MD5 is invalid");
		}
		URI original = validatedHttpUri(task.getUrl());
		Path retained = findReusableArchive(retainedArchives, task.getHash());
		if (retained != null) {
			long size = Files.size(retained);
			task.setContentLength(size);
			task.setDownloadSize(size);
			task.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Downloaded);
			return new InstallResult(retained, original, false, false, true);
		}
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
		return installFrom(source, task, wayback, true);
	}

	private InstallResult installFrom(URI source, DownloadTask task, boolean wayback,
			boolean allowLandingPage) throws IOException {
		Files.createDirectories(downloadDirectory);
		Path staging = Files.createTempFile(downloadDirectory, ".bmsir-", ".part");
		DownloadedResource downloaded;
		List<URI> candidates = List.of();
		try {
			downloaded = download(source, staging, task);
			String extension = SongArchives.detectedExtension(staging);
			if (extension == null) {
				if (!allowLandingPage || !downloaded.htmlLike()) {
					throw new IOException("Response is not a ZIP, RAR4/RAR5, 7z, or supported HTML landing page");
				}
				candidates = archiveCandidates(staging, downloaded.source(), downloaded.contentType());
				if (candidates.isEmpty()) {
					throw new IOException("HTML landing page contains no ZIP, RAR, or 7z links");
				}
			} else {
				verifyRequestedChart(staging, task.getHash());
				Path destination = downloadDirectory.resolve(
						"bmsir-" + task.getHash().toLowerCase(Locale.ROOT) + extension);
				moveWithoutReplacing(staging, destination);
				return new InstallResult(destination, downloaded.source(), wayback, false, false);
			}
		} catch (RuntimeException error) {
			throw new IOException("Archive validation failed: " + error.getMessage(), error);
		} finally {
			Files.deleteIfExists(staging);
		}

		IOException firstFailure = null;
		for (URI candidate : candidates) {
			try {
				InstallResult result = installFrom(candidate, task, wayback, false);
				return new InstallResult(result.archive(), result.source(), result.wayback(), true, false);
			} catch (IOException error) {
				if (firstFailure == null) {
					firstFailure = error;
				}
			}
		}
		throw combinedFailure(
				"All " + candidates.size() + " archive links from the HTML landing page failed",
				firstFailure,
				null);
	}

	private Path findReusableArchive(List<Path> retainedArchives, String expectedMd5) {
		if (retainedArchives == null) {
			return null;
		}
		for (Path candidate : retainedArchives) {
			if (!isRetainedArchive(candidate)) {
				continue;
			}
			try {
				verifyRequestedChart(candidate, expectedMd5);
				return candidate.toAbsolutePath().normalize();
			} catch (IOException | RuntimeException ignored) {
				// A retained package is reusable only when the requested chart is verified.
			}
		}
		return null;
	}

	private boolean isRetainedArchive(Path candidate) {
		if (candidate == null) {
			return false;
		}
		Path normalized = candidate.toAbsolutePath().normalize();
		Path parent = normalized.getParent();
		String filename = normalized.getFileName() == null
				? ""
				: normalized.getFileName().toString().toLowerCase(Locale.ROOT);
		try {
			return downloadDirectory.equals(parent)
					&& filename.startsWith("bmsir-")
					&& ARCHIVE_EXTENSIONS.stream().anyMatch(filename::endsWith)
					&& Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
					&& Files.size(normalized) <= maxDownloadSize;
		} catch (IOException ignored) {
			return false;
		}
	}

	private DownloadedResource download(URI source, Path destination, DownloadTask task) throws IOException {
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
				String contentType = connection.getContentType();
				boolean declaredHtml = isHtmlContentType(contentType);
				task.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Downloading);
				task.setContentLength(Math.max(0, declaredSize));
				task.setDownloadSize(0);
				try (InputStream input = connection.getInputStream();
						var output = Files.newOutputStream(destination)) {
					byte[] first = input.readNBytes(RESPONSE_SNIFF_SIZE);
					boolean archiveLike = looksLikeSupportedArchive(first);
					boolean htmlLike = declaredHtml || looksLikeHtml(first);
					long effectiveLimit = archiveLike
							? maxDownloadSize
							: Math.min(maxDownloadSize, MAX_LANDING_PAGE_SIZE);
					if (declaredSize > effectiveLimit || first.length > effectiveLimit) {
						throw new IOException(htmlLike
								? "HTML landing page exceeds the size limit"
								: "Non-archive response exceeds the size limit");
					}
					output.write(first);
					long downloaded = first.length;
					task.setDownloadSize(downloaded);
					byte[] buffer = new byte[8192];
					int read;
					while ((read = input.read(buffer)) >= 0) {
						downloaded += read;
						if (downloaded > effectiveLimit) {
							throw new IOException(archiveLike
									? "Archive exceeds the download size limit"
									: "HTML or non-archive response exceeds the size limit");
						}
						output.write(buffer, 0, read);
						task.setDownloadSize(downloaded);
					}
					task.setDownloadTaskStatus(DownloadTask.DownloadTaskStatus.Downloaded);
					return new DownloadedResource(current, contentType, htmlLike);
				}
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

	private HttpURLConnection open(URI uri) throws IOException {
		ensureAllowedNetworkTarget(uri);
		HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		connection.setInstanceFollowRedirects(false);
		connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
		connection.setReadTimeout(READ_TIMEOUT_MILLIS);
		connection.setRequestProperty("User-Agent", "BMS-IR-Arena-oraja/BodyDownloader");
		connection.setRequestProperty("Accept-Encoding", "identity");
		return connection;
	}

	private List<URI> archiveCandidates(Path landingPage, URI base, String contentType) throws IOException {
		byte[] body = Files.readAllBytes(landingPage);
		if (body.length > MAX_LANDING_PAGE_SIZE) {
			throw new IOException("HTML landing page exceeds the size limit");
		}
		Charset charset = htmlCharset(contentType, body);
		LinkedHashSet<URI> candidates = new LinkedHashSet<>();
		HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
			@Override
			public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
				if (tag != HTML.Tag.A || candidates.size() >= MAX_ARCHIVE_CANDIDATES) {
					return;
				}
				Object href = attributes.getAttribute(HTML.Attribute.HREF);
				if (href != null) {
					addArchiveCandidate(candidates, base, href.toString());
				}
			}

			@Override
			public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
				handleStartTag(tag, attributes, position);
			}
		};
		new ParserDelegator().parse(new StringReader(new String(body, charset)), callback, true);
		return List.copyOf(candidates);
	}

	private void addArchiveCandidate(Set<URI> candidates, URI base, String href) {
		if (candidates.size() >= MAX_ARCHIVE_CANDIDATES || href == null || href.isBlank()) {
			return;
		}
		try {
			URI resolved = base.resolve(href.trim().replace(" ", "%20"));
			if (resolved.getFragment() != null) {
				String value = resolved.toString();
				resolved = URI.create(value.substring(0, value.indexOf('#')));
			}
			URI candidate = validatedHttpUri(resolved.toString());
			String path = candidate.getPath() == null ? "" : candidate.getPath().toLowerCase(Locale.ROOT);
			if (ARCHIVE_EXTENSIONS.stream().anyMatch(path::endsWith)
					&& (allowPrivateNetwork || isPotentiallyPublicHost(candidate.getHost()))) {
				candidates.add(candidate);
			}
		} catch (IOException | IllegalArgumentException ignored) {
			// A malformed or unsupported link is not a download candidate.
		}
	}

	private static Charset htmlCharset(String contentType, byte[] body) {
		Matcher header = CONTENT_TYPE_CHARSET.matcher(contentType == null ? "" : contentType);
		if (header.find()) {
			try {
				return Charset.forName(header.group(1));
			} catch (RuntimeException ignored) {
				// Fall through to bounded HTML metadata sniffing.
			}
		}
		String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
		Matcher metadata = META_CHARSET.matcher(head);
		if (metadata.find()) {
			try {
				return Charset.forName(metadata.group(1));
			} catch (RuntimeException ignored) {
				// Fall through to UTF-8, which is safe for ASCII URL attributes.
			}
		}
		return StandardCharsets.UTF_8;
	}

	private static boolean isHtmlContentType(String contentType) {
		if (contentType == null) {
			return false;
		}
		String normalized = contentType.toLowerCase(Locale.ROOT);
		return normalized.contains("text/html") || normalized.contains("application/xhtml+xml");
	}

	private static boolean looksLikeHtml(byte[] prefix) {
		int offset = startsWith(prefix, 0xef, 0xbb, 0xbf) ? 3 : 0;
		String value = new String(prefix, offset, Math.min(prefix.length - offset, 512), StandardCharsets.ISO_8859_1)
				.stripLeading()
				.toLowerCase(Locale.ROOT);
		return value.startsWith("<!doctype html") || value.startsWith("<html")
				|| value.startsWith("<head") || value.startsWith("<body");
	}

	private static boolean looksLikeSupportedArchive(byte[] prefix) {
		return startsWith(prefix, 0x50, 0x4b, 0x03, 0x04)
				|| startsWith(prefix, 0x50, 0x4b, 0x05, 0x06)
				|| startsWith(prefix, 0x50, 0x4b, 0x07, 0x08)
				|| startsWith(prefix, 0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00)
				|| startsWith(prefix, 0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00)
				|| startsWith(prefix, 0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c);
	}

	private static boolean startsWith(byte[] value, int... expected) {
		if (value.length < expected.length) {
			return false;
		}
		for (int index = 0; index < expected.length; index++) {
			if ((value[index] & 0xff) != expected[index]) {
				return false;
			}
		}
		return true;
	}

	private void ensureAllowedNetworkTarget(URI uri) throws IOException {
		if (allowPrivateNetwork) {
			return;
		}
		InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
		if (addresses.length == 0) {
			throw new IOException("Download host did not resolve");
		}
		for (InetAddress address : addresses) {
			if (!isPublicAddress(address)) {
				throw new IOException("Local or private network download targets are not allowed");
			}
		}
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

	private static boolean isPotentiallyPublicHost(String host) {
		String normalized = host.toLowerCase(Locale.ROOT);
		if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
			return false;
		}
		if (!(normalized.contains(":") || normalized.matches("[0-9.]+"))) {
			return true;
		}
		try {
			return isPublicAddress(InetAddress.getByName(normalized));
		} catch (IOException | RuntimeException ignored) {
			return false;
		}
	}

	private static boolean isPublicAddress(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		byte[] value = address.getAddress();
		if (address instanceof Inet4Address && value.length == 4) {
			int first = value[0] & 0xff;
			int second = value[1] & 0xff;
			int third = value[2] & 0xff;
			return first != 0
					&& first != 10
					&& first != 127
					&& first < 224
					&& !(first == 100 && second >= 64 && second <= 127)
					&& !(first == 169 && second == 254)
					&& !(first == 172 && second >= 16 && second <= 31)
					&& !(first == 192 && second == 0 && (third == 0 || third == 2))
					&& !(first == 192 && second == 168)
					&& !(first == 198 && (second == 18 || second == 19
							|| (second == 51 && third == 100)))
					&& !(first == 203 && second == 0 && third == 113);
		}
		if (value.length == 16) {
			int first = value[0] & 0xff;
			boolean uniqueLocal = (first & 0xfe) == 0xfc;
			boolean documentation = first == 0x20 && (value[1] & 0xff) == 0x01
					&& (value[2] & 0xff) == 0x0d && (value[3] & 0xff) == 0xb8;
			return !uniqueLocal && !documentation;
		}
		return false;
	}

	private static IOException combinedFailure(String message, IOException first, IOException second) {
		String details = message;
		if (first != null && first.getMessage() != null) {
			details += ": " + first.getMessage();
		}
		if (second != null && second.getMessage() != null) {
			details += " / " + second.getMessage();
		}
		IOException result = new IOException(details, first);
		if (second != null) {
			result.addSuppressed(second);
		}
		return result;
	}

	record DownloadedResource(URI source, String contentType, boolean htmlLike) {
	}

	record InstallResult(Path archive, URI source, boolean wayback, boolean landingPage, boolean reused) {
	}
}
