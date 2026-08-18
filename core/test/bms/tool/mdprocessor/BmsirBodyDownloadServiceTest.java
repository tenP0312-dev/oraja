package bms.tool.mdprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class BmsirBodyDownloadServiceTest {

	@TempDir
	Path temporary;

	private HttpServer server;
	private URI base;
	private final AtomicInteger waybackRequests = new AtomicInteger();

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.start();
		base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void installsValidatedZipWithoutExtractingIt() throws Exception {
		byte[] chart = "#PLAYER 1\n#TITLE Direct\n#00111:01\n".getBytes(StandardCharsets.UTF_8);
		byte[] archive = zip("Pack/chart.bms", chart, "Pack/payload.exe", new byte[] { 1, 2, 3 });
		server.createContext("/body", exchange -> respond(exchange, 200, "application/octet-stream", archive));
		server.createContext("/available", exchange -> {
			waybackRequests.incrementAndGet();
			respond(exchange, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
		});

		String md5 = md5(chart);
		DownloadTask task = task(base.resolve("/body"), md5);
		BmsirBodyDownloadService service = service(1024 * 1024);
		BmsirBodyDownloadService.InstallResult result = service.install(task);

		assertEquals(temporary.resolve("bmsir-" + md5 + ".zip"), result.archive());
		assertFalse(result.wayback());
		assertFalse(result.landingPage());
		assertEquals(0, waybackRequests.get());
		assertTrue(Files.isRegularFile(result.archive()));
		assertFalse(Files.exists(temporary.resolve("Pack")));
		assertEquals(archive.length, Files.size(result.archive()));
	}

	@Test
	void resolvesArchiveLinkFromHtmlLandingPage() throws Exception {
		byte[] chart = "#TITLE Landing page\n".getBytes(StandardCharsets.UTF_8);
		byte[] archive = zip("Pack/chart.bms", chart);
		server.createContext("/entry", exchange -> respond(exchange, 200, "text/html; charset=UTF-8",
				("<!doctype html><html><body>"
						+ "<a href=\"/information\">Details</a>"
						+ "<a href=\"downloads/song.zip\">Download</a>"
						+ "</body></html>").getBytes(StandardCharsets.UTF_8)));
		server.createContext("/downloads/song.zip",
				exchange -> respond(exchange, 200, "application/zip", archive));
		server.createContext("/available", exchange -> {
			waybackRequests.incrementAndGet();
			respond(exchange, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
		});

		BmsirBodyDownloadService.InstallResult result = service(1024 * 1024)
				.install(task(base.resolve("/entry"), md5(chart)));

		assertEquals(base.resolve("/downloads/song.zip"), result.source());
		assertFalse(result.wayback());
		assertTrue(result.landingPage());
		assertEquals(0, waybackRequests.get());
		assertTrue(Files.isRegularFile(result.archive()));
	}

	@Test
	void triesLaterArchiveLinkWhenAnEarlierPackageHasTheWrongChart() throws Exception {
		byte[] chart = "#TITLE Requested\n".getBytes(StandardCharsets.UTF_8);
		byte[] wrongArchive = zip("wrong.bms", "#TITLE Wrong\n".getBytes(StandardCharsets.UTF_8));
		byte[] correctArchive = zip("requested.bms", chart);
		Path retainedWrongArchive = Files.write(
				temporary.resolve("bmsir-" + "1".repeat(32) + ".zip"), wrongArchive);
		server.createContext("/entry", exchange -> respond(exchange, 200, "text/html",
				("<a href=\"/wrong.zip\">Old package</a>"
						+ "<a href=\"/correct.7z\">Current package</a>").getBytes(StandardCharsets.UTF_8)));
		server.createContext("/wrong.zip", exchange -> respond(exchange, 200, "application/zip", wrongArchive));
		server.createContext("/correct.7z", exchange -> respond(exchange, 200, "application/octet-stream", correctArchive));
		server.createContext("/available", exchange -> respond(exchange, 200, "application/json",
				"{}".getBytes(StandardCharsets.UTF_8)));

		BmsirBodyDownloadService.InstallResult result = service(1024 * 1024)
				.install(task(base.resolve("/entry"), md5(chart)), java.util.List.of(retainedWrongArchive));

		assertEquals(base.resolve("/correct.7z"), result.source());
		assertTrue(result.landingPage());
		assertFalse(result.reused());
		assertEquals(correctArchive.length, Files.size(result.archive()));
	}

	@Test
	void reusesRetainedPackageOnlyWhenItContainsTheRequestedChart() throws Exception {
		byte[] firstChart = "#TITLE First\n".getBytes(StandardCharsets.UTF_8);
		byte[] requestedChart = "#TITLE Another difficulty\n".getBytes(StandardCharsets.UTF_8);
		byte[] archive = zip("Pack/first.bms", firstChart, "Pack/another.bms", requestedChart);
		Path retained = Files.write(
				temporary.resolve("bmsir-" + md5(firstChart) + ".zip"), archive);
		AtomicInteger bodyRequests = new AtomicInteger();
		server.createContext("/body", exchange -> {
			bodyRequests.incrementAndGet();
			respond(exchange, 500, "text/plain", new byte[0]);
		});

		DownloadTask task = task(base.resolve("/body"), md5(requestedChart));
		BmsirBodyDownloadService.InstallResult result = service(1024 * 1024)
				.install(task, java.util.List.of(retained));

		assertEquals(retained, result.archive());
		assertTrue(result.reused());
		assertEquals(0, bodyRequests.get());
		assertEquals(archive.length, task.getDownloadSize());
		assertEquals(DownloadTask.DownloadTaskStatus.Downloaded, task.getDownloadTaskStatus());
		try (var files = Files.list(temporary)) {
			assertEquals(1, files.count());
		}
	}

	@Test
	void resolvesArchiveLinkFromArchivedHtmlLandingPage() throws Exception {
		byte[] chart = "#TITLE Archived landing page\n".getBytes(StandardCharsets.UTF_8);
		byte[] archive = zip("chart.bms", chart);
		server.createContext("/gone", exchange -> respond(exchange, 404, "text/plain", new byte[0]));
		server.createContext("/snapshot-page", exchange -> respond(exchange, 200, "text/html",
				"<a href=\"/snapshot/song.rar\">Archived package</a>".getBytes(StandardCharsets.UTF_8)));
		server.createContext("/snapshot/song.rar",
				exchange -> respond(exchange, 200, "application/octet-stream", archive));
		server.createContext("/available", exchange -> {
			waybackRequests.incrementAndGet();
			String json = "{\"archived_snapshots\":{\"closest\":{\"available\":true,"
					+ "\"status\":\"200\",\"url\":\"" + base.resolve("/snapshot-page") + "\"}}}";
			respond(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
		});

		BmsirBodyDownloadService.InstallResult result = service(1024 * 1024)
				.install(task(base.resolve("/gone"), md5(chart)));

		assertEquals(base.resolve("/snapshot/song.rar"), result.source());
		assertTrue(result.wayback());
		assertTrue(result.landingPage());
		assertEquals(1, waybackRequests.get());
	}

	@Test
	void fallsBackToAnAvailableWaybackSnapshot() throws Exception {
		byte[] chart = "#TITLE Archived\n".getBytes(StandardCharsets.UTF_8);
		byte[] archive = zip("chart.bms", chart);
		server.createContext("/gone", exchange -> respond(exchange, 404, "text/plain", new byte[0]));
		server.createContext("/snapshot", exchange -> respond(exchange, 200, "application/zip", archive));
		server.createContext("/available", exchange -> {
			waybackRequests.incrementAndGet();
			String json = "{\"archived_snapshots\":{\"closest\":{\"available\":true,"
					+ "\"status\":\"200\",\"url\":\"" + base.resolve("/snapshot") + "\"}}}";
			respond(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
		});

		BmsirBodyDownloadService.InstallResult result = service(1024 * 1024)
				.install(task(base.resolve("/gone"), md5(chart)));

		assertTrue(result.wayback());
		assertEquals(base.resolve("/snapshot"), result.source());
		assertEquals(1, waybackRequests.get());
	}

	@Test
	void rejectsWrongChartHashAndLeavesNoArchiveOrExtraction() throws Exception {
		byte[] archive = zip("chart.bms", "#TITLE Wrong\n".getBytes(StandardCharsets.UTF_8));
		server.createContext("/body", exchange -> respond(exchange, 200, "application/zip", archive));
		server.createContext("/available", exchange -> respond(exchange, 200, "application/json",
				"{}".getBytes(StandardCharsets.UTF_8)));

		IOException error = assertThrows(IOException.class,
				() -> service(1024 * 1024).install(task(base.resolve("/body"), "0".repeat(32))));

		assertTrue(error.getMessage().contains("requested chart MD5"));
		try (var files = Files.list(temporary)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void rejectsOversizedResponsesBeforeArchiveParsing() throws Exception {
		server.createContext("/large", exchange -> respond(exchange, 200, "application/octet-stream", new byte[64]));
		server.createContext("/available", exchange -> respond(exchange, 200, "application/json",
				"{}".getBytes(StandardCharsets.UTF_8)));

		IOException error = assertThrows(IOException.class,
				() -> service(16).install(task(base.resolve("/large"), "0".repeat(32))));

		assertTrue(error.getMessage().contains("download size limit"));
	}

	@Test
	void rejectsOversizedHtmlLandingPages() throws Exception {
		server.createContext("/large-page", exchange -> respond(exchange, 200, "text/html",
				new byte[2 * 1024 * 1024 + 1]));
		server.createContext("/available", exchange -> respond(exchange, 200, "application/json",
				"{}".getBytes(StandardCharsets.UTF_8)));

		IOException error = assertThrows(IOException.class,
				() -> service(4 * 1024 * 1024).install(task(base.resolve("/large-page"), "0".repeat(32))));

		assertTrue(error.getMessage().contains("HTML landing page exceeds the size limit"));
		try (var files = Files.list(temporary)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void rejectsLandingPagesWithoutArchiveLinks() throws Exception {
		server.createContext("/page", exchange -> respond(exchange, 200, "text/html",
				"<a href=\"/readme\">Read me</a>".getBytes(StandardCharsets.UTF_8)));
		server.createContext("/available", exchange -> respond(exchange, 200, "application/json",
				"{}".getBytes(StandardCharsets.UTF_8)));

		IOException error = assertThrows(IOException.class,
				() -> service(1024 * 1024).install(task(base.resolve("/page"), "0".repeat(32))));

		assertTrue(error.getMessage().contains("contains no ZIP, RAR, or 7z links"));
		try (var files = Files.list(temporary)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void rejectsUnsafeEntryPathsAndDoesNotExtractThem() throws Exception {
		byte[] chart = "#TITLE Escape\n".getBytes(StandardCharsets.UTF_8);
		byte[] archive = zip("../chart.bms", chart);
		server.createContext("/unsafe", exchange -> respond(exchange, 200, "application/zip", archive));
		server.createContext("/available", exchange -> respond(exchange, 200, "application/json",
				"{}".getBytes(StandardCharsets.UTF_8)));

		IOException error = assertThrows(IOException.class,
				() -> service(1024 * 1024).install(task(base.resolve("/unsafe"), md5(chart))));

		assertTrue(error.getMessage().contains("Unsafe ZIP entry"));
		try (var files = Files.list(temporary)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void reusesValidatedArchiveFromDiskAcrossServiceRestart() throws Exception {
		byte[] chart = "#TITLE Existing\n".getBytes(StandardCharsets.UTF_8);
		byte[] archive = zip("chart.bms", chart);
		AtomicInteger bodyRequests = new AtomicInteger();
		server.createContext("/body", exchange -> {
			bodyRequests.incrementAndGet();
			respond(exchange, 500, "text/plain", new byte[0]);
		});
		server.createContext("/available", exchange -> respond(exchange, 200, "application/json",
				"{}".getBytes(StandardCharsets.UTF_8)));
		String md5 = md5(chart);
		Path installed = Files.write(temporary.resolve("bmsir-" + md5 + ".zip"), archive);

		BmsirBodyDownloadService.InstallResult result = service(1024 * 1024)
				.install(task(base.resolve("/body"), md5));

		assertEquals(installed, result.archive());
		assertTrue(result.reused());
		assertEquals(0, bodyRequests.get());
	}

	@Test
	void rejectsInvalidExactArchiveWithoutNetworkOrOverwrite() throws Exception {
		byte[] requestedChart = "#TITLE Requested\n".getBytes(StandardCharsets.UTF_8);
		byte[] existingArchive = zip("wrong.bms", "#TITLE Wrong\n".getBytes(StandardCharsets.UTF_8));
		byte[] downloadableArchive = zip("requested.bms", requestedChart);
		AtomicInteger bodyRequests = new AtomicInteger();
		server.createContext("/body", exchange -> {
			bodyRequests.incrementAndGet();
			respond(exchange, 200, "application/zip", downloadableArchive);
		});
		String md5 = md5(requestedChart);
		Path existing = Files.write(temporary.resolve("bmsir-" + md5 + ".zip"), existingArchive);
		byte[] original = Files.readAllBytes(existing);

		IOException error = assertThrows(IOException.class,
				() -> service(1024 * 1024).install(task(base.resolve("/body"), md5)));

		assertTrue(error.getMessage().contains("failed validation"));
		assertEquals(0, bodyRequests.get());
		assertEquals(HexFormat.of().formatHex(original),
				HexFormat.of().formatHex(Files.readAllBytes(existing)));
	}

	@Test
	void acceptsOnlyHttpArchiveCandidatesAndSkipsBmsirSongPages() {
		assertTrue(BmsirBodyDownloadService.isEligibleBodyUrl("https://example.com/song.zip"));
		assertTrue(BmsirBodyDownloadService.isEligibleBodyUrl("https://example.com/distribution-page"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("file:///tmp/song.zip"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("https://www.bms-ir.org/new/song?md5=abc"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("https://bms-ir.org/new/songs/123"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("https://user@example.com/song.zip"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("http://127.0.0.1/song.zip"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("http://localhost/song.zip"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("http://[::1]/song.zip"));
	}

	@Test
	void blocksPrivateNetworkTargetsOutsideTheTestPolicy() throws Exception {
		server.createContext("/body", exchange -> respond(exchange, 200, "application/zip", new byte[0]));
		BmsirBodyDownloadService service = new BmsirBodyDownloadService(
				temporary, base.resolve("/available"), 1024 * 1024, false);

		IOException error = assertThrows(IOException.class,
				() -> service.install(task(base.resolve("/body"), "0".repeat(32))));

		assertTrue(error.getMessage().contains("Local or private network download targets are not allowed"));
	}

	private BmsirBodyDownloadService service(long maxSize) {
		return new BmsirBodyDownloadService(temporary, base.resolve("/available"), maxSize);
	}

	private static DownloadTask task(URI uri, String md5) {
		return new DownloadTask(1, uri.toString(), "Test song", md5,
				DownloadTask.DownloadMode.ArchiveInPlace);
	}

	private static byte[] zip(Object... namesAndContents) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream output = new ZipOutputStream(bytes)) {
			for (int index = 0; index < namesAndContents.length; index += 2) {
				output.putNextEntry(new ZipEntry((String) namesAndContents[index]));
				output.write((byte[]) namesAndContents[index + 1]);
				output.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

	private static String md5(byte[] value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value));
	}

	private static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
			throws IOException {
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.sendResponseHeaders(status, body.length);
		try (var response = exchange.getResponseBody()) {
			response.write(body);
		}
	}
}
