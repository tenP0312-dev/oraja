package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * HTTPS transport of the NONSTOP MIX IR API (Issue #535). Uses the configured
 * BMS-IR Arena server and the BMS-IR login of the player, like the My
 * Difficulty Table endpoint.
 */
public final class BMSIRNonstopMixTransport {
    public static final String API_PATH = "/new/api/oraja/nonstop_mix.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private BMSIRNonstopMixTransport() {
    }

    public record Response(int status, JsonNode body) {
        public boolean ok() {
            return status >= 200 && status < 300 && body.path("ok").asBoolean(false);
        }

        public String error() {
            if (status == 404 && !body.has("error")) {
                return "unsupported";
            }
            return body.path("error").asText(status >= 500 ? "server_error" : "request_failed");
        }
    }

    /** Whether a BMS-IR login is configured (needed for publishing and scores). */
    public static boolean hasLogin(MainController main) {
        IRConfig config = bmsirConfig(main);
        if (config == null || config.getUserid() == null) {
            return false;
        }
        try {
            Integer.parseInt(config.getUserid().trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Sends one request.
     *
     * @param withLogin attach the player's BMS-IR id and password hash
     */
    public static Response post(MainController main, ObjectNode payload, boolean withLogin) throws IOException {
        payload.put("client_version", Version.getArenaClientVersion());
        if (withLogin) {
            IRConfig config = bmsirConfig(main);
            if (config == null) {
                throw new IllegalStateException("BMS-IR configuration is missing");
            }
            payload.put("player_id", Integer.parseInt(config.getUserid().trim()));
            payload.put("passmd5", md5(config.getPassword()));
        }
        URL url = BMSIRManiacApiClient.endpoint(main.getPlayerConfig().getBmsirArenaServer(), API_PATH).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(15_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "BMS-IR-Arena-oraja/" + Version.getArenaClientVersion());
        byte[] requestBody = JSON.writeValueAsBytes(payload);
        connection.setFixedLengthStreamingMode(requestBody.length);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBody);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            JsonNode body = JSON.createObjectNode();
            if (stream != null) {
                try (InputStream input = stream) {
                    byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                    if (bytes.length > MAX_RESPONSE_BYTES) {
                        throw new IOException("NONSTOP MIX response is too large");
                    }
                    try {
                        JsonNode parsed = JSON.readTree(bytes);
                        if (parsed != null) {
                            body = parsed;
                        }
                    } catch (IOException notJson) {
                        // an HTML error page from a server without this API
                    }
                }
            }
            return new Response(status, body);
        } finally {
            connection.disconnect();
        }
    }

    private static IRConfig bmsirConfig(MainController main) {
        if (main == null || main.getPlayerConfig() == null) {
            return null;
        }
        IRConfig[] configurations = main.getPlayerConfig().getIrconfig();
        if (configurations == null) {
            return null;
        }
        for (IRConfig config : configurations) {
            if (config != null && config.getIrname() != null
                    && config.getIrname().toLowerCase(Locale.ROOT).contains("bms")) {
                return config;
            }
        }
        return null;
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
