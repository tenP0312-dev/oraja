package bms.player.beatoraja.nonstop;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Local storage of NONSTOP MIX sets and records, under the player folder
 * ({@code nonstopmix/}). Records live in their own file and never touch the
 * ordinary score database.
 */
public final class MixStore {
    private static final Logger logger = LoggerFactory.getLogger(MixStore.class);
    private static final String SETS = "sets";
    private static final String RECORDS = "records.jsonl";
    private static final String DRAFT = "draft.json";

    private final Path root;

    public MixStore(Path root) {
        this.root = root;
    }

    public static MixStore forPlayer(String playerPath, String playerName) {
        return new MixStore(Path.of(playerPath == null ? "player" : playerPath,
                playerName == null ? "player1" : playerName, "nonstopmix"));
    }

    public Path root() {
        return root;
    }

    /** Saved sets, newest first. Unreadable files are skipped. */
    public List<MixPlan> listSets() {
        Path directory = root.resolve(SETS);
        List<MixPlan> plans = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return plans;
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                try {
                    plans.add(MixJson.fromText(Files.readString(file, StandardCharsets.UTF_8)));
                } catch (IOException | RuntimeException e) {
                    logger.warn("NONSTOP MIX set could not be read: {} ({})", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.warn("NONSTOP MIX sets could not be listed: {}", e.getMessage());
        }
        plans.sort(Comparator.comparingLong((MixPlan plan) -> plan.updatedAt).reversed());
        return plans;
    }

    public void saveSet(MixPlan plan) throws IOException {
        plan.updatedAt = System.currentTimeMillis();
        writeAtomically(root.resolve(SETS).resolve(safeName(plan.id) + ".json"), MixJson.toText(plan, true));
    }

    public void deleteSet(MixPlan plan) throws IOException {
        Files.deleteIfExists(root.resolve(SETS).resolve(safeName(plan.id) + ".json"));
    }

    /** The editor's unsaved work survives a crash or a failed generation. */
    public void saveDraft(MixPlan plan) {
        try {
            writeAtomically(root.resolve(DRAFT), MixJson.toText(plan, true));
        } catch (IOException e) {
            logger.warn("NONSTOP MIX draft could not be saved: {}", e.getMessage());
        }
    }

    public MixPlan loadDraft() {
        Path file = root.resolve(DRAFT);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return MixJson.fromText(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            logger.warn("NONSTOP MIX draft could not be read: {}", e.getMessage());
            return null;
        }
    }

    public synchronized void addRecord(MixResult result) throws IOException {
        Files.createDirectories(root);
        String line = MixJson.mapper().writeValueAsString(result.toJson()) + "\n";
        Files.writeString(root.resolve(RECORDS), line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** All records of a set identity, newest first. */
    public synchronized List<MixResult> records(String identity) {
        List<MixResult> results = new ArrayList<>();
        Path file = root.resolve(RECORDS);
        if (!Files.isRegularFile(file)) {
            return results;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode node = MixJson.mapper().readTree(line);
                    if (identity == null || identity.equals(node.path("identity").asText())) {
                        results.add(MixResult.fromJson(node));
                    }
                } catch (IOException | RuntimeException e) {
                    // one broken line must not hide the other records
                }
            }
        } catch (IOException e) {
            logger.warn("NONSTOP MIX records could not be read: {}", e.getMessage());
        }
        results.sort(Comparator.comparingLong((MixResult result) -> result.playedAt).reversed());
        return results;
    }

    /** Best finished record by EX score, or null. */
    public MixResult best(String identity) {
        MixResult best = null;
        for (MixResult result : records(identity)) {
            if (best == null || result.clear > best.clear
                    || result.clear == best.clear && result.exScore > best.exScore) {
                best = result;
            }
        }
        return best;
    }

    private static String safeName(String id) {
        String value = id == null ? "" : id.replaceAll("[^A-Za-z0-9_-]", "_");
        return value.isEmpty() ? "set" : value;
    }

    private static void writeAtomically(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
