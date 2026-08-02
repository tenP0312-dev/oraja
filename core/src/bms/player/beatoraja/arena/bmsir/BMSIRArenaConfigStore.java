package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.PlayerConfig;
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

/**
 * Persists BMS-IR-specific settings independently from config_player.json.
 *
 * Older or non-BMS-IR bodies rewrite PlayerConfig with only the fields they know.
 * Keeping this deliberately allow-listed sidecar makes those rewrites harmless
 * without copying IR credentials or other unrelated player settings.
 */
public final class BMSIRArenaConfigStore {
    static final String FILE_NAME = "bmsir_arena.json";
    private static final Logger logger =
            LoggerFactory.getLogger(BMSIRArenaConfigStore.class);

    private BMSIRArenaConfigStore() {
    }

    /**
     * Loads the sidecar when present. When absent, migrates the BMS-IR values
     * already loaded from config_player.json and creates the sidecar once.
     */
    public static boolean loadOrMigrate(
            String playerPath,
            String playerId,
            PlayerConfig player
    ) {
        Path path = sidecarPath(playerPath, playerId);
        if (!Files.exists(path)) {
            boolean written = write(playerPath, player);
            if (written) {
                logger.info("BMS-IR Arena settings migrated to {}", path);
            }
            return written;
        }

        try {
            Settings settings = RobustFile.load(path, data -> parse(path, data));
            settings.applyTo(player);
            return true;
        } catch (IOException e) {
            // Do not replace a damaged file with defaults. The original and its
            // backup remain available for manual recovery.
            logger.error(
                    "BMS-IR Arena settings could not be loaded from {}: {}",
                    path,
                    e.getLocalizedMessage()
            );
            return false;
        }
    }

    public static boolean write(String playerPath, PlayerConfig player) {
        if (player == null || player.getId() == null || player.getId().isBlank()) {
            logger.error("BMS-IR Arena settings were not saved: player id is missing");
            return false;
        }
        Path path = sidecarPath(playerPath, player.getId());
        try {
            Json json = configuredJson();
            String serialized = json.prettyPrint(Settings.from(player));
            RobustFile.write(path, serialized.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | SerializationException e) {
            logger.error(
                    "BMS-IR Arena settings could not be saved to {}: {}",
                    path,
                    e.getLocalizedMessage()
            );
            return false;
        }
    }

    static Path sidecarPath(String playerPath, String playerId) {
        return Paths.get(playerPath, playerId, FILE_NAME);
    }

    private static Settings parse(Path path, byte[] data) throws ParseException {
        try {
            Settings settings = configuredJson().fromJson(
                    Settings.class,
                    new String(data, StandardCharsets.UTF_8)
            );
            if (settings == null) {
                throw new SerializationException("empty settings");
            }
            return settings;
        } catch (SerializationException e) {
            throw new ParseException(
                    "BMS-IR Arena settings parse failed - Path: "
                            + path
                            + ", Log: "
                            + e.getLocalizedMessage(),
                    0
            );
        }
    }

    private static Json configuredJson() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        json.setOutputType(JsonWriter.OutputType.json);
        json.setUsePrototypes(false);
        return json;
    }

    /** Explicit allow-list. Do not replace with PlayerConfig serialization. */
    static final class Settings {
        private int schemaVersion = 7;
        private Boolean oneBassEnabled;
        private Boolean startHerePreviewEnabled;
        private Boolean danLocalSyncEnabled;
        private boolean enabled = false;
        private String server = "wss://www.bms-ir.org/new/arena/ws/client";
        private boolean unrestrictedRating = false;
        private boolean allowCpu = true;
        private boolean allowHigherSelection = false;
        private boolean randomMirror = false;
        private String rulesetProfile = "lr2";
        private int overlayMode = 0;
        private int lastVisibleOverlayMode = 0;
        private int overlayHotkeyFunction = 5;
        private int overlayHotkeyModifiers = 3;
        private int[] overlayHotkeyKeys;
        private boolean showCursor = false;
        private boolean stayInRoom = true;
        private boolean roomParticipating = true;
        private boolean spectatorPublic = false;
        private boolean forceHostOption = false;
        private boolean alwaysReady = false;
        private int graphHighlight = 0;
        private String targetMode = PlayerConfig.BMSIR_ARENA_TARGET_OFF;
        private String graphOrder = PlayerConfig.BMSIR_ARENA_GRAPH_ORDER_RANK;
        private String coverControlMode;
        private Integer coverChangeStep;
        private boolean coverHispeedAutoAdjustEnabled = false;
        private String[] numpadActions;
        private Integer numpadJudgeTimingStep;
        private boolean judgeTimingRestoreEnabled = false;
        private boolean infoNotificationsEnabled = true;
        private boolean presentationOverlayEnabled = true;
        private boolean countdownSeEnabled = true;
        private boolean startSeEnabled = true;
        private boolean phaseWarningEnabled = true;
        private int notificationSeVolume = 100;
        private boolean muteChat = false;
        private String nominationPolicy = "all";
        private String seriesFormat = "single";
        private int firstToWins = 2;
        private int nominationSeconds = 60;
        private int optionSeconds = 10;
        private int intermissionSeconds = 0;

        static Settings from(PlayerConfig player) {
            Settings settings = new Settings();
            settings.oneBassEnabled = player.isBmsirOneBassEnabled();
            settings.startHerePreviewEnabled =
                    player.isBmsirStartHerePreviewEnabled();
            settings.danLocalSyncEnabled = player.isBmsirDanLocalSyncEnabled();
            settings.enabled = player.isBmsirArenaEnabled();
            settings.server = player.getBmsirArenaServer();
            settings.unrestrictedRating = player.isBmsirArenaUnrestrictedRating();
            settings.allowCpu = player.isBmsirArenaAllowCpu();
            settings.allowHigherSelection = player.isBmsirArenaAllowHigherSelection();
            settings.randomMirror = player.isBmsirArenaRandomMirror();
            settings.rulesetProfile = player.getBmsirRulesetProfile();
            settings.overlayMode = player.getBmsirArenaOverlayMode();
            settings.lastVisibleOverlayMode =
                    player.getBmsirArenaLastVisibleOverlayMode();
            settings.overlayHotkeyFunction = player.getBmsirArenaOverlayHotkeyFunction();
            settings.overlayHotkeyModifiers = player.getBmsirArenaOverlayHotkeyModifiers();
            settings.overlayHotkeyKeys = player.getBmsirArenaOverlayHotkeyKeys();
            settings.showCursor = player.isBmsirArenaShowCursor();
            settings.stayInRoom = player.isBmsirArenaStayInRoom();
            settings.roomParticipating = player.isBmsirArenaRoomParticipating();
            settings.spectatorPublic = player.isBmsirArenaSpectatorPublic();
            settings.forceHostOption = player.isBmsirArenaForceHostOption();
            settings.alwaysReady = player.isBmsirArenaAlwaysReady();
            settings.graphHighlight = player.getBmsirArenaGraphHighlight();
            settings.targetMode = player.getBmsirArenaTargetMode();
            settings.graphOrder = player.getBmsirArenaGraphOrder();
            settings.coverControlMode = player.getBmsirCoverControlMode();
            settings.coverChangeStep = player.getBmsirCoverChangeStep();
            settings.coverHispeedAutoAdjustEnabled =
                    player.isBmsirCoverHispeedAutoAdjustEnabled();
            settings.numpadActions = player.getBmsirNumpadActions();
            settings.numpadJudgeTimingStep =
                    player.getBmsirNumpadJudgeTimingStep();
            settings.judgeTimingRestoreEnabled =
                    player.isBmsirJudgeTimingRestoreEnabled();
            settings.infoNotificationsEnabled =
                    player.isBmsirInfoNotificationsEnabled();
            settings.presentationOverlayEnabled =
                    player.isBmsirArenaPresentationOverlayEnabled();
            settings.countdownSeEnabled = player.isBmsirArenaCountdownSeEnabled();
            settings.startSeEnabled = player.isBmsirArenaStartSeEnabled();
            settings.phaseWarningEnabled = player.isBmsirArenaPhaseWarningEnabled();
            settings.notificationSeVolume = player.getBmsirArenaNotificationSeVolume();
            settings.muteChat = player.isBmsirArenaMuteChat();
            settings.nominationPolicy = player.getBmsirArenaNominationPolicy();
            settings.seriesFormat = player.getBmsirArenaSeriesFormat();
            settings.firstToWins = player.getBmsirArenaFirstToWins();
            settings.nominationSeconds = player.getBmsirArenaNominationSeconds();
            settings.optionSeconds = player.getBmsirArenaOptionSeconds();
            settings.intermissionSeconds = player.getBmsirArenaIntermissionSeconds();
            return settings;
        }

        void applyTo(PlayerConfig player) {
            // Null means a schema 1/2/3 sidecar. Preserve the common-config value
            // (or the new default) until the next write upgrades the sidecar.
            if (oneBassEnabled != null) {
                player.setBmsirOneBassEnabled(oneBassEnabled);
            }
            if (startHerePreviewEnabled != null) {
                player.setBmsirStartHerePreviewEnabled(startHerePreviewEnabled);
            }
            if (danLocalSyncEnabled != null) {
                player.setBmsirDanLocalSyncEnabled(danLocalSyncEnabled);
            }
            player.setBmsirArenaEnabled(enabled);
            player.setBmsirArenaServer(server);
            player.setBmsirArenaUnrestrictedRating(unrestrictedRating);
            player.setBmsirArenaAllowCpu(allowCpu);
            player.setBmsirArenaAllowHigherSelection(allowHigherSelection);
            player.setBmsirArenaRandomMirror(randomMirror);
            player.setBmsirRulesetProfile(rulesetProfile);
            player.setBmsirArenaLastVisibleOverlayMode(lastVisibleOverlayMode);
            player.setBmsirArenaOverlayMode(overlayMode);
            player.setBmsirArenaOverlayHotkeyFunction(overlayHotkeyFunction);
            player.setBmsirArenaOverlayHotkeyModifiers(overlayHotkeyModifiers);
            player.setBmsirArenaOverlayHotkeyKeys(
                    overlayHotkeyKeys == null
                            ? BMSIRArenaHotkey.fromLegacy(
                                    overlayHotkeyFunction,
                                    overlayHotkeyModifiers
                            )
                            : overlayHotkeyKeys
            );
            player.setBmsirArenaShowCursor(showCursor);
            player.setBmsirArenaStayInRoom(stayInRoom);
            player.setBmsirArenaRoomParticipating(roomParticipating);
            player.setBmsirArenaSpectatorPublic(spectatorPublic);
            player.setBmsirArenaForceHostOption(forceHostOption);
            player.setBmsirArenaAlwaysReady(alwaysReady);
            player.setBmsirArenaGraphHighlight(graphHighlight);
            player.setBmsirArenaTargetMode(targetMode);
            player.setBmsirArenaGraphOrder(graphOrder);
            if (coverControlMode != null) {
                player.setBmsirCoverControlMode(coverControlMode);
            }
            if (coverChangeStep != null) {
                player.setBmsirCoverChangeStep(coverChangeStep);
            }
            player.setBmsirCoverHispeedAutoAdjustEnabled(
                    coverHispeedAutoAdjustEnabled
            );
            if (numpadActions != null) {
                player.setBmsirNumpadActions(numpadActions);
            }
            if (numpadJudgeTimingStep != null) {
                player.setBmsirNumpadJudgeTimingStep(numpadJudgeTimingStep);
            }
            player.setBmsirJudgeTimingRestoreEnabled(
                    judgeTimingRestoreEnabled
            );
            player.setBmsirInfoNotificationsEnabled(infoNotificationsEnabled);
            player.setBmsirArenaPresentationOverlayEnabled(
                    presentationOverlayEnabled
            );
            player.setBmsirArenaCountdownSeEnabled(countdownSeEnabled);
            player.setBmsirArenaStartSeEnabled(startSeEnabled);
            player.setBmsirArenaPhaseWarningEnabled(phaseWarningEnabled);
            player.setBmsirArenaNotificationSeVolume(notificationSeVolume);
            player.setBmsirArenaMuteChat(muteChat);
            player.setBmsirArenaNominationPolicy(nominationPolicy);
            player.setBmsirArenaSeriesFormat(seriesFormat);
            player.setBmsirArenaFirstToWins(firstToWins);
            player.setBmsirArenaNominationSeconds(nominationSeconds);
            player.setBmsirArenaOptionSeconds(optionSeconds);
            player.setBmsirArenaIntermissionSeconds(intermissionSeconds);
        }
    }
}
