package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.TableData;
import bms.player.beatoraja.system.RobustFile;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.SerializationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;

/** Per-player, last-good cache of BMS-IR Primary IR selection tables. */
public final class BMSIRPrimaryIrTableCache {
    static final String FILE_NAME = "bmsir_primary_ir_tables.json";
    private static final Logger logger =
            LoggerFactory.getLogger(BMSIRPrimaryIrTableCache.class);

    private BMSIRPrimaryIrTableCache() {
    }

    /**
     * Replaces the cache only with a complete non-empty set of valid tables.
     * Empty or invalid refreshes preserve the previous last-good file.
     */
    public static boolean sync(String playerPath, String playerId, TableData[] tables) {
        if (!valid(tables)) {
            logger.info("BMS-IR Primary IR table cache kept the last-good data");
            return false;
        }
        Path path = cachePath(playerPath, playerId);
        try {
            Files.createDirectories(path.getParent());
            String serialized = configuredJson().prettyPrint(tables);
            RobustFile.write(path, serialized.getBytes(StandardCharsets.UTF_8));
            logger.info("BMS-IR Primary IR table cache saved {} tables to {}", tables.length, path);
            return true;
        } catch (IOException | SerializationException error) {
            logger.error(
                    "BMS-IR Primary IR table cache could not save {}: {}",
                    path,
                    error.getLocalizedMessage()
            );
            return false;
        }
    }

    public static TableData[] read(String playerPath, String playerId) {
        Path path = cachePath(playerPath, playerId);
        if (!Files.isRegularFile(path)) {
            return new TableData[0];
        }
        try {
            return RobustFile.load(path, data -> parse(path, data));
        } catch (IOException error) {
            logger.error(
                    "BMS-IR Primary IR table cache could not be loaded from {}: {}",
                    path,
                    error.getLocalizedMessage()
            );
            return new TableData[0];
        }
    }

    static Path cachePath(String playerPath, String playerId) {
        return Paths.get(playerPath, playerId, FILE_NAME);
    }

    private static TableData[] parse(Path path, byte[] data) throws ParseException {
        try {
            TableData[] parsed = configuredJson().fromJson(
                    TableData[].class,
                    new String(data, StandardCharsets.UTF_8)
            );
            if (!valid(parsed)) {
                throw new SerializationException("empty or invalid Primary IR table cache");
            }
            return parsed;
        } catch (RuntimeException error) {
            ParseException failure = new ParseException(
                    "BMS-IR Primary IR table cache parse failed - Path: "
                            + path
                            + ", Log: "
                            + error.getLocalizedMessage(),
                    0
            );
            failure.initCause(error);
            throw failure;
        }
    }

    private static boolean valid(TableData[] tables) {
        if (tables == null || tables.length == 0) {
            return false;
        }
        for (TableData table : tables) {
            if (table == null || !table.validate()) {
                return false;
            }
        }
        return true;
    }

    private static Json configuredJson() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        json.setOutputType(JsonWriter.OutputType.json);
        json.setUsePrototypes(false);
        return json;
    }
}
