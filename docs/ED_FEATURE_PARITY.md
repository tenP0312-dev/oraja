# Endless Dream and Arena feature parity

This is the maintained compatibility contract for Arena oraja. The old
14-item list was only the published Endless Dream v0.4.0 inventory; it was not
a complete audit of later Endless Dream `main`, upstream `main`, or Arena
distribution fixes. Treating those 14 items as the complete product inventory
was incorrect.

The executable sources of truth are now three complementary manifests:

- `tools/ed_0_4_0_feature_parity.json`: the historical v0.4.0 release's fixed
  14-feature inventory;
- `tools/ed_mainline_feature_parity.json`: every non-merge change after the
  common baseline in legacy ED and upstream main, classified commit by commit;
- `tools/distribution_feature_parity.json`: fixes first shipped or recovered
  through Arena distribution work.

## Full-history audit boundary

The mainline audit compares:

- common baseline `b3d5dba6`;
- legacy ED tip `4107a517` (48 non-merge commits after the baseline); and
- upstream tip `d387cd92` (4 additional non-merge commits not already present
  in the legacy ED history).

All 52 commits are present in the mainline manifest with one of five explicit
outcomes:

- `integrated`: its behavior is directly present in Arena;
- `equivalent`: Arena already has the same or a newer protected behavior;
- `superseded`: an intentional Arena replacement owns the old entry/state/run
  path;
- `excluded`: the product owner explicitly decided the feature does not belong
  in Arena; or
- `metadata`: version, branch-marker, or formatting-only work with no runtime
  behavior.

The validator fixes the ledger at 52 unique full commit hashes. Removing a
row, duplicating a row, using an unknown outcome, or pointing an integrated
row at an unknown feature fails the release gate.

## Historical v0.4.0 inventory

The fixed 14-item release inventory remains protected because users still rely
on it:

1. last-played song sorting;
2. Random Trainer shift and mirror;
3. DECIDE-screen skipping;
4. in-game lane-cover, LIFT, and HIDDEN settings;
5. Discord result Webhooks;
6. configured beatoraja IR and LR2-compatible BMS-IR leaderboards;
7. per-chart volume normalization;
8. Skin Widget Manager;
9. Music Select song context menu;
10. live skin configuration;
11. difficulty-table management;
12. LR2IR G-BATTLE and ghost data;
13. optional CN end caps; and
14. OBS WebSocket integration.

This count describes v0.4.0 only. It must never again be reported as the full
ED/Arena feature count.

## Post-v0.4.0 and upstream inventory

After the explicit exclusion of rian's DX/X MODE and rianIR submission path,
the 24 retained mainline behavior groups are:

1. authored `#LNMODE` retention;
2. missed-POOR note visibility at the judgement line;
3. LR2-style difficulty filtering, input assignment, and skin APIs 221/309;
4. 5K/10K result input compatibility and mode restoration;
5. arrow-key HI-SPEED and lane-cover controls;
6. floating HI-SPEED;
7. PNG/JPG screenshot and Webhook attachment formats;
8. the Arena MANIAC replacement for legacy Extra Note state/execution;
9. cursor focus/visibility lifecycle;
10. song and course `/deletescore`;
11. database BACKBMP fallback;
12. IR-provided local-player display name;
13. Music Select LN/CN/HCN switching;
14. skin number 378 play date and 379 failed measure (including LITONE);
15. per-controller JKOC;
16. Practice Random Trainer seed application;
17. immediate rival-cache refresh;
18. JavaCV 1.5.11 and its matching FFmpeg preset;
19. synchronized numeric volume inputs;
20. complete `oraja_helper` select/play/result/play-end payloads;
21. inequality-based uncleared Random Select presets;
22. independent BGA texture ownership;
23. controller hotplug/reconnect; and
24. nonzero fatal process exit.

The excluded ledger rows remain in the manifest so a later audit cannot
mistake their absence for an accidental omission. They cover DX/X MODE judge,
gauge, class-gauge, naming and course behavior, plus rianIR routing and its
replay-send payload.

## Evidence model

Every retained behavior group names three independently checked source
markers:

- `entry`: the command, input, setting, parser header, or external event that
  reaches it;
- `state`: persistent configuration or explicitly session-scoped state; and
- `execution`: the runtime path that applies the behavior.

Fragile groups also name regression-test markers. This protects against the
specific failure mode that triggered the re-audit: a visible setting or skin
constant surviving while its runtime path silently disappears.

## Distribution-to-main inventory

The distribution manifest separately protects fixes that crossed release and
main branches, including last-played sort visibility, Skin Widget lifetime,
top-row/NUMPAD separation, BGA cache ownership, controller hotplug, JKOC,
volume input, rival refresh, Primary IR, Practice Random Trainer seed, and
fatal exit status. Overlap with the full-history manifest is deliberate: one
gate proves origin-history coverage, while the other prevents release-branch
integration from disappearing again.

## Gates

Run:

```sh
python3 tools/check_feature_parity.py --root .
python3 tools/test_feature_parity.py
```

The package-tools CI job and reviewed release builder run the same parity
validation. A distribution cannot silently omit an entire manifest, a source
entry/state/execution route, a protected regression marker, or a ledger row.

Automated checks do not replace operator-only physical acceptance. Controller
hotplug, background input, F5 window behavior, LITONE rendering, skin event
interaction, and audio-device behavior still require the applicable real
hardware/client pass before a binary is published.
