package bms.player.beatoraja;

import bms.player.beatoraja.song.SongData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableDataAccessorTest {
    private static final String CONFIGURED_URL = "https://example.test/table/header.json";
    private static final String SECOND_URL = "https://example.test/second/header.json";
    private static final String ORPHAN_URL = "https://example.test/old/header.json";

    @Test
    void replacesConfiguredCachesAndRemovesOrphansOnlyAfterAllTablesLoad(@TempDir Path tempDir)
            throws IOException {
        TableDataAccessor accessor = new TableDataAccessor(tempDir.toString());
        accessor.write(table(CONFIGURED_URL, "Old configured table", "OLD"));
        accessor.write(table(ORPHAN_URL, "Orphan table", "OLD"));
        Path unrelatedFile = tempDir.resolve("keep.txt");
        Files.writeString(unrelatedFile, "keep me");

        TableDataAccessor.TableUpdateResult result = accessor.replaceAllTableData(
                new String[]{CONFIGURED_URL},
                url -> table(url, "Updated table", "NEW"));

        assertTrue(result.success());
        assertEquals(1, result.updatedCount());
        assertEquals("Updated table", accessor.readCache(CONFIGURED_URL).getName());
        assertEquals("NEW", accessor.readCache(CONFIGURED_URL).getFolder()[0].getName());
        assertEquals(1, accessor.readAll().length);
        assertEquals("keep me", Files.readString(unrelatedFile));
        assertFalse(hasTemporaryDirectories(tempDir));
    }

    @Test
    void keepsTheCompletePreviousCacheSetWhenAnyTableFailsToLoad(@TempDir Path tempDir)
            throws IOException {
        TableDataAccessor accessor = new TableDataAccessor(tempDir.toString());
        accessor.write(table(CONFIGURED_URL, "Last good table", "OLD"));
        accessor.write(table(ORPHAN_URL, "Last good orphan", "OLD"));
        Map<String, byte[]> originalCaches = cacheBytes(tempDir);

        TableDataAccessor.TableUpdateResult result = accessor.replaceAllTableData(
                new String[]{CONFIGURED_URL, SECOND_URL},
                url -> SECOND_URL.equals(url) ? null : table(url, "New table", "NEW"));

        assertFalse(result.success());
        assertEquals(0, result.updatedCount());
        assertEquals(List.of(SECOND_URL), result.failedUrls());
        Map<String, byte[]> retainedCaches = cacheBytes(tempDir);
        assertEquals(originalCaches.keySet(), retainedCaches.keySet());
        originalCaches.forEach((name, bytes) -> assertArrayEquals(bytes, retainedCaches.get(name)));
        assertEquals("Last good table", accessor.readCache(CONFIGURED_URL).getName());
        assertEquals(2, accessor.readAll().length);
        assertFalse(hasTemporaryDirectories(tempDir));
    }

    private static TableData table(String url, String name, String folderName) {
        SongData song = new SongData();
        song.setTitle("Chart");
        song.setSha256("a".repeat(64));

        TableData.TableFolder folder = new TableData.TableFolder();
        folder.setName(folderName);
        folder.setSong(new SongData[]{song});

        TableData table = new TableData();
        table.setUrl(url);
        table.setName(name);
        table.setFolder(new TableData.TableFolder[]{folder});
        table.setCourse(CourseData.EMPTY);
        return table;
    }

    private static Map<String, byte[]> cacheBytes(Path directory) throws IOException {
        Map<String, byte[]> caches = new TreeMap<>();
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".bmt"))
                    .toList()) {
                caches.put(path.getFileName().toString(), Files.readAllBytes(path));
            }
        }
        return caches;
    }

    private static boolean hasTemporaryDirectories(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.anyMatch(path -> Files.isDirectory(path)
                    && path.getFileName().toString().startsWith(".table-update-"));
        }
    }
}
