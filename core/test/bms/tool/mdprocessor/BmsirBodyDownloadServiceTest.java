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
		assertEquals(0, waybackRequests.get());
		assertTrue(Files.isRegularFile(result.archive()));
		assertFalse(Files.exists(temporary.resolve("Pack")));
		assertEquals(archive.length, Files.size(result.archive()));
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
	void neverReplacesAnExistingArchive() throws Exception {
		byte[] chart = "#TITLE Existing\n".getBytes(StandardCharsets.UTF_8);
		byte[] archive = zip("chart.bms", chart);
		server.createContext("/body", exchange -> respond(exchange, 200, "application/zip", archive));
		server.createContext("/available", exchange -> respond(exchange, 200, "application/json",
				"{}".getBytes(StandardCharsets.UTF_8)));
		String md5 = md5(chart);
		BmsirBodyDownloadService service = service(1024 * 1024);
		Path installed = service.install(task(base.resolve("/body"), md5)).archive();
		byte[] original = Files.readAllBytes(installed);

		IOException error = assertThrows(IOException.class,
				() -> service.install(task(base.resolve("/body"), md5)));

		assertTrue(error.getMessage().contains("already exists"));
		assertEquals(HexFormat.of().formatHex(original),
				HexFormat.of().formatHex(Files.readAllBytes(installed)));
	}

	@Test
	void acceptsOnlyHttpArchiveCandidatesAndSkipsBmsirSongPages() {
		assertTrue(BmsirBodyDownloadService.isEligibleBodyUrl("https://example.com/song.zip"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("file:///tmp/song.zip"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("https://www.bms-ir.org/new/song?md5=abc"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("https://bms-ir.org/new/songs/123"));
		assertFalse(BmsirBodyDownloadService.isEligibleBodyUrl("https://user@example.com/song.zip"));
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
