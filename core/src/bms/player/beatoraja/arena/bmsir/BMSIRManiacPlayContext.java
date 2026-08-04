package bms.player.beatoraja.arena.bmsir;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.player.beatoraja.pattern.BMSIRManiacModifier;
import bms.player.beatoraja.pattern.AutoplayModifier;
import bms.player.beatoraja.pattern.LaneShuffleModifier.PlayerBattleModifier;

/** Immutable play identity plus the placement hash produced during loading. */
public final class BMSIRManiacPlayContext {
    public static final String MODEL_STORAGE_HASH = "bmsir.maniac.storage_hash";
    public static final String MODEL_BASE_HASH = "bmsir.maniac.base_hash";
    public static final String MODEL_OPTIONS = "bmsir.maniac.options";
    public static final String MODEL_RANKING_CLASS = "bmsir.maniac.ranking_class";
    public static final String MODEL_VIRTUAL_HASH = "bmsir.maniac.virtual_hash";
    public static final String MODEL_GENERATION_SEED = "bmsir.maniac.generation_seed";
    public static final String MODEL_ALGORITHM_VERSION = "bmsir.maniac.algorithm_version";
    public static final String MODEL_PLACEMENT_HASH = "bmsir.maniac.placement_hash";

    private final BMSIRManiacSettings settings;
    private final String baseHash;
    private final String storageHash;
    private final String virtualHash;
    private final long generationSeed;
    private final boolean doubleBattleApplied;
    private final boolean doubleBattleSuspended;
    private String placementHash;

    private BMSIRManiacPlayContext(
            BMSIRManiacSettings settings,
            String baseHash,
            boolean doubleBattleApplied,
            boolean doubleBattleSuspended
    ) {
        this.settings = settings;
        this.baseHash = baseHash;
        this.storageHash = settings.storageChartId(baseHash);
        this.virtualHash = settings.virtualChartId(baseHash);
        this.generationSeed = settings.generationSeed(baseHash);
        this.doubleBattleApplied = doubleBattleApplied;
        this.doubleBattleSuspended = doubleBattleSuspended;
    }

    public static BMSIRManiacPlayContext prepare(
            BMSIRManiacSettings persisted,
            BMSModel model,
            boolean blocked
    ) {
        if (persisted == null || model == null || blocked) return null;
        BMSIRManiacSettings applied = effectiveSettings(persisted, model.getMode());
        if (applied == null) return null;
        boolean nativeDouble = model.getMode().player == 2;
        boolean dbRequested = persisted.isDoubleBattle();
        boolean dbApplied = dbRequested && !nativeDouble && supportsDoubleBattle(model.getMode());
        boolean dbSuspended = dbRequested && !dbApplied;

        BMSIRManiacPlayContext context = new BMSIRManiacPlayContext(
                applied,
                model.getSHA256(),
                dbApplied,
                dbSuspended
        );
        context.apply(model);
        return context;
    }

    public static BMSIRManiacSettings effectiveSettings(
            BMSIRManiacSettings persisted,
            Mode mode
    ) {
        if (persisted == null || mode == null) return null;
        BMSIRManiacSettings applied = new BMSIRManiacSettings(persisted);
        if (applied.isDoubleBattle()
                && (mode.player == 2 || !supportsDoubleBattle(mode))) {
            applied.setDoubleBattle(false);
        }
        return applied.isActive() ? applied : null;
    }

    private void apply(BMSModel model) {
        BMSIRManiacModifier modifier = new BMSIRManiacModifier(settings);
        modifier.modify(model);
        if (doubleBattleApplied) {
            model.setMode(switch (model.getMode()) {
                case BEAT_5K -> Mode.BEAT_10K;
                case BEAT_7K -> Mode.BEAT_14K;
                case KEYBOARD_24K -> Mode.KEYBOARD_24K_DOUBLE;
                default -> model.getMode();
            });
            new PlayerBattleModifier().modify(model);
            if (settings.isAutoScratch()) {
                new AutoplayModifier(model.getMode().scratchKey).modify(model);
            }
        }
        updatePlacement(model);
        mark(model);
    }

    public void updatePlacement(BMSModel model) {
        placementHash = BMSIRManiacModifier.placementHash(model);
        mark(model);
    }

    private void mark(BMSModel model) {
        model.getValues().put(MODEL_STORAGE_HASH, storageHash);
        model.getValues().put(MODEL_BASE_HASH, baseHash == null ? "" : baseHash);
        model.getValues().put(MODEL_OPTIONS, settings.canonicalOptions());
        model.getValues().put(MODEL_RANKING_CLASS, settings.rankingClass().name());
        if (virtualHash != null) model.getValues().put(MODEL_VIRTUAL_HASH, virtualHash);
        model.getValues().put(MODEL_GENERATION_SEED, Long.toUnsignedString(generationSeed));
        model.getValues().put(MODEL_ALGORITHM_VERSION,
                Integer.toString(BMSIRManiacSettings.ALGORITHM_VERSION));
        if (placementHash != null) model.getValues().put(MODEL_PLACEMENT_HASH, placementHash);
    }

    private static boolean supportsDoubleBattle(Mode mode) {
        return mode == Mode.BEAT_5K || mode == Mode.BEAT_7K || mode == Mode.KEYBOARD_24K;
    }

    public static boolean isDoubleBattleSuspended(
            BMSIRManiacSettings settings,
            Mode mode
    ) {
        return settings != null
                && settings.isDoubleBattle()
                && mode != null
                && mode.player == 2;
    }

    public BMSIRManiacSettings settings() { return new BMSIRManiacSettings(settings); }
    public String baseHash() { return baseHash; }
    public String storageHash() { return storageHash; }
    public String virtualHash() { return virtualHash; }
    public long generationSeed() { return generationSeed; }
    public String placementHash() { return placementHash; }
    public boolean isDoubleBattleApplied() { return doubleBattleApplied; }
    public boolean isDoubleBattleSuspended() { return doubleBattleSuspended; }
    public String randomLink() { return settings.getRandomLink(); }
}
