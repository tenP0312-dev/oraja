# Endless Dream Feature Parity

This document is the maintained parity contract between LR2oraja Endless Dream
v0.4.0 and Arena oraja. Chat history and release-branch ancestry are not the
contract. The executable source of truth is
`tools/ed_0_4_0_feature_parity.json`.

## ED v0.4.0 inventory

The inventory contains exactly these 14 additions:

1. last-played song sorting;
2. Random Trainer shift and mirror;
3. DECIDE-screen skipping;
4. in-game lane-cover, LIFT, and HIDDEN settings;
5. Discord result Webhooks;
6. configured beatoraja IR and LR2-compatible BMS-IR leaderboards;
7. per-chart volume normalization;
8. Skin Widget Manager;
9. the Music Select song context menu;
10. live skin configuration;
11. difficulty-table management;
12. LR2IR G-BATTLE and ghost data;
13. optional CN end caps; and
14. OBS WebSocket integration.

Each manifest item must provide three independently named evidence roles:

- `entry`: the user-visible command or setting that reaches the feature;
- `state`: its persistent configuration or explicitly session-scoped state;
- `execution`: the runtime path that applies the feature.

Fragile items additionally name a regression-test marker. Removing a feature,
renaming its protected route without updating the inventory, dropping a test,
or changing the exact count fails parity validation.

## Distribution-to-main inventory

`tools/distribution_feature_parity.json` records fixes that were discovered in
post-v0.4.0 ED commits or in Arena distribution branches and are now integrated
into `main`. It currently protects:

- last-played sort visibility for legacy skins;
- Practice Random Trainer seed application;
- independent BGA texture ownership;
- controller connect/disconnect/reconnect handling;
- Skin Widget Manager independent lifetime and W/H export;
- distinct top-row `8` and NUMPAD `8` behavior;
- per-controller JKOC configuration;
- synchronized slider and numeric volume input;
- immediate rival-cache refresh from ranking responses;
- a separate configured Primary IR leaderboard; and
- nonzero fatal process exit status.

This is a fixed integration inventory, not a promise to merge an old release
branch wholesale. Main contains newer Arena protocol, skin, and configuration
work, so known distribution changes are selected and protected individually.

## Gates

Run the validator locally with:

```sh
python3 tools/check_feature_parity.py --root .
```

The package-tools CI job runs the same command. The final two-lane release
builder also validates both manifests in each clean reviewed worktree before
starting Gradle, so a distribution cannot silently omit the protected paths.

Automated checks do not replace operator-only physical acceptance. Controller
hotplug, focus/background input, F5 window behavior, skin rendering, and audio
device behavior still require the applicable hardware/client pass before a
binary is published.
